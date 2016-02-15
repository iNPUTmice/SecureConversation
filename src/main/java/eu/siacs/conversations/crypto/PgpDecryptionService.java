package eu.siacs.conversations.crypto;

import android.app.PendingIntent;
import android.util.Log;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.UiCallback;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PgpDecryptionService {

	private final XmppConnectionService xmppConnectionService;
	private final ConcurrentHashMap<String, List<Message>> messages = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Boolean> decryptingMessages = new ConcurrentHashMap<>();
	private Boolean keychainLocked = false;
	private final Object keychainLockedLock = new Object();

	public PgpDecryptionService(XmppConnectionService xmppConnectionService) {
		this.xmppConnectionService = xmppConnectionService;
	}

	public void add(Message message) {
		if (isRunning()) {
			decryptDirectly(message);
		} else {
			store(message);
		}
	}

	public void addAll(List<Message> messagesList) {
		if (!messagesList.isEmpty()) {
			String conversationUuid = messagesList.get(0).getConversation().getUuid();
			if (!messages.containsKey(conversationUuid)) {
				List<Message> list = Collections.synchronizedList(new LinkedList<Message>());
				messages.put(conversationUuid, list);
			}
			synchronized (messages.get(conversationUuid)) {
				messages.get(conversationUuid).addAll(messagesList);
			}
			decryptAllMessages();
		}
	}

	public void onKeychainUnlocked() {
		synchronized (keychainLockedLock) {
			keychainLocked = false;
		}
		decryptAllMessages();
	}

	public void onKeychainLocked() {
		synchronized (keychainLockedLock) {
			keychainLocked = true;
		}
		xmppConnectionService.updateConversationUi();
	}

	public void onOpenPgpServiceBound() {
		decryptAllMessages();
	}

	public boolean isRunning() {
		synchronized (keychainLockedLock) {
			return !keychainLocked;
		}
	}

	private void store(Message message) {
		if (messages.containsKey(message.getConversation().getUuid())) {
			messages.get(message.getConversation().getUuid()).add(message);
		} else {
			List<Message> messageList = Collections.synchronizedList(new LinkedList<Message>());
			messageList.add(message);
			messages.put(message.getConversation().getUuid(), messageList);
		}
	}

	private void decryptAllMessages() {
		for (String uuid : messages.keySet()) {
			decryptMessages(uuid);
		}
	}

	private void decryptMessages(final String uuid) {
		synchronized (decryptingMessages) {
			Boolean decrypting = decryptingMessages.get(uuid);
			if ((decrypting != null && !decrypting) || decrypting == null) {
				decryptingMessages.put(uuid, true);
				decryptMessage(uuid);
			}
		}
	}

	private void decryptMessage(final String uuid) {
		Message message = null;
		synchronized (messages.get(uuid)) {
			while (!messages.get(uuid).isEmpty()) {
				if (messages.get(uuid).get(0).isStillEncryptedPgp()) {
					if (isRunning()) {
						message = messages.get(uuid).remove(0);
					}
					break;
				} else {
					messages.get(uuid).remove(0);
				}
			}
			boolean decryptStarted = decryptAppropriately(message, new UiCallback<Message>() {

					@Override
					public void userInputRequried(PendingIntent pi, Message message) {
						// TODO: PHILIP is this necessary
						// add message to the end of the list, to prevent an erroneous message
						// from preventing the decryption of any further messages
						messages.get(uuid).add(message);
						decryptingMessages.put(uuid, false);
					}

					@Override
					public void success(Message message) {
						Log.d("PHILIP", "PgpDecryptionService decryptMessage success!");
						xmppConnectionService.updateConversationUi();
						decryptMessage(uuid);
					}

					@Override
					public void error(int error, Message message) {
						Log.d("PHILIP", "pgpDecryptionService: failed - status" + message.getStatus());
						message.setAppropriateEncryptionFailed();
						message.setDecryptionFailureReason(error);
						xmppConnectionService.updateConversationUi();
						decryptMessage(uuid);
					}
				});
            if (!decryptStarted) {
                decryptingMessages.put(uuid, false);
            }
        }
    }

	private void decryptDirectly(Message message) {
		Log.d("PHILIP", "decrypting message directly!");
		decryptAppropriately(message, new UiCallback<Message>() {

			@Override
			public void userInputRequried(PendingIntent pi, Message message) {
				Log.d("PHILIP", "PgpDecryptionService: " + "userInputRequried");

				store(message);
			}

			@Override
			public void success(Message message) {
				xmppConnectionService.updateConversationUi();
				xmppConnectionService.getNotificationService().updateNotification(false);
			}

			@Override
			public void error(int error, Message message) {
				Log.d("PHILIP", "pgpDecryptionService: failed " + message.getStatus());
				message.setAppropriateEncryptionFailed();
				xmppConnectionService.updateConversationUi();
			}
		});
	}

	/**
	 * Decrypts XEP27 PGP and OX PGP messages
	 * @param message message type be either ENCRYPTION_PGP or ENCRYPTION_PGP_OX, throws Exception
	 *                otherwise
	 * @return false if the required engine was null in xmppConnectionService, true otherwise
	 */
	private boolean decryptAppropriately(Message message, UiCallback<Message> callback) {
        if (message == null || xmppConnectionService == null) {
            return false;
        }
		BasePgpEngine pgpEngine;
		switch (message.getEncryption()) {
			case Message.ENCRYPTION_PGP:
				Log.d("PHILIP", "decrypting: XEP27");
				pgpEngine = xmppConnectionService.getXep27PgpEngine();
				break;
			case Message.ENCRYPTION_PGP_OX:
				Log.d("PHILIP", "decrypting: OX");
				pgpEngine = xmppConnectionService.getOxPgpEngine();
				break;
			default:
				throw new UnsupportedOperationException(
						"Non PGP message received in PgpDecryptionService - Type: "
								+ message.getType());
        }
        if (pgpEngine == null) {
			Log.d("PHILIP", "pgpEngine null in PgpDecryptionService");
            return false;
        }
        pgpEngine.decrypt(message, callback);
        return true;
    }
}
