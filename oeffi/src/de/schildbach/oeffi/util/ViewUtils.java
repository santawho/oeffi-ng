package de.schildbach.oeffi.util;

import android.view.View;

public class ViewUtils {
    public static void setVisibility(final View view, final boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
