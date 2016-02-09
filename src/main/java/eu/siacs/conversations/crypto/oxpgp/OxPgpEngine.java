package eu.siacs.conversations.crypto.oxpgp;

import android.app.PendingIntent;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.openintents.openpgp.util.OpenPgpApi;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.XmlReader;
import eu.siacs.conversations.xmpp.jid.Jid;

/**
 * Created by abrahamphilip on 6/2/16.
 */
public class OxPgpEngine {
    private OpenPgpApi mApi;
    private XmppConnectionService mXmppConnectionService;

    public static final String ELEMENT_SIGNCRYPT_NAME = "signcrypt";
    public static final String ELEMENT_SIGNCRYPT_NS = "urn:xmpp:openpgp:0";
    public static final String ELEMENT_OPENPGP_NAME = "openpgp";
    public static final String ELEMENT_OPENPGP_NS = "urn:xmpp:openpgp:0";
    public static final String ELEMENT_PAYLOAD_NAME = "payload";
    public static final String ELEMENT_PAYLOAD_BODY_NAME = "body";
    public static final String ELEMENT_PAYLOAD_BODY_NS = "jabber:client";

    private static final int RPAD_MAX_LENGTH = 50;

    private final String TAG = "OxPgpEngine";

    public OxPgpEngine(OpenPgpApi api, XmppConnectionService service) {
        this.mApi = api;
        this.mXmppConnectionService = service;
    }


    public void encrypt(final Message message, final UiCallback<Message> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_ENCRYPT);
        final Conversation conversation = message.getConversation();
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            long[] keys = {
                    conversation.getContact().getPgpKeyId(),
                    conversation.getAccount().getPgpId()
            };
            params.putExtra(OpenPgpApi.EXTRA_KEY_IDS, keys);
        } else {
            //params.putExtra(OpenPgpApi.EXTRA_KEY_IDS, conversation.getMucOptions().getPgpKeyIds());
            throw new UnsupportedOperationException("OXPGP does not support MUC yet");
        }

        if (!message.needsUploading()) {
            params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
            final String toEncryptBody = getSingCryptPacket(message);

            InputStream is = new ByteArrayInputStream(toEncryptBody.getBytes());
            final OutputStream os = new ByteArrayOutputStream();
            mApi.executeApiAsync(params, is, os, new OpenPgpApi.IOpenPgpCallback() {

                @Override
                public void onReturn(Intent result) {
                    notifyPgpDecryptionService(message.getConversation().getAccount(), OpenPgpApi.ACTION_ENCRYPT, result);
                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                            OpenPgpApi.RESULT_CODE_ERROR)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            try {
                                os.flush();
                                StringBuilder encryptedMessageBody = new StringBuilder();
                                String[] lines = os.toString().split("\n");
                                for (int i = 2; i < lines.length - 1; ++i) {
                                    if (!lines[i].contains("Version")) {
                                        encryptedMessageBody.append(lines[i].trim());
                                    }
                                }
                                message.setEncryptedBody(encryptedMessageBody.toString());
                                callback.success(message);
                            } catch (IOException e) {
                                callback.error(R.string.openpgp_error, message);
                            }

                            break;
                        case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                            callback.userInputRequried((PendingIntent) result
                                            .getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                    message);
                            break;
                        case OpenPgpApi.RESULT_CODE_ERROR:
                            callback.error(R.string.openpgp_error, message);
                            break;
                    }
                }
            });
        } else {
            try {
                DownloadableFile inputFile = this.mXmppConnectionService
                        .getFileBackend().getFile(message, true);
                DownloadableFile outputFile = this.mXmppConnectionService
                        .getFileBackend().getFile(message, false);
                outputFile.getParentFile().mkdirs();
                outputFile.createNewFile();
                final InputStream is = new FileInputStream(inputFile);
                final OutputStream os = new FileOutputStream(outputFile);
                mApi.executeApiAsync(params, is, os, new OpenPgpApi.IOpenPgpCallback() {

                    @Override
                    public void onReturn(Intent result) {
                        notifyPgpDecryptionService(message.getConversation().getAccount(), OpenPgpApi.ACTION_ENCRYPT, result);
                        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                                OpenPgpApi.RESULT_CODE_ERROR)) {
                            case OpenPgpApi.RESULT_CODE_SUCCESS:
                                try {
                                    os.flush();
                                } catch (IOException ignored) {
                                    //ignored
                                }
                                FileBackend.close(os);
                                callback.success(message);
                                break;
                            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                                callback.userInputRequried(
                                        (PendingIntent) result
                                                .getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                        message);
                                break;
                            case OpenPgpApi.RESULT_CODE_ERROR:
                                callback.error(R.string.openpgp_error, message);
                                break;
                        }
                    }
                });
            } catch (final IOException e) {
                callback.error(R.string.openpgp_error, message);
            }
        }
    }

    private String getSingCryptPacket(Message message) {
        String body;
        if (message.hasFileOnRemoteHost()) {
            body = message.getFileParams().url.toString();
        } else {
            body = message.getBody();
        }
        Element signCryptElement = new Element(ELEMENT_SIGNCRYPT_NAME, ELEMENT_SIGNCRYPT_NS);

        // to element MUST have a jid attribute which SHOULD be a bare JID
        Jid toJid = message.getConversation().getJid().toBareJid();
        Element toElement = new Element("to");
        toElement.setAttribute("jid", toJid.getLocalpart() + "@" + toJid.getDomainpart());

        // timestamp is a MUST
        Date currDate = Calendar.getInstance().getTime();
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(currDate);
        String time = new SimpleDateFormat("HH:mm:ss.SSSZZZZZ", Locale.US).format(currDate);
        Element timestampEle = new Element("time");
        timestampEle.setAttribute("stamp", date + "T" + time);

        // rpad is a SHOULD, to prevent determination of message length
        Element rpadEle = new Element("rpad");
        rpadEle.setContent(getRandomPadding());
        Log.d("PHILIP", "rpad:" + rpadEle.getContent());

        // payload contains stanza extension elements which would normally be considered children
        // of an unencrypted message
        Element payLoadEle = new Element(ELEMENT_PAYLOAD_NAME);
        Element bodyEle = new Element(ELEMENT_PAYLOAD_BODY_NAME, ELEMENT_PAYLOAD_BODY_NS);
        bodyEle.setContent(body);
        payLoadEle.addChild(bodyEle);

        signCryptElement.addChild(toElement);
        signCryptElement.addChild(timestampEle);
        signCryptElement.addChild(rpadEle);
        signCryptElement.addChild(payLoadEle);

        return signCryptElement.toString();
    }

    private void notifyPgpDecryptionService(Account account, String action, final Intent result) {
        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
            case OpenPgpApi.RESULT_CODE_SUCCESS:
                if (OpenPgpApi.ACTION_SIGN.equals(action)) {
                    account.getPgpDecryptionService().onKeychainUnlocked();
                }
                break;
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                account.getPgpDecryptionService().onKeychainLocked();
                break;
        }
    }

    /**
     * this is to prevent approximation of message length, which can be used to detect short
     * messages like yes or no
     *
     * @return random length string
     */
    private static String getRandomPadding() {
        Random generator = new Random();
        StringBuilder randomStringBuilder = new StringBuilder();
        int randomLength = generator.nextInt(RPAD_MAX_LENGTH);
        // TODO: does repChar really need to be random?
        char repChar = 'a';
        for (int i = 0; i < randomLength; i++) {
            randomStringBuilder.append(repChar);
        }
        return randomStringBuilder.toString();
    }

    /**
     * Expects message body to be set to the contents of the <openpgp> tag
     *
     * @param message
     * @param callback
     */
    public void decrypt(final Message message,
                        final UiCallback<Message> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        if (message.getType() == Message.TYPE_TEXT) {
            InputStream is = new ByteArrayInputStream(message.getBody().getBytes());
            final OutputStream os = new ByteArrayOutputStream();

            mApi.executeApiAsync(params, is, os, new OpenPgpApi.IOpenPgpCallback() {

                @Override
                public void onReturn(Intent result) {
                    notifyPgpDecryptionService(message.getConversation().getAccount(),
                            OpenPgpApi.ACTION_DECRYPT_VERIFY, result);
                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                            OpenPgpApi.RESULT_CODE_ERROR)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            try {
                                os.flush();
                                if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                                    message.setBody(extractBody(os.toString()));
                                    message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                                    final HttpConnectionManager manager = mXmppConnectionService.getHttpConnectionManager();
                                    if (message.trusted()
                                            && message.treatAsDownloadable() != Message.Decision.NEVER
                                            && manager.getAutoAcceptFileSize() > 0) {
                                        manager.createNewDownloadConnection(message);
                                    }
                                    mXmppConnectionService.updateMessage(message);
                                    callback.success(message);
                                }
                            } catch (IOException | XmlPullParserException e) {
                                callback.error(R.string.openpgp_error, message);
                                return;
                            }

                            return;
                        case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                            callback.userInputRequried((PendingIntent) result
                                            .getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                    message);
                            return;
                        case OpenPgpApi.RESULT_CODE_ERROR:
                            callback.error(R.string.openpgp_error, message);
                    }
                }
            });
        } else if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE) {
            // TODO: PHILIP get this working
            throw new UnsupportedOperationException("Images and files not handled yet");
            /*
            try {
                final DownloadableFile inputFile = this.mXmppConnectionService
                        .getFileBackend().getFile(message, false);
                final DownloadableFile outputFile = this.mXmppConnectionService
                        .getFileBackend().getFile(message, true);
                outputFile.getParentFile().mkdirs();
                outputFile.createNewFile();
                InputStream is = new FileInputStream(inputFile);
                OutputStream os = new FileOutputStream(outputFile);
                mApi.executeApiAsync(params, is, os, new OpenPgpApi.IOpenPgpCallback() {

                    @Override
                    public void onReturn(Intent result) {
                        notifyPgpDecryptionService(message.getConversation().getAccount(), OpenPgpApi.ACTION_DECRYPT_VERIFY, result);
                        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                                OpenPgpApi.RESULT_CODE_ERROR)) {
                            case OpenPgpApi.RESULT_CODE_SUCCESS:
                                URL url = message.getFileParams().url;
                                mXmppConnectionService.getFileBackend().updateFileParams(message,url);
                                message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                                OxPgpEngine.this.mXmppConnectionService
                                        .updateMessage(message);
                                inputFile.delete();
                                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                intent.setData(Uri.fromFile(outputFile));
                                mXmppConnectionService.sendBroadcast(intent);
                                callback.success(message);
                                return;
                            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                                callback.userInputRequried(
                                        (PendingIntent) result
                                                .getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                        message);
                                return;
                            case OpenPgpApi.RESULT_CODE_ERROR:
                                callback.error(R.string.openpgp_error, message);
                        }
                    }
                });
            } catch (final IOException e) {
                callback.error(R.string.error_decrypting_file, message);
            }
                */

        }
    }

    protected @NonNull String extractBody(String openpgpElement) throws IOException,
            XmlPullParserException {
        XmlReader tagReader = new XmlReader();
        Log.d("PHILIP", "body extraction:" + openpgpElement);
        InputStream is = new ByteArrayInputStream(openpgpElement.getBytes());
        tagReader.setInputStream(is);
        Tag openpgp = tagReader.readTag();
        Element signCrypt = tagReader.readElement(openpgp);
        if (signCrypt == null) {
            throw new XmlPullParserException("No signcrypt element!");
        }
        Element payload = signCrypt.findChild(ELEMENT_PAYLOAD_NAME);
        if (payload == null) {
            throw new XmlPullParserException("No payload element inside signcrypt!");
        }
        Element nestedBody = payload.findChild(ELEMENT_PAYLOAD_BODY_NAME);
        if (nestedBody == null) {
            throw new XmlPullParserException("No body inside payload element!");
        }
        return nestedBody.getContent();
    }
}
