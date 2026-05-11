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

import androidx.activity.result.contract.ActivityResultContracts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.SettingsUtil;
import de.schildbach.oeffi.util.Toast;

public class BackupFragment extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@androidx.annotation.Nullable final Bundle savedInstanceState, @androidx.annotation.Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_backup);

        setupActionPreference("backup_save", SaveActionHandler.class);
        setupActionPreference("backup_restore", RestoreActionHandler.class);
    }

    public static class SaveActionHandler extends ActionHandler {
        @Override
        boolean handleAction(final PreferenceActivity context, final String prefkey) {
            final AtomicBoolean isOk = new AtomicBoolean(false);
            context.registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
                        try (final OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                            final SettingsUtil settingsUtil = new SettingsUtil(Application.getInstance());
                            isOk.set(settingsUtil.backup(os));
                        } catch (final IOException ioe) {
                            context.getLog().error("SaveActionHandler openOutputStream", ioe);
                        }
                        if (isOk.get()) {
                            new Toast(context).longToast(R.string.backup_save_ok);
                        } else {
                            new Toast(context).longToast(R.string.backup_error);
                        }
                        dismissParentingActivity(context);
                    }).launch("oeffi-settings.zip");
            return false;
        }
    }

    public static class RestoreActionHandler extends ActionHandler {
        @Override
        boolean handleAction(final PreferenceActivity context, final String prefkey) {
            final AtomicBoolean isOk = new AtomicBoolean(false);
            context.registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(), uri -> {
                        try (final InputStream is = context.getContentResolver().openInputStream(uri)) {
                            final SettingsUtil settingsUtil = new SettingsUtil(Application.getInstance());
                            isOk.set(settingsUtil.prepareRestore(is));
                        } catch (final IOException ioe) {
                            context.getLog().error("RestoreActionHandler openInputStream", ioe);
                        }
                        if (isOk.get()) {
                            new Toast(context).longToast(R.string.backup_restore_ok);
                            context.runOnUiThread(() -> {
                                Application.getInstance().restart();
                            });
                        } else {
                            new Toast(context).longToast(R.string.backup_error);
                        }
                        dismissParentingActivity(context);
                    }).launch(new String[]{"application/zip"});
            return false;
        }
    }
}
