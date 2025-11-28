package de.schildbach.oeffi.util.locationview;

import android.database.Cursor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.directions.QueryHistoryProvider;
import de.schildbach.oeffi.network.LocationSearchProviderFactory;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.pte.LocationSearchProvider;
import de.schildbach.pte.LocationSearchProviderId;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.SuggestLocationsResult;

public class LocationSuggestionsCollector {
    private static final Logger log = LoggerFactory.getLogger(LocationSuggestionsCollector.class);

    public static List<Location> collectSuggestions(
            final CharSequence aConstraint,
            final AbstractSet<LocationType> suggestedLocationTypes,
            final NetworkId network,
            final LocationSearchProviderId searchProviderId) {
        if (aConstraint == null)
            return null;
        final String constraint = aConstraint.toString().trim();
        if (constraint.isEmpty())
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

            loadResultsFromFavoriteStations(constraint, network, results);
            loadResultsFromQueryHistory(constraint, network, results);
            loadResultsFromNetworkProvider(constraint,
                    network, searchProviderId,
                    suggestedLocationTypes,
                    results);

            final List<Location> filteredResults = new ArrayList<>();
            for (final Location location : results) {
                boolean use = false;
                switch (location.type) {
                    case STATION: use = selectStations; break;
                    case ADDRESS: use = selectAddresses; break;
                    case POI: use = selectPOIs; break;
                }
                if (use)
                    filteredResults.add(location);
            }

            if (!filteredResults.isEmpty())
                return filteredResults;

            return results;
        } catch (final IOException ioe) {
            log.error("collectSuggestions failed", ioe);
            return null;
        }
    }

    private static void loadResultsFromFavoriteStations(
            final String constraint,
            final NetworkId network,
            final List<Location> results) {
        final Cursor cursor = Application.getInstance().getContentResolver().query(
                FavoriteStationsProvider.CONTENT_URI(), null,
                FavoriteStationsProvider.KEY_TYPE + "=? AND "
                        + FavoriteStationsProvider.KEY_STATION_NETWORK + "=?",
                new String[] { String.valueOf(FavoriteStationsProvider.TYPE_FAVORITE), network.name() },
                null);

        if (cursor == null)
            return;

        final int typeC = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_TYPE);
        final int idC = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_ID);
        final int latC = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_LAT);
        final int lonC = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_LON);
        final int placeC = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_PLACE);
        final int nameC = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_NAME);
        final int nickNameC = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_NICKNAME);

        final String lowerCaseConstraint = constraint.toLowerCase(Constants.DEFAULT_LOCALE);

        while (cursor.moveToNext()) {
            final String name = cursor.getString(nameC);
            final String place = cursor.getString(placeC);
            final String nickName = cursor.getString(nickNameC);
            if (namePartMatches(name, lowerCaseConstraint)
                    || namePartMatches(place, lowerCaseConstraint)
                    || namePartMatches(nickName, lowerCaseConstraint)) {
                final LocationType type = LocationType.valueOf(cursor.getString(typeC));
                final String id = cursor.getString(idC);
                final int lat = cursor.getInt(latC);
                final int lon = cursor.getInt(lonC);
                final Point coord = lat != 0 || lon != 0 ? Point.from1E6(lat, lon) : null;
                final Location location;
                if (nickName != null)
                    location = new Location(type, id, coord, null, nickName);
                else
                    location = new Location(type, id, coord, place, name);
                if (!results.contains(location))
                    results.add(location);
            }
        }
        cursor.close();
    }

    private static boolean namePartMatches(final String namePart, final String lowerCaseConstraint) {
        return namePart != null && namePart.toLowerCase(Constants.DEFAULT_LOCALE).contains(lowerCaseConstraint);
    }

    private static void loadResultsFromQueryHistory(
            final String constraint,
            final NetworkId network,
            final List<Location> results) {
        final Cursor cursor = Application.getInstance().getContentResolver().query(
                QueryHistoryProvider.CONTENT_URI().buildUpon()
                        .appendPath(network.name())
                        .appendQueryParameter(QueryHistoryProvider.QUERY_PARAM_Q, constraint)
                        .build(),
                null, null, null, QueryHistoryProvider.KEY_LAST_QUERIED + " DESC");

        if (cursor == null)
            return;

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

        final String lowerCaseConstraint = constraint.toLowerCase(Constants.DEFAULT_LOCALE);

        while (cursor.moveToNext()) {
            final String fromName = cursor.getString(fromNameC);
            if (fromName.toLowerCase(Constants.DEFAULT_LOCALE).contains(lowerCaseConstraint)) {
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
            if (toName.toLowerCase(Constants.DEFAULT_LOCALE).contains(lowerCaseConstraint)) {
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
    }

    private static void loadResultsFromNetworkProvider(
            final String constraint,
            final NetworkId network,
            final LocationSearchProviderId searchProviderId,
            final AbstractSet<LocationType> suggestedLocationTypes,
            final List<Location> results
    ) throws IOException {
        final int constraintLength = constraint.length();
        if (constraintLength >= 3) {
            final int maxLocations = constraintLength >= 8 ? 15 : constraintLength * 2;
            final LocationSearchProvider locationSearchProvider = searchProviderId != null
                    ? LocationSearchProviderFactory.provider(searchProviderId)
                    : NetworkProviderFactory.provider(network);
            final SuggestLocationsResult suggestLocationsResult = locationSearchProvider
                    .suggestLocations(constraint, suggestedLocationTypes, maxLocations);
            if (suggestLocationsResult.status == SuggestLocationsResult.Status.OK) {
                final List<Location> foundLocations = suggestLocationsResult.getLocations();
                for (final Location location : foundLocations) {
                    if (!results.contains(location))
                        results.add(location);
                }
            }
        }
    }
}
