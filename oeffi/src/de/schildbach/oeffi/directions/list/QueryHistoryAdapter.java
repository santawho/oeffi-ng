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

package de.schildbach.oeffi.directions.list;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.BaseColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryHistoryProvider;
import de.schildbach.oeffi.directions.QueryStoredTripsProvider;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Point;

public class QueryHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final long ID_OFFSET_HISTORY = 0;
    private static final long ID_OFFSET_TRIPS = 1000000000000L;

    public interface ContextListener extends
            QueryHistoryViewHolder.ContextMenuItemListener,
            QueryStoredTripViewHolder.ContextListener {}

    private abstract class CursorBase<VHT extends RecyclerView.ViewHolder> {
        protected final Uri uri;
        protected Cursor cursor;
        protected ContentObserver contentObserver;
        protected int rowIdColumn;
        protected int numRows = -1;

        CursorBase(final Uri uri) {
            this.uri = uri;
            requery();
        }

        public void close() {
            numRows = -1;
            if (contentObserver != null) {
                contentResolver.unregisterContentObserver(contentObserver);
                contentObserver = null;
            }
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }

        protected String getSortOrder() {
            return null;
        }

        public void requery() {
            close();
            cursor = contentResolver.query(uri, null, null, null, getSortOrder());
            contentObserver = new ContentObserver(new Handler()) {
                @Override
                public void onChange(final boolean selfChange) {
                    requery();
                }
            };
            contentResolver.registerContentObserver(uri, true, contentObserver);
            rowIdColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            notifyDataSetChanged();
        }

        public int getCount() {
            if (numRows < 0)
                numRows = cursor.getCount();
            return numRows;
        }

        public long getItemId(final int position) {
            cursor.moveToPosition(position);
            return cursor.getLong(rowIdColumn);
        }

        public abstract void onBindViewHolder(final VHT holder, final int position);
    }

    private class HistoryCursor extends CursorBase<QueryHistoryViewHolder> {
        private final int fromTypeColumn;
        private final int fromIdColumn;
        private final int fromLatColumn;
        private final int fromLonColumn;
        private final int fromPlaceColumn;
        private final int fromNameColumn;
        private final int toTypeColumn;
        private final int toIdColumn;
        private final int toLatColumn;
        private final int toLonColumn;
        private final int toPlaceColumn;
        private final int toNameColumn;
        private final int viaTypeColumn;
        private final int viaIdColumn;
        private final int viaLatColumn;
        private final int viaLonColumn;
        private final int viaPlaceColumn;
        private final int viaNameColumn;
        private final int favoriteColumn;
        private final int savedTripDepartureTimeColumn;
        private final int savedTripColumn;

        HistoryCursor() {
            super(QueryHistoryProvider.CONTENT_URI_BUILDER(network, usage).build());
            fromTypeColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_TYPE);
            fromIdColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_ID);
            fromLatColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_LAT);
            fromLonColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_LON);
            fromPlaceColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_PLACE);
            fromNameColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_NAME);
            toTypeColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_TYPE);
            toIdColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_ID);
            toLatColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_LAT);
            toLonColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_LON);
            toPlaceColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_PLACE);
            toNameColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_NAME);
            viaTypeColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_VIA_TYPE);
            viaIdColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_VIA_ID);
            viaLatColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_VIA_LAT);
            viaLonColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_VIA_LON);
            viaPlaceColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_VIA_PLACE);
            viaNameColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_VIA_NAME);
            favoriteColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FAVORITE);
            savedTripDepartureTimeColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_LAST_DEPARTURE_TIME);
            savedTripColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_LAST_TRIP);
        }

        @Override
        protected String getSortOrder() {
            return QueryHistoryProvider.KEY_FAVORITE + " DESC, "
                    + QueryHistoryProvider.KEY_LAST_QUERIED + " DESC";
        }

        @Override
        public void onBindViewHolder(final QueryHistoryViewHolder holder, final int position) {
            cursor.moveToPosition(position);
            final long rowId = cursor.getLong(rowIdColumn);
            final LocationType fromType = QueryHistoryProvider.convert(cursor.getInt(fromTypeColumn));
            final String fromId = cursor.getString(fromIdColumn);
            final int fromLat = cursor.getInt(fromLatColumn);
            final int fromLon = cursor.getInt(fromLonColumn);
            final Point fromCoord = fromLat != 0 || fromLon != 0 ? Point.from1E6(fromLat, fromLon) : null;
            final String fromPlace = cursor.getString(fromPlaceColumn);
            final String fromName = cursor.getString(fromNameColumn);
            final Location from = new Location(fromType, fromId, fromCoord, fromPlace, fromName);
            final LocationType toType = QueryHistoryProvider.convert(cursor.getInt(toTypeColumn));
            final String toId = cursor.getString(toIdColumn);
            final int toLat = cursor.getInt(toLatColumn);
            final int toLon = cursor.getInt(toLonColumn);
            final Point toCoord = toLat != 0 || toLon != 0 ? Point.from1E6(toLat, toLon) : null;
            final String toPlace = cursor.getString(toPlaceColumn);
            final String toName = cursor.getString(toNameColumn);
            final Location to = new Location(toType, toId, toCoord, toPlace, toName);
            final LocationType viaType = QueryHistoryProvider.convert(cursor.getInt(viaTypeColumn));
            final String viaId = cursor.getString(viaIdColumn);
            final int viaLat = cursor.getInt(viaLatColumn);
            final int viaLon = cursor.getInt(viaLonColumn);
            final Point viaCoord = viaLat != 0 || viaLon != 0 ? Point.from1E6(viaLat, viaLon) : null;
            final String viaPlace = cursor.getString(viaPlaceColumn);
            final String viaName = cursor.getString(viaNameColumn);
            final Location via = viaType == LocationType.ANY ? null : new Location(viaType, viaId, viaCoord, viaPlace, viaName);
            final boolean isFavorite = cursor.getInt(favoriteColumn) == 1;
            final long savedTripDepartureTime = cursor.getLong(savedTripDepartureTimeColumn);
            final byte[] serializedSavedTrip = cursor.getBlob(savedTripColumn);
            final Integer fromFavState = FavoriteStationsProvider.favState(contentResolver, network, from);
            final Integer toFavState = FavoriteStationsProvider.favState(contentResolver, network, to);
            holder.bind(rowId, from, to, via,
                    isFavorite, savedTripDepartureTime, serializedSavedTrip, fromFavState, toFavState,
                    selectedRowId, clickListener, contextListener);
        }
    }

    private class TripsCursor extends CursorBase<QueryStoredTripViewHolder> {
        private final int fromTypeColumn;
        private final int fromIdColumn;
        private final int fromLatColumn;
        private final int fromLonColumn;
        private final int fromPlaceColumn;
        private final int fromNameColumn;
        private final int toTypeColumn;
        private final int toIdColumn;
        private final int toLatColumn;
        private final int toLonColumn;
        private final int toPlaceColumn;
        private final int toNameColumn;
        private final int viaTypeColumn;
        private final int viaIdColumn;
        private final int viaLatColumn;
        private final int viaLonColumn;
        private final int viaPlaceColumn;
        private final int viaNameColumn;
        private final int tripDepartureTimeColumn;
        private final int tripDepartureTimeOffsetColumn;
        private final int tripArrivalTimeColumn;
        private final int tripArrivalTimeOffsetColumn;
        private final int tripColumn;
        private final int tripIdColumn;
        private final int reloadRequestColumn;

        TripsCursor() {
            super(QueryStoredTripsProvider.CONTENT_URI_BUILDER(network, usage).build());
            fromTypeColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_FROM_TYPE);
            fromIdColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_FROM_ID);
            fromLatColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_FROM_LAT);
            fromLonColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_FROM_LON);
            fromPlaceColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_FROM_PLACE);
            fromNameColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_FROM_NAME);
            toTypeColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TO_TYPE);
            toIdColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TO_ID);
            toLatColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TO_LAT);
            toLonColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TO_LON);
            toPlaceColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TO_PLACE);
            toNameColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TO_NAME);
            viaTypeColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_VIA_TYPE);
            viaIdColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_VIA_ID);
            viaLatColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_VIA_LAT);
            viaLonColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_VIA_LON);
            viaPlaceColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_VIA_PLACE);
            viaNameColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_VIA_NAME);
            tripDepartureTimeColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_DEPARTURE_TIME);
            tripDepartureTimeOffsetColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_DEPARTURE_TIME_OFFSET);
            tripArrivalTimeColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_ARRIVAL_TIME);
            tripArrivalTimeOffsetColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_ARRIVAL_TIME_OFFSET);
            tripColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TRIP);
            tripIdColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TRIP_ID);
            reloadRequestColumn = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_RELOAD_REQUEST_DATA);
        }

        @Override
        protected String getSortOrder() {
            return "max(0," + refTime + "-" + QueryStoredTripsProvider.KEY_ARRIVAL_TIME + "),"
                    + QueryStoredTripsProvider.KEY_DEPARTURE_TIME;
        }

        @Override
        public void requery() {
            close();

            if (refTime > 0 && deleteTripsAfterMillis >= 0) {
                QueryStoredTripsProvider.deleteOlderTrips(context, network, usage, refTime - deleteTripsAfterMillis);
            }

            super.requery();
        }

        @Override
        public void onBindViewHolder(final QueryStoredTripViewHolder holder, final int position) {
            cursor.moveToPosition(position);
            final long rowId = cursor.getLong(rowIdColumn);
            final LocationType fromType = QueryHistoryProvider.convert(cursor.getInt(fromTypeColumn));
            final String fromId = cursor.getString(fromIdColumn);
            final int fromLat = cursor.getInt(fromLatColumn);
            final int fromLon = cursor.getInt(fromLonColumn);
            final Point fromCoord = fromLat != 0 || fromLon != 0 ? Point.from1E6(fromLat, fromLon) : null;
            final String fromPlace = cursor.getString(fromPlaceColumn);
            final String fromName = cursor.getString(fromNameColumn);
            final Location from = new Location(fromType, fromId, fromCoord, fromPlace, fromName);
            final LocationType toType = QueryHistoryProvider.convert(cursor.getInt(toTypeColumn));
            final String toId = cursor.getString(toIdColumn);
            final int toLat = cursor.getInt(toLatColumn);
            final int toLon = cursor.getInt(toLonColumn);
            final Point toCoord = toLat != 0 || toLon != 0 ? Point.from1E6(toLat, toLon) : null;
            final String toPlace = cursor.getString(toPlaceColumn);
            final String toName = cursor.getString(toNameColumn);
            final Location to = new Location(toType, toId, toCoord, toPlace, toName);
            final LocationType viaType = QueryHistoryProvider.convert(cursor.getInt(viaTypeColumn));
            final String viaId = cursor.getString(viaIdColumn);
            final int viaLat = cursor.getInt(viaLatColumn);
            final int viaLon = cursor.getInt(viaLonColumn);
            final Point viaCoord = viaLat != 0 || viaLon != 0 ? Point.from1E6(viaLat, viaLon) : null;
            final String viaPlace = cursor.getString(viaPlaceColumn);
            final String viaName = cursor.getString(viaNameColumn);
            final Location via = viaType == LocationType.ANY ? null : new Location(viaType, viaId, viaCoord, viaPlace, viaName);
            final long tripDepartureTimeValue = cursor.getLong(tripDepartureTimeColumn);
            final int tripDepartureTimeOffset = cursor.getInt(tripDepartureTimeOffsetColumn);
            final PTDate tripDepartureTime = tripDepartureTimeValue == 0 ? null : new PTDate(tripDepartureTimeValue, tripDepartureTimeOffset);
            final long tripArrivalTimeValue = cursor.getLong(tripArrivalTimeColumn);
            final int tripArrivalTimeOffset = cursor.getInt(tripArrivalTimeOffsetColumn);
            final PTDate tripArrivalTime = tripArrivalTimeValue == 0 ? null : new PTDate(tripArrivalTimeValue, tripArrivalTimeOffset);
            final byte[] serializedTrip = cursor.getBlob(tripColumn);
            final String tripId = cursor.getString(tripIdColumn);
            final byte[] serializedReloadRequest = QueryStoredTripsProvider.getReloadRequestColumnBlob(cursor, reloadRequestColumn);
            holder.bind(rowId,
                    from, to, via,
                    tripDepartureTime, tripArrivalTime,
                    serializedTrip, tripId,
                    serializedReloadRequest,
                    null,
                    selectedRowId, clickListener, contextListener);
        }
    }

    private final OeffiActivity context;
    private final ContentResolver contentResolver;
    private final LayoutInflater inflater;
    private final NetworkId network;
    private final String usage;
    private final QueryHistoryClickListener clickListener;
    private final ContextListener contextListener;
    private final int historyEntryLayoutId;
    private final long deleteTripsAfterMillis;
    private final int maxHistoryEntries;
    private final long upcomingTimeLimitMs;

    private HistoryCursor historyCursor;
    private TripsCursor tripsCursor;
    private long refTime;

    private long selectedRowId = RecyclerView.NO_ID;

    public QueryHistoryAdapter(
            final OeffiActivity context,
            final NetworkId network, final String usage,
            final QueryHistoryClickListener clickListener,
            final int historyEntryLayoutId,
            final ContextListener contextListener,
            final long deleteTripsAfterMillis,
            final int maxHistoryEntries,
            final long upcomingTimeLimitMs) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
        this.inflater = LayoutInflater.from(context);
        this.network = network;
        this.usage = usage;
        this.clickListener = clickListener;
        this.contextListener = contextListener;
        this.deleteTripsAfterMillis = deleteTripsAfterMillis;
        this.maxHistoryEntries = maxHistoryEntries;
        this.upcomingTimeLimitMs = upcomingTimeLimitMs;
        this.historyEntryLayoutId = historyEntryLayoutId;

        setHasStableIds(true);
    }

    public void close() {
        historyCursor.close();
        tripsCursor.close();
    }

    public void setRefTime(final long refTime) {
        this.refTime = refTime;

        if (tripsCursor == null)
            tripsCursor = new TripsCursor();
        else
            tripsCursor.requery();

        if (historyCursor == null)
            historyCursor = new HistoryCursor();
        else
            historyCursor.requery();
    }

    public Uri putEntry(final Location from, final Location to, final Location via) {
        final Uri uri = QueryHistoryProvider.put(contentResolver, network, usage, from, to, via, null, true, maxHistoryEntries);
        historyCursor.requery();
        return uri;
    }

    public void removeEntry(final int position) {
        final Uri uri = QueryHistoryProvider.historyRowUri(network, usage, getItemId(position));
        contentResolver.delete(uri, null, null);
        notifyItemRemoved(position);
        historyCursor.requery();
    }

    public void removeAllEntries(final boolean exceptFavorites) {
        final Uri uri = QueryHistoryProvider.CONTENT_URI_BUILDER(network, usage).build();
        contentResolver.delete(uri, exceptFavorites ? (QueryHistoryProvider.KEY_FAVORITE + "= 0") : null, null);
        notifyItemRangeRemoved(tripsCursor.getCount(), getItemCount());
        historyCursor.requery();
    }

    public void setIsFavorite(final int position, final boolean isFavorite) {
        final Uri uri = QueryHistoryProvider.historyRowUri(network, usage, getItemId(position));
        final ContentValues values = new ContentValues();
        values.put(QueryHistoryProvider.KEY_FAVORITE, isFavorite ? 1 : 0);
        contentResolver.update(uri, values, null, null);
        historyCursor.requery();
    }

    public void setSavedTrip(
            final int position, final long departureTime, final long arrivalTime,
            final byte[] serializedTrip) {
        final Uri uri = QueryHistoryProvider.historyRowUri(network, usage, getItemId(position));
        final ContentValues values = new ContentValues();
        values.put(QueryHistoryProvider.KEY_LAST_DEPARTURE_TIME, departureTime);
        values.put(QueryHistoryProvider.KEY_LAST_ARRIVAL_TIME, arrivalTime);
        values.put(QueryHistoryProvider.KEY_LAST_TRIP, serializedTrip);
        contentResolver.update(uri, values, null, null);
        notifyItemChanged(position);
        historyCursor.requery();
    }

    public void setSelectedEntry(final long rowId) {
        this.selectedRowId = rowId;
        notifyDataSetChanged();
    }

    public void clearSelectedEntry() {
        setSelectedEntry(RecyclerView.NO_ID);
    }

    @Override
    public int getItemCount() {
        return historyCursor.getCount() + tripsCursor.getCount();
    }

    @Override
    public long getItemId(final int position) {
        final int numTrips = tripsCursor.getCount();
        if (position < numTrips) {
            return tripsCursor.getItemId(position) + ID_OFFSET_TRIPS;
        } else {
            return historyCursor.getItemId(position - numTrips) + ID_OFFSET_HISTORY;
        }
    }

    @Override
    public int getItemViewType(final int position) {
        return position < tripsCursor.getCount()
                ? R.layout.directions_query_stored_trip_entry
                : historyEntryLayoutId;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        final View itemView = inflater.inflate(viewType, parent, false);
        if (viewType == historyEntryLayoutId) {
            return new QueryHistoryViewHolder(context, network, itemView);
        } else if (viewType == R.layout.directions_query_stored_trip_entry) {
            return new QueryStoredTripViewHolder(context, network, usage, upcomingTimeLimitMs, itemView);
        } else {
            throw new IllegalArgumentException("unexpected view type " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
        final int numTrips = tripsCursor.getCount();
        if (holder instanceof QueryHistoryViewHolder) {
            historyCursor.onBindViewHolder((QueryHistoryViewHolder) holder, position - numTrips);
        } else if (holder instanceof QueryStoredTripViewHolder) {
            tripsCursor.onBindViewHolder((QueryStoredTripViewHolder) holder, position);
        } else {
            throw new IllegalArgumentException("unexpected holder type " + holder.getClass().getName());
        }
    }
}
