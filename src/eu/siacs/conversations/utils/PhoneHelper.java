package eu.siacs.conversations.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Profile;

public class PhoneHelper {
	
	private static final String PGP_COLUMN=ContactsContract.Data.DATA1;
	private static final String PGP_COLUMN_NAME="PGP-Key-ID";
	private static final String PGP_KEY_ID=ContactsContract.Data.DATA2;
	private static final String CUSTOM_MIME_TYPE="vnd.com.google.cursor.item/contact_user_defined_field";

	public static void loadPhoneContacts(final Context context,
			final OnPhoneContactsLoadedListener listener) {
		final List<Bundle> phoneContactsPGP = new ArrayList<Bundle>();
		
		final String[] PROJECTION = new String[] { ContactsContract.Data._ID,
				ContactsContract.Data.DISPLAY_NAME,
				ContactsContract.Data.PHOTO_THUMBNAIL_URI,
				ContactsContract.Data.LOOKUP_KEY,
				ContactsContract.CommonDataKinds.Im.DATA};
		
		final String[] GPG_PROJECTION = new String[] { ContactsContract.Data._ID,
				ContactsContract.Data.LOOKUP_KEY,
				PGP_KEY_ID};

		final String SELECTION = "(" + ContactsContract.Data.MIMETYPE + "=\""
				+ ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
				+ "\") AND (" + ContactsContract.CommonDataKinds.Im.PROTOCOL
				+ "=\"" + ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER
				+ "\")";

		final String GPG_SELECTION = "(" + ContactsContract.Data.MIMETYPE + "=\""
				+ CUSTOM_MIME_TYPE + "\") AND (" + PGP_COLUMN + "=\""
				+ PGP_COLUMN_NAME + "\")";
		
		CursorLoader mCursorLoader = new CursorLoader(context,
				ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null,
				null);
		mCursorLoader.registerListener(0, new OnLoadCompleteListener<Cursor>() {

			final HashMap<String,Bundle> phoneContacts = new HashMap<String,Bundle>();
			
			@Override
			public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor) {
				if (cursor==null) {
					return;
				}
				while (cursor.moveToNext()) {
					Bundle contact = new Bundle();
					contact.putInt("phoneid", cursor.getInt(cursor
							.getColumnIndex(ContactsContract.Data._ID)));
					contact.putString(
							"displayname",
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)));
					contact.putString(
							"photouri",
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI)));
					String lookupKey=cursor.getString(cursor
							.getColumnIndex(ContactsContract.Data.LOOKUP_KEY));
					contact.putString("lookup", lookupKey);
					
					contact.putString("jid",cursor.getString(cursor
									.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)));
					phoneContacts.put(lookupKey,contact);
				}
				cursor.close();
				
				CursorLoader mCursorLoaderGPG = new CursorLoader(context,
						ContactsContract.Data.CONTENT_URI, GPG_PROJECTION, GPG_SELECTION, null,
						null);
				mCursorLoaderGPG.registerListener(0, new OnLoadCompleteListener<Cursor>() {

					@Override
					public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor) {
						if (cursor==null) {
							phoneContactsPGP.addAll(phoneContacts.values());
							return;
						}
						while (cursor.moveToNext()) {
							String lookupKey=cursor.getString(cursor
									.getColumnIndex(ContactsContract.Data.LOOKUP_KEY));
							Bundle contacts=phoneContacts.get(lookupKey);
							if(contacts!=null)
							{
								String pgpKeyId=cursor.getString(cursor
										.getColumnIndex(PGP_KEY_ID));
								if(pgpKeyId!=null && pgpKeyId.startsWith("0x"))
								{
									try
									{
										contacts.putLong("pgpkeyid",CryptoHelper.convertHexToKeyId(pgpKeyId.substring(2)));
									}
									catch(NumberFormatException e)
									{
										//Do nothing, could not add PGP key
									}
									catch(IndexOutOfBoundsException e)
									{
										//Do nothing, could not add PGP key
									}
								}
							}
						}
						cursor.close();
						phoneContactsPGP.addAll(phoneContacts.values());
							if (listener != null) {
							listener.onPhoneContactsLoaded(phoneContactsPGP);
						}
					}
				});
				mCursorLoaderGPG.startLoading();
			}
		});
		mCursorLoader.startLoading();
	}

	public static Uri getSefliUri(Context context) {
		String[] mProjection = new String[] { Profile._ID,
				Profile.PHOTO_URI };
		Cursor mProfileCursor = context.getContentResolver().query(
				Profile.CONTENT_URI, mProjection, null, null, null);

		if (mProfileCursor == null || mProfileCursor.getCount() == 0) {
			return null;
		} else {
			mProfileCursor.moveToFirst();
			String uri = mProfileCursor.getString(1);
			if (uri == null) {
				return null;
			} else {
				return Uri.parse(uri);
			}
		}
	}
}
