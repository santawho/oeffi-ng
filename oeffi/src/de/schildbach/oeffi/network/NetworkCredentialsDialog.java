package de.schildbach.oeffi.network;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.View;
import android.widget.EditText;

import java.io.IOException;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.SuggestLocationsResult;
import de.schildbach.pte.provider.NetworkProvider;

public class NetworkCredentialsDialog {
    @SuppressLint("UnsafeImplicitIntentLaunch")
    public static void show(
            final Activity contextActivity,
            final NetworkId networkId) {
        final DialogBuilder builder = DialogBuilder.get(contextActivity, R.layout.network_credentials_dialog);
        builder.setTitle(R.string.network_preferences_credentials_edit_title);
        final View contentView = builder.getView();
        final EditText editText = contentView.findViewById(R.id.credentials_text);
        editText.setHint(contextActivity.getString(R.string.network_preferences_credentials_edit_hint,
                NetworkResources.instance(contextActivity, networkId).label));
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            final String text = editText.getText().toString();
            final NetworkProviderFactory providerFactory = NetworkProviderFactory.getInstance();
            providerFactory.setNetworkCredentials(networkId, text);
            testNetworkInBackground(contextActivity, providerFactory.getNetworkProvider(networkId));
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.setNeutralButton(R.string.help, (dialog, which) -> {
            contextActivity.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(contextActivity.getString(R.string.network_preferences_credentials_help_url))));
        });
        builder.setCancelable(true);
        builder.show();
    }

    private static void testNetworkInBackground(
            final Activity contextActivity,
            final NetworkProvider networkProvider) {
        final HandlerThread handlerThread = new HandlerThread("networkCredentialsTestThread", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        new Handler(handlerThread.getLooper()).post(() -> {
            try {
                final SuggestLocationsResult result = networkProvider.suggestLocations("somewhere", null, 1);
                if (result.status == SuggestLocationsResult.Status.OK)
                    return;
            } catch (final IOException ioe) {
                // fall through ...
            } finally {
                contextActivity.runOnUiThread(() -> handlerThread.getLooper().quit());
            }
            contextActivity.runOnUiThread(() ->
                    DialogBuilder
                            .warn(contextActivity, R.string.network_preferences_credentials_not_working_title)
                            .setCanceledOnTouchOutside(true)
                            .setMessage(R.string.network_preferences_credentials_not_working_message)
                            .show());
        });
    }
}
