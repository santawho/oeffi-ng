package de.schildbach.oeffi.directions.navigation;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.util.GeoUtils;

public class TripGeoUtils extends GeoUtils {
    public static final int DEFAULT_ESTIMATION_UNCERTAINTY_SECONDS = 300;
    public static final int DEFAULT_MAX_DISTANCE_METERS = 1000;

    public static double getBearing(final Point from, final Point to) {
        final double fromLat = from.getLatAsDouble();
        final double fromLon = from.getLonAsDouble();
        final double toLat = to.getLatAsDouble();
        final double toLon = to.getLonAsDouble();
        final double lonDiff = toLon - fromLon;
        final double y = Math.sin(lonDiff) * Math.cos(toLat);
        final double x = Math.cos(fromLat) * Math.sin(toLat) - Math.sin(fromLat) * Math.cos(toLat) * Math.cos(lonDiff);
        return Math.toDegrees(Math.atan2(y, x));
    }

    public static class ClosestPointOnTrip {
        public final Point position;
        public final PointAndDistance closest;
        public final Trip.Public closestLeg;
        public final int closestLegIndex;
        public final Point expectedPoint;
        public final double expectedDistance;
        public final boolean isNearCurrentTrain;
        public final boolean isNearOrigin;
        public final List<Integer> ridingOnLegsIndexes;
        public final List<Integer> waitingForLegsIndexes;

        public ClosestPointOnTrip(
                final Point position,
                final Trip trip) {
            this (position, trip, null);
        }

        public ClosestPointOnTrip(
                final Point position,
                final Trip trip,
                final Date refTime) {
            this(position, trip, refTime, null, null);
        }

        public ClosestPointOnTrip(
                final Point position,
                final Trip trip,
                final Date refTime,
                final Integer estimationUncertaintySeconds,
                final Double maxDistanceMeters) {
            this(
                    position,
                    trip.legs,
                    refTime != null ? refTime.getTime() : System.currentTimeMillis(),
                    1000L * (estimationUncertaintySeconds != null
                            ? estimationUncertaintySeconds
                            : DEFAULT_ESTIMATION_UNCERTAINTY_SECONDS),
                    maxDistanceMeters != null ? maxDistanceMeters
                            : DEFAULT_MAX_DISTANCE_METERS);
        }

        private ClosestPointOnTrip(
                final Point position,
                final List<Trip.Leg> legs,
                final long refTime,
                final long estimationUncertaintyMillis,
                final double maxDistanceMeters) {
            this.position = position;

            Trip.Public closestLeg = null;
            int closestLegIndex = 0;
            PointAndDistance closestPoint = null;
            double closestDistance = Double.MAX_VALUE;
            double expectedDistance = Double.MAX_VALUE;
            Point expectedPoint = null;
            boolean isNearCurrentTrain = false;
            boolean isNearOrigin = false;
            ridingOnLegsIndexes = new ArrayList<>();
            waitingForLegsIndexes = new ArrayList<>();
            UncertainTimeSpan prevArrivalTimeSpan = null;

            for (int legIndex = 0; legIndex < legs.size(); legIndex++) {
                final Trip.Leg aLeg = legs.get(legIndex);
                if (!(aLeg instanceof Trip.Public))
                    continue;
                final Trip.Public leg = (Trip.Public) aLeg;
                final ClosestPointOnLeg closestPointOnLeg = new ClosestPointOnLeg(
                        position, leg,
                        null, null,
                        refTime,
                        estimationUncertaintyMillis, maxDistanceMeters);
                final PointAndDistance closest = closestPointOnLeg.closest;
                if (closest.distanceInMeters < closestDistance) {
                    closestLeg = leg;
                    closestPoint = closest;
                    closestDistance = closest.distanceInMeters;
                    closestLegIndex = legIndex;
                }
                final UncertainTimeSpan arrivalTimeSpan = UncertainTimeSpan.get(
                        leg.arrivalStop.plannedArrivalTime, leg.arrivalStop.predictedArrivalTime,
                        estimationUncertaintyMillis);
                final UncertainTimeSpan departureTimeSpan = UncertainTimeSpan.get(
                        leg.departureStop.plannedDepartureTime, leg.departureStop.predictedDepartureTime,
                        estimationUncertaintyMillis);
                if (prevArrivalTimeSpan == null) {
                    if (departureTimeSpan != null && refTime < departureTimeSpan.end) {
                        final Point originGeo = leg.departureStop.location.coord;
                        if (originGeo != null) {
                            final double dist = geoDistanceInMeters(position, originGeo);
                            if (dist < maxDistanceMeters) {
                                isNearOrigin = true;
                                waitingForLegsIndexes.add(legIndex);
                            }
                            if (dist < expectedDistance) {
                                expectedPoint = originGeo;
                                expectedDistance = dist;
                            }
                        }
                    }
                } else if (departureTimeSpan != null
                        && refTime < departureTimeSpan.end
                        && refTime > departureTimeSpan.start) {
                    final Point stationGeo = leg.departureStop.location.coord;
                    if (stationGeo != null) {
                        final double dist = geoDistanceInMeters(position, stationGeo);
                        if (dist < maxDistanceMeters) {
                            isNearCurrentTrain = true;
                            waitingForLegsIndexes.add(legIndex);
                        }
                        if (dist < expectedDistance) {
                            expectedPoint = stationGeo;
                            expectedDistance = dist;
                        }
                    }
                }
                if (arrivalTimeSpan != null && departureTimeSpan != null
                        && refTime > departureTimeSpan.start && refTime < arrivalTimeSpan.end) {
                    if (closest.distanceInMeters < maxDistanceMeters) {
                        isNearCurrentTrain = true;
                        ridingOnLegsIndexes.add(legIndex);
                    }
                    if (closest.distanceInMeters < expectedDistance) {
                        expectedPoint = closest.point;
                        expectedDistance = closest.distanceInMeters;
                    }
                }
                prevArrivalTimeSpan = arrivalTimeSpan;
            }

            this.closest = closestPoint;
            this.closestLeg = closestLeg;
            this.closestLegIndex = closestLegIndex;
            this.expectedPoint = expectedPoint;
            this.expectedDistance = expectedDistance;
            this.isNearCurrentTrain = isNearCurrentTrain;
            this.isNearOrigin = isNearOrigin;
        }
    }

    public static class ClosestPointOnLeg {
        public final Point position;
        public final PointAndDistance closest;
        public final Point expectedPoint;
        public final double expectedDistance;
        public final boolean isNearCurrentTrain;
        public final boolean isNearOrigin;

        public ClosestPointOnLeg(
                final Point position,
                final Trip.Public journeyLeg) {
            this(position, journeyLeg, null, null, null, null, null);
        }

        public ClosestPointOnLeg(
                final Point position,
                final Trip.Public journeyLeg,
                final Date refTime) {
            this(position, journeyLeg, null, null, refTime, null, null);
        }

        public ClosestPointOnLeg(
                final Point position,
                final Trip.Public journeyLeg,
                final Location entryLocation,
                final Location exitLocation,
                final Date refTime) {
            this(position, journeyLeg, entryLocation, exitLocation, refTime, null, null);
        }

        public ClosestPointOnLeg(
                final Point position,
                final Trip.Public journeyLeg,
                final Location entryLocation,
                final Location exitLocation,
                final Date refTime,
                final Integer estimationUncertaintySeconds,
                final Double maxDistanceMeters) {
            this(
                    position,
                    journeyLeg,
                    entryLocation,
                    exitLocation,
                    refTime != null ? refTime.getTime() : System.currentTimeMillis(),
                    1000L * (estimationUncertaintySeconds != null
                            ? estimationUncertaintySeconds
                            : DEFAULT_ESTIMATION_UNCERTAINTY_SECONDS),
                            maxDistanceMeters != null ? maxDistanceMeters
                                    : DEFAULT_MAX_DISTANCE_METERS);
        }

        public ClosestPointOnLeg(
                final Point position,
                final Trip.Public journeyLeg,
                final Location entryLocation,
                final Location exitLocation,
                final long refTime,
                final long estimationUncertaintyMillis,
                final double maxDistanceMeters) {
            this.position = position;

            PointAndDistance closestPoint = null;
            double closestDistance = Double.MAX_VALUE;
            double expectedDistance = Double.MAX_VALUE;
            Point expectedPoint = null;
            boolean isNearCurrentTrain = false;
            boolean isNearOrigin = false;

            int originIndex = -1;
            Stop originStop = journeyLeg.departureStop;
            if (entryLocation != null) {
                final String entryIdentityId = entryLocation.identityId;
                if (journeyLeg.intermediateStops != null) {
                    final List<Stop> intermediateStops = journeyLeg.intermediateStops;
                    for (int index = 0; index < intermediateStops.size(); ++index) {
                        final Stop stop = intermediateStops.get(index);
                        if (entryIdentityId.equals(stop.location.identityId)) {
                            originStop = stop;
                            originIndex = index;
                            break;
                        }
                    }
                }
            }

            int destinationIndex = -1;
            Stop destinationStop = journeyLeg.departureStop;
            if (exitLocation != null) {
                final String exitIdentityId = exitLocation.identityId;
                if (journeyLeg.intermediateStops != null) {
                    final List<Stop> intermediateStops = journeyLeg.intermediateStops;
                    for (int index = intermediateStops.size() - 1; index >= 0; --index) {
                        final Stop stop = intermediateStops.get(index);
                        if (exitIdentityId.equals(stop.location.identityId)) {
                            destinationStop = stop;
                            destinationIndex = index;
                            break;
                        }
                    }
                }
            }

            final List<Point> path = getPathForLeg(journeyLeg);
            final PointAndDistance closestPointOnTrack = findClosestPointOnTrack(position, path, 0, -1);
            if (closestPointOnTrack.distanceInMeters < closestDistance) {
                closestPoint = closestPointOnTrack;
                closestDistance = closestPoint.distanceInMeters;
            }
            final UncertainTimeSpan departureTimeSpan = UncertainTimeSpan.get(
                    originStop.plannedDepartureTime, destinationStop.predictedDepartureTime,
                    estimationUncertaintyMillis);
            final UncertainTimeSpan arrivalTimeSpan = UncertainTimeSpan.get(
                    destinationStop.plannedArrivalTime, destinationStop.predictedArrivalTime,
                    estimationUncertaintyMillis);
            if (departureTimeSpan != null && refTime < departureTimeSpan.end) {
                final Point originCoord = originStop.location.coord;
                if (originCoord != null) {
                    final double dist = geoDistanceInMeters(position, originCoord);
                    if (dist < maxDistanceMeters) {
                        isNearOrigin = true;
                    }
                    if (dist < expectedDistance) {
                        expectedPoint = originCoord;
                        expectedDistance = dist;
                    }
                }
            }
            if (arrivalTimeSpan != null && departureTimeSpan != null
                    && refTime > departureTimeSpan.start && refTime < arrivalTimeSpan.end) {
                if (closestDistance < maxDistanceMeters) {
                    isNearCurrentTrain = true;
                }
                if (closestDistance < expectedDistance) {
                    expectedPoint = closestPoint.point;
                    expectedDistance = closestDistance;
                }
            }

            this.closest = closestPoint;
            this.expectedPoint = expectedPoint;
            this.expectedDistance = expectedDistance;
            this.isNearCurrentTrain = isNearCurrentTrain;
            this.isNearOrigin = isNearOrigin;
        }

    }

    public static List<Point> getPathForLeg(final Trip.Leg leg) {
        final List<Point> legPath = leg.getPath();
        if (legPath != null)
            return legPath;

        return getPathForLegStops(leg);
    }

    public static List<Point> getPathForLegStops(final Trip.Leg leg) {
        final List<Point> path = new ArrayList<>();

        if (leg.departure != null) {
            addPointFromLocation(path, leg.departure);
        }

        if (leg instanceof Trip.Public) {
            final Trip.Public publicLeg = (Trip.Public) leg;
            final List<Stop> intermediateStops = publicLeg.intermediateStops;

            if (intermediateStops != null) {
                for (final Stop stop : intermediateStops)
                    addPointFromLocation(path, stop.location);
            }
        }

        if (leg.arrival != null) {
            addPointFromLocation(path, leg.arrival);
        }

        return path;
    }

    private static void addPointFromLocation(final List<Point> path, final Location location) {
        if (location != null)
            path.add(location.coord);
    }

    public static class UncertainTimeSpan {
        final long start;
        final long end;

        public static UncertainTimeSpan get(
                final Date targetTime,
                final Date predictedTime,
                final long estimationUncertaintyMillis) {
            if (targetTime == null)
                return null;
            return new UncertainTimeSpan(targetTime, predictedTime, estimationUncertaintyMillis);
        }

        private UncertainTimeSpan(
                final Date targetTime,
                final Date predictedTime,
                final long estimationUncertaintyMillis) {
            start = targetTime.getTime() - 60000;
            end = (predictedTime != null ? predictedTime : targetTime).getTime()
                    + estimationUncertaintyMillis;
        }
    }

    public static class PointAndDistance {
        public final Point point;
        public final int pointBeforeIndex;
        public final int pointAfterIndex;
        public final double distanceInMeters;
        public final double relativePosition;

        public PointAndDistance(
                final Point point,
                final int pointBeforeIndex,
                final int pointAfterIndex,
                final double distanceInMeters,
                final double relativePosition) {
            this.point = point;
            this.pointBeforeIndex = pointBeforeIndex;
            this.pointAfterIndex = pointAfterIndex;
            this.distanceInMeters = distanceInMeters;
            this.relativePosition = relativePosition;
        }
    }

    public static PointAndDistance findClosestPointOnLine(
            final Point position,
            final Point pointA,
            final Point pointB) {
        final double distanceA = geoDistanceInMeters(position, pointA);
        final double distanceB = geoDistanceInMeters(position, pointB);
        final double distanceS = geoDistanceInMeters(pointA, pointB);
        if (distanceS < 0.0001)
            return new PointAndDistance(pointA, 0, 0, distanceA, 0.0);
        final double relativePosition =
                (distanceS * distanceS + distanceA * distanceA - distanceB * distanceB) /
                        (2 * distanceS * distanceS);
        if (relativePosition <= 0.0)
            return new PointAndDistance(pointA, -1, 0, distanceA, relativePosition);
        if (relativePosition >= 1.0)
            return new PointAndDistance(pointB, 1, 2, distanceB, relativePosition);
        final Point closestPoint = Point.fromDouble(
                pointA.getLatAsDouble()
                        + relativePosition * (pointB.getLatAsDouble() - pointA.getLatAsDouble()),
                pointA.getLonAsDouble()
                        + relativePosition * (pointB.getLonAsDouble() - pointA.getLonAsDouble()));
        return new PointAndDistance(
                closestPoint,
                0, 1,
                geoDistanceInMeters(position, closestPoint),
                relativePosition);
    }

    public static PointAndDistance findClosestPointOnTrack(
            final Point position,
            final List<Point> track,
            int startIndex, int endIndex) {
        if (track == null || track.isEmpty())
            return null;
        final int lastIndex = track.size() - 1;
        if (startIndex < 0)
            startIndex = 0;
        if (endIndex < 0 || endIndex > lastIndex)
            endIndex = lastIndex + 1;
        if (startIndex >= lastIndex) {
            final Point lastPoint = track.get(lastIndex);
            return new PointAndDistance(
                    lastPoint, lastIndex, lastIndex + 1,
                    GeoUtils.geoDistanceInMeters(position, lastPoint),
                    0.0);
        }
        PointAndDistance closestPoint = null;
        int pointBeforeIndex = -1;
        int pointAfterIndex = -1;
        double distance = Double.MAX_VALUE;
        Point pointA = track.get(startIndex);
        for (int indexAfter = startIndex + 1; indexAfter < endIndex; ++indexAfter) {
            final Point pointB = track.get(indexAfter);
            final PointAndDistance toLine = findClosestPointOnLine(position, pointA, pointB);
            if (closestPoint == null) {
                closestPoint = toLine;
                distance = toLine.distanceInMeters;
                pointBeforeIndex = toLine.pointBeforeIndex;
                pointAfterIndex = toLine.pointAfterIndex;
            } else if (toLine.distanceInMeters <= distance) {
                closestPoint = toLine;
                distance = toLine.distanceInMeters;
                final int bi = toLine.pointBeforeIndex;
                final int ax = bi + indexAfter;
                final int bx = ax - 1;
                if (bi < 0 && bx == pointBeforeIndex) {
                    // before this section and exactly after the previous
                    // treat like it lies between the same point
                    pointBeforeIndex = bx;
                    pointAfterIndex = bx;
                } else {
                    pointBeforeIndex = bx;
                    pointAfterIndex = ax;
                }
            }
            pointA = pointB;
        }
        return new PointAndDistance(
                closestPoint.point,
                pointBeforeIndex, pointAfterIndex,
                closestPoint.distanceInMeters,
                closestPoint.relativePosition);
    }

//    public GeoUtils.PointAndDistance getClosestLegPathPointForStop(
//            final Trip.Public publicLeg,
//            final Stop previousStop) {
//        if (this.publicLegRef == null || publicLegRef.get() != publicLeg) {
//            this.publicLegRef = new WeakReference<>(publicLeg);
//            final List<Point> points = publicLeg.path;
//            if (points != null && !points.isEmpty()) {
//                final int currentIndex = previousStop == null ? 0
//                        : previousStop.legPathPoint == null ? 0
//                        : previousStop.legPathPoint.pointAfterIndex;
//                this.legPathPoint = findClosestPointOnTrack(location.coord, points, currentIndex, -1);
//            }
//        }
//
//        return legPathPoint;
//    }

//    public void setupClosestPointOnStopsForPublicLeg() {
//        if (intermediateStops != null) {
//            Stop previousStop = departureStop;
//            for (final Stop stop : intermediateStops) {
//                stop.getClosestLegPathPoint(this, previousStop);
//                previousStop = stop;
//            }
//        }
//    }
}
