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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.directions.QueryTripsRunnable;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.TripsOverviewActivity;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Trip;

public class TripNavigatorActivity extends TripDetailsActivity {
    private static final long NAVIGATION_AUTO_REFRESH_INTERVAL_SECS = 110;
    public static final String INTENT_EXTRA_DELETEREQUEST = TripNavigatorActivity.class.getName() + ".deleterequest";
    public static final String INTENT_EXTRA_NEXTEVENT = TripNavigatorActivity.class.getName() + ".nextevent";
    public static final String INTENT_EXTRA_PLAYALARM = TripNavigatorActivity.class.getName() + ".playalarm";

    public static boolean startNavigation(
            final Activity contextActivity,
            final NetworkId network, final Trip trip, final RenderConfig renderConfig,
            final boolean sameWindow) {
        if (!NavigationAlarmManager.getInstance().checkPermission(contextActivity))
            return false;

        final RenderConfig rc = new RenderConfig();
        rc.isNavigation = true;
        rc.isJourney = renderConfig.isJourney;
        rc.queryTripsRequestData = renderConfig.queryTripsRequestData;
        final Intent intent = buildStartIntent(contextActivity, network, trip, rc,
                false, Page.NEXT_EVENT, null, sameWindow);
        contextActivity.startActivity(intent);
        return true;
    }

    protected static Intent buildStartIntent(
            final Context context,
            final NetworkId network, final Trip trip, final RenderConfig renderConfig,
            final boolean deleteRequest, final Page setShowPage,
            final String playAlarmNotificationTag,
            final boolean sameWindow) {
        renderConfig.isNavigation = true;
        final Intent intent = TripDetailsActivity.buildStartIntent(TripNavigatorActivity.class, context, network, trip, renderConfig);
        intent.putExtra(INTENT_EXTRA_DELETEREQUEST, deleteRequest);
        if (setShowPage != null)
            intent.putExtra(INTENT_EXTRA_NEXTEVENT, setShowPage.pageNum);
        if (playAlarmNotificationTag != null)
            intent.putExtra(INTENT_EXTRA_PLAYALARM, playAlarmNotificationTag);
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
                .authority(TripNavigatorActivity.class.getName())
                .path(network.name() + "/" + trip.getUniqueId())
                .build();
        intent.setData(uri);
        return intent;
    }

    private Navigator navigator;
    private Runnable navigationRefreshRunnable;
    private long nextNavigationRefreshTime = 0;
    private boolean navigationNotificationBeingDeleted;
    private BroadcastReceiver updateTriggerReceiver;
    private boolean permissionRequestRunning;
    private boolean soundEnabled = true;
    private boolean isStartupComplete = false;
    EventLogEntryView eventLogLatestView;
    ListView eventLogListView;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        findViewById(R.id.navigation_event_log).setVisibility(View.VISIBLE);

        eventLogLatestView = findViewById(R.id.navigation_event_log_latest);
        eventLogListView = findViewById(R.id.navigation_event_log_list);
        eventLogLatestView.setTextSize(getResources().getDimension(R.dimen.font_size_large));

        swipeRefreshForTripList.setOnRefreshListener(this::refreshNavigation);
        swipeRefreshForTripList.setEnabled(true);
        swipeRefreshForNextEvent.setOnRefreshListener(this::refreshNavigation);
        swipeRefreshForNextEvent.setEnabled(true);

        final Intent intent = getIntent();
        handleDeleteNotification(intent);
        handleSwitchToNextEvent(intent);
        handlePlayAlarm(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // trigger from notification refresher
        updateTriggerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
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
    protected boolean allowScreenLock() {
        return true;
    }

    @Override
    protected void setupFromTrip(final Trip trip) {
        final Trip tripFromPresentNotification = new NavigationNotification(getIntent()).getTrip();
        navigator = new Navigator(network, tripFromPresentNotification);
        super.setupFromTrip(navigator.getCurrentTrip());
    }

    @Override
    protected void setupActionBar() {
        setPrimaryColor(renderConfig.isAlternativeConnectionSearch
                        ? R.color.bg_action_alternative_directions
                        : R.color.bg_action_bar_navigation);
        actionBar.setPrimaryTitle(getString(R.string.navigation_details_title));
        actionBar.addProgressButton().setOnClickListener(buttonView -> refreshNavigation());
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
    public void onBackPressedEvent() {
        if (isShowingNextEvent())
            setShowPage(R.id.directions_trip_details_list_frame);
        else
            moveTaskToBack(true); // super.onBackPressedEvent();
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
    protected void onNewIntent(@NonNull final Intent intent) {
        super.onNewIntent(intent);
        if (!handleDeleteNotification(intent)) {
            doCheckAutoRefresh(true);
            handleSwitchToNextEvent(intent);
            handlePlayAlarm(intent);
        }
    }

    private void handleSwitchToNextEvent(final Intent intent) {
        final int setShowPageNum = intent.getIntExtra(INTENT_EXTRA_NEXTEVENT, -1);
        if (setShowPageNum >= 0)
            setShowPage(Page.getPageForNum(setShowPageNum));
    }

    private boolean handleDeleteNotification(final Intent intent) {
        final boolean deleteRequest = intent.getBooleanExtra(INTENT_EXTRA_DELETEREQUEST, false);
        if (!deleteRequest)
            return false;

        askStopNavigation();
        return true;
    }

    private void handlePlayAlarm(final Intent intent) {
        final String playAlarmNotificationTag = intent.getStringExtra(INTENT_EXTRA_PLAYALARM);
        if (playAlarmNotificationTag == null)
            return;

        new TravelAlarmManager(this).showAlarmPopupDialog(playAlarmNotificationTag);
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
        return doCheckAutoRefresh(true);
    }

    private boolean doCheckAutoRefresh(final boolean doNotifcationUpdate) {
        if (isPaused) return false;
        if (nextNavigationRefreshTime < 0) return false;
        final long now = new Date().getTime();
        if (now < nextNavigationRefreshTime) return false;
        refreshNavigation(doNotifcationUpdate, false);
        return true;
    }

    private void refreshNavigation() {
        refreshNavigation(true, true);
    }

    private void refreshNavigation(final boolean doNotificationUpdate, final boolean forceRefreshAll) {
        if (navigationRefreshRunnable != null)
            return;

        nextNavigationRefreshTime = -1; // block auto-refresh
        actionBar.startProgress();
        // swipeRefreshForTripList.setRefreshing(true);
        // swipeRefreshForNextEvent.setRefreshing(true);

        navigationRefreshRunnable = () -> {
            try {
                final Trip updatedTrip = navigator.refresh(forceRefreshAll, new Date());
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
                    swipeRefreshForTripList.setRefreshing(false);
                    swipeRefreshForNextEvent.setRefreshing(false);
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
            final QueryTripsRunnable.TripRequestData queryTripsRequestData) {
        final PTDate arrivalTime = stop.getArrivalTime();
        final TimeSpec.Absolute time = new TimeSpec.Absolute(TimeSpec.DepArr.DEPART,
                arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
        final TripsOverviewActivity.RenderConfig overviewConfig = getOverviewConfig(
                stop, isLegDeparture, currentJourneyRef, feederJourneyRef, connectionJourneyRef, time);

//        final ProgressDialog progressDialog = ProgressDialog.show(TripNavigatorActivity.this, null,
//                getString(R.string.directions_query_progress), true, true, dialog -> {
//                    if (queryTripsRunnable != null)
//                        queryTripsRunnable.cancel();
//                });
//        progressDialog.setCanceledOnTouchOutside(false);
//
//        final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
//        queryTripsRunnable = new QueryTripsRunnable(getResources(), progressDialog, handler,
//                networkProvider, stop.location, null,
//                queryTripsRequestData != null ? queryTripsRequestData.to : getLastPublicLocation(),
//                time,
//                queryTripsRequestData != null ? queryTripsRequestData.options : getTripOptionsFromPrefs()) {
//            @Override
//            protected void onPostExecute() {
//                if (!isDestroyed())
//                    progressDialog.dismiss();
//            }
//
//            @Override
//            protected void onResult(final QueryTripsResult result, TripRequestData reloadRequestData) {
//                if (result.status == QueryTripsResult.Status.OK) {
//                    log.debug("Got {}", result.toShortString());
//
//                    TripsOverviewActivity.start(TripNavigatorActivity.this, network,
//                            TimeSpec.DepArr.DEPART, result, null, reloadRequestData, overviewConfig);
//                } else if (result.status == QueryTripsResult.Status.UNKNOWN_FROM) {
//                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_unknown_from);
//                } else if (result.status == QueryTripsResult.Status.UNKNOWN_VIA) {
//                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_unknown_via);
//                } else if (result.status == QueryTripsResult.Status.UNKNOWN_TO) {
//                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_unknown_to);
//                } else if (result.status == QueryTripsResult.Status.UNKNOWN_LOCATION) {
//                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_unknown_location);
//                } else if (result.status == QueryTripsResult.Status.TOO_CLOSE) {
//                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_too_close);
//                } else if (result.status == QueryTripsResult.Status.UNRESOLVABLE_ADDRESS) {
//                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_unresolvable_address);
//                } else if (result.status == QueryTripsResult.Status.NO_TRIPS) {
//                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_no_trips);
//                } else if (result.status == QueryTripsResult.Status.INVALID_DATE) {
//                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_invalid_date);
//                } else if (result.status == QueryTripsResult.Status.SERVICE_DOWN) {
//                    networkProblem();
//                } else if (result.status == QueryTripsResult.Status.AMBIGUOUS) {
//                    new Toast(TripNavigatorActivity.this).longToast(R.string.directions_message_ambiguous_location);
//                }
//            }
//
//            @Override
//            protected void onRedirect(final HttpUrl url) {
//                new Toast(TripNavigatorActivity.this).longToast(R.string.directions_alert_redirect_message);
//            }
//
//            @Override
//            protected void onBlocked(final HttpUrl url) {
//                new Toast(TripNavigatorActivity.this).longToast(R.string.directions_alert_blocked_message);
//            }
//
//            @Override
//            protected void onInternalError(final HttpUrl url) {
//                new Toast(TripNavigatorActivity.this).longToast(R.string.directions_alert_internal_error_message);
//            }
//
//            @Override
//            protected void onSSLException(final SSLException x) {
//                new Toast(TripNavigatorActivity.this).longToast(R.string.directions_alert_ssl_exception_message);
//            }
//
//            private void networkProblem() {
//                new Toast(TripNavigatorActivity.this).longToast(R.string.alert_network_problem_message);
//            }
//        };
//
//        log.info("Executing: {}", queryTripsRunnable);
//
//        backgroundHandler.post(queryTripsRunnable);
        DirectionsActivity.start(TripNavigatorActivity.this,
                stop.location,
                tripRenderer.trip.to,
                null,
                time,
                overviewConfig,
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return true;
    }

    private void updateNotification(final Trip aTrip) {
        if (!isStartupComplete)
            return;

        final Trip trip = aTrip != null ? aTrip : tripRenderer.trip;
        final Intent intent = getIntent();
        final NavigationNotification navigationNotification = new NavigationNotification(intent);
        final NavigationNotification.Configuration configuration = Objects.clone(navigationNotification.getConfiguration());
        configuration.soundEnabled = soundEnabled;
        NavigationNotification.updateFromForeground(this, intent, trip, configuration,
                () -> runOnUiThread(this::updateGUI));
    }

    @Override
    protected TripsOverviewActivity.RenderConfig getOverviewConfig(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final TimeSpec time) {
        final TripsOverviewActivity.RenderConfig overviewConfig = super.getOverviewConfig(
                stop, isLegDeparture, currentJourneyRef, feederJourneyRef, connectionJourneyRef, time);
        overviewConfig.actionBarColor = R.color.bg_action_alternative_directions;
        return overviewConfig;
    }

    private NavigationNotification guiUpdateNavigationNotification;

    @Override
    protected boolean updateGUI() {
        guiUpdateNavigationNotification = new NavigationNotification(getIntent());
        if (!super.updateGUI())
            return false;
        updateEventLog();
        return true;
    }

    @Override
    protected boolean updateIndividualLeg(final View row, final TripRenderer.LegContainer legC, final Date now) {
        final boolean isNow = super.updateIndividualLeg(row, legC, now);

        if (isNow) {
            setupAlarmBellButton(row, R.id.directions_trip_details_individual_entry_progress_bell,
                    legC.transferTo, true);
        }

        return isNow;
    }

    @Override
    protected boolean updatePublicLeg(final View row, final TripRenderer.LegContainer legC, final TripRenderer.LegContainer walkLegC, final TripRenderer.LegContainer nextLegC, final Date now) {
        final boolean isNow = super.updatePublicLeg(row, legC, walkLegC, nextLegC, now);

        if (isNow) {
            setupAlarmBellButton(row, R.id.directions_trip_details_public_entry_progress_bell,
                    legC, false);
        }

        return isNow;
    }

    private void setupAlarmBellButton(
            final View row,
            final int bellResId,
            final TripRenderer.LegContainer legC,
            final boolean alarmIsForDeparture) {
        final ImageButton bellButton = row.findViewById(bellResId);

        bellButton.setVisibility(View.VISIBLE);
        bellButton.setOnClickListener(v -> {
            new TravelAlarmManager(this).showConfigureTravelAlarmDialog(
                    legC, alarmIsForDeparture, getIntent(), this::updateGUI);
        });

        final boolean isAlarmActive;
        if (legC != null) {
            final TravelAlarmManager.TravelAlarmState travelAlarmState =
                    new TravelAlarmManager(this)
                            .new TravelAlarmState(legC, alarmIsForDeparture, guiUpdateNavigationNotification);
            isAlarmActive = travelAlarmState.isAlarmActive;
        } else {
            isAlarmActive = false;
        }

        setupBellButton(bellButton, isAlarmActive);
    }

    @Override
    protected void updateNavigationInstructions() {
        super.updateNavigationInstructions();

        final TripRenderer.LegContainer currentLeg = tripRenderer.currentLeg;
        final View alarmView = findViewById(R.id.navigation_next_event_alarm);
        if (currentLeg == null) {
            alarmView.setVisibility(View.GONE);
            return;
        }

        final boolean alarmIsForDeparture;
        final TripRenderer.LegContainer legContainer;
        if (tripRenderer.nextEventTypeIsPublic) {
            alarmIsForDeparture = false;
            legContainer = currentLeg;
        } else {
            alarmIsForDeparture = true;
            legContainer = currentLeg.transferTo;
        }

        alarmView.setVisibility(View.VISIBLE);
        alarmView.setOnClickListener(v -> {
            new TravelAlarmManager(this).showConfigureTravelAlarmDialog(
                    legContainer, alarmIsForDeparture,
                    getIntent(), this::updateGUI);
        });

        final long alarmAtMs;
        final boolean isAlarmActive;
        if (legContainer != null) {
            final TravelAlarmManager.TravelAlarmState travelAlarmState =
                    new TravelAlarmManager(this).new TravelAlarmState(
                            legContainer, alarmIsForDeparture,
                            guiUpdateNavigationNotification);
            alarmAtMs = travelAlarmState.currentTravelAlarmAtMs;
            isAlarmActive = travelAlarmState.isAlarmActive;
        } else {
            alarmAtMs = 0;
            isAlarmActive = false;
        }

        final long now = System.currentTimeMillis();

        final View detailsView = findViewById(R.id.navigation_next_event_alarm_details);
        final TextView timeView = findViewById(R.id.navigation_next_event_alarm_details_time);
        final TextView offsetView = findViewById(R.id.navigation_next_event_alarm_details_offset);
        final TextView sleepView = findViewById(R.id.navigation_next_event_alarm_details_sleep);
        if (alarmAtMs <= 0) {
            detailsView.setVisibility(View.GONE);
        } else {
            detailsView.setVisibility(View.VISIBLE);
            timeView.setText(Formats.formatTime(timeZoneSelector, alarmAtMs, PTDate.SYSTEM_OFFSET));
            offsetView.setText(formatDuration(tripRenderer.nextEventEstimatedTime.getTime() - alarmAtMs));
            final ImageView sleepIcon = findViewById(R.id.navigation_next_event_alarm_details_sleep_icon);
            final long sleepMs = alarmAtMs - now;
            if (sleepMs > 0) {
                sleepView.setVisibility(View.VISIBLE);
                sleepIcon.setVisibility(View.VISIBLE);
                sleepView.setText(formatDuration(sleepMs));
            } else {
                sleepView.setVisibility(View.GONE);
                sleepIcon.setVisibility(View.GONE);
            }
        }

        setupBellButton(findViewById(R.id.navigation_next_event_alarm_bell), isAlarmActive);
    }

    private void setupBellButton(final ImageView bellButton, final boolean isAlarmActive) {
        final int drawableResId;
        final int colorId;
        if (isAlarmActive) {
            drawableResId = R.drawable.ic_bell_on_black_24dp;
            colorId = R.color.fg_significant;
        } else {
            drawableResId = R.drawable.ic_bell_off_black_24dp;
            colorId = R.color.fg_insignificant;
        }
        final Drawable drawable = getDrawable(drawableResId);
        drawable.setTint(getColor(colorId));
        bellButton.setImageDrawable(drawable);
    }

    private String formatDuration(final long millis) {
        final long minutes = (millis + 30000) / 60000;
        if (minutes < 100)
            return Long.toString(minutes);
        final long hours = (minutes + 30) / 60;
        return hours + "h";
    }

    @Override
    protected boolean isShowTravelAlarm() {
        return true;
    }

    protected boolean shallShowChildActivitiesInNewTask() {
        return true;
    }

    @Override
    protected boolean onStationContextMenuItemClicked(
            final int menuItemId, final TripRenderer.LegContainer legC, final Stop stop) {
        if (menuItemId == R.id.station_context_set_departure_travel_alarm) {
            new TravelAlarmManager(this)
                    .showConfigureTravelAlarmDialog(legC, true, getIntent(), this::updateGUI);
            return true;
        }

        if (menuItemId == R.id.station_context_set_arrival_travel_alarm) {
            new TravelAlarmManager(this)
                    .showConfigureTravelAlarmDialog(legC, false, getIntent(), this::updateGUI);
            return true;
        }

        return false;
    }

    private void updateEventLog() {
        final View noEntriesView = findViewById(R.id.navigation_event_log_no_entries);
        final NavigationNotification.ExtraData extraData = guiUpdateNavigationNotification.getExtraData();
        final NavigationNotification.EventLogEntry[] logEntries = extraData == null ? null : extraData.eventLogEntries;
        if (logEntries == null || logEntries.length == 0) {
            noEntriesView.setVisibility(View.VISIBLE);
            eventLogLatestView.setVisibility(View.GONE);
            eventLogListView.setVisibility(View.GONE);
            return;
        }

        noEntriesView.setVisibility(View.GONE);
        eventLogLatestView.setVisibility(View.VISIBLE);
        eventLogListView.setVisibility(View.VISIBLE);

        eventLogLatestView.setLogEntry(logEntries[logEntries.length - 1]);

        eventLogListView.setAdapter(new BaseAdapter() {
            @Override
            public boolean isEnabled(final int position) {
                return false;
            }

            @Override
            public int getCount() {
                return logEntries.length - 1;
            }

            @Override
            public NavigationNotification.EventLogEntry getItem(final int position) {
                return logEntries[logEntries.length - 2 - position];
            }

            @Override
            public long getItemId(final int position) {
                return logEntries.length - 2 - position;
            }

            @Override
            public View getView(final int position, final View convertView, final ViewGroup parent) {
                final EventLogEntryView logView;
                if (convertView != null) {
                    logView = (EventLogEntryView) convertView;
                } else {
                    logView = (EventLogEntryView) getLayoutInflater().inflate(
                            R.layout.navigation_event_log_entry, parent, false);
                }
                logView.setLogEntry(getItem(position));
                return logView;
            }
        });
    }

    public static class EventLogEntryView extends LinearLayout {
        TextView timestampView;
        TextView messageView;
        NavigationNotification.EventLogEntry eventLogEntry;

        public EventLogEntryView(final Context context) {
            this(context, null);
        }

        public EventLogEntryView(final Context context, @Nullable final AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public EventLogEntryView(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr) {
            this(context, attrs, defStyleAttr, 0);
        }

        public EventLogEntryView(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
        }

        @Override
        protected void onFinishInflate() {
            super.onFinishInflate();

            timestampView = findViewById(R.id.navigation_event_log_entry_timestamp);
            messageView = findViewById(R.id.navigation_event_log_entry_message);
        }

        public void setTextSize(final float fontSize) {
            timestampView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
            messageView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
        }

        public void setLogEntry(final NavigationNotification.EventLogEntry eventLogEntry) {
            this.eventLogEntry = eventLogEntry;
            final Application application = Application.getInstance();
            final String timeText = String.format("%s (%s)",
                    Formats.formatTime(application.getSystemTimeZoneSelector(), eventLogEntry.timestamp, 0),
                    Formats.formatTimeDiff(application, System.currentTimeMillis(), eventLogEntry.timestamp));
            timestampView.setText(timeText);
            messageView.setText(Html.fromHtml(eventLogEntry.message, Html.FROM_HTML_MODE_COMPACT));
            final int textColorId;
            switch (eventLogEntry.type) {
                case PUBLIC_LEG_START:
                case PUBLIC_LEG_END:
                case FINAL_TRANSFER:
                    textColorId = R.color.fg_significant;
                    break;
                case PUBLIC_LEG_END_REMINDER:
                case TRANSFER_END_REMINDER:
                    textColorId = R.color.fg_less_significant;
                    break;
                case ARRIVAL_DELAY_CHANGE:
                case DEPARTURE_DELAY_CHANGE:
                case ARRIVAL_POSITION_CHANGE:
                case DEPARTURE_POSITION_CHANGE:
                case TRANSFER_CRITICAL:
                    textColorId = R.color.fg_trip_next_event_important;
                    break;
                case MESSAGE:
                case START_STOP:
                default:
                    textColorId = R.color.fg_insignificant;
                    break;
            }
            messageView.setTextColor(application.getColor(textColorId));
        }
    }
}
