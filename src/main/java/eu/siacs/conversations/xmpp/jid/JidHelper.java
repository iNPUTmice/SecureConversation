package eu.siacs.conversations.xmpp.jid;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import rocks.xmpp.addr.Jid;

public final class JidHelper {

    private static List<String> LOCALPART_BLACKLIST = Arrays.asList("xmpp","jabber","me");

    public static String localPartOrFallback(Jid jid) {
        if (LOCALPART_BLACKLIST.contains(jid.getLocal().toLowerCase(Locale.ENGLISH))) {
            final String domain = jid.getDomain();
            final int index = domain.lastIndexOf('.');
            return index > 1 ? domain.substring(0,index) : domain;
        } else {
            return jid.getLocal();
        }
    }

    public static Jid fromString(final String jid) throws IllegalArgumentException {
        return Jid.of(jid);
    }

    public static Jid fromString(final String jid, final boolean safe) throws IllegalArgumentException {
        return fromString(jid);
    }

    public static Jid fromParts(final String localpart,
                                                final String domainpart,
                                                final String resourcepart) throws IllegalArgumentException {
        try {
            return Jid.of(localpart, domainpart, resourcepart);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static boolean isDomainJid(Jid jid) {
        return jid.getLocal() == null;
    }
}
