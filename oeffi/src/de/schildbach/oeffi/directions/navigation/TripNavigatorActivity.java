package de.schildbach.oeffi.directions.navigation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Trip;

public class TripNavigatorActivity extends TripDetailsActivity {

    public static void start(
            final Activity contextActivity,
            final NetworkId network, final Trip trip, final RenderConfig renderConfig) {
        RenderConfig rc = new RenderConfig();
        rc.isNavigation = true;
        rc.isJourney = renderConfig.isJourney;
        Intent intent = buildStartIntent(TripNavigatorActivity.class, contextActivity, network, trip, rc);
        contextActivity.startActivity(intent);
    }

    protected static Intent buildStartIntent(
            final Class<? extends TripDetailsActivity> activityClass, final Context context,
            final NetworkId network, final Trip trip, final RenderConfig renderConfig) {
        Intent intent = TripDetailsActivity.buildStartIntent(activityClass, context, network, trip, renderConfig);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
//                    | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
//                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
//                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        Uri.Builder builder = new Uri.Builder().scheme("data").authority(activityClass.getName());
        for (final Trip.Leg leg: trip.legs) {
            if (leg instanceof Trip.Public) {
                JourneyRef journeyRef = ((Trip.Public) leg).journeyRef;
                builder.appendPath(journeyRef == null ? "-" : Integer.toString(journeyRef.hashCode()));
            }
        }
        Uri uri = builder.build();
        intent.setData(uri);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        // super.onBackPressed();
    }
}
