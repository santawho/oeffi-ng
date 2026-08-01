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
import java.io.OutputStreamWriter;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.ResourcesInterceptor;
import de.schildbach.oeffi.util.Toast;

public class CustomizeFragment extends PreferenceFragment {
    @Override
    public void onCreatePreferences(@androidx.annotation.Nullable final Bundle savedInstanceState, @androidx.annotation.Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_customize);

        setupActionPreference("customize_load", LoadActionHandler.class);

        if (Application.getInstance().isCustomizationConfigurationSet())
            setupActionPreference("customize_clear", ClearActionHandler.class);
        else
            disablePreference("customize_clear");

        setupActionPreference("customize_write_reference", WriteReferenceActionHandler.class);
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
                    }).launch(new String[] { "text/plain" });
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

    public static class WriteReferenceActionHandler extends ActionHandler {
        @Override
        boolean handleAction(final PreferenceActivity context, final String prefkey) {
            final AtomicBoolean isOk = new AtomicBoolean(false);
            context.registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("text/plain"),
                    uri -> {
                        if (uri != null) {
                            try (final OutputStreamWriter wr = new OutputStreamWriter(context.getContentResolver().openOutputStream(uri))) {
                                final Properties properties = ResourcesInterceptor.getAllResourcesAsProperties();
                                final Application application = Application.getInstance();
                                final String comment = application.getString(R.string.customize_properties_comment,
                                        application.getAppName(), application.packageInfo().versionName);
                                properties.store(wr, comment);
                                isOk.set(true);
                            } catch (final IOException ioe) {
                                context.getLog().error("WriteReferenceActionHandler openOutputStream", ioe);
                            }
                            if (isOk.get()) {
                                new Toast(context).longToast(R.string.customize_write_ok);
                            } else {
                                new Toast(context).longToast(R.string.customize_error);
                            }
                        }
                        dismissParentingActivity(context);
                    }).launch("oeffi-resource-values.template.txt");
            return false;
        }
    }
}
