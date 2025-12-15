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
import android.content.res.Resources;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import de.schildbach.oeffi.R;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class MultiDrawable extends AnimationDrawable {
    private final Resources res;
    private final Map<Integer, Integer> drawableIndexes = new HashMap<>();

    public MultiDrawable(final Context context) {
        this.res = context.getResources();
        setFadeDuration(250);
        setTint(res.getColor(R.color.fg_less_significant));
    }

    private void setFadeDuration(final int ms) {
        setEnterFadeDuration(ms);
        setExitFadeDuration(ms);
    }

    public int add(final int drawableResId) {
        final int nextIndex = drawableIndexes.size();
        drawableIndexes.put(drawableResId, nextIndex);
        final Drawable drawable = res.getDrawable(drawableResId).mutate();
        addFrame(drawable, 0);
        return nextIndex;
    }

    public void selectDrawableByResId(final int resId) {
        selectDrawable(requireNonNull(drawableIndexes.get(resId), "unknown resId"));
    }
}
