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
import java.util.function.Consumer;

import javax.annotation.Nullable;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.GeoUtils;
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
        public Point refPoint;
        public Date refTime;
        public Stop nearestStop;
        public float distanceToNearestStop;
        public Stop sectionOtherStop;
        public float sectionLength;
        public boolean sectionIsAfterNearestStop; // otherwise is before
        public Date plannedTimeAtRefPoint;
        public Trip.Public simulatedPublicLeg;

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
            if (initialLeg != null) {
                publicLeg = updatedLeg;
                setRefPoint(refPoint, refTime);
            }
        }

        private void setRefPoint(final Point refPoint, final Date refTime) {
            this.refPoint = refPoint;
            this.refTime = refTime;
            nearestStop = null;
            sectionOtherStop = null;
            distanceToNearestStop = Float.MAX_VALUE;
            sectionLength = Float.MAX_VALUE;
            simulatedPublicLeg = null;
            if (publicLeg == null)
                return;
            if (refPoint == null)
                return;

            final Consumer<Consumer<Stop>> evalAllStops = consumer -> {
                consumer.accept(publicLeg.departureStop);
                if (publicLeg.intermediateStops != null) {
                    for (Stop intermediateStop : publicLeg.intermediateStops)
                        consumer.accept(intermediateStop);
                }
                consumer.accept(publicLeg.arrivalStop);
            };

            // first step: nearest stop
            evalAllStops.accept(stop -> {
                final Location location = stop.location;
                if (!location.hasCoord())
                    return;
                final float distanceToRef = GeoUtils.distanceBetween(location.coord, refPoint).distanceInMeters;
                if (distanceToRef > distanceToNearestStop)
                    return;
                nearestStop = stop;
                distanceToNearestStop = distanceToRef;
            });

            if (nearestStop == null)
                return;

            final float MINIMUM_REQUIRED_DISTANCE = 500;
            final Point nearestStopCoord = nearestStop.location.coord;

            // second step: nearest other stop to the nearest stop that is at least 500 meters afar.
            sectionIsAfterNearestStop = false;
            evalAllStops.accept(new Consumer<Stop>() {
                boolean isAfterNearestStop = false;
                float minDist = Float.MAX_VALUE;
                @Override
                public void accept(final Stop stop) {
                    final Location location = stop.location;
                    if (stop == nearestStop) {
                        isAfterNearestStop = true;
                        return;
                    }
                    if (!location.hasCoord())
                        return;
                    final float distanceToNearest = GeoUtils.distanceBetween(location.coord, nearestStopCoord).distanceInMeters;
                    if (distanceToNearest < MINIMUM_REQUIRED_DISTANCE)
                        return;
                    final float distanceToRef = GeoUtils.distanceBetween(location.coord, refPoint).distanceInMeters;
                    if (distanceToRef > minDist)
                        return;
                    minDist = distanceToRef;
                    sectionOtherStop = stop;
                    sectionLength = distanceToNearest;
                    sectionIsAfterNearestStop = isAfterNearestStop;
                }
            });

            if (sectionOtherStop != null) {
                final Stop beginStop, endStop;
                final float beginDist;
                if (sectionIsAfterNearestStop) {
                    beginStop = nearestStop;
                    endStop = sectionOtherStop;
                    beginDist = distanceToNearestStop;
                } else {
                    beginStop = sectionOtherStop;
                    endStop = nearestStop;
                    beginDist = sectionLength - distanceToNearestStop;
                }
                final float distRel = beginDist / sectionLength; // should be between 0.0 and 1.0
                if (distRel <= 0.0) {
                    plannedTimeAtRefPoint = beginStop.plannedDepartureTime;
                } else if (distRel >= 1.0) {
                    plannedTimeAtRefPoint = endStop.plannedArrivalTime;
                } else {
                    final long beginTime = beginStop.plannedDepartureTime.getTime();
                    final long endTime = endStop.plannedArrivalTime.getTime();
                    plannedTimeAtRefPoint = new Date(beginTime + (long) (distRel * (float) (endTime - beginTime)));
                }

                final long delayAtRefPoint = refTime.getTime() - plannedTimeAtRefPoint.getTime();
                if (delayAtRefPoint > 0) {
                    Stop departureStop = publicLeg.departureStop;
                    Stop arrivalStop = publicLeg.arrivalStop;
                    List<Stop> intermediateStops = publicLeg.intermediateStops;
                    if (arrivalStop != endStop && intermediateStops != null) {
                        intermediateStops = new ArrayList<>();
                        boolean delayedArrival = false;
                        for (Stop stop : publicLeg.intermediateStops) {
                            final boolean delayedDeparture;
                            if (delayedArrival) {
                                final long stopIntervalLength = stop.plannedDepartureTime.getTime() - stop.plannedArrivalTime.getTime();
                                delayedDeparture = stopIntervalLength < 4 * 60000;
                            } else {
                                delayedDeparture = false;
                            }
                            intermediateStops.add(new Stop(
                                    stop.location,
                                    stop.plannedArrivalTime,
                                    delayedArrival ? new Date(stop.plannedArrivalTime.getTime() + delayAtRefPoint) : stop.predictedArrivalTime,
                                    stop.plannedArrivalPosition, stop.predictedArrivalPosition,
                                    stop.arrivalCancelled,
                                    stop.plannedDepartureTime,
                                    delayedDeparture ? new Date(stop.plannedDepartureTime.getTime() + delayAtRefPoint) : stop.predictedDepartureTime,
                                    stop.plannedDeparturePosition, stop.predictedDeparturePosition,
                                    stop.departureCancelled));
                            if (!delayedDeparture)
                                delayedArrival = false;
                            if (stop == beginStop)
                                delayedArrival = true;
                        }
                    }
                    arrivalStop = new Stop(
                            arrivalStop.location,
                            arrivalStop.plannedArrivalTime, new Date(arrivalStop.plannedArrivalTime.getTime() + delayAtRefPoint),
                            arrivalStop.plannedArrivalPosition, arrivalStop.predictedArrivalPosition,
                            arrivalStop.arrivalCancelled,
                            arrivalStop.plannedDepartureTime, arrivalStop.predictedDepartureTime,
                            arrivalStop.plannedDeparturePosition, arrivalStop.predictedDeparturePosition,
                            arrivalStop.departureCancelled);

                    simulatedPublicLeg = new Trip.Public(
                            publicLeg.line,
                            publicLeg.destination,
                            departureStop,
                            arrivalStop,
                            intermediateStops,
                            publicLeg.path,
                            publicLeg.message,
                            publicLeg.journeyRef,
                            refTime);
                }
            }
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
        public long playedStartAlarmId;
    }

    public final Trip trip;
    private final boolean isJourney;

    public List<LegContainer> legs = new ArrayList<>();
    public LegContainer currentLeg;
    public final Map<LegKey, Boolean> legExpandStates;
    public LegContainer nearestPublicLeg;
    public NotificationData notificationData;
    public Point refPoint;
    public Date refTime;
    private Boolean feasible;

    public TripRenderer(final TripRenderer previous, final Trip trip, final boolean isJourney, final Date now) {
        this.trip = trip;
        this.isJourney = isJourney;
        this.legExpandStates = previous != null ? previous.legExpandStates : new HashMap<>();
        setupFromTrip(trip);
        evaluateByTime(now);
    }

    public void setRefPoint(final Point refPoint, final Date refTime) {
        this.refPoint = refPoint;
        this.refTime = refTime;
        nearestPublicLeg = null;
        float minDistance = Float.MAX_VALUE;
        for (LegContainer leg : legs) {
            leg.setRefPoint(refPoint, refTime);
            if (leg.nearestStop != null && leg.distanceToNearestStop < minDistance) {
                nearestPublicLeg = leg;
                minDistance = leg.distanceToNearestStop;
            }
        }
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
            final Trip.Leg leg = trip.legs.get(iLeg);
            final Trip.Leg prevLeg = (iLeg > 0) ? trip.legs.get(iLeg - 1) : null;
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
                isCurrent = updateIndividualLeg(legC, iLeg == 0, now);
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
        final Trip.Public leg = legC.publicLeg;
        final Stop departureStop = leg.departureStop;
        final Stop arrivalStop = leg.arrivalStop;
        final Date beginTime = departureStop.getDepartureTime();
        final Date plannedBeginTime = departureStop.plannedDepartureTime;
        final Date endTime = arrivalStop.getArrivalTime();
        final Date plannedEndTime = arrivalStop.plannedArrivalTime;
        if (now.before(beginTime)) {
            // leg is in the future
        } else if (now.after(endTime)) {
            // leg is in the past
        } else {
            // leg is now
            setNextEventType(true, false);
            setPrevEventLatestTime(beginTime, plannedBeginTime);
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

            // if (nextPublicLeg != null)
            //     setNextPublicLegDuration(nextPublicLeg.getDepartureTime(), nextPublicLeg.getArrivalTime());

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
            final boolean isInitialIndividual,
            final Date now) {
        final Trip.Individual leg = legC.individualLeg;
        final Trip.Public nextPublicLeg = legC.transferTo != null ? legC.transferTo.publicLeg : null;
        final Stop transferFrom = legC.transferFrom != null ? legC.transferFrom.publicLeg.arrivalStop : null;
        final Stop transferTo = nextPublicLeg != null ? nextPublicLeg.departureStop : null;
        final Date beginTime = transferFrom != null ? transferFrom.getArrivalTime() : leg == null ? null : leg.departureTime;
        final Date plannedBeginTime = transferFrom != null ? transferFrom.plannedArrivalTime : leg == null ? null : leg.departureTime;
        final Date endTime = transferTo != null ? transferTo.getDepartureTime() : leg == null ? null : leg.arrivalTime;
        final Date plannedEndTime = transferTo != null ? transferTo.plannedDepartureTime : leg == null ? null : leg.arrivalTime;
        if (transferFrom != null && beginTime != null && now.before(beginTime)) {
            // leg is in the future
        } else if (endTime != null && now.after(endTime)) {
            // leg is in the past
        } else {
            // leg is now
            setNextEventType(false, isInitialIndividual);
            setPrevEventLatestTime(beginTime, plannedBeginTime);
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
            setNextEventTransport(nextPublicLeg);
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

             if (nextPublicLeg != null)
                 setNextPublicLegDuration(nextPublicLeg.getDepartureTime(), nextPublicLeg.getArrivalTime());

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
    public boolean nextEventIsInitialIndividual;

    private void setNextEventType(final boolean isPublic, final boolean isInitialIndividual) {
        nextEventTypeIsPublic = isPublic;
        nextEventIsInitialIndividual = isInitialIndividual;
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

    public Date prevEventLatestTime;
    public Date nextEventEarliestTime;
    public Date nextEventEstimatedTime;
    public long nextEventTimeLeftMs;
    public String nextEventTimeLeftValue;
    public String nextEventTimeLeftUnit;
    public String nextEventTimeLeftChronometerFormat;
    public boolean nextEventTimeLeftCritical;
    public boolean nextEventTimeHourglassVisible;
    public String nextEventTimeLeftExplainStr;

    private void setPrevEventLatestTime(final Date beginTime, final Date plannedBeginTime) {
        prevEventLatestTime = beginTime;
        if (plannedBeginTime != null && plannedBeginTime.after(beginTime))
            prevEventLatestTime = plannedBeginTime;
    }

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
        nextEventArrivalPosName = arrPos != null ? Formats.makeBreakableStationName(arrPos.toString()) : null;
        nextEventArrivalPosChanged = arrChanged;
        nextEventDeparturePosName = depPos != null ? Formats.makeBreakableStationName(depPos.toString()) : null;
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

    public String nextPublicLegDurationTimeValue;

    public void setNextPublicLegDuration(final Date begin, final Date end) {
        this.nextPublicLegDurationTimeValue = Long.toString((end.getTime() - begin.getTime()) / 60000);
    }
}
