package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.Arrays;
import java.util.ArrayList;

import eu.siacs.conversations.Config;

public class Capabilities extends AbstractEntity {

	public static final String TABLENAME = "capabilities";

	public static final String HASH = "hash";
	public static final String CAPS = "caps";

	protected String mHash;
	protected ArrayList<String> mCaps;

	@Override
	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(HASH, mHash);

		String capsstr = "";
		for (String s : mCaps)
			capsstr += s + ";";

		values.put(CAPS, capsstr);

		return values;
	}

	public static Capabilities fromCursor(Cursor cursor) {

		String capsstr = cursor.getString(cursor.getColumnIndex(CAPS));
		ArrayList<String> caps = new ArrayList<String>(Arrays.asList(capsstr.split(";")));

		return new Capabilities(cursor.getString(cursor.getColumnIndex(HASH)), caps);
	}

	public Capabilities(final String hash, final ArrayList<String> caps) {
		this.mHash = hash;
		this.mCaps = caps;
	}

	public String getHash()
	{
		return mHash;
	}
	public ArrayList<String> getCapabilityList()
	{
		return mCaps;
	}
}
