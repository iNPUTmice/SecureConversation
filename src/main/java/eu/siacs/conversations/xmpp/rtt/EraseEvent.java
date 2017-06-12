package eu.siacs.conversations.xmpp.rtt;

public class EraseEvent extends RttEvent {

    private int number;
    private int position;

    public EraseEvent() {
        super(RttEvent.Type.ERASE);
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
