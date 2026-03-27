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

import android.os.Bundle;

import de.schildbach.oeffi.R;

public class DirectionsUIFragment extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@androidx.annotation.Nullable final Bundle savedInstanceState, @androidx.annotation.Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_directions_ui);

        setupActionPreference("user_interface_location_selector_help", DirectionsUIFragment.class, SelectorHelp.class);

        setupDynamicSummary("max_history_entries", R.string.user_interface_max_history_entries_summary);
        setupDynamicSummary("stored_trips_retention_hours", R.string.user_interface_stored_trips_retention_hours_summary);

        setupDynamicSummary("user_interface_location_selector_numrows", R.string.user_interface_location_selector_numrows_summary);
        setupDynamicSummary("user_interface_location_selector_longholdtime", R.string.user_interface_location_selector_longholdtime_summary);
    }

    @Override
    protected boolean isPreferenceRequiringRestart(final String key) {
        return "user_interface_directions_time_and_go_bottom_enabled".equals(key)
                || "max_history_entries".equals(key)
                || "stored_trips_retention_hours".equals(key)
                || "user_interface_directions_history_show_saved_trip_enabled".equals(key)
                || "user_interface_directions_show_clear_button_enabled".equals(key)
                || super.isPreferenceRequiringRestart(key);
    }

    public static class SelectorHelp extends ShowHelpHandler {
        @Override
        protected int getHelpTextResourceId() {
            return R.string.directions_location_selector_help_long_text;
        }
    }
}
