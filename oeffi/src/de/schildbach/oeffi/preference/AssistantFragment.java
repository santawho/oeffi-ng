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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.provider.Settings;

import java.util.List;

import javax.annotation.Nullable;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.assistant.AssistantActivity;

public class AssistantFragment extends PreferenceFragment {

    public static final String KEY_ASSISTANT_ENABLED = "assistant_enabled";
    public static final String KEY_ASSISTANT_CHOOSE = "assistant_choose";
    public static final String KEY_ASSISTANT_CHOOSE_VOICE = "assistant_choose_voice";
    public static final String KEY_ASSISTANT_BUTTON_NEARBY_STATIONS_ENABLED = "assistant_button_nearby_stations_enabled";
    public static final String KEY_ASSISTANT_BUTTON_NAVIGATION_INSTRUCTION_ENABLED = "assistant_button_navigation_instruction_enabled";
    public static final String KEY_ASSISTANT_HEADSET_NAVIGATION_INSTRUCTION_ENABLED = "assistant_headset_navigation_instruction_enabled";

    private static boolean hasVoiceSettings;

    private static Intent getVoiceSettingsActivityIntent() {
        return new Intent(Intent.ACTION_VIEW).setClassName(
                "com.android.settings",
                "com.android.settings.Settings$ManageVoiceActivity");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_assistant);

        setupActionPreference(KEY_ASSISTANT_CHOOSE, AssistantActionHandler.class);
        setupActionPreference(KEY_ASSISTANT_CHOOSE_VOICE, AssistantActionHandler.class);

        final Application application = Application.getInstance();
        final boolean isEnabled = application.isComponentEnabled(AssistantActivity.class, false);
        final CheckBoxPreference enabledPref = (CheckBoxPreference) findPreference(KEY_ASSISTANT_ENABLED);
        enabledPref.setChecked(isEnabled);

        final List<ResolveInfo> resolveInfos = application.getPackageManager()
                .queryIntentActivities(getVoiceSettingsActivityIntent(), PackageManager.GET_RESOLVED_FILTER);
        hasVoiceSettings = !resolveInfos.isEmpty();
        final Preference chooseVoicePreference = findPreference(KEY_ASSISTANT_CHOOSE_VOICE);
        chooseVoicePreference.setEnabled(hasVoiceSettings && isEnabled);

        if (hasVoiceSettings) {
            enabledPref.setOnPreferenceChangeListener((pref, newValue) -> {
                final Boolean newChecked = (Boolean) newValue;
                chooseVoicePreference.setEnabled(newChecked);
                return true;
            });
        }
    }

    public static class AssistantActionHandler extends ActionHandler {
        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            if (KEY_ASSISTANT_ENABLED.equals(prefkey)) {
                // enable activity alias having the ACTION_ASSIST intent filter
                final Application application = Application.getInstance();
                final boolean toBeEnabled = application.getSharedPreferences().getBoolean(KEY_ASSISTANT_ENABLED, false);
                application.setComponentEnabled(AssistantActivity.class, toBeEnabled);
            } else if (KEY_ASSISTANT_CHOOSE.equals(prefkey)) {
                context.startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
            } else if (KEY_ASSISTANT_CHOOSE_VOICE.equals(prefkey)) {
                if (hasVoiceSettings)
                    context.startActivity(getVoiceSettingsActivityIntent());
            } else {
                return false;
            }
            return true;
        }
    }
}
