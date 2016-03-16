package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.solovyev.android.views.llm.LinearLayoutManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.xmpp.OnGatewayPromptResult;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class EnterJidDialog {
	public interface OnEnterJidDialogPositiveListener {
		boolean onEnterJidDialogPositive(Jid account, Jid contact) throws EnterJidDialog.JidError;
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

	protected class GatewayListAdapter extends RecyclerView.Adapter<GatewayListAdapter.ViewHolder> {
		protected class ViewHolder extends RecyclerView.ViewHolder {
			protected TextView label;
			protected ToggleButton button;
			protected int index;

			public ViewHolder(View view, int i) {
				super(view);
				this.label = (TextView) view.findViewById(R.id.label);
				this.button = (ToggleButton) view.findViewById(R.id.button);
				setIndex(i);
				button.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						button.setChecked(true); // Force visual not to flap to unchecked
						setSelected(index);
					}
				});
			}

			public void setIndex(int i) {
				this.index = i;
				button.setChecked(selected == i);
			}

			public void setText(int res) {
				label.setText(res);
				button.setVisibility(View.GONE);
				label.setVisibility(View.VISIBLE);
			}

			public void useButton(int res) {
				button.setText(res);
				button.setTextOff(button.getText());
				button.setTextOn(button.getText());
				label.setVisibility(View.GONE);
				button.setVisibility(View.VISIBLE);
			}

			public void useButton(String txt) {
				button.setTextOff(txt);
				button.setTextOn(txt);
				button.setChecked(selected == this.index);
				label.setVisibility(View.GONE);
				button.setVisibility(View.VISIBLE);
			}
		}

		protected List<Pair<Contact,String>> gateways;
		protected int selected;

		public GatewayListAdapter() {
			this.gateways = new ArrayList<>();
			this.selected = 0;
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
			View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.enter_jid_dialog_gateway_list_item, null);
			return new ViewHolder(view, i);
		}

		@Override
		public void onBindViewHolder(ViewHolder viewHolder, int i) {
			viewHolder.setIndex(i);

			if(i == 0) {
				if(getItemCount() < 2) {
					viewHolder.setText(R.string.account_settings_jabber_id);
				} else {
					viewHolder.useButton(R.string.account_settings_jabber_id);
				}
			} else {
				viewHolder.useButton(this.gateways.get(i-1).first.getDisplayName());

				for(Presence p : this.gateways.get(i-1).first.getPresences().getPresences().values()) {
					if(p.getServiceDiscoveryResult() != null) {
						for(ServiceDiscoveryResult.Identity id : p.getServiceDiscoveryResult().getIdentities()) {
							if(id.getCategory().equals("gateway")) {
								viewHolder.useButton(id.getType());
								break;
							}
						}
					}
				}
			}
		}

		@Override
		public int getItemCount() {
			return this.gateways.size() + 1;
		}

		public void setSelected(int i) {
			int old = this.selected;
			this.selected = i;

			if(i == 0) {
				jid.setThreshold(1);
				jid.setHint(R.string.account_settings_example_jabber_id);
			} else {
				jid.setThreshold(999999); // do not autocomplete
				jid.setHint(this.gateways.get(i-1).second);
			}

			notifyItemChanged(old);
			notifyItemChanged(i);
		}

		public void clear() {
			this.gateways.clear();
			notifyDataSetChanged();
			setSelected(0);
		}

		public void add(Contact gateway, String prompt) {
			this.gateways.add(new Pair<>(gateway, prompt));
			notifyDataSetChanged();
		}
	}

	protected final AlertDialog dialog;
	protected View.OnClickListener dialogOnClick;
	protected OnEnterJidDialogPositiveListener listener = null;
	protected final Spinner spinner;
	protected final AutoCompleteTextView jid;

	public EnterJidDialog(
		final XmppActivity context, List<String> knownHosts, final List<String> activatedAccounts,
		final String title, final String positiveButton,
		final String prefilledJid, final String account, boolean allowEditJid
	) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(title);
		View dialogView = LayoutInflater.from(context).inflate(R.layout.enter_jid_dialog, null);
		this.spinner = (Spinner) dialogView.findViewById(R.id.account);
		jid = (AutoCompleteTextView) dialogView.findViewById(R.id.jid);
		jid.setAdapter(new KnownHostsAdapter(context,R.layout.simple_list_item, knownHosts));
		if (prefilledJid != null) {
			jid.append(prefilledJid);
			if (!allowEditJid) {
				jid.setFocusable(false);
				jid.setFocusableInTouchMode(false);
				jid.setClickable(false);
				jid.setCursorVisible(false);
			}
		}

		final RecyclerView gatewayList = (RecyclerView) dialogView.findViewById(R.id.gateway_list);
		gatewayList.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
		gatewayList.setAdapter(new GatewayListAdapter());

		if (account == null) {
			StartConversationActivity.populateAccountSpinner(context, activatedAccounts, spinner);
		} else {
			ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
					R.layout.simple_list_item,
					new String[] { account });
			spinner.setEnabled(false);
			adapter.setDropDownViewResource(R.layout.simple_list_item);
			spinner.setAdapter(adapter);
		}

		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView spinner, View view, int position, long id) {
				final Jid accountJid = getAccountJid();
				if(context.xmppConnectionService != null && accountJid != null) {
					((GatewayListAdapter) gatewayList.getAdapter()).clear();

					final Account account = context.xmppConnectionService.findAccountByJid(accountJid);
					TreeSet<Contact> gateways = new TreeSet<>(account.getRoster().getWithIdentity("gateway", null));
					gateways.addAll(account.getRoster().getWithFeature("jabber:iq:gateway"));

					for(final Contact gateway : gateways) {
						context.xmppConnectionService.fetchGatewayPrompt(account, gateway.getJid(), new OnGatewayPromptResult() {
							@Override
							public void onGatewayPromptResult(final String prompt, String errorMessage) {
								if (prompt != null) {
									context.runOnUiThread(new Runnable() {
										public void run() {
											((GatewayListAdapter) gatewayList.getAdapter()).add(gateway, prompt);
										}
									});
								}
							}
						});
					}
				}
			}

			@Override
			public void onNothingSelected(AdapterView spinner) {
				((GatewayListAdapter) gatewayList.getAdapter()).clear();
			}
		});

		builder.setView(dialogView);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(positiveButton, null);
		this.dialog = builder.create();

		this.dialogOnClick = new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				if (!spinner.isEnabled() && account == null) {
					return;
				}
				final Jid accountJid = getAccountJid();
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

	protected Jid getAccountJid() {
		try {
			if (Config.DOMAIN_LOCK != null) {
				return Jid.fromParts((String) spinner.getSelectedItem(), Config.DOMAIN_LOCK, null);
			} else {
				return Jid.fromString((String) spinner.getSelectedItem());
			}
		} catch (final InvalidJidException e) {
			return null;
		}
	}

	public void setOnEnterJidDialogPositiveListener(OnEnterJidDialogPositiveListener listener) {
		this.listener = listener;
	}

	public void show() {
		this.dialog.show();
		this.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(this.dialogOnClick);
	}
}
