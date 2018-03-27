package eu.siacs.conversations.ui.widget;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Display;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.util.SendButtonTool;

/**
 * Created by mxf on 2018/3/21.
 */

public class KeyboardMeasurementLayout extends RelativeLayout {

    private int screenHeight;
    private int keyboardHeight;
    private boolean isKeyboardActive;
    private KeyboardStateListener keyboardStateListener;
    private EmotionsViewStateListener emotionsViewStateListener;
    private ImageView indicator;

    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener = () -> {
        int screenHeight = getScreenHeight();
        Rect rect = new Rect();
        ((Activity) getContext()).getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
        int keyboardHeight = screenHeight - rect.bottom;
        if (Math.abs(keyboardHeight) > screenHeight / 5) {
            toggleKeyboardState(true);
            KeyboardMeasurementLayout.this.keyboardHeight = keyboardHeight;
        } else {
            toggleKeyboardState(false);
        }
    };


    public KeyboardMeasurementLayout(@NonNull Context context) {
        this(context, null);
    }

    public KeyboardMeasurementLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyboardMeasurementLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public KeyboardMeasurementLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.KeyboardMeasurementLayout);
        keyboardHeight = (int) typedArray.getDimension(0,500);
        typedArray.recycle();
        getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
    }

    private void toggleKeyboardState(boolean isActive) {
        isKeyboardActive = isActive;
        if (keyboardStateListener != null) {
            keyboardStateListener.onKeyboardStateChange(isActive);
        }
    }

    public int getKeyboardHeight() {
        return keyboardHeight;
    }

    public void setIndicator(ImageView indicator) {
        this.indicator = indicator;
        int res = SendButtonTool.getThemeResource(
                getContext(), R.attr.ic_insert_emoticon, R.drawable.ic_insert_emoticon);
        indicator.setImageResource(res);
    }

    public boolean isShown() {
        return getLayoutParams().height > 0;
    }

    public void showNotRequest() {
        getLayoutParams().height = keyboardHeight;
        forceLayout();
        toggleEmotionsViewState(true);
    }

    public void show() {
        getLayoutParams().height = keyboardHeight;
        requestLayout();
        toggleEmotionsViewState(true);
    }

    public void hide() {
        getLayoutParams().height = 0;
        requestLayout();
        toggleEmotionsViewState(false);
    }

    private void toggleEmotionsViewState(boolean isActive) {
        if (emotionsViewStateListener != null) {
            emotionsViewStateListener.onEmotionsViewStateChange(isActive);
        }
        if (indicator != null) {
            int res = SendButtonTool.getThemeResource(
                    getContext(), R.attr.ic_insert_emoticon, R.drawable.ic_insert_emoticon);
            indicator.setImageResource(isActive ? R.drawable.ic_insert_emoticon_active : res);
        }
    }

    public boolean isKeyboardActive() {
        return isKeyboardActive;
    }

    private int getScreenHeight() {
        if (screenHeight > 0) {
            return screenHeight;
        }
        WindowManager windowManager = (WindowManager)
                getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point outSize = new Point();
        display.getRealSize(outSize);
        screenHeight = outSize.y;
        return screenHeight;
    }

    public void setEmotionsViewStateListener(EmotionsViewStateListener listener){
        this.emotionsViewStateListener = listener;
    }

    public void setKeyboardStateListener(KeyboardStateListener listener) {
        this.keyboardStateListener = listener;
    }

    public interface KeyboardStateListener {
        void onKeyboardStateChange(boolean isActive);
    }

    public interface EmotionsViewStateListener {
        void onEmotionsViewStateChange(boolean isActive);
    }
}
