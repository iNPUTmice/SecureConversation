package eu.siacs.conversations.xmpp.jid;

import android.content.Context;
import android.util.Base64;

import com.jakewharton.disklrucache.DiskLruCache;

import net.java.otr4j.session.SessionID;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.IDN;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.ConversationsApplication;
import eu.siacs.conversations.utils.CryptoHelper;
import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;

/**
 * The `Jid' class provides an immutable representation of a JID.
 */
public final class Jid implements Serializable {

	private static final long serialVersionUID = -8551822259231076467L;

	private static DiskLruCache CACHE = null;

	private final String localpart;
	private final String domainpart;
	private final String resourcepart;

	// It's much more efficient to store the ful JID as well as the parts instead of figuring them
	// all out every time (since some characters are displayed but aren't used for comparisons).
	private final String displayjid;

	public String getLocalpart() {
		return localpart;
	}

	public String getDomainpart() {
		return IDN.toUnicode(domainpart);
	}

	public String getResourcepart() {
		return resourcepart;
	}


	private static void initCache() throws IOException {
		File folder = ConversationsApplication.getContext().getDir(Config.JID_CACHE_DIR, Context.MODE_PRIVATE);
		CACHE = DiskLruCache.open(folder, Config.JID_CACHE_VERSION, 2, Config.JID_CACHE_SIZE);

	}

	private static Jid getFromCache(final String key){
		String hash = CryptoHelper.md5(key);
		Jid jid = null;

		try {
			if(CACHE == null)
				initCache();

			DiskLruCache.Snapshot snapshot = CACHE.get(hash);
			if(snapshot == null || !snapshot.getString(0).equals(key)) return null;
			String diskEntry = snapshot.getString(1);

			byte[] bytes = Base64.decode(diskEntry, Base64.DEFAULT);

			try {
				ObjectInputStream objectInputStream = new ObjectInputStream( new ByteArrayInputStream(bytes) );
				jid = (Jid)objectInputStream.readObject();
			} catch (IOException | ClassNotFoundException | ClassCastException ignored) {}

		} catch (IOException ignored) {}

		return jid;
	}

	private static void storeInCache(final String key, final Jid jid){
		String hash = CryptoHelper.md5(key);
		try {
			if(CACHE == null)
				initCache();

			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
			objectOutputStream.writeObject(jid);
			objectOutputStream.close();
			String encoded = new String(Base64.encode(byteArrayOutputStream.toByteArray(), Base64.DEFAULT));

			DiskLruCache.Editor editor = CACHE.edit(hash);
			editor.set(0, key);
			editor.set(1, encoded);
			editor.commit();
		} catch (IOException e) {
			e.printStackTrace();
	}


	}

	public static Jid fromSessionID(final SessionID id) throws InvalidJidException{
		if (id.getUserID().isEmpty()) {
			return Jid.fromString(id.getAccountID());
		} else {
			return Jid.fromString(id.getAccountID()+"/"+id.getUserID());
		}
	}

	public static Jid fromString(final String jid) throws InvalidJidException {
		Jid j = getFromCache(jid);
		if (j == null){
			j = new Jid(jid);
			storeInCache(jid, j);
		}
		return j;
	}

	public static Jid fromParts(final String localpart,
			final String domainpart,
			final String resourcepart) throws InvalidJidException {
		String out;
		if (localpart == null || localpart.isEmpty()) {
			out = domainpart;
		} else {
			out = localpart + "@" + domainpart;
		}
		if (resourcepart != null && !resourcepart.isEmpty()) {
			out = out + "/" + resourcepart;
		}
		return Jid.fromString(out);
	}

	private Jid(final String jid) throws InvalidJidException {
		if (jid == null) throw new InvalidJidException(InvalidJidException.IS_NULL);

		// Hackish Android way to count the number of chars in a string... should work everywhere.
		final int atCount = jid.length() - jid.replace("@", "").length();
		final int slashCount = jid.length() - jid.replace("/", "").length();

		// Throw an error if there's anything obvious wrong with the JID...
		if (jid.isEmpty() || jid.length() > 3071) {
			throw new InvalidJidException(InvalidJidException.INVALID_LENGTH);
		}

		// Go ahead and check if the localpart or resourcepart is empty.
		if (jid.startsWith("@") || (jid.endsWith("@") && slashCount == 0) || jid.startsWith("/") || (jid.endsWith("/") && slashCount < 2)) {
			throw new InvalidJidException(InvalidJidException.INVALID_CHARACTER);
		}

		String finaljid;

		final int domainpartStart;
		final int atLoc = jid.indexOf("@");
		final int slashLoc = jid.indexOf("/");
		// If there is no "@" in the JID (eg. "example.net" or "example.net/resource")
		// or there are one or more "@" signs but they're all in the resourcepart (eg. "example.net/@/rp@"):
		if (atCount == 0 || (atCount > 0 && slashLoc != -1 && atLoc > slashLoc)) {
			localpart = "";
			finaljid = "";
			domainpartStart = 0;
		} else {
			final String lp = jid.substring(0, atLoc);
			try {
				localpart = Stringprep.nodeprep(lp);
			} catch (final StringprepException e) {
				throw new InvalidJidException(InvalidJidException.STRINGPREP_FAIL, e);
			}
			if (localpart.isEmpty() || localpart.length() > 1023) {
				throw new InvalidJidException(InvalidJidException.INVALID_PART_LENGTH);
			}
			domainpartStart = atLoc + 1;
			finaljid = lp + "@";
		}

		final String dp;
		if (slashCount > 0) {
			final String rp = jid.substring(slashLoc + 1, jid.length());
			try {
				resourcepart = Stringprep.resourceprep(rp);
			} catch (final StringprepException e) {
				throw new InvalidJidException(InvalidJidException.STRINGPREP_FAIL, e);
			}
			if (resourcepart.isEmpty() || resourcepart.length() > 1023) {
				throw new InvalidJidException(InvalidJidException.INVALID_PART_LENGTH);
			}
			dp = IDN.toUnicode(jid.substring(domainpartStart, slashLoc), IDN.USE_STD3_ASCII_RULES);
			finaljid = finaljid + dp + "/" + rp;
		} else {
			resourcepart = "";
			dp = IDN.toUnicode(jid.substring(domainpartStart, jid.length()),
					IDN.USE_STD3_ASCII_RULES);
			finaljid = finaljid + dp;
		}

		// Remove trailing "." before storing the domain part.
		if (dp.endsWith(".")) {
			try {
				domainpart = IDN.toASCII(dp.substring(0, dp.length() - 1), IDN.USE_STD3_ASCII_RULES);
			} catch (final IllegalArgumentException e) {
				throw new InvalidJidException(e);
			}
		} else {
			try {
				domainpart = IDN.toASCII(dp, IDN.USE_STD3_ASCII_RULES);
			} catch (final IllegalArgumentException e) {
				throw new InvalidJidException(e);
			}
		}

		// TODO: Find a proper domain validation library; validate individual parts, separators, etc.
		if (domainpart.isEmpty() || domainpart.length() > 1023) {
			throw new InvalidJidException(InvalidJidException.INVALID_PART_LENGTH);
		}

		this.displayjid = finaljid;
	}

	public Jid toBareJid() {
		try {
			return resourcepart.isEmpty() ? this : fromParts(localpart, domainpart, "");
		} catch (final InvalidJidException e) {
			// This should never happen.
			return null;
		}
	}

	public Jid toDomainJid() {
		try {
			return resourcepart.isEmpty() && localpart.isEmpty() ? this : fromString(getDomainpart());
		} catch (final InvalidJidException e) {
			// This should never happen.
			return null;
		}
	}

	@Override
	public String toString() {
		return displayjid;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		final Jid jid = (Jid) o;

		return jid.hashCode() == this.hashCode();
	}

	@Override
	public int hashCode() {
		int result = localpart.hashCode();
		result = 31 * result + domainpart.hashCode();
		result = 31 * result + resourcepart.hashCode();
		return result;
	}

	public boolean hasLocalpart() {
		return !localpart.isEmpty();
	}

	public boolean isBareJid() {
		return this.resourcepart.isEmpty();
	}

	public boolean isDomainJid() {
		return !this.hasLocalpart();
	}
}
