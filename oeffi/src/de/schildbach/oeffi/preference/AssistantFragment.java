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

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.assistant.AssistantActivity;

public class AssistantFragment extends PreferenceFragment {
    public static final String ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST";

    public static final String KEY_ASSISTANT_ENABLED = "assistant_enabled";
    public static final String KEY_ASSISTANT_CHOOSE = "assistant_choose";
    public static final String KEY_ASSISTANT_CHOOSE_VOICE = "assistant_choose_voice";
    public static final String KEY_ASSISTANT_BUTTON_NEARBY_STATIONS_ENABLED = "assistant_button_nearby_stations_enabled";
    public static final String KEY_ASSISTANT_BUTTON_NAVIGATION_INSTRUCTION_ENABLED = "assistant_button_navigation_instruction_enabled";
    public static final String KEY_ASSISTANT_BUTTON_NAVIGATION_SCREEN_ENABLED = "assistant_button_navigation_screen_enabled";
    public static final String KEY_ASSISTANT_HEADSET_NAVIGATION_INSTRUCTION_ENABLED = "assistant_headset_navigation_instruction_enabled";
    public static final String KEY_ASSISTANT_FALLBACK_APP = "assistant_fallback_app";

    private static boolean hasVoiceSettings;

    private static Intent getVoiceSettingsActivityIntent() {
        return new Intent(Intent.ACTION_VIEW).setClassName(
                "com.android.settings",
                "com.android.settings.Settings$ManageVoiceActivity");
    }

    @Override
    public void onCreatePreferences(@androidx.annotation.Nullable final Bundle savedInstanceState, @androidx.annotation.Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_assistant);

        setupActionPreference(KEY_ASSISTANT_CHOOSE, AssistantActionHandler.class);
        setupActionPreference(KEY_ASSISTANT_CHOOSE_VOICE, AssistantActionHandler.class);
        setupActionPreference(KEY_ASSISTANT_FALLBACK_APP, AssistantActionHandler.class);
        setupDynamicSummaryForFallbackApp(KEY_ASSISTANT_FALLBACK_APP);

        final Application application = Application.getInstance();
        final boolean isEnabled = application.isComponentEnabled(AssistantActivity.class, false);
        final CheckBoxPreference enabledPref = (CheckBoxPreference) findPreference(KEY_ASSISTANT_ENABLED);
        enabledPref.setChecked(isEnabled);

        final List<ResolveInfo> resolveInfos = application.getPackageManager()
                .queryIntentActivities(getVoiceSettingsActivityIntent(), PackageManager.GET_RESOLVED_FILTER);
        hasVoiceSettings = !resolveInfos.isEmpty();
        final Preference chooseVoicePreference = findPreference(KEY_ASSISTANT_CHOOSE_VOICE);
        chooseVoicePreference.setEnabled(hasVoiceSettings && isEnabled);

        enabledPref.setOnPreferenceChangeListener((pref, newValue) -> {
            final Boolean newChecked = (Boolean) newValue;
            // enable activity alias having the ACTION_ASSIST intent filter
            application.setComponentEnabled(AssistantActivity.class, newChecked);
            chooseVoicePreference.setEnabled(hasVoiceSettings && newChecked);
            return true;
        });
    }

    private void setupDynamicSummaryForFallbackApp(final String prefKey) {
        setupDynamicSummary(prefKey, R.string.assistant_action_fallback_app_summary,
                packageName -> getApplicationLabelForPackageName((String) packageName));
    }

    @Override
    public void onResume() {
        super.onResume();
        preferenceChanged(KEY_ASSISTANT_FALLBACK_APP,
                getFallbackAssistantPackageName(preferenceActivity, KEY_ASSISTANT_FALLBACK_APP, Intent.ACTION_ASSIST));
    }

    private String getApplicationLabelForPackageName(final String packageName) {
        final Application application = Application.getInstance();
        try {
            final PackageManager packageManager = application.getPackageManager();
            final PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            return packageInfo.applicationInfo.loadLabel(packageManager).toString();
        } catch (final Exception e) {
            return application.getString(R.string.assistant_choose_fallback_app_none);
        }
    }

    public static class AssistantActionHandler extends ActionHandler {
        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            if (KEY_ASSISTANT_CHOOSE.equals(prefkey)) {
                context.startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
            } else if (KEY_ASSISTANT_CHOOSE_VOICE.equals(prefkey)) {
                if (hasVoiceSettings)
                    context.startActivity(getVoiceSettingsActivityIntent());
            } else if (KEY_ASSISTANT_FALLBACK_APP.equals(prefkey)) {
                return !chooseFallbackAssistant(context, prefkey);
            } else {
                return false;
            }
            return true;
        }

        private boolean chooseFallbackAssistant(final PreferenceActivity context, final String prefkey) {
            final Application application = Application.getInstance();
            final SharedPreferences preferences = application.getSharedPreferences();
            final List<AssistantInfo> assistantInfos = getAssistantApps(context, Intent.ACTION_ASSIST);
            if (assistantInfos.isEmpty()) {
                preferences.edit().putString(prefkey, null).apply();
                return false;
            }
            assistantInfos.add(0, new AssistantInfo(null,
                    context.getString(R.string.assistant_choose_fallback_app_none)));
            final String currentFallbackAssistantPackageName =
                    getFallbackAssistantPackageName(context, prefkey, Intent.ACTION_ASSIST);
            int selectedIndex = -1;
            final CharSequence[] items = new CharSequence[assistantInfos.size()];
            for (int i = 0; i < assistantInfos.size(); i++) {
                final AssistantInfo assistantInfo = assistantInfos.get(i);
                items[i] = assistantInfo.label;
                final String packageName = assistantInfo.packageName;
                if (Objects.equals(packageName, currentFallbackAssistantPackageName))
                    selectedIndex = i;
            }
            if (selectedIndex < 0) {
                selectedIndex = 0;
                preferences.edit().putString(prefkey, assistantInfos.get(selectedIndex).packageName).apply();
            }
            final Resources resources = context.getResources();
            final TextView longTitle = new TextView(context);
            longTitle.setText(R.string.assistant_choose_fallback_app_title);
            longTitle.setTextColor(resources.getColor(R.color.fg_significant));
            longTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.font_size_large));
            final int horzPad = (int) resources.getDimension(R.dimen.text_padding_horizontal_verylax);
            final int vertPad = (int) resources.getDimension(R.dimen.text_padding_vertical_lax);
            longTitle.setPadding(horzPad, vertPad, horzPad, vertPad);
            new AlertDialog.Builder(context)
                    .setCustomTitle(longTitle)
                    .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
                        final String assistantPackageName = assistantInfos.get(which).packageName;
                        preferences.edit().putString(prefkey, assistantPackageName).apply();
                        dialog.dismiss();
                    })
                    .setOnDismissListener(dialog -> {
                        dismissParentingActivity(context);
                    })
                    .create().show();
            return true;
        }
    }

    public static String getFallbackAssistantPackageName(
            final Context context,
            final String prefkey,
            final String action) {
        final Application application = Application.getInstance();
        final SharedPreferences preferences = application.getSharedPreferences();
        final String currentAssistantPackageName = preferences.getString(prefkey, null);
//        if (currentAssistantPackageName != null)
//            return currentAssistantPackageName;
//        final List<AssistantInfo> assistantApps = getAssistantApps(context, action);
//        if (assistantApps.isEmpty())
//            return null;
//        return assistantApps.get(assistantApps.size() - 1).packageName;
        return currentAssistantPackageName;
    }

    private static class AssistantInfo {
        final String packageName;
        final CharSequence label;

        private AssistantInfo(final String packageName, final CharSequence label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private static List<AssistantInfo> getAssistantApps(
            final Context context,
            final String action) {
        final Application application = Application.getInstance();
        final String myPackageName = application.getPackageName();
        final PackageManager packageManager = application.getPackageManager();
        final Intent assistIntent = new Intent(action);
        final List<AssistantInfo> assistantInfos = new ArrayList<>();
        for (final ResolveInfo resolveInfo : packageManager.queryIntentActivities(assistIntent, PackageManager.MATCH_ALL)) {
            final ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo != null) {
                final ApplicationInfo applicationInfo = activityInfo.applicationInfo;
                final String packageName = applicationInfo.packageName;
                if (packageName.equals(myPackageName))
                    continue;
                assistantInfos.add(new AssistantInfo(packageName, applicationInfo.loadLabel(packageManager)));
            }
        }
        return assistantInfos;
    }
}
