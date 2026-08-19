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

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.Preference;

import java.util.function.Function;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.AppChooser;
import de.schildbach.oeffi.util.ExternalMapsUtils;

public class MapsFragment extends PreferenceFragment {
    public static final String KEY_POINT_ACTION = "user_interface_map_point_action";
    public static final String KEY_POINT_APP = "user_interface_map_point_app";
    public static final String KEY_TRIP_ACTION = "user_interface_map_trip_action";
    public static final String KEY_TRIP_APP = "user_interface_map_trip_app";

    public enum ActionMode {
        INTERNAL,
        EXTERNAL,
        CHOOSE,
        MENU,
    }

    public static ActionMode getActionMode(final boolean forTrips) {
        final SharedPreferences prefs = Application.getInstance().getSharedPreferences();
        final String prefValue = prefs.getString(forTrips ? KEY_TRIP_ACTION : KEY_POINT_ACTION, ActionMode.INTERNAL.name());
        final ActionMode actionMode = ActionMode.valueOf(prefValue);
        return actionMode;
    }

    public static AppChooser.ComponentInfo getPreselectedApp(final boolean forTrips) {
        final SharedPreferences prefs = Application.getInstance().getSharedPreferences();
        final String prefValue = prefs.getString(forTrips ? KEY_TRIP_APP : KEY_POINT_APP, null);
        if (prefValue == null)
            return null;
        return new AppChooser.ComponentInfo(prefValue);
    }

    private final Function<Object, Object> summaryValueMapper = in -> {
        if (in == null)
            return getString(R.string.app_chooser_app_none);
        return new AppChooser.ComponentInfo((String) in).getLabel();
    };

    @Override
    public void onCreatePreferences(@androidx.annotation.Nullable final Bundle savedInstanceState, @androidx.annotation.Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_maps);

        setupDynamicSummaries();

        setupCustomPreference(KEY_POINT_APP, this::onClickPointApp);
        setupCustomPreference(KEY_TRIP_APP, this::onClickTripApp);
    }

    private void setupDynamicSummaries() {
        setupDynamicSummary("user_interface_map_tile_resolution", R.string.user_interface_map_tile_resolution_summary);

        setupDynamicSummary(KEY_POINT_APP, R.string.user_interface_map_app_summary, summaryValueMapper);
        setupDynamicSummary(KEY_TRIP_APP, R.string.user_interface_map_app_summary, summaryValueMapper);
    }

    private void onAppValueChanged(
            final Preference preference,
            final AppChooser.ComponentInfo componentInfo) {
        final String prefValue = componentInfo.toPrefValue();
        Application.getInstance().getSharedPreferences()
                .edit()
                .putString(preference.getKey(), prefValue)
                .apply();

        setupDynamicSummaries();
    }
    private void onClickPointApp(final Preference preference) {
        ExternalMapsUtils.chooseActivityToOpenPoint(preferenceActivity,
                componentInfo -> onAppValueChanged(preference, componentInfo));
    }

    private void onClickTripApp(final Preference preference) {
        ExternalMapsUtils.chooseActivityToOpenTrip(preferenceActivity,
                componentInfo -> onAppValueChanged(preference, componentInfo));
    }
}
