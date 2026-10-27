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

import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.util.HorizontalSwipeHelper;
import de.schildbach.oeffi.util.MapPopupMenu;
import de.schildbach.oeffi.util.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import javax.annotation.Nullable;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.oeffi.util.locationview.LocationTextView;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.PTDate;

public class QueryHistoryViewHolder extends RecyclerView.ViewHolder implements HorizontalSwipeHelper.HorizontalSwipeHolder {
    public interface ContextMenuItemListener {
        boolean onQueryHistoryContextMenuItemClick(
                int adapterPosition,
                Location from, Location to, Location via,
                @Nullable byte[] serializedSavedTrip, int menuItemId, @Nullable Location menuItemLocation);
    }

    private final DirectionsActivity context;
    private final NetworkId network;
    private final LocationTextView fromView;
    private final LocationTextView toView;
    private final LocationTextView viaView;
    private final View favoriteView;
    private final View viaContainerView;
    private final Button tripView;
    private final ImageButton contextButton;
    private QueryHistoryClickListener clickListener;
    private ContextMenuItemListener contextMenuItemListener;
    private Location from;
    private Location to;
    private Location via;
    private byte[] serializedSavedTrip;
    private boolean isFavorite;
    private Integer fromFavState;
    private Integer toFavState;
    private boolean hasSavedTrip;
    private PopupMenu contextMenu;

    public QueryHistoryViewHolder(
            final DirectionsActivity context,
            final NetworkId network,
            final View itemView) {
        super(itemView);
        this.context = context;
        this.network = network;

        fromView = itemView.findViewById(R.id.directions_query_history_entry_from);
        toView = itemView.findViewById(R.id.directions_query_history_entry_to);
        viaView = itemView.findViewById(R.id.directions_query_history_entry_via);
        viaContainerView = itemView.findViewById(R.id.directions_query_history_entry_via_container);
        favoriteView = itemView.findViewById(R.id.directions_query_history_entry_favorite);
        tripView = itemView.findViewById(R.id.directions_query_history_entry_trip);
        contextButton = itemView.findViewById(R.id.directions_query_history_entry_context_button);
    }

    public void bind(
            final long rowId,
            final Location from, final Location to, final Location via,
            final boolean isFavorite,
            final long savedTripDepartureTime, final byte[] serializedSavedTrip, final Integer fromFavState,
            final Integer toFavState, final long selectedRowId, final QueryHistoryClickListener clickListener,
            final ContextMenuItemListener contextMenuItemListener) {
        this.clickListener = clickListener;
        this.contextMenuItemListener = contextMenuItemListener;
        this.from = from;
        this.to = to;
        this.via = via;
        this.serializedSavedTrip = serializedSavedTrip;
        this.isFavorite = isFavorite;
        this.fromFavState = fromFavState;
        this.toFavState = toFavState;
        this.hasSavedTrip = savedTripDepartureTime > 0;

        fromView.setLocation(from);
        toView.setLocation(to);
        if (viaContainerView != null)
            ViewUtils.setVisibility(viaContainerView, via != null);
        if (viaView != null)
            viaView.setLocation(via);

        favoriteView.setVisibility(isFavorite ? View.VISIBLE : View.INVISIBLE);

        if (tripView != null) {
            if (hasSavedTrip) {
                tripView.setVisibility(View.VISIBLE);
                final long now = System.currentTimeMillis();
                tripView.setText(Formats.formatDate(context.getTimeZoneSelector(), now, savedTripDepartureTime, PTDate.NETWORK_OFFSET) + "\n"
                        + Formats.formatTime(context.getTimeZoneSelector(), savedTripDepartureTime, PTDate.NETWORK_OFFSET));
                tripView.setOnClickListener(v -> {
                    final int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION)
                        clickListener.onSavedTripClick(position,
                                from, to, via,
                                new PTDate(savedTripDepartureTime, PTDate.NETWORK_OFFSET), null,
                                serializedSavedTrip, null,
                                null,
                                true);
                });
            } else {
                tripView.setVisibility(View.GONE);
            }
        }

        final boolean selected = rowId == selectedRowId;
        itemView.setActivated(selected);
        itemView.setOnClickListener(v -> {
            final int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                clickListener.onEntryClick(position, from, to, via);
            }
        });
        itemView.setOnLongClickListener(v -> {
            showContextMenu(v);
            return true;
        });

        contextButton.setVisibility(selected ? View.VISIBLE : View.GONE);
        contextButton.setOnClickListener(this::showContextMenu);
    }

    private void showContextMenu(final View view) {
        final PopupMenu contextMenu = new PopupMenu(context, R.menu.directions_query_history_context, view);
        contextMenu.findItem(R.id.directions_query_history_context_show_trip).setVisible(hasSavedTrip);
        contextMenu.findItem(R.id.directions_query_history_context_remove_trip).setVisible(hasSavedTrip);
        contextMenu.findItem(R.id.directions_query_history_context_add_favorite).setVisible(!isFavorite);
        contextMenu.findItem(R.id.directions_query_history_context_remove_favorite).setVisible(isFavorite);
        final SubMenu fromMenu;
        if (from.isIdentified()) {
            fromMenu = contextMenu.addSubMenu(R.menu.directions_query_history_location_context, from.uniqueShortName());
            fromMenu.findItem(R.id.directions_query_history_location_context_details)
                    .setVisible(from.type == LocationType.STATION);
            fromMenu.findItem(R.id.directions_query_history_location_context_add_favorite)
                    .setVisible(from.type == LocationType.STATION && (fromFavState == null
                            || fromFavState != FavoriteStationsProvider.TYPE_FAVORITE));
            final MenuItem mapMenuItem = fromMenu.findItem(R.id.directions_query_history_location_context_map);
            if (from.hasCoord())
                MapPopupMenu.setupSubMenu(context, mapMenuItem, network, from);
            else
                mapMenuItem.setVisible(false);
        } else {
            fromMenu = null;
        }
        final SubMenu toMenu;
        if (to.isIdentified()) {
            toMenu = contextMenu.addSubMenu(R.menu.directions_query_history_location_context, to.uniqueShortName());
            toMenu.findItem(R.id.directions_query_history_location_context_details)
                    .setVisible(to.type == LocationType.STATION);
            toMenu.findItem(R.id.directions_query_history_location_context_add_favorite)
                    .setVisible(to.type == LocationType.STATION
                            && (toFavState == null || toFavState != FavoriteStationsProvider.TYPE_FAVORITE));
            final MenuItem mapMenuItem = toMenu.findItem(R.id.directions_query_history_location_context_map);
            if (to.hasCoord())
                MapPopupMenu.setupSubMenu(context, mapMenuItem, network, to);
            else
                mapMenuItem.setVisible(false);
        } else {
            toMenu = null;
        }
        contextMenu.setOnMenuItemClickListener(item -> {
            final int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                if (fromMenu != null && item == fromMenu.findItem(item.getItemId()))
                    return contextMenuItemListener.onQueryHistoryContextMenuItemClick(position, from, to, via,
                            serializedSavedTrip, item.getItemId(), from);
                else if (toMenu != null && item == toMenu.findItem(item.getItemId()))
                    return contextMenuItemListener.onQueryHistoryContextMenuItemClick(position, from, to, via,
                            serializedSavedTrip, item.getItemId(), to);
                else
                    return contextMenuItemListener.onQueryHistoryContextMenuItemClick(position, from, to, via,
                            serializedSavedTrip, item.getItemId(), null);
            } else {
                return false;
            }
        });
        contextMenu.setOnDismissListener(popupMenu -> {
            this.contextMenu = null;
        });
        contextMenu.show();
        this.contextMenu = contextMenu;
    }

    @Override
    public void onRevealLeft(final int adapterPosition) {
        isFavorite = !isFavorite;
        contextMenuItemListener.onQueryHistoryContextMenuItemClick(
                adapterPosition, from, to, via,
                serializedSavedTrip,
                isFavorite
                    ? R.id.directions_query_history_context_add_favorite
                    : R.id.directions_query_history_context_remove_favorite,
                null);
    }

    @Override
    public void onRevealRight(final int adapterPosition) {
        contextMenuItemListener.onQueryHistoryContextMenuItemClick(
                adapterPosition, from, to, via,
                serializedSavedTrip,
                R.id.directions_query_history_context_remove_entry,
                null);
    }

    @Override
    public Drawable getLeftIcon(final int adapterPosition) {
        return isFavorite ? context.drawableSwipeStarBorder : context.drawableSwipeStarSolid;
    }

    @Override
    public Drawable getRightIcon(final int adapterPosition) {
        return context.drawableSwipeDelete;
    }

    @Override
    public boolean canRevealLeft(final int adapterPosition) {
        return true;
    }

    @Override
    public boolean canRevealRight(final int adapterPosition) {
        return true;
    }
}
