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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.FontManager;
import de.schildbach.oeffi.util.Toast;

public class UserInterfaceFragment extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState, @Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_user_interface);

        setup();

        setupCustomPreference("user_interface_font_install", installFontHandler());
        setupCustomPreference("user_interface_font_delete", p -> deleteFont());
    }

    @Override
    protected boolean isPreferenceRequiringRestart(final String key) {
        return "user_interface_darkmode_switch".equals(key)
                || "user_interface_darkmode_amoled_enabled".equals(key)
                || "user_interface_location_action_buttons_enabled".equals(key)
                || "user_interface_font_enabled".equals(key)
                || super.isPreferenceRequiringRestart(key);
    }

    private void setup() {
        if (FontManager.instance.isCustomFontInstalled()) {
            enablePreference("user_interface_font_enabled");
            enablePreference("user_interface_font_delete");
        } else {
            setUseCustomFont(false);
            disablePreference("user_interface_font_enabled");
            disablePreference("user_interface_font_delete");
        }
    }

    protected void setUseCustomFont(final boolean use) {
        if (use != prefs.getBoolean("user_interface_font_enabled", false)) {
            prefs.edit().putBoolean("user_interface_font_enabled", use).apply();
            setRestartRequired();
        }
        ((CheckBoxPreference) findPreference("user_interface_font_enabled")).setChecked(use);
    }

    private CustomPreference.ClickHandler installFontHandler() {
        final ActivityResultLauncher<String[]> launcher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    final AtomicBoolean isOk = new AtomicBoolean(false);
                    if (uri != null) {
                        try (final InputStream is = preferenceActivity.getContentResolver().openInputStream(uri)) {
                            isOk.set(FontManager.instance.installCustomFontFromZip(is));
                        } catch (final IOException ioe) {
                            preferenceActivity.getLog().error("InstallFontActionHandler installCustomFontFromZip", ioe);
                        }
                        if (isOk.get()) {
                            setUseCustomFont(true);
                            setup();
                            setRestartRequired();
                        } else {
                            new Toast(preferenceActivity).longToast(R.string.error_occurred);
                        }
                    }
                });
        return p -> launcher.launch(new String[]{"application/zip"});
    }

    private void deleteFont() {
        DialogBuilder.get(preferenceActivity)
                .setCanceledOnTouchOutside(true)
                .setTitle(R.string.user_interface_font_delete_title)
                .setMessage(R.string.are_you_sure)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    FontManager.instance.removeCustomFont();
                    setup();
                })
                .show();
    }
}
