package eu.siacs.conversations.xmpp.rtt;

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
}
