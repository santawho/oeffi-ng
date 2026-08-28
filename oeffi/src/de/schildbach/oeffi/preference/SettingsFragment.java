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

import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkResources;
import de.schildbach.pte.NetworkId;

public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@androidx.annotation.Nullable final Bundle savedInstanceState, @androidx.annotation.Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_settings);

        final PreferenceScreen aboutPreferenceScreen = getPreferenceManager().createPreferenceScreen(getContext());
        aboutPreferenceScreen.setFragment(AboutFragment.class.getName());
        aboutPreferenceScreen.setTitle(Application.getInstance().getString(R.string.about_title, Application.getInstance().getAppName()));
        addPreference(aboutPreferenceScreen);

        setupActionPreference(Constants.PREFS_KEY_NETWORK_PROVIDER, CommonFragment.NetworkProviderActionHandler.class);
        setupDynamicSummary(
                Constants.PREFS_KEY_NETWORK_PROVIDER, R.string.global_preferences_network_provider_summary,
                networkIdName -> {
                    if (networkIdName == null)
                        return "-";
                    final NetworkId networkId = NetworkId.valueOf((String) networkIdName);
                    final NetworkResources networkResources = NetworkResources.instance(getContext(), networkId);
                    return networkResources.label;
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        preferenceChanged(Constants.PREFS_KEY_NETWORK_PROVIDER, Application.getInstance().prefsGetNetworkId(false).name());
    }
}
