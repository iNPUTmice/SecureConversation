package eu.siacs.conversations.xmpp.rtt;

import eu.siacs.conversations.xml.Element;

public class WaitEvent extends RttEvent {

	private long waitInterval;

	public WaitEvent() {
		super(RttEvent.Type.WAIT);
	}

	public long getWaitInterval() {
		return waitInterval;
	}

	public void setWaitInterval(long waitInterval) {
		this.waitInterval = waitInterval;
	}

	@Override
	public Element toElement() {
		Element w = new Element("w");
		w.setAttribute("n", waitInterval);
		return w;
	}
}
