package eu.siacs.conversations.ui;

import android.app.PendingIntent;

public interface UiCallback<T> {
	public void success(final T object);

	public void error(final int errorCode, final T object);

	public void userInputRequired(final PendingIntent pi, final T object);
}
