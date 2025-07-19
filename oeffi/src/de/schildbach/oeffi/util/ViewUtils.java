package de.schildbach.oeffi.util;

import android.view.View;

public class ViewUtils {
    public static void setVisibility(final View view, final boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public static boolean isVisible(final View view) {
        return view.getVisibility() == View.VISIBLE;
    }
}
