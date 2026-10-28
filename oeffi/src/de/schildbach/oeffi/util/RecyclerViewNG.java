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
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import de.schildbach.oeffi.R;

public class RecyclerViewNG extends RecyclerView {
    private final int starMargin;

    public RecyclerViewNG(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        starMargin = getResources().getDimensionPixelOffset(R.dimen.text_padding_horizontal_lax);

        new ItemTouchHelper(new HorizontalSwipeHelper()).attachToRecyclerView(this);
    }

    public static abstract class HorizontalSwipeHolder extends RecyclerView.ViewHolder {
        public HorizontalSwipeHolder(@NonNull final View itemView) {
            super(itemView);
        }

        protected abstract void onRevealLeft(int adapterPosition);

        protected abstract void onRevealRight(int adapterPosition);

        protected abstract Drawable getLeftIcon(int adapterPosition);

        protected abstract Drawable getRightIcon(int adapterPosition);

        protected abstract boolean canRevealLeft(int adapterPosition);

        protected abstract boolean canRevealRight(int adapterPosition);
    }

    public class HorizontalSwipeHelper extends ItemTouchHelper.SimpleCallback {
        private HorizontalSwipeHelper() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        }

        private float lastDX = 0;
        private int lastDirection = 0;
        private int actionTriggerThreshold;

        @Override
        public boolean isItemViewSwipeEnabled() {
            return true;
        }

        @Override
        public float getSwipeEscapeVelocity(final float defaultValue) {
            return Float.MAX_VALUE; // disable swipe by flinging
        }

        @Override
        public float getSwipeThreshold(@NonNull final RecyclerView.ViewHolder viewHolder) {
            return Float.MAX_VALUE; // disable swipe by dragging
        }

        @Override
        public void onChildDraw(
                @NonNull final Canvas c, @NonNull final RecyclerView recyclerView,
                final RecyclerView.ViewHolder viewHolder,
                float dX, final float dY, final int actionState,
                final boolean isCurrentlyActive) {
            if (!(viewHolder instanceof HorizontalSwipeHolder))
                return;
            final HorizontalSwipeHolder swipeHolder = (HorizontalSwipeHolder) viewHolder;

            final int adapterPosition = viewHolder.getAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION)
                return;

            if ((dX > 0 && swipeHolder.canRevealLeft(adapterPosition))
                    || (dX < 0 && swipeHolder.canRevealRight(adapterPosition))) {
                final Drawable drawable = dX > 0 ? swipeHolder.getLeftIcon(adapterPosition) : swipeHolder.getRightIcon(adapterPosition);

                final int drawableHeight = drawable.getIntrinsicHeight();
                final int drawableWidth = drawable.getIntrinsicWidth();
                final int drawableTop = viewHolder.itemView.getTop() + viewHolder.itemView.getHeight() / 2 - drawableHeight / 2;
                actionTriggerThreshold = starMargin * 2 + drawableWidth;
                if (dX > 0) {
                    // drag right
                    if (dX > actionTriggerThreshold) {
                        dX = actionTriggerThreshold;
                        if (isCurrentlyActive) {
                            drawable.setBounds(starMargin, drawableTop, starMargin + drawableWidth,
                                    drawableTop + drawableHeight);
                            drawable.draw(c);
                        }
                    }
                } else {
                    // drag left
                    if (dX < -actionTriggerThreshold) {
                        dX = -actionTriggerThreshold;
                        if (isCurrentlyActive) {
                            final int right = viewHolder.itemView.getWidth() - starMargin;
                            drawable.setBounds(right - drawableWidth, drawableTop, right,
                                    drawableTop + drawableHeight);
                            drawable.draw(c);
                        }
                    }
                }
            } else {
                dX = 0;
            }

            lastDX = dX;
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

            if (dX == 0 && lastDirection != 0) {
                if (lastDirection == ItemTouchHelper.RIGHT)
                    swipeHolder.onRevealLeft(adapterPosition);
                else if (lastDirection == ItemTouchHelper.LEFT)
                    swipeHolder.onRevealRight(adapterPosition);
                recyclerView.getAdapter().notifyItemChanged(adapterPosition);
                lastDirection = 0;
            }
        }

        @Override
        public void onSelectedChanged(final RecyclerView.ViewHolder viewHolder, final int actionState) {
            super.onSelectedChanged(viewHolder, actionState);

            if (actionState == ItemTouchHelper.ACTION_STATE_IDLE
                    && actionTriggerThreshold > 0
                    && Math.abs(lastDX) >= actionTriggerThreshold) {
                lastDirection = lastDX > 0 ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT;
            }
        }

        @Override
        public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder, final int direction) {
            throw new IllegalStateException();
        }

        @Override
        public boolean onMove(
                @NonNull final RecyclerView recyclerView, @NonNull final RecyclerView.ViewHolder viewHolder,
                @NonNull final RecyclerView.ViewHolder target) {
            throw new IllegalStateException();
        }
    }
}
