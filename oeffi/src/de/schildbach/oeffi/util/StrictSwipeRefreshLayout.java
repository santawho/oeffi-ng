package de.schildbach.oeffi.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import de.schildbach.oeffi.Application;

public class StrictSwipeRefreshLayout extends SwipeRefreshLayout {
    private final int touchSlop;
    private float initialX;
    private boolean hasMovedHorizontally;

    public StrictSwipeRefreshLayout(@NonNull final Context context) {
        this(context, null);
    }

    public StrictSwipeRefreshLayout(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = ev.getX();
                hasMovedHorizontally = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - initialX) > touchSlop)
                    hasMovedHorizontally = true;
                break;
        }

        if (hasMovedHorizontally)
            return false;

        return super.onInterceptTouchEvent(ev);
    }
}
