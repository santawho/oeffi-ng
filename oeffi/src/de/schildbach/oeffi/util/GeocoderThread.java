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

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import com.google.common.base.Strings;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class GeocoderThread extends Thread {
    public interface Callback {
        void onGeocoderResult(Address address);

        void onGeocoderFail(Exception exception);
    }

    private final Geocoder geocoder;
    private final double latitude, longitude;
    private final Handler callbackHandler;
    private final Callback callback;
    private long startTime;

    private static final Logger log = LoggerFactory.getLogger(GeocoderThread.class);

    public GeocoderThread(final Context context, final Point coord, final Callback callback) {
        this(context, coord.getLatAsDouble(), coord.getLonAsDouble(), callback);
    }

    public GeocoderThread(final Context context, final double latitude, final double longitude,
            final Callback callback) {
        this.geocoder = new Geocoder(checkNotNull(context), Locale.GERMANY);
        checkArgument(latitude > -90);
        checkArgument(latitude < 90);
        this.latitude = latitude;
        checkArgument(longitude > -180);
        checkArgument(longitude < 180);
        this.longitude = longitude;
        this.callback = checkNotNull(callback);

        callbackHandler = new Handler(Looper.myLooper());

        start();
    }

    @Override
    public void run() {
        startTime = System.currentTimeMillis();

        try {
            if (Geocoder.isPresent()) {
                final List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                if (addresses.size() >= 1) {
                    final Address address = addresses.get(0);
                    log.info("Geocoder took {} ms, for {}/{} returned {}", System.currentTimeMillis() - startTime,
                            latitude, longitude, address);
                    onResult(address);
                } else {
                    final String msg = "Geocoder returned no addresses";
                    log.info(msg);
                    onFail(new IllegalStateException(msg));
                }
            } else {
                final String msg = "Geocoder not implemented";
                log.info(msg);
                onFail(new UnsupportedOperationException(msg));
            }
        } catch (final IOException x) {
            log.info("Geocoder failed: {}",x.getMessage());
            onFail(x);
        }
    }

    private void onResult(final Address address) {
        callbackHandler.post(() -> callback.onGeocoderResult(address));
    }

    private void onFail(final Exception exception) {
        callbackHandler.post(() -> callback.onGeocoderFail(exception));
    }

    public static Location addressToLocation(final Address address) {
        final Point coord;
        if (address.hasLatitude() && address.hasLongitude())
            coord = Point.fromDouble(address.getLatitude(), address.getLongitude());
        else
            coord = null;

        final int maxAddressLineIndex = address.getMaxAddressLineIndex();
        final Location location;
        if (Strings.emptyToNull(address.getFeatureName()) != null && Strings.emptyToNull(address.getLocality()) != null
                && Strings.emptyToNull(address.getPostalCode()) != null) {
            final String thoroughfare = Strings.emptyToNull(address.getThoroughfare());
            location = new Location(LocationType.ADDRESS, null, coord,
                    Stream.of(address.getPostalCode(), address.getLocality()).filter(Objects::nonNull).collect(Collectors.joining(" ")),
                    Stream.of(thoroughfare, address.getFeatureName()).filter(Objects::nonNull).collect(Collectors.joining(" ")));
        } else if (maxAddressLineIndex >= 2 && Strings.emptyToNull(address.getAddressLine(2)) != null) {
            location = new Location(LocationType.ADDRESS, null, coord, address.getAddressLine(1),
                    address.getAddressLine(0));
        } else {
            location = new Location(LocationType.ADDRESS, null, coord);
        }

        return location;
    }
}
