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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryTripsRunnable;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.navigation.NavigationAlarmManager;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Trip;

public class OperationNavigatorActivity extends OperationDetailsActivity {

    public static final String INTENT_EXTRA_DELETEREQUEST = OperationNavigatorActivity.class.getName() + ".deleterequest";
    public static final int DELETEREQUEST_NOT_REQUESTED = 0;
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
        QueryTripsRunnable.TripRequestData reloadRequestData = renderConfig.queryTripsRequestData;
        if (rc.queryTripsRequestData == null) {
            reloadRequestData = new QueryTripsRunnable.TripRequestData();
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

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mustEnableTrackButton = true;

        swipeRefreshForTripList.setOnRefreshListener(this::refreshNavigationByUserCommand);
        swipeRefreshForTripList.setEnabled(true);

        swipeRefreshForNextEvent.setOnRefreshListener(this::refreshNavigationByUserCommand);
        swipeRefreshForNextEvent.setEnabled(true);
    }

    @Override
    protected long getPeriodicUpdateIntervalMs() {
        return 15000;
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
//        if (navigationRefreshRunnable != null)
//            return;
//
//        nextNavigationRefreshTime = -1; // block auto-refresh
//        actionBar.startProgress();
//        // swipeRefreshForTripList.setRefreshing(true);
//        // swipeRefreshForNextEvent.setRefreshing(true);
//
//        navigationRefreshRunnable = () -> {
//            try {
//                Trip updatedTrip = navigator.refresh(forceRefreshAll, new Date());
//                if (updatedTrip == null) {
//                    handler.post(() -> new Toast(this).toast(R.string.toast_network_problem));
//                } else {
//                    if (refreshTripDetails)
//                        updatedTrip = loadTripDetails(updatedTrip);
//                    if (doNotificationUpdate) {
//                        isStartupComplete = true;
//                        updateNotification(updatedTrip);
//                    }
//                    final Trip finalUpdatedTrip = updatedTrip;
//                    runOnUiThread(() -> onTripUpdated(finalUpdatedTrip));
//                }
//            } catch (IOException e) {
//                handler.post(() -> new Toast(this).toast(R.string.toast_network_problem));
//            } finally {
//                navigationRefreshRunnable = null;
//                runOnUiThread(() -> {
//                    swipeRefreshForTripList.setRefreshing(false);
//                    swipeRefreshForNextEvent.setRefreshing(false);
//                    actionBar.stopProgress();
//                    nextNavigationRefreshTime = new Date().getTime()
//                            + NAVIGATION_AUTO_REFRESH_INTERVAL_SECS * 1000;
//                });
//            }
//        };
//        backgroundHandler.post(navigationRefreshRunnable);
    }
}
