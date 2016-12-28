package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

public class NorLinearLayout extends LinearLayout {
	public NorLinearLayout(Context context) {
		super(context);
	}

	public NorLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public NorLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public boolean hasOverlappingRendering() {
		// Makes setAlpha more performant, see https://plus.google.com/+RomanNurik/posts/NSgQvbfXGQN
		return false;
	}
}