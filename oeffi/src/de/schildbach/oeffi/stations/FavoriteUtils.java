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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public class FavoriteUtils {
    public static Uri persist(final ContentResolver contentResolver, final int type, final NetworkId networkId,
            final Location station) {
        // checkArgument(station.type == LocationType.STATION, "not a station: %s", station);
        final ContentValues values = new ContentValues();
        values.put(FavoriteStationsProvider.KEY_TYPE, type);
        values.put(FavoriteStationsProvider.KEY_STATION_NETWORK, networkId.name());
        values.put(FavoriteStationsProvider.KEY_STATION_ID, station.id);
        values.put(FavoriteStationsProvider.KEY_STATION_TYPE, station.type.name());
        values.put(FavoriteStationsProvider.KEY_STATION_PLACE, station.place);
        values.put(FavoriteStationsProvider.KEY_STATION_NAME, station.name);
        values.put(FavoriteStationsProvider.KEY_STATION_LAT, station.hasCoord() ? station.getLatAs1E6() : 0);
        values.put(FavoriteStationsProvider.KEY_STATION_LON, station.hasCoord() ? station.getLonAs1E6() : 0);

        final Uri rowUri = contentResolver.insert(FavoriteStationsProvider.CONTENT_URI(), values);

        return rowUri;
    }

    public static int delete(final ContentResolver contentResolver, final NetworkId networkId, final String stationId) {
        final int numRows = contentResolver
                .delete(FavoriteStationsProvider.CONTENT_URI(),
                        FavoriteStationsProvider.KEY_STATION_NETWORK + "=? AND "
                                + FavoriteStationsProvider.KEY_STATION_ID + "=?",
                        new String[] { networkId.name(), stationId });

        return numRows;
    }

    public static Map<Location, Integer> loadAll(final ContentResolver contentResolver, final NetworkId networkId) {
        final Cursor c = contentResolver.query(FavoriteStationsProvider.CONTENT_URI(), null,
                FavoriteStationsProvider.KEY_STATION_NETWORK + "=?", new String[] { networkId.name() }, null);
        final int typeIndex = c.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_TYPE);

        final Map<Location, Integer> favorites = new HashMap<>(c.getCount());
        while (c.moveToNext())
            favorites.put(FavoriteStationsProvider.getLocation(c), c.getInt(typeIndex));

        c.close();
        return favorites;
    }

}
