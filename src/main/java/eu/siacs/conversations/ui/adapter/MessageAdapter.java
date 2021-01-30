package eu.siacs.conversations.ui.adapter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Strings;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Message.FileParams;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.http.HttpDownloadConnection;
import eu.siacs.conversations.http.P1S3UrlStreamHandler;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.XmppFragment;
import eu.siacs.conversations.ui.service.AudioPlayer;
import eu.siacs.conversations.ui.text.DividerSpan;
import eu.siacs.conversations.ui.text.QuoteSpan;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.MyLinkify;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.ui.widget.ClickableMovementMethod;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.EmojiWrapper;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.Patterns;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleFileTransferConnection;
import eu.siacs.conversations.xmpp.mam.MamReference;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    public static final String DATE_SEPARATOR_BODY = "DATE_SEPARATOR";
    public static final int SENT = 0;
    public static final int RECEIVED = 1;
    private static final int STATUS = 2;
    private static final int DATE_SEPARATOR = 3;
    private static final int RTP_SESSION = 4;
    private final XmppActivity activity;
    private final AudioPlayer audioPlayer;
    private List<Message> mMessages;
    private List<String> highlightedTerm = null;
    private final DisplayMetrics metrics;
    private OnContactPictureClicked mOnContactPictureClickedListener;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener;
    private boolean mUseGreenBackground = false;
    private OnQuoteListener onQuoteListener;
    private XmppFragment mFragment;

    public MessageAdapter(XmppActivity activity, XmppFragment fragment, List<Message> messages) {
        super();
        this.mMessages = messages;
        this.audioPlayer = new AudioPlayer(this, activity);
        this.activity = activity;
        this.mFragment = fragment;
        metrics = this.activity.getResources().getDisplayMetrics();
        updatePreferences();
    }


    private static void resetClickListener(View... views) {
        for (View view : views) {
            view.setOnClickListener(null);
        }
    }

    public void flagScreenOn() {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void flagScreenOff() {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void setVolumeControl(final int stream) {
        activity.setVolumeControlStream(stream);
    }

    public void setOnContactPictureClicked(OnContactPictureClicked listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public Activity getActivity() {
        return activity;
    }

    public void setOnContactPictureLongClicked(
            OnContactPictureLongClicked listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }

    public void setOnQuoteListener(OnQuoteListener listener) {
        this.onQuoteListener = listener;
    }

    private int getItemViewType(Message message) {
        if (message.getType() == Message.TYPE_STATUS) {
            if (DATE_SEPARATOR_BODY.equals(message.getBody())) {
                return DATE_SEPARATOR;
            } else {
                return STATUS;
            }
        } else if (message.getType() == Message.TYPE_RTP_SESSION) {
            return RTP_SESSION;
        } else if (message.getStatus() <= Message.STATUS_RECEIVED) {
            return RECEIVED;
        } else {
            return SENT;
        }
    }

    private int viewTypeToLayout(int viewType) {
        switch (viewType) {
            case DATE_SEPARATOR:
                return R.layout.message_date_bubble;
            case RTP_SESSION:
                return R.layout.message_rtp_session;
            case SENT:
                return R.layout.message_sent;
            case RECEIVED:
                return R.layout.message_received;
            case STATUS:
                return R.layout.message_status;
            default:
                throw new AssertionError("Unknown view type");
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(viewTypeToLayout(viewType), parent, false);
        switch(viewType) {
            case SENT:
            case RECEIVED:
                return new SentReceivedViewHolder(view, viewType);
            case RTP_SESSION:
                return new RTPViewHolder(view);
            case DATE_SEPARATOR:
                return new DateSeparatorViewHolder(view);
            case STATUS:
                return new StatusViewHolder(view);
            default:
                throw new AssertionError("Unknown view type");
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {
        Message message = mMessages.get(position);
        viewHolder.bind(message);
    }

    @Override
    public int getItemViewType(int position) {
        return this.getItemViewType(mMessages.get(position));
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    private int getMessageTextColor(boolean onDark, boolean primary) {
        if (onDark) {
            return ContextCompat.getColor(activity, primary ? R.color.white : R.color.white70);
        } else {
            return ContextCompat.getColor(activity, primary ? R.color.black87 : R.color.black54);
        }
    }

    private void displayStatus(SentReceivedViewHolder viewHolder, Message message, int type, boolean darkBackground) {
        String filesize = null;
        String info = null;
        Context context = viewHolder.itemView.getContext();
        boolean error = false;
        if (viewHolder.indicatorReceived != null) {
            viewHolder.indicatorReceived.setVisibility(View.GONE);
        }

        if (viewHolder.edit_indicator != null) {
            if (message.edited()) {
                viewHolder.edit_indicator.setVisibility(View.VISIBLE);
                viewHolder.edit_indicator.setImageResource(darkBackground ? R.drawable.ic_mode_edit_white_18dp : R.drawable.ic_mode_edit_black_18dp);
                viewHolder.edit_indicator.setAlpha(darkBackground ? 0.7f : 0.57f);
            } else {
                viewHolder.edit_indicator.setVisibility(View.GONE);
            }
        }
        final Transferable transferable = message.getTransferable();
        boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI
                && message.getMergedStatus() <= Message.STATUS_RECEIVED;
        if (message.isFileOrImage() || transferable != null || MessageUtils.unInitiatedButKnownSize(message)) {
            FileParams params = message.getFileParams();
            filesize = params.size > 0 ? UIHelper.filesizeToString(params.size) : null;
            if (transferable != null && (transferable.getStatus() == Transferable.STATUS_FAILED || transferable.getStatus() == Transferable.STATUS_CANCELLED)) {
                error = true;
            }
        }
        switch (message.getMergedStatus()) {
            case Message.STATUS_WAITING:
                info = context.getString(R.string.waiting);
                break;
            case Message.STATUS_UNSEND:
                if (transferable != null) {
                    info = context.getString(R.string.sending_file, transferable.getProgress());
                } else {
                    info = context.getString(R.string.sending);
                }
                break;
            case Message.STATUS_OFFERED:
                info = context.getString(R.string.offering);
                break;
            case Message.STATUS_SEND_RECEIVED:
            case Message.STATUS_SEND_DISPLAYED:
                viewHolder.indicatorReceived.setImageResource(darkBackground ? R.drawable.ic_done_white_18dp : R.drawable.ic_done_black_18dp);
                viewHolder.indicatorReceived.setAlpha(darkBackground ? 0.7f : 0.57f);
                viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
                break;
            case Message.STATUS_SEND_FAILED:
                final String errorMessage = message.getErrorMessage();
                if (Message.ERROR_MESSAGE_CANCELLED.equals(errorMessage)) {
                    info = context.getString(R.string.cancelled);
                } else if (errorMessage != null) {
                    final String[] errorParts = errorMessage.split("\\u001f", 2);
                    if (errorParts.length == 2) {
                        switch (errorParts[0]) {
                            case "file-too-large":
                                info = context.getString(R.string.file_too_large);
                                break;
                            default:
                                info = context.getString(R.string.send_failed);
                                break;
                        }
                    } else {
                        info = context.getString(R.string.send_failed);
                    }
                } else {
                    info = context.getString(R.string.send_failed);
                }
                error = true;
                break;
            default:
                if (multiReceived) {
                    info = UIHelper.getMessageDisplayName(message);
                }
                break;
        }
        if (error && type == SENT) {
            if (darkBackground) {
                viewHolder.time.setTextAppearance(context, R.style.TextAppearance_Conversations_Caption_Warning_OnDark);
            } else {
                viewHolder.time.setTextAppearance(context, R.style.TextAppearance_Conversations_Caption_Warning);
            }
        } else {
            if (darkBackground) {
                viewHolder.time.setTextAppearance(viewHolder.itemView.getContext(), R.style.TextAppearance_Conversations_Caption_OnDark);
            } else {
                viewHolder.time.setTextAppearance(viewHolder.itemView.getContext(), R.style.TextAppearance_Conversations_Caption);
            }
            viewHolder.time.setTextColor(this.getMessageTextColor(darkBackground, false));
        }
        if (message.getEncryption() == Message.ENCRYPTION_NONE) {
            viewHolder.indicator.setVisibility(View.GONE);
        } else {
            boolean verified = false;
            if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                final FingerprintStatus status = message.getConversation()
                        .getAccount().getAxolotlService().getFingerprintTrust(
                                message.getFingerprint());
                if (status != null && status.isVerified()) {
                    verified = true;
                }
            }
            if (verified) {
                viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_verified_user_white_18dp : R.drawable.ic_verified_user_black_18dp);
            } else {
                viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_lock_white_18dp : R.drawable.ic_lock_black_18dp);
            }
            if (darkBackground) {
                viewHolder.indicator.setAlpha(0.7f);
            } else {
                viewHolder.indicator.setAlpha(0.57f);
            }
            viewHolder.indicator.setVisibility(View.VISIBLE);
        }

        final String formattedTime = UIHelper.readableTimeDifferenceFull(context, message.getMergedTimeSent());
        final String bodyLanguage = message.getBodyLanguage();
        final String bodyLanguageInfo = bodyLanguage == null ? "" : String.format(" \u00B7 %s", bodyLanguage.toUpperCase(Locale.US));
        if (message.getStatus() <= Message.STATUS_RECEIVED) {
            if ((filesize != null) && (info != null)) {
                viewHolder.time.setText(formattedTime + " \u00B7 " + filesize + " \u00B7 " + info + bodyLanguageInfo);
            } else if ((filesize == null) && (info != null)) {
                viewHolder.time.setText(formattedTime + " \u00B7 " + info + bodyLanguageInfo);
            } else if ((filesize != null) && (info == null)) {
                viewHolder.time.setText(formattedTime + " \u00B7 " + filesize + bodyLanguageInfo);
            } else {
                viewHolder.time.setText(formattedTime + bodyLanguageInfo);
            }
        } else {
            if ((filesize != null) && (info != null)) {
                viewHolder.time.setText(filesize + " \u00B7 " + info + bodyLanguageInfo);
            } else if ((filesize == null) && (info != null)) {
                if (error) {
                    viewHolder.time.setText(info + " \u00B7 " + formattedTime + bodyLanguageInfo);
                } else {
                    viewHolder.time.setText(info);
                }
            } else if ((filesize != null) && (info == null)) {
                viewHolder.time.setText(filesize + " \u00B7 " + formattedTime + bodyLanguageInfo);
            } else {
                viewHolder.time.setText(formattedTime + bodyLanguageInfo);
            }
        }
    }

    private void displayInfoMessage(SentReceivedViewHolder viewHolder, CharSequence text, boolean darkBackground) {
        Context context = viewHolder.itemView.getContext();
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.messageBody.setText(text);
        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(context, R.style.TextAppearance_Conversations_Body1_Secondary_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(context, R.style.TextAppearance_Conversations_Body1_Secondary);
        }
        viewHolder.messageBody.setTextIsSelectable(false);
    }

    private void displayEmojiMessage(final SentReceivedViewHolder viewHolder, final String body, final boolean darkBackground) {
        Context context = viewHolder.itemView.getContext();
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(context, R.style.TextAppearance_Conversations_Body1_Emoji_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(context, R.style.TextAppearance_Conversations_Body1_Emoji);
        }
        Spannable span = new SpannableString(body);
        float size = Emoticons.isEmoji(body) ? 3.0f : 2.0f;
        span.setSpan(new RelativeSizeSpan(size), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        viewHolder.messageBody.setText(EmojiWrapper.transform(span));
    }

    private void applyQuoteSpan(SpannableStringBuilder body, int start, int end, boolean darkBackground) {
        if (start > 1 && !"\n\n".equals(body.subSequence(start - 2, start).toString())) {
            body.insert(start++, "\n");
            body.setSpan(new DividerSpan(false), start - 2, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            end++;
        }
        if (end < body.length() - 1 && !"\n\n".equals(body.subSequence(end, end + 2).toString())) {
            body.insert(end, "\n");
            body.setSpan(new DividerSpan(false), end, end + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int color = darkBackground ? this.getMessageTextColor(darkBackground, false)
                : ContextCompat.getColor(activity, R.color.green700_desaturated);
        DisplayMetrics metrics = activity.getBaseContext().getResources().getDisplayMetrics();
        body.setSpan(new QuoteSpan(color, metrics), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * Applies QuoteSpan to group of lines which starts with > or » characters.
     * Appends likebreaks and applies DividerSpan to them to show a padding between quote and text.
     */
    private boolean handleTextQuotes(SpannableStringBuilder body, boolean darkBackground) {
        boolean startsWithQuote = false;
        char previous = '\n';
        int lineStart = -1;
        int lineTextStart = -1;
        int quoteStart = -1;
        for (int i = 0; i <= body.length(); i++) {
            char current = body.length() > i ? body.charAt(i) : '\n';
            if (lineStart == -1) {
                if (previous == '\n') {
                    if ((current == '>' && UIHelper.isPositionFollowedByQuoteableCharacter(body, i))
                            || current == '\u00bb' && !UIHelper.isPositionFollowedByQuote(body, i)) {
                        // Line start with quote
                        lineStart = i;
                        if (quoteStart == -1) quoteStart = i;
                        if (i == 0) startsWithQuote = true;
                    } else if (quoteStart >= 0) {
                        // Line start without quote, apply spans there
                        applyQuoteSpan(body, quoteStart, i - 1, darkBackground);
                        quoteStart = -1;
                    }
                }
            } else {
                // Remove extra spaces between > and first character in the line
                // > character will be removed too
                if (current != ' ' && lineTextStart == -1) {
                    lineTextStart = i;
                }
                if (current == '\n') {
                    body.delete(lineStart, lineTextStart);
                    i -= lineTextStart - lineStart;
                    if (i == lineStart) {
                        // Avoid empty lines because span over empty line can be hidden
                        body.insert(i++, " ");
                    }
                    lineStart = -1;
                    lineTextStart = -1;
                }
            }
            previous = current;
        }
        if (quoteStart >= 0) {
            // Apply spans to finishing open quote
            applyQuoteSpan(body, quoteStart, body.length(), darkBackground);
        }
        return startsWithQuote;
    }

    private void displayTextMessage(final SentReceivedViewHolder viewHolder, final Message message, boolean darkBackground, int type) {
        Context context = viewHolder.itemView.getContext();
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);

        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(context, R.style.TextAppearance_Conversations_Body1_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(context, R.style.TextAppearance_Conversations_Body1);
        }
        viewHolder.messageBody.setHighlightColor(ContextCompat.getColor(activity, darkBackground
                ? (type == SENT || !mUseGreenBackground ? R.color.black26 : R.color.grey800) : R.color.grey500));
        viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);

        if (message.getBody() != null) {
            final String nick = UIHelper.getMessageDisplayName(message);
            SpannableStringBuilder body = message.getMergedBody();
            boolean hasMeCommand = message.hasMeCommand();
            if (hasMeCommand) {
                body = body.replace(0, Message.ME_COMMAND.length(), nick + " ");
            }
            if (body.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
                body = new SpannableStringBuilder(body, 0, Config.MAX_DISPLAY_MESSAGE_CHARS);
                body.append("\u2026");
            }
            Message.MergeSeparator[] mergeSeparators = body.getSpans(0, body.length(), Message.MergeSeparator.class);
            for (Message.MergeSeparator mergeSeparator : mergeSeparators) {
                int start = body.getSpanStart(mergeSeparator);
                int end = body.getSpanEnd(mergeSeparator);
                body.setSpan(new DividerSpan(true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            boolean startsWithQuote = handleTextQuotes(body, darkBackground);
            if (!message.isPrivateMessage()) {
                if (hasMeCommand) {
                    body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, nick.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                String privateMarker;
                if (message.getStatus() <= Message.STATUS_RECEIVED) {
                    privateMarker = activity.getString(R.string.private_message);
                } else {
                    Jid cp = message.getCounterpart();
                    privateMarker = activity.getString(R.string.private_message_to, Strings.nullToEmpty(cp == null ? null : cp.getResource()));
                }
                body.insert(0, privateMarker);
                int privateMarkerIndex = privateMarker.length();
                if (startsWithQuote) {
                    body.insert(privateMarkerIndex, "\n\n");
                    body.setSpan(new DividerSpan(false), privateMarkerIndex, privateMarkerIndex + 2,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    body.insert(privateMarkerIndex, " ");
                }
                body.setSpan(new ForegroundColorSpan(getMessageTextColor(darkBackground, false)), 0, privateMarkerIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                body.setSpan(new StyleSpan(Typeface.BOLD), 0, privateMarkerIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (hasMeCommand) {
                    body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), privateMarkerIndex + 1,
                            privateMarkerIndex + 1 + nick.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            if (message.getConversation().getMode() == Conversation.MODE_MULTI && message.getStatus() == Message.STATUS_RECEIVED) {
                if (message.getConversation() instanceof Conversation) {
                    final Conversation conversation = (Conversation) message.getConversation();
                    Pattern pattern = NotificationService.generateNickHighlightPattern(conversation.getMucOptions().getActualNick());
                    Matcher matcher = pattern.matcher(body);
                    while (matcher.find()) {
                        body.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            Matcher matcher = Emoticons.getEmojiPattern(body).matcher(body);
            while (matcher.find()) {
                if (matcher.start() < matcher.end()) {
                    body.setSpan(new RelativeSizeSpan(1.2f), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            StylingHelper.format(body, viewHolder.messageBody.getCurrentTextColor());
            if (highlightedTerm != null) {
                StylingHelper.highlight(activity, body, highlightedTerm, StylingHelper.isDarkText(viewHolder.messageBody));
            }
            MyLinkify.addLinks(body, true);
            viewHolder.messageBody.setAutoLinkMask(0);
            viewHolder.messageBody.setText(EmojiWrapper.transform(body));
            viewHolder.messageBody.setMovementMethod(ClickableMovementMethod.getInstance());
        } else {
            viewHolder.messageBody.setText("");
            viewHolder.messageBody.setTextIsSelectable(false);
        }
    }

    private void displayDownloadableMessage(SentReceivedViewHolder viewHolder, final Message message, String text, final boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, darkBackground);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(text);
        viewHolder.download_button.setOnClickListener(v -> ConversationFragment.downloadFile(activity, message));
    }

    private void displayOpenableMessage(SentReceivedViewHolder viewHolder, final Message message, final boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, darkBackground);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
        viewHolder.download_button.setOnClickListener(v -> openDownloadable(message));
    }

    private void displayLocationMessage(SentReceivedViewHolder viewHolder, final Message message, final boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, darkBackground);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.download_button.setText(R.string.show_location);
        viewHolder.download_button.setOnClickListener(v -> showLocation(message));
    }

    private void displayAudioMessage(SentReceivedViewHolder viewHolder, Message message, boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, darkBackground);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.GONE);
        final RelativeLayout audioPlayer = viewHolder.audioPlayer;
        audioPlayer.setVisibility(View.VISIBLE);
        AudioPlayer.ViewHolder.get(audioPlayer).setDarkBackground(darkBackground);
        this.audioPlayer.init(audioPlayer, message);
    }

    private void displayMediaPreviewMessage(SentReceivedViewHolder viewHolder, final Message message, final boolean darkBackground) {
        toggleWhisperInfo(viewHolder, message, darkBackground);
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.VISIBLE);
        final FileParams params = message.getFileParams();
        final float target = activity.getResources().getDimension(R.dimen.image_preview_width);
        final int scaledW;
        final int scaledH;
        if (Math.max(params.height, params.width) * metrics.density <= target) {
            scaledW = (int) (params.width * metrics.density);
            scaledH = (int) (params.height * metrics.density);
        } else if (Math.max(params.height, params.width) <= target) {
            scaledW = params.width;
            scaledH = params.height;
        } else if (params.width <= params.height) {
            scaledW = (int) (params.width / ((double) params.height / target));
            scaledH = (int) target;
        } else {
            scaledW = (int) target;
            scaledH = (int) (params.height / ((double) params.width / target));
        }
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(scaledW, scaledH);
        layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
        viewHolder.image.setLayoutParams(layoutParams);
        activity.loadBitmap(message, viewHolder.image);
        viewHolder.image.setOnClickListener(v -> openDownloadable(message));
    }

    private void toggleWhisperInfo(SentReceivedViewHolder viewHolder, final Message message, final boolean darkBackground) {
        if (message.isPrivateMessage()) {
            final String privateMarker;
            if (message.getStatus() <= Message.STATUS_RECEIVED) {
                privateMarker = activity.getString(R.string.private_message);
            } else {
                Jid cp = message.getCounterpart();
                privateMarker = activity.getString(R.string.private_message_to, Strings.nullToEmpty(cp == null ? null : cp.getResource()));
            }
            final SpannableString body = new SpannableString(privateMarker);
            body.setSpan(new ForegroundColorSpan(getMessageTextColor(darkBackground, false)), 0, privateMarker.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            body.setSpan(new StyleSpan(Typeface.BOLD), 0, privateMarker.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            viewHolder.messageBody.setText(body);
            viewHolder.messageBody.setVisibility(View.VISIBLE);
        } else {
            viewHolder.messageBody.setVisibility(View.GONE);
        }
    }

    private void loadMoreMessages(Conversation conversation) {
        conversation.setLastClearHistory(0, null);
        activity.xmppConnectionService.updateConversation(conversation);
        conversation.setHasMessagesLeftOnServer(true);
        conversation.setFirstMamReference(null);
        long timestamp = conversation.getLastMessageTransmitted().getTimestamp();
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }
        conversation.messagesLoaded.set(true);
        MessageArchiveService.Query query = activity.xmppConnectionService.getMessageArchiveService().query(conversation, new MamReference(0), timestamp, false);
        if (query != null) {
            Toast.makeText(activity, R.string.fetching_history_from_server, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(activity, R.string.not_fetching_history_retention_period, Toast.LENGTH_SHORT).show();
        }
    }
    
    public FileBackend getFileBackend() {
        return activity.xmppConnectionService.getFileBackend();
    }

    public void stopAudioPlayer() {
        audioPlayer.stop();
    }

    public void unregisterListenerInAudioPlayer() {
        audioPlayer.unregisterListener();
    }

    public void startStopPending() {
        audioPlayer.startStopPending();
    }

    public void openDownloadable(Message message) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ConversationFragment.registerPendingMessage(activity, message);
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, ConversationsActivity.REQUEST_OPEN_MESSAGE);
            return;
        }
        final DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        ViewUtil.view(activity, file);
    }

    private void showLocation(Message message) {
        for (Intent intent : GeoHelper.createGeoIntentsFromMessage(activity, message)) {
            if (intent.resolveActivity(activity.getBaseContext().getPackageManager()) != null) {
                activity.getBaseContext().startActivity(intent);
                return;
            }
        }
        Toast.makeText(activity, R.string.no_application_found_to_display_location, Toast.LENGTH_SHORT).show();
    }

    public void updatePreferences() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        this.mUseGreenBackground = p.getBoolean("use_green_background", activity.getResources().getBoolean(R.bool.use_green_background));
    }


    public void setHighlightedTerm(List<String> terms) {
        this.highlightedTerm = terms == null ? null : StylingHelper.filterHighlightedWords(terms);
    }

    public interface OnQuoteListener {
        void onQuote(String text);
    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(View v, Message message);
    }

    public abstract class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        abstract void bind(Message message);
    }

    public class SentReceivedViewHolder extends ViewHolder implements View.OnCreateContextMenuListener{

        protected LinearLayout message_box;
        protected ImageView contact_picture;
        protected Button download_button;
        protected ImageView indicator;
        public ImageView edit_indicator;
        protected ImageView image;
        protected TextView messageBody;
        protected TextView time;
        protected ImageView indicatorReceived;
        public RelativeLayout audioPlayer;

        private int type;
        private boolean quotable = false;

        public SentReceivedViewHolder(@NonNull View view, int messageType) {
            super(view);

            message_box = view.findViewById(R.id.message_box);
            contact_picture = view.findViewById(R.id.message_photo);
            download_button = view.findViewById(R.id.download_button);
            indicator = view.findViewById(R.id.security_indicator);
            edit_indicator = view.findViewById(R.id.edit_indicator);
            image = view.findViewById(R.id.message_image);
            messageBody = view.findViewById(R.id.message_body);
            time = view.findViewById(R.id.message_time);
            indicatorReceived = view.findViewById(R.id.indicator_received);
            audioPlayer = view.findViewById(R.id.audio_player);

            type = messageType;

            view.setOnCreateContextMenuListener(this);
        }

        public int getType() {
            return type;
        }
        public boolean isQuotable() { return quotable; }

        @Override
        void bind(Message message) {
            final boolean omemoEncryption = message.getEncryption() == Message.ENCRYPTION_AXOLOTL;
            final boolean isInValidSession = message.isValidInSession() && (!omemoEncryption || message.isTrusted());
            final Conversational conversation = message.getConversation();
            final Account account = conversation.getAccount();
            boolean darkBackground = type == RECEIVED && (!isInValidSession || mUseGreenBackground) || activity.isDarkTheme();

            quotable = message.isQuotable();

            AvatarWorkerTask.loadAvatar(message, contact_picture, R.dimen.avatar);
            resetClickListener(message_box, messageBody);

            contact_picture.setOnClickListener(v -> {
                if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
                    MessageAdapter.this.mOnContactPictureClickedListener
                            .onContactPictureClicked(message);
                }

            });
            contact_picture.setOnLongClickListener(v -> {
                if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
                    MessageAdapter.this.mOnContactPictureLongClickedListener
                            .onContactPictureLongClicked(v, message);
                    return true;
                } else {
                    return false;
                }
            });

            final Transferable transferable = message.getTransferable();
            final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(message);
            if (unInitiatedButKnownSize || message.isDeleted() || (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING)) {
                if (unInitiatedButKnownSize || transferable != null && transferable.getStatus() == Transferable.STATUS_OFFER) {
                    displayDownloadableMessage(this, message, activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)), darkBackground);
                } else if (transferable != null && transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
                    displayDownloadableMessage(this, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)), darkBackground);
                } else {
                    displayInfoMessage(this, UIHelper.getMessagePreview(activity, message).first, darkBackground);
                }
            } else if (message.isFileOrImage() && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
                if (message.getFileParams().width > 0 && message.getFileParams().height > 0) {
                    displayMediaPreviewMessage(this, message, darkBackground);
                } else if (message.getFileParams().runtime > 0) {
                    displayAudioMessage(this, message, darkBackground);
                } else {
                    displayOpenableMessage(this, message, darkBackground);
                }
            } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                if (account.isPgpDecryptionServiceConnected()) {
                    if (conversation instanceof Conversation && !account.hasPendingPgpIntent((Conversation) conversation)) {
                        displayInfoMessage(this, activity.getString(R.string.message_decrypting), darkBackground);
                    } else {
                        displayInfoMessage(this, activity.getString(R.string.pgp_message), darkBackground);
                    }
                } else {
                    displayInfoMessage(this, activity.getString(R.string.install_openkeychain), darkBackground);
                    message_box.setOnClickListener((view) -> { activity.showInstallPgpDialog(); });
                    messageBody.setOnClickListener((view) -> { activity.showInstallPgpDialog(); });
                }
            } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                displayInfoMessage(this, activity.getString(R.string.decryption_failed), darkBackground);
            } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
                displayInfoMessage(this, activity.getString(R.string.not_encrypted_for_this_device), darkBackground);
            } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
                displayInfoMessage(this, activity.getString(R.string.omemo_decryption_failed), darkBackground);
            } else {
                if (message.isGeoUri()) {
                    displayLocationMessage(this, message, darkBackground);
                } else if (message.bodyIsOnlyEmojis() && message.getType() != Message.TYPE_PRIVATE) {
                    displayEmojiMessage(this, message.getBody().trim(), darkBackground);
                } else if (message.treatAsDownloadable()) {
                    try {
                        URL url = new URL(message.getBody());
                        if (P1S3UrlStreamHandler.PROTOCOL_NAME.equalsIgnoreCase(url.getProtocol())) {
                            displayDownloadableMessage(this,
                                    message,
                                    activity.getString(R.string.check_x_filesize,
                                            UIHelper.getFileDescriptionString(activity, message)),
                                    darkBackground);
                        } else {
                            displayDownloadableMessage(this,
                                    message,
                                    activity.getString(R.string.check_x_filesize_on_host,
                                            UIHelper.getFileDescriptionString(activity, message),
                                            url.getHost()),
                                    darkBackground);
                        }
                    } catch (Exception e) {
                        displayDownloadableMessage(this,
                                message,
                                activity.getString(R.string.check_x_filesize,
                                        UIHelper.getFileDescriptionString(activity, message)),
                                darkBackground);
                    }
                } else {
                    displayTextMessage(this, message, darkBackground, type);
                }
            }

            displayStatus(this, message, type, darkBackground);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            v.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));
            Message m = mMessages.get(getAdapterPosition());
            final Transferable t = m.getTransferable();
            Message relevantForCorrection = m;
            while (relevantForCorrection.mergeable(relevantForCorrection.next())) {
                relevantForCorrection = relevantForCorrection.next();
            }
            if (m.getType() != Message.TYPE_STATUS && m.getType() != Message.TYPE_RTP_SESSION) {

                if (m.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE || m.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
                    return;
                }

                if (m.getStatus() == Message.STATUS_RECEIVED && t != null && (t.getStatus() == Transferable.STATUS_CANCELLED || t.getStatus() == Transferable.STATUS_FAILED)) {
                    return;
                }

                final boolean deleted = m.isDeleted();
                final boolean encrypted = m.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED
                        || m.getEncryption() == Message.ENCRYPTION_PGP;
                final boolean receiving = m.getStatus() == Message.STATUS_RECEIVED && (t instanceof JingleFileTransferConnection || t instanceof HttpDownloadConnection);
                activity.getMenuInflater().inflate(R.menu.message_context, menu);
                menu.setHeaderTitle(R.string.message_options);
                MenuItem openWith = menu.findItem(R.id.open_with);
                MenuItem copyMessage = menu.findItem(R.id.copy_message);
                MenuItem copyLink = menu.findItem(R.id.copy_link);
                MenuItem quoteMessage = menu.findItem(R.id.quote_message);
                MenuItem retryDecryption = menu.findItem(R.id.retry_decryption);
                MenuItem correctMessage = menu.findItem(R.id.correct_message);
                MenuItem shareWith = menu.findItem(R.id.share_with);
                MenuItem sendAgain = menu.findItem(R.id.send_again);
                MenuItem copyUrl = menu.findItem(R.id.copy_url);
                MenuItem downloadFile = menu.findItem(R.id.download_file);
                MenuItem cancelTransmission = menu.findItem(R.id.cancel_transmission);
                MenuItem deleteFile = menu.findItem(R.id.delete_file);
                MenuItem showErrorMessage = menu.findItem(R.id.show_error_message);

                // This way we can use a closure to have access to the message
                shareWith.setOnMenuItemClickListener((menuItem) -> {
                    ShareUtil.share(activity, m);
                    return true;
                });
                copyLink.setOnMenuItemClickListener((menuItem) -> {
                    ShareUtil.copyLinkToClipboard(activity, m);
                    return true;
                });
                copyMessage.setOnMenuItemClickListener((menuItem) -> {
                    ShareUtil.copyToClipboard(activity, m);
                    return true;
                });
                copyUrl.setOnMenuItemClickListener((menuItem) -> {
                    ShareUtil.copyUrlToClipboard(activity, m);
                    return true;
                });
                correctMessage.setOnMenuItemClickListener((menuItem) -> {
                    if (mFragment != null && mFragment instanceof ConversationFragment) {
                        ((ConversationFragment) mFragment).correctMessage(m);
                    }
                    return true;
                });
                quoteMessage.setOnMenuItemClickListener((menuItem) -> {
                    if (mFragment != null && mFragment instanceof ConversationFragment) {
                        ((ConversationFragment) mFragment).quoteMessage(m);
                    }
                    return true;
                });
                sendAgain.setOnMenuItemClickListener((menuItem) -> {
                    if (mFragment != null && mFragment instanceof ConversationFragment) {
                        ((ConversationFragment) mFragment).resendMessage(m);
                    }
                    return true;
                });
                downloadFile.setOnMenuItemClickListener((menuItem) -> {
                    if (mFragment != null && mFragment instanceof ConversationFragment) {
                        ((ConversationFragment) mFragment).startDownloadable(m);
                    }
                    return true;
                });
                cancelTransmission.setOnMenuItemClickListener((menuItem) -> {
                    if (mFragment != null && mFragment instanceof ConversationFragment) {
                        ((ConversationFragment) mFragment).cancelTransmission(m);
                    }
                    return true;
                });
                retryDecryption.setOnMenuItemClickListener((menuItem) -> {
                    if (mFragment != null && mFragment instanceof ConversationFragment) {
                        ((ConversationFragment) mFragment).retryDecryption(m);
                    }
                    return true;
                });
                deleteFile.setOnMenuItemClickListener((menuItem) -> {
                    if (mFragment != null && mFragment instanceof ConversationFragment) {
                        ((ConversationFragment) mFragment).deleteFile(m);
                    }
                    return true;
                });
                showErrorMessage.setOnMenuItemClickListener((menuItem) -> {
                    if (mFragment != null && mFragment instanceof ConversationFragment) {
                        ((ConversationFragment) mFragment).showErrorMessage(m);
                    }
                    return true;
                });
                openWith.setOnMenuItemClickListener((menuItem) -> {
                    if (mFragment != null && mFragment instanceof ConversationFragment) {
                        ((ConversationFragment) mFragment).openWith(m);
                    }
                    return true;
                });
                final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(m);
                final boolean showError = m.getStatus() == Message.STATUS_SEND_FAILED && m.getErrorMessage() != null && !Message.ERROR_MESSAGE_CANCELLED.equals(m.getErrorMessage());
                if (!m.isFileOrImage() && !encrypted && !m.isGeoUri() && !m.treatAsDownloadable() && !unInitiatedButKnownSize && t == null) {
                    copyMessage.setVisible(true);
                    quoteMessage.setVisible(!showError && MessageUtils.prepareQuote(m).length() > 0);
                    String body = m.getMergedBody().toString();
                    if (ShareUtil.containsXmppUri(body)) {
                        copyLink.setTitle(R.string.copy_jabber_id);
                        copyLink.setVisible(true);
                    } else if (Patterns.AUTOLINK_WEB_URL.matcher(body).find()) {
                        copyLink.setVisible(true);
                    }
                }
                if (m.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED && !deleted) {
                    retryDecryption.setVisible(true);
                }
                if (!showError
                        && relevantForCorrection.getType() == Message.TYPE_TEXT
                        && !m.isGeoUri()
                        && relevantForCorrection.isLastCorrectableMessage()
                        && m.getConversation() instanceof Conversation) {
                    correctMessage.setVisible(true);
                }
                if ((m.isFileOrImage() && !deleted && !receiving) || (m.getType() == Message.TYPE_TEXT && !m.treatAsDownloadable()) && !unInitiatedButKnownSize && t == null) {
                    shareWith.setVisible(true);
                }
                if (m.getStatus() == Message.STATUS_SEND_FAILED) {
                    sendAgain.setVisible(true);
                }
                if (m.hasFileOnRemoteHost()
                        || m.isGeoUri()
                        || m.treatAsDownloadable()
                        || unInitiatedButKnownSize
                        || t instanceof HttpDownloadConnection) {
                    copyUrl.setVisible(true);
                }
                if (m.isFileOrImage() && deleted && m.hasFileOnRemoteHost()) {
                    downloadFile.setVisible(true);
                    downloadFile.setTitle(activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, m)));
                }
                final boolean waitingOfferedSending = m.getStatus() == Message.STATUS_WAITING
                        || m.getStatus() == Message.STATUS_UNSEND
                        || m.getStatus() == Message.STATUS_OFFERED;
                final boolean cancelable = (t != null && !deleted) || waitingOfferedSending && m.needsUploading();
                if (cancelable) {
                    cancelTransmission.setVisible(true);
                }
                if (m.isFileOrImage() && !deleted && !cancelable) {
                    String path = m.getRelativeFilePath();
                    if (path == null || !path.startsWith("/") || FileBackend.isInDirectoryThatShouldNotBeScanned(getActivity(), path)) {
                        deleteFile.setVisible(true);
                        deleteFile.setTitle(activity.getString(R.string.delete_x_file, UIHelper.getFileDescriptionString(activity, m)));
                    }
                }
                if (showError) {
                    showErrorMessage.setVisible(true);
                }
                final String mime = m.isFileOrImage() ? m.getMimeType() : null;
                if ((m.isGeoUri() && GeoHelper.openInOsmAnd(getActivity(), m)) || (mime != null && mime.startsWith("audio/"))) {
                    openWith.setVisible(true);
                }
            }
        }
    }

    public class RTPViewHolder extends ViewHolder {

        protected TextView status_message;
        protected LinearLayout message_box;
        protected ImageView indicatorReceived;

        public RTPViewHolder(@NonNull View view) {
            super(view);

            status_message = view.findViewById(R.id.message_body);
            message_box = view.findViewById(R.id.message_box);
            indicatorReceived = view.findViewById(R.id.indicator_received);
        }

        @Override
        void bind(Message message) {
            final boolean isDarkTheme = activity.isDarkTheme();
            final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
            final RtpSessionStatus rtpSessionStatus = RtpSessionStatus.of(message.getBody());
            final long duration = rtpSessionStatus.duration;
            if (received) {
                if (duration > 0) {
                    status_message.setText(activity.getString(R.string.incoming_call_duration, TimeFrameUtils.resolve(activity, duration)));
                } else if (rtpSessionStatus.successful) {
                    status_message.setText(R.string.incoming_call);
                } else {
                    status_message.setText(activity.getString(R.string.incoming_call_duration, UIHelper.readableTimeDifferenceFull(activity, message.getTimeSent())));
                }
            } else {
                if (duration > 0) {
                    status_message.setText(activity.getString(R.string.outgoing_call_duration, TimeFrameUtils.resolve(activity, duration)));
                } else {
                    status_message.setText(R.string.outgoing_call);
                }
            }
            indicatorReceived.setImageResource(RtpSessionStatus.getDrawable(received, rtpSessionStatus.successful, isDarkTheme));
            indicatorReceived.setAlpha(isDarkTheme ? 0.7f : 0.57f);
            message_box.setBackgroundResource(isDarkTheme ? R.drawable.date_bubble_grey : R.drawable.date_bubble_white);
        }
    }

    public class DateSeparatorViewHolder extends ViewHolder {

        protected TextView status_message;
        protected LinearLayout message_box;
        protected ImageView indicatorReceived;

        public DateSeparatorViewHolder(@NonNull View view) {
            super(view);

            status_message = view.findViewById(R.id.message_body);
            message_box = view.findViewById(R.id.message_box);
            indicatorReceived = view.findViewById(R.id.indicator_received);
        }

        @Override
        void bind(Message message) {
            if (UIHelper.today(message.getTimeSent())) {
                status_message.setText(R.string.today);
            } else if (UIHelper.yesterday(message.getTimeSent())) {
                status_message.setText(R.string.yesterday);
            } else {
                status_message.setText(DateUtils.formatDateTime(activity, message.getTimeSent(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
            }
            message_box.setBackgroundResource(activity.isDarkTheme() ? R.drawable.date_bubble_grey : R.drawable.date_bubble_white);
        }
    }

    public class StatusViewHolder extends ViewHolder {

        protected ImageView contact_picture;
        protected TextView status_message;
        public Button load_more_messages;

        public StatusViewHolder(@NonNull View view) {
            super(view);

            contact_picture = view.findViewById(R.id.message_photo);
            status_message = view.findViewById(R.id.status_message);
            load_more_messages = view.findViewById(R.id.load_more_messages);
        }

        @Override
        void bind(Message message) {
            final Conversational conversation = message.getConversation();
            if ("LOAD_MORE".equals(message.getBody())) {
                status_message.setVisibility(View.GONE);
                contact_picture.setVisibility(View.GONE);
                load_more_messages.setVisibility(View.VISIBLE);
                load_more_messages.setOnClickListener(v -> loadMoreMessages((Conversation) message.getConversation()));
            } else {
                status_message.setVisibility(View.VISIBLE);
                load_more_messages.setVisibility(View.GONE);
                status_message.setText(message.getBody());
                boolean showAvatar;
                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    showAvatar = true;
                    AvatarWorkerTask.loadAvatar(message, contact_picture, R.dimen.avatar_on_status_message);
                } else if (message.getCounterpart() != null || message.getTrueCounterpart() != null || (message.getCounterparts() != null && message.getCounterparts().size() > 0)) {
                    showAvatar = true;
                    AvatarWorkerTask.loadAvatar(message, contact_picture, R.dimen.avatar_on_status_message);
                } else {
                    showAvatar = false;
                }
                if (showAvatar) {
                    contact_picture.setAlpha(0.5f);
                    contact_picture.setVisibility(View.VISIBLE);
                } else {
                    contact_picture.setVisibility(View.GONE);
                }
            }
        }
    }
}
