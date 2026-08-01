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

package de.schildbach.oeffi.preference;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.directions.driverops.OperationsActivity;
import de.schildbach.oeffi.plans.PlansPickerActivity;
import de.schildbach.oeffi.stations.FavoriteStationsActivity;
import de.schildbach.oeffi.stations.StationsActivity;

public class ShortcutsFragment extends PreferenceFragment {

    public static final String KEY_SHORTCUTS_DIRECTIONS = "shortcuts_directions";
    public static final String KEY_SHORTCUTS_STATIONS = "shortcuts_stations";
    public static final String KEY_SHORTCUTS_FAVORITES = "shortcuts_favorites";
    public static final String KEY_SHORTCUTS_PLANS = "shortcuts_plans";
    public static final String KEY_SHORTCUTS_OPERATIONS = "shortcuts_operations";

    @Override
    public void onCreatePreferences(@androidx.annotation.Nullable final Bundle savedInstanceState, @androidx.annotation.Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_shortcuts);

        setupActionPreference(KEY_SHORTCUTS_DIRECTIONS, DirectionsShortcutActionHandler.class);
        setupActionPreference(KEY_SHORTCUTS_STATIONS, StationsShortcutActionHandler.class);
        setupActionPreference(KEY_SHORTCUTS_FAVORITES, FavoritesShortcutActionHandler.class);
        setupActionPreference(KEY_SHORTCUTS_PLANS, PlansShortcutActionHandler.class);

        if (Application.getInstance().isDriverMode())
            setupActionPreference(KEY_SHORTCUTS_OPERATIONS, OperationsShortcutActionHandler.class);
        else
            removePreference(KEY_SHORTCUTS_OPERATIONS);
    }

    public static class DirectionsShortcutActionHandler extends ActionHandler {
        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            createShortcut(context,
                    DirectionsActivity.class,
                    R.string.directions_icon_label,
                    R.mipmap.ic_oeffi_ng_directions_color_48dp,
                    null);
            return true;
        }
    }

    public static class OperationsShortcutActionHandler extends ActionHandler {
        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            final Bundle extras = new Bundle();
//            extras.putBoolean(Constants.KEY_EXTRAS_DRIVERMODE_ENABLED, true);
            createShortcut(context,
                    OperationsActivity.class,
                    R.string.operations_icon_label,
                    R.mipmap.ic_oeffi_ng_operations_color_48dp,
                    extras);
            return true;
        }
    }

    public static class StationsShortcutActionHandler extends ActionHandler {
        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            createShortcut(context,
                    StationsActivity.class,
                    R.string.stations_icon_label,
                    R.mipmap.ic_oeffi_ng_stations_color_48dp,
                    null);
            return true;
        }
    }

    public static class FavoritesShortcutActionHandler extends ActionHandler {
        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            createShortcut(context,
                    FavoriteStationsActivity.Main.class,
                    R.string.favorite_stations_icon_label,
                    R.mipmap.ic_oeffi_ng_favorites_color_48dp,
                    null);
            return true;
        }
    }

    public static class PlansShortcutActionHandler extends ActionHandler {
        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            createShortcut(context,
                    PlansPickerActivity.class,
                    R.string.plans_icon_label,
                    R.mipmap.ic_oeffi_ng_plans_color_48dp,
                    null);
            return true;
        }
    }

    private static void createShortcut(
            final Context context,
            final Class<? extends Activity> activityClass,
            final int labelId,
            final int iconId,
            final Bundle extras) {
        final Intent shortcutIntent = new Intent(context, activityClass)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_DEFAULT);
        if (extras != null)
            shortcutIntent.putExtras(extras);
        final String id = "ShortcutsFragment:" + System.currentTimeMillis();
        ShortcutManagerCompat.requestPinShortcut(context,
                new ShortcutInfoCompat.Builder(context, id)
                        .setActivity(new ComponentName(context, DirectionsActivity.class))
                        .setShortLabel(context.getString(labelId))
                        .setIcon(IconCompat.createWithResource(context, iconId))
                        .setIntent(shortcutIntent)
                        .build(), null);
//            context.sendBroadcast(new Intent()
//                    .putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
//                    .putExtra(Intent.EXTRA_SHORTCUT_NAME, context.getString(labelId))
//                    .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context, iconId))
//                    .putExtra("duplicate", true)
//                    .setAction("com.android.launcher.action.INSTALL_SHORTCUT"));
    }
}
