package eu.siacs.conversations.xmpp.rtt;

public class EraseEvent extends RttEvent {

	private Integer number;
	private Integer position;

	public EraseEvent() {
		super(RttEvent.Type.ERASE);
		this.number = null;
		this.position = null;
	}

	public Integer getNumber() {
		if (number != null) return number;
		else return 1;
	}

	public void setNumber(Integer number) {
		if (number != 1) this.number = number;
	}

	public Integer getPosition() {
		return position;
	}

	public void setPosition(Integer position) {
		this.position = position;
	}
}
