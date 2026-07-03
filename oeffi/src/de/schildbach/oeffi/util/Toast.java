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
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.core.view.LayoutInflaterCompat;

import de.schildbach.oeffi.R;
import okhttp3.internal.http.HttpMethod;

public class Toast {
    private final Context context;

    public Toast(final Context context) {
        this.context = context;
    }

    public final void toast(final int textResId, final Object... formatArgs) {
        customToast(textResId, android.widget.Toast.LENGTH_SHORT, formatArgs);
    }

    public final void toast(final CharSequence text) {
        customToast(text, android.widget.Toast.LENGTH_SHORT);
    }

    public final void longToast(final int textResId, final Object... formatArgs) {
        customToast(textResId, android.widget.Toast.LENGTH_LONG, formatArgs);
    }

    public final void longToast(final CharSequence text) {
        customToast(text, android.widget.Toast.LENGTH_LONG);
    }

    private void customToast(final int textResId, final int duration,
            final Object... formatArgs) {
        customToast(context.getString(textResId, formatArgs), duration);
    }

    private void customToast(final CharSequence text, final int duration) {
        final android.widget.Toast toast = new android.widget.Toast(context);
        toast.setDuration(duration);
        final View toastView = LayoutInflater.from(context).inflate(R.layout.toast, null);
        toast.setView(toastView);
        final TextView textView = toastView.findViewById(R.id.toast_text);
        textView.setText(Html.fromHtml(text.toString()));
        toast.show();
    }
}
