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

import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.preference.Preference;
import de.schildbach.oeffi.R;

import javax.annotation.Nullable;

public class DonateFragment extends PreferenceFragment {
    private static final String KEY_ABOUT_DONATE_BITCOIN = "about_donate_bitcoin";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_donate);
        final Preference donateBitcoinPreference = findPreference(KEY_ABOUT_DONATE_BITCOIN);
        donateBitcoinPreference.setOnPreferenceClickListener(preference -> {
            try {
                startActivity(donateBitcoinPreference.getIntent());
            } catch (final ActivityNotFoundException x) {
                donateBitcoinPreference.setEnabled(false);
            }
            return true;
        });
    }
}
