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
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.schildbach.oeffi.R;

public class PopupMenu extends androidx.appcompat.widget.PopupMenu {
    public static void setForceShowIcon(final android.widget.PopupMenu popupMenu, final boolean forceShowIcon) {
        _setForceShowIcon(popupMenu, forceShowIcon);
    }

    public static void setForceShowIcon(final androidx.appcompat.widget.PopupMenu popupMenu, final boolean forceShowIcon) {
        _setForceShowIcon(popupMenu, forceShowIcon);
    }

    private static void _setForceShowIcon(final Object popupMenu, final boolean forceShowIcon) {
        try {
            Class<?> classPopupMenu = Class.forName(popupMenu.getClass().getName());
            Field mPopup = null;
            NoSuchFieldException firstNsfe = null;
            while (classPopupMenu != null) {
                try {
                    mPopup = classPopupMenu.getDeclaredField("mPopup");
                    break;
                } catch (final NoSuchFieldException nsfe) {
                    if (firstNsfe == null) firstNsfe = nsfe;
                    classPopupMenu = classPopupMenu.getSuperclass();
                }
            }
            if (mPopup == null) throw firstNsfe;
            mPopup.setAccessible(true);
            final Object menuPopupHelper = mPopup.get(popupMenu);
            final Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
            final Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
            setForceIcons.invoke(menuPopupHelper, forceShowIcon);
        } catch (final Exception x) {
            // Swallow
        }
    }

    final Context context;
    final int menuResId;

    public PopupMenu(@NonNull final Context context, @NonNull final View anchor) {
        this(context, anchor, Gravity.NO_GRAVITY);
    }

    public PopupMenu(@NonNull final Context context, final int menuResId, @NonNull final View anchor) {
        this(context, menuResId, anchor, Gravity.NO_GRAVITY);
    }

    public PopupMenu(@NonNull final Context context, @NonNull final View anchor, final int gravity) {
        this(context, anchor, gravity, android.R.attr.popupMenuStyle, 0);
    }

    public PopupMenu(final Context context, final int menuResId, final View anchor, final int gravity) {
        this(context, menuResId, anchor, gravity, android.R.attr.popupMenuStyle, 0);
    }

    public PopupMenu(@NonNull final Context context, @NonNull final View anchor, final int gravity, final int popupStyleAttr, final int popupStyleRes) {
        this(context, 0, anchor, gravity, popupStyleAttr, popupStyleRes);
    }

    public PopupMenu(final Context context, final int menuResId, final View anchor, final int gravity, final int popupStyleAttr, final int popupStyleRes) {
        super(context, anchor, gravity, popupStyleAttr, popupStyleRes);

        this.context = context;
        this.menuResId = menuResId;

        if (menuResId != 0)
            inflate(menuResId, getMenu());

        final int iconColor = context.getColor(R.color.fg_significant);
        final Drawable defaultIcon = context.getDrawable(R.drawable.ic_dot_white_24dp);
        defaultIcon.setTint(iconColor);
        setupIcons(getMenu(), iconColor, defaultIcon);
    }

    public MenuItem findItem(final int itemId) {
        return getMenu().findItem(itemId);
    }

    public SubMenu addSubMenu(final int menuResId, final String title) {
        final SubMenu subMenu = getMenu().addSubMenu(title);
        inflate(menuResId, subMenu);
        return subMenu;
    }

    private void inflate(final int menuResId, final Menu menu) {
        new MenuInflater(context).inflate(menuResId, menu);
        setForceShowIcon(true);
    }

    @Override
    public void setForceShowIcon(final boolean forceShowIcon) {
        setForceShowIcon(this, forceShowIcon);
    }

    private static void setupIcons(final Menu menu, final int iconColor, final Drawable defaultIcon) {
        final int size = menu.size();
        if (size == 0)
            return;
        boolean haveSomeIcons = false;
        boolean someIconsMissing = false;
        for (int itemIndex = 0; itemIndex < size; ++itemIndex) {
            final MenuItem item = menu.getItem(itemIndex);
            final Drawable icon = item.getIcon();
            if (icon != null) {
                haveSomeIcons = true;
                icon.setTint(iconColor);
            } else {
                someIconsMissing = true;
            }
            final SubMenu subMenu = item.getSubMenu();
            if (subMenu != null)
                setupIcons(subMenu, iconColor, defaultIcon);
        }
        if (haveSomeIcons && someIconsMissing) {
            for (int itemIndex = 0; itemIndex < size; ++itemIndex) {
                final MenuItem item = menu.getItem(itemIndex);
                final Drawable icon = item.getIcon();
                if (icon == null)
                    item.setIcon(defaultIcon);
            }
        }
    }
}
