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

package de.schildbach.oeffi.plans;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.URLs;
import de.schildbach.oeffi.util.Downloader;
import de.schildbach.oeffi.util.GeoUtils;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Point;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public class PlanContentProvider extends ContentProvider {
    public static Uri CONTENT_URI() {
        return Uri.parse("content://" + Application.getApplicationId() + ".plans");
    }

    public static final String KEY_PLAN_ID = "plan_id";
    public static final String KEY_PLAN_NAME = "plan_name";
    public static final String KEY_PLAN_LAT = "plan_lat";
    public static final String KEY_PLAN_LON = "plan_lon";
    public static final String KEY_PLAN_VALID_FROM = "plan_valid_from";
    public static final String KEY_PLAN_DISCLAIMER = "plan_disclaimer";
    public static final String KEY_PLAN_REMOTE_URL = "plan_remote_url";
    public static final String KEY_PLAN_NETWORK_LOGO = "plan_network_logo";

    public static final String KEY_STATION_NETWORK = "station_network";
    public static final String KEY_STATION_ID = "station_id";
    public static final String KEY_STATION_LABEL = "station_label";
    public static final String KEY_STATION_PLAN_ID = "station_plan_id";
    public static final String KEY_STATION_X = "station_x";
    public static final String KEY_STATION_Y = "station_y";

    private Application application;
    private Downloader downloader;

    private static final Logger log = LoggerFactory.getLogger(PlanContentProvider.class);

    public static Uri planUri(final String planId) {
        return CONTENT_URI().buildUpon().appendPath("plan").appendPath(planId).build();
    }

    public static Uri stationsUri(final String planId) {
        return planUri(planId).buildUpon().appendPath("stations").build();
    }

    public static Uri stationsUri(final NetworkId network, final String localId) {
        return CONTENT_URI().buildUpon().appendPath("stations").appendPath(network.name().toLowerCase(Locale.US))
                .appendPath(localId).build();
    }

    @Override
    public boolean onCreate() {
        this.application = (Application) getContext();
        downloader = new Downloader(application.getCacheDir());
        return true;
    }

    @Override
    public String getType(final Uri uri) {
        return null;
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs,
            final String sortOrder) {
        final FutureCallback<Integer> notifyChangeCallback = new FutureCallback<Integer>() {
            public void onSuccess(final @Nullable Integer status) {
                if (status == HttpURLConnection.HTTP_OK)
                    getContext().getContentResolver().notifyChange(uri, null);
            }

            public void onFailure(final Throwable t) {
            }
        };

        final File indexFile = new File(getContext().getFilesDir(), Constants.PLAN_INDEX_FILENAME);
        final HttpUrl remoteIndexUrl = URLs.getPlansBaseUrl().newBuilder()
                .addPathSegment(Constants.PLAN_INDEX_FILENAME).build();
        final ListenableFuture<Integer> download = downloader.download(application.okHttpClient(), remoteIndexUrl, indexFile);
        Futures.addCallback(download, notifyChangeCallback, MoreExecutors.directExecutor());

        final File stationsFile = new File(getContext().getFilesDir(), Constants.PLAN_STATIONS_FILENAME);
        final HttpUrl remoteStationsUrl = URLs.getPlansBaseUrl().newBuilder()
                .addPathSegment(Constants.PLAN_STATIONS_FILENAME + ".bz2").build();
        final ListenableFuture<Integer> stationsDownload = downloader.download(application.okHttpClient(), remoteStationsUrl, stationsFile, true);
        Futures.addCallback(stationsDownload, notifyChangeCallback, MoreExecutors.directExecutor());

        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() <= 2) {
            String q = null;
            String id = null;
            if (pathSegments.size() == 2 && pathSegments.get(0).equals("plan"))
                id = pathSegments.get(1).trim();
            else if (pathSegments.size() == 2 && pathSegments.get(0).equals(SearchManager.SUGGEST_URI_PATH_QUERY))
                q = pathSegments.get(1).trim().toLowerCase(Locale.ENGLISH);
            else if (pathSegments.isEmpty())
                id = null;
            else
                throw new IllegalArgumentException("Bad path: " + uri);

            final Cursor cursor = readIndexIntoCursor(indexFile, id, q);
            if (sortOrder != null) {
                final String[] latLon = sortOrder.split(",");
                final double lat = Double.parseDouble(latLon[0]);
                final double lon = Double.parseDouble(latLon[1]);
                return new DistanceSortingCursorWrapper(cursor, lat, lon);
            } else {
                return cursor;
            }
        } else if (pathSegments.size() == 3) {
            if (pathSegments.get(0).equals("plan") && pathSegments.get(2).equals("stations")) {
                final String planId = pathSegments.get(1).trim();
                return readStationsIntoCursor(stationsFile, planId, null, null);
            } else if (pathSegments.get(0).equals("stations")) {
                final String network = pathSegments.get(1).trim();
                final String localId = pathSegments.get(2).trim();
                return readStationsIntoCursor(stationsFile, null, network, localId);
            }
        }
        throw new IllegalArgumentException("Bad path: " + uri);
    }

    private Cursor readIndexIntoCursor(final File indexFile, @Nullable final String idFilter,
            @Nullable final String query) {
        if (indexFile.exists()) {
            try {
                return readIndexIntoCursor(new FileInputStream(indexFile), idFilter, query);
            } catch (final IOException | NumberFormatException x) {
                log.warn("Could not read " + indexFile + ", deleting.", x);
                Downloader.deleteDownload(indexFile);
            }
        }

        try {
            return readIndexIntoCursor(getContext().getAssets().open(Constants.PLAN_INDEX_FILENAME), idFilter, query);
        } catch (final IOException | NumberFormatException x) {
            throw new RuntimeException("Fatal problem reading asset " + Constants.PLAN_INDEX_FILENAME, x);
        }
    }

    private Cursor readIndexIntoCursor(final InputStream is, @Nullable final String idFilter,
            @Nullable final String query) throws IOException, NumberFormatException {

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            final MatrixCursor cursor = new MatrixCursor(
                    new String[] { BaseColumns._ID, KEY_PLAN_ID, KEY_PLAN_NAME, KEY_PLAN_LAT, KEY_PLAN_LON,
                            KEY_PLAN_VALID_FROM, KEY_PLAN_DISCLAIMER, KEY_PLAN_REMOTE_URL, KEY_PLAN_NETWORK_LOGO });

            final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#')
                    continue;

                final String[] fields = line.split("\\|");
                final int numFields = fields.length;

                if (numFields < 4)
                    throw new IOException("bad line: '" + line + "', " + numFields + " fields");

                final String planId = fields[0];
                final int rowId = planId.hashCode(); // FIXME colliding hashcodes
                final String[] coords = fields[1].split(",");
                final Point p = Point.fromDouble(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
                final Date planValidFrom = parse(fields[2], dateFormat);
                final String planName = fields[3];
                final String planDisclaimer = numFields > 4 ? Strings.emptyToNull(fields[4]) : null;
                final String planUrl = numFields > 5 ? Strings.emptyToNull(fields[5]) : null;
                final String planNetworkLogo = numFields > 6 ? Strings.emptyToNull(fields[6]) : null;

                boolean filterMatch = true;
                if (query != null && !planName.toLowerCase(Constants.DEFAULT_LOCALE).contains(query)
                        && !(planDisclaimer != null
                                && planDisclaimer.toLowerCase(Constants.DEFAULT_LOCALE).contains(query)))
                    filterMatch = false;
                if (idFilter != null && !planId.equals(idFilter))
                    filterMatch = false;

                if (filterMatch) {
                    cursor.newRow().add(rowId).add(planId).add(planName).add(p.getLatAs1E6()).add(p.getLonAs1E6())
                            .add(planValidFrom != null ? planValidFrom.getTime() : 0).add(planDisclaimer).add(planUrl)
                            .add(planNetworkLogo);
                }
            }

            return cursor;
        }
    }

    private Cursor readStationsIntoCursor(final File stationsFile, @Nullable final String planIdFilter,
            @Nullable final String networkFilter, @Nullable final String localIdFilter) {
        if (stationsFile.exists()) {
            try {
                return readStationsIntoCursor(new FileInputStream(stationsFile), planIdFilter, networkFilter,
                        localIdFilter);
            } catch (final IOException | NumberFormatException x) {
                log.warn("Could not read " + stationsFile + ", deleting.", x);
                Downloader.deleteDownload(stationsFile);
            }
        }

        return null;
    }

    private Cursor readStationsIntoCursor(final InputStream is, @Nullable final String planIdFilter,
            @Nullable final String networkFilter, @Nullable final String localIdFilter)
            throws IOException, NumberFormatException {
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            final MatrixCursor cursor = new MatrixCursor(new String[] { BaseColumns._ID, KEY_STATION_NETWORK,
                    KEY_STATION_ID, KEY_STATION_LABEL, KEY_STATION_PLAN_ID, KEY_STATION_X, KEY_STATION_Y });

            float xScaleFactor = 1, yScaleFactor = 1;
            int xOffset = 0, yOffset = 0;
            String line = null;
            while (true) {
                line = reader.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#')
                    continue;
                if (line.charAt(0) == '!') {
                    if (line.startsWith("!transform:")) {
                        xScaleFactor = 1;
                        yScaleFactor = 1;
                        xOffset = 0;
                        yOffset = 0;
                        final String params = line.substring(11).trim();
                        if (!params.isEmpty()) {
                            final Iterator<String> i = Splitter.on(',').trimResults().split(params).iterator();
                            if (i.hasNext())
                                xScaleFactor = Float.parseFloat(i.next());
                            if (i.hasNext())
                                yScaleFactor = Float.parseFloat(i.next());
                            if (i.hasNext())
                                xOffset = Integer.parseInt(i.next());
                            if (i.hasNext())
                                yOffset = Integer.parseInt(i.next());
                            if (i.hasNext())
                                log.info("Ignoring some transform parameters in: {}", line);
                        }
                    } else {
                        log.info("Ignoring command: {}", line);
                    }
                } else {
                    final Iterator<String> i = Splitter.on('|').trimResults().split(line).iterator();
                    final String network = Strings.emptyToNull(i.next());
                    final String localId = Strings.emptyToNull(i.next());
                    final String label = Strings.emptyToNull(i.next());
                    final String planId = checkNotNull(Strings.emptyToNull(i.next()));
                    if ((planIdFilter == null || planIdFilter.equals(planId))
                            && (networkFilter == null || networkFilter.equals(network))
                            && (localIdFilter == null || localIdFilter.equals(localId))) {
                        final long rowId = network != null && localId != null ? Objects.hash(network, localId)
                                : Objects.hash(label);
                        final int x, y;
                        if (i.hasNext()) {
                            x = (int) Math.round(Double.parseDouble(i.next()) / xScaleFactor) + xOffset;
                            y = (int) Math.round(Double.parseDouble(i.next()) / yScaleFactor) + yOffset;
                        } else {
                            x = 0;
                            y = 0;
                        }
                        cursor.newRow().add(rowId).add(network).add(localId).add(label).add(planId).add(x).add(y);
                    }
                }
            }

            return cursor;
        }
    }

    private static Date parse(String string, final DateFormat dateFormat) throws IOException {
        if (string.length() == 0)
            return null;
        else if (string.length() == 4)
            string += "-01-01";
        else if (string.length() == 7)
            string += "-01";

        try {
            return dateFormat.parse(string);
        } catch (final ParseException x) {
            x.printStackTrace();
            throw new IOException(x.toString());
        }
    }

    private static class DistanceSortingCursorWrapper extends CursorWrapper {
        private final Cursor cursor;
        private final int size;
        private List<Integer> mapping;
        private int pos = -1;

        public DistanceSortingCursorWrapper(final Cursor cursor, final double lat, final double lon) {
            super(cursor);

            this.cursor = cursor;
            this.size = cursor.getCount();

            mapping = new LinkedList<>();
            for (int i = 0; i < size; i++)
                mapping.add(i);

            final int latColumn = cursor.getColumnIndexOrThrow(KEY_PLAN_LAT);
            final int lonColumn = cursor.getColumnIndexOrThrow(KEY_PLAN_LON);

            final Comparator<Integer> comparator = new Comparator<Integer>() {
                public int compare(final Integer index1, final Integer index2) {
                    cursor.moveToPosition(index1);
                    final Point p1 = Point.from1E6(cursor.getInt(latColumn), cursor.getInt(lonColumn));
                    final float dist1 = GeoUtils.distanceBetween(lat, lon, p1).distanceInMeters;

                    cursor.moveToPosition(index2);
                    final Point p2 = Point.from1E6(cursor.getInt(latColumn), cursor.getInt(lonColumn));
                    final float dist2 = GeoUtils.distanceBetween(lat, lon, p2).distanceInMeters;
                    return Float.compare(dist1, dist2);
                }
            };

            try {
                Collections.sort(mapping, comparator);
            } catch (final IllegalArgumentException x) {
                log.warn("Failed sorting " + size + " cursor entries by distance to " + lat + "," + lon, x);
                // we boldly continue, hoping that the list isn't corrupt
            }
        }

        @Override
        public int getPosition() {
            return pos;
        }

        @Override
        public final boolean moveToPosition(final int position) {
            if (position < 0) {
                pos = -1;
                cursor.moveToPosition(-1);
                return false;
            } else if (position >= size) {
                pos = size;
                cursor.moveToPosition(size);
                return false;
            } else {
                pos = position;
                cursor.moveToPosition(mapping.get(pos));
                return true;
            }
        }

        @Override
        public final boolean move(final int offset) {
            return moveToPosition(pos + offset);
        }

        @Override
        public final boolean moveToFirst() {
            return moveToPosition(0);
        }

        @Override
        public final boolean moveToLast() {
            return moveToPosition(size - 1);
        }

        @Override
        public final boolean moveToNext() {
            return moveToPosition(pos + 1);
        }

        @Override
        public final boolean moveToPrevious() {
            return moveToPosition(pos - 1);
        }
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
