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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Installer;
import de.schildbach.oeffi.util.AppInstaller;

import javax.annotation.Nullable;

/** @noinspection deprecation*/
public class AboutFragment extends PreferenceFragment {
    public static PreferenceActivity.Header getHeader() {
        final PreferenceActivity.Header aboutHeader = new PreferenceActivity.Header();
        aboutHeader.fragment = AboutFragment.class.getName();
        aboutHeader.title = Application.getInstance().getString(R.string.about_title, Application.getInstance().getAppName());
        return aboutHeader;
    }

    private static final String KEY_ABOUT_VERSION = "about_version";
    private static final String KEY_ABOUT_COPYRIGHT = "about_copyright";
    private static final String KEY_ABOUT_MARKET_APP = "about_market_app";
    private static final String KEY_ABOUT_CHANGELOG = "about_changelog";
    private static final String KEY_ABOUT_POLICY = "about_policy";
    private static final String KEY_ABOUT_FAQ = "about_faq";
    private static final String KEY_ABOUT_APP_UPDATE_CATEGORY = "about_update_category";
    private static final String KEY_ABOUT_DOWNLOAD_APK = "about_download_apk";
    private static final String KEY_ABOUT_UPDATE = "about_update";
    private static final String KEY_ABOUT_SHARE = "about_share";
    private static final String KEY_ABOUT_SHOW_QR = "about_show_qr";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_about);

        final Application application = Application.getInstance();

        findPreference(KEY_ABOUT_VERSION).setSummary(application.packageInfo().versionName);

        final Installer installer = Installer.from(application);
        final Preference prefMarketApp = findPreference(KEY_ABOUT_MARKET_APP);
        if (installer != null) {
            final Uri marketUri = installer.appMarketUriFor(application);
            prefMarketApp.setSummary(marketUri.toString());
            prefMarketApp.setIntent(new Intent(Intent.ACTION_VIEW, marketUri));
        } else {
            removeOrDisablePreference(prefMarketApp);
        }

        if (!getResources().getBoolean(R.bool.flags_show_twitter))
            removeOrDisablePreference("about_twitter");

        final String changeLogUrl = application.getString(R.string.about_changelog_url);
        if (!changeLogUrl.isEmpty()) {
            final Preference prefChangeLog = findPreference(KEY_ABOUT_CHANGELOG);
            prefChangeLog.setSummary(changeLogUrl);
            prefChangeLog.setIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(changeLogUrl)));
        }

        if (AppInstaller.isApkUrlAvailable()) {
            setupActionPreference(KEY_ABOUT_UPDATE, AboutFragment.class, InstallHandler.class);
            setupActionPreference(KEY_ABOUT_DOWNLOAD_APK, AboutFragment.class, DownloadApkHandler.class);
            setupActionPreference(KEY_ABOUT_SHARE, AboutFragment.class, ShareActionHandler.class);
            setupActionPreference(KEY_ABOUT_SHOW_QR, AboutFragment.class, ShowQrActionHandler.class);
        } else {
            removeOrDisablePreference(KEY_ABOUT_UPDATE);
            removeOrDisablePreference(KEY_ABOUT_DOWNLOAD_APK);
            removeOrDisablePreference(KEY_ABOUT_SHARE);
            removeOrDisablePreference(KEY_ABOUT_SHOW_QR);
            removeOrDisablePreference(KEY_ABOUT_SHOW_QR);
            removeOrDisablePreference(KEY_ABOUT_APP_UPDATE_CATEGORY);
        }

        setupActionPreference(KEY_ABOUT_VERSION, AboutFragment.class, EnableExtrasActionHandler.class);
        setupActionPreference(KEY_ABOUT_COPYRIGHT, AboutFragment.class, EnableExtrasActionHandler.class);

        final String policyUrl = application.getTranslatedString(R.string.about_privacy_policy_url);
        final Preference prefPolicy = findPreference(KEY_ABOUT_POLICY);
        prefPolicy.setSummary(policyUrl);
        prefPolicy.setIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)));

        final String faqUrl = application.getTranslatedString(R.string.about_faq_url);
        final Preference prefFaq = findPreference(KEY_ABOUT_FAQ);
        prefFaq.setSummary(faqUrl);
        prefFaq.setIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(faqUrl)));
    }

    public static class EnableExtrasActionHandler extends ActionHandler {
        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            if (KEY_ABOUT_VERSION.equals(prefkey))
                ExtrasFragment.handleTick(1);
            else if (KEY_ABOUT_COPYRIGHT.equals(prefkey))
                ExtrasFragment.handleTick(2);

            return true;
        }
    }

    public static class ShareActionHandler extends ActionHandler {
        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            Application.getInstance().shareApp(context);
            return true;
        }
    }

    public static class ShowQrActionHandler extends ActionHandler {
        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            Application.getInstance().showImageDialog(context, R.drawable.qr_update,
                    dialog -> dismissParentingActivity(context));
            return false;
        }
    }

    public static class InstallHandler extends ActionHandler {
        @Override
        boolean handleAction(final PreferenceActivity context, final String prefkey) {
            new AppInstaller(context, aBoolean -> dismissParentingActivity(context))
                    .downloadAndInstallApk();
            return false;
        }
    }

    public static class DownloadApkHandler extends ActionHandler {
        @Override
        boolean handleAction(final PreferenceActivity context, final String prefkey) {
            new AppInstaller(context, aBoolean -> dismissParentingActivity(context))
                    .showExternalDownloader();
            return true;
        }
    }
}
