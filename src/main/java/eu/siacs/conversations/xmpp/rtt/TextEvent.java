package eu.siacs.conversations.xmpp.rtt;

import eu.siacs.conversations.xml.Element;

public class TextEvent extends RttEvent {

	private String text;
	private Integer position;

	public TextEvent() {
		super(RttEvent.Type.TEXT);
		position = null;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public Integer getPosition() {
		return position;
	}

	public void setPosition(Integer position) {
		this.position = position;
	}

	@Override
	public Element toElement() {
		Element t = new Element("t");
		t.setContent(text);
		if (position != null) {
			t.setAttribute("p", position);
		}
		return t;
	}
}
