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
import android.preference.CheckBoxPreference;

import javax.annotation.Nullable;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;

public class ExtrasFragment extends PreferenceFragment {
    private static final String KEY_MORE_EXTRAS_ENABLED = "more_extras_enabled";

    private static int counter = 0;

    public static void handleTick(final int type) {
        if ((type == 1 && (counter & 1) == 0)
                || (type == 2 && (counter & 1) != 0)) {
            if (++counter >= 8) {
                counter = 0;
                final SharedPreferences preferences = Application.getInstance().getSharedPreferences();
                final boolean isExtrasEnabled = preferences.getBoolean(KEY_MORE_EXTRAS_ENABLED, false);
                preferences.edit().putBoolean(KEY_MORE_EXTRAS_ENABLED, !isExtrasEnabled).apply();
            }
        } else {
            counter = 0;
        }
    }

    public static boolean isMoreExtrasEnabled() {
        return Application.getInstance().getSharedPreferences()
                .getBoolean(KEY_MORE_EXTRAS_ENABLED, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_extras);

        if (!isMoreExtrasEnabled())
            return;

        final CheckBoxPreference tripExtraInfoPreference = new CheckBoxPreference(preferenceActivity);
        tripExtraInfoPreference.setKey(Constants.KEY_EXTRAS_TRIPEXTRAINFO_ENABLED);
        tripExtraInfoPreference.setTitle(R.string.extras_tripextrainfo_enabled_title);
        tripExtraInfoPreference.setDefaultValue(false);
        addPreference(tripExtraInfoPreference);
    }
}
