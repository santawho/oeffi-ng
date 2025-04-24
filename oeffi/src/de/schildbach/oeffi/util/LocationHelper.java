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

import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import de.schildbach.pte.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LocationHelper {
    public interface Callback {
        void onLocationStart(String provider);

        void onLocationStop(boolean timedOut);

        void onLocation(Point here);

        void onLocationFail();
    }

    private final LocationManager manager;
    private final Handler handler = new Handler();
    private LocationListener listener;
    private Callback callback;
    private long startTime;

    private static final Logger log = LoggerFactory.getLogger(LocationHelper.class);

    public LocationHelper(final LocationManager manager, final Callback callback) {
        this.manager = manager;
        this.callback = callback;
    }

    public void startLocation(final Criteria criteria, final boolean includeLastKnown, final long timeout) {
        if (isRunning())
            throw new IllegalStateException();

        startTime = System.currentTimeMillis();

        final String provider = manager.getBestProvider(criteria, true);

        if ("static".equals(provider)) {
            // special handling for Google TV
            final Location location = manager.getLastKnownLocation(provider);
            if (location.getLatitude() != 0 || location.getLongitude() != 0) {
                log.info("LocationHelper returned static {}", location);
                callback.onLocation(locationToPoint(location));
            }
        } else if (provider != null) {
            callback.onLocationStart(provider);

            if (includeLastKnown) {
                final Location location = manager.getLastKnownLocation(provider);
                if (location != null && (location.getLatitude() != 0 || location.getLongitude() != 0)) {
                    log.info("LocationHelper returned last known {}", location);
                    callback.onLocation(locationToPoint(location));
                }
            }

            listener = new LocationListener() {
                public void onLocationChanged(final Location location) {
                    // early remove listener, just to make sure no location updates pile up while callback is
                    // running
                    manager.removeUpdates(this);

                    log.info("LocationHelper took {} ms, returned {}", System.currentTimeMillis() - startTime,
                            location);
                    callback.onLocation(locationToPoint(location));

                    stop(false);
                }

                public void onProviderDisabled(final String provider) {
                }

                public void onProviderEnabled(final String provider) {
                }

                public void onStatusChanged(final String provider, final int status, final Bundle extras) {
                }
            };

            manager.requestLocationUpdates(provider, 100, 0, listener);

            if (timeout > 0) {
                handler.postDelayed(() -> {
                    log.info("LocationHelper timed out");
                    stop(true);
                }, timeout);
            }
        } else {
            log.info("LocationHelper failed");
            callback.onLocationFail();
        }
    }

    public void stop() {
        stop(false);
    }

    private void stop(final boolean timedOut) {
        handler.removeCallbacksAndMessages(null);

        if (listener != null) {
            manager.removeUpdates(listener);
            listener = null;

            callback.onLocationStop(timedOut);
        }
    }

    public boolean isRunning() {
        return listener != null;
    }

    public static Point locationToPoint(final Location location) {
        return Point.fromDouble(location.getLatitude(), location.getLongitude());
    }
}
