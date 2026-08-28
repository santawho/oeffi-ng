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
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryStoredTripsProvider;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.TripUtils;
import de.schildbach.oeffi.util.LineView;
import de.schildbach.oeffi.tripeval.TripGeoUtils;
import de.schildbach.oeffi.tripeval.TripRenderer;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Destination;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.provider.NetworkProvider;

public class OperationDetailsActivity extends TripDetailsActivity {

    public static void startOperation(
            final Context context,
            final NetworkId network,
            final List<Trip.Public> journeyLegs,
            final Date loadedAt,
            final int intentFlags) {
        final Trip trip = TripUtils.createTripFromJourneys(loadedAt, journeyLegs);
        final RenderConfig renderConfig = new RenderConfig();
        renderConfig.isJourney = true;
        renderConfig.isOperation = true;
        final Intent intent = buildStartIntent(OperationDetailsActivity.class, context, network, trip, renderConfig);
        intent.addFlags(intentFlags);
        context.startActivity(intent);
    }

    private int colorTimeGood, colorTimeEarly, colorTimeDelay;
    private long thresholdEarlyMillis, thresholdDelayMillis;

    protected boolean isForOperations() {
        return true;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        colorTimeGood = getColor(R.color.fg_operation_time_good);
        colorTimeEarly = getColor(R.color.fg_operation_time_early);
        colorTimeDelay = getColor(R.color.fg_operation_time_delay);

        thresholdEarlyMillis = Integer.parseInt(prefs.getString("extras_drivermode_threshold_early", getString(R.string.default_drivermode_threshold_early))) * 1000L;
        thresholdDelayMillis = Integer.parseInt(prefs.getString("extras_drivermode_threshold_delay", getString(R.string.default_drivermode_threshold_delay))) * 1000L;
    }

    @Override
    protected void startNavigationForJourneyToExit(final Stop exitStop) {
        final Trip.Public journeyLeg = (Trip.Public) tripRenderer.trip.legs.get(0);
        final Trip journeyTrip = TripUtils.createTripFromJourney(
                tripRenderer.trip.loadedAt,
                journeyLeg,
                journeyLeg.entryLocation,
                exitStop.location);
        final RenderConfig navigationRenderConfig = new RenderConfig();
        navigationRenderConfig.isJourney = true;
        navigationRenderConfig.isOperation = true;
        startNavigation(journeyTrip, navigationRenderConfig);

        // QueryStoredTripsProvider.put(getContentResolver(),
        //         network, getStoredTripsUsage(),
        //         tripRenderer.trip, renderConfig.queryTripsRequestData, 0);
        QueryStoredTripsProvider.put(getContentResolver(),
                network, getStoredTripsUsage(),
                journeyTrip, renderConfig.queryTripsRequestData, 0);
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
    protected int getSimulatedStopTimeColor(final Long simulatedDelay) {
        if (simulatedDelay == null)
            return colorSimulated;
        if (simulatedDelay < -thresholdEarlyMillis)
            return colorTimeEarly;
        else if (simulatedDelay > thresholdDelayMillis)
            return colorTimeDelay;
        else
            return colorTimeGood;
    }

    protected boolean isShowSeconds() {
        return false;
    }

    protected boolean isShowRemaining() {
        return false;
    }

    @SuppressLint("SetTextI18n")
    protected void renderIntervalMinsAndSecs(
            final Long intervalMs,
            final ViewGroup viewGroup,
            final int textColor,
            final boolean showPlus,
            final boolean isNowBased,
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

        final int style = isNowBased ? Typeface.ITALIC : Typeface.NORMAL;

        final long mins = intervalSecs / 60;
        minsView.setText((isNegative ? "-" : showPlus ? "+" : "") + mins);
        minsView.setTypeface(null, style);
        minsView.setTextColor(textColor);

        if (isShowSeconds()) {
            secsView.setVisibility(View.VISIBLE);
            final long secs = intervalSecs - mins * 60;
            secsView.setText((secs < 10 ? ":0" : ":") + secs);
            secsView.setTypeface(null, style);
            secsView.setTextColor(textColor);
        } else {
            secsView.setVisibility(View.GONE);
        }
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

        if (nearestStopIndex < 0)
            return null;
        if (stopIndex == nearestStopIndex) {
            isNextAction = !sectionIsAfterNearestStop && !isAtNearestStop;
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

        final Long timeToPlan = plannedTime == null ? null : (nowTime - plannedTime);
        final Long timeToPrediction = predictedTime == null ? null : (predictedTime - nowTime);

        if (plannedTime != null && predictedTime != null) {
            arrivalDelay = predictedTime - plannedTime;
            color = getSimulatedStopTimeColor(arrivalDelay);
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

        renderIntervalMinsAndSecs(timeToPlan, row, textColor, false, true,
                R.id.operation_details_stop_time_to_plan_min,
                R.id.operation_details_stop_time_to_plan_sec);
        renderIntervalMinsAndSecs(timeToPrediction, row, textColor, false, true,
                R.id.operation_details_stop_time_to_prediction_min,
                R.id.operation_details_stop_time_to_prediction_sec);
        renderIntervalMinsAndSecs(arrivalDelay, row, textColor, true, false,
                R.id.operation_details_stop_delay_min,
                R.id.operation_details_stop_delay_sec);

        final ImageView arrowView = row.findViewById(R.id.operation_details_stop_arrow);
        arrowView.setColorFilter(textColor);

        if (isNextAction) {
            final TextView locationView = row.findViewById(R.id.operation_details_stop_location);
            locationView.setVisibility(View.VISIBLE);
            locationView.setText(Formats.makeBreakableStationName(stop.location.name));
            locationView.setTextColor(textColor);
        }

        containerView.setBackgroundColor(backgroundColor);

        if (legsScrollFocusView == null)
            legsScrollFocusView = row;

        row.setOnClickListener(view -> setShowPage(R.id.navigation_next_event));
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
        final boolean departureDelayIsNowBased;
        final int color;

        final Long timetoPlan = plannedTime == null ? null : (nowTime - plannedTime);
        final Long timeToPrediction;

        final int backgroundColor, textColor;
        if (isNextAction) {
            timeToPrediction = null;
            if (plannedTime != null && predictedTime != null) {
                departureDelay = nowTime - plannedTime;
                departureDelayIsNowBased = true;
                color = getSimulatedStopTimeColor(departureDelay);
            } else {
                departureDelay = null;
                departureDelayIsNowBased = false;
                color = colorSimulated;
            }
            backgroundColor = color;
            textColor = colorDefaultBackground;
        } else {
            // timeToPrediction = predictedTime == null ? null : (predictedTime - nowTime);
            timeToPrediction = null;
            if (plannedTime != null && predictedTime != null) {
                departureDelay = predictedTime - plannedTime;
                departureDelayIsNowBased = false;
                color = getSimulatedStopTimeColor(departureDelay);
            } else {
                departureDelay = null;
                departureDelayIsNowBased = false;
                color = colorSimulated;
            }
            backgroundColor = Color.TRANSPARENT;
            textColor = color;
        }

        final TableRow row = (TableRow) inflater.inflate(R.layout.operation_details_stop_departure, null);
        final ViewGroup containerView = row.findViewById(R.id.operation_details_stop_container);

        renderIntervalMinsAndSecs(timetoPlan, row, textColor, false, true,
                R.id.operation_details_stop_time_to_plan_min,
                R.id.operation_details_stop_time_to_plan_sec);
        renderIntervalMinsAndSecs(timeToPrediction, row, textColor, false, true,
                R.id.operation_details_stop_time_to_prediction_min,
                R.id.operation_details_stop_time_to_prediction_sec);
        renderIntervalMinsAndSecs(departureDelay, row, textColor, true, departureDelayIsNowBased,
                R.id.operation_details_stop_delay_min,
                R.id.operation_details_stop_delay_sec);

        ((ImageView) row.findViewById(R.id.operation_details_stop_arrow)).setColorFilter(textColor);

        if (isNextAction) {
            final TextView locationView = row.findViewById(R.id.operation_details_stop_location);
            locationView.setVisibility(View.VISIBLE);
            locationView.setText(Formats.makeBreakableStationName(stop.location.name));
            locationView.setTextColor(textColor);
        }

        containerView.setBackgroundColor(backgroundColor);

        if (legsScrollFocusView == null)
            legsScrollFocusView = row;

        row.setOnClickListener(view -> setShowPage(R.id.navigation_next_event));
        return row;
    }

    @Override
    protected int getNextEventLayoutId() {
        return R.layout.operation_next_event;
    }

    protected void processNearestStop(
            final Stop nearestStop,
            final boolean isEndOfJourney,
            final long timeLeftToStopMillis,
            final long timePassedSincePreviousStopMillis,
            final LineDestination lineDestination,
            final Location prevStop) {
        // nothing to do here, see sub-classes
    }

    @Override
    protected void updateNavigationInstructions() {
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

        final ViewGroup containerView = findViewById(R.id.navigation_next_event_container);

        if (messageResId != 0) {
            containerView.setVisibility(View.GONE);
            findViewById(R.id.operation_next_event_message_container).setVisibility(View.VISIBLE);
            final TextView messageView = findViewById(R.id.operation_next_event_message_text);
            messageView.setText(getString(messageResId));
            return;
        }

        findViewById(R.id.operation_next_event_message_container).setVisibility(View.GONE);
        containerView.setVisibility(View.VISIBLE);

        // final Trip.Public operationLeg = operationLegC.publicLeg;
        final Trip.Public simulatedLeg = operationLegC.simulatedPublicLeg;
        final Line line = simulatedLeg.line;
        final LineDestination lineDestination = new LineDestination(line, simulatedLeg.destination);

        final LineView lineView = findViewById(R.id.operation_next_event_line);
        lineView.setLine(line);
        final TextView destinationView = findViewById(R.id.operation_next_event_destination);
        final Destination destination = simulatedLeg.destination;
        destinationView.setText(destination == null ? null : Constants.DESTINATION_ARROW_PREFIX
                + Formats.makeBreakableStationName(Formats.fullLocationName(destination.location)));

        final boolean sectionIsAfterNearestStop = operationLegC.sectionIsAfterNearestStop;
        final boolean isAtNearestStop = operationLegC.isAtNearestStop;
        final double sectionRelation = operationLegC.sectionRelation;
        final double distanceToNearestStop = operationLegC.distanceToNearestStop;

        final Stop nearestStop = TripRenderer.LegContainer.getPublicStopByIndex(simulatedLeg, nearestStopIndex);
        final int nextStopIndex = nearestStopIndex + 1;
        final Stop nextStop = TripRenderer.LegContainer.getPublicStopByIndex(simulatedLeg, nextStopIndex);
        final Stop prevStop = TripRenderer.LegContainer.getPublicStopByIndex(simulatedLeg, nearestStopIndex - 1);

        if (nearestStop == null)
            return;

        final long now = System.currentTimeMillis();
        final boolean isEndOfJourney = nextStop == null;

        PTDate tPlan, tPred;
        final Long nearestArrivalDelay;
        final boolean nearestArrivalDelayIsNowBased;
        final Long nearestDepartureDelay;
        final boolean nearestDepartureDelayIsNowBased;
        if (isAtNearestStop) {
            // waiting for departure
            tPlan = nearestStop.plannedArrivalTime;
            nearestArrivalDelay = tPlan == null ? null : now - tPlan.getTime();
            nearestArrivalDelayIsNowBased = true;
            tPlan = nearestStop.plannedDepartureTime;
            nearestDepartureDelay = tPlan == null ? null : now - tPlan.getTime();
            nearestDepartureDelayIsNowBased = true;
            processNearestStop(
                    nearestStop,
                    isEndOfJourney,
                    0,
                    -1,
                    lineDestination,
                    nearestStop.location);
        } else if (sectionIsAfterNearestStop) {
            // leaving nearest stop
            nearestArrivalDelay = null;
            nearestArrivalDelayIsNowBased = false;
            tPlan = nearestStop.plannedDepartureTime;
            final long timePassedSincePreviousStopMillis;
            if (isEndOfJourney
                    || nextStop.predictedArrivalTime == null
                    || nextStop.plannedArrivalTime == null
                    || nearestStop.plannedDepartureTime == null) {
                timePassedSincePreviousStopMillis = -1;
            } else {
                final long plannedDuration = nextStop.plannedArrivalTime.getTime() - nearestStop.plannedDepartureTime.getTime();
                final long predictedRemaining = nextStop.predictedArrivalTime.getTime() - now;
                timePassedSincePreviousStopMillis = plannedDuration - predictedRemaining;
            }
            nearestDepartureDelay = tPlan == null ? null : now - tPlan.getTime();
            nearestDepartureDelayIsNowBased = true;
            processNearestStop(
                    nearestStop,
                    isEndOfJourney,
                    -1,
                    timePassedSincePreviousStopMillis,
                    lineDestination,
                    nearestStop.location);
        } else {
            // approaching nearest stop
            tPlan = nearestStop.plannedArrivalTime;
            tPred = nearestStop.predictedArrivalTime;
            nearestArrivalDelay = tPlan == null ? null : tPred == null ? 0 : tPred.getTime() - tPlan.getTime();
            nearestArrivalDelayIsNowBased = false;
            processNearestStop(
                    nearestStop,
                    isEndOfJourney,
                    tPred == null ? 1 : tPred.getTime() - now,
                    Long.MAX_VALUE,
                    lineDestination,
                    prevStop == null ? null : prevStop.location);

            tPlan = nearestStop.plannedDepartureTime;
            tPred = nearestStop.predictedDepartureTime;
            nearestDepartureDelay = tPlan == null ? null : tPred == null ? 0 : tPred.getTime() - tPlan.getTime();
            nearestDepartureDelayIsNowBased = false;
        }
        final Long nextArrivalDelay;
        final boolean nextArrivalDelayIsNowBased;
        if (isEndOfJourney) {
            nextArrivalDelay = null;
            nextArrivalDelayIsNowBased = false;
        } else {
            tPlan = nextStop.plannedArrivalTime;
            tPred = nextStop.predictedArrivalTime;
            nextArrivalDelay = tPlan == null ? null : tPred == null ? 0 : tPred.getTime() - tPlan.getTime();
            nextArrivalDelayIsNowBased = false;
        }

        final TextView nearestStopNameView = containerView.findViewById(R.id.operation_next_event_nearest_station_name);
        final TextView nearestStopPlaceView = containerView.findViewById(R.id.operation_next_event_nearest_station_place);
        nearestStopNameView.setText(Formats.makeBreakableStationName(nearestStop.location.name));
        nearestStopNameView.setBackgroundColor(Color.TRANSPARENT);
        nearestStopNameView.setTextColor(colorSignificant);
        nearestStopPlaceView.setText(Formats.makeBreakableStationName(nearestStop.location.place));
        nearestStopPlaceView.setBackgroundColor(Color.TRANSPARENT);
        nearestStopPlaceView.setTextColor(colorSignificant);

        boolean isNextAction;
        int color, delayTextColor, otherTextColor;

        final ImageView nearestArrivalArrowView = containerView.findViewById(R.id.operation_next_event_nearest_station_arrow);
        nearestArrivalArrowView.setBackgroundColor(Color.TRANSPARENT);

        final View nearestArrivalView = containerView.findViewById(R.id.operation_next_event_nearest_station_arrival);
        if (nearestArrivalDelay == null) {
            nearestArrivalView.setVisibility(View.INVISIBLE);
            nearestArrivalArrowView.setVisibility(View.INVISIBLE);
        } else {
            color = getSimulatedStopTimeColor(nearestArrivalDelay);
            isNextAction = isAtNearestStop ? nearestDepartureDelay == null : !sectionIsAfterNearestStop;
            nearestArrivalView.setVisibility(View.VISIBLE);
            nearestArrivalArrowView.setVisibility(View.VISIBLE);
            delayTextColor = isNextAction ? colorDefaultBackground : color;
            otherTextColor = isNextAction ? colorDefaultBackground : colorSignificant;
            renderIntervalMinsAndSecs(nearestArrivalDelay, containerView, delayTextColor,
                    true, nearestArrivalDelayIsNowBased,
                    R.id.operation_next_event_nearest_station_arrival_delay_min,
                    R.id.operation_next_event_nearest_station_arrival_delay_sec);
            final TextView planTimeView = containerView.findViewById(R.id.operation_next_event_nearest_station_arrival_plan_time);
            setPlanTime(planTimeView, false, nearestStop.plannedArrivalTime, otherTextColor);
            setRemaining(
                    containerView.findViewById(R.id.operation_next_event_nearest_station_arrival_remaining),
                    nearestStop.getArrivalTime().getTime() - now,
                    containerView.findViewById(R.id.operation_next_event_nearest_station_arrival_distance),
                    nearestStop.location.coord, operationLegC, nearestStopIndex,
                    otherTextColor);

            if (isNextAction) {
                nearestArrivalView.setBackgroundColor(color);
                nearestStopNameView.setBackgroundColor(color);
                nearestStopNameView.setTextColor(colorDefaultBackground);
                nearestStopPlaceView.setBackgroundColor(color);
                nearestStopPlaceView.setTextColor(colorDefaultBackground);

                nearestArrivalArrowView.setBackgroundColor(color);
                nearestArrivalArrowView.setColorFilter(colorDefaultBackground);
            } else {
                nearestArrivalView.setBackgroundColor(Color.TRANSPARENT);

                nearestArrivalArrowView.setColorFilter(color);
            }
        }

        final ImageView nextArrivalArrowView = containerView.findViewById(R.id.operation_next_event_next_station_arrow);
        boolean nextArrivalArrowViewAlreadyRendered = false;

        final View nearestDepartureView = containerView.findViewById(R.id.operation_next_event_nearest_station_departure);
        if (nearestDepartureDelay == null) {
            nearestDepartureView.setVisibility(View.INVISIBLE);
        } else {
            color = getSimulatedStopTimeColor(nearestDepartureDelay);
            isNextAction = isAtNearestStop;
            nearestDepartureView.setVisibility(View.VISIBLE);
            delayTextColor = isNextAction ? colorDefaultBackground : color;
            otherTextColor = isNextAction ? colorDefaultBackground : colorSignificant;
            renderIntervalMinsAndSecs(nearestDepartureDelay, containerView, delayTextColor,
                    true, nearestDepartureDelayIsNowBased,
                    R.id.operation_next_event_nearest_station_departure_delay_min,
                    R.id.operation_next_event_nearest_station_departure_delay_sec);
            final TextView planTimeView = containerView.findViewById(R.id.operation_next_event_nearest_station_departure_plan_time);
            setPlanTime(planTimeView, true, nearestStop.plannedDepartureTime, otherTextColor);
            setRemaining(
                    containerView.findViewById(R.id.operation_next_event_nearest_station_departure_remaining),
                    nearestStop.getDepartureTime().getTime() - now,
                    null, null, null, 0,
                    otherTextColor);

            if (isNextAction) {
                nearestDepartureView.setBackgroundColor(color);
                nearestStopNameView.setBackgroundColor(color);
                nearestStopNameView.setTextColor(colorDefaultBackground);
                nearestStopPlaceView.setBackgroundColor(color);
                nearestStopPlaceView.setTextColor(colorDefaultBackground);

                nextArrivalArrowView.setColorFilter(colorDefaultBackground);
                nextArrivalArrowView.setBackgroundColor(color);
                nextArrivalArrowViewAlreadyRendered = true;
            } else {
                nearestDepartureView.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        final View nextArrivalView = containerView.findViewById(R.id.operation_next_event_next_station_arrival);
        if (nextArrivalDelay == null) {
            nextArrivalView.setVisibility(View.INVISIBLE);
            nextArrivalArrowView.setVisibility(View.INVISIBLE);
        } else {
            color = getSimulatedStopTimeColor(nextArrivalDelay);
            isNextAction = !isAtNearestStop && sectionIsAfterNearestStop;
            nextArrivalView.setVisibility(View.VISIBLE);
            nextArrivalArrowView.setVisibility(View.VISIBLE);
            delayTextColor = isNextAction ? colorDefaultBackground : color;
            otherTextColor = isNextAction ? colorDefaultBackground : colorSignificant;
            renderIntervalMinsAndSecs(nextArrivalDelay, containerView, delayTextColor,
                    true, nextArrivalDelayIsNowBased,
                    R.id.operation_next_event_next_station_arrival_delay_min,
                    R.id.operation_next_event_next_station_arrival_delay_sec);
            final TextView planTimeView = containerView.findViewById(R.id.operation_next_event_next_station_arrival_plan_time);
            setPlanTime(planTimeView, false, nextStop.plannedArrivalTime, otherTextColor);
            setRemaining(
                    containerView.findViewById(R.id.operation_next_event_next_station_arrival_remaining),
                    nextStop.getArrivalTime().getTime() - now,
                    containerView.findViewById(R.id.operation_next_event_next_station_arrival_distance),
                    nextStop.location.coord, operationLegC, nextStopIndex,
                    otherTextColor);

            final TextView nextStopNameView = containerView.findViewById(R.id.operation_next_event_next_station_name);
            final TextView nextStopPlaceView = containerView.findViewById(R.id.operation_next_event_next_station_place);
            nextStopNameView.setText(Formats.makeBreakableStationName(nextStop.location.name));
            nextStopPlaceView.setText(Formats.makeBreakableStationName(nextStop.location.place));

            if (isNextAction) {
                nextArrivalView.setBackgroundColor(color);
                nextStopNameView.setTextColor(colorDefaultBackground);
                nextStopPlaceView.setTextColor(colorDefaultBackground);

                nextArrivalArrowView.setColorFilter(colorDefaultBackground);
                nextArrivalArrowView.setBackgroundColor(color);
            } else {
                nextArrivalView.setBackgroundColor(Color.TRANSPARENT);
                nextStopNameView.setTextColor(colorSignificant);
                nextStopPlaceView.setTextColor(colorSignificant);

                if (!nextArrivalArrowViewAlreadyRendered) {
                    nextArrivalArrowView.setColorFilter(color);
                    nextArrivalArrowView.setBackgroundColor(Color.TRANSPARENT);
                }
            }
        }
    }

    protected void setPlanTime(
            final TextView planTimeView,
            final boolean isDeparture,
            final PTDate date,
            final int textColor) {
        planTimeView.setText(getString(
                isDeparture
                    ? R.string.operation_next_event_departure_time_format
                    : R.string.operation_next_event_arrival_time_format,
                Formats.formatTime(timeZoneSelector, date)));
        planTimeView.setTextColor(textColor);
    }

    protected void setRemaining(
            final TextView remainingView,
            final long timeSpan,
            final TextView distanceView,
            final Point locationCoord,
            final TripRenderer.LegContainer legC, final int stopIndex,
            final int textColor) {
        remainingView.setText(isShowRemaining() ? Formats.formatTimeSpanMS(timeSpan, false) : null);
        remainingView.setTextColor(textColor);

        if (distanceView != null) {
            final Point deviceCoord = getDeviceLocation();
            final Double deviceBearing = getDeviceBearing();
            final String distanceText;
            if (isShowRemaining() && locationCoord != null && deviceCoord != null) {
                final double distanceOnPath = legC == null ? 999_000d : legC.geoDistanceOnPathInMeters(deviceCoord, deviceBearing, stopIndex);
                final double directDistance = TripGeoUtils.geoDistanceInMeters(deviceCoord, locationCoord);
                if (Math.abs(distanceOnPath) < Math.abs(directDistance)) {
                    log.warn("unexpected distance order {} < {} at {}/{}",
                            distanceOnPath, directDistance, deviceCoord, deviceBearing);
                }
                distanceText =
                        legC == null ? "" : (Formats.formatDistance(distanceOnPath, false) + " / ")
                        + Formats.formatDistance(directDistance, false);
            } else {
                distanceText = null;
            }
            distanceView.setText(distanceText);
            distanceView.setTextColor(textColor);
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
