package eu.siacs.conversations.xmpp.jid;

public final class JidHelper {

    public static rocks.xmpp.addr.Jid fromString(final String jid) throws IllegalArgumentException {
        return rocks.xmpp.addr.Jid.of(jid);
    }

    public static rocks.xmpp.addr.Jid fromString(final String jid, final boolean safe) throws IllegalArgumentException {
        return fromString(jid);
    }

    public static rocks.xmpp.addr.Jid fromParts(final String localpart,
                                                final String domainpart,
                                                final String resourcepart) throws IllegalArgumentException {
        try {
            return rocks.xmpp.addr.Jid.of(localpart,domainpart,resourcepart);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static boolean hasLocalpart(rocks.xmpp.addr.Jid jid) {
        return jid.getLocal() != null;
    }

    public static boolean isDomainJid(rocks.xmpp.addr.Jid jid) {
        return jid.getLocal() == null;
    }
}
