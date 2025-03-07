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

    public static class LegContainer {
        public @Nullable Trip.Individual individualLeg;
        public @Nullable Trip.Public publicLeg;
        public final @Nullable Trip.Public initialLeg;
        public final LegContainer transferFrom;
        public final LegContainer transferTo;

        public LegContainer(
                final @Nullable Trip.Public baseLeg) {
            this.publicLeg = baseLeg;
            this.initialLeg = baseLeg;
            this.individualLeg = null;
            this.transferFrom = null;
            this.transferTo = null;
        }

        public LegContainer(
                final @Nullable Trip.Individual baseLeg,
                final LegContainer transferFrom, final LegContainer transferTo) {
            this.individualLeg = baseLeg;
            this.transferFrom = transferFrom;
            this.transferTo = transferTo;
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
        long refreshRequiredAt;
        int legIndex;
        boolean isArrival;
        Date eventTime;
        Date plannedEventTime;
        Position position;
        Position plannedPosition;
        long leftTimeReminded;
        boolean transferCritical;
    }

    public final Trip trip;
    private final boolean isJourney;

    public List<LegContainer> legs = new ArrayList<>();
    public LegContainer currentLeg;
    public final Map<LegKey, Boolean> legExpandStates = new HashMap<>();
    public NotificationData notificationData;
    private Boolean travelable;

    public TripRenderer(final Trip trip, final boolean isJourney, final Date now) {
        this.trip = trip;
        this.isJourney = isJourney;
        setupFromTrip(trip);
        evaluateByTime(now);
    }

    public boolean isFeasible() {
        if (travelable == null) {
            travelable = trip.isTravelable();
        }
        return travelable;
    }

    private void setupFromTrip(final Trip trip) {
        LegContainer prevC = null;
        for (int iLeg = 0; iLeg < trip.legs.size(); ++iLeg) {
            final Trip.Leg prevLeg = (iLeg > 0) ? trip.legs.get(iLeg - 1) : null;
            Trip.Leg leg = trip.legs.get(iLeg);
            final Trip.Leg nextLeg = (iLeg + 1 < trip.legs.size()) ? trip.legs.get(iLeg + 1) : null;

            if (leg instanceof Trip.Individual) {
                final LegContainer transferFrom = (prevLeg instanceof Trip.Public) ? prevC : null;
                final LegContainer transferTo = (nextLeg instanceof Trip.Public) ? new LegContainer((Trip.Public) nextLeg) : null;
                legs.add(new LegContainer((Trip.Individual) leg, transferFrom, transferTo));
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
                final LegContainer newC = new LegContainer((Trip.Public) leg);
                if (prevC != null || iLeg == 0) {
                    legs.add(new LegContainer(null, prevC, newC));
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
                notificationData.legIndex = iLeg;
             }
        }
    }

    private boolean updatePublicLeg(
            final TripRenderer.LegContainer legC,
            final TripRenderer.LegContainer walkLegC,
            final TripRenderer.LegContainer nextLegC,
            final Date now) {
        Trip.Public leg = legC.publicLeg;
        Date beginTime = leg.departureStop.getDepartureTime();
        Date endTime = leg.arrivalStop.getArrivalTime();
        Date plannedEndTime = leg.arrivalStop.plannedArrivalTime;
        if (now.before(beginTime)) {
            // leg is in the future
        } else if (now.after(endTime)) {
            // leg is in the past
        } else {
            // leg is now
            setNextEventType(true);
            final boolean eventIsNow = setNextEventTimeLeft(now, endTime, plannedEndTime, 0);
            String targetName = leg.arrivalStop.location.uniqueShortName();
            setNextEventTarget(targetName);
            String depName = (nextLegC != null) ? nextLegC.publicLeg.departureStop.location.uniqueShortName() : null;
            boolean depChanged = depName != null && !depName.equals(targetName);
            setNextEventDeparture(depChanged ? depName : null);
            final Position arrPos = leg.arrivalStop.getArrivalPosition();
            final Position depPos = (nextLegC != null) ? nextLegC.publicLeg.getDeparturePosition() : null;
            final Position plannedDepPos = (nextLegC != null) ? nextLegC.publicLeg.departureStop.plannedDeparturePosition : null;
            setNextEventPositions(arrPos, depPos, depPos != null && !depPos.equals(plannedDepPos));
            setNextEventTransport((nextLegC != null) ? nextLegC.publicLeg : null);
            setNextEventTransferTimes(walkLegC, false);
            setNextEventActions(
                    nextLegC != null
                            ? (eventIsNow
                                ? R.string.directions_trip_details_next_event_action_ride_now
                                : R.string.directions_trip_details_next_event_action_ride)
                            : (eventIsNow
                                ? R.string.directions_trip_details_next_event_action_arrival_now
                                : R.string.directions_trip_details_next_event_action_arrival),
                        walkLegC == null ? 0
                            : depChanged ? R.string.directions_trip_details_next_event_action_next_transfer
                            : R.string.directions_trip_details_next_event_action_next_interchange
            );

            notificationData.isArrival = true;
            notificationData.eventTime = endTime;
            notificationData.plannedEventTime = plannedEndTime;
            notificationData.position = depPos;
            notificationData.plannedPosition = plannedDepPos;
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
            final String targetName = (transferTo != null) ? transferTo.location.uniqueShortName() : null;
            setNextEventTarget(targetName);
            final String arrName = (transferFrom != null) ? transferFrom.location.uniqueShortName() : null;
            final boolean depChanged = arrName != null && !arrName.equals(targetName);
            setNextEventDeparture(null);
            final Position arrPos = transferFrom != null ? transferFrom.getArrivalPosition() : null;
            final Position depPos = transferTo != null ? transferTo.getDeparturePosition() : null;
            final Position plannedDepPos = transferTo != null ? transferTo.plannedDeparturePosition : null;
            setNextEventPositions(arrPos, depPos, depPos != null && !depPos.equals(plannedDepPos));
            setNextEventTransport(legC.transferTo != null ? legC.transferTo.publicLeg : null);
            setNextEventTransferTimes(legC, true);
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

            notificationData.isArrival = false;
            notificationData.eventTime = endTime;
            notificationData.plannedEventTime = plannedEndTime;
            notificationData.position = depPos;
            notificationData.plannedPosition = plannedDepPos;
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
    public String nextEventArrivalPosName;
    public String nextEventDeparturePosName;
    public boolean nextEventDeparturePosChanged;

    private void setNextEventPositions(final Position arrPos, final Position depPos, boolean depChanged) {
        nextEventPositionsAvailable = arrPos != null || depPos != null;
        nextEventArrivalPosName = arrPos != null ? Formats.makeBreakableStationName(arrPos.name) : null;
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
            nextEventTransportDestinationName = dest != null ? Formats.makeBreakableStationName(dest.uniqueShortName()) : null;
        }
    }

    public boolean nextEventChangeOverAvailable;
    public boolean nextEventTransferAvailable;
    public String nextEventTransferLeftTimeValue;
    public boolean nextEventTransferLeftTimeCritical;
    public String nextEventTransferExplain;
    public boolean nextEventTransferWalkAvailable;
    public String nextEventTransferWalkTimeValue;
    public int nextEventTransferIconId;

    private void setNextEventTransferTimes(final LegContainer walkLegC, final boolean forWalkLeg) {
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
            long leftMins = (depTime.getTime() - arrTime.getTime()) / 60000 - 1;
            nextEventTransferLeftTimeValue = Long.toString(leftMins);
            nextEventTransferLeftTimeCritical = leftMins - walkMins < 3;

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
