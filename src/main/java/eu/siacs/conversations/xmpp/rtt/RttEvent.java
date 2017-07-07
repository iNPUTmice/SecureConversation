package eu.siacs.conversations.xmpp.rtt;

public class RttEvent {
	public enum Type {
		TEXT,
		ERASE,
		WAIT
	}

	public Type type;

	public RttEvent(Type type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}
}
