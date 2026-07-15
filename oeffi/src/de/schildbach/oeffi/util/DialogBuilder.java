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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;

import de.schildbach.oeffi.R;

public class DialogBuilder extends AlertDialog.Builder {
    public static DialogBuilder get(final Context context) {
        return new DialogBuilder(context, 0);
    }

    public static DialogBuilder get(final Context context, final int layoutResId) {
        return new DialogBuilder(context, 0)
                .setView(((Activity) context).getLayoutInflater().inflate(layoutResId, null));
    }

    public static DialogBuilder warn(final Context context, final int titleResId) {
        final DialogBuilder builder = get(context);
        builder.setIcon(R.drawable.ic_warning_amber_24dp);
        builder.setTitle(titleResId);
        return builder;
    }

    private DialogBuilder(final Context context, final int theme) {
        super(context, theme);
    }

    private View customView;

    public View getView() {
        return customView;
    }

    @Override
    public DialogBuilder setView(final View view) {
        super.setView(view);
        customView = view;
        return this;
    }

    public <T extends View> T findViewById(final int resId) {
        return customView.findViewById(resId);
    }

    private boolean canceledOnTouchOutside;

    public DialogBuilder setCanceledOnTouchOutside(final boolean canceledOnTouchOutside) {
        this.canceledOnTouchOutside = canceledOnTouchOutside;
        return this;
    }

    @Override
    public AlertDialog create() {
        final AlertDialog dialog = super.create();
        dialog.setCanceledOnTouchOutside(canceledOnTouchOutside);
        return dialog;
    }
}
