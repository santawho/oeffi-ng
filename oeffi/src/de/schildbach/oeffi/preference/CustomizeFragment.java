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
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Toast;

public class CustomizeFragment extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@androidx.annotation.Nullable final Bundle savedInstanceState, @androidx.annotation.Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_customize);

        setupActionPreference("customize_load", LoadActionHandler.class);
        setupActionPreference("customize_clear", ClearActionHandler.class);
    }

    public static class LoadActionHandler extends ActionHandler {
        @Override
        boolean handleAction(final PreferenceActivity context, final String prefkey) {
            final AtomicBoolean isOk = new AtomicBoolean(false);
            context.registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri == null)
                            return;
                        try (final InputStream is = context.getContentResolver().openInputStream(uri)) {
                            final Properties properties = new Properties();
                            properties.load(is);
                            Application.getInstance().setCustomizationConfiguration(properties);
                            isOk.set(true);
                        } catch (final IOException ioe) {
                            context.getLog().error("Customize handler openInputStream", ioe);
                        }
                        if (isOk.get()) {
                            new Toast(context).longToast(R.string.customize_ok);
                            context.runOnUiThread(() -> {
                                Application.getInstance().postTerminate(context);
                            });
                        } else {
                            new Toast(context).longToast(R.string.customize_error);
                        }
                    }).launch(new String[] {
                            "text/plain",
                            // "text/x-java-properties",
                            // "application/octet-stream",
                    });
            return false;
        }
    }

    public static class ClearActionHandler extends ActionHandler {
        @Override
        boolean handleAction(final PreferenceActivity context, final String prefkey) {
            try {
                Application.getInstance().setCustomizationConfiguration(null);
            } catch (final IOException ioe) {
                throw new RuntimeException(ioe);
            }
            new Toast(context).longToast(R.string.customize_ok);
            Application.getInstance().postTerminate(context);
            return false;
        }
    }
}
