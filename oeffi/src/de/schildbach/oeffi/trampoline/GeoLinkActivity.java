package de.schildbach.oeffi.trampoline;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.widget.Button;

import androidx.annotation.NonNull;

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

        dialog.show();
    }

    private void setupButton(final Dialog dialog, final int buttonId, final Intent incomingIntent, final String aliasName) {
        final Button button = dialog.findViewById(buttonId);
        button.setText(Html.fromHtml(button.getText().toString()));
        button.setOnClickListener(v -> {
            final Intent newIntent = new Intent(incomingIntent);
            newIntent.setComponent(new ComponentName(this.getPackageName(), aliasName));
            startActivity(newIntent);
            dialog.dismiss();
        });
    }
}
