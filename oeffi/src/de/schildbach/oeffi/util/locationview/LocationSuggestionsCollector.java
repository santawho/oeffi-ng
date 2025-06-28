package de.schildbach.oeffi.util.locationview;

import android.database.Cursor;

import java.io.IOException;
import java.util.AbstractSet;
import java.util.LinkedList;
import java.util.List;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.directions.QueryHistoryProvider;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.SuggestLocationsResult;

public class LocationSuggestionsCollector {
    public static List<Location> collectSuggestions(
            final CharSequence constraint,
            final AbstractSet<LocationType> suggestedLocationTypes,
            final NetworkId network) {
        if (constraint == null)
            return null;
        final String constraintStr = constraint.toString().trim();
        if (constraintStr.isEmpty())
            return null;
        if (suggestedLocationTypes.isEmpty()) {
            suggestedLocationTypes.add(LocationType.STATION);
            suggestedLocationTypes.add(LocationType.ADDRESS);
            suggestedLocationTypes.add(LocationType.POI);
        }
        final boolean selectStations = suggestedLocationTypes.contains(LocationType.STATION);
        final boolean selectAddresses = suggestedLocationTypes.contains(LocationType.ADDRESS);
        final boolean selectPOIs = suggestedLocationTypes.contains(LocationType.POI);
        try {
            final List<Location> results = new LinkedList<>();

            // local autocomplete
            final Cursor cursor = Application.getInstance().getContentResolver().query(
                    QueryHistoryProvider.CONTENT_URI().buildUpon().appendPath(network.name())
                            .appendQueryParameter(QueryHistoryProvider.QUERY_PARAM_Q, constraintStr)
                            .build(),
                    null, null, null, QueryHistoryProvider.KEY_LAST_QUERIED + " DESC");

            final int fromTypeC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_TYPE);
            final int fromIdC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_ID);
            final int fromLatC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_LAT);
            final int fromLonC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_LON);
            final int fromPlaceC = cursor
                    .getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_PLACE);
            final int fromNameC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_NAME);
            final int toTypeC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_TYPE);
            final int toIdC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_ID);
            final int toLatC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_LAT);
            final int toLonC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_LON);
            final int toPlaceC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_PLACE);
            final int toNameC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_NAME);

            while (cursor.moveToNext()) {
                final String fromName = cursor.getString(fromNameC);
                if (fromName.toLowerCase(Constants.DEFAULT_LOCALE)
                        .contains(constraintStr.toLowerCase(Constants.DEFAULT_LOCALE))) {
                    final LocationType fromType = QueryHistoryProvider
                            .convert(cursor.getInt(fromTypeC));
                    final String fromId = cursor.getString(fromIdC);
                    final int fromLat = cursor.getInt(fromLatC);
                    final int fromLon = cursor.getInt(fromLonC);
                    final Point fromCoord = fromLat != 0 || fromLon != 0
                            ? Point.from1E6(fromLat, fromLon) : null;
                    final String fromPlace = cursor.getString(fromPlaceC);
                    final Location location = new Location(fromType, fromId, fromCoord, fromPlace,
                            fromName);
                    if (!results.contains(location))
                        results.add(location);
                }
                final String toName = cursor.getString(toNameC);
                if (toName.toLowerCase(Constants.DEFAULT_LOCALE)
                        .contains(constraintStr.toLowerCase(Constants.DEFAULT_LOCALE))) {
                    final LocationType toType = QueryHistoryProvider
                            .convert(cursor.getInt(toTypeC));
                    final String toId = cursor.getString(toIdC);
                    final int toLat = cursor.getInt(toLatC);
                    final int toLon = cursor.getInt(toLonC);
                    final Point toCoord = toLat != 0 || toLon != 0 ? Point.from1E6(toLat, toLon)
                            : null;
                    final String toPlace = cursor.getString(toPlaceC);
                    final Location location = new Location(toType, toId, toCoord, toPlace, toName);
                    if (!results.contains(location))
                        results.add(location);
                }
            }
            cursor.close();

            // remote autocomplete
            if (constraint.length() >= 3) {
                final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
                final SuggestLocationsResult suggestLocationsResult = networkProvider
                        .suggestLocations(constraint, suggestedLocationTypes, 15);
                if (suggestLocationsResult.status == SuggestLocationsResult.Status.OK) {
                    final List<Location> foundLocations = suggestLocationsResult.getLocations();
                    for (Location location : foundLocations) {
                        if (results.contains(location))
                            continue;
                        boolean use = false;
                        switch (location.type) {
                            case STATION: use = selectStations; break;
                            case ADDRESS: use = selectAddresses; break;
                            case POI: use = selectPOIs; break;
                        }
                        if (use)
                            results.add(location);
                    }
                }
            }

            return results;
        } catch (final IOException x) {
            x.printStackTrace();
            return null;
        }
    }
}
