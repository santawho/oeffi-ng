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

import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import de.schildbach.oeffi.Application;
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
            return getLocationFromGmapsLongUrl(URLDecoder.decode(location, StandardCharsets.UTF_8.name()));
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
        final Application application = Application.getInstance();
        final Uri contentUri = application.getSharedFileContentUri(kmlFile);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("kml");
        final Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(contentUri, mimeType)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

// THE REAL REASON FOR OSMAND NOT WORKING IS: THEY CANNOT HANDLE KML AND REQUIRE TO USE GPX !!!
///  NOTE: OSMAND seems to support a tag <osmand:color> inside GPX. No other app probably ...
//        // grant permisions for all apps that can handle given intent
//        // see: https://stackoverflow.com/questions/45541013/opening-gpx-file-in-osmand-from-another-application
//        // OSMAND, for example, does not open the KML when just adding FLAG_GRANT_READ_URI_PERMISSION to the intent
//        // requires in manifest:
//        //     <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" tools:ignore="QueryAllPackagesPermission" />
//        List<ResolveInfo> resInfoList = application.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_ALL);
//        for (ResolveInfo resolveInfo : resInfoList) {
//            String packageName = resolveInfo.activityInfo.packageName;
//            application.grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
//        }

        return intent;
    }
}
