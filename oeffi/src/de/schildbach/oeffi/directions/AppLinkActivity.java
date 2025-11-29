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

package de.schildbach.oeffi.directions;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.List;

import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.pte.NetworkId;

public class AppLinkActivity extends OeffiActivity {
    public static final String LINK_IDENTIFIER_NETWORK = "network";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull final Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(final Intent incomingIntent) {
        final Uri appLinkUri = incomingIntent.getData();
        log.info("app link: {}", appLinkUri);

        Intent activityIntent = null;
        if (appLinkUri != null) {
            final String pathPrefix = getString(R.string.app_url_path_prefix);
            if (appLinkUri.getPath().startsWith(pathPrefix)) {
                List<String> urlPath = appLinkUri.getPathSegments();
                List<String> actionArgs = urlPath.subList(pathPrefix.split("/").length - 1, urlPath.size());
                if (!actionArgs.isEmpty()) {
                    final String networkName;
                    if (LINK_IDENTIFIER_NETWORK.equals(actionArgs.get(0)) && actionArgs.size() >= 3) {
                        networkName = actionArgs.get(1);
                        actionArgs = actionArgs.subList(2, actionArgs.size());
                    } else {
                        networkName = null;
                    }
                    log.info("app link action: \"{}\" with network={}", actionArgs.get(0), networkName);
                    if (activityIntent == null)
                        activityIntent = DirectionsActivity.handleAppLink(this, actionArgs);
                    // if (intent == null)
                    //     intent = OtherActivity.handleAppLink(this, action, actionArgs);
                    // ...
                    if (activityIntent != null) {
                        if (networkName != null)
                            activityIntent.putExtra(INTENT_EXTRA_NETWORK_NAME, networkName);
                        final String[] argsArray = actionArgs.toArray(new String[actionArgs.size()]);
                        activityIntent.putExtra(INTENT_EXTRA_LINK_ARGS, argsArray);
                    }
                }
            }
        }

        if (activityIntent != null) {
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(activityIntent);
        } else {
            log.warn("unhandled app link: {}", appLinkUri);
            startFallbackActivity();
        }

        finishAndRemoveTask();
    }

    private void startFallbackActivity() {
        DirectionsActivity.start(this,
                null, null, null, null, null, false,
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    public static Uri getLinkUrl(final Context context, final String... args) {
        return getNetworkLinkUrl(context, null, args);
    }

    public static Uri getNetworkLinkUrl(final Context context, final NetworkId networkId, final String... args) {
        final Uri.Builder builder = new Uri.Builder()
                .scheme("https")
                .authority(context.getString(R.string.app_url_host))
                .appendEncodedPath(context.getString(R.string.app_url_path_prefix).substring(1));
        if (networkId != null) {
            builder.appendEncodedPath(LINK_IDENTIFIER_NETWORK).appendEncodedPath(networkId.name());
        }
        for (final String arg : args) {
            builder.appendPath(arg);
        }
        return builder.build();
    }
}
