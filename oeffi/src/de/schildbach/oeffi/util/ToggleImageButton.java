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
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.Checkable;

import androidx.appcompat.widget.AppCompatImageButton;

public class ToggleImageButton extends AppCompatImageButton implements Checkable {
    public static interface OnCheckedChangeListener {
        void onCheckedChanged(final ToggleImageButton buttonView, final boolean isChecked);
    }

    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };

    private boolean isChecked;
    private boolean isFiring;
    private OnCheckedChangeListener onCheckedChangeListener;

    public ToggleImageButton(final Context context) {
        super(context);
    }

    public ToggleImageButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public ToggleImageButton(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setOnCheckedChangeListener(final OnCheckedChangeListener onCheckedChangeListener) {
        this.onCheckedChangeListener = onCheckedChangeListener;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(final boolean checked) {
        if (isChecked == checked)
            return;

        isChecked = checked;
        refreshDrawableState();

        if (!isFiring) {
            isFiring = true;
            if (onCheckedChangeListener != null)
                onCheckedChangeListener.onCheckedChanged(this, isChecked);
            isFiring = false;
        }
    }

    public void toggle() {
        setChecked(!isChecked);
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    @Override
    public int[] onCreateDrawableState(final int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked())
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        return drawableState;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Bundle bundle = new Bundle();
        bundle.putParcelable("super_state", super.onSaveInstanceState());
        bundle.putBoolean("is_checked", isChecked);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(final Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle bundle = (Bundle) state;
            super.onRestoreInstanceState(bundle.getParcelable("super_state"));
            setChecked(bundle.getBoolean("is_checked"));
        } else {
            super.onRestoreInstanceState(state);
        }
    }
}
