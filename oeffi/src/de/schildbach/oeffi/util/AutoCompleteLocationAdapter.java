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
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.LocationTextView;
import de.schildbach.oeffi.directions.QueryHistoryProvider;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.SuggestLocationsResult;

public class AutoCompleteLocationAdapter extends BaseAdapter implements Filterable {
    private final Activity activity;
    private final NetworkId network;
    private List<Location> locations = new LinkedList<>();

    public AutoCompleteLocationAdapter(final Activity context, final NetworkId network) {
        this.activity = context;
        this.network = network;
    }

    public Activity getActivity() {
        return activity;
    }

    public int getCount() {
        return locations.size();
    }

    public Object getItem(final int position) {
        return locations.get(position);
    }

    public long getItemId(final int position) {
        return position;
    }

    public View getView(final int position, View row, final ViewGroup parent) {
        if (row == null)
            row = activity.getLayoutInflater().inflate(R.layout.directions_location_dropdown_entry, null);

        final Location location = locations.get(position);
        ((LocationTextView) row).setLocation(location);

        return row;
    }

    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(final CharSequence constraint) {
                final FilterResults filterResults = new FilterResults();
                final List<Location> results = collectSuggestions(constraint);
                if (results != null) {
                    filterResults.values = results;
                    filterResults.count = results.size();
                }
                return filterResults;
            }

            @Override
            protected void publishResults(final CharSequence constraint, final FilterResults filterResults) {
                if (filterResults.values != null) {
                    locations = (List<Location>) filterResults.values;
                    notifyDataSetChanged();
                }
            }
        };
    }

    public List<Location> collectSuggestions(final CharSequence constraint) {
        if (constraint == null)
            return null;
        final String constraintStr = constraint.toString().trim();
        if (constraintStr.isEmpty())
            return null;
        try {
            final List<Location> results = new LinkedList<>();

            // local autocomplete
            final Cursor cursor = activity.getContentResolver().query(
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
                final NetworkProvider networkProvider = getProvider();
                final EnumSet<LocationType> suggestedLocationTypes = EnumSet
                        .of(LocationType.STATION, LocationType.POI, LocationType.ADDRESS);
                final SuggestLocationsResult suggestLocationsResult = networkProvider
                        .suggestLocations(constraint, suggestedLocationTypes, 0);
                if (suggestLocationsResult.status == SuggestLocationsResult.Status.OK)
                    for (final Location location : suggestLocationsResult.getLocations())
                        if (!results.contains(location))
                            results.add(location);
            }

            return results;
        } catch (final IOException x) {
            x.printStackTrace();
            return null;
        }
    }

    @NonNull
    public NetworkProvider getProvider() {
        return NetworkProviderFactory.provider(network);
    }
}

