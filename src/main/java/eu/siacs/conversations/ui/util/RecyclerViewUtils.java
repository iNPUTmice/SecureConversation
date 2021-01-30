package eu.siacs.conversations.ui.util;

import android.view.View;
import android.widget.ListView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class RecyclerViewUtils {
    public static LinearLayoutManager getLayoutManager(final RecyclerView view) {
        return (LinearLayoutManager) view.getLayoutManager();
    }

    public static void scrollToBottom(final RecyclerView view) {
        final int count = view.getAdapter().getItemCount();
        if (count > 0) {
            scrollToPosition(view, count - 1, true);
        }
    }

    public static void scrollToPosition(final RecyclerView view, int pos, boolean jumpToBottom) {
        final LinearLayoutManager lm = getLayoutManager(view);
        if (jumpToBottom) {
            final View lastChild = view.getChildAt(view.getChildCount() - 1);
            if (lastChild != null) {
                lm.scrollToPositionWithOffset(pos, -lastChild.getHeight());
                return;
            }
        }
        lm.scrollToPosition(pos);
    }

    public static int getLastVisiblePosition(RecyclerView view) {
        return getLayoutManager(view).findLastVisibleItemPosition();
    }

    public static int getFirstVisiblePosition(RecyclerView view) {
        return getLayoutManager(view).findFirstVisibleItemPosition();
    }
}
