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

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import de.schildbach.oeffi.util.SwipeLayout;

import javax.annotation.Nullable;

import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.oeffi.util.locationview.LocationTextView;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.PTDate;

public class QueryHistoryViewHolder extends RecyclerView.ViewHolder
        implements SwipeLayout.SwipeListener {
    public interface ContextMenuItemListener {
        boolean onQueryHistoryContextMenuItemClick(
                int adapterPosition,
                Location from, Location to, Location via,
                @Nullable byte[] serializedSavedTrip, int menuItemId, @Nullable Location menuItemLocation);
    }

    private final OeffiActivity context;
    private final NetworkId network;
    private final SwipeLayout swipeLayout;
    private final LocationTextView fromView;
    private final LocationTextView toView;
    private final LocationTextView viaView;
    private final View favoriteView;
    private final View viaContainerView;
    private final Button tripView;
    private final ImageButton contextButton;
    public ContextMenuItemListener contextMenuItemListener;
    private Location from;
    private Location to;
    private Location via;
    private byte[] serializedSavedTrip;
    private boolean isFavorite;
    private Integer fromFavState;
    private Integer toFavState;
    private boolean hasSavedTrip;
    private PopupMenu contextMenu;
    private final ImageView starView;
    private boolean starOpened;
    private boolean removeOpened;

    public QueryHistoryViewHolder(
            final OeffiActivity context,
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

        swipeLayout = (SwipeLayout) itemView;
        starView = swipeLayout.findViewById(R.id.directions_query_history_entry_swipe_star);
        setStarDrawable();

        swipeLayout.addRevealListener(R.id.directions_query_history_entry_swipe_star,
                (child, edge, fraction, distance) -> {
                    starOpened = fraction > 0.999;
                });
        swipeLayout.addRevealListener(R.id.directions_query_history_entry_swipe_remove,
                (child, edge, fraction, distance) -> {
                    removeOpened = fraction > 0.999;
                });

        swipeLayout.addSwipeListener(this);
    }

    public void bind(
            final long rowId,
            final Location from, final Location to, final Location via,
            final boolean isFavorite,
            final long savedTripDepartureTime, final byte[] serializedSavedTrip, final Integer fromFavState,
            final Integer toFavState, final long selectedRowId, final QueryHistoryClickListener clickListener,
            final ContextMenuItemListener contextMenuItemListener) {
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
                                null);
                });
            } else {
                tripView.setVisibility(View.GONE);
            }
        }

        final boolean selected = rowId == selectedRowId;
        itemView.setActivated(selected);
        itemView.setOnClickListener(v -> {
            final int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION)
                clickListener.onEntryClick(position, from, to, via);
        });
        itemView.setOnLongClickListener(v -> {
            showContextMenu(v);
            return true;
        });

        contextButton.setVisibility(selected ? View.VISIBLE : View.GONE);
        contextButton.setOnClickListener(this::showContextMenu);
    }

    private void showContextMenu(final View view) {
        final PopupMenu contextMenu = new PopupMenu(context, view);
        final MenuInflater inflater = contextMenu.getMenuInflater();
        final Menu menu = contextMenu.getMenu();
        inflater.inflate(R.menu.directions_query_history_context, menu);
        menu.findItem(R.id.directions_query_history_context_show_trip).setVisible(hasSavedTrip);
        menu.findItem(R.id.directions_query_history_context_remove_trip).setVisible(hasSavedTrip);
        menu.findItem(R.id.directions_query_history_context_add_favorite).setVisible(!isFavorite);
        menu.findItem(R.id.directions_query_history_context_remove_favorite).setVisible(isFavorite);
        final SubMenu fromMenu;
        if (from.isIdentified()) {
            fromMenu = menu.addSubMenu(from.uniqueShortName());
            inflater.inflate(R.menu.directions_query_history_location_context, fromMenu);
            fromMenu.findItem(R.id.directions_query_history_location_context_details)
                    .setVisible(from.type == LocationType.STATION);
            fromMenu.findItem(R.id.directions_query_history_location_context_add_favorite)
                    .setVisible(from.type == LocationType.STATION && (fromFavState == null
                            || fromFavState != FavoriteStationsProvider.TYPE_FAVORITE));
            final MenuItem mapMenuItem = fromMenu.findItem(R.id.directions_query_history_location_context_map);
            if (from.hasCoord())
                StationContextMenu.prepareMapMenu(context, mapMenuItem.getSubMenu(), network, from);
            else
                mapMenuItem.setVisible(false);
        } else {
            fromMenu = null;
        }
        final SubMenu toMenu;
        if (to.isIdentified()) {
            toMenu = menu.addSubMenu(to.uniqueShortName());
            inflater.inflate(R.menu.directions_query_history_location_context, toMenu);
            toMenu.findItem(R.id.directions_query_history_location_context_details)
                    .setVisible(to.type == LocationType.STATION);
            toMenu.findItem(R.id.directions_query_history_location_context_add_favorite)
                    .setVisible(to.type == LocationType.STATION
                            && (toFavState == null || toFavState != FavoriteStationsProvider.TYPE_FAVORITE));
            final MenuItem mapMenuItem = toMenu.findItem(R.id.directions_query_history_location_context_map);
            if (to.hasCoord())
                StationContextMenu.prepareMapMenu(context, mapMenuItem.getSubMenu(), network, to);
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
    public void onStartOpen(final SwipeLayout layout) {
        if (contextMenu != null) {
            contextMenu.dismiss();
            contextMenu = null;
        }
    }

    @Override
    public void onHandRelease(final SwipeLayout layout, final float xvel, final float yvel) {
        swipeLayout.close();
        final int position = getAdapterPosition();

        if (starOpened) {
            starOpened = false;
            isFavorite = !isFavorite;
            setStarDrawable();
            contextMenuItemListener.onQueryHistoryContextMenuItemClick(
                    position, from, to, via,
                    serializedSavedTrip,
                    isFavorite
                        ? R.id.directions_query_history_context_add_favorite
                        : R.id.directions_query_history_context_remove_favorite,
                    null);
        }

        if (removeOpened) {
            removeOpened = false;
            contextMenuItemListener.onQueryHistoryContextMenuItemClick(
                    position, from, to, via,
                    serializedSavedTrip,
                    R.id.directions_query_history_context_remove_entry,
                    null);
        }
    }

    private void setStarDrawable() {
        starView.setImageDrawable(isFavorite
                ? context.getDrawable(R.drawable.ic_star_border_white_24dp)
                : context.getDrawable(R.drawable.ic_star_white_24dp));
    }
}
