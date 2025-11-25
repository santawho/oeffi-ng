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

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;

import java.util.ArrayList;
import java.util.List;

public class QueryHistoryProvider extends ContentProvider {
    private static final String DATABASE_TABLE = "query_history";

    public static Uri CONTENT_URI() {
        return Uri.parse("content://" + Application.getApplicationId() + ".directions." + DATABASE_TABLE);
    };

    public static final String KEY_ROWID = "_id";
    public static final String KEY_NETWORK = "query_network";
    public static final String KEY_FROM_TYPE = "query_from_type";
    public static final String KEY_FROM_ID = "query_from_id";
    public static final String KEY_FROM_LAT = "query_from_lat";
    public static final String KEY_FROM_LON = "query_from_lon";
    public static final String KEY_FROM_PLACE = "query_from_place";
    public static final String KEY_FROM_NAME = "query_from";
    public static final String KEY_TO_TYPE = "query_to_type";
    public static final String KEY_TO_ID = "query_to_id";
    public static final String KEY_TO_LAT = "query_to_lat";
    public static final String KEY_TO_LON = "query_to_lon";
    public static final String KEY_TO_PLACE = "query_to_place";
    public static final String KEY_TO_NAME = "query_to";
    public static final String KEY_VIA_TYPE = "query_via_type";
    public static final String KEY_VIA_ID = "query_via_id";
    public static final String KEY_VIA_LAT = "query_via_lat";
    public static final String KEY_VIA_LON = "query_via_lon";
    public static final String KEY_VIA_PLACE = "query_via_place";
    public static final String KEY_VIA_NAME = "query_via";
    public static final String KEY_FAVORITE = "favorite";
    public static final String KEY_TIMES_QUERIED = "times_queried";
    public static final String KEY_LAST_QUERIED = "last_queried";
    public static final String KEY_LAST_DEPARTURE_TIME = "last_departure_time";
    public static final String KEY_LAST_ARRIVAL_TIME = "last_arrival_time";
    public static final String KEY_LAST_TRIP = "last_connection"; // TODO migrate

    public static final String QUERY_PARAM_Q = "q";

    public static final int TYPE_ANY = 0;
    public static final int TYPE_STATION = 1;
    public static final int TYPE_POI = 2;
    public static final int TYPE_ADDRESS = 3;
    public static final int TYPE_COORD = 4;

    public static Uri historyRowUri(final NetworkId network, final long rowId) {
        return QueryHistoryProvider.CONTENT_URI().buildUpon().appendPath(network.name()).appendPath(Long.toString(rowId))
                .build();
    }

    public static Uri put(
            final ContentResolver contentResolver, final NetworkId network,
            final Location from, final Location to, final Location via,
            final Boolean favorite, final boolean isQuery) {
        final Cursor cursor = cursor(contentResolver, network, from, to, via);

        final Uri historyUri;

        if (cursor.moveToFirst()) {
            final long rowId = cursor.getLong(cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_ROWID));
            historyUri = historyRowUri(network, rowId);

            final long timesQueried = cursor
                    .getLong(cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TIMES_QUERIED));

            if (isQuery || timesQueried > 0 || Boolean.TRUE.equals(favorite)) {
                final ContentValues values = new ContentValues();

                if (cursor.getInt(cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_ID)) == 0)
                    values.put(QueryHistoryProvider.KEY_FROM_ID, from.id);

                if (cursor.getInt(cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_LAT)) == 0
                        && cursor.getInt(cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_LON)) == 0) {
                    values.put(QueryHistoryProvider.KEY_FROM_LAT, from.hasCoord() ? from.getLatAs1E6() : 0);
                    values.put(QueryHistoryProvider.KEY_FROM_LON, from.hasCoord() ? from.getLonAs1E6() : 0);
                }

                if (cursor.getInt(cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_ID)) == 0)
                    values.put(QueryHistoryProvider.KEY_TO_ID, to.id);

                if (cursor.getInt(cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_LAT)) == 0
                        && cursor.getInt(cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_LON)) == 0) {
                    values.put(QueryHistoryProvider.KEY_TO_LAT, to.hasCoord() ? to.getLatAs1E6() : 0);
                    values.put(QueryHistoryProvider.KEY_TO_LON, to.hasCoord() ? to.getLonAs1E6() : 0);
                }

                if (favorite != null)
                    values.put(QueryHistoryProvider.KEY_FAVORITE, favorite);

                if (isQuery) {
                    values.put(QueryHistoryProvider.KEY_TIMES_QUERIED, timesQueried + 1);
                    values.put(QueryHistoryProvider.KEY_LAST_QUERIED, System.currentTimeMillis());
                }

                contentResolver.update(historyUri, values, null, null);
            } else {
                contentResolver.delete(historyUri, null, null);
            }
        } else {
            final ContentValues values = new ContentValues();
            values.put(QueryHistoryProvider.KEY_FROM_TYPE, QueryHistoryProvider.convert(from.type));
            values.put(QueryHistoryProvider.KEY_FROM_ID, from.id);
            values.put(QueryHistoryProvider.KEY_FROM_LAT, from.hasCoord() ? from.getLatAs1E6() : 0);
            values.put(QueryHistoryProvider.KEY_FROM_LON, from.hasCoord() ? from.getLonAs1E6() : 0);
            values.put(QueryHistoryProvider.KEY_FROM_PLACE, from.place);
            values.put(QueryHistoryProvider.KEY_FROM_NAME, from.name);
            values.put(QueryHistoryProvider.KEY_TO_TYPE, QueryHistoryProvider.convert(to.type));
            values.put(QueryHistoryProvider.KEY_TO_ID, to.id);
            values.put(QueryHistoryProvider.KEY_TO_LAT, to.hasCoord() ? to.getLatAs1E6() : 0);
            values.put(QueryHistoryProvider.KEY_TO_LON, to.hasCoord() ? to.getLonAs1E6() : 0);
            values.put(QueryHistoryProvider.KEY_TO_PLACE, to.place);
            values.put(QueryHistoryProvider.KEY_TO_NAME, to.name);
            values.put(QueryHistoryProvider.KEY_VIA_TYPE, QueryHistoryProvider.convert(via == null ? LocationType.ANY : via.type));
            values.put(QueryHistoryProvider.KEY_VIA_ID, via == null ? null : via.id);
            values.put(QueryHistoryProvider.KEY_VIA_LAT, via != null && via.hasCoord() ? via.getLatAs1E6() : 0);
            values.put(QueryHistoryProvider.KEY_VIA_LON, via != null && via.hasCoord() ? via.getLonAs1E6() : 0);
            values.put(QueryHistoryProvider.KEY_VIA_PLACE, via == null ? null : via.place);
            values.put(QueryHistoryProvider.KEY_VIA_NAME, via == null ? null : via.name);

            if (favorite != null)
                values.put(QueryHistoryProvider.KEY_FAVORITE, favorite);

            values.put(QueryHistoryProvider.KEY_TIMES_QUERIED, isQuery ? 1 : 0);
            values.put(QueryHistoryProvider.KEY_LAST_QUERIED, isQuery ? System.currentTimeMillis() : 0);

            final Uri baseUri = QueryHistoryProvider.CONTENT_URI().buildUpon().appendPath(network.name()).build();
            historyUri = contentResolver.insert(baseUri, values);

            final Application application = Application.getInstance();
            final int maxHistoryEntries = application.getSharedPreferences()
                    .getInt(Constants.PREFS_KEY_MAX_HISTORY_ENTRIES,
                            application.getResources().getInteger(R.integer.default_max_history_entries));
            final Cursor deleteCursor = contentResolver.query(baseUri, null,
                    QueryHistoryProvider.KEY_FAVORITE + "= 0", null,
                    KEY_LAST_QUERIED + " DESC");
            if (deleteCursor != null) {
                if (deleteCursor.moveToPosition(maxHistoryEntries - 1)) {
                    while (deleteCursor.moveToNext()) {
                        final Uri deleteUri = baseUri.buildUpon()
                                .appendPath(String.valueOf(deleteCursor
                                        .getInt(deleteCursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_ROWID))))
                                .build();
                        contentResolver.delete(deleteUri, null, null);
                    }
                }
                deleteCursor.close();
            }
        }

        cursor.close();

        return historyUri;
    }

    public static Cursor cursor(
            final ContentResolver contentResolver, final NetworkId network,
            final Location from, final Location to, final Location via) {
        final StringBuilder selection = new StringBuilder();
        final List<String> selectionArgs = new ArrayList<>();

        selection.append(QueryHistoryProvider.KEY_FROM_TYPE).append("=?");
        selectionArgs.add(Integer.toString(QueryHistoryProvider.convert(from.type)));

        if (from.hasId()) {
            selection.append(" AND ").append(QueryHistoryProvider.KEY_FROM_ID).append("=?");
            selectionArgs.add(from.id);
        } else {
            if (from.place != null) {
                selection.append(" AND ").append(QueryHistoryProvider.KEY_FROM_PLACE).append("=?");
                selectionArgs.add(from.place);
            } else {
                selection.append(" AND ").append(QueryHistoryProvider.KEY_FROM_PLACE).append(" IS NULL");
            }

            selection.append(" AND ").append(QueryHistoryProvider.KEY_FROM_NAME).append("=?");
            selectionArgs.add(from.name);
        }

        selection.append(" AND ").append(QueryHistoryProvider.KEY_TO_TYPE).append("=?");
        selectionArgs.add(Integer.toString(QueryHistoryProvider.convert(to.type)));

        if (to.hasId()) {
            selection.append(" AND ").append(QueryHistoryProvider.KEY_TO_ID).append("=?");
            selectionArgs.add(to.id);
        } else {
            if (to.place != null) {
                selection.append(" AND ").append(QueryHistoryProvider.KEY_TO_PLACE).append("=?");
                selectionArgs.add(to.place);
            } else {
                selection.append(" AND ").append(QueryHistoryProvider.KEY_TO_PLACE).append(" IS NULL");
            }

            selection.append(" AND ").append(QueryHistoryProvider.KEY_TO_NAME).append("=?");
            selectionArgs.add(to.name);
        }

        if (via == null) {
            selection.append(" AND ").append(QueryHistoryProvider.KEY_VIA_TYPE).append("=?");
            selectionArgs.add(Integer.toString(QueryHistoryProvider.convert(LocationType.ANY)));
        } else {
            selection.append(" AND ").append(QueryHistoryProvider.KEY_VIA_TYPE).append("=?");
            selectionArgs.add(Integer.toString(QueryHistoryProvider.convert(via.type)));

            if (via.hasId()) {
                selection.append(" AND ").append(QueryHistoryProvider.KEY_VIA_ID).append("=?");
                selectionArgs.add(via.id);
            } else {
                if (via.place != null) {
                    selection.append(" AND ").append(QueryHistoryProvider.KEY_VIA_PLACE).append("=?");
                    selectionArgs.add(via.place);
                } else {
                    selection.append(" AND ").append(QueryHistoryProvider.KEY_VIA_PLACE).append(" IS NULL");
                }

                selection.append(" AND ").append(QueryHistoryProvider.KEY_VIA_NAME).append("=?");
                selectionArgs.add(via.name);
            }
        }

        return contentResolver.query(QueryHistoryProvider.CONTENT_URI().buildUpon().appendPath(network.name()).build(),
                null, selection.toString(), selectionArgs.toArray(new String[0]), null);
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

    private QueryHistoryHelper helper;

    @Override
    public boolean onCreate() {
        helper = new QueryHistoryHelper(getContext());
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

        long rowId = helper.getWritableDatabase().insertOrThrow(DATABASE_TABLE, null, values);

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
        if (pathSegments.size() < 1)
            throw new IllegalArgumentException(uri.toString());

        qb.appendWhere(KEY_NETWORK + "=");
        qb.appendWhereEscapeString(pathSegments.get(0));

        if (pathSegments.size() >= 2) {
            qb.appendWhere(" AND " + KEY_ROWID + "=");
            qb.appendWhereEscapeString(pathSegments.get(1));
        }

        final String name = uri.getQueryParameter(QUERY_PARAM_Q);
        if (name != null) {
            qb.appendWhere(" AND (" + KEY_FROM_NAME + " LIKE ");
            qb.appendWhereEscapeString('%' + name + '%');
            qb.appendWhere(" OR " + KEY_TO_NAME + " LIKE ");
            qb.appendWhereEscapeString('%' + name + '%');
            qb.appendWhere(")");
        }

        final Cursor cursor = qb.query(helper.getReadableDatabase(), projection, selection, selectionArgs, null, null,
                sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    /**
     * Restricted to usage by {@link Application#onCreate()} only.
     */
    public static void migrateQueryHistory(final Context context, final String fromName, final NetworkId to) {
        final QueryHistoryHelper helper = new QueryHistoryHelper(context);
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            if (to != null)
                db.execSQL(
                        "UPDATE OR IGNORE " + DATABASE_TABLE + " SET " + KEY_NETWORK + "=? WHERE " + KEY_NETWORK + "=?",
                        new String[] { to.name(), fromName });
            db.execSQL("DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_NETWORK + "=?", new String[] { fromName });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        helper.close();
    }

    /**
     * Restricted to usage by {@link Application#onCreate()} only.
     */
    public static void migrateQueryHistoryIds(final Context context, final NetworkId network, final String fromId,
            final String toId, final int offset) {
        final QueryHistoryHelper helper = new QueryHistoryHelper(context);
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            db.execSQL("UPDATE OR IGNORE " + DATABASE_TABLE + " SET " + KEY_FROM_ID + "=CAST(CAST(" + KEY_FROM_ID
                    + " AS INTEGER)+? AS TEXT) WHERE " + KEY_NETWORK + "=? AND " + KEY_FROM_TYPE + "=" + TYPE_STATION
                    + " AND CAST(" + KEY_FROM_ID + " AS INTEGER)>=? AND CAST(" + KEY_FROM_ID + " AS INTEGER)<?",
                    new String[] { Integer.toString(offset), network.name(), fromId, toId });
            db.execSQL("UPDATE OR IGNORE " + DATABASE_TABLE + " SET " + KEY_TO_ID + "=CAST(CAST(" + KEY_TO_ID
                    + " AS INTEGER)+? AS TEXT) WHERE " + KEY_NETWORK + "=? AND " + KEY_TO_TYPE + "=" + TYPE_STATION
                    + " AND CAST(" + KEY_TO_ID + " AS INTEGER)>=? AND CAST(" + KEY_TO_ID + " AS INTEGER)<?",
                    new String[] { Integer.toString(offset), network.name(), fromId, toId });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        helper.close();
    }

    /**
     * Restricted to usage by {@link Application#onCreate()} only.
     */
    public static void deleteQueryHistory(final Context context, final String network) {
        final QueryHistoryHelper helper = new QueryHistoryHelper(context);
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_NETWORK + "=?", new String[] { network });
            db.execSQL("DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_NETWORK + "=?", new String[] { network });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        helper.close();
    }

    /**
     * Restricted to usage by {@link Application#onCreate()} only.
     */
    public static void deleteQueryHistory(final Context context, final NetworkId network, final String fromId,
            final String toId) {
        final QueryHistoryHelper helper = new QueryHistoryHelper(context);
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_NETWORK + "=? AND " + KEY_FROM_TYPE + "="
                    + TYPE_STATION + " AND CAST(" + KEY_FROM_ID + " AS INTEGER)>=? AND CAST(" + KEY_FROM_ID
                    + " AS INTEGER)<?", new String[] { network.name(), fromId, toId });
            db.execSQL("DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_NETWORK + "=? AND " + KEY_TO_TYPE + "="
                    + TYPE_STATION + " AND CAST(" + KEY_TO_ID + " AS INTEGER)>=? AND CAST(" + KEY_TO_ID
                    + " AS INTEGER)<?", new String[] { network.name(), fromId, toId });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        helper.close();
    }

    private static class QueryHistoryHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "oeffi";
        private static final int DATABASE_VERSION = 7;

        private static final String DATABASE_CREATE = "CREATE TABLE " + DATABASE_TABLE + " (" //
                + KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " //
                + KEY_NETWORK + " TEXT NOT NULL, " //
                + KEY_FROM_TYPE + " INTEGER NOT NULL, " //
                + KEY_FROM_ID + " TEXT, " //
                + KEY_FROM_LAT + " INTEGER NOT NULL, " //
                + KEY_FROM_LON + " INTEGER NOT NULL, " //
                + KEY_FROM_PLACE + " TEXT, " //
                + KEY_FROM_NAME + " TEXT NOT NULL, " //
                + KEY_TO_TYPE + " INTEGER NOT NULL, " //
                + KEY_TO_ID + " TEXT, " //
                + KEY_TO_LAT + " INTEGER NOT NULL, " //
                + KEY_TO_LON + " INTEGER NOT NULL, " //
                + KEY_TO_PLACE + " TEXT, " //
                + KEY_TO_NAME + " TEXT NOT NULL, " //
                + KEY_VIA_TYPE + " INTEGER NOT NULL DEFAULT " + convert(LocationType.ANY) + ", " //
                + KEY_VIA_ID + " TEXT, " //
                + KEY_VIA_LAT + " INTEGER NOT NULL DEFAULT 0, " //
                + KEY_VIA_LON + " INTEGER NOT NULL DEFAULT 0, " //
                + KEY_VIA_PLACE + " TEXT, " //
                + KEY_VIA_NAME + " TEXT, " //
                + KEY_FAVORITE + " INTEGER DEFAULT 0, " //
                + KEY_TIMES_QUERIED + " INTEGER NOT NULL DEFAULT 0, " //
                + KEY_LAST_QUERIED + " INTEGER NOT NULL, " // TODO NULL
                + KEY_LAST_DEPARTURE_TIME + " INTEGER NOT NULL DEFAULT 0, " //
                + KEY_LAST_ARRIVAL_TIME + " INTEGER NOT NULL DEFAULT 0, " //
                + KEY_LAST_TRIP + " BLOB);";
        private static final String DATABASE_COLUMN_LIST = KEY_ROWID + "," + KEY_NETWORK
                + "," + KEY_FROM_TYPE + "," + KEY_FROM_ID + "," + KEY_FROM_LAT + "," + KEY_FROM_LON + "," + KEY_FROM_PLACE + "," + KEY_FROM_NAME
                + "," + KEY_TO_TYPE + "," + KEY_TO_ID + "," + KEY_TO_LAT + "," + KEY_TO_LON + "," + KEY_TO_PLACE + "," + KEY_TO_NAME
                + "," + KEY_VIA_TYPE + "," + KEY_VIA_ID + "," + KEY_VIA_LAT + "," + KEY_VIA_LON + "," + KEY_VIA_PLACE + "," + KEY_VIA_NAME
                + "," + KEY_FAVORITE + "," + KEY_TIMES_QUERIED + "," + KEY_LAST_QUERIED + ","
                + KEY_LAST_DEPARTURE_TIME + "," + KEY_LAST_ARRIVAL_TIME + "," + KEY_LAST_TRIP;

        public QueryHistoryHelper(final Context context) {
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
                db.execSQL(
                        "ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_FROM_TYPE + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_FROM_ID + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_FROM_LAT + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_FROM_LON + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_TO_TYPE + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_TO_ID + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_TO_LAT + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_TO_LON + " INT NOT NULL DEFAULT 0");
            } else if (oldVersion == 2) {
                // removed proper migration because very few old clients left
                db.execSQL("DELETE FROM " + DATABASE_TABLE);
            } else if (oldVersion == 3) {
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_FROM_PLACE + " TEXT");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_TO_PLACE + " TEXT");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_LAST_DEPARTURE_TIME
                        + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_LAST_ARRIVAL_TIME
                        + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_LAST_TRIP + " BLOB");
            } else if (oldVersion == 4) {
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_FAVORITE + " INTEGER DEFAULT 0");
            } else if (oldVersion == 5) {
                final String DATABASE_TABLE_OLD = DATABASE_TABLE + "_old";
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " RENAME TO " + DATABASE_TABLE_OLD);
                db.execSQL(DATABASE_CREATE);
                db.execSQL("INSERT INTO " + DATABASE_TABLE + " SELECT " + DATABASE_COLUMN_LIST + " FROM "
                        + DATABASE_TABLE_OLD);
                db.execSQL("DROP TABLE " + DATABASE_TABLE_OLD);
                db.execSQL("UPDATE " + DATABASE_TABLE + " SET " + KEY_FROM_ID + "=NULL WHERE " + KEY_FROM_ID + "=0");
                db.execSQL("UPDATE " + DATABASE_TABLE + " SET " + KEY_TO_ID + "=NULL WHERE " + KEY_TO_ID + "=0");
            } else if (oldVersion == 6) {
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_VIA_TYPE + " INT NOT NULL DEFAULT " + convert(LocationType.ANY));
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_VIA_ID + "  TEXT");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_VIA_LAT + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_VIA_LON + " INT NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_VIA_NAME + " TEXT");
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_VIA_PLACE + " TEXT");
            } else {
                throw new UnsupportedOperationException("old=" + oldVersion);
            }
        }
    }
}
