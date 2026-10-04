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

import androidx.annotation.Nullable;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkCredentialsDialog;
import de.schildbach.oeffi.network.NetworkPickerActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.network.NetworkResources;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.provider.NetworkProvider;

public class NetworkFragment extends PreferenceFragment {

    public static final String PREF_KEY_NETWORK_CREDENTIALS = "network_credentials";

    @Override
    public void onCreatePreferences(
            @Nullable final Bundle savedInstanceState,
            @Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_network);

        final Application application = Application.getInstance();
        final Context context = getContext();

        setupCustomPreference(Constants.PREFS_KEY_NETWORK_PROVIDER, preference -> {
            NetworkPickerActivity.start(context, false);
        });
        setupCustomPreference(PREF_KEY_NETWORK_CREDENTIALS, preference ->
                NetworkCredentialsDialog.show(preferenceActivity, application.prefsGetNetworkId(false)));
        setupDynamicSummary(
                Constants.PREFS_KEY_NETWORK_PROVIDER, R.string.network_preferences_provider_summary,
                networkIdName -> (networkIdName == null) ? "-"
                        : getNetworkLabel(NetworkId.valueOf((String) networkIdName)));
    }

    private String getNetworkLabel(final NetworkId networkId) {
        return NetworkResources.instance(getContext(), networkId).label;
    }

    @Override
    public void onResume() {
        super.onResume();

        final NetworkId networkId = Application.getInstance().prefsGetNetworkId(false);
        final NetworkProvider networkProvider = NetworkProviderFactory.getInstance().getNetworkProvider(networkId);

        preferenceChanged(Constants.PREFS_KEY_NETWORK_PROVIDER, networkId.name());

        if (networkProvider != null && networkProvider.requiresCredentials())
            enablePreference(PREF_KEY_NETWORK_CREDENTIALS);
        else
            disablePreference(PREF_KEY_NETWORK_CREDENTIALS);
    }
}
