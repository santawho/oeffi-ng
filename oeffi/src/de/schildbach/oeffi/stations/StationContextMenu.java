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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.RadioButton;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.directions.DirectionsShortcutActivity;
import de.schildbach.oeffi.plans.PlanActivity;
import de.schildbach.oeffi.plans.PlanContentProvider;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.util.Locale;

public class StationContextMenu extends PopupMenu {
    private static final Logger log = LoggerFactory.getLogger(StationContextMenu.class);

    public StationContextMenu(final Context context, final View anchor, final NetworkId network, final Location station,
            final Integer favState, final boolean showFavorite, final boolean showIgnore, final boolean showMap,
            final boolean showDepartures, final boolean showNearbyDepartures,
            final boolean showDirections, final boolean showDirectionsVia, final boolean showAlternativeDirections,
            final boolean showNavigateTo, final boolean showShortcut) {
        super(context, anchor);
        inflate(R.menu.stations_station_context);
        final Menu menu = getMenu();
        final boolean isFavorite = favState != null && favState == FavoriteStationsProvider.TYPE_FAVORITE;
        final boolean isIgnored = favState != null && favState == FavoriteStationsProvider.TYPE_IGNORE;
        menu.findItem(R.id.station_context_add_favorite).setVisible(showFavorite && !isFavorite);
        menu.findItem(R.id.station_context_remove_favorite).setVisible(showFavorite && isFavorite);
        menu.findItem(R.id.station_context_add_ignore).setVisible(showIgnore && !isIgnored);
        menu.findItem(R.id.station_context_remove_ignore).setVisible(showIgnore && isIgnored);
        menu.findItem(R.id.station_context_infopage).setVisible(station.infoUrl != null);
        final MenuItem mapItem = menu.findItem(R.id.station_context_map);
        if (showMap && station.hasCoord())
            prepareMapMenu(context, mapItem.getSubMenu(), network, station);
        else
            mapItem.setVisible(false);
        menu.findItem(R.id.station_context_show_departures).setVisible(showDepartures);
        menu.findItem(R.id.station_context_nearby_departures).setVisible(showNearbyDepartures);
        menu.findItem(R.id.station_context_directions_from).setVisible(showDirections);
        menu.findItem(R.id.station_context_directions_via).setVisible(showDirectionsVia);
        menu.findItem(R.id.station_context_directions_to).setVisible(showDirections);
        menu.findItem(R.id.station_context_launcher_shortcut).setVisible(showShortcut);
        menu.findItem(R.id.station_context_directions_alternative_from).setVisible(showAlternativeDirections);
        menu.findItem(R.id.station_context_navigate_to).setVisible(showNavigateTo);
    }

    public static AlertDialog createLauncherShortcutDialog(final Context context, final NetworkId networkId,
            final Location location) {
        final View view = LayoutInflater.from(context).inflate(R.layout.create_launcher_shortcut_dialog, null);

        final DialogBuilder builder = DialogBuilder.get(context);
        builder.setTitle(R.string.station_context_launcher_shortcut_title);
        String locName = location.uniqueShortName();
        ((EditText)view.findViewById(R.id.create_launcher_shortcut_dialog_name)).setText(locName);
        if (location.type != LocationType.STATION) {
            view.findViewById(R.id.create_launcher_shortcut_dialog_departures).setEnabled(false);
        }
        builder.setView(view);
        builder.setPositiveButton(R.string.create_launcher_shortcut_dialog_button_ok,
                (dialog, which) -> {
                    final boolean departuresChecked = ((RadioButton)view
                            .findViewById(R.id.create_launcher_shortcut_dialog_departures)).isChecked();
                    final EditText nameView = view
                            .findViewById(R.id.create_launcher_shortcut_dialog_name);
                    final String shortcutName = nameView.getText().toString();
                    final String shortcutId;
                    final Intent shortcutIntent;
                    final int iconId;
                    final int defaultNameId;
                    final Class<? extends Activity> activityClass;
                    if (departuresChecked) {
                        shortcutId = "departures-at-" + networkId.name() + "-" + location.id;
                        shortcutIntent = StationDetailsActivity.fillIntent(
                                new Intent(Intent.ACTION_MAIN, null, context, StationDetailsActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                                networkId, location, null);
                        iconId = R.mipmap.ic_oeffi_stations_color_48dp;
                        defaultNameId = R.string.departures_shortcut_default_name;
                        activityClass = StationDetailsActivity.class;
                    } else {
                        shortcutId = "directions-to-" + networkId.name() + "-" + location.id;
                        shortcutIntent = new Intent(Intent.ACTION_MAIN, null, context,
                                DirectionsShortcutActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_NETWORK, networkId.name());
                        shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_TYPE, location.type.name());
                        if (location.hasId())
                            shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_ID, location.id);
                        if (location.hasCoord()) {
                            shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_LAT,
                                    location.getLatAs1E6());
                            shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_LON,
                                    location.getLonAs1E6());
                        }
                        shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_PLACE, location.place);
                        shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_NAME, location.name);
                        iconId = R.mipmap.ic_oeffi_directions_color_48dp;
                        defaultNameId = R.string.directions_shortcut_default_name;
                        activityClass = DirectionsActivity.class;
                    }
                    log.info("creating launcher shortcut {} to {}", shortcutId, location);
                    ShortcutManagerCompat.requestPinShortcut(context,
                            new ShortcutInfoCompat.Builder(context, shortcutId)
                                    .setActivity(new ComponentName(context, activityClass))
                                    .setShortLabel(shortcutName.length() > 0 ? shortcutName : context.getString(defaultNameId))
                                    .setIcon(IconCompat.createWithResource(context, iconId))
                                    .setIntent(shortcutIntent).build(),
                            null);
                });
        builder.setNegativeButton(R.string.button_cancel, null);
        return builder.create();
    }

    public static void prepareMapMenu(final Context context, final Menu menu, final NetworkId network,
            final Location location) {
        new MenuInflater(context).inflate(R.menu.station_map_context, menu);
        final MenuItem mapsItem = menu.findItem(R.id.station_map_context_maps);

        if (location.hasCoord()) {
            final double lat = location.getLatAsDouble();
            final double lon = location.getLonAsDouble();
            final String name = location.name;

            final Intent mapsIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(String.format(Locale.ENGLISH, "geo:%.6f,%.6f?q=%.6f,%.6f%s", lat, lon, lat, lon,
                            name != null ? '(' + URLEncoder.encode(name.replaceAll("[()]", "")) + ')' : "")));
            mapsItem.setOnMenuItemClickListener(item -> {
                context.startActivity(mapsIntent);
                return true;
            });
            mapsItem.setVisible(true);
        } else {
            mapsItem.setVisible(false);
        }

        final ContentResolver contentResolver = context.getContentResolver();
        final Cursor stationsCursor = contentResolver.query(PlanContentProvider.stationsUri(network, location.id), null,
                null, null, null);
        if (stationsCursor != null) {
            final int planIdColumn = stationsCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_STATION_PLAN_ID);
            while (stationsCursor.moveToNext()) {
                final String planId = stationsCursor.getString(planIdColumn);
                final Cursor plansCursor = contentResolver.query(PlanContentProvider.planUri(planId), null, null, null,
                        null);
                plansCursor.moveToFirst();
                final String planName = plansCursor
                        .getString(plansCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_NAME));
                plansCursor.close();
                menu.add(planName).setOnMenuItemClickListener(item -> {
                    PlanActivity.start(context, planId, location.id);
                    return true;
                });
            }
            stationsCursor.close();
        }
    }
}
