package de.schildbach.oeffi.directions.navigation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import javax.net.ssl.SSLException;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryTripsRunnable;
import de.schildbach.oeffi.directions.TimeSpec;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.TripsOverviewActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;
import okhttp3.HttpUrl;

public class TripNavigatorActivity extends TripDetailsActivity {
    private static final Logger log = LoggerFactory.getLogger(TripNavigatorActivity.class);

    public static void start(
            final Activity contextActivity,
            final NetworkId network, final Trip trip, final RenderConfig renderConfig) {
        RenderConfig rc = new RenderConfig();
        rc.isNavigation = true;
        rc.isJourney = renderConfig.isJourney;
        rc.queryTripsRequestData = renderConfig.queryTripsRequestData;
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
    
    private QueryTripsRunnable queryTripsRunnable;

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (isShowingNextEvent())
            setShowNextEvent(false);
        else
            moveTaskToBack(true); // super.onBackPressed();
    }
    
    protected boolean onFindAlternativeConnections(
            final Stop stop,
            final JourneyRef feederJourneyRef, final JourneyRef connectionJourneyRef,
            QueryTripsRunnable.ReloadRequestData queryTripsRequestData) {
        final Date arrivalTime = stop.getArrivalTime();
        final TimeSpec.Absolute time = new TimeSpec.Absolute(TimeSpec.DepArr.DEPART,
                arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
        TripsOverviewActivity.RenderConfig overviewConfig = new TripsOverviewActivity.RenderConfig();
        overviewConfig.isAlternativeConnectionSearch = true;
        overviewConfig.referenceTime = time;
        overviewConfig.feederJourneyRef = feederJourneyRef;
        overviewConfig.connectionJourneyRef = connectionJourneyRef;
        overviewConfig.actionBarColor = R.color.bg_action_alternative_directions;

        final ProgressDialog progressDialog = ProgressDialog.show(TripNavigatorActivity.this, null,
                getString(R.string.directions_query_progress), true, true, dialog -> {
                    if (queryTripsRunnable != null)
                        queryTripsRunnable.cancel();
                });
        progressDialog.setCanceledOnTouchOutside(false);

        final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
        queryTripsRunnable = new QueryTripsRunnable(getResources(), progressDialog, handler,
                networkProvider, stop.location, null,
                queryTripsRequestData.to, time, queryTripsRequestData.options) {
            @Override
            protected void onPostExecute() {
                if (!isDestroyed())
                    progressDialog.dismiss();
            }

            @Override
            protected void onResult(final QueryTripsResult result, ReloadRequestData reloadRequestData) {
                if (result.status == QueryTripsResult.Status.OK) {
                    log.debug("Got {}", result.toShortString());

                    TripsOverviewActivity.start(TripNavigatorActivity.this, network,
                            TimeSpec.DepArr.DEPART, result, null, reloadRequestData, overviewConfig);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_FROM) {
                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_unknown_from);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_VIA) {
                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_unknown_via);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_TO) {
                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_unknown_to);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_LOCATION) {
                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_unknown_location);
                } else if (result.status == QueryTripsResult.Status.TOO_CLOSE) {
                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_too_close);
                } else if (result.status == QueryTripsResult.Status.UNRESOLVABLE_ADDRESS) {
                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_unresolvable_address);
                } else if (result.status == QueryTripsResult.Status.NO_TRIPS) {
                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_no_trips);
                } else if (result.status == QueryTripsResult.Status.INVALID_DATE) {
                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_invalid_date);
                } else if (result.status == QueryTripsResult.Status.SERVICE_DOWN) {
                    networkProblem();
                } else if (result.status == QueryTripsResult.Status.AMBIGUOUS) {
                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_ambiguous_location);
                }
            }

            @Override
            protected void onRedirect(final HttpUrl url) {
                new Toast(TripNavigatorActivity.this).longToast(R.string.directions_alert_redirect_message);
            }

            @Override
            protected void onBlocked(final HttpUrl url) {
                new Toast(TripNavigatorActivity.this).longToast(R.string.directions_alert_blocked_message);
            }

            @Override
            protected void onInternalError(final HttpUrl url) {
                new Toast(TripNavigatorActivity.this).longToast(R.string.directions_alert_internal_error_message);
            }

            @Override
            protected void onSSLException(final SSLException x) {
                new Toast(TripNavigatorActivity.this).longToast(R.string.directions_alert_ssl_exception_message);
            }

            private void networkProblem() {
                new Toast(TripNavigatorActivity.this).longToast(R.string.alert_network_problem_message);
            }
        };

        log.info("Executing: {}", queryTripsRunnable);

        backgroundHandler.post(queryTripsRunnable);
        return true;
    }
}
