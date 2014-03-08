package eu.siacs.conversations.utils;

import java.util.Hashtable;

import android.app.Activity;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Profile;

public class PhoneHelper {
	
	public static void loadPhoneContacts(Context context, final OnPhoneContactsLoadedListener listener, 
		final boolean defaultAlias) { // if an alias should be used as displayname if it exists
		if (Looper.myLooper()==null) {
			Looper.prepare();
		}
		final Looper mLooper = Looper.myLooper();
		final Hashtable<String, Bundle> phoneContacts = new Hashtable<String, Bundle>();
		
		final String[] PROJECTION = new String[] {
				ContactsContract.Data._ID,
				ContactsContract.Data.DISPLAY_NAME,
				ContactsContract.Data.PHOTO_THUMBNAIL_URI,
				ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.Data.MIMETYPE,
				ContactsContract.CommonDataKinds.Im.DATA,
                ContactsContract.CommonDataKinds.Nickname.NAME };

		final String SELECTION = "((" + ContactsContract.Data.MIMETYPE + "=\""
				+ ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
				+ "\") AND (" + ContactsContract.CommonDataKinds.Im.PROTOCOL
				+ "=\"" + ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER
				+ "\")) OR (" +  ContactsContract.Data.MIMETYPE
                + "=\"" + ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE +"\")" ;
		
		CursorLoader mCursorLoader = new CursorLoader(context,
				ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null,
				null);
		mCursorLoader.registerListener(0, new OnLoadCompleteListener<Cursor>() {

			@Override
			public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor) {
                
                Hashtable<String, Bundle> ht = new Hashtable<String, Bundle>();
                
				while (cursor.moveToNext()) {
                    String lookUpKey = cursor.getString(cursor
                            .getColumnIndex(ContactsContract.Data.LOOKUP_KEY));
                    String mimetype = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE));


					Bundle contact = ht.get(lookUpKey);
                    if (contact == null) {
                        contact = new Bundle();
                    }
                    contact.putInt("phoneid", cursor.getInt(cursor
							.getColumnIndex(ContactsContract.Data._ID)));
                    if (defaultAlias && 
			mimetype.equals(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE))
                    {
                        String nickname = cursor.getString(cursor
                                .getColumnIndex(ContactsContract.CommonDataKinds.Nickname.NAME));
                        contact.putString(
                                "displayname",nickname);
                    } else if(mimetype.equals(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)){
                        contact.putString("jid", 
                            cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)));
                        if(contact.getString("displayname") == null){
                            contact.putString(
                                    "displayname",
                                    cursor.getString(cursor
                                            .getColumnIndex(ContactsContract.Data.DISPLAY_NAME)));
                        }
                    }
                    contact.putString(
                            "photouri",
                            cursor.getString(cursor
                                    .getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI)));
                    contact.putString("lookup",lookUpKey);
                    ht.put(lookUpKey, contact);

				}
                for(String lookUpKey: ht.keySet()){
                    Bundle contact = ht.get(lookUpKey);
                    String jid = contact.getString("jid");
                    if(jid != null){
                        phoneContacts.put(jid, contact);
                    }
                }
				if (listener!=null) {
					listener.onPhoneContactsLoaded(phoneContacts);
				}
				mLooper.quit();
			}
		});
		mCursorLoader.startLoading();
	}

	public static Uri getSefliUri(Activity activity) {
		String[] mProjection = new String[] { Profile._ID,
				Profile.PHOTO_THUMBNAIL_URI };
		Cursor mProfileCursor = activity.getContentResolver().query(
				Profile.CONTENT_URI, mProjection, null, null, null);

		if (mProfileCursor.getCount()==0) {
			return null;
		} else {
			mProfileCursor.moveToFirst();
			String uri = mProfileCursor.getString(1);
			if (uri==null) {
				return null;
			} else {
				return Uri.parse(uri);
			}
		}
	}
}
