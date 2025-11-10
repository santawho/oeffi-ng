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

package de.schildbach.oeffi.util.locationview;

import android.content.Context;
import android.text.Html;
import android.util.AttributeSet;
import android.widget.TextView;
import de.schildbach.oeffi.R;
import de.schildbach.pte.dto.Location;

import java.util.Locale;

public class LocationTextView extends TextView {
    private String label = null;
    private Location location = null;
    private boolean showLocationType = true;

    public LocationTextView(final Context context) {
        super(context);
    }

    public LocationTextView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public void setLabel(final int labelResId) {
        this.label = getContext().getString(labelResId);
        update();
    }

    public void setLabel(final String label) {
        this.label = label;
        update();
    }

    public void setShowLocationType(final boolean showLocationType) {
        this.showLocationType = showLocationType;
        update();
    }

    public void setLocation(final Location location) {
        this.location = location;
        update();
    }

    private void update() {
        if (location != null) {
            final StringBuilder text = new StringBuilder();
            if (label != null)
                text.append("<b><u>").append(label).append("</u></b><br>");
            if (location.place != null)
                text.append(location.place).append(",<br>");
            if (location.name != null)
                text.append("<b>").append(location.name).append("</b>");
            if (text.length() == 0 && location.hasCoord())
                text.append(getContext().getString(R.string.directions_location_view_coordinate)).append(":<br/>")
                        .append(String.format(Locale.ENGLISH, "%1$.6f, %2$.6f", location.getLatAsDouble(),
                                location.getLonAsDouble()));
            setText(Html.fromHtml(text.toString(), Html.FROM_HTML_MODE_COMPACT));
            setCompoundDrawablesWithIntrinsicBounds(
                    showLocationType ? LocationView.locationTypeIconRes(location.type) : 0, 0, 0, 0);
        } else {
            setText(null);
            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }
}
