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

package de.schildbach.oeffi.util;

import static java.util.Objects.requireNonNull;

import android.location.Location;

import androidx.annotation.FloatRange;

import de.schildbach.pte.dto.Point;

public class GeoUtils {
    public static class DistanceResult {
        public float distanceInMeters;
        public float initialBearing;
        public float finalBearing;
    }

    public static DistanceResult distanceBetween(
            @FloatRange(from = -90.0, to = 90.0) double startLatitude,
            @FloatRange(from = -180.0, to = 180.0) double startLongitude,
            @FloatRange(from = -90.0, to = 90.0) double endLatitude,
            @FloatRange(from = -180.0, to = 180.0)  double endLongitude) {
        final float[] results = new float[3];
        android.location.Location.distanceBetween(startLatitude, startLongitude, endLatitude, endLongitude, results);
        final DistanceResult distanceResult = new DistanceResult();
        distanceResult.distanceInMeters = results[0];
        distanceResult.initialBearing = results[1];
        distanceResult.finalBearing = results[2];
        return distanceResult;
    }

    public static DistanceResult distanceBetween(
            final Point start,
            @FloatRange(from = -90.0, to = 90.0) double endLatitude,
            @FloatRange(from = -180.0, to = 180.0)  double endLongitude) {
        requireNonNull(start);
        return distanceBetween(start.getLatAsDouble(), start.getLonAsDouble(), endLatitude, endLongitude);
    }

    public static DistanceResult distanceBetween(
            @FloatRange(from = -90.0, to = 90.0) double startLatitude,
            @FloatRange(from = -180.0, to = 180.0) double startLongitude,
            final Point end) {
        requireNonNull(end);
        return distanceBetween(startLatitude, startLongitude, end.getLatAsDouble(), end.getLonAsDouble());
    }

    public static DistanceResult distanceBetween(
            final Point start,
            final Point end) {
        requireNonNull(start);
        return distanceBetween(start.getLatAsDouble(), start.getLonAsDouble(), end);
    }

    public static DistanceResult distanceBetween(final Location start, final Point end) {
        requireNonNull(start);
        return distanceBetween(start.getLatitude(), start.getLongitude(), end);
    }
}
