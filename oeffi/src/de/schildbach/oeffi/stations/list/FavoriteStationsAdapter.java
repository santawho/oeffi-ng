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

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
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

    public FavoriteStationsAdapter(
            final Context context, final NetworkId network,
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

    public void renameEntry(final int position) {
        cursor.moveToPosition(position);
        final int nameCol = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_NAME);
        final int placeCol = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_PLACE);
        final int nickNameCol = cursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_NICKNAME);
        final String name = cursor.getString(nameCol);
        final String place = cursor.getString(placeCol);
        final String nickName = cursor.getString(nickNameCol);
        final Uri uri = Uri.withAppendedPath(FavoriteStationsProvider.CONTENT_URI(), String.valueOf(getItemId(position)));

        final EditText editText = new EditText(context);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        final String defaultName = place == null ? name : name + ", " + place;
        editText.setText(nickName != null ? nickName : defaultName);
        editText.setHint(defaultName);
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.stations_favorite_stations_rename_title, defaultName))
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    final String newNickName = editText.getText().toString();
                    final ContentValues values = new ContentValues();
                    if (newNickName.isEmpty())
                        values.putNull(FavoriteStationsProvider.KEY_STATION_NICKNAME);
                    else
                        values.put(FavoriteStationsProvider.KEY_STATION_NICKNAME, newNickName);
                    contentResolver.update(uri, values, null, null);
                    notifyItemRemoved(position);
                    cursor.requery();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create().show();
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

    @NonNull
    @Override
    public FavoriteStationViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        return new FavoriteStationViewHolder(
                inflater.inflate(R.layout.favorites_list_entry, parent, false),
                context, clickListener, contextMenuItemListener);
    }

    @Override
    public void onBindViewHolder(final FavoriteStationViewHolder holder, final int position) {
        cursor.moveToPosition(position);
        final long rowId = cursor.getLong(rowIdColumn);
        final NetworkId network = NetworkId.valueOf(cursor.getString(networkColumn));
        final Location station = FavoriteStationsProvider.getLocation(cursor).getNick();
        holder.bind(rowId, network, station, showNetwork, selectedRowId);
    }
}
