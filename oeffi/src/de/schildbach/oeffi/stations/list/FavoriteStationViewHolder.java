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

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkResources;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;

public class FavoriteStationViewHolder extends RecyclerView.ViewHolder {
    private final Context context;
    private final StationClickListener clickListener;
    private final StationContextMenuItemListener contextMenuItemListener;
    private final TextView networkView;
    private final TextView placeView;
    private final TextView nameView;
    private final ImageButton contextButton;

    public FavoriteStationViewHolder(final View itemView, final Context context,
            final StationClickListener clickListener, final StationContextMenuItemListener contextMenuItemListener) {
        super(itemView);
        this.context = context;
        this.clickListener = clickListener;
        this.contextMenuItemListener = contextMenuItemListener;

        networkView = itemView.findViewById(R.id.favorites_list_entry_network);
        placeView = itemView.findViewById(R.id.favorites_list_entry_place);
        nameView = itemView.findViewById(R.id.favorites_list_entry_name);
        contextButton = itemView.findViewById(R.id.favorites_list_entry_context_button);
    }

    public void bind(final long rowId, final NetworkId network, final Location station, final boolean showNetwork,
            final long selectedRowId) {
        final boolean selected = rowId == selectedRowId;
        itemView.setActivated(selected);
        itemView.setOnClickListener(v -> {
            final int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION)
                clickListener.onStationClick(position, network, station);
        });

        if (showNetwork) {
            try {
                final NetworkResources networkRes = NetworkResources.instance(context, network.name());
                networkView.setText(networkRes.label);
            } catch (final Resources.NotFoundException x) {
                networkView.setText(network.name());
            }
        } else {
            networkView.setVisibility(View.GONE);
        }

        placeView.setText(station.place);
        nameView.setText(station.name);

        if (contextMenuItemListener != null) {
            contextButton.setVisibility(View.VISIBLE);
            contextButton.setOnClickListener(v -> {
                final PopupMenu contextMenu = new StationContextMenu(context, v, network, station,
                        FavoriteStationsProvider.TYPE_FAVORITE, true, false, true, false, false, false);
                contextMenu.setOnMenuItemClickListener(item -> {
                    final int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION)
                        return contextMenuItemListener.onStationContextMenuItemClick(position, network, station,
                                null, item.getItemId());
                    else
                        return false;
                });
                contextMenu.show();
            });
        } else {
            contextButton.setVisibility(View.GONE);
        }
    }
}
