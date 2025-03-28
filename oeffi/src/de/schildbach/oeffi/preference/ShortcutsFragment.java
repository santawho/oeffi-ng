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
import android.preference.Preference;
import android.preference.PreferenceFragment;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import javax.annotation.Nullable;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.plans.PlansPickerActivity;
import de.schildbach.oeffi.stations.StationsActivity;

public class ShortcutsFragment extends PreferenceFragment {

    public static final String KEY_SHORTCUTS_DIRECTIONS = "shortcuts_directions";
    public static final String KEY_SHORTCUTS_STATIONS = "shortcuts_stations";
    public static final String KEY_SHORTCUTS_PLANS = "shortcuts_plans";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_shortcuts);

        setupActionPreference(KEY_SHORTCUTS_DIRECTIONS);
        setupActionPreference(KEY_SHORTCUTS_STATIONS);
        setupActionPreference(KEY_SHORTCUTS_PLANS);
    }

    private void setupActionPreference(final String prefkey) {
        final Preference preference = findPreference(prefkey);
        PreferenceActivity.setActionIntent(preference, ActionHandler.class);
    }

    public static class ActionHandler implements PreferenceActivity.ActionHandler {
        @Override
        public boolean handleAction(final Context context, final String prefkey) {
            if (KEY_SHORTCUTS_DIRECTIONS.equals(prefkey)) {
                createShortcut(context,
                        DirectionsActivity.class,
                        R.string.directions_icon_label,
                        R.mipmap.ic_oeffi_ng_directions_color_48dp);
                return true;
            } else if (KEY_SHORTCUTS_STATIONS.equals(prefkey)) {
                createShortcut(context,
                        StationsActivity.class,
                        R.string.stations_icon_label,
                        R.mipmap.ic_oeffi_ng_stations_color_48dp);
                return true;
            } else if (KEY_SHORTCUTS_PLANS.equals(prefkey)) {
                createShortcut(context,
                        PlansPickerActivity.class,
                        R.string.plans_icon_label,
                        R.mipmap.ic_oeffi_ng_plans_color_48dp);
                return true;
            }
            return false;
        }

        private void createShortcut(
                final Context context,
                Class<? extends Activity> activityClass,
                final int labelId,
                final int iconId) {
            final Intent shortcutIntent = new Intent(context, activityClass)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_DEFAULT);
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
}
