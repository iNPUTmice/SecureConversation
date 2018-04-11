package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.IntentSender.SendIntentException;
import android.content.res.Resources;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.databinding.ActivityMucDetailsBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnMucRosterUpdate;
import eu.siacs.conversations.ui.adapter.ConferenceDetailsContactAdapter;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class ConferenceDetailsActivity extends XmppActivity implements OnConversationUpdate, OnMucRosterUpdate, XmppConnectionService.OnAffiliationChanged, XmppConnectionService.OnRoleChanged, XmppConnectionService.OnConfigurationPushed {
	public static final String ACTION_VIEW_MUC = "view_muc";


	private Conversation mConversation;
	private OnClickListener inviteListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			inviteToConversation(mConversation);
		}
	};
	private ActivityMucDetailsBinding binding;
	private String uuid = null;
	private User mSelectedUser = null;

	private boolean mAdvancedMode = false;

	private UiCallback<Conversation> renameCallback = new UiCallback<Conversation>() {
		@Override
		public void success(Conversation object) {
			runOnUiThread(() -> {
				Toast.makeText(ConferenceDetailsActivity.this, getString(R.string.your_nick_has_been_changed), Toast.LENGTH_SHORT).show();
				updateView();
			});

		}

		@Override
		public void error(final int errorCode, Conversation object) {
			runOnUiThread(() -> Toast.makeText(ConferenceDetailsActivity.this, getString(errorCode), Toast.LENGTH_SHORT).show());
		}

		@Override
		public void userInputRequried(PendingIntent pi, Conversation object) {

		}
	};

	private OnClickListener mNotifyStatusClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			AlertDialog.Builder builder = new AlertDialog.Builder(ConferenceDetailsActivity.this);
			builder.setTitle(R.string.pref_notification_settings);
			String[] choices = {
					getString(R.string.notify_on_all_messages),
					getString(R.string.notify_only_when_highlighted),
					getString(R.string.notify_never)
			};
			final AtomicInteger choice;
			if (mConversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0) == Long.MAX_VALUE) {
				choice = new AtomicInteger(2);
			} else {
				choice = new AtomicInteger(mConversation.alwaysNotify() ? 0 : 1);
			}
			builder.setSingleChoiceItems(choices, choice.get(), (dialog, which) -> choice.set(which));
			builder.setNegativeButton(R.string.cancel, null);
			builder.setPositiveButton(R.string.ok, (dialog, which) -> {
				if (choice.get() == 2) {
					mConversation.setMutedTill(Long.MAX_VALUE);
				} else {
					mConversation.setMutedTill(0);
					mConversation.setAttribute(Conversation.ATTRIBUTE_ALWAYS_NOTIFY, String.valueOf(choice.get() == 0));
				}
				xmppConnectionService.updateConversation(mConversation);
				updateView();
			});
			builder.create().show();
		}
	};

	private OnClickListener mChangeConferenceSettings = new OnClickListener() {
		@Override
		public void onClick(View v) {
			final MucOptions mucOptions = mConversation.getMucOptions();
			AlertDialog.Builder builder = new AlertDialog.Builder(ConferenceDetailsActivity.this);
			builder.setTitle(R.string.conference_options);
			final String[] options;
			final boolean[] values;
			if (mAdvancedMode) {
				options = new String[]{
						getString(R.string.members_only),
						getString(R.string.moderated),
						getString(R.string.non_anonymous)
				};
				values = new boolean[]{
						mucOptions.membersOnly(),
						mucOptions.moderated(),
						mucOptions.nonanonymous()
				};
			} else {
				options = new String[]{
						getString(R.string.members_only),
						getString(R.string.non_anonymous)
				};
				values = new boolean[]{
						mucOptions.membersOnly(),
						mucOptions.nonanonymous()
				};
			}
			builder.setMultiChoiceItems(options, values, (dialog, which, isChecked) -> values[which] = isChecked);
			builder.setNegativeButton(R.string.cancel, null);
			builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
				if (!mucOptions.membersOnly() && values[0]) {
					xmppConnectionService.changeAffiliationsInConference(mConversation,
							MucOptions.Affiliation.NONE,
							MucOptions.Affiliation.MEMBER);
				}
				Bundle options1 = new Bundle();
				options1.putString("muc#roomconfig_membersonly", values[0] ? "1" : "0");
				if (values.length == 2) {
					options1.putString("muc#roomconfig_whois", values[1] ? "anyone" : "moderators");
				} else if (values.length == 3) {
					options1.putString("muc#roomconfig_moderatedroom", values[1] ? "1" : "0");
					options1.putString("muc#roomconfig_whois", values[2] ? "anyone" : "moderators");
				}
				options1.putString("muc#roomconfig_persistentroom", "1");
				xmppConnectionService.pushConferenceConfiguration(mConversation,
						options1,
						ConferenceDetailsActivity.this);
			});
			builder.create().show();
		}
	};
	private OnValueEdited onSubjectEdited = new OnValueEdited() {

		@Override
		public String onValueEdited(String value) {
			xmppConnectionService.pushSubjectToConference(mConversation, value);
			return null;
		}
	};
	private ConferenceDetailsContactAdapter contactAdapter;

	public static boolean cancelPotentialWork(User user, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final User old = bitmapWorkerTask.o;
			if (old == null || user != old) {
				bitmapWorkerTask.cancel(true);
			} else {
				return false;
			}
		}
		return true;
	}

	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	@Override
	public void onConversationUpdate() {
		refreshUi();
	}

	@Override
	public void onMucRosterUpdate() {
		refreshUi();
	}

	@Override
	protected void refreshUiReal() {
		updateView();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.binding = DataBindingUtil.setContentView(this, R.layout.activity_muc_details);
		setSupportActionBar(binding.toolbar);
		configureActionBar(getSupportActionBar());
		this.mAdvancedMode = getPreferences().getBoolean("advanced_muc_mode", false);
		this.contactAdapter = new ConferenceDetailsContactAdapter( this,
				new ConferenceDetailsContactAdapter.OnContactClickListener() {
					@Override
					public void onContactClick(User user) {
						highlightInMuc(mConversation, user.getName());
					}

					@Override
					public void onKeyClick(User user) {
						viewPgpKey(user);
					}

				});
		this.contactAdapter.setEditNickButtonClickListener(v -> quickEdit(mConversation.getMucOptions().getActualNick(),
				0,
				value -> {
					if (xmppConnectionService.renameInMuc(mConversation, value, renameCallback)) {
						return null;
					} else {
						return getString(R.string.invalid_username);
					}
				}));
		this.contactAdapter.setNotifyStatusClickListener(mNotifyStatusClickListener);
		this.contactAdapter.setChangeConferenceSettings(mChangeConferenceSettings);
		this.contactAdapter.setInviteListener(inviteListener);
		this.binding.mucMembers.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false));
		this.binding.mucMembers.setAdapter(contactAdapter);
	}

	@Override
	protected void onStart() {
		super.onStart();
		final int theme = findTheme();
		if (this.mTheme != theme) {
			recreate();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		if (MenuDoubleTabUtil.shouldIgnoreTap()) {
			return false;
		}
		switch (menuItem.getItemId()) {
			case android.R.id.home:
				finish();
				break;
			case R.id.action_edit_subject:
				if (mConversation != null) {
					quickEdit(mConversation.getMucOptions().getSubject(),
							R.string.edit_subject_hint,
							this.onSubjectEdited);
				}
				break;
			case R.id.action_share_http:
				shareLink(true);
				break;
			case R.id.action_share_uri:
				shareLink(false);
				break;
			case R.id.action_save_as_bookmark:
				saveAsBookmark();
				break;
			case R.id.action_delete_bookmark:
				deleteBookmark();
				break;
			case R.id.action_advanced_mode:
				this.mAdvancedMode = !menuItem.isChecked();
				menuItem.setChecked(this.mAdvancedMode);
				getPreferences().edit().putBoolean("advanced_muc_mode", mAdvancedMode).apply();
				invalidateOptionsMenu();
				updateView();
				break;
		}
		return super.onOptionsItemSelected(menuItem);
	}

	@Override
	protected String getShareableUri(boolean http) {
		if (mConversation != null) {
			if (http) {
				return "https://conversations.im/j/" + mConversation.getJid().asBareJid().toEscapedString();
			} else {
				return "xmpp:" + mConversation.getJid().asBareJid() + "?join";
			}
		} else {
			return null;
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem menuItemSaveBookmark = menu.findItem(R.id.action_save_as_bookmark);
		MenuItem menuItemDeleteBookmark = menu.findItem(R.id.action_delete_bookmark);
		MenuItem menuItemAdvancedMode = menu.findItem(R.id.action_advanced_mode);
		MenuItem menuItemChangeSubject = menu.findItem(R.id.action_edit_subject);
		menuItemAdvancedMode.setChecked(mAdvancedMode);
		if (mConversation == null) {
			return true;
		}
		if (mConversation.getBookmark() != null) {
			menuItemSaveBookmark.setVisible(false);
			menuItemDeleteBookmark.setVisible(true);
		} else {
			menuItemDeleteBookmark.setVisible(false);
			menuItemSaveBookmark.setVisible(true);
		}
		menuItemChangeSubject.setVisible(mConversation.getMucOptions().canChangeSubject());
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.muc_details, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		Object tag = v.getTag();
		if (tag instanceof User) {
			getMenuInflater().inflate(R.menu.muc_details_context, menu);
			final User user = (User) tag;
			final User self = mConversation.getMucOptions().getSelf();
			this.mSelectedUser = user;
			String name;
			final Contact contact = user.getContact();
			if (contact != null && contact.showInRoster()) {
				name = contact.getDisplayName();
			} else if (user.getRealJid() != null) {
				name = user.getRealJid().asBareJid().toString();
			} else {
				name = user.getName();
			}
			menu.setHeaderTitle(name);
			if (user.getRealJid() != null) {
				MenuItem showContactDetails = menu.findItem(R.id.action_contact_details);
				MenuItem startConversation = menu.findItem(R.id.start_conversation);
				MenuItem giveMembership = menu.findItem(R.id.give_membership);
				MenuItem removeMembership = menu.findItem(R.id.remove_membership);
				MenuItem giveAdminPrivileges = menu.findItem(R.id.give_admin_privileges);
				MenuItem removeAdminPrivileges = menu.findItem(R.id.remove_admin_privileges);
				MenuItem removeFromRoom = menu.findItem(R.id.remove_from_room);
				MenuItem banFromConference = menu.findItem(R.id.ban_from_conference);
				MenuItem invite = menu.findItem(R.id.invite);
				startConversation.setVisible(true);
				if (contact != null && contact.showInRoster()) {
					showContactDetails.setVisible(!contact.isSelf());
				}
				if (user.getRole() == MucOptions.Role.NONE) {
					invite.setVisible(true);
				}
				if (self.getAffiliation().ranks(MucOptions.Affiliation.ADMIN) &&
						self.getAffiliation().outranks(user.getAffiliation())) {
					if (mAdvancedMode) {
						if (user.getAffiliation() == MucOptions.Affiliation.NONE) {
							giveMembership.setVisible(true);
						} else {
							removeMembership.setVisible(true);
						}
						banFromConference.setVisible(true);
					} else {
						removeFromRoom.setVisible(true);
					}
					if (user.getAffiliation() != MucOptions.Affiliation.ADMIN) {
						giveAdminPrivileges.setVisible(true);
					} else {
						removeAdminPrivileges.setVisible(true);
					}
				}
			} else {
				MenuItem sendPrivateMessage = menu.findItem(R.id.send_private_message);
				sendPrivateMessage.setVisible(user.getRole().ranks(MucOptions.Role.VISITOR));
			}

		}
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		Jid jid = mSelectedUser.getRealJid();
		switch (item.getItemId()) {
			case R.id.action_contact_details:
				Contact contact = mSelectedUser.getContact();
				if (contact != null) {
					switchToContactDetails(contact);
				}
				return true;
			case R.id.start_conversation:
				startConversation(mSelectedUser);
				return true;
			case R.id.give_admin_privileges:
				xmppConnectionService.changeAffiliationInConference(mConversation, jid, MucOptions.Affiliation.ADMIN, this);
				return true;
			case R.id.give_membership:
				xmppConnectionService.changeAffiliationInConference(mConversation, jid, MucOptions.Affiliation.MEMBER, this);
				return true;
			case R.id.remove_membership:
				xmppConnectionService.changeAffiliationInConference(mConversation, jid, MucOptions.Affiliation.NONE, this);
				return true;
			case R.id.remove_admin_privileges:
				xmppConnectionService.changeAffiliationInConference(mConversation, jid, MucOptions.Affiliation.MEMBER, this);
				return true;
			case R.id.remove_from_room:
				removeFromRoom(mSelectedUser);
				return true;
			case R.id.ban_from_conference:
				xmppConnectionService.changeAffiliationInConference(mConversation, jid, MucOptions.Affiliation.OUTCAST, this);
				if (mSelectedUser.getRole() != MucOptions.Role.NONE) {
					xmppConnectionService.changeRoleInConference(mConversation, mSelectedUser.getName(), MucOptions.Role.NONE, this);
				}
				return true;
			case R.id.send_private_message:
				if (mConversation.getMucOptions().allowPm()) {
					privateMsgInMuc(mConversation, mSelectedUser.getName());
				} else {
					Toast.makeText(this, R.string.private_messages_are_disabled, Toast.LENGTH_SHORT).show();
				}
				return true;
			case R.id.invite:
				xmppConnectionService.directInvite(mConversation, jid);
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}

	private void removeFromRoom(final User user) {
		if (mConversation.getMucOptions().membersOnly()) {
			xmppConnectionService.changeAffiliationInConference(mConversation, user.getRealJid(), MucOptions.Affiliation.NONE, this);
			if (user.getRole() != MucOptions.Role.NONE) {
				xmppConnectionService.changeRoleInConference(mConversation, mSelectedUser.getName(), MucOptions.Role.NONE, ConferenceDetailsActivity.this);
			}
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.ban_from_conference);
			builder.setMessage(getString(R.string.removing_from_public_conference, user.getName()));
			builder.setNegativeButton(R.string.cancel, null);
			builder.setPositiveButton(R.string.ban_now, (dialog, which) -> {
				xmppConnectionService.changeAffiliationInConference(mConversation, user.getRealJid(), MucOptions.Affiliation.OUTCAST, ConferenceDetailsActivity.this);
				if (user.getRole() != MucOptions.Role.NONE) {
					xmppConnectionService.changeRoleInConference(mConversation, mSelectedUser.getName(), MucOptions.Role.NONE, ConferenceDetailsActivity.this);
				}
			});
			builder.create().show();
		}
	}

	protected void startConversation(User user) {
		if (user.getRealJid() != null) {
			Conversation conversation = xmppConnectionService.findOrCreateConversation(this.mConversation.getAccount(), user.getRealJid().asBareJid(), false, true);
			switchToConversation(conversation);
		}
	}

	protected void saveAsBookmark() {
		xmppConnectionService.saveConversationAsBookmark(mConversation,
				mConversation.getMucOptions().getSubject());
	}

	protected void deleteBookmark() {
		Account account = mConversation.getAccount();
		Bookmark bookmark = mConversation.getBookmark();
		account.getBookmarks().remove(bookmark);
		bookmark.setConversation(null);
		xmppConnectionService.pushBookmarks(account);
		updateView();
	}

	@Override
	void onBackendConnected() {
		if (mPendingConferenceInvite != null) {
			mPendingConferenceInvite.execute(this);
			mPendingConferenceInvite = null;
		}
		if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
			this.uuid = getIntent().getExtras().getString("uuid");
		}
		if (uuid != null) {
			this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
			if (this.mConversation != null) {
				updateView();
			}
		}
	}

	private void updateView() {
		invalidateOptionsMenu();
		setTitle(mConversation.getName());
		final MucOptions mucOptions = mConversation.getMucOptions();
		final ArrayList<User> users = mucOptions.getUsers();
		Collections.sort(users);
		contactAdapter.setOnline(mucOptions.online());
		contactAdapter.setConversation(mConversation);
		contactAdapter.setUsers(users);
		contactAdapter.setCanInvite(mConversation.getMucOptions().canInvite());
		contactAdapter.notifyDataSetChanged();
	}

	public String getStatus(User user) {
		if (mAdvancedMode) {
			return getString(user.getAffiliation().getResId()) +
					" (" + getString(user.getRole().getResId()) + ')';
		} else {
			return getString(user.getAffiliation().getResId());
		}
	}

	private void viewPgpKey(User user) {
		PgpEngine pgp = xmppConnectionService.getPgpEngine();
		if (pgp != null) {
			PendingIntent intent = pgp.getIntentForKey(user.getPgpKeyId());
			if (intent != null) {
				try {
					startIntentSenderForResult(intent.getIntentSender(), 0, null, 0, 0, 0);
				} catch (SendIntentException ignored) {

				}
			}
		}
	}

	@Override
	public void onAffiliationChangedSuccessful(Jid jid) {
		refreshUi();
	}

	@Override
	public void onAffiliationChangeFailed(Jid jid, int resId) {
		displayToast(getString(resId, jid.asBareJid().toString()));
	}

	@Override
	public void onRoleChangedSuccessful(String nick) {

	}

	@Override
	public void onRoleChangeFailed(String nick, int resId) {
		displayToast(getString(resId, nick));
	}

	@Override
	public void onPushSucceeded() {
		displayToast(getString(R.string.modified_conference_options));
	}

	@Override
	public void onPushFailed() {
		displayToast(getString(R.string.could_not_modify_conference_options));
	}

	private void displayToast(final String msg) {
		runOnUiThread(() -> Toast.makeText(ConferenceDetailsActivity.this, msg, Toast.LENGTH_SHORT).show());
	}

	public boolean getAdvancedMode(){
		return mAdvancedMode;
	}

	public void loadAvatar(User user, ImageView imageView) {
		if (cancelPotentialWork(user, imageView)) {
			final Bitmap bm = avatarService().get(user, getPixel(48), true);
			if (bm != null) {
				cancelPotentialWork(user, imageView);
				imageView.setImageBitmap(bm);
				imageView.setBackgroundColor(0x00000000);
			} else {
				String seed = user.getRealJid() != null ? user.getRealJid().asBareJid().toString() : null;
				imageView.setBackgroundColor(UIHelper.getColorForName(seed == null ? user.getName() : seed));
				imageView.setImageDrawable(null);
				final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
				final AsyncDrawable asyncDrawable = new AsyncDrawable(getResources(), null, task);
				imageView.setImageDrawable(asyncDrawable);
				try {
					task.execute(user);
				} catch (final RejectedExecutionException ignored) {
				}
			}
		}
	}

	static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
		}

		BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}

	class BitmapWorkerTask extends AsyncTask<User, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private User o = null;

		private BitmapWorkerTask(ImageView imageView) {
			imageViewReference = new WeakReference<>(imageView);
		}

		@Override
		protected Bitmap doInBackground(User... params) {
			this.o = params[0];
			if (imageViewReference.get() == null) {
				return null;
			}
			return avatarService().get(this.o, getPixel(48), isCancelled());
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (bitmap != null && !isCancelled()) {
				final ImageView imageView = imageViewReference.get();
				if (imageView != null) {
					imageView.setImageBitmap(bitmap);
					imageView.setBackgroundColor(0x00000000);
				}
			}
		}
	}

}
