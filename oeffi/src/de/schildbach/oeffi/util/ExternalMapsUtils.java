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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.preference.MapsFragment;
import de.schildbach.oeffi.util.geofiles.GeoFileProducer;
import de.schildbach.oeffi.util.geofiles.GpxProducer;
import de.schildbach.oeffi.util.geofiles.KmlProducer;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Trip;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ExternalMapsUtils {
    private static final Logger log = LoggerFactory.getLogger(ExternalMapsUtils.class);

    public static final String GMAPS_SHORT_LOCATION_URL_PREFIX = "https://maps.app.goo.gl/";

    public static Location resolveLocationUrl(final String gmapsShortUrl) {
        try (final Response response = new OkHttpClient.Builder().followRedirects(false).build()
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
        for (final String dataElement : dataElements) {
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
                return LocationUtils.locationFromCoord(point);
            return new Location(LocationType.ADDRESS, null, point, null, placeName);
        } catch (final NumberFormatException nfe) {
            return null;
        }
    }

    public static Intent getOpenGeoFileIntent(final File geoFile) {
        final Application application = Application.getInstance();
        final Uri contentUri = application.getSharedFileContentUri(geoFile);
        final String fileName = geoFile.getName();
        final int lastDot = fileName.lastIndexOf('.');
        final String ext = lastDot < 0 ? "" : fileName.substring(lastDot + 1);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mimeType == null)
            mimeType = "application/" + ext + "+xml";
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

    public static void openPointInPreselectedExternalMapsApp(
            final Activity context,
            final Location location) {
        if (location == null)
            return;
        final Intent intent = createIntentToOpenPointInExternalMapsApp(location);
        if (intent != null) {
            final AppChooser.ComponentInfo componentInfo = MapsFragment.getPreselectedApp(false);
            if (componentInfo != null)
                intent.setComponent(componentInfo.getComponentName());
            context.startActivity(intent);
        }
    }

    public static void openPointByChoosingExternalMapsApp(
            final Activity context,
            final Location location) {
        if (location == null)
            return;
        chooseActivityToOpenPoint(context, componentInfo -> {
            final Intent intent = createIntentToOpenPointInExternalMapsApp(location);
            if (intent != null) {
                intent.setComponent(componentInfo.getComponentName());
                context.startActivity(intent);
            }
        });
    }

    public static Intent createIntentToOpenPointInExternalMapsApp(
            final Location location) {
        return createIntentToOpenPointInExternalMapsApp(
                location.getLatAsDouble(), location.getLonAsDouble(),
                location.name);
    }

    public static Intent createIntentToOpenPointInExternalMapsApp(
            final double lat, final double lon,
            final String name) {
        return new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(Locale.ENGLISH, "geo:%.6f,%.6f?q=%.6f,%.6f%s", lat, lon, lat, lon,
                        name != null ? '(' + URLEncoder.encode(name.replaceAll("[()]", "")) + ')' : "")));
    }

    public static void openTripInPreselectedExternalMapsApp(
            final Activity context,
            final Trip trip) {
        if (trip == null)
            return;
        final AppChooser.ComponentInfo componentInfo = MapsFragment.getPreselectedApp(true);
        if (componentInfo == null) {
            openTripByChoosingExternalMapsApp(context, trip);
        } else {
            final GeoFileProducer producer = componentInfo.typeIndex == 1
                    ? new KmlProducer()
                    : new GpxProducer();
            producer.setApplication(Application.getInstance());
            final Intent intent = createIntentToOpenTripInExternalMapsApp(trip, producer);
            if (intent != null) {
                intent.setComponent(componentInfo.getComponentName());
                context.startActivity(intent);
            }
        }
    }

    public static void openTripByChoosingExternalMapsApp(
            final Activity context,
            final Trip trip) {
        if (trip == null)
            return;
        chooseActivityToOpenTrip(context, componentInfo -> {
            final GeoFileProducer producer = componentInfo.typeIndex == 1
                    ? new KmlProducer()
                    : new GpxProducer();
            producer.setApplication(Application.getInstance());
            final Intent intent = createIntentToOpenTripInExternalMapsApp(trip, producer);
            if (intent != null) {
                intent.setComponent(componentInfo.getComponentName());
                context.startActivity(intent);
            }
        });
    }

    public static Intent createIntentToOpenTripInExternalMapsApp(
            final Trip trip,
            final GeoFileProducer producer) {
        String extension = "?";
        try {
            extension = producer.getFilenameExtension();
            final File geoFile = new File(Application.getInstance().getShareDir(),
                    "shareroute." + extension);
            producer.writeTrip(trip, geoFile);
            return getOpenGeoFileIntent(geoFile);
        } catch (final Exception e) {
            log.error("cannot create shared {} file", extension, e);
            return null;
        }
    }

    public static Intent createChooserToOpenTripInExternalMapsApp(
            final Trip trip,
            final GeoFileProducer producer,
            final String title) {
        final Intent intent = createIntentToOpenTripInExternalMapsApp(trip, producer);
        if (intent == null)
            return null;
        return Intent.createChooser(intent, title);
    }

    public static void chooseActivityToOpenPoint(
            final Activity context,
            final Consumer<AppChooser.ComponentInfo> resultConsumer) {
        final Intent intent = createIntentToOpenPointInExternalMapsApp(0, 0, null);
        if (intent == null)
            return;
        AppChooser.chooseActivityForIntent(
                context,
                new AppChooser.IntentAndDescription[] {
                        new AppChooser.IntentAndDescription(intent),
                },
                context.getString(R.string.user_interface_map_app_content_type_points),
                resultConsumer);
    }

    public static void chooseActivityToOpenTrip(
            final Activity context,
            final Consumer<AppChooser.ComponentInfo> resultConsumer) {
        final Intent kmlIntent = createIntentToOpenTripInExternalMapsApp(null, new KmlProducer());
        final Intent gpxIntent = createIntentToOpenTripInExternalMapsApp(null, new GpxProducer());
        if (kmlIntent == null && gpxIntent == null)
            return;
        AppChooser.chooseActivityForIntent(
                context,
                new AppChooser.IntentAndDescription[] {
                        new AppChooser.IntentAndDescription(gpxIntent, "GPX"),
                        new AppChooser.IntentAndDescription(kmlIntent, "KML"),
                },
                context.getString(R.string.user_interface_map_app_content_type_trips),
                resultConsumer);
    }
}
