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

package de.schildbach.oeffi.stations.list;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;

import javax.annotation.Nullable;

public class FavoriteStationsAdapter extends RecyclerView.Adapter<FavoriteStationViewHolder> {
    private final Context context;
    private final ContentResolver contentResolver;
    private final LayoutInflater inflater;
    private final boolean showNetwork;
    private final StationClickListener clickListener;
    @Nullable
    private final StationContextMenuItemListener contextMenuItemListener;

    private final Cursor cursor;
    private final int rowIdColumn;
    private final int networkColumn;

    private long selectedRowId = RecyclerView.NO_ID;

    public FavoriteStationsAdapter(final Context context, final NetworkId network,
            final StationClickListener clickListener,
            @Nullable final StationContextMenuItemListener contextMenuItemListener) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
        this.inflater = LayoutInflater.from(context);
        this.showNetwork = network == null;
        this.clickListener = clickListener;
        this.contextMenuItemListener = contextMenuItemListener;

        cursor = network != null ? //
                contentResolver.query(FavoriteStationsProvider.CONTENT_URI(), null,
                        FavoriteStationsProvider.KEY_TYPE + "=?" + " AND "
                                + FavoriteStationsProvider.KEY_STATION_NETWORK + "=?",
                        new String[] { String.valueOf(FavoriteStationsProvider.TYPE_FAVORITE), network.name() },
                        FavoriteStationsProvider.KEY_STATION_PLACE + "," + FavoriteStationsProvider.KEY_STATION_NAME)
                : //
                contentResolver.query(FavoriteStationsProvider.CONTENT_URI(), null,
                        FavoriteStationsProvider.KEY_TYPE + "=?",
                        new String[] { String.valueOf(FavoriteStationsProvider.TYPE_FAVORITE) },
                        FavoriteStationsProvider.KEY_STATION_NETWORK + "," + FavoriteStationsProvider.KEY_STATION_PLACE
                                + "," + FavoriteStationsProvider.KEY_STATION_NAME);
        rowIdColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID);
        networkColumn = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_NETWORK);

        setHasStableIds(true);
    }

    public void removeEntry(final int position) {
        final Uri uri = Uri.withAppendedPath(FavoriteStationsProvider.CONTENT_URI(), String.valueOf(getItemId(position)));
        contentResolver.delete(uri, null, null);
        notifyItemRemoved(position);
        cursor.requery();
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
        return cursor.getCount();
    }

    @Override
    public long getItemId(final int position) {
        cursor.moveToPosition(position);
        return cursor.getLong(rowIdColumn);
    }

    @Override
    public FavoriteStationViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new FavoriteStationViewHolder(inflater.inflate(R.layout.favorites_list_entry, parent, false), context,
                clickListener, contextMenuItemListener);
    }

    @Override
    public void onBindViewHolder(final FavoriteStationViewHolder holder, final int position) {
        cursor.moveToPosition(position);
        final long rowId = cursor.getLong(rowIdColumn);
        final NetworkId network = NetworkId.valueOf(cursor.getString(networkColumn));
        final Location station = FavoriteStationsProvider.getLocation(cursor);
        holder.bind(rowId, network, station, showNetwork, selectedRowId);
    }
}
