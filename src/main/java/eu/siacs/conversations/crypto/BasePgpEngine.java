package eu.siacs.conversations.crypto;

import android.app.PendingIntent;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.UiCallback;

public interface BasePgpEngine {
    void decrypt(final Message message, final UiCallback<Message> callback);

    long fetchKeyId(Account account, String status, String signature);

    void chooseKey(final Account account, final UiCallback<Account> callback);

    void hasKey(final Contact contact, final UiCallback<Contact> callback);

    PendingIntent getIntentForKey(Contact contact);
    PendingIntent getIntentForKey(Account account, long pgpKeyId);
}
