package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;

import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class EnterJidDialog {
	public static interface OnEnterJidDialogPositiveListener {
		public boolean onEnterJidDialogPositive(Jid account, Jid contact) throws EnterJidDialog.JidError;
	}

	public static class JidError extends Exception {
		final String msg;

		public JidError(final String msg) {
			this.msg = msg;
		}

		public String toString() {
			return msg;
		}
	}

	protected final AlertDialog dialog;
	protected View.OnClickListener dialogOnClick;
	protected OnEnterJidDialogPositiveListener listener = null;

	public EnterJidDialog(
		final Context context, List<String> knownHosts, List<String> activatedAccounts,
		final String title, final String positiveButton,
		final String prefilledJid, boolean allowEditJid
	) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(title);
		View dialogView = LayoutInflater.from(context).inflate(R.layout.enter_jid_dialog, null);
		final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
		final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView.findViewById(R.id.jid);
		jid.setAdapter(new KnownHostsAdapter(context,android.R.layout.simple_list_item_1, knownHosts));
		if (prefilledJid != null) {
			jid.append(prefilledJid);
			if (!allowEditJid) {
				jid.setFocusable(false);
				jid.setFocusableInTouchMode(false);
				jid.setClickable(false);
				jid.setCursorVisible(false);
			}
		}

		ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
				android.R.layout.simple_spinner_item, activatedAccounts);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);

		builder.setView(dialogView);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(positiveButton, null);
		this.dialog = builder.create();

		this.dialogOnClick = new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final Jid accountJid;
				try {
					if (Config.DOMAIN_LOCK != null) {
						accountJid = Jid.fromParts((String) spinner.getSelectedItem(), Config.DOMAIN_LOCK, null);
					} else {
						accountJid = Jid.fromString((String) spinner.getSelectedItem());
					}
				} catch (final InvalidJidException e) {
					return;
				}
				final Jid contactJid;
				try {
					contactJid = Jid.fromString(jid.getText().toString());
				} catch (final InvalidJidException e) {
					jid.setError(context.getString(R.string.invalid_jid));
					return;
				}

				if(listener != null) {
					try {
						if(listener.onEnterJidDialogPositive(accountJid, contactJid)) {
							dialog.dismiss();
						}
					} catch(JidError error) {
						jid.setError(error.toString());
					}
				}
			}
		};
	}

	public void setOnEnterJidDialogPositiveListener(OnEnterJidDialogPositiveListener listener) {
		this.listener = listener;
	}

	public void show() {
		this.dialog.show();
		this.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(this.dialogOnClick);
	}
}
