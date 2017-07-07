package eu.siacs.conversations.xmpp.rtt;

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

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}
}
