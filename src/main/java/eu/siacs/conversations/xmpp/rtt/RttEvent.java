package eu.siacs.conversations.xmpp.rtt;

import eu.siacs.conversations.xml.Element;

public abstract class RttEvent {
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

	public abstract Element toElement();
}
