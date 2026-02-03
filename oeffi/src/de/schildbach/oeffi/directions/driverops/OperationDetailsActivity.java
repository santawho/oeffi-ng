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
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.Collections;
import java.util.Date;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryStoredTripsProvider;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.navigation.TripRenderer;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.util.ViewUtils;
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
    protected boolean isShowLongStay(final Stop stop, final boolean isRowSimulated) {
        // more than 1 second stay, then show departure row
        return isRowSimulated && isLongStay(stop.plannedArrivalTime, stop.plannedDepartureTime, 1);
    }

    @Override
    protected int getStopTimeColor(final Long simulatedDelay) {
        if (simulatedDelay == null)
            return colorSimulated;
        if (simulatedDelay < -thresholdEarlyMillis)
            return colorTimeEarly;
        else if (simulatedDelay > thresholdDelayMillis)
            return colorTimeDelay;
        else
            return colorTimeGood;
    }

    @SuppressLint("SetTextI18n")
    protected void renderIntervalMinsAndSecs(
            final Long intervalMs,
            final ViewGroup viewGroup,
            final int textColor,
            final boolean showPlus,
            final int minsViewId, final int secsViewId) {
        final TextView minsView = viewGroup.findViewById(minsViewId);
        final TextView secsView = viewGroup.findViewById(secsViewId);

        if (intervalMs == null) {
            minsView.setVisibility(View.GONE);
            secsView.setVisibility(View.GONE);
            return;
        }

        final boolean isNegative;
        final long intervalSecs;
        if (intervalMs < 0) {
            isNegative = true;
            intervalSecs = (-intervalMs) / 1000;
        } else {
            isNegative = false;
            intervalSecs = intervalMs / 1000;
        }

        final long mins = intervalSecs / 60;
        final long secs = intervalSecs - mins * 60;

        minsView.setText((isNegative ? "-" : showPlus ? "+" : "") + mins);
        secsView.setText((secs < 10 ? ":0" : ":") + secs);

        minsView.setTextColor(textColor);
        secsView.setTextColor(textColor);
    }

    @Override
    protected TableRow makeStopRowArrival(
            final TripRenderer.LegContainer legC,
            final int stopIndex,
            final Stop stop,
            final Stop simulatedStop,
            final Date now) {
        final int nearestStopIndex = legC.nearestStopIndex;
        final boolean isAtNearestStop = legC.isAtNearestStop;
        final boolean sectionIsAfterNearestStop = legC.sectionIsAfterNearestStop;
        final boolean isNextAction;
        boolean isFocusView = false;

        if (nearestStopIndex < 0)
            return null;
        if (stopIndex == nearestStopIndex) {
            isNextAction = !sectionIsAfterNearestStop && !isAtNearestStop;
            isFocusView = true;
        } else if (sectionIsAfterNearestStop && stopIndex == nearestStopIndex + 1) {
            // rendering the stop after the nearest
            isNextAction = !isAtNearestStop;
        } else {
            return null;
        }

        final long nowTime = now.getTime();
        final PTDate plannedArrivalTime = simulatedStop.plannedArrivalTime;
        final PTDate predictedArrivalTime = simulatedStop.predictedArrivalTime;
        final Long plannedTime = plannedArrivalTime == null ? null : plannedArrivalTime.getTime();
        final Long predictedTime = predictedArrivalTime == null ? null : predictedArrivalTime.getTime();
        final Long arrivalDelay;
        final int color;

        final Long timeToPlan = plannedTime == null ? null : (plannedTime - nowTime);
        final Long timeToPrediction = predictedTime == null ? null : (predictedTime - nowTime);

        if (plannedTime != null && predictedTime != null) {
            arrivalDelay = predictedTime - plannedTime;
            color = getStopTimeColor(arrivalDelay);
        } else {
            arrivalDelay = null;
            color = colorSimulated;
        }

        final int backgroundColor, textColor;
        if (isNextAction) {
            backgroundColor = color;
            textColor = colorDefaultBackground;
        } else {
            backgroundColor = Color.TRANSPARENT;
            textColor = color;
        }

        final TableRow row = (TableRow) inflater.inflate(R.layout.operation_details_stop_arrival, null);
        final ViewGroup containerView = row.findViewById(R.id.operation_details_stop_container);

        renderIntervalMinsAndSecs(timeToPlan, row, textColor, false,
                R.id.operation_details_stop_time_to_plan_min,
                R.id.operation_details_stop_time_to_plan_sec);
        renderIntervalMinsAndSecs(timeToPrediction, row, textColor, false,
                R.id.operation_details_stop_time_to_prediction_min,
                R.id.operation_details_stop_time_to_prediction_sec);
        renderIntervalMinsAndSecs(arrivalDelay, row, textColor, true,
                R.id.operation_details_stop_delay_min,
                R.id.operation_details_stop_delay_sec);

        final ImageView arrowView = row.findViewById(R.id.operation_details_stop_arrow);
        arrowView.setColorFilter(textColor);

        containerView.setBackgroundColor(backgroundColor);

        if (isFocusView)
            legsScrollFocusView = row;

        return row;
    }

    @Override
    protected TableRow makeStopRowDeparture(
            final TripRenderer.LegContainer legC,
            final int stopIndex,
            final Stop stop,
            final Stop simulatedStop,
            final Date now) {
        final int nearestStopIndex = legC.nearestStopIndex;
        final boolean isAtNearestStop = legC.isAtNearestStop;
        final boolean sectionIsAfterNearestStop = legC.sectionIsAfterNearestStop;
        final boolean isNextAction;
        if (nearestStopIndex < 0)
            return null;
        if (stopIndex == nearestStopIndex) {
            isNextAction = isAtNearestStop;
        } else {
            return null;
        }

        final long nowTime = now.getTime();
        final PTDate plannedDepartureTime = simulatedStop.plannedDepartureTime;
        final PTDate predictedDepartureTime = simulatedStop.predictedDepartureTime;
        final Long plannedTime = plannedDepartureTime == null ? null : plannedDepartureTime.getTime();
        final Long predictedTime = predictedDepartureTime == null ? null : predictedDepartureTime.getTime();
        final Long departureDelay;
        final int color;

        final Long timetoPlan = plannedTime == null ? null : (plannedTime - nowTime);
        final Long timeToPrediction;

        final int backgroundColor, textColor;
        if (isNextAction) {
            timeToPrediction = null;
            if (plannedTime != null && predictedTime != null) {
                departureDelay = nowTime - plannedTime;
                color = getStopTimeColor(departureDelay);
            } else {
                departureDelay = null;
                color = colorSimulated;
            }
            backgroundColor = color;
            textColor = colorDefaultBackground;
        } else {
            // timeToPrediction = predictedTime == null ? null : (predictedTime - nowTime);
            timeToPrediction = null;
            if (plannedTime != null && predictedTime != null) {
                departureDelay = predictedTime - plannedTime;
                color = getStopTimeColor(departureDelay);
            } else {
                departureDelay = null;
                color = colorSimulated;
            }
            backgroundColor = Color.TRANSPARENT;
            textColor = color;
        }

        final TableRow row = (TableRow) inflater.inflate(R.layout.operation_details_stop_departure, null);
        final ViewGroup containerView = row.findViewById(R.id.operation_details_stop_container);

        renderIntervalMinsAndSecs(timetoPlan, row, textColor, false,
                R.id.operation_details_stop_time_to_plan_min,
                R.id.operation_details_stop_time_to_plan_sec);
        renderIntervalMinsAndSecs(timeToPrediction, row, textColor, false,
                R.id.operation_details_stop_time_to_prediction_min,
                R.id.operation_details_stop_time_to_prediction_sec);
        renderIntervalMinsAndSecs(departureDelay, row, textColor, true,
                R.id.operation_details_stop_delay_min,
                R.id.operation_details_stop_delay_sec);

        final ImageView arrowView = row.findViewById(R.id.operation_details_stop_arrow);
        arrowView.setColorFilter(textColor);

        containerView.setBackgroundColor(backgroundColor);

        return row;
    }

    @Override
    protected int getNextEventLayoutId() {
        return R.layout.navigation_next_event_operation;
    }

    @Override
    protected void updateNavigationInstructions() {
        final int colorHighlight = getColor(R.color.bg_trip_details_public_now);
        final int colorNormal = ViewUtils.getAttrColor(this, R.attr.bg_level0);

        final TripRenderer.LegContainer initialWalkLegC;
        final TripRenderer.LegContainer operationLegC;
        if (tripRenderer.legs.isEmpty()) {
            initialWalkLegC = null;
            operationLegC = null;
        } else {
            final TripRenderer.LegContainer firstLegC = tripRenderer.legs.get(0);
            if (firstLegC.publicLeg != null) {
                initialWalkLegC = null;
                operationLegC = firstLegC;
            } else {
                initialWalkLegC = firstLegC;
                if (tripRenderer.legs.size() >= 2) {
                    operationLegC = tripRenderer.legs.get(1);
                } else {
                    operationLegC = null;
                }
            }
        }

        int messageResId = 0;
        if (operationLegC == null) {
            messageResId = R.string.operation_not_an_operation;
        } else if (operationLegC.simulatedPublicLeg == null) {
            messageResId = R.string.operation_location_tracking_required;
        }

        final int nearestStopIndex = operationLegC.nearestStopIndex;
        if (nearestStopIndex < 0) {
            messageResId = R.string.operation_location_not_available;
        }

        if (messageResId != 0) {
            findViewById(R.id.navigation_next_event_container).setVisibility(View.GONE);
            findViewById(R.id.operation_next_event_message_container).setVisibility(View.VISIBLE);
            final TextView messageView = findViewById(R.id.operation_next_event_message_text);
            messageView.setText(getString(messageResId));
            return;
        }

        findViewById(R.id.operation_next_event_message_container).setVisibility(View.GONE);
        findViewById(R.id.navigation_next_event_container).setVisibility(View.VISIBLE);

        // final Trip.Public operationLeg = operationLegC.publicLeg;
        final Trip.Public simulatedLeg = operationLegC.simulatedPublicLeg;
        final boolean sectionIsAfterNearestStop = operationLegC.sectionIsAfterNearestStop;
        final boolean isAtNearestStop = operationLegC.isAtNearestStop;
        final double sectionRelation = operationLegC.sectionRelation;
        final double distanceToNearestStop = operationLegC.distanceToNearestStop;

        final Stop nearestStop;
        final Stop nextStop;
        final int numIntermediateStops = simulatedLeg.intermediateStops == null ? 0 : simulatedLeg.intermediateStops.size();
        if (nearestStopIndex == 0) {
            nearestStop = simulatedLeg.departureStop;
            if (numIntermediateStops > 0)
                nextStop = simulatedLeg.intermediateStops.get(0);
            else
                nextStop = simulatedLeg.arrivalStop;
        } else if (nearestStopIndex > numIntermediateStops) {
            nearestStop = simulatedLeg.arrivalStop;
            nextStop = null;
        } else {
            nearestStop = simulatedLeg.intermediateStops.get(nearestStopIndex - 1);
            if (nearestStopIndex < numIntermediateStops)
                nextStop = simulatedLeg.intermediateStops.get(nearestStopIndex);
            else
                nextStop = simulatedLeg.arrivalStop;
        }


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
