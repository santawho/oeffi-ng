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

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkPickerActivity;

import javax.annotation.Nullable;

public class CommonFragment extends PreferenceFragment {
    public static final String KEY_COMMON_NETWORK_PROVIDER = "network_provider";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_common);

        setupActionPreference(KEY_COMMON_NETWORK_PROVIDER);
    }

    private void setupActionPreference(final String prefkey) {
        final Preference preference = findPreference(prefkey);
        PreferenceActivity.setActionIntent(preference, ActionHandler.class);
    }

    public static class ActionHandler implements PreferenceActivity.ActionHandler {
        @Override
        public boolean handleAction(final Context context, final String prefkey) {
            if (KEY_COMMON_NETWORK_PROVIDER.equals(prefkey)) {
                NetworkPickerActivity.start(context);
                return true;
            }
            return false;
        }
    }
}
