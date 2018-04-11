package eu.siacs.conversations.ui.adapter;

import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.makeramen.roundedimageview.RoundedImageView;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ButtonInviteBinding;
import eu.siacs.conversations.databinding.ConferenceDetailsContactBinding;
import eu.siacs.conversations.databinding.ConferenceDetailsHeaderBinding;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.ui.ConferenceDetailsActivity;

/**
 * Created by mxf on 2018/4/5.
 */
public class ConferenceDetailsContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final float INACTIVE_ALPHA = 0.4684f; //compromise between dark and light theme
    private static final int TYPE_NORMAL = 0x2;
    private static final int TYPE_INVITE = 0x1;
    private static final int TYPE_HEADER = 0x0;

    private List<MucOptions.User> users = new ArrayList<>();
    private OnContactClickListener onContactClickListener;
    private ConferenceDetailsActivity activity;
    private View.OnClickListener inviteListener;
    private View.OnClickListener changeConferenceSettings;
    private View.OnClickListener editNickButtonClickListener;
    private View.OnClickListener notifyStatusClickListener;
    private boolean canInvite;
    private Conversation conversation;
    private boolean isOnline;

    public ConferenceDetailsContactAdapter(ConferenceDetailsActivity activity,OnContactClickListener onContactClickListener) {
        this.activity = activity;
        this.onContactClickListener = onContactClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_HEADER;
        } else if (position == users.size() + 1) {
            return TYPE_INVITE;
        }else {
            return TYPE_NORMAL;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder holder;
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_INVITE:
                ButtonInviteBinding buttonInviteBinding = DataBindingUtil.inflate(inflater,R.layout.button_invite, parent, false);
                holder = InviteViewHolder.get(buttonInviteBinding);
                break;
            case TYPE_HEADER:
                ConferenceDetailsHeaderBinding headerBinding = DataBindingUtil.inflate(inflater,R.layout.conference_details_header,parent,false);
                holder = HeaderViewHolder.get(headerBinding);
                break;
            default:
                ConferenceDetailsContactBinding contactBinding =
                        DataBindingUtil.inflate(inflater, R.layout.conference_details_contact, parent, false);
                holder = ContactViewHolder.get(contactBinding);
                break;
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        setupBackgroundAndMargin(viewHolder, position);
        int type = getItemViewType(position);
        if(type == TYPE_INVITE){
            ButtonInviteBinding binding = ((InviteViewHolder) viewHolder).binding;
            binding.invite.setOnClickListener(inviteListener);
        }else if(type == TYPE_HEADER){
            bindHeader((HeaderViewHolder) viewHolder);
        }else {
            bindContact((ContactViewHolder) viewHolder, position - 1);
        }
    }

    private void setupBackgroundAndMargin(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if(position == 0){
            return;
        }
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) viewHolder.itemView.getLayoutParams();
        int margin = (int) viewHolder.itemView.getResources().getDimension(R.dimen.activity_vertical_margin);
        layoutParams.leftMargin =  margin;
        layoutParams.rightMargin =  margin;
        int res;
        if (getItemCount() == 2 && position == 1) {
            res = R.drawable.background_contact_only_one;
            layoutParams.topMargin =  margin;
            layoutParams.bottomMargin =  margin;
        } else if (isFirstView(position)) {
            res = R.drawable.background_contact_first;
            layoutParams.topMargin =  margin;
            layoutParams.bottomMargin =  0;
            viewHolder.itemView.setPadding(margin, margin, margin, 0);
        } else if (isLastView(position)) {
            res = R.drawable.background_contact_last;
            layoutParams.topMargin =  0;
            layoutParams.bottomMargin =  margin;
        } else {
            layoutParams.topMargin =  0;
            layoutParams.bottomMargin =  0;
            res = R.drawable.background_contact_normal;
        }
        Drawable drawable = viewHolder.itemView.getResources().getDrawable(res);
        viewHolder.itemView.setBackground(drawable);
    }

    private void bindHeader(HeaderViewHolder viewHolder) {
        final MucOptions mucOptions = conversation.getMucOptions();
        final MucOptions.User self = mucOptions.getSelf();
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = conversation.getAccount().getJid().getLocal();
        } else {
            account = conversation.getAccount().getJid().asBareJid().toString();
        }
        viewHolder.binding.detailsAccount.setText(activity.getString(R.string.using_account, account));
        viewHolder.binding.yourPhoto.setImageBitmap(activity.avatarService().get(conversation.getAccount(),activity.getPixel(48)));
        viewHolder.binding.mucJabberid.setText(conversation.getJid().asBareJid().toString());
        viewHolder.binding.mucYourNick.setText(mucOptions.getActualNick());
        if (mucOptions.online()) {
            viewHolder.binding.mucSettings.setVisibility(View.VISIBLE);
            viewHolder.binding.mucInfoMore.setVisibility(activity.getAdvancedMode() ? View.VISIBLE : View.GONE);
            final String status = activity.getStatus(self);
            if (status != null) {
                viewHolder.binding.mucRole.setVisibility(View.VISIBLE);
                viewHolder.binding.mucRole.setText(status);
            } else {
                viewHolder.binding.mucRole.setVisibility(View.GONE);
            }
            if (mucOptions.membersOnly()) {
                viewHolder.binding.mucConferenceType.setText(R.string.private_conference);
            } else {
                viewHolder.binding.mucConferenceType.setText(R.string.public_conference);
            }
            if (mucOptions.mamSupport()) {
                viewHolder.binding.mucInfoMam.setText(R.string.server_info_available);
            } else {
                viewHolder.binding.mucInfoMam.setText(R.string.server_info_unavailable);
            }
            if (self.getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
                viewHolder.binding.changeConferenceButton.setVisibility(View.VISIBLE);
            } else {
                viewHolder.binding.changeConferenceButton.setVisibility(View.GONE);
            }
        } else {
            viewHolder.binding.mucInfoMore.setVisibility(View.GONE);
            viewHolder.binding.mucSettings.setVisibility(View.GONE);
        }

        viewHolder.binding.changeConferenceButton.setOnClickListener(changeConferenceSettings);
        viewHolder.binding.editNickButton.setOnClickListener(editNickButtonClickListener);
        viewHolder.binding.mucInfoMore.setVisibility(isOnline && activity.getAdvancedMode() ? View.VISIBLE : View.GONE);
        viewHolder.binding.notificationStatusButton.setOnClickListener(notifyStatusClickListener);

        int ic_notifications = activity.getThemeResource(R.attr.icon_notifications, R.drawable.ic_notifications_black_24dp);
        int ic_notifications_off = activity.getThemeResource(R.attr.icon_notifications_off, R.drawable.ic_notifications_off_black_24dp);
        int ic_notifications_paused = activity.getThemeResource(R.attr.icon_notifications_paused, R.drawable.ic_notifications_paused_black_24dp);
        int ic_notifications_none = activity.getThemeResource(R.attr.icon_notifications_none, R.drawable.ic_notifications_none_black_24dp);

        long mutedTill = conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
        if (mutedTill == Long.MAX_VALUE) {
            viewHolder.binding.notificationStatusText.setText(R.string.notify_never);
            viewHolder.binding.notificationStatusButton.setImageResource(ic_notifications_off);
        } else if (System.currentTimeMillis() < mutedTill) {
            viewHolder.binding.notificationStatusText.setText(R.string.notify_paused);
            viewHolder.binding.notificationStatusButton.setImageResource(ic_notifications_paused);
        } else if (conversation.alwaysNotify()) {
            viewHolder.binding.notificationStatusText.setText(R.string.notify_on_all_messages);
            viewHolder.binding.notificationStatusButton.setImageResource(ic_notifications);
        } else {
            viewHolder.binding.notificationStatusText.setText(R.string.notify_only_when_highlighted);
            viewHolder.binding.notificationStatusButton.setImageResource(ic_notifications_none);
        }

    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    private boolean isLastView(int position) {
        return position == getItemCount()-1;
    }

    private boolean isFirstView(int position) {
        return position == 1;
    }

    private void bindContact(@NonNull ContactViewHolder contactViewHolder, int position) {
        MucOptions.User user = users.get(position);
        View.OnClickListener listener = v -> {
            switch (v.getId()) {
                case R.id.key:
                    onContactClickListener.onKeyClick(user);
                    break;
                default:
                    onContactClickListener.onContactClick(user);
                    break;
            }
        };
        contactViewHolder.itemView.setOnClickListener(listener);
        activity.registerForContextMenu(contactViewHolder.itemView);
        contactViewHolder.itemView.setTag(user);
        if (activity.getAdvancedMode() && user.getPgpKeyId() != 0) {
            contactViewHolder.binding.key.setVisibility(View.VISIBLE);
            contactViewHolder.binding.key.setOnClickListener(listener);
            contactViewHolder.binding.key.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId()));
        }

        Contact contact = user.getContact();
        String name = user.getName();
        if (contact != null) {
           contactViewHolder.binding.contactDisplayName.setText(contact.getDisplayName());
           contactViewHolder.binding.contactJid.setText((name != null ? name + " \u2022 " : "") + activity.getStatus(user));
        } else {
           contactViewHolder.binding.contactDisplayName.setText(name == null ? "" : name);
           contactViewHolder.binding.contactJid.setText(activity.getStatus(user));
        }
        activity.loadAvatar(user, contactViewHolder.binding.contactPhoto);
        if (user.getRole() == MucOptions.Role.NONE) {
            contactViewHolder.binding.contactJid.setAlpha(INACTIVE_ALPHA);
            contactViewHolder.binding.key.setAlpha(INACTIVE_ALPHA);
            contactViewHolder.binding.contactDisplayName.setAlpha(INACTIVE_ALPHA);
            contactViewHolder.binding.contactPhoto.setAlpha(INACTIVE_ALPHA);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ContactViewHolder) {
            ContactViewHolder viewHolder = (ContactViewHolder) holder;
            RoundedImageView contactPhoto = viewHolder.binding.contactPhoto;
            Drawable drawable = contactPhoto.getDrawable();
            if (drawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                if(bitmap != null){
                    contactPhoto.setImageBitmap(null);
                    bitmap.recycle();
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        if (conversation == null) {
            return 0;
        }
        int itemCount = isOnline ? users.size() : 0;
        if (isOnline && canInvite) {
            itemCount++;
        } else {
            itemCount += 2;
        }
        return itemCount;
    }

    public void setCanInvite(boolean canInvite) {
        this.canInvite = canInvite;
    }

    public void setUsers(List<MucOptions.User> users) {
        this.users.clear();
        this.users.addAll(users);
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public void setInviteListener(View.OnClickListener inviteListener) {
        this.inviteListener = inviteListener;
    }

    public void setChangeConferenceSettings(View.OnClickListener changeConferenceSettings) {
        this.changeConferenceSettings = changeConferenceSettings;
    }

    public void setEditNickButtonClickListener(View.OnClickListener editNickButtonClickListener) {
        this.editNickButtonClickListener = editNickButtonClickListener;
    }

    public void setNotifyStatusClickListener(View.OnClickListener notifyStatusClickListener) {
        this.notifyStatusClickListener = notifyStatusClickListener;
    }

    public interface OnContactClickListener{
        void onContactClick(MucOptions.User user);

        void onKeyClick(MucOptions.User user);
    }

    public static class ContactViewHolder extends RecyclerView.ViewHolder{

        private ConferenceDetailsContactBinding binding;

        public ContactViewHolder(View itemView) {
            super(itemView);
        }

        public static ContactViewHolder get(ConferenceDetailsContactBinding binding){
            ContactViewHolder holder = new ContactViewHolder(binding.getRoot());
            holder.binding = binding;
            return holder;
        }
    }

    public static class InviteViewHolder extends RecyclerView.ViewHolder{

        private ButtonInviteBinding binding;

        public InviteViewHolder(View itemView) {
            super(itemView);
        }

        public static InviteViewHolder get(ButtonInviteBinding binding){
            InviteViewHolder holder = new InviteViewHolder(binding.getRoot());
            holder.binding = binding;
            return holder;
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder{
        private ConferenceDetailsHeaderBinding binding;

        public HeaderViewHolder(View itemView) {
            super(itemView);
        }

        public static HeaderViewHolder get(ConferenceDetailsHeaderBinding binding){
            HeaderViewHolder holder = new HeaderViewHolder(binding.getRoot());
            holder.binding = binding;
            return holder;
        }
    }
}
