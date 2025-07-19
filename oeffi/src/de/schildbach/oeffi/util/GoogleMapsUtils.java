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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import com.google.common.base.Charsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import de.schildbach.oeffi.Application;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;
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

    public static Intent getOpenKmlIntent(final File kmlFile) {
        final Uri contentUri = Application.getInstance().getSharedFileContentUri(kmlFile);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("kml");
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(contentUri, mimeType)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    public static File writeTripAsKml(final Trip trip, final File kmlFile) throws IOException, XmlPullParserException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final XmlSerializer xs = XmlPullParserFactory.newInstance().newSerializer();
        xs.setOutput(bos, StandardCharsets.UTF_8.name());
        xs.startDocument(null, null);

        xs.startTag(null, "kml");
        xs.startTag(null, "Document");

        xmlTextNode(xs, "name", "kmlFile");

        xs.startTag(null, "Style");
        xs.attribute(null, "id", "tripPoly");
        xs.startTag(null, "LineStyle");
        xmlTextNode(xs, "width", "10");
        xmlTextNode(xs, "color", "7dff00ff");
        xmlTextNode(xs, "colorMode", "random");
        xs.endTag(null, "LineStyle");
        xs.endTag(null, "Style");

        xs.startTag(null, "Folder");
        xmlTextNode(xs, "name", "tripName");
        xmlTextNode(xs, "visibility", "1");
        xmlTextNode(xs, "description", "trip description");

        xs.startTag(null, "Placemark");
        xmlTextNode(xs, "name", "place name");
        xmlTextNode(xs, "visibility", "1");
        xmlTextNode(xs, "styleUrl", "#tripPoly");

        xs.startTag(null, "LineString");
        xmlTextNode(xs, "extrude", "1");
        xmlTextNode(xs, "altitudeMode", "relativeToGround");
        xs.startTag(null, "coordinates");

        for (Trip.Leg iLeg : trip.legs) {
            if (!(iLeg instanceof Trip.Public))
                continue;

            final Trip.Public leg = (Trip.Public) iLeg;
            xs.text(kmlLocationForStop(leg.departureStop));
            final List<Stop> intermediateStops = leg.intermediateStops;
            if (intermediateStops != null)
                for (Stop stop : intermediateStops)
                    xs.text(kmlLocationForStop(stop));
            xs.text(kmlLocationForStop(leg.arrivalStop));
        }

        xs.endTag(null, "coordinates");
        xs.endTag(null, "LineString");
        xs.endTag(null, "Placemark");
        xs.endTag(null, "Folder");

        xs.endTag(null, "Document");
        xs.endTag(null, "kml");

        xs.endDocument();
        xs.flush();
        bos.close();

        final String xmlString = bos.toString(StandardCharsets.UTF_8.name());

        final FileOutputStream fos = new FileOutputStream(kmlFile);
        fos.write(xmlString.getBytes(StandardCharsets.UTF_8));
        fos.close();

        return kmlFile;
    }

    private static void xmlTextNode(final XmlSerializer xs, final String name, final String value) throws IOException {
        xs.startTag(null, name);
        xs.text(value);
        xs.endTag(null, name);
    }

    @SuppressLint("DefaultLocale")
    private static String kmlLocationForStop(final Stop stop) {
        final Location location = stop.location;
        return String.format(Locale.US, "%f,%f,17\n", location.getLonAsDouble(), location.getLatAsDouble());
    }
}
