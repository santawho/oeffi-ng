package de.schildbach.oeffi.util;

import android.content.Context;
import android.content.res.TypedArray;
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

    public static int getAttrColorId(final Context context, final int attrColor) {
        try (final TypedArray ta = context.obtainStyledAttributes(new int[]{attrColor})) {
            return ta.getResourceId(0, android.R.color.black);
        }
    }

    public static int getAttrColor(final Context context, final int attrColor) {
        return context.getColor(getAttrColorId(context, attrColor));
    }
}
