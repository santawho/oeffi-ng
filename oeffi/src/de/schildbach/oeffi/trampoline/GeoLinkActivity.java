package de.schildbach.oeffi.trampoline;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import java.util.List;

import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.stations.StationsActivity;

public class GeoLinkActivity extends OeffiActivity {

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
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.geo_link_dialog);
        dialog.setOnDismissListener(d -> finishAndRemoveTask());
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        setupButton(dialog, R.id.geo_link_directions_from, incomingIntent, DirectionsActivity.class.getName() + ".FROM");
        setupButton(dialog, R.id.geo_link_directions_to, incomingIntent, DirectionsActivity.class.getName() + ".TO");
        setupButton(dialog, R.id.geo_link_stations_nearby, incomingIntent, StationsActivity.class.getName() + ".NEARBY");
        setupButton(dialog, R.id.geo_link_directions_trip, incomingIntent, DirectionsActivity.class.getName() + ".TRIP");

        dialog.show();
    }

    private void setupButton(final Dialog dialog, final int buttonId, final Intent incomingIntent, final String aliasName) {
        final Button button = dialog.findViewById(buttonId);

        final Intent newIntent = new Intent(incomingIntent);
        newIntent.setComponent(null);

        final String myPackageName = this.getPackageName();
        final PackageManager packageManager = application.getPackageManager();
        final List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(newIntent, PackageManager.MATCH_ALL);
        boolean canHandleIntent = false;
        for (final ResolveInfo resolveInfo : resolveInfos) {
            final ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo.packageName.equals(myPackageName) && activityInfo.name.equals(aliasName)) {
                canHandleIntent = true;
                break;
            }
        }
        if (!canHandleIntent) {
            button.setVisibility(View.GONE);
            return;
        }

        newIntent.setComponent(new ComponentName(myPackageName, aliasName));

        button.setText(Html.fromHtml(button.getText().toString()));
        button.setOnClickListener(v -> {
            startActivity(newIntent);
            dialog.dismiss();
        });
    }
}
