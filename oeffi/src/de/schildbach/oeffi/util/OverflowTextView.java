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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.Nullable;

import de.schildbach.oeffi.R;

@SuppressLint("AppCompatCustomView")
public class OverflowTextView extends TextView {
    public OverflowTextView(final Context context) {
        this(context, null);
    }

    public OverflowTextView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        construct(context, attrs, 0, 0);
    }

    public OverflowTextView(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    private int overflowViewId = -1;

    public OverflowTextView(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        construct(context, attrs, defStyleAttr, defStyleRes);
    }

    private void construct(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        final int[] overflowTextViewAttrs = R.styleable.OverflowTextView;
        final TypedArray a = context.getTheme().obtainStyledAttributes(attrs, overflowTextViewAttrs, defStyleAttr, defStyleRes);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            saveAttributeDataForStyleable(context, overflowTextViewAttrs, attrs, a, defStyleAttr, defStyleRes);
        final int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            final int attr = a.getIndex(i);

            if (attr == R.styleable.OverflowTextView_overflow_id) {
                overflowViewId = a.getResourceId(attr, -1);
                break;
            }
        }
        a.recycle();
    }

    private CharSequence text;

    @Override
    public void setText(final CharSequence text, final BufferType type) {
        this.text = text;
        super.setText(text, type);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int lineCount = getLineCount();
        if (lineCount > 1) {
            final int endFirstLine = getLayout().getLineVisibleEnd(0);
            final int startSecondLine = getLayout().getLineStart(1);
            int posNonAlpha = -1;
            int posSpace = -1;
            for (int pos = startSecondLine - 1; pos > 0; --pos) {
                final char c = text.charAt(pos);
                if (Character.isSpaceChar(c)) {
                    posSpace = pos;
                    break;
                }
                if (posNonAlpha < 0 && !Character.isAlphabetic(c)) {
                    posNonAlpha = pos;
                    break;
                }
            }
            final CharSequence firstLine;
            final CharSequence remainingText;
            if (posSpace > 0) {
                firstLine = text.subSequence(0, posSpace);
                remainingText = text.subSequence(posSpace + 1, text.length());
            } else if (posNonAlpha > 0) {
                firstLine = text.subSequence(0, posNonAlpha);
                remainingText = text.subSequence(posNonAlpha, text.length());
            } else {
                firstLine = text.subSequence(0, endFirstLine);
                remainingText = text.subSequence(startSecondLine, text.length());
            }
            super.setText(firstLine, BufferType.NORMAL);
            final TextView overflowView = getOverflowView();
            overflowView.setText(remainingText);
            overflowView.setVisibility(View.VISIBLE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            final TextView overflowView = getOverflowView();
            overflowView.setVisibility(View.GONE);
        }
    }

    private TextView overflowView;

    private TextView getOverflowView() {
        if (overflowView == null) {
            ViewParent parent = getParent();
            while (parent != null) {
                if (parent instanceof ViewGroup) {
                    overflowView = ((ViewGroup) parent).findViewById(overflowViewId);
                    if (overflowView != null)
                        break;
                    parent = parent.getParent();
                }
            }
            if (overflowView == null)
                throw new RuntimeException("overflow view not found");
        }
        return overflowView;
    }
}
