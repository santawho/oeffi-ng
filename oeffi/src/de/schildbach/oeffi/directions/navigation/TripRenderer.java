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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;

public class TripRenderer {
    public static final String NO_TIME_LEFT_VALUE = "@@@";
    public static final int TRANSFER_CRITICAL_MINUTES = 3;

    public static class LegContainer {
        public int legIndex;
        public @Nullable Trip.Individual individualLeg;
        public @Nullable Trip.Public publicLeg;
        public final @Nullable Trip.Public initialLeg;
        public final LegContainer transferFrom;
        public final LegContainer transferTo;
        public final boolean transferCritical;

        public LegContainer(
                final int legIndex,
                final @Nullable Trip.Public baseLeg) {
            this.legIndex = legIndex;
            this.publicLeg = baseLeg;
            this.initialLeg = baseLeg;
            this.individualLeg = null;
            this.transferFrom = null;
            this.transferTo = null;
            this.transferCritical = false;
        }

        public LegContainer(
                final int legIndex,
                final @Nullable Trip.Individual baseLeg,
                final LegContainer transferFrom, final LegContainer transferTo,
                final boolean transferCritical) {
            this.legIndex = legIndex;
            this.individualLeg = baseLeg;
            this.transferFrom = transferFrom;
            this.transferTo = transferTo;
            this.transferCritical = transferCritical;
            this.publicLeg = null;
            this.initialLeg = null;
        }

        public boolean isTransfer() {
            return initialLeg == null;
        }

        public void setCurrentLegState(Trip.Public updatedLeg) {
            if (initialLeg != null)
                publicLeg = updatedLeg;
        }
    }

    public static class LegKey {
        private String key;
        public LegKey(Trip.Leg leg) {
            key = leg.departure.id + "/" + leg.arrival.id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LegKey)) return false;
            LegKey legKey = (LegKey) other;
            return Objects.equals(key, legKey.key);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key);
        }
    };

    public static class NotificationData implements Serializable {
        private static final long serialVersionUID = -699098832883209694L;

        private static int idc;
        public final int id;

        public NotificationData() {
            this.id = ++idc;
        }

        public long refreshNotificationRequiredAt;
        public long refreshTripRequiredAt;
        public int currentLegIndex;
        public boolean isArrival;
        public Date eventTime;
        public int publicArrivalLegIndex;
        public int publicDepartureLegIndex;
        public Date plannedEventTime;
        public Position departurePosition;
        public Position plannedDeparturePosition;
        public long leftTimeReminded;
        public boolean nextTransferCritical;
        public String transfersCritical;
    }

    public final Trip trip;
    private final boolean isJourney;

    public List<LegContainer> legs = new ArrayList<>();
    public LegContainer currentLeg;
    public final Map<LegKey, Boolean> legExpandStates;
    public NotificationData notificationData;
    private Boolean feasible;

    public TripRenderer(final TripRenderer previous, final Trip trip, final boolean isJourney, final Date now) {
        this.trip = trip;
        this.isJourney = isJourney;
        this.legExpandStates = previous != null ? previous.legExpandStates : new HashMap<>();
        setupFromTrip(trip);
        evaluateByTime(now);
    }

    public boolean isFeasible() {
        if (feasible == null) {
            feasible = trip.isTravelable();
        }
        return feasible;
    }

    private static boolean isTransferCritical(
            final Trip.Individual individualLeg,
            final LegContainer transferFrom,
            final LegContainer transferTo) {
        if (transferFrom == null || transferTo == null)
            return false;
        final Stop arrivalStop = transferFrom.publicLeg.arrivalStop;
        final Stop departureStop = transferTo.publicLeg.departureStop;
        final Date arrTime = arrivalStop.getArrivalTime();
        final Date depTime = departureStop.getDepartureTime();
        final int leftMins = (int) ((depTime.getTime() - arrTime.getTime()) / 60000 - 1);
        final int walkMins = individualLeg != null ? individualLeg.min : 0;
        return leftMins - walkMins < TRANSFER_CRITICAL_MINUTES;
    }

    private void setupFromTrip(final Trip trip) {
        LegContainer prevC = null;
        for (int iLeg = 0; iLeg < trip.legs.size(); ++iLeg) {
            final Trip.Leg prevLeg = (iLeg > 0) ? trip.legs.get(iLeg - 1) : null;
            Trip.Leg leg = trip.legs.get(iLeg);
            final int iNext = iLeg + 1;
            final Trip.Leg nextLeg = (iNext < trip.legs.size()) ? trip.legs.get(iNext) : null;

            if (leg instanceof Trip.Individual) {
                final Trip.Individual individualLeg = (Trip.Individual) leg;
                final LegContainer transferFrom = (prevLeg instanceof Trip.Public) ? prevC : null;
                final LegContainer transferTo = (nextLeg instanceof Trip.Public) ? new LegContainer(iNext, (Trip.Public) nextLeg) : null;
                legs.add(new LegContainer(iLeg, individualLeg, transferFrom, transferTo, isTransferCritical(individualLeg, transferFrom, transferTo)));
                if (transferTo != null) {
                    setupPath(nextLeg);
                    legs.add(transferTo);
                    ++iLeg;

                    if (isJourney) {
                        legExpandStates.put(new LegKey(nextLeg), true);
                    }
                }
                prevC = transferTo;
            } else if (leg instanceof Trip.Public) {
                final Trip.Public publicLeg = (Trip.Public) leg;
                final LegContainer newC = new LegContainer(iLeg, publicLeg);
                if (prevC != null || iLeg == 0) {
                    legs.add(new LegContainer(-1, null, prevC, newC, isTransferCritical(null, prevC, newC)));
                }
                legs.add(newC);
                prevC = newC;

                if (isJourney) {
                    legExpandStates.put(new LegKey(leg), true);
                }
            }

            setupPath(leg);
        }
    }

    public void evaluateByTime(final Date now) {
        notificationData = new NotificationData();
        setNextEventClock(now);
        for (int iLeg = 0; iLeg < legs.size(); ++iLeg) {
            final TripRenderer.LegContainer legC = legs.get(iLeg);
            final boolean isCurrent;
            if (legC.publicLeg != null) {
                final int iWalk = iLeg + 1;
                TripRenderer.LegContainer walkLegC = (iWalk < legs.size()) ? legs.get(iWalk) : null;
                final int iNext = iLeg + 2;
                TripRenderer.LegContainer nextLegC = (iNext < legs.size()) ? legs.get(iNext) : null;
                isCurrent = updatePublicLeg(legC, walkLegC, nextLegC, now);
            } else {
                isCurrent = updateIndividualLeg(legC, now);
            }
            if (isCurrent) {
                currentLeg = legC;
                notificationData.currentLegIndex = iLeg;
            }
        }
        final char[] legsCriticality = new char[legs.size()];
        for (int iLeg = 0; iLeg < legs.size(); ++iLeg) {
            final TripRenderer.LegContainer legC = legs.get(iLeg);
            legsCriticality[iLeg] = legC.transferCritical ? '*' : '-';
        }
        notificationData.transfersCritical = new String(legsCriticality);
    }

    private boolean updatePublicLeg(
            final TripRenderer.LegContainer legC,
            final TripRenderer.LegContainer walkLegC,
            final TripRenderer.LegContainer nextLegC,
            final Date now) {
        Trip.Public leg = legC.publicLeg;
        final Stop departureStop = leg.departureStop;
        final Stop arrivalStop = leg.arrivalStop;
        Date beginTime = departureStop.getDepartureTime();
        Date endTime = arrivalStop.getArrivalTime();
        Date plannedEndTime = arrivalStop.plannedArrivalTime;
        if (now.before(beginTime)) {
            // leg is in the future
        } else if (now.after(endTime)) {
            // leg is in the past
        } else {
            // leg is now
            setNextEventType(true);
            final boolean eventIsNow = setNextEventTimeLeft(now, endTime, plannedEndTime, 0);
            String targetName = Formats.fullLocationName(arrivalStop.location);
            setNextEventTarget(targetName);
            final Trip.Public nextPublicLeg = (nextLegC != null) ? nextLegC.publicLeg : null;
            final Stop nextDepartureStop = (nextPublicLeg != null) ? nextPublicLeg.departureStop : null;
            String depName = (nextDepartureStop != null) ? Formats.fullLocationName(nextDepartureStop.location) : null;
            boolean depChanged = depName != null && !depName.equals(targetName);
            setNextEventDeparture(depChanged ? depName : null);
            final Position arrPos = arrivalStop.getArrivalPosition();
            final Position depPos = (nextPublicLeg != null) ? nextPublicLeg.getDeparturePosition() : null;
            final Position plannedDepPos = (nextPublicLeg != null) ? nextDepartureStop.plannedDeparturePosition : null;
            setNextEventPositions(
                    arrivalStop, arrPos, arrPos != null && !arrPos.equals(arrivalStop.plannedArrivalPosition),
                    nextDepartureStop, depPos, depPos != null && !depPos.equals(plannedDepPos));
            setNextEventTransport(nextPublicLeg);
            setNextEventTransferTimes(walkLegC, false, now);
            setNextEventActions(
                    nextLegC != null
                            ? (eventIsNow
                                ? R.string.directions_trip_details_next_event_action_ride_now
                                : R.string.directions_trip_details_next_event_action_ride)
                            : (eventIsNow
                                ? R.string.directions_trip_details_next_event_action_arrival_now
                                : R.string.directions_trip_details_next_event_action_arrival),
                        walkLegC == null ? 0
                            : nextLegC == null ? R.string.directions_trip_details_next_event_action_next_final_transfer
                            : depChanged ? R.string.directions_trip_details_next_event_action_next_transfer
                            : R.string.directions_trip_details_next_event_action_next_interchange
            );

            notificationData.publicArrivalLegIndex = legC.legIndex;
            notificationData.publicDepartureLegIndex = nextPublicLeg != null ? nextLegC.legIndex: -1;
            notificationData.isArrival = true;
            notificationData.eventTime = endTime;
            notificationData.plannedEventTime = plannedEndTime;
            notificationData.departurePosition = depPos;
            notificationData.plannedDeparturePosition = plannedDepPos;
            notificationData.nextTransferCritical = nextEventTransferLeftTimeCritical;
            return true;
        }
        return false;
    }

    private boolean updateIndividualLeg(
            final TripRenderer.LegContainer legC,
            final Date now) {
        final Trip.Individual leg = legC.individualLeg;
        final Stop transferFrom = legC.transferFrom != null ? legC.transferFrom.publicLeg.arrivalStop : null;
        final Stop transferTo = legC.transferTo != null ? legC.transferTo.publicLeg.departureStop : null;
        Date beginTime = transferFrom != null ? transferFrom.getArrivalTime() : leg == null ? null : leg.departureTime;
        Date endTime = transferTo != null ? transferTo.getDepartureTime() : leg == null ? null : leg.arrivalTime;
        Date plannedEndTime = transferTo != null ? transferTo.plannedDepartureTime : leg == null ? null : leg.arrivalTime;
        if (transferFrom != null && beginTime != null && now.before(beginTime)) {
            // leg is in the future
        } else if (endTime != null && now.after(endTime)) {
            // leg is in the past
        } else {
            // leg is now
            setNextEventType(false);
            final boolean eventIsNow = setNextEventTimeLeft(now, endTime, transferTo != null ? plannedEndTime : null, leg != null ? leg.min : 0);
            final String targetName = (transferTo != null) ? Formats.fullLocationName(transferTo.location) : null;
            setNextEventTarget(targetName);
            final String arrName = (transferFrom != null) ? Formats.fullLocationName(transferFrom.location) : null;
            final boolean depChanged = arrName != null && !arrName.equals(targetName);
            setNextEventDeparture(null);
            final Position arrPos = transferFrom != null ? transferFrom.getArrivalPosition() : null;
            final Position plannedArrPos = transferFrom != null ? transferFrom.plannedArrivalPosition : null;
            final Position depPos = transferTo != null ? transferTo.getDeparturePosition() : null;
            final Position plannedDepPos = transferTo != null ? transferTo.plannedDeparturePosition : null;
            setNextEventPositions(
                    transferFrom, arrPos, arrPos != null && !arrPos.equals(plannedArrPos),
                    transferTo, depPos, depPos != null && !depPos.equals(plannedDepPos));
            setNextEventTransport(legC.transferTo != null ? legC.transferTo.publicLeg : null);
            setNextEventTransferTimes(legC, true, now);
            setNextEventActions(transferTo == null ? 0
                            : transferFrom == null ? (eventIsNow
                                        ? R.string.directions_trip_details_next_event_action_departure_now
                                        : R.string.directions_trip_details_next_event_action_departure)
                            : depChanged ? (eventIsNow
                                        ? R.string.directions_trip_details_next_event_action_transfer_now
                                        : R.string.directions_trip_details_next_event_action_transfer)
                            : (eventIsNow
                                        ? R.string.directions_trip_details_next_event_action_interchange_now
                                        : R.string.directions_trip_details_next_event_action_interchange),
                    0);

            notificationData.publicArrivalLegIndex = legC.transferFrom != null ? legC.transferFrom.legIndex : -1;
            notificationData.publicDepartureLegIndex = legC.transferTo != null ? legC.transferTo.legIndex : -1;
            notificationData.isArrival = false;
            notificationData.eventTime = endTime;
            notificationData.plannedEventTime = plannedEndTime;
            notificationData.departurePosition = depPos;
            notificationData.plannedDeparturePosition = plannedDepPos;
            notificationData.nextTransferCritical = nextEventTransferLeftTimeCritical;
            return true;
        }
        return false;
    }

    private void setupPath(final Trip.Leg leg) {
        if (leg.path == null) {
            leg.path = new ArrayList<>();

            if (leg.departure != null) {
                final Point departurePoint = pointFromLocation(leg.departure);
                if (departurePoint != null)
                    leg.path.add(departurePoint);
            }

            if (leg instanceof Trip.Public) {
                final Trip.Public publicLeg = (Trip.Public) leg;
                final List<Stop> intermediateStops = publicLeg.intermediateStops;

                if (intermediateStops != null) {
                    for (final Stop stop : intermediateStops) {
                        final Point stopPoint = pointFromLocation(stop.location);
                        if (stopPoint != null)
                            leg.path.add(stopPoint);
                    }
                }
            }

            if (leg.arrival != null) {
                final Point arrivalPoint = pointFromLocation(leg.arrival);
                if (arrivalPoint != null)
                    leg.path.add(arrivalPoint);
            }
        }
    }

    private static Point pointFromLocation(final Location location) {
        if (location.hasCoord())
            return location.coord;

        return null;
    }

    public boolean nextEventTypeIsPublic;

    private void setNextEventType(boolean isPublic) {
        nextEventTypeIsPublic = isPublic;
    }

    public Date nextEventClock;

    private void setNextEventClock(final Date time) {
        nextEventClock = time;
    }

    public int nextEventCurrentStringId;
    public int nextEventNextStringId;

    private void setNextEventActions(final int currentId, final int nextId) {
        nextEventCurrentStringId = currentId;
        nextEventNextStringId = nextId;
    }

    public Date nextEventEarliestTime;
    public Date nextEventEstimatedTime;
    public long nextEventTimeLeftMs;
    public String nextEventTimeLeftValue;
    public String nextEventTimeLeftUnit;
    public String nextEventTimeLeftChronometerFormat;
    public boolean nextEventTimeLeftCritical;
    public boolean nextEventTimeHourglassVisible;
    public String nextEventTimeLeftExplainStr;

    @SuppressLint("DefaultLocale")
    private boolean setNextEventTimeLeft(final Date now, final Date endTime, final Date plannedEndTime, final int walkMins) {
        boolean retValue = false;
        nextEventEstimatedTime = endTime;
        nextEventEarliestTime = endTime;
        if (plannedEndTime != null && plannedEndTime.before(endTime))
            nextEventEarliestTime = plannedEndTime;
        nextEventTimeLeftMs = endTime.getTime() - now.getTime();
        long leftSecs = nextEventTimeLeftMs / 1000;
        long delaySecs = (plannedEndTime == null) ? 0 : (endTime.getTime() - plannedEndTime.getTime()) / 1000;
        leftSecs += 5;
        boolean isNegative = false;
        if (leftSecs < 0) {
            isNegative = true;
            leftSecs = -leftSecs;
        }
        long value = 0;
        String valueStr = null;
        final String unit;
        String chronoFormat = null;
        String explainStr = null;
        final boolean hourglassVisible;
        if (leftSecs < 70) {
            retValue = true;
            valueStr = NO_TIME_LEFT_VALUE;
            unit = "";
            hourglassVisible = false;
        } else {
            hourglassVisible = true;
            final long leftMins = leftSecs / 60;
            if (leftMins < 60) {
                value = leftMins;
                unit = "min";
                final long delayMins = delaySecs / 60;
                if (delayMins != 0)
                    explainStr = String.format("(%d%+d)", leftMins - delayMins, delayMins);
                if (leftMins >= 10)
                    chronoFormat = "%1$.2s";
            } else {
                final long leftHours = leftMins / 60;
                if (leftHours < 3) {
                    valueStr = String.format("%d:%02d", leftHours, leftMins - leftHours * 60);
                    unit = "h";
                    chronoFormat = "%1$.4s";
                } else if (leftHours < 24) {
                    value = leftHours;
                    unit = "h";
                } else {
                    value = (leftHours + 12) / 24;
                    unit = "d";
                }
            }
        }
        if (valueStr == null)
            valueStr = "" + value;
        if (isNegative)
            valueStr = "-" + valueStr;

        nextEventTimeLeftValue = valueStr;
        nextEventTimeLeftUnit = unit;
        nextEventTimeLeftChronometerFormat = chronoFormat;
        nextEventTimeLeftCritical = leftSecs - walkMins * 60 < 60;
        nextEventTimeHourglassVisible = hourglassVisible;
        nextEventTimeLeftExplainStr = explainStr;

        return retValue;
    }

    public String nextEventTargetName;

    private void setNextEventTarget(final String name) {
        nextEventTargetName = Formats.makeBreakableStationName(name);
    }

    public boolean nextEventPositionsAvailable;
    public Stop nextEventArrivalStop;
    public Stop nextEventDepartureStop;
    public boolean nextEventStopChange;
    public String nextEventArrivalPosName;
    public boolean nextEventArrivalPosChanged;
    public String nextEventDeparturePosName;
    public boolean nextEventDeparturePosChanged;

    private void setNextEventPositions(
            final Stop arrStop, final Position arrPos, boolean arrChanged,
            final Stop depStop, final Position depPos, boolean depChanged) {
        nextEventArrivalStop = arrStop;
        nextEventDepartureStop = depStop;
        nextEventStopChange = (arrStop != null && depStop != null) && !arrStop.location.id.equals(depStop.location.id);
        nextEventPositionsAvailable = arrPos != null || depPos != null;
        nextEventArrivalPosName = arrPos != null ? Formats.makeBreakableStationName(arrPos.name) : null;
        nextEventArrivalPosChanged = arrChanged;
        nextEventDeparturePosName = depPos != null ? Formats.makeBreakableStationName(depPos.name) : null;
        nextEventDeparturePosChanged = depChanged;
    }

    public Line nextEventTransportLine;
    public String nextEventTransportDestinationName;

    private void setNextEventTransport(final Trip.Public leg) {
        if (leg == null || leg.line == null) {
            nextEventTransportLine = null;
            nextEventTransportDestinationName = null;
        } else {
            nextEventTransportLine = leg.line;
            final Location dest = leg.destination;
            nextEventTransportDestinationName = dest == null ? null :
                    Formats.makeBreakableStationName(Formats.fullLocationName(dest));
        }
    }

    public boolean nextEventChangeOverAvailable;
    public boolean nextEventTransferAvailable;
    public String nextEventTransferLeftTimeValue;
    public String nextEventTransferLeftTimeFromNowValue;
    public boolean nextEventTransferLeftTimeCritical;
    public String nextEventTransferExplain;
    public boolean nextEventTransferWalkAvailable;
    public String nextEventTransferWalkTimeValue;
    public int nextEventTransferIconId;

    private void setNextEventTransferTimes(final LegContainer walkLegC, final boolean forWalkLeg, final Date now) {
        if (walkLegC == null) {
            nextEventChangeOverAvailable = false;
            return;
        }

        nextEventChangeOverAvailable = true;

        final Trip.Individual individualLeg = walkLegC.individualLeg;
        final int walkMins = individualLeg != null ? individualLeg.min : 0;

        if (!forWalkLeg && walkLegC.transferFrom != null && walkLegC.transferTo != null) {
            nextEventTransferAvailable = true;
            final Stop arrivalStop = walkLegC.transferFrom.publicLeg.arrivalStop;
            final Stop departureStop = walkLegC.transferTo.publicLeg.departureStop;
            final Date arrTime = arrivalStop.getArrivalTime();
            final Date depTime = departureStop.getDepartureTime();
            final long leftMins = (depTime.getTime() - arrTime.getTime()) / 60000 - 1;
            nextEventTransferLeftTimeValue = Long.toString(leftMins);
            nextEventTransferLeftTimeCritical = leftMins - walkMins < TRANSFER_CRITICAL_MINUTES;

            final long leftMinsFromNow = (depTime.getTime() - now.getTime()) / 60000;
            if (leftMinsFromNow <= 15)
                nextEventTransferLeftTimeFromNowValue = Long.toString(leftMinsFromNow);

            final long arrDelay = (arrTime.getTime() - arrivalStop.plannedArrivalTime.getTime()) / 60000;
            final long depDelay = (depTime.getTime() - departureStop.plannedDepartureTime.getTime()) / 60000;
            if (arrDelay != 0 || depDelay != 0) {
                String explainStr = String.format("(%d", leftMins + arrDelay - depDelay);
                if (depDelay != 0) explainStr += String.format("%+d", depDelay);
                if (arrDelay != 0) explainStr += String.format("%+d", -arrDelay);
                explainStr += ")";
                nextEventTransferExplain = explainStr;
            } else {
                nextEventTransferExplain = null;
            }
        } else {
            nextEventTransferAvailable = false;
        }

        if (walkMins > 0) {
            nextEventTransferWalkAvailable = true;
            nextEventTransferWalkTimeValue = Integer.toString(walkMins);
            final int iconId;
            if (individualLeg == null)
                iconId = R.drawable.ic_directions_walk_grey600_24dp;
            else switch (individualLeg.type) {
                case WALK:
                default:
                    iconId = R.drawable.ic_directions_walk_grey600_24dp;
                    break;
                case BIKE:
                    iconId = R.drawable.ic_directions_bike_grey600_24dp;
                    break;
                case CAR:
                case TRANSFER:
                    iconId = R.drawable.ic_local_taxi_grey600_24dp;
                    break;
            }
            nextEventTransferIconId = iconId;

        } else {
            nextEventTransferWalkAvailable = false;
            nextEventTransferWalkTimeValue = null;
            nextEventTransferIconId = 0;
        }
    }

    public String nextEventDepartureName;

    private void setNextEventDeparture(String name) {
        nextEventDepartureName = Formats.makeBreakableStationName(name);
    }
}
