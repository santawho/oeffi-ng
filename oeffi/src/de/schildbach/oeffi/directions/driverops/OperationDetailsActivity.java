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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import java.util.Collections;
import java.util.Date;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryStoredTripsProvider;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.navigation.TripRenderer;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.provider.NetworkProvider;

public class OperationDetailsActivity extends TripDetailsActivity {

    public static void start(
            final Context context,
            final NetworkId network,
            final Trip.Public journeyLeg,
            final Date loadedAt,
            final int intentFlags) {
        start(OperationDetailsActivity.class,
                context, network, journeyLeg, true, loadedAt, intentFlags);
    }

    private int colorTimeGood, colorTimeEarly, colorTimeDelay;
    private long thresholdEarlyMillis, thresholdDelayMillis;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        colorTimeGood = getColor(R.color.fg_time_good);
        colorTimeEarly = getColor(R.color.fg_time_early);
        colorTimeDelay = getColor(R.color.fg_time_delay);

        thresholdEarlyMillis = Integer.parseInt(prefs.getString("extras_drivermode_threshold_early", getString(R.string.default_drivermode_threshold_early))) * 1000L;
        thresholdDelayMillis = Integer.parseInt(prefs.getString("extras_drivermode_threshold_delay", getString(R.string.default_drivermode_threshold_delay))) * 1000L;
    }

    @Override
    protected void startNavigationForJourneyToExit(final Stop exitStop) {
        final Trip.Public journeyLeg = (Trip.Public) tripRenderer.trip.legs.get(0);
        final Location entryLocation = journeyLeg.entryLocation;
        final Location exitLocation = exitStop.location;
        final Trip journeyTrip = new Trip(
                tripRenderer.trip.loadedAt,
                null,
                null,
                entryLocation,
                exitLocation,
                Collections.singletonList(journeyLeg),
                null,
                null,
                0);
        final RenderConfig navigationRenderConfig = new RenderConfig();
        navigationRenderConfig.isJourney = true;
        navigationRenderConfig.isOperation = true;
        startNavigation(journeyTrip, navigationRenderConfig);
    }

    @Override
    protected void startNavigation(final Trip trip, final RenderConfig renderConfig) {
        if (OperationNavigatorActivity.startNavigation(this, network, trip, renderConfig, isTaskRoot())) {
            setResult(RESULT_OK, new Intent());
            finish();
        }
    }

    @Override
    protected View.OnClickListener getStartNavigationClickListener() {
        if (renderConfig.isOperation
                && NetworkProviderFactory.provider(network).hasCapabilities(NetworkProvider.Capability.JOURNEY)) {
            final Trip.Public journeyLeg = (Trip.Public) tripRenderer.trip.legs.get(0);
            final Location exitLocation = journeyLeg.exitLocation;
            if (exitLocation != null) {
                if (exitLocation.equals(journeyLeg.arrivalStop.location)) {
                    return v -> startNavigationForJourneyToExit(journeyLeg.arrivalStop);
                } else if (journeyLeg.intermediateStops != null) {
                    for (final Stop stop : journeyLeg.intermediateStops) {
                        if (exitLocation.equals(stop.location)) {
                            return v -> startNavigationForJourneyToExit(stop);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected int getContentLayoutId() {
        return R.layout.navigation_next_event_operation;
    }

    @Override
    protected int getActionBarColorId() {
        return R.color.bg_action_bar_operation;
    }

    @Override
    protected void addActionBarButtons() {
        addBookmarkActionBarButton();
    }

    @Override
    protected int getActionBarTitleStringId() {
        return R.string.operation_details_title;
    }

    @Override
    protected String getStoredTripsUsage() {
        return QueryStoredTripsProvider.USAGE_OPERATION;
    }

    @Override
    protected boolean isShowCompactTimes() {
        return false;
    }

    @Override
    protected long getLongStayMinMillis() {
        return 1; // any stop that does not depart at the same time as arrival
    }

    @Override
    protected int getStopTimeColor(final long simulatedDelay) {
        if (simulatedDelay < -thresholdEarlyMillis)
            return colorTimeEarly;
        else if (simulatedDelay > thresholdDelayMillis)
            return colorTimeDelay;
        else
            return colorTimeGood;
    }

    @Override
    protected void updateNavigationInstructions() {
        // TODO
    }

    protected TripDetailsActivity.StopClickListener newStopClickListener(
            final TripRenderer.LegContainer legC,
            final Stop stop,
            final boolean stopIsLegDeparture,
            final boolean stopIsLegArrival,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef) {
        return new OperationDetailsActivity.StopClickListener(
                legC,
                stop,
                stopIsLegDeparture,
                stopIsLegArrival,
                currentJourneyRef,
                feederJourneyRef,
                connectionJourneyRef);
    }

    protected class StopClickListener extends TripDetailsActivity.StopClickListener {
        public StopClickListener(
                final TripRenderer.LegContainer legC,
                final Stop stop,
                final boolean stopIsLegDeparture,
                final boolean stopIsLegArrival,
                final JourneyRef currentJourneyRef,
                final JourneyRef feederJourneyRef,
                final JourneyRef connectionJourneyRef) {
            super(legC, stop, stopIsLegDeparture, stopIsLegArrival,
                    currentJourneyRef, feederJourneyRef, connectionJourneyRef);
        }

        @Override
        public boolean onClick(final View v, final boolean isLongClick) {
            if (isLongClick) {
                PTDate time = stop.getArrivalTime();
                if (time == null)
                    time = stop.getDepartureTime(true);
                StationDetailsActivity.start(OperationDetailsActivity.this,
                        network, stop.location, time, null,
                        shallShowChildActivitiesInNewTask());
                return true;
            }

            return super.onClick(v, isLongClick);
        }
    }
}
