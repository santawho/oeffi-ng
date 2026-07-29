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

package de.schildbach.oeffi.directions.driverops;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryStoredTripsProvider;
import de.schildbach.oeffi.directions.QueryTripRunnable;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.TripUtils;
import de.schildbach.oeffi.directions.navigation.NavigationAlarmManager;
import de.schildbach.oeffi.directions.navigation.NotificationSoundManager;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.LineView;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Destination;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;

public class OperationNavigatorActivity extends OperationDetailsActivity {
    private static final long OPERATION_AUTO_REFRESH_INTERVAL_SECS = 110;
    public static final String INTENT_EXTRA_DELETEREQUEST = OperationNavigatorActivity.class.getName() + ".deleterequest";
    public static final int DELETEREQUEST_NOT_REQUESTED = 0;
    public static final int DELETEREQUEST_ASK = 1;
    public static final int DELETEREQUEST_FORCE = 2;
    public static final String INTENT_EXTRA_SHOWPAGE = OperationNavigatorActivity.class.getName() + ".showpage";

    public static boolean startNavigation(
            final Activity contextActivity,
            final NetworkId network,
            final Trip trip,
            final RenderConfig renderConfig,
            final boolean sameWindow) {
        if (!NavigationAlarmManager.getInstance().checkPermission(contextActivity))
            return false;

        final RenderConfig rc = new RenderConfig();
        rc.isNavigation = true;
        rc.isOperation = renderConfig.isOperation;
        rc.isJourney = renderConfig.isJourney;
        QueryTripRunnable.TripRequestData reloadRequestData = renderConfig.queryTripsRequestData;
        if (rc.queryTripsRequestData == null) {
            reloadRequestData = new QueryTripRunnable.TripRequestData();
            reloadRequestData.from = trip.from;
            reloadRequestData.to = trip.to;
            reloadRequestData.via = null;
            reloadRequestData.date = trip.getMinTime();
            reloadRequestData.dep = true;
            reloadRequestData.options = null;
        }
        renderConfig.queryTripsRequestData = reloadRequestData;

        final Intent intent = buildStartIntent(contextActivity, network, trip, rc,
                DELETEREQUEST_NOT_REQUESTED, Page.NEXT_EVENT, null, sameWindow);
        contextActivity.startActivity(intent);
        return true;
    }

    protected static Intent buildStartIntent(
            final Context context,
            final NetworkId network, final Trip trip, final RenderConfig renderConfig,
            final int deleteRequest, final Page setShowPage,
            final String playAlarmNotificationTag,
            final boolean sameWindow) {
        renderConfig.isNavigation = true;
        final Intent intent = TripDetailsActivity.buildStartIntent(OperationNavigatorActivity.class, context, network, trip, renderConfig);
        intent.putExtra(INTENT_EXTRA_DELETEREQUEST, deleteRequest);
        if (setShowPage != null)
            intent.putExtra(INTENT_EXTRA_SHOWPAGE, setShowPage.pageNum);
        intent.addFlags(
                (sameWindow ? Intent.FLAG_ACTIVITY_CLEAR_TASK : Intent.FLAG_ACTIVITY_NEW_TASK)
                        | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        // | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        // | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        // | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | (playAlarmNotificationTag != null ? Intent.FLAG_ACTIVITY_NO_USER_ACTION : 0)
                        | Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
        final Uri uri = new Uri.Builder()
                .scheme("data")
                .authority(OperationNavigatorActivity.class.getName())
                .path(network.name() + "/" + trip.getUniqueId())
                .build();
        intent.setData(uri);
        return intent;
    }

    private Runnable navigationRefreshRunnable;
    private long nextNavigationRefreshTime = 0;
    private boolean OperationNotificationBeingDeleted;
    private boolean permissionRequestRunning;
    private boolean announcementsEnabled = true;
    private boolean isStartupComplete = false;
    private boolean stillCheckForOtherNavigations;
    private long autoScrollInhibitTimeMs;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        swipeRefreshForTripList.setOnRefreshListener(this::refreshNavigationByUserCommand);
        swipeRefreshForTripList.setEnabled(true);

        swipeRefreshForNextEvent.setOnRefreshListener(this::refreshNavigationByUserCommand);
        swipeRefreshForNextEvent.setOnClickListener(v -> askStopNavigation());
        swipeRefreshForNextEvent.setEnabled(true);

        findViewById(R.id.navigation_next_event_container).setOnClickListener(v -> askStopNavigation());

        final Intent intent = getIntent();
        handleDeleteNotification(intent);
        handleSwitchToNextEvent(intent);

        stillCheckForOtherNavigations = true;
        mustEnableTrackButton = true;

        markTripAsDone(false);
    }

    @Override
    protected boolean mustOpenActivityInNewTask() {
        return true;
    }

    protected boolean shallShowChildActivitiesInNewTask() {
        return true;
    }

    @Override
    protected int getActionBarColorId() {
        return R.color.bg_action_bar_operation_navigation;
    }

    @Override
    protected int getActionBarTitleStringId() {
        return R.string.operation_navigation_title;
    }

    @Override
    protected void setupActionBar() {
        super.setupActionBar();
        actionBar.addProgressButton().setOnClickListener(buttonView -> refreshNavigationByUserCommand());
    }

    private static final String S_KEEP_AWAKE_NEVER = "never";
    private static final String S_KEEP_AWAKE_WHEN_CHARGING = "when-charging";
    private static final String S_KEEP_AWAKE_ALWAYS = "always";
    private int keepDisplayOnConfig;

    private long periodicUpdateIntervalMsOnBattery;
    private long periodicUpdateIntervalMsWhenCharging;

    private int getIntegerValueFromPrefs(final String key, final int defaultValueResId) {
        final String defaultValue = getString(defaultValueResId);
        final String value = prefs.getString(key, defaultValue);
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException nfe) {
            return Integer.parseInt(defaultValue);
        }
    }

    private void setupConfig() {
        final String value = prefs.getString("extras_drivermode_navigation_keep_active", S_KEEP_AWAKE_WHEN_CHARGING);
        keepDisplayOnConfig = S_KEEP_AWAKE_WHEN_CHARGING.equals(value) ? 0
                : S_KEEP_AWAKE_ALWAYS.equals(value) ? 1
                : -1;
        periodicUpdateIntervalMsOnBattery = 1000L * getIntegerValueFromPrefs(
                "extras_drivermode_navigation_refresh_battery_interval",
                R.string.default_drivermode_navigation_refresh_battery_interval);
        periodicUpdateIntervalMsWhenCharging = 1000L * getIntegerValueFromPrefs(
                "extras_drivermode_navigation_refresh_charging_interval",
                R.string.default_drivermode_navigation_refresh_charging_interval);
        autoScrollInhibitTimeMs = 1000L * getIntegerValueFromPrefs(
                "extras_drivermode_navigation_scroll_inhibit_interval",
                R.string.default_drivermode_navigation_scroll_inhibit_interval);
    }

    @Override
    protected long getPeriodicUpdateIntervalMs() {
        return isExternalPower()
                ? periodicUpdateIntervalMsWhenCharging
                : periodicUpdateIntervalMsOnBattery;
    }

    private void setMustKeepDisplayOn() {
        setMustKeepDisplayOn(keepDisplayOnConfig < 0 ? false
                : keepDisplayOnConfig > 0 ? true
                : isExternalPower());
    }

    @Override
    protected View.OnClickListener getStartNavigationClickListener() {
        return null;
    }

    @Override
    protected void addActionBarButtons() {
        actionBar.addButton(R.drawable.ic_clear_white_24dp, R.string.drivermode_navigation_action_cancel)
                .setOnClickListener(view -> askStopNavigation());

        if (prefs.getBoolean("extras_drivermode_announcements_enabled", false)) {
            announcementsEnabled = prefs.getBoolean("extras_drivermode_announcements_initially_active_enabled", true);
            final ToggleImageButton soundButton = actionBar.addToggleButton(R.drawable.ic_sound_white_24dp,
                    R.string.drivermode_navigation_action_annoucements);
            soundButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                announcementsEnabled = isChecked;
            });
            soundButton.setChecked(announcementsEnabled);
        } else {
            announcementsEnabled = false;
        }
    }

    private void stopNavigation(final boolean markAsDone) {
        OperationNotification.remove(this, getIntent());
        markTripAsDone(markAsDone);
        finishAndRemoveTask();
    }

    private void markTripAsDone(final boolean isDone) {
        final String tripId = tripRenderer.trip.getUniqueId();
        QueryStoredTripsProvider.updateStateFlags(getContentResolver(),
                network, getStoredTripsUsage(), tripId, isDone ? QueryStoredTripsProvider.STATE_FLAG_DONE : 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setupConfig();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressedEvent() {
        if (isShowingNextEvent())
            setShowPage(R.id.directions_trip_details_list_frame);
        else
            moveTaskToBack(true); // super.onBackPressedEvent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OperationNotificationBeingDeleted) {
            if (!permissionRequestRunning) {
                if (OperationNotification.requestPermissions(this, 1)) {
                    final boolean doNotificationUpdate = !isStartupComplete;
                    final boolean forceRefreshAll = !isStartupComplete;
                    refreshNavigation(doNotificationUpdate, forceRefreshAll, false);
                } else {
                    permissionRequestRunning = true;
                }
            }

            if (stillCheckForOtherNavigations) {
                stillCheckForOtherNavigations = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    askStopOtherNavigations();
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void askStopOtherNavigations() {
        final List<Intent> taskIntents = new ArrayList<>();
        final int myTaskId = getTaskId();
        final ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (final ActivityManager.AppTask appTask : activityManager.getAppTasks()) {
            final ActivityManager.RecentTaskInfo taskInfo = appTask.getTaskInfo();
            final ComponentName baseActivity = taskInfo.baseActivity;
            if (baseActivity == null)
                continue;
            final String activityClassName = baseActivity.getClassName();
            if (!activityClassName.equals(OperationNavigatorActivity.class.getName()))
                continue;
            if (taskInfo.taskId == myTaskId) // skip myself
                continue;
            taskIntents.add(taskInfo.baseIntent);
        }
        if (!taskIntents.isEmpty()) {
            DialogBuilder.get(this)
                    .setTitle(R.string.operation_stopnavothers_title)
                    .setMessage(R.string.operation_stopnavothers_text)
                    .setPositiveButton(R.string.operation_stopnavothers_stop, (dialogInterface, i) -> {
                        for (final Intent taskIntent : taskIntents) {
                            taskIntent.putExtra(INTENT_EXTRA_DELETEREQUEST, DELETEREQUEST_FORCE);
                            startActivity(taskIntent);
                        }
                    })
                    .setNegativeButton(R.string.operation_stopnavothers_continue, null)
                    .show();
        }
    }

    @Override
    protected void onNewIntent(@NonNull final Intent intent) {
        super.onNewIntent(intent);
        if (!handleDeleteNotification(intent)) {
            doCheckAutoRefresh(true);
            handleSwitchToNextEvent(intent);
        }
    }

    private void handleSwitchToNextEvent(final Intent intent) {
        final int setShowPageNum = intent.getIntExtra(INTENT_EXTRA_SHOWPAGE, -1);
        if (setShowPageNum >= 0)
            setShowPage(Page.getPageForNum(setShowPageNum));
    }

    private boolean handleDeleteNotification(final Intent intent) {
        final int deleteRequest = intent.getIntExtra(INTENT_EXTRA_DELETEREQUEST, 0);
        switch (deleteRequest) {
            case DELETEREQUEST_ASK:
                askStopNavigation();
                return true;
            case DELETEREQUEST_FORCE:
                stopNavigation(true);
                return true;
            case DELETEREQUEST_NOT_REQUESTED:
                break;
        }
        return false;
    }

    private void askStopNavigation() {
        OperationNotificationBeingDeleted = true;
        final DialogBuilder dialogBuilder = DialogBuilder.get(this, R.layout.operation_menu);
        final AlertDialog alertDialog = dialogBuilder
                .setOnCancelListener(dialog -> cancelStopNavigation())
                .setCancelable(true)
                .create();
        dialogBuilder.findViewById(R.id.operation_menu_continue).setOnClickListener(v -> {
            alertDialog.dismiss();
            cancelStopNavigation();
        });
        dialogBuilder.findViewById(R.id.operation_menu_stop_for_later).setOnClickListener(v -> {
            alertDialog.dismiss();
            stopNavigation(false);
            OperationsActivity.start(this);
        });
        dialogBuilder.findViewById(R.id.operation_menu_terminate).setOnClickListener(v -> {
            alertDialog.dismiss();
            stopNavigation(true);
            OperationsActivity.start(this);
        });

        loadNextOperation();

        final Trip.Public journeyLeg = nextTrip == null ? null : nextTrip.getFirstPublicLeg();
        if (journeyLeg == null) {
            dialogBuilder.findViewById(R.id.operation_menu_next_operation_container).setVisibility(View.GONE);
        } else {
            final LineView lineView = dialogBuilder.findViewById(R.id.operation_menu_next_operation_line);
            lineView.setLine(journeyLeg.line);
            final TextView destinationView = dialogBuilder.findViewById(R.id.operation_menu_next_operation_destination);
            final Destination destination = journeyLeg.destination;
            destinationView.setText(destination == null ? null : Constants.DESTINATION_ARROW_PREFIX
                    + Formats.makeBreakableStationName(Formats.fullLocationName(destination.location)));

            final View.OnClickListener onClickListener = v -> {
                alertDialog.dismiss();
                stopNavigation(true);
                startNextNavigation(nextTrip, nextTripsRequestData);
            };
            dialogBuilder.findViewById(R.id.operation_menu_next_operation).setOnClickListener(onClickListener);
            dialogBuilder.findViewById(R.id.operation_menu_next_operation_info).setOnClickListener(onClickListener);
        }

        alertDialog.show();
    }

    private void cancelStopNavigation() {
        OperationNotificationBeingDeleted = false;
        doCheckAutoRefresh(true);
        updateNotification(null);
    }

    private Trip nextTrip;
    private QueryTripRunnable.TripRequestData nextTripsRequestData;

    private void loadNextOperation() {
        final String currentTripId = tripRenderer.trip.getUniqueId();
        nextTrip = null;
        nextTripsRequestData = null;
        try (final Cursor cursor = getContentResolver().query(
                QueryStoredTripsProvider.CONTENT_URI_BUILDER(network, getStoredTripsUsage()).build(),
                null,
                QueryStoredTripsProvider.KEY_STATE_FLAGS + " & " + QueryStoredTripsProvider.STATE_FLAG_DONE + " =0", null,
                QueryStoredTripsProvider.KEY_DEPARTURE_TIME)) {
            if (cursor == null)
                return;
            while (cursor.moveToNext()) {
                final String tripId = cursor.getString(cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TRIP_ID));
                if (currentTripId.equals(tripId))
                    continue;
                nextTrip = (Trip) Objects.deserialize(cursor.getBlob(
                        cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TRIP)), true);
                nextTripsRequestData = (QueryTripRunnable.TripRequestData) Objects.deserialize(cursor.getBlob(
                        cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_RELOAD_REQUEST_DATA)), true);
                break;
            }
        }
    }

    private void startNextNavigation(
            final Trip trip,
            final QueryTripRunnable.TripRequestData tripsRequestData) {
        final TripDetailsActivity.RenderConfig renderConfig = new TripDetailsActivity.RenderConfig();
        renderConfig.isOperation = true;
        renderConfig.isJourney = true;
        renderConfig.queryTripsRequestData = tripsRequestData;
        final Trip journeyTrip = TripUtils.createTripFromJourneyTrip(trip);
        OperationNavigatorActivity.startNavigation(this, network, journeyTrip, renderConfig, false);

    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode,
            @NonNull final String[] permissions,
            @NonNull final int[] grantResults,
            final int deviceId) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId);
        boolean granted = true;
        for (final int grantResult : grantResults) {
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
        setMustKeepDisplayOn();
        return doCheckAutoRefresh(true);
    }

    private boolean doCheckAutoRefresh(final boolean doNotifcationUpdate) {
        if (isPaused)
            return false;
        if (nextNavigationRefreshTime >= 0) {
            final long now = new Date().getTime();
            if (now >= nextNavigationRefreshTime)
                refreshNavigation(doNotifcationUpdate, false, false);
        }
        return true;
    }

    private void refreshNavigationByUserCommand() {
        refreshNavigation(
                true,
                true,
                isTripDetailsLoadingEnabled());
    }

    private void refreshNavigation(
            final boolean doNotificationUpdate,
            final boolean forceRefreshAll,
            final boolean refreshTripDetails) {
        if (navigationRefreshRunnable != null)
            return;

        nextNavigationRefreshTime = -1; // block auto-refresh
        actionBar.startProgress();
        // swipeRefreshForTripList.setRefreshing(true);
        // swipeRefreshForNextEvent.setRefreshing(true);

        navigationRefreshRunnable = () -> {
            try {
                final Trip updatedTrip = forceRefreshAll
                    ? TripUtils.refreshTrip(network, tripRenderer.trip, forceRefreshAll, refreshTripDetails, new Date(), 30000)
                    : tripRenderer.trip;
                if (updatedTrip == null) {
                    handler.post(() -> new Toast(this).toast(R.string.toast_network_problem));
                } else {
                    if (doNotificationUpdate) {
                        isStartupComplete = true;
                        updateNotification(updatedTrip);
                    }
                    runOnUiThread(() -> onTripUpdated(updatedTrip));
                }
            } catch (final IOException ioe) {
                handler.post(() -> new Toast(this).toast(R.string.toast_network_problem));
            } finally {
                navigationRefreshRunnable = null;
                runOnUiThread(() -> {
                    swipeRefreshForTripList.setRefreshing(false);
                    swipeRefreshForNextEvent.setRefreshing(false);
                    actionBar.stopProgress();
                    nextNavigationRefreshTime = new Date().getTime()
                            + OPERATION_AUTO_REFRESH_INTERVAL_SECS * 1000;
                });
            }
        };
        backgroundHandler.post(navigationRefreshRunnable);
    }

    @Override
    protected boolean mustScrollIntoView() {
        return super.mustScrollIntoView()
                || (autoScrollInhibitTimeMs > 0
                    && System.currentTimeMillis() - lastUserInteractionTime > autoScrollInhibitTimeMs);
    }

    private void updateNotification(final Trip aTrip) {
        if (!isStartupComplete)
            return;

        final Trip trip = aTrip != null ? aTrip : tripRenderer.trip;
        final Intent intent = getIntent();
        final OperationNotification OperationNotification = new OperationNotification(intent);
        final OperationNotification.Configuration configuration = Objects.clone(OperationNotification.getConfiguration());
        OperationNotification.updateFromForeground(this, intent, trip, configuration,
                () -> runOnUiThread(this::updateGUI));
    }

    @Override
    protected boolean isShowSeconds() {
        // return isExternalPower();
        return true;
    }

    @Override
    protected boolean isShowRemaining() {
        return true;
    }

    @Override
    protected void updateNavigationInstructions() {
        super.updateNavigationInstructions();

        final int style = isShowSeconds() ? DateFormat.MEDIUM : DateFormat.SHORT;
        final String clockStr = DateFormat.getTimeInstance(style).format(new Date());
        ((TextView) findViewById(R.id.navigation_next_event_clock)).setText(clockStr);
    }

    private String lastNearestStopIdentityId;
    private boolean nextStopHasBeenAnnounced;
    private boolean thisStopHasBeenAnnounced;
    private boolean loggedLeaving, loggedHere;
    private long loggedApproachingMillis;

    @Override
    protected void processNearestStop(
            final Stop nearestStop,
            final boolean isEndOfJourney,
            final long timeLeftToStopMillis) {
        final String stopName = nearestStop.location.name;
        final String identityId = nearestStop.location.identityId;
        final boolean isNewStop = identityId != null && !identityId.equals(lastNearestStopIdentityId);

        if (isNewStop) {
            loggedLeaving = false;
            loggedHere = false;
            loggedApproachingMillis = 999999;
            nextStopHasBeenAnnounced = false;
            thisStopHasBeenAnnounced = false;
            lastNearestStopIdentityId = identityId;
        }

        if (timeLeftToStopMillis < 0) {
            if (!loggedLeaving) {
                loggedLeaving = true;
                loggedHere = false;
                loggedApproachingMillis = 999999;
                log.debug("leaving stop {}", stopName);
            }
        } else if (timeLeftToStopMillis == 0) {
            if (!loggedHere) {
                loggedHere = true;
                loggedLeaving = false;
                loggedApproachingMillis = 999999;
                log.debug("at stop {}", stopName);
            }
            announceNextStop(stopName, 0, isEndOfJourney);
            announceThisStop(stopName, isEndOfJourney);
        } else {
            final long secsLeft = timeLeftToStopMillis / 1000;
            if (Math.abs(loggedApproachingMillis - timeLeftToStopMillis) > 15000) {
                loggedApproachingMillis = timeLeftToStopMillis;
                loggedHere = false;
                loggedLeaving = false;
                log.debug("approaching stop {}, arrival in {} sec", stopName, secsLeft);
            }
            announceNextStop(stopName, secsLeft, isEndOfJourney);
        }
    }

    private void announceNextStop(final String stopName, final long secsLeft, final boolean isEndOfJourney) {
        if (!nextStopHasBeenAnnounced && secsLeft <= 60) {
            nextStopHasBeenAnnounced = true;
            log.debug("now near stop {}", stopName);
            announceStop(false, stopName, isEndOfJourney);
        }
    }

    private void announceThisStop(final String stopName, final boolean isEndOfJourney) {
        if (!thisStopHasBeenAnnounced) {
            thisStopHasBeenAnnounced = true;
            log.info("now at stop {}", stopName);
            if (prefs.getBoolean("extras_drivermode_announcements_this_stop_enabled", false)) {
                announceStop(true, stopName, isEndOfJourney);
            }
        }
    }

    private void announceStop(final boolean atStop, final String stopName, final boolean isEndOfJourney) {
        final StringBuilder sb = new StringBuilder(getString(
                atStop ? R.string.drivermode_announcement_this_stop : R.string.drivermode_announcement_next_stop,
                makeSpeakableLocationName(stopName)));
        if (isEndOfJourney) {
            sb.append(" . ");
            sb.append(getString(R.string.drivermode_announcement_journey_end));
        }

        final int gongSoundId = prefs.getBoolean("extras_drivermode_announcements_play_gong_enabled", true)
                ? R.raw.announcement_gong : 0;
        NotificationSoundManager.getInstance().playAlarmSoundAndVibration(
                AudioAttributes.USAGE_MEDIA,
                gongSoundId,
                null,
                Collections.singletonList(sb.toString()));
    }

    private String makeSpeakableLocationName(final String locationName) {
        if (locationName == null)
            return null;
        return removeDisturbingInterpunctuationFromSpeakableName(locationName);
    }

    private String removeDisturbingInterpunctuationFromSpeakableName(final String name) {
        return name.replaceAll("[,.][ .]*", " ");
    }
}
