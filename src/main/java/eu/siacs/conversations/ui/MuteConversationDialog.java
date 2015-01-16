package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.SystemClock;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;

public final class MuteConversationDialog {
	public static void show(final Context context,
			final XmppConnectionService xmppConnectionService,
			final Conversation conversation) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.disable_notifications);
		final int[] durations = context.getResources().getIntArray(
				R.array.mute_options_durations);
		builder.setItems(R.array.mute_options_descriptions,
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						final long till;
						if (durations[which] == -1) {
							till = Long.MAX_VALUE;
						} else {
							till = SystemClock.elapsedRealtime()
								+ (durations[which] * 1000);
						}
						conversation.setMutedTill(till);
						xmppConnectionService.databaseBackend
							.updateConversation(conversation);
						xmppConnectionService.updateConversationUi();
					}
				});
		builder.create().show();
	}
}
