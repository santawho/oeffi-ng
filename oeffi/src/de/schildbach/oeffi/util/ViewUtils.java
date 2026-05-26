package de.schildbach.oeffi.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.RemoteViews;
import android.widget.TextView;

import de.schildbach.oeffi.R;

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

    public static void remoteViewsSetBackgroundColor(
            final RemoteViews remoteViews,
            final int viewId, final int color) {
        remoteViews.setInt(viewId, "setBackgroundColor", color);
    }

    public static void setCanceledStrikeThru(final TextView view, final boolean strikeThru) {
        view.getPaint().setStrikeThruText(strikeThru);
        if (strikeThru) {
            view.setForeground(new ColorDrawable() {
                {
                    setColor(view.getResources().getColor(R.color.fg_canceled_strikethru));
                }

                @Override
                public void draw(final Canvas canvas) {
                    final CharSequence text = view.getText();
                    final Rect bounds = new Rect();
                    view.getPaint().getTextBounds(text, 0, text.length(), bounds);
                    final int height = view.getHeight();
                    final int center = height / 2;
                    final int halfThickness = (height / 6) / 2;
                    setBounds(bounds.left, center - halfThickness, bounds.right, center + halfThickness);
                    super.draw(canvas);
                }
            });
        }
    }
}
