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

package de.schildbach.oeffi.stations;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import de.schildbach.oeffi.Application;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FavoriteStationsProvider extends ContentProvider {
    private static final String DATABASE_TABLE = "favorites";

    public static Uri CONTENT_URI() {
        return Uri.parse("content://" + Application.getApplicationId() + ".stations." + DATABASE_TABLE);
    };

    public static final String KEY_ROWID = "_id";
    public static final String KEY_TYPE = "type";
    public static final String KEY_STATION_NETWORK = "station_network";
    public static final String KEY_STATION_TYPE = "station_type";
    public static final String KEY_STATION_ID = "station_id";
    public static final String KEY_STATION_PLACE = "station_place";
    public static final String KEY_STATION_NAME = "station_name";
    public static final String KEY_STATION_LAT = "station_lat";
    public static final String KEY_STATION_LON = "station_lon";

    public static final int TYPE_FAVORITE = 1;
    public static final int TYPE_IGNORE = 2;

    private Helper helper;

    @Override
    public boolean onCreate() {
        helper = new Helper(getContext());
        return true;
    }

    @Override
    public String getType(final Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        long rowId = helper.getWritableDatabase().replace(DATABASE_TABLE, null, values);
        if (rowId == -1)
            return null;
        else
            return ContentUris.withAppendedId(CONTENT_URI(), rowId);
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        final List<String> pathSegments = uri.getPathSegments();

        String whereClause = null;
        List<String> whereArgs = new ArrayList<>();

        if (pathSegments.size() >= 1) {
            whereClause = KEY_ROWID + "=?";
            whereArgs.add(pathSegments.get(0));
        }

        if (selection != null) {
            whereClause = (whereClause != null ? whereClause + " AND " : "") + "(" + selection + ")";
            whereArgs.addAll(Arrays.asList(selectionArgs));
        }

        final int count = helper.getWritableDatabase().delete(DATABASE_TABLE, whereClause,
                whereArgs.toArray(new String[0]));

        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection, final String[] selectionArgs,
            final String sortOrder) {
        final List<String> pathSegments = uri.getPathSegments();

        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(DATABASE_TABLE);
        if (pathSegments.size() >= 1)
            qb.appendWhere(KEY_ROWID + "=" + pathSegments.get(0));

        final Cursor cursor = qb.query(helper.getReadableDatabase(), projection, selection, selectionArgs, null, null,
                sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    public static Location getLocation(final Cursor cursor) {
        final int idIndex = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_ID);
        final int typeIndex = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_TYPE);
        final int placeIndex = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_PLACE);
        final int nameIndex = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_NAME);
        final int latIndex = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_LAT);
        final int lonIndex = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_LON);
        return new Location(LocationType.valueOf(cursor.getString(typeIndex)), cursor.getString(idIndex),
                Point.from1E6(cursor.getInt(latIndex), cursor.getInt(lonIndex)), cursor.getString(placeIndex),
                cursor.getString(nameIndex));
    }

    public static Integer favState(final ContentResolver contentResolver, final NetworkId network,
            final Location location) {
        if (!location.isIdentified() || location.type != LocationType.STATION)
            return null;

        final Cursor favCursor = contentResolver.query(FavoriteStationsProvider.CONTENT_URI(),
                new String[] { FavoriteStationsProvider.KEY_TYPE }, FavoriteStationsProvider.KEY_STATION_NETWORK
                        + "=? AND " + FavoriteStationsProvider.KEY_STATION_ID + "=?",
                new String[] { network.name(), location.id }, null);
        final Integer favState = favCursor.moveToFirst()
                ? favCursor.getInt(favCursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_TYPE)) : null;
        favCursor.close();
        return favState;
    }

    /**
     * Restricted to usage by {@link Application#onCreate()} only.
     */
    public static void migrateFavoriteStations(final Context context, final String fromName, final NetworkId to) {
        final Helper helper = new Helper(context);
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            if (to != null)
                db.execSQL("UPDATE OR IGNORE " + DATABASE_TABLE + " SET " + KEY_STATION_NETWORK + "=? WHERE "
                        + KEY_STATION_NETWORK + "=?", new String[] { to.name(), fromName });
            db.execSQL("DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_STATION_NETWORK + "=?",
                    new String[] { fromName });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        helper.close();
    }

    /**
     * Restricted to usage by {@link Application#onCreate()} only.
     */
    public static void migrateFavoriteStationIds(final Context context, final NetworkId network, final String fromId,
            final String toId, final int offset) {
        final Helper helper = new Helper(context);
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            db.execSQL(
                    "UPDATE OR IGNORE " + DATABASE_TABLE + " SET " + KEY_STATION_ID + "=CAST(CAST(" + KEY_STATION_ID
                            + " AS INTEGER)+? AS TEXT) WHERE " + KEY_STATION_NETWORK + "=? AND CAST(" + KEY_STATION_ID
                            + " AS INTEGER)>=? AND CAST(" + KEY_STATION_ID + " AS INTEGER)<?",
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
    public static void deleteFavoriteStations(final Context context, final String network) {
        final Helper helper = new Helper(context);
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_STATION_NETWORK + "=?",
                    new String[] { network });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        helper.close();
    }

    /**
     * Restricted to usage by {@link Application#onCreate()} only.
     */
    public static void deleteFavoriteStations(final Context context, final NetworkId network, final String fromId,
            final String toId) {
        final Helper helper = new Helper(context);
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try {
            db.execSQL(
                    "DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_STATION_NETWORK + "=? AND CAST(" + KEY_STATION_ID
                            + " AS INTEGER)>=? AND CAST(" + KEY_STATION_ID + " AS INTEGER)<?",
                    new String[] { network.name(), fromId, toId });
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        helper.close();
    }

    private static class Helper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "station_favorites";
        private static final int DATABASE_VERSION = 6;

        private static final String DATABASE_CREATE = "CREATE TABLE " + DATABASE_TABLE + " (" //
                + KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " //
                + KEY_TYPE + " INT NOT NULL DEFAULT 1, " //
                + KEY_STATION_NETWORK + " TEXT NOT NULL, " //
                + KEY_STATION_ID + " TEXT NOT NULL, " //
                + KEY_STATION_TYPE + " TEXT, " //
                + KEY_STATION_PLACE + " TEXT, " //
                + KEY_STATION_NAME + " TEXT, " //
                + KEY_STATION_LAT + " INT NOT NULL DEFAULT 0, " //
                + KEY_STATION_LON + " INT NOT NULL DEFAULT 0, " //
                + "UNIQUE (" + KEY_STATION_NETWORK + "," + KEY_STATION_ID + "));";
        private static final String DATABASE_COLUMN_LIST = KEY_ROWID + "," + KEY_TYPE + "," + KEY_STATION_NETWORK + ","
                + KEY_STATION_ID + "," + KEY_STATION_TYPE + "," + KEY_STATION_PLACE + "," + KEY_STATION_NAME + ","
                + KEY_STATION_LAT + "," + KEY_STATION_LON;

        public Helper(final Context context) {
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
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_TYPE + " INT NOT NULL DEFAULT 1");
            } else if (oldVersion == 2) {
                // removed proper migration because very few old clients left
                db.execSQL("DELETE FROM " + DATABASE_TABLE);
            } else if (oldVersion == 3) {
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_STATION_PLACE + " TEXT");
                db.execSQL(
                        "ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_STATION_LAT + " INT NOT NULL DEFAULT 0");
                db.execSQL(
                        "ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_STATION_LON + " INT NOT NULL DEFAULT 0");
            } else if (oldVersion == 4) {
                final String DATABASE_TABLE_OLD = DATABASE_TABLE + "_old";
                db.execSQL("ALTER TABLE " + DATABASE_TABLE + " RENAME TO " + DATABASE_TABLE_OLD);
                db.execSQL(DATABASE_CREATE);
                db.execSQL("INSERT INTO " + DATABASE_TABLE + " SELECT " + DATABASE_COLUMN_LIST + " FROM "
                        + DATABASE_TABLE_OLD);
                db.execSQL("DROP TABLE " + DATABASE_TABLE_OLD);
            } else if (oldVersion == 5) {
                db.execSQL(
                        "ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_STATION_TYPE + " TEXT DEFAULT '" + LocationType.STATION.name() + "'");
            } else {
                throw new UnsupportedOperationException("old=" + oldVersion);
            }
        }
    }
}
