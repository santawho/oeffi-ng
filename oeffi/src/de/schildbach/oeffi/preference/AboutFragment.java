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
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Installer;

import javax.annotation.Nullable;

public class AboutFragment extends PreferenceFragment {
    private static final String KEY_ABOUT_VERSION = "about_version";
    private static final String KEY_ABOUT_MARKET_APP = "about_market_app";
    private static final String KEY_ABOUT_CHANGELOG = "about_changelog";
    private static final String KEY_ABOUT_UPDATE = "about_update";

    private Activity activity;
    private Application application;

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
        this.application = (Application) activity.getApplication();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_about);
        findPreference(KEY_ABOUT_VERSION).setSummary(application.packageInfo().versionName);
        final Installer installer = Installer.from(application);
        if (installer != null) {
            final Uri marketUri = installer.appMarketUriFor(application);
            findPreference(KEY_ABOUT_MARKET_APP).setSummary(marketUri.toString());
            findPreference(KEY_ABOUT_MARKET_APP).setIntent(new Intent(Intent.ACTION_VIEW, marketUri));
        } else {
            removeOrDisablePreference(findPreference(KEY_ABOUT_MARKET_APP));
        }

        if (!getResources().getBoolean(R.bool.flags_show_twitter))
            removeOrDisablePreference(findPreference("about_twitter"));

        String changeLogUrl = activity.getString(R.string.about_changelog_url);
        if (!changeLogUrl.isEmpty()) {
            findPreference(KEY_ABOUT_CHANGELOG).setSummary(changeLogUrl);
            findPreference(KEY_ABOUT_CHANGELOG).setIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(changeLogUrl)));
        }

        String updateUrl = activity.getString(R.string.about_update_apk_url);
        if (updateUrl.isEmpty())
            removeOrDisablePreference(findPreference(KEY_ABOUT_UPDATE));
    }

    private void removeOrDisablePreference(final Preference preference) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            preference.getParent().removePreference(preference);
        else
            preference.setEnabled(false);
    }
}
