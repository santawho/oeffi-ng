package de.schildbach.oeffi.util;

import android.net.Uri;

import com.google.common.base.Charsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.List;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GoogleMapsUtils {
    private static final Logger log = LoggerFactory.getLogger(GoogleMapsUtils.class);

    public static final String GMAPS_SHORT_LOCATION_URL_PREFIX = "https://maps.app.goo.gl/";

    public static Location resolveLocationUrl(final String gmapsShortUrl) {
        try (Response response = new OkHttpClient.Builder().followRedirects(false).build()
                .newCall(new Request.Builder().head().url(gmapsShortUrl).build())
                .execute()) {
            final int code = response.code();
            if (code != 302) {
                log.error("cannot HEAD {}: code {}", gmapsShortUrl, code);
                return null;
            }
            final String location = response.header("Location");
            if (location == null)
                return null;
            return getLocationFromGmapsLongUrl(URLDecoder.decode(location, Charsets.UTF_8.name()));
        } catch (IOException e) {
            log.error("cannot HEAD {}: {}", gmapsShortUrl, e.getMessage());
            return null;
        }
    }

    public static Location getLocationFromGmapsLongUrl(final String gmapsLongUrl) {
        final Uri uri = Uri.parse(gmapsLongUrl);
        final List<String> pathSegments = uri.getPathSegments();
        String placeName = null;
        if (pathSegments.isEmpty())
            return null;
        if (pathSegments.size() >= 3 && pathSegments.get(0).equals("maps") && pathSegments.get(1).equals("place")) {
            placeName = pathSegments.get(2);
        }
        final String lastSegment = pathSegments.get(pathSegments.size() - 1);
        if (!lastSegment.startsWith("data="))
            return null;
        final String dataValue = lastSegment.substring(5);
        final String[] dataElements = dataValue.split("!");
        String lon = null, lat = null;
        for (String dataElement : dataElements) {
            if (dataElement.length() < 2)
                continue;
            final String id = dataElement.substring(0, 2);
            final String value = dataElement.substring(2);
            if ("3d".equals(id)) {
                lat = value;
            } else if ("4d".equals(id)) {
                lon = value;
            }
        }
        if (lon == null || lat == null) {
            if (placeName == null)
                return null;
            return new Location(LocationType.ANY, null, null, placeName);
        }
        try {
            final Point point = Point.fromDouble(Double.parseDouble(lat), Double.parseDouble(lon));
            if (placeName == null)
                return Location.coord(point);
            return new Location(LocationType.ADDRESS, null, point, null, placeName);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }
}
