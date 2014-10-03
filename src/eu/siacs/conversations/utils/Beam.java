package eu.siacs.conversations.utils;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcEvent;
import android.content.Context;
import android.app.Activity;
import android.os.Parcelable;
import android.util.Log;
import eu.siacs.conversations.Config;
import android.provider.Settings;

public class Beam implements CreateNdefMessageCallback {
	private NfcAdapter nfcAdapter = null;
	private String myself = null;

	public void setMyself(String myself) {
		this.myself = myself;
	}

	public String activate(Activity activity, final Context context, Intent intent, boolean config, String myself) {
		String jid = null;
		setMyself(myself);
		Log.d(Config.LOGTAG, "onResume: nfcAdapter=" + nfcAdapter);
		// TODO: adapt this flow - when do we actually check NFC availability?
		if (nfcAdapter == null)
			nfcAdapter = NfcAdapter.getDefaultAdapter(context);
		Log.d(Config.LOGTAG, "nfcAdapter=" + nfcAdapter);
		if (nfcAdapter != null) {
			if (nfcAdapter.isEnabled() || nfcAdapter.isNdefPushEnabled()) {
				// only if nfc and nde/beam is enabled we can proceed
				Log.d(Config.LOGTAG, "NFC and NDE available");
				nfcAdapter.setNdefPushMessageCallback(this, activity);
				// this is the moment where we actually notice that we received
				// a beam event:
				if (intent != null && NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
					Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
					NdefMessage msg = (NdefMessage)rawMsgs[0];
					jid = new String(msg.getRecords()[0].getPayload());
					Log.d(Config.LOGTAG, "we got the payload, should be a jid: " + jid);
				}
			}
			else {
				if (config) {
					// if either is switched off ask the user to turn it on, but: it
					// may well be that we land here without any nfc in the device
					// at all
					Log.d(Config.LOGTAG, nfcAdapter.isEnabled() ? "NFC and Beam not enabled" : "Beam not enabled");
					AlertDialog.Builder builder = new AlertDialog.Builder(context);
					builder.setMessage(nfcAdapter.isEnabled() ? // TODO from resource
						"For this operation you need NFC and Beam which is currently disabled, you have to enable it in the settings." :
						"For this operation you need Beam which is currently disabled, you have to enable it in the settings."
						);
					builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialogInterface, int i) {
							// jump into system settings for wireless stuff
							context.startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
						}
					});
					builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialogInterface, int i) {
							// user decided not to configure
						}
					});
					builder.create().show();
				}
			}
		}
		else {
			// devices without any nfc capability often just return null
			Log.d(Config.LOGTAG, "NFC not available");
		}
		return jid;
	}

	@Override
	public NdefMessage createNdefMessage(NfcEvent event) {
		NdefMessage msg = null;
		if (myself != null) {
			Log.d(Config.LOGTAG, "Message requested");
			msg = new NdefMessage(new NdefRecord[] { NdefRecord.createMime(
				"application/vnd.eu.siacs.conversations.jid",
				myself.getBytes()), // TODO: get real "myself" jid
				});
		}
		return msg;
	}
}
