/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
