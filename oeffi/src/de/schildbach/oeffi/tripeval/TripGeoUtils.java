package de.schildbach.oeffi.tripeval;

import java.util.ArrayList;
import java.util.List;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.util.GeoUtils;

public class TripGeoUtils {
    public static double geoDistanceInMeters(final Point pointA, final Point pointB) {
        final double rad = Math.PI / 180;
        final double lat1 = pointA.getLatAsDouble() * rad;
        final double lon1 = pointA.getLonAsDouble() * rad;
        final double lat2 = pointB.getLatAsDouble() * rad;
        final double lon2 = pointB.getLonAsDouble() * rad;
        final double sinDLat = Math.sin((lat2 - lat1) / 2);
        final double sinDLon = Math.sin((lon2 - lon1) / 2);
        final double a = sinDLat * sinDLat + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
        final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000 * c;
    }

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

    public static double clipBearing(final double bearing) {
        if (bearing <= -180.0d)
            return bearing + 360.0d;
        if (bearing > 180.0d)
            return bearing - 360.0d;
        return bearing;
    }

    public static boolean isReverseBearing(final double moveBearing, final double pathBearing) {
        final double bearingDiff = clipBearing(pathBearing - moveBearing);
        return bearingDiff < -90.0 || bearingDiff > 90.0;
    }

    public static class GeoPath {
        private final List<Point> pointsAsList;
        private Point[] points;
        private double[] distancesFromStart;
        private double[] bearings;

        public GeoPath(final Trip.Leg leg, final boolean forStopsOnly) {
            if (!forStopsOnly) {
                final List<Point> legPath = leg.getPath();
                if (legPath != null) {
                    pointsAsList = legPath;
                    return;
                }
            }

            pointsAsList = new ArrayList<>();

            if (leg.departure != null) {
                addPointFromLocation(leg.departure);
            }

            if (leg instanceof Trip.Public) {
                final Trip.Public publicLeg = (Trip.Public) leg;
                final List<Stop> intermediateStops = publicLeg.intermediateStops;

                if (intermediateStops != null) {
                    for (final Stop stop : intermediateStops)
                        addPointFromLocation(stop.location);
                }
            }

            if (leg.arrival != null) {
                addPointFromLocation(leg.arrival);
            }
        }

        private void addPointFromLocation(final Location location) {
            if (location != null)
                pointsAsList.add(location.coord);
        }

        private Point[] getPointsAsArray() {
            if (points == null) {
                points = pointsAsList.toArray(new Point[0]);
            }
            return points;
        }

        private double[] getDistancesFromStart() {
            if (distancesFromStart == null) {
                final Point[] points = getPointsAsArray();
                final int length = points.length;
                distancesFromStart = new double[length];
                if (length > 0) {
                    double distSum = 0.0d;
                    distancesFromStart[0] = distSum;
                    Point prevPoint = points[0];
                    for (int index = 1; index < length; index += 1) {
                        final Point currPoint = points[index];
                        final double dist = geoDistanceInMeters(prevPoint, currPoint);
                        distSum += dist;
                        distancesFromStart[index] = distSum;
                        prevPoint = currPoint;
                    }
                }
            }
            return distancesFromStart;
        }

        public double[] getBearings() {
            if (bearings == null) {
                final Point[] points = getPointsAsArray();
                final int length = points.length;
                bearings = new double[length];
                if (length > 1) {
                    Point prevPoint = points[0];
                    for (int index = 1; index < length; index += 1) {
                        final Point currPoint = points[index];
                        bearings[index] = getBearing(prevPoint, currPoint);
                        prevPoint = currPoint;
                    }
                }
                bearings[0] = bearings[1];
            }
            return bearings;
        }

        public double getBearingBeforeIndex(final int pointOnPathIndex) {
            final double[] bearings = getBearings();
            if (bearings == null)
                return 0.0d;
            final int length = bearings.length;
            if (length == 0)
                return 0.0d;
            if (pointOnPathIndex < 0)
                return bearings[0];
            if (pointOnPathIndex >= length)
                return bearings[length - 1];
            return bearings[pointOnPathIndex];
        }

        public List<Point> getPoints() {
            return pointsAsList;
        }

        public boolean isEmpty() {
            return pointsAsList == null || pointsAsList.isEmpty();
        }

        public PointAndDistance findClosestPoint(final Point position, final Double bearing) {
            return findClosestPoint(position, 0, -1, bearing);
        }

        public PointAndDistance findClosestPoint(
                final Point position,
                int startIndex, int endIndex,
                final Double bearing) {
            if (isEmpty())
                return null;
            final int lastIndex = pointsAsList.size() - 1;
            if (startIndex < 0)
                startIndex = 0;
            if (endIndex < 0 || endIndex > lastIndex)
                endIndex = lastIndex + 1;
            if (startIndex >= lastIndex) {
                final Point lastPoint = pointsAsList.get(lastIndex);
                return new PointAndDistance(
                        position, lastPoint, lastIndex, lastIndex + 1,
                        GeoUtils.geoDistanceInMeters(position, lastPoint),
                        0.0);
            }
            final boolean obeyBearing;
            final double forwardBearing;
            if (bearing == null) {
                obeyBearing = false;
                forwardBearing = 0.0d;
            } else {
                obeyBearing = true;
                forwardBearing = bearing;
            }
            PointAndDistance closestPointForward = null;
            int pointBeforeIndexForward = -1;
            int pointAfterIndexForward = -1;
            PointAndDistance closestPointReverse = null;
            int pointBeforeIndexReverse = -1;
            int pointAfterIndexReverse = -1;
            PointAndDistance closestPoint;
            int pointBeforeIndex;
            int pointAfterIndex;
            double distance = Double.MAX_VALUE;
            Point pointA = pointsAsList.get(startIndex);
            for (int indexAfter = startIndex + 1; indexAfter < endIndex; ++indexAfter) {
                final Point pointB = pointsAsList.get(indexAfter);
                final PointAndDistance toLine = findClosestPointOnLine(position, pointA, pointB);
                final double lineBearing = getBearingBeforeIndex(toLine.pointAfterIndex);
                final boolean isGoingOpposite = obeyBearing && isReverseBearing(forwardBearing, lineBearing);
                if (isGoingOpposite) {
                    closestPoint = closestPointReverse;
                    pointBeforeIndex = pointBeforeIndexReverse;
                    pointAfterIndex = pointAfterIndexReverse;
                } else {
                    closestPoint = closestPointForward;
                    pointBeforeIndex = pointBeforeIndexForward;
                    pointAfterIndex = pointAfterIndexForward;
                };
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
                if (isGoingOpposite) {
                    closestPointReverse = closestPoint;
                    pointBeforeIndexReverse = pointBeforeIndex;
                    pointAfterIndexReverse = pointAfterIndex;
                } else {
                    closestPointForward = closestPoint;
                    pointBeforeIndexForward = pointBeforeIndex;
                    pointAfterIndexForward = pointAfterIndex;
                }
                pointA = pointB;
            }

            final boolean useReverse;
            if (closestPointForward == null) {
                useReverse = true;
            } else if (closestPointReverse == null) {
                useReverse = false;
            } else {
                useReverse = closestPointForward.distanceInMeters / closestPointReverse.distanceInMeters > 2.0;
            }
            if (useReverse) {
                closestPoint = closestPointReverse;
                pointBeforeIndex = pointBeforeIndexReverse;
                pointAfterIndex = pointAfterIndexReverse;
            } else {
                closestPoint = closestPointForward;
                pointBeforeIndex = pointBeforeIndexForward;
                pointAfterIndex = pointAfterIndexForward;
            }
            if (closestPoint == null)
                return null;
            return new PointAndDistance(
                    closestPoint.originalPoint,
                    closestPoint.closestPoint,
                    pointBeforeIndex, pointAfterIndex,
                    closestPoint.distanceInMeters,
                    closestPoint.relativePosition);
        }

        public double geoDistanceOnPathInMeters(final Point pointA, final Point pointB, final Double bearingAtPointA) {
            return geoDistanceOnPathInMeters(pointA, findClosestPoint(pointB, null), bearingAtPointA);
        }

        public double geoDistanceOnPathInMeters(final Point pointA, final PointAndDistance closestB, final Double bearingAtPointA) {
            final PointAndDistance closestA = findClosestPoint(pointA, bearingAtPointA);
            return geoDistanceOnPathInMeters(closestA, closestB);
        }

        public double geoDistanceOnPathInMeters(final PointAndDistance closestA, final PointAndDistance closestB) {
            final double directDistance = geoDistanceInMeters(closestA.originalPoint, closestB.originalPoint);
            final double distanceOffPath = closestA.distanceInMeters + closestB.distanceInMeters;
            if (directDistance < distanceOffPath)
                return directDistance;

            return geoDistanceOnPathOnlyInMeters(closestA, closestB, distanceOffPath);
        }

        public double geoDistanceOnPathOnlyInMeters(final PointAndDistance closestA, final PointAndDistance closestB) {
            return geoDistanceOnPathOnlyInMeters(closestA, closestB, 0.0d);
        }

        public double geoDistanceOnPathOnlyInMeters(
                final PointAndDistance closestA,
                final PointAndDistance closestB,
                final double distanceOffPath) {
            final boolean reverse;
            final double distance;
            final int pointBeforeIndex = closestA.pointBeforeIndex;
            final int otherPointBeforeIndex = closestB.pointBeforeIndex;
            final double[] distances = getDistancesFromStart();
            if (pointBeforeIndex == otherPointBeforeIndex) {
                // A and B in same section
                final int pointAfterIndex = closestA.pointAfterIndex;
                double relDiff = closestB.relativePosition - closestA.relativePosition;
                // A before B
                // B before A
                reverse = relDiff < 0;
                if (reverse)
                    relDiff = -relDiff;
                if (pointBeforeIndex < 0) {
                    // section before the path
                    distance = distanceOffPath;
                } else if (pointAfterIndex > distances.length) {
                    // section after the path
                    distance = distanceOffPath;
                } else {
                    // section in the path
                    distance = relDiff * (distances[pointAfterIndex] - distances[pointBeforeIndex]) + distanceOffPath;
                }
            } else {
                final PointAndDistance p1, p2;
                if (otherPointBeforeIndex < pointBeforeIndex) {
                    // B is in section before section of A
                    p1 = closestB;
                    p2 = closestA;
                    reverse = true;
                } else {
                    // A is in section before section of B
                    p1 = closestA;
                    p2 = closestB;
                    reverse = false;
                }
                final int beforeIndex, afterIndex;
                final double d1, d2;
                if (p1.pointBeforeIndex < 0) {
                    // first point is before path
                    beforeIndex = 0;
                    d1 = 0.0d;
                } else {
                    beforeIndex = p1.pointAfterIndex;
                    d1 = (1.0d - p1.relativePosition) * (distances[p1.pointAfterIndex] - distances[p1.pointBeforeIndex]);
                }
                if (p2.pointAfterIndex >= distances.length) {
                    // last point is after path
                    afterIndex = distances.length - 1;
                    d2 = 0.0d;
                } else {
                    afterIndex = p2.pointBeforeIndex;
                    d2 = p2.relativePosition * (distances[p2.pointAfterIndex] - distances[p2.pointBeforeIndex]);
                }
                distance = d1 + (distances[afterIndex] - distances[beforeIndex]) + d2 + distanceOffPath;
            }

            return reverse ? -distance : distance;
        }
    }

    public static class PointAndDistance {
        public final Point originalPoint;
        public final Point closestPoint;
        public final int pointBeforeIndex;
        public final int pointAfterIndex;
        public final double distanceInMeters;
        public final double relativePosition;

        public PointAndDistance(
                final Point originalPoint,
                final Point closestPoint,
                final int pointBeforeIndex,
                final int pointAfterIndex,
                final double distanceInMeters,
                final double relativePosition) {
            this.originalPoint = originalPoint;
            this.closestPoint = closestPoint;
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
            return new PointAndDistance(position, pointA, 0, 0, distanceA, 0.0);
        final double relativePosition =
                (distanceS * distanceS + distanceA * distanceA - distanceB * distanceB) /
                        (2 * distanceS * distanceS);
        if (relativePosition < 0.0)
            return new PointAndDistance(position, pointA, -1, 0, distanceA, relativePosition);
        if (relativePosition > 1.0)
            return new PointAndDistance(position, pointB, 1, 2, distanceB, relativePosition);
        final Point closestPoint = Point.fromDouble(
                pointA.getLatAsDouble()
                        + relativePosition * (pointB.getLatAsDouble() - pointA.getLatAsDouble()),
                pointA.getLonAsDouble()
                        + relativePosition * (pointB.getLonAsDouble() - pointA.getLonAsDouble()));
        return new PointAndDistance(
                position, closestPoint,
                0, 1,
                geoDistanceInMeters(position, closestPoint),
                relativePosition);
    }
}
