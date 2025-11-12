package de.schildbach.oeffi.util;

import android.view.View;
import android.view.ViewParent;

public class ViewUtils {
    public static void setVisibility(final View view, final boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public static boolean isVisible(final View view) {
        View v = view;
        while (v != null) {
            final int visibility = view.getVisibility();
            if (visibility != View.VISIBLE)
                return false;
            final ViewParent parent = v.getParent();
            if (!(parent instanceof View))
                break;
            v = (View) parent;
        }
        return true;
    }
}
