package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import eu.siacs.conversations.R;

public class AudioViewGroup extends RelativeLayout {
    private boolean showProgress = true;
    private String audioFile = "";

    public AudioViewGroup(final Context context) {
        super(context);
        init(context, null);
    }

    public AudioViewGroup(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AudioViewGroup(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public void init(final Context context, final AttributeSet attrs) {
        if (attrs != null) {
            final TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.Audio, 0, 0);

            try {
                showProgress = a.getBoolean(R.styleable.Audio_showProgress, true);
                audioFile = a.getNonResourceString(R.styleable.Audio_uri);
            } finally {
                a.recycle();
            }
        }

        View.inflate(context, R.layout.media_player, this);
    }

    public boolean isShowProgress() {
        return showProgress;
    }

    public void setShowProgress(final boolean showProgress) {
        this.showProgress = showProgress;
        invalidate();
        requestLayout();
    }
}
