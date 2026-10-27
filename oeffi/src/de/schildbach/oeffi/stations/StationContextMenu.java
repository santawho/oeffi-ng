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
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.directions.DirectionsShortcutActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.MapPopupMenu;
import de.schildbach.oeffi.util.PopupMenu;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StationContextMenu extends PopupMenu {
    private static final Logger log = LoggerFactory.getLogger(StationContextMenu.class);

    public StationContextMenu(
            final Activity context, final View anchor,
            final NetworkId network, final Location station,
            final Integer favState,
            final boolean showFavorite, final boolean showIgnore, final boolean showRename,
            final boolean showMap,
            final boolean showDepartures, final boolean showNearbyDepartures,
            final boolean showTravelAlarmDeparture, final boolean showTravelAlarmArrival,
            final boolean showDirections, final boolean showDirectionsVia, final boolean showAlternativeDirections,
            final boolean showNavigateTo, final boolean showShortcut) {
        super(context, R.menu.stations_station_context, anchor);
        final boolean isFavorite = favState != null && favState == FavoriteStationsProvider.TYPE_FAVORITE;
        final boolean isIgnored = favState != null && favState == FavoriteStationsProvider.TYPE_IGNORE;
        findItem(R.id.station_context_add_favorite).setVisible(showFavorite && !isFavorite);
        findItem(R.id.station_context_rename_favorite).setVisible(showRename && isFavorite);
        findItem(R.id.station_context_remove_favorite).setVisible(showFavorite && isFavorite);
        findItem(R.id.station_context_add_ignore).setVisible(showIgnore && !isIgnored);
        findItem(R.id.station_context_remove_ignore).setVisible(showIgnore && isIgnored);
        final String infoUrl = NetworkProviderFactory.provider(network).getLocationInfoUrl(station);
        findItem(R.id.station_context_infopage).setVisible(infoUrl != null);
        final MenuItem mapItem = findItem(R.id.station_context_map);
        if (showMap && station.hasCoord())
            MapPopupMenu.setupSubMenu(context, mapItem, network, station);
        else
            mapItem.setVisible(false);
        findItem(R.id.station_context_show_departures).setVisible(showDepartures);
        findItem(R.id.station_context_nearby_departures).setVisible(showNearbyDepartures);
        findItem(R.id.station_context_set_departure_travel_alarm).setVisible(showTravelAlarmDeparture);
        findItem(R.id.station_context_set_arrival_travel_alarm).setVisible(showTravelAlarmArrival);
        findItem(R.id.station_context_directions_from).setVisible(showDirections);
        findItem(R.id.station_context_directions_via).setVisible(showDirectionsVia);
        findItem(R.id.station_context_directions_to).setVisible(showDirections);
        findItem(R.id.station_context_launcher_shortcut).setVisible(showShortcut);
        findItem(R.id.station_context_directions_alternative_from).setVisible(showAlternativeDirections);
        findItem(R.id.station_context_navigate_to).setVisible(showNavigateTo);
        setForceShowIcon(true);
    }

    public static void showLauncherShortcutDialog(
            final Context context,
            final NetworkId networkId,
            final Location location) {
        final DialogBuilder builder = DialogBuilder.get(context, R.layout.create_launcher_shortcut_dialog);
        final EditText nameView = builder.findViewById(R.id.create_launcher_shortcut_dialog_name);
        nameView.setText(location.uniqueShortName());
        final RadioButton departuresShortcutRadioButton = builder.findViewById(R.id.create_launcher_shortcut_dialog_departures);
        final RadioButton departuresWidgetRadioButton = builder.findViewById(R.id.create_launcher_shortcut_dialog_departures_widget);
        final boolean isStation = location.type == LocationType.STATION;
        departuresShortcutRadioButton.setEnabled(isStation);
        final AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        final boolean widgetSupported = widgetManager.isRequestPinAppWidgetSupported();
        departuresWidgetRadioButton.setEnabled(isStation && widgetSupported);
        builder
            .setTitle(R.string.station_context_launcher_shortcut_title)
            .setPositiveButton(R.string.create_launcher_shortcut_dialog_button_ok, (dialog, which) -> {
                final boolean departuresShortcutChecked = departuresShortcutRadioButton.isChecked();
                final boolean departuresWidgetChecked = departuresWidgetRadioButton.isChecked();
                final String shortcutName = nameView.getText().toString();
                final String shortcutId;
                final Intent shortcutIntent;
                final int iconId;
                final int defaultNameId;
                final Class<? extends Activity> activityClass;
                if (departuresWidgetChecked) {
                    WidgetPinningReceiver.name = shortcutName;
                    WidgetPinningReceiver.networkId = networkId;
                    WidgetPinningReceiver.location = location;
                    final ComponentName componentName = new ComponentName(context, NearestFavoriteStationWidgetProvider.class);
                    WidgetPinningReceiver.oldWidgetIds = widgetManager.getAppWidgetIds(componentName);
                    widgetManager.requestPinAppWidget(
                            componentName,
                            null,
                            PendingIntent.getBroadcast(
                                    context, 0,
                                    new Intent(context, WidgetPinningReceiver.class),
                                    PendingIntent.FLAG_MUTABLE));
                } else {
                    if (departuresShortcutChecked) {
                        shortcutId = "departures-at-" + networkId.name() + "-" + location.id + "-" + shortcutName;
                        shortcutIntent = StationDetailsActivity.fillIntent(
                                new Intent(Intent.ACTION_MAIN, null, context, StationDetailsActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                                networkId, location, null);
                        iconId = R.mipmap.ic_oeffi_ng_stations_color_48dp;
                        defaultNameId = R.string.departures_shortcut_default_name;
                        activityClass = StationDetailsActivity.class;
                    } else {
                        shortcutId = "directions-to-" + networkId.name() + "-" + location.id + "-" + shortcutName;
                        shortcutIntent = DirectionsShortcutActivity.fillIntent(
                                new Intent(Intent.ACTION_MAIN, null, context, DirectionsShortcutActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                                networkId, location);
                        iconId = R.mipmap.ic_oeffi_ng_directions_color_48dp;
                        defaultNameId = R.string.directions_shortcut_default_name;
                        activityClass = DirectionsActivity.class;
                    }
                    log.info("creating launcher shortcut {} to {}", shortcutId, location);
                    final String shortLabel = !shortcutName.isEmpty() ? shortcutName : context.getString(defaultNameId);
                    ShortcutManagerCompat.requestPinShortcut(context,
                            new ShortcutInfoCompat.Builder(context, shortcutId)
                                    .setActivity(new ComponentName(context, activityClass))
                                    .setShortLabel(shortLabel)
                                    .setIcon(IconCompat.createWithResource(context, iconId))
                                    .setIntent(shortcutIntent).build(),
                            null);
                }
            })
            .setNegativeButton(R.string.button_cancel, null)
            .show();
    }

    public static class WidgetPinningReceiver extends BroadcastReceiver {
        public static String name;
        public static NetworkId networkId;
        public static Location location;
        public static int[] oldWidgetIds;

        @Override
        public void onReceive(final Context context, final Intent intent) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                final ComponentName componentName = new ComponentName(context, NearestFavoriteStationWidgetProvider.class);
                final AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
                final int[] newWidgetIds = widgetManager.getAppWidgetIds(componentName);
                if (newWidgetIds == null || newWidgetIds.length == 0)
                    return;
                if (oldWidgetIds == null) {
                    widgetId = newWidgetIds[0];
                } else {
                    NEW: for (final int newWidgetId : newWidgetIds) {
                        for (final int oldWidgetId : oldWidgetIds) {
                            if (oldWidgetId == newWidgetId)
                                continue NEW;
                        }
                        widgetId = newWidgetId;
                        break;
                    }
                }
            }

            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID)
                NearestFavoriteStationWidgetService.configureWidget(context, widgetId, name, networkId, location);
        }
    }
}
