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

package de.schildbach.oeffi.stations;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.PopupWindow;
import de.schildbach.oeffi.R;
import de.schildbach.pte.dto.Product;

import java.util.HashSet;
import java.util.Set;

public class StationsFilterPopup extends PopupWindow
        implements CompoundButton.OnCheckedChangeListener, OnLongClickListener {
    public interface Listener {
        void filterChanged(Set<Product> filter);
    }

    private final Set<Product> filter;
    private final Listener listener;

    public StationsFilterPopup(final Context context, final Set<Product> filter, final Listener listener) {
        super(context, null, 0, R.style.My_Widget_PopupMenu);
        setContentView(LayoutInflater.from(context).inflate(R.layout.stations_filter_popup_content, null));
        setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        setFocusable(true);

        this.filter = new HashSet<>(filter);
        this.listener = listener;

        final View contentView = getContentView();
        for (final Product product : Product.ALL_SELECTABLE) {
            final CheckBox checkBox = contentView.findViewWithTag(Character.toString(product.code));
            checkBox.setChecked(filter.contains(product));
            checkBox.setOnCheckedChangeListener(this);
            checkBox.setOnLongClickListener(this);
        }
    }

    public void onCheckedChanged(final CompoundButton v, final boolean isChecked) {
        final Product product = Product.fromCode(((String) v.getTag()).charAt(0));
        if (isChecked)
            filter.add(product);
        else
            filter.remove(product);
        notifyFilterChanged();
    }

    public boolean onLongClick(final View v) {
        final Product product = Product.fromCode(((String) v.getTag()).charAt(0));
        final View contentView = getContentView();
        for (final Product p : Product.ALL_SELECTABLE) {
            final CheckBox checkBox = contentView.findViewWithTag(Character.toString(p.code));
            checkBox.setChecked(p != product); // Implicit notify
        }
        return true;
    }

    private void notifyFilterChanged() {
        listener.filterChanged(new HashSet<>(filter));
    }
}
