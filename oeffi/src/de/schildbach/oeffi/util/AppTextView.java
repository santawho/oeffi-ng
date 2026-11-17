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

import androidx.annotation.Nullable;

public class AppTextView extends androidx.appcompat.widget.AppCompatTextView {
    public AppTextView(final Context context) {
        this(context, null);
    }

    public AppTextView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        construct(context, attrs, 0, 0);
    }

    public AppTextView(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AppTextView(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
//        super(context, attrs, defStyleAttr, defStyleRes);
        super(context, attrs, defStyleAttr);
        construct(context, attrs, defStyleAttr, defStyleRes);
    }

    private void construct(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        setTypeface(FontManager.appTypeFace, getTypeface().getStyle());
    }
}
