package eu.siacs.conversations.xmpp.rtt;

import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;

import java.util.LinkedList;

import eu.siacs.conversations.Config;

public class RttEventListener implements TextWatcher {

	/*
	 * addLast()	 -> add Element to the queue
	 * removeFirst() -> remove Element from the queue
	 * size()		 -> size of the queue
	 *
	 * It is synchronized because another method will be called every 700-1000 milliseconds which
	 * will empty the queue and actually send these events to the other device packed in <rtt />
	 * stanzas.
	 */
	private final LinkedList<RttEvent> rttEventQueue;

	/*
	 * Stores number of milliseconds from epoch at the time of the latest edit
	 */
	private long lastMessageMillis;

	/*
	 * Stores number of milliseconds from epoch at the time of the current edit
	 */
	private long currentMessageMillis;

	private static final long MIN_WAIT_DUR = 100;

	private String _before, _after;
	private int _beforelen, _afterlen;

	public RttEventListener() {
		rttEventQueue = new LinkedList<>();
		currentMessageMillis = lastMessageMillis = 0;
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		_before = String.valueOf(s.subSequence(start, start + count));
		_beforelen = _before.length();
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		_after = String.valueOf(s.subSequence(start, start + count));
		_afterlen = _after.length();

		currentMessageMillis = SystemClock.elapsedRealtime();
		if (lastMessageMillis == 0) {
			lastMessageMillis = currentMessageMillis;
		}

		if (_afterlen > _beforelen) {
			if (_after.startsWith(_before)) {
				String changedPart = String.valueOf(_after.subSequence(_before.length(), _after.length()));
				addTextEvent(changedPart, start + _before.length());
			} else if (_after.endsWith(_before)) {
				String changedPart = String.valueOf(_after.subSequence(0, _after.length() - _before.length()));
				addTextEvent(changedPart, start);
			} else {
				addEraseEvent(before, start + before);
				addTextEvent(_after, start);
			}
		} else if (_afterlen == _beforelen) {
			addEraseEvent(before, start + before);
			addTextEvent(_after, start);
		} else {
			if (_before.startsWith(_after)) {
				addEraseEvent(_beforelen - _afterlen, start + _beforelen);
			} else if (_before.endsWith(_after)) {
				addEraseEvent(_beforelen - _afterlen, start + _beforelen - _afterlen);
			} else {
				addEraseEvent(before, start + before);
				addTextEvent(_after, start);
			}

		}
	}

	@Override
	public void afterTextChanged(Editable s) {

	}

	private void addTextEvent(CharSequence text, int position) {
		if (text.length() > 0) {
			addWaitEvent();
			TextEvent t = new TextEvent();
			t.setPosition(position);
			t.setText(String.valueOf(text));

			synchronized (rttEventQueue) {
				rttEventQueue.addLast(t);
				Log.i(Config.LOGTAG, "Text RTT event with text = " + t.getText() + " at position = " + t.getPosition());
			}
		}
	}

	private void addWaitEvent() {
		if (currentMessageMillis - lastMessageMillis > MIN_WAIT_DUR) {
			WaitEvent w = new WaitEvent();
			w.setWaitInterval(currentMessageMillis - lastMessageMillis);

			synchronized (rttEventQueue) {
				rttEventQueue.addLast(w);
				Log.i(Config.LOGTAG, "Wait RTT event with " + w.getWaitInterval() + " milliseconds");
			}
		}

		lastMessageMillis = currentMessageMillis;
	}

	private void addEraseEvent(int number, int position) {
		if (number > 0) {
			addWaitEvent();
			EraseEvent e = new EraseEvent();
			e.setPosition(position);
			e.setNumber(number);

			synchronized (rttEventQueue) {
				rttEventQueue.addLast(e);
				Log.i(Config.LOGTAG, "Erase RTT event to erase " + e.getNumber() + "characters from " + e.getPosition() + "th character");
			}
		}
	}
}
