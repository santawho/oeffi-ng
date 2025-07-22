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

package de.schildbach.oeffi.directions.navigation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import javax.net.ssl.SSLException;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryTripsRunnable;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.TripsOverviewActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;
import okhttp3.HttpUrl;

public class TripNavigatorActivity extends TripDetailsActivity {
    private static final Logger log = LoggerFactory.getLogger(TripNavigatorActivity.class);

    private static final long NAVIGATION_AUTO_REFRESH_INTERVAL_SECS = 110;
    public static final String INTENT_EXTRA_DELETEREQUEST = TripNavigatorActivity.class.getName() + ".deleterequest";
    public static final String INTENT_EXTRA_NEXTEVENT = TripNavigatorActivity.class.getName() + ".nextevent";

    public static void startNavigation(
            final Activity contextActivity,
            final NetworkId network, final Trip trip, final RenderConfig renderConfig,
            final boolean sameWindow) {
            RenderConfig rc = new RenderConfig();
        rc.isNavigation = true;
        rc.isJourney = renderConfig.isJourney;
        rc.queryTripsRequestData = renderConfig.queryTripsRequestData;
        Intent intent = buildStartIntent(contextActivity, network, trip, rc, false, false, sameWindow);
        contextActivity.startActivity(intent);
    }

    protected static Intent buildStartIntent(
            final Context context,
            final NetworkId network, final Trip trip, final RenderConfig renderConfig,
            final boolean deleteRequest, final boolean showNextEvent,
            final boolean sameWindow) {
        renderConfig.isNavigation = true;
        final Intent intent = TripDetailsActivity.buildStartIntent(TripNavigatorActivity.class, context, network, trip, renderConfig);
        intent.putExtra(INTENT_EXTRA_DELETEREQUEST, deleteRequest);
        intent.putExtra(INTENT_EXTRA_NEXTEVENT, showNextEvent);
        intent.addFlags(
                sameWindow ? Intent.FLAG_ACTIVITY_CLEAR_TASK : Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                // | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                // | Intent.FLAG_ACTIVITY_SINGLE_TOP
                // | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
        final Uri uri = new Uri.Builder()
                .scheme("data")
                .authority(TripNavigatorActivity.class.getName())
                .path(network.name() + "/" + trip.getUniqueId())
                .build();
        intent.setData(uri);
        return intent;
    }

    private Navigator navigator;
    private QueryTripsRunnable queryTripsRunnable;
    private Runnable navigationRefreshRunnable;
    private long nextNavigationRefreshTime = 0;
    private boolean navigationNotificationBeingDeleted;
    private BroadcastReceiver updateTriggerReceiver;
    private boolean permissionRequestRunning;
    private boolean soundEnabled = true;
    private boolean isStartupComplete = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        handleDeleteNotification(intent);
        handleSwitchToNextEvent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // trigger from notification refresher
        updateTriggerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isPaused) {
                    nextNavigationRefreshTime = 1; // forces refresh
                    if (!doCheckAutoRefresh(false))
                        updateGUI();
                }
            }
        };
        ContextCompat.registerReceiver(this, updateTriggerReceiver,
                new IntentFilter(NavigationNotification.ACTION_UPDATE_TRIGGER),
                ContextCompat.RECEIVER_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (updateTriggerReceiver != null) {
            unregisterReceiver(updateTriggerReceiver);
            updateTriggerReceiver = null;
        }
    }

    @Override
    protected void setupFromTrip(final Trip trip) {
        final Trip tripFromPresentNotification = new NavigationNotification(this, getIntent()).getTrip();
        navigator = new Navigator(network, tripFromPresentNotification);
        super.setupFromTrip(navigator.getCurrentTrip());
    }

    @Override
    protected void setupActionBar() {
        setPrimaryColor(renderConfig.isAlternativeConnectionSearch
                        ? R.color.bg_action_alternative_directions
                        : R.color.bg_action_bar_navigation);
        actionBar.setPrimaryTitle(getString(R.string.navigation_details_title));
        actionBar.addProgressButton().setOnClickListener(buttonView ->
                refreshNavigation(true, true));
    }

    @Override
    protected void addActionBarButtons() {
        actionBar.addButton(R.drawable.ic_clear_white_24dp, R.string.directions_trip_navigation_action_cancel)
                .setOnClickListener(view -> askStopNavigation());

        final ToggleImageButton soundButton = actionBar.addToggleButton(R.drawable.ic_sound_white_24dp,
                R.string.directions_trip_navigation_action_sound);
        soundButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            soundEnabled = isChecked;
            updateNotification(null);
        });
        soundButton.setChecked(soundEnabled);
    }

    private void stopNavigation() {
        NavigationNotification.remove(this, getIntent());
        finishAndRemoveTask();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (isShowingNextEvent())
            setShowNextEvent(false);
        else
            moveTaskToBack(true); // super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!navigationNotificationBeingDeleted) {
            if (!permissionRequestRunning) {
                if (NavigationNotification.requestPermissions(this, 1)) {
                    final boolean doNotificationUpdate = !isStartupComplete;
                    final boolean forceRefreshAll = !isStartupComplete;
                    refreshNavigation(doNotificationUpdate, forceRefreshAll);
                } else {
                    permissionRequestRunning = true;
                }
            }
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (!handleDeleteNotification(intent)) {
            doCheckAutoRefresh(true);
            handleSwitchToNextEvent(intent);
        }
    }

    private void handleSwitchToNextEvent(final Intent intent) {
        final boolean showNextEvent = intent.getBooleanExtra(INTENT_EXTRA_NEXTEVENT, false);
        setShowNextEvent(showNextEvent);
    }

    private boolean handleDeleteNotification(final Intent intent) {
        final boolean deleteRequest = intent.getBooleanExtra(INTENT_EXTRA_DELETEREQUEST, false);
        if (!deleteRequest)
            return false;

        askStopNavigation();
        return true;
    }

    private void askStopNavigation() {
        navigationNotificationBeingDeleted = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.navigation_stopnav_title)
                .setMessage(R.string.navigation_stopnav_text)
                .setPositiveButton(R.string.navigation_stopnav_stop, (dialogInterface, i) -> {
                    stopNavigation();
                })
                .setNegativeButton(R.string.navigation_stopnav_continue, (dialogInterface, i) -> {
                    navigationNotificationBeingDeleted = false;
                    doCheckAutoRefresh(true);
                    updateNotification(null);
                })
                .create().show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, int deviceId) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId);
        boolean granted = true;
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        if (granted) {
            permissionRequestRunning = false;
            updateNotification(null);
        } else {
            // warning ??
        }
    }

    @Override
    protected boolean checkAutoRefresh() {
        if (!isStartupComplete)
            return false;
        return doCheckAutoRefresh(true);
    }

    private boolean doCheckAutoRefresh(final boolean doNotifcationUpdate) {
        if (isPaused) return false;
        if (nextNavigationRefreshTime < 0) return false;
        long now = new Date().getTime();
        if (now < nextNavigationRefreshTime) return false;
        refreshNavigation(doNotifcationUpdate, false);
        return true;
    }

    private void refreshNavigation(final boolean doNotificationUpdate, final boolean forceRefreshAll) {
        if (navigationRefreshRunnable != null)
            return;

        nextNavigationRefreshTime = -1; // block auto-refresh
        actionBar.startProgress();

        navigationRefreshRunnable = () -> {
            try {
                Trip updatedTrip = navigator.refresh(forceRefreshAll, new Date());
                if (updatedTrip == null) {
                    handler.post(() -> new Toast(this).toast(R.string.toast_network_problem));
                } else {
                    if (doNotificationUpdate) {
                        isStartupComplete = true;
                        updateNotification(updatedTrip);
                    }
                    runOnUiThread(() -> onTripUpdated(updatedTrip));
                }
            } catch (IOException e) {
                handler.post(() -> new Toast(this).toast(R.string.toast_network_problem));
            } finally {
                navigationRefreshRunnable = null;
                runOnUiThread(() -> {
                    actionBar.stopProgress();
                    nextNavigationRefreshTime = new Date().getTime()
                            + NAVIGATION_AUTO_REFRESH_INTERVAL_SECS * 1000;
                });
            }
        };
        backgroundHandler.post(navigationRefreshRunnable);
    }

    @Override
    protected boolean onFindAlternativeConnections(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            QueryTripsRunnable.TripRequestData queryTripsRequestData) {
        final Date arrivalTime = stop.getArrivalTime();
        final TimeSpec.Absolute time = new TimeSpec.Absolute(TimeSpec.DepArr.DEPART,
                arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
        final TripsOverviewActivity.RenderConfig overviewConfig = getOverviewConfig(
                stop, isLegDeparture, currentJourneyRef, feederJourneyRef, connectionJourneyRef, time);

        final ProgressDialog progressDialog = ProgressDialog.show(TripNavigatorActivity.this, null,
                getString(R.string.directions_query_progress), true, true, dialog -> {
                    if (queryTripsRunnable != null)
                        queryTripsRunnable.cancel();
                });
        progressDialog.setCanceledOnTouchOutside(false);

        final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
        queryTripsRunnable = new QueryTripsRunnable(getResources(), progressDialog, handler,
                networkProvider, stop.location, null,
                queryTripsRequestData != null ? queryTripsRequestData.to : getLastPublicLocation(),
                time,
                queryTripsRequestData != null ? queryTripsRequestData.options : getTripOptionsFromPrefs()) {
            @Override
            protected void onPostExecute() {
                if (!isDestroyed())
                    progressDialog.dismiss();
            }

            @Override
            protected void onResult(final QueryTripsResult result, TripRequestData reloadRequestData) {
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

    private void updateNotification(final Trip aTrip) {
        if (!isStartupComplete)
            return;

        final Trip trip = aTrip != null ? aTrip : tripRenderer.trip;
        final Intent intent = getIntent();
        final NavigationNotification navigationNotification = new NavigationNotification(this, intent);
        NavigationNotification.Configuration configuration = Objects.clone(navigationNotification.getConfiguration());
        configuration.soundEnabled = soundEnabled;
        NavigationNotification.updateFromForeground(this, intent, trip, configuration);
    }

    @Override
    protected TripsOverviewActivity.RenderConfig getOverviewConfig(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final TimeSpec.Absolute time) {
        final TripsOverviewActivity.RenderConfig overviewConfig = super.getOverviewConfig(
                stop, isLegDeparture, currentJourneyRef, feederJourneyRef, connectionJourneyRef, time);
        overviewConfig.actionBarColor = R.color.bg_action_alternative_directions;
        return overviewConfig;
    }

    @Override
    protected boolean updateIndividualLeg(final View row, final TripRenderer.LegContainer legC, final Date now) {
        final ImageButton progressBell = row.findViewById(R.id.directions_trip_details_individual_entry_progress_bell);
        progressBell.setVisibility(View.GONE);

        final boolean isNow = super.updateIndividualLeg(row, legC, now);
        if (!isNow)
            return isNow;

        final boolean isInitialIndividualLeg = legC.legIndex <= 0;
        if (!isInitialIndividualLeg)
            return isNow;

        final TripRenderer.LegContainer transferTo = legC.transferTo;
        if (transferTo == null)
            return isNow;

        final Trip.Public firstPublicLeg = transferTo.publicLeg;
        if (firstPublicLeg == null)
            return isNow;

        progressBell.setVisibility(View.VISIBLE);

        final NavigationNotification navigationNotification = new NavigationNotification(this, getIntent());
        progressBell.setOnClickListener(v -> new StartAlarmManager().showConfigureStartAlarmDialog(
                this, getIntent(),
                isAlarmActive -> updateBellState(progressBell, isAlarmActive)));

        final Integer startAlarmMinutes = navigationNotification.getConfiguration().startAlarmMinutes;
        updateBellState(progressBell,startAlarmMinutes != null);

        return isNow;
    }

    private void updateBellState(final ImageButton progressBell, final boolean isAlarmActive) {
        final Drawable drawable = getDrawable(isAlarmActive
                ? R.drawable.ic_bell_on_black_24dp
                : R.drawable.ic_bell_off_black_24dp);
        final int colorId = isAlarmActive ? R.color.fg_significant : R.color.fg_insignificant;
        drawable.setTint(getColor(colorId));
        progressBell.setImageDrawable(drawable);
    }
}
