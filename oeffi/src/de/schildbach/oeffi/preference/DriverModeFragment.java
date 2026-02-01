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

import javax.annotation.Nullable;

import de.schildbach.oeffi.R;

public class DriverModeFragment extends PreferenceFragment {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_drivermode);

        setupDynamicSummary("extras_drivermode_threshold_early", R.string.extras_drivermode_threshold_early_summary);
        setupDynamicSummary("extras_drivermode_threshold_delay", R.string.extras_drivermode_threshold_delay_summary);
        setupDynamicSummary("extras_drivermode_stored_operations_retention_hours", R.string.extras_drivermode_stored_operations_retention_hours_summary);
        setupDynamicSummary("extras_drivermode_navigation_refresh_battery_interval", R.string.extras_drivermode_navigation_refresh_battery_interval_summary);
        setupDynamicSummary("extras_drivermode_navigation_refresh_charging_interval", R.string.extras_drivermode_navigation_refresh_charging_interval_summary);
    }
}
