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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import de.schildbach.oeffi.R;

public class FilterSearchView extends FrameLayout {
    public interface TextChangeListener {
        void onFilterChanged(final String filterText);
    }

    private EditText editText;
    private TextChangeListener textChangeListener;
    private ImageView clearButton;
    private OnClickListener clearButtonOnClickListener;

    public FilterSearchView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        editText = findViewById(R.id.filter_text);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(final Editable s) {
                if (textChangeListener != null)
                    textChangeListener.onFilterChanged(s.toString());
            }

            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) { }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) { }
        });

        clearButton = findViewById(R.id.filter_clear);
        clearButton.setOnClickListener(v -> {
            editText.setText(null);
            if (clearButtonOnClickListener != null)
                clearButtonOnClickListener.onClick(v);
        });
    }

    public void setTextChangeListener(final TextChangeListener textChangeListener) {
        this.textChangeListener = textChangeListener;
    }

    public void setOnClearListener(final OnClickListener onClickListener) {
        this.clearButtonOnClickListener = onClickListener;
    }

    public void setHint(final int resId) {
        editText.setHint(resId);
    }

    public void setHint(final CharSequence hintText) {
        editText.setHint(hintText);
    }

    public Editable getText() {
        return editText.getText();
    }
}
