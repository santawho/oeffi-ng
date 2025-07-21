package de.schildbach.oeffi.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

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
        checkNotNull(start);
        return distanceBetween(start.getLatAsDouble(), start.getLonAsDouble(), endLatitude, endLongitude);
    }

    public static DistanceResult distanceBetween(
            @FloatRange(from = -90.0, to = 90.0) double startLatitude,
            @FloatRange(from = -180.0, to = 180.0) double startLongitude,
            final Point end) {
        checkNotNull(end);
        return distanceBetween(startLatitude, startLongitude, end.getLatAsDouble(), end.getLonAsDouble());
    }

    public static DistanceResult distanceBetween(
            final Point start,
            final Point end) {
        checkNotNull(start);
        return distanceBetween(start.getLatAsDouble(), start.getLonAsDouble(), end);
    }

    public static DistanceResult distanceBetween(final Location start, final Point end) {
        checkNotNull(start);
        return distanceBetween(start.getLatitude(), start.getLongitude(), end);
    }
}
