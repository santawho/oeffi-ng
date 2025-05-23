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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.ActionProvider;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.oeffi.R;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class NavigationMenuAdapter extends RecyclerView.Adapter<NavigationMenuAdapter.ViewHolder> {
    private final Resources res;
    private final LayoutInflater inflater;
    private final List<NavigationMenuItem> menuItems = new LinkedList<>();
    private final List<NavigationMenuItem> visibleItems = new LinkedList<>();
    private final MenuItem.OnMenuItemClickListener menuClickListener;

    public NavigationMenuAdapter(final Context context, final MenuItem.OnMenuItemClickListener menuClickListener) {
        this.res = context.getResources();
        this.inflater = LayoutInflater.from(context);
        this.menuClickListener = menuClickListener;

        setHasStableIds(true);
    }

    @Override
    public void onAttachedToRecyclerView(final RecyclerView recyclerView) {
        visibleItems.clear();
        for (final NavigationMenuItem item : menuItems)
            if (item.isVisible())
                visibleItems.add(item);
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    @Override
    public long getItemId(final int position) {
        return visibleItems.get(position).getItemId();
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.navigation_drawer_entry, parent, false), menuClickListener);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        holder.bind(visibleItems.get(position));

        // Offset the first list item so that it isn't stuck under a transparent status bar.
        if (position == 0) {
            final int statusHeight = res
                    .getDimensionPixelSize(res.getIdentifier("status_bar_height", "dimen", "android"));
            holder.itemView.setPadding(0, statusHeight, 0, 0);
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView commentView;
        private final MenuItem.OnMenuItemClickListener menuClickListener;

        public ViewHolder(final View itemView, final MenuItem.OnMenuItemClickListener menuClickListener) {
            super(itemView);

            iconView = itemView.findViewById(R.id.navigation_drawer_entry_icon);
            titleView = itemView.findViewById(R.id.navigation_drawer_entry_title);
            commentView = itemView.findViewById(R.id.navigation_drawer_entry_comment);

            this.menuClickListener = menuClickListener;
        }

        public void bind(final MenuItem item) {
            itemView.setActivated(item.isChecked());
            itemView.setFocusable(item.isEnabled());
            if (item.isEnabled()) {
                itemView.setOnClickListener(v -> {
                    item.setActionView(v);
                    menuClickListener.onMenuItemClick(item);
                });
            }

            final boolean primaryItem = item.getTitleCondensed() != null;

            iconView.setImageDrawable(item.getIcon());
            if (item.isChecked())
                iconView.setImageState(new int[] { android.R.attr.state_checked }, false);

            titleView.setText(item.getTitle());
            titleView.setTypeface(primaryItem ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    res.getDimension(primaryItem ? R.dimen.font_size_xlarge : R.dimen.font_size_large));

            commentView.setText(item.getTitleCondensed());
            commentView.setVisibility(primaryItem ? View.VISIBLE : View.GONE);

        }
    }

    public Menu getMenu() {
        return menu;
    }

    private final Menu menu = new Menu() {
        public MenuItem findItem(final int id) {
            for (final MenuItem item : menuItems)
                if (item.getItemId() == id)
                    return item;

            return null;
        }

        public MenuItem getItem(final int index) {
            return menuItems.get(index);
        }

        public int size() {
            return menuItems.size();
        }

        public MenuItem add(final CharSequence title) {
            return add(Menu.NONE, Menu.NONE, 0, title);
        }

        public MenuItem add(final int titleRes) {
            throw new UnsupportedOperationException();
        }

        public MenuItem add(final int groupId, final int itemId, final int order, final CharSequence title) {
            final NavigationMenuItem item = new NavigationMenuItem(groupId, itemId, order, title);

            final int index = Collections.binarySearch(menuItems, item);

            if (index >= 0) {
                // found, scan for last
                final int size = menuItems.size();

                int insert = index;
                while (insert < size && menuItems.get(insert).order == order)
                    insert++;

                // and insert after
                menuItems.add(insert, item);
            } else {
                // not found, index at insertion point
                menuItems.add(-(index + 1), item);
            }

            notifyDataSetChanged();

            return item;
        }

        public MenuItem add(final int groupId, final int itemId, final int order, final int titleRes) {
            throw new UnsupportedOperationException();
        }

        public void removeItem(final int id) {
            throw new UnsupportedOperationException();
        }

        public SubMenu addSubMenu(final CharSequence title) {
            throw new UnsupportedOperationException();
        }

        public SubMenu addSubMenu(final int titleRes) {
            throw new UnsupportedOperationException();
        }

        public SubMenu addSubMenu(final int groupId, final int itemId, final int order, final CharSequence title) {
            throw new UnsupportedOperationException();
        }

        public SubMenu addSubMenu(final int groupId, final int itemId, final int order, final int titleRes) {
            throw new UnsupportedOperationException();
        }

        public void removeGroup(final int groupId) {
            throw new UnsupportedOperationException();
        }

        public boolean hasVisibleItems() {
            for (final MenuItem item : menuItems)
                if (item.isVisible())
                    return true;

            return false;
        }

        public void clear() {
            menuItems.clear();

            notifyDataSetChanged();
        }

        public int addIntentOptions(final int groupId, final int itemId, final int order, final ComponentName caller,
                final Intent[] specifics, final Intent intent, final int flags, final MenuItem[] outSpecificItems) {
            throw new UnsupportedOperationException();
        }

        public void close() {
            throw new UnsupportedOperationException();
        }

        public boolean isShortcutKey(final int keyCode, final KeyEvent event) {
            throw new UnsupportedOperationException();
        }

        public boolean performIdentifierAction(final int id, final int flags) {
            throw new UnsupportedOperationException();
        }

        public boolean performShortcut(final int keyCode, final KeyEvent event, final int flags) {
            throw new UnsupportedOperationException();
        }

        public void setGroupCheckable(final int group, final boolean checkable, final boolean exclusive) {
            throw new UnsupportedOperationException();
        }

        public void setGroupEnabled(final int group, final boolean enabled) {
            throw new UnsupportedOperationException();
        }

        public void setGroupVisible(final int group, final boolean visible) {
            throw new UnsupportedOperationException();
        }

        public void setQwertyMode(final boolean isQwerty) {
            throw new UnsupportedOperationException();
        }
    };

    private final class NavigationMenuItem implements MenuItem, Comparable<MenuItem> {
        private final int groupId;
        private final int itemId;
        private final int order;
        private View actionView;

        private CharSequence title = null;
        private CharSequence titleCondensed = null;
        private Drawable icon = null;
        private boolean enabled = true;
        private boolean visible = true;
        private boolean checkable = false;
        private boolean checked = false;
        private char numericShortcut = 0;
        private char alphabeticShortcut = 0;

        private NavigationMenuItem(final int groupId, final int itemId, final int order, final CharSequence title) {
            this.groupId = groupId;
            this.itemId = itemId;
            this.order = order;

            setTitle(title);
        }

        public char getAlphabeticShortcut() {
            return alphabeticShortcut;
        }

        public int getGroupId() {
            return groupId;
        }

        public Drawable getIcon() {
            return icon;
        }

        public Intent getIntent() {
            throw new UnsupportedOperationException();
        }

        public int getItemId() {
            return itemId;
        }

        public ContextMenuInfo getMenuInfo() {
            throw new UnsupportedOperationException();
        }

        public char getNumericShortcut() {
            return numericShortcut;
        }

        public int getOrder() {
            return order;
        }

        public SubMenu getSubMenu() {
            throw new UnsupportedOperationException();
        }

        public CharSequence getTitle() {
            return title;
        }

        public CharSequence getTitleCondensed() {
            return titleCondensed;
        }

        public boolean hasSubMenu() {
            return false;
        }

        public boolean isCheckable() {
            return checkable;
        }

        public boolean isChecked() {
            return checked;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isVisible() {
            return visible;
        }

        public MenuItem setAlphabeticShortcut(final char alphabeticShortcut) {
            this.alphabeticShortcut = alphabeticShortcut;

            notifyDataSetChanged();

            return this;
        }

        public MenuItem setCheckable(final boolean checkable) {
            this.checkable = checkable;

            notifyDataSetChanged();

            return this;
        }

        public MenuItem setChecked(final boolean checked) {
            this.checked = checked;

            notifyDataSetChanged();

            return this;
        }

        public MenuItem setEnabled(final boolean enabled) {
            this.enabled = enabled;

            notifyDataSetChanged();

            return this;
        }

        public MenuItem setIcon(final Drawable icon) {
            this.icon = icon;

            notifyDataSetChanged();

            return this;
        }

        public MenuItem setIcon(final int iconRes) {
            return setIcon(iconRes != 0 ? res.getDrawable(iconRes) : null);
        }

        public MenuItem setIntent(final Intent intent) {
            throw new UnsupportedOperationException();
        }

        public MenuItem setNumericShortcut(final char numericShortcut) {
            this.numericShortcut = numericShortcut;

            notifyDataSetChanged();

            return this;
        }

        public MenuItem setOnMenuItemClickListener(final OnMenuItemClickListener menuItemClickListener) {
            throw new UnsupportedOperationException();
        }

        public MenuItem setShortcut(final char numericShortcut, final char alphabeticShortcut) {
            setNumericShortcut(numericShortcut);
            setAlphabeticShortcut(alphabeticShortcut);

            return this;
        }

        public MenuItem setTitle(final CharSequence title) {
            this.title = title;

            notifyDataSetChanged();

            return this;
        }

        public MenuItem setTitle(final int titleRes) {
            return setTitle(res.getString(titleRes));
        }

        public MenuItem setTitleCondensed(final CharSequence titleCondensed) {
            this.titleCondensed = titleCondensed;

            notifyDataSetChanged();

            return this;
        }

        public MenuItem setVisible(final boolean visible) {
            this.visible = visible;

            notifyDataSetChanged();

            return this;
        }

        public boolean collapseActionView() {
            throw new UnsupportedOperationException();
        }

        public boolean expandActionView() {
            throw new UnsupportedOperationException();
        }

        public ActionProvider getActionProvider() {
            throw new UnsupportedOperationException();
        }

        public View getActionView() {
            return actionView;
        }

        public boolean isActionViewExpanded() {
            throw new UnsupportedOperationException();
        }

        public MenuItem setActionProvider(final ActionProvider actionProvider) {
            throw new UnsupportedOperationException();
        }

        public MenuItem setActionView(final View view) {
            this.actionView = view;

            notifyDataSetChanged();

            return this;
        }

        public MenuItem setActionView(final int resId) {
            throw new UnsupportedOperationException();
        }

        public MenuItem setOnActionExpandListener(final OnActionExpandListener listener) {
            throw new UnsupportedOperationException();
        }

        public void setShowAsAction(final int actionEnum) {
            throw new UnsupportedOperationException();
        }

        public MenuItem setShowAsActionFlags(final int actionEnum) {
            throw new UnsupportedOperationException();
        }

        public int compareTo(final MenuItem o) {
            if (this.getOrder() == o.getOrder())
                return 0;

            return this.getOrder() > o.getOrder() ? 1 : -1;
        }
    }
}
