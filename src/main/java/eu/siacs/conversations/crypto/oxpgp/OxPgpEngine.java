package eu.siacs.conversations.crypto.oxpgp;

import android.app.PendingIntent;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.BasePgpEngine;
import eu.siacs.conversations.crypto.Xep27PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.XmlReader;

public class OxPgpEngine implements BasePgpEngine {
    private OpenPgpApi mApi;
    private XmppConnectionService mXmppConnectionService;

    public static final String ELEMENT_SIGNCRYPT_NAME = "signcrypt";
    public static final String ELEMENT_SIGNCRYPT_NS = Constants.PGP_NS;
    public static final String ELEMENT_OPENPGP_NAME = "openpgp";
    public static final String ELEMENT_OPENPGP_NS = Constants.PGP_NS;
    public static final String ELEMENT_PAYLOAD_NAME = "payload";
    public static final String ELEMENT_PAYLOAD_BODY_NAME = "body";
    public static final String ELEMENT_PAYLOAD_BODY_NS = "jabber:client";

    private static final int RPAD_MAX_LENGTH = 50;

    private static final int BASE64_ENCODING_FLAG = Base64.DEFAULT;

    public OxPgpEngine(OpenPgpApi api, XmppConnectionService service) {
        this.mApi = api;
        this.mXmppConnectionService = service;
    }

    @Override
    public void hasKey(final Contact contact, final UiCallback<Contact> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getOxPgpKeyId());
        mApi.executeApiAsync(params, null, null, new OpenPgpApi.IOpenPgpCallback() {

            @Override
            public void onReturn(Intent result) {
                switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                    case OpenPgpApi.RESULT_CODE_SUCCESS:
                        callback.success(contact);
                        return;
                    case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                        callback.userInputRequried((PendingIntent) result
                                        .getParcelableExtra(OpenPgpApi.RESULT_INTENT),
                                contact);
                        return;
                    case OpenPgpApi.RESULT_CODE_ERROR:
                        callback.error(R.string.openpgp_error, contact);
                }
            }
        });
    }

    @Override
    public PendingIntent getIntentForKey(Contact contact) {
        // TODO: PHILIP
        throw new UnsupportedOperationException("Work in progress");
    }

    @Override
    public PendingIntent getIntentForKey(Account account, long pgpKeyId) {
        // TODO: PHILIP
        throw new UnsupportedOperationException("Work in progress");
    }

    public void signAndEncrypt(final Message message, final UiCallback<Message> callback) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_SIGN_AND_ENCRYPT);
        params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, false);
        params.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID,
                message.getConversation().getAccount().getOxPgpId());
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
            final String toEncryptBody = getSingCryptPacket(message);

            InputStream is = new ByteArrayInputStream(toEncryptBody.getBytes());
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            mApi.executeApiAsync(params, is, os, new OpenPgpApi.IOpenPgpCallback() {

                @Override
                public void onReturn(Intent result) {

                    notifyPgpDecryptionService(message.getConversation().getAccount(),
                            OpenPgpApi.ACTION_ENCRYPT, result);

                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                            OpenPgpApi.RESULT_CODE_ERROR)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            try {
                                os.flush();
                                byte[] encData = os.toByteArray();
                                Log.d("PHILIP", "enc armor check: " + os);
                                String base64Enc = Base64.encodeToString(encData,
                                        BASE64_ENCODING_FLAG);
                                Log.d("PHILIP", "OXpgpengine signAndEncrypt b64: " + base64Enc);
                                message.setEncryptedBody(base64Enc);
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
            throw new UnsupportedOperationException(
                    "OX does not handle image or file sending yet!");
            /*
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
                        notifyPgpDecryptionService(message.getConversation().getAccount(),
                                OpenPgpApi.ACTION_ENCRYPT, result);
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
            */
        }
    }

    @Override
    public long fetchKeyId(Account account, String status, String signature) {
        // TODO: PHILIP
        return new Xep27PgpEngine(mApi, mXmppConnectionService).fetchKeyId(account,
                status, signature);
    }

    @Override
    public void chooseKey(Account account, UiCallback<Account> callback) {
        // TODO: PHILIP
        new Xep27PgpEngine(mApi, mXmppConnectionService).chooseKey(account, callback);
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
        Element toElement = new Element("to")
                .setAttribute("jid", message.getConversation().getJid().toBareJidString());

        // timestamp is a MUST
        Date currDate = Calendar.getInstance().getTime();
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(currDate);
        String time = new SimpleDateFormat("HH:mm:ss.SSSZZZZZ", Locale.US).format(currDate);
        Element timestampEle = new Element("time");
        timestampEle.setAttribute("stamp", date + "T" + time);

        // rpad is a SHOULD, to prevent determination of message length
        Element rpadEle = new Element("rpad");
        rpadEle.setContent(getRandomPadding());

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

        Log.d("PHILIP", "signcrypt: " + signCryptElement);

        return signCryptElement.toString();
    }

    private void notifyPgpDecryptionService(Account account, String action, final Intent result) {
        // TODO: PHILIP
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
     * Expects message body to be set to the inner XML of the <openpgp> tag
     *
     * @param message  message whose body contains the inner XML of the <openpgp> tag
     * @param callback callback to the UI in case input is required/success/error
     */
    @Override
    public void decrypt(final Message message, final UiCallback<Message> callback) {

        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        if (message.getType() == Message.TYPE_TEXT) {
            Log.d("PHILIP", "oxpgpengine to decrypt body: " + message.getBody());
            byte[] encrypted;
            try {
                encrypted = Base64.decode(message.getBody(), BASE64_ENCODING_FLAG);
            } catch (IllegalArgumentException e) {
                Log.e("PHILIP", "Error base64 decryption", e);
                callback.error(R.string.ox_pgp_invalid_base64, message);
                return;
            }

            InputStream is = new ByteArrayInputStream(encrypted);
            final OutputStream os = new ByteArrayOutputStream();

            mApi.executeApiAsync(params, is, os, new OpenPgpApi.IOpenPgpCallback() {

                @Override
                public void onReturn(Intent result) {
                    Log.d("PHILIP", "OnPGPEngine decrypt returned: " + result.getIntExtra(
                            OpenPgpApi.RESULT_CODE,
                            -1));
                    notifyPgpDecryptionService(message.getConversation().getAccount(),
                            OpenPgpApi.ACTION_DECRYPT_VERIFY, result);
                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
                            OpenPgpApi.RESULT_CODE_ERROR)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            try {
                                // check if signature is valid according to XEP
                                OpenPgpSignatureResult signatureResult
                                        = result.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
                                if (!isSignatureCorrect(signatureResult, message.getContact())) {
                                    Log.d("PHILIP", "invalid signature!");
                                    message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED_OX);
                                    message.setDecryptionFailureReason(
                                            R.string.ox_pgp_invalid_signature);
                                    // TODO PHILIP: Figure out a way to show errors properly;
                                    mXmppConnectionService.updateMessage(message);
                                    // not really a success
                                    callback.success(message);
                                    return;
                                }

                                // signature is valid, go ahead
                                os.flush();
                                Log.d("PHILIP", "decrypted: " + os.toString());
                                if (message.getEncryption() == Message.ENCRYPTION_PGP_OX) {
                                    message.setBody(extractBody(os.toString()));
                                    message.setEncryption(Message.ENCRYPTION_DECRYPTED_OX);
                                    final HttpConnectionManager manager
                                            = mXmppConnectionService.getHttpConnectionManager();
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

    /**
     * checks if a signature obtained is valid according to the XEP. This involves 2 checks:
     * 1. If contact public key id matches signer key id
     * 2. If one of the userIds of the signing key is xmpp:username@domain.com
     *
     * @param signatureResult the result containing signature data obtained from OpenkeyChain
     * @param contact         the user from whom the message was sent
     * @return true if signature is valid according to XEP, false otherwise
     */
    private boolean isSignatureCorrect(OpenPgpSignatureResult signatureResult, Contact contact) {
        // 1. Contact public key id matches signatureResult public key id
        if (contact.getOxPgpKeyId() != signatureResult.getKeyId()) {
            Log.d("PHILIP", "expected signing key id: " + contact.getOxPgpKeyId()
                    + ", found: " + signatureResult.getKeyId());
            return false;
        }

        final String expectedUserId = "xmpp:" + contact.getJid().toBareJidString();

            // TODO: PHILIP remove below loop
            for(String userId: signatureResult.getUserIds()) {
                Log.d("PHILIP", "oxPGPEngine: found userid: " + userId);
                if (userId.contains(expectedUserId)) {
                    return true;
                }
            }
            Log.d("PHILIP", "expected signing key to have userid: "
                    + expectedUserId + " not present");
            return false;
    }

    protected
    @NonNull
    String extractBody(String openpgpElement) throws IOException,
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
