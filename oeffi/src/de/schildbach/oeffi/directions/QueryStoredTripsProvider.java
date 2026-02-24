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

package de.schildbach.oeffi.directions;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.TripRef;

public class QueryStoredTripsProvider extends ForNetworkContentProvider {
    private static final String DATABASE_TABLE = "query_stored_trips";

    private static Uri CONTENT_URI() {
        return Uri.parse("content://" + Application.getApplicationId() + ".directions." + DATABASE_TABLE);
    };

    public static Uri.Builder CONTENT_URI_BUILDER(final NetworkId network, final String usage) {
        return CONTENT_URI().buildUpon().appendPath(getNetworkKey(network, usage));
    };

    public static final String KEY_ROWID = "_id";
    public static final String KEY_NETWORK = "network";
    public static final String KEY_FROM_TYPE = "from_type";
    public static final String KEY_FROM_ID = "from_id";
    public static final String KEY_FROM_LAT = "from_lat";
    public static final String KEY_FROM_LON = "from_lon";
    public static final String KEY_FROM_PLACE = "from_place";
    public static final String KEY_FROM_NAME = "from_name";
    public static final String KEY_TO_TYPE = "to_type";
    public static final String KEY_TO_ID = "to_id";
    public static final String KEY_TO_LAT = "to_lat";
    public static final String KEY_TO_LON = "to_lon";
    public static final String KEY_TO_PLACE = "to_place";
    public static final String KEY_TO_NAME = "to_name";
    public static final String KEY_VIA_TYPE = "via_type";
    public static final String KEY_VIA_ID = "via_id";
    public static final String KEY_VIA_LAT = "via_lat";
    public static final String KEY_VIA_LON = "via_lon";
    public static final String KEY_VIA_PLACE = "via_place";
    public static final String KEY_VIA_NAME = "via_name";
    public static final String KEY_DEPARTURE_TIME = "departure_time";
    public static final String KEY_DEPARTURE_TIME_OFFSET = "departure_time_offset";
    public static final String KEY_ARRIVAL_TIME = "arrival_time";
    public static final String KEY_ARRIVAL_TIME_OFFSET = "arrival_time_offset";
    public static final String KEY_RELOAD_REQUEST_DATA = "reload_data";
    public static final String KEY_STATE_FLAGS = "state_flags";
    public static final String KEY_TRIP = "trip_data";
    public static final String KEY_TRIP_ID = "trip_id";

    public static final int STATE_FLAG_DONE = 1;

    public static final int TYPE_ANY = 0;
    public static final int TYPE_STATION = 1;
    public static final int TYPE_POI = 2;
    public static final int TYPE_ADDRESS = 3;
    public static final int TYPE_COORD = 4;

    private static Uri tripRowUri(final NetworkId network, final String usage, final long rowId) {
        return CONTENT_URI_BUILDER(network, usage).appendPath(Long.toString(rowId)).build();
    }

    public static Uri put(
            final ContentResolver contentResolver, final NetworkId network, final String usage,
            final Trip trip,
            final QueryTripsRunnable.TripRequestData reloadRequestData,
            final int stateFlags) {
        final String tripId = trip.getUniqueId();
        final Long rowId = getRowId(contentResolver, network, usage, tripId);

        final ContentValues values = new ContentValues();
        final PTDate firstPublicLegDepartureTime = trip.getFirstPublicLegDepartureTime();
        final PTDate lastPublicLegArrivalTime = trip.getLastPublicLegArrivalTime();
        values.put(KEY_DEPARTURE_TIME, firstPublicLegDepartureTime == null ? 0 : firstPublicLegDepartureTime.getTime());
        values.put(KEY_DEPARTURE_TIME_OFFSET, firstPublicLegDepartureTime == null ? 0 : firstPublicLegDepartureTime.getOffset());
        values.put(KEY_ARRIVAL_TIME, lastPublicLegArrivalTime == null ? 0 : lastPublicLegArrivalTime.getTime());
        values.put(KEY_ARRIVAL_TIME_OFFSET, lastPublicLegArrivalTime == null ? 0 : lastPublicLegArrivalTime.getOffset());
        values.put(KEY_TRIP, Objects.serialize(trip));

        final Uri tripsUri;

        if (rowId != null) {
            tripsUri = tripRowUri(network, usage, rowId);
            contentResolver.update(tripsUri, values, null, null);
        } else {
            values.put(KEY_TRIP_ID, tripId);

            final Location from, to, via;
            final TripRef tripRef = trip.tripRef;
            if (tripRef != null) {
                from = tripRef.from;
                to = tripRef.to;
                via = tripRef.via;
            } else {
                from = trip.from;
                to = trip.to;
                via = null;
            }

            values.put(KEY_FROM_TYPE, convert(from.type));
            values.put(KEY_FROM_ID, from.id);
            values.put(KEY_FROM_LAT, from.hasCoord() ? from.getLatAs1E6() : 0);
            values.put(KEY_FROM_LON, from.hasCoord() ? from.getLonAs1E6() : 0);
            values.put(KEY_FROM_PLACE, from.place);
            values.put(KEY_FROM_NAME, from.name);
            values.put(KEY_TO_TYPE, convert(to.type));
            values.put(KEY_TO_ID, to.id);
            values.put(KEY_TO_LAT, to.hasCoord() ? to.getLatAs1E6() : 0);
            values.put(KEY_TO_LON, to.hasCoord() ? to.getLonAs1E6() : 0);
            values.put(KEY_TO_PLACE, to.place);
            values.put(KEY_TO_NAME, to.name);
            values.put(KEY_VIA_TYPE, convert(via == null ? LocationType.ANY : via.type));
            values.put(KEY_VIA_ID, via == null ? null : via.id);
            values.put(KEY_VIA_LAT, via != null && via.hasCoord() ? via.getLatAs1E6() : 0);
            values.put(KEY_VIA_LON, via != null && via.hasCoord() ? via.getLonAs1E6() : 0);
            values.put(KEY_VIA_PLACE, via == null ? null : via.place);
            values.put(KEY_VIA_NAME, via == null ? null : via.name);
            values.put(KEY_RELOAD_REQUEST_DATA, Objects.serialize(reloadRequestData));
            values.put(KEY_STATE_FLAGS, stateFlags);

            final Uri baseUri = CONTENT_URI_BUILDER(network, usage).build();
            tripsUri = contentResolver.insert(baseUri, values);
        }

        return tripsUri;
    }

    public static byte[] getReloadRequestColumnBlob(final Cursor cursor, final int reloadRequestColumnIdx) {
        final byte[] blob = cursor.getBlob(reloadRequestColumnIdx);
        return blob == null || blob.length == 0 ? null : blob;
    }

    public static Long getRowId(
            final ContentResolver contentResolver,
            final NetworkId network, final String usage,
            final String tripId) {
        final StringBuilder selection = new StringBuilder();
        final List<String> selectionArgs = new ArrayList<>();

        selection.append(KEY_TRIP_ID).append("=?");
        selectionArgs.add(tripId);

        try (final Cursor cursor = contentResolver.query(CONTENT_URI_BUILDER(network, usage).build(),
                null, selection.toString(), selectionArgs.toArray(new String[0]), null)) {
            if (cursor != null && cursor.moveToFirst())
                return cursor.getLong(cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_ROWID));
        }
        return null;
    }

    public static int delete(
            final ContentResolver contentResolver,
            final NetworkId network, final String usage,
            final String tripId) {
        final StringBuilder selection = new StringBuilder();
        final List<String> selectionArgs = new ArrayList<>();

        selection.append(KEY_TRIP_ID).append("=?");
        selectionArgs.add(tripId);

        return contentResolver.delete(
                CONTENT_URI_BUILDER(network, usage).build(),
                selection.toString(), selectionArgs.toArray(new String[0]));
    }

    private static int convert(final LocationType type) {
        if (type == LocationType.ANY)
            return TYPE_ANY;
        if (type == LocationType.STATION)
            return TYPE_STATION;
        if (type == LocationType.POI)
            return TYPE_POI;
        if (type == LocationType.ADDRESS)
            return TYPE_ADDRESS;
        if (type == LocationType.COORD)
            return TYPE_COORD;
        throw new IllegalArgumentException("unknown type: " + type);
    }

    public static LocationType convert(final int type) {
        if (type == TYPE_ANY)
            return LocationType.ANY;
        if (type == TYPE_STATION)
            return LocationType.STATION;
        if (type == TYPE_POI)
            return LocationType.POI;
        if (type == TYPE_ADDRESS)
            return LocationType.ADDRESS;
        if (type == TYPE_COORD)
            return LocationType.COORD;
        throw new IllegalArgumentException("unknown type: " + type);
    }

    private QueryTripsHelper helper;

    @Override
    public boolean onCreate() {
        helper = new QueryTripsHelper(getContext());
        return true;
    }

    @Override
    public String getType(final Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() != 1)
            throw new IllegalArgumentException(uri.toString());

        final String network = pathSegments.get(0);
        values.put(KEY_NETWORK, network);

        final long rowId = helper.getWritableDatabase().insertOrThrow(DATABASE_TABLE, null, values);
        final Uri rowUri = CONTENT_URI().buildUpon().appendPath(network).appendPath(Long.toString(rowId)).build();

        getContext().getContentResolver().notifyChange(rowUri, null);

        return rowUri;
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() != 2)
            throw new IllegalArgumentException(uri.toString());

        final String network = pathSegments.get(0);
        final String rowId = pathSegments.get(1);

        final int count = helper.getWritableDatabase().update(DATABASE_TABLE, values, KEY_NETWORK + "='" + network
                + "' AND " + KEY_ROWID + "=" + rowId + (selection != null ? " AND (" + selection + ")" : ""),
                selectionArgs);

        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() < 1)
            throw new IllegalArgumentException(uri.toString());

        final String network = pathSegments.get(0);
        final String rowId = pathSegments.size() >= 2 ? pathSegments.get(1) : null;

        final StringBuilder whereClause = new StringBuilder(KEY_NETWORK + "='" + network + "'");
        if (rowId != null)
            whereClause.append(" AND " + KEY_ROWID + "=" + rowId);
        if (selection != null)
            whereClause.append(" AND (" + selection + ")");
        final int count = helper.getWritableDatabase().delete(DATABASE_TABLE, whereClause.toString(), selectionArgs);

        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs,
            final String sortOrder) {
        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(DATABASE_TABLE);

        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.isEmpty())
            throw new IllegalArgumentException(uri.toString());

        qb.appendWhere(KEY_NETWORK + "=");
        qb.appendWhereEscapeString(pathSegments.get(0));

        if (pathSegments.size() >= 2) {
            qb.appendWhere(" AND " + KEY_ROWID + "=");
            qb.appendWhereEscapeString(pathSegments.get(1));
        }

        final Cursor cursor = qb.query(helper.getReadableDatabase(), projection, selection, selectionArgs, null, null,
                sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    /**
     * Restricted to usage by {@link Application#onCreate()} only.
     */
    public static void deleteTripsForNetwork(final Context context, final NetworkId network, final String usage) {
        final QueryTripsHelper helper = new QueryTripsHelper(context);
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_NETWORK + "=?",
                    new String[] { getNetworkKey(network, usage) });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        helper.close();
    }

    public static void deleteOlderTrips(
            final Context context,
            final NetworkId network, final String usage,
            final long minArrivalTime) {
        final QueryTripsHelper helper = new QueryTripsHelper(context);
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            db.execSQL(
                    "DELETE FROM " + DATABASE_TABLE + " WHERE "
                            + KEY_NETWORK + "=? AND "
                            + KEY_ARRIVAL_TIME + "<?",
                    new String[] {getNetworkKey(network, usage), String.valueOf(minArrivalTime)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        helper.close();
    }

    private static class QueryTripsHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "trips";
        private static final int DATABASE_VERSION = 2;

        private static final String DATABASE_CREATE = "CREATE TABLE " + DATABASE_TABLE + " (" //
                + KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " //
                + KEY_NETWORK + " TEXT NOT NULL, " //
                + KEY_FROM_TYPE + " INTEGER NOT NULL, " //
                + KEY_FROM_ID + " TEXT, " //
                + KEY_FROM_LAT + " INTEGER NOT NULL, " //
                + KEY_FROM_LON + " INTEGER NOT NULL, " //
                + KEY_FROM_PLACE + " TEXT, " //
                + KEY_FROM_NAME + " TEXT, " //
                + KEY_TO_TYPE + " INTEGER NOT NULL, " //
                + KEY_TO_ID + " TEXT, " //
                + KEY_TO_LAT + " INTEGER NOT NULL, " //
                + KEY_TO_LON + " INTEGER NOT NULL, " //
                + KEY_TO_PLACE + " TEXT, " //
                + KEY_TO_NAME + " TEXT, " //
                + KEY_VIA_TYPE + " INTEGER NOT NULL DEFAULT " + convert(LocationType.ANY) + ", " //
                + KEY_VIA_ID + " TEXT, " //
                + KEY_VIA_LAT + " INTEGER NOT NULL DEFAULT 0, " //
                + KEY_VIA_LON + " INTEGER NOT NULL DEFAULT 0, " //
                + KEY_VIA_PLACE + " TEXT, " //
                + KEY_VIA_NAME + " TEXT, " //
                + KEY_DEPARTURE_TIME + " INTEGER NOT NULL, " //
                + KEY_DEPARTURE_TIME_OFFSET + " INTEGER NOT NULL, " //
                + KEY_ARRIVAL_TIME + " INTEGER NOT NULL, " //
                + KEY_ARRIVAL_TIME_OFFSET + " INTEGER NOT NULL, " //
                + KEY_TRIP_ID + " TEXT NOT NULL, " //
                + KEY_TRIP + " BLOB NOT NULL," //
                + KEY_RELOAD_REQUEST_DATA + " BLOB," //
                + KEY_STATE_FLAGS + " INTEGER NOT NULL DEFAULT 0);";
        private static final String DATABASE_COLUMN_LIST = KEY_ROWID + "," + KEY_NETWORK
                + "," + KEY_FROM_TYPE + "," + KEY_FROM_ID + "," + KEY_FROM_LAT + "," + KEY_FROM_LON + "," + KEY_FROM_PLACE + "," + KEY_FROM_NAME
                + "," + KEY_TO_TYPE + "," + KEY_TO_ID + "," + KEY_TO_LAT + "," + KEY_TO_LON + "," + KEY_TO_PLACE + "," + KEY_TO_NAME
                + "," + KEY_VIA_TYPE + "," + KEY_VIA_ID + "," + KEY_VIA_LAT + "," + KEY_VIA_LON + "," + KEY_VIA_PLACE + "," + KEY_VIA_NAME
                + "," + KEY_DEPARTURE_TIME + "," + KEY_DEPARTURE_TIME_OFFSET
                + "," + KEY_ARRIVAL_TIME + "," + KEY_ARRIVAL_TIME_OFFSET
                + "," + KEY_TRIP_ID + "," + KEY_TRIP
                + "," + KEY_RELOAD_REQUEST_DATA
                + "," + KEY_STATE_FLAGS;

        public QueryTripsHelper(final Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            db.beginTransaction();
            try {
                for (int v = oldVersion; v < newVersion; v++)
                    upgrade(db, v);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        private void upgrade(final SQLiteDatabase db, final int oldVersion) {
            if (oldVersion == 1) {
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_STATE_FLAGS + " INTEGER NOT NULL DEFAULT 0");
                recreate(db);
            } else  {
                throw new UnsupportedOperationException("old=" + oldVersion);
            }
        }

        private void recreate(final SQLiteDatabase db) {
            final String DATABASE_TABLE_OLD = DATABASE_TABLE + "_old";
            db.execSQL("ALTER TABLE " + DATABASE_TABLE + " RENAME TO " + DATABASE_TABLE_OLD);
            db.execSQL(DATABASE_CREATE);
            db.execSQL("INSERT INTO " + DATABASE_TABLE + " SELECT " + DATABASE_COLUMN_LIST + " FROM "
                    + DATABASE_TABLE_OLD);
            db.execSQL("DROP TABLE " + DATABASE_TABLE_OLD);
        }
    }
}
