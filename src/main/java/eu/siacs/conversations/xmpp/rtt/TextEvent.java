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

	public Integer getPosition() {
		return position;
	}

	public void setPosition(Integer position) {
		this.position = position;
	}
}
