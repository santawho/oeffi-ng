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

public class UserInterfaceFragment extends PreferenceFragment {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_user_interface);

        setupActionPreference("user_interface_location_selector_help", UserInterfaceFragment.class, SelectorHelp.class);
        setupActionPreference("user_interface_voice_control_help", UserInterfaceFragment.class, VoiceControlHelp.class);

        setupDynamicSummary("user_interface_map_tile_resolution", R.string.user_interface_map_tile_resolution_summary);
    }

    public static class SelectorHelp extends ShowHelpHandler {
        @Override
        protected int getHelpTextResourceId() {
            return R.string.directions_location_selector_help_long_text;
        }
    }

    public static class VoiceControlHelp extends ShowHelpHandler {
        @Override
        protected int getHelpTextResourceId() {
            return R.string.user_interface_voice_control_help_long_text;
        }
    }
}
