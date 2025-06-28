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

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.daimajia.swipe.SimpleSwipeListener;
import com.daimajia.swipe.SwipeLayout;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.locationview.LocationTextView;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;

public class QueryHistoryViewHolder extends RecyclerView.ViewHolder {
    private final Context context;
    private final NetworkId network;
    private final LocationTextView fromView;
    private final LocationTextView toView;
    private final View favoriteView;
    private final Button tripView;
    private final ImageButton contextButton;
    private final SwipeLayout swipeLayout;
    private final MySwipeListener swipeListener;
    public QueryHistoryContextMenuItemListener contextMenuItemListener;
    private Location from;
    private Location to;
    private byte[] serializedSavedTrip;
    private boolean isFavorite;
    private Integer fromFavState;
    private Integer toFavState;
    private boolean hasSavedTrip;
    private PopupMenu contextMenu;

    public QueryHistoryViewHolder(final View itemView, final Context context, final NetworkId network) {
        super(itemView);
        this.context = context;
        this.network = network;

        fromView = itemView.findViewById(R.id.directions_query_history_entry_from);
        toView = itemView.findViewById(R.id.directions_query_history_entry_to);
        favoriteView = itemView.findViewById(R.id.directions_query_history_entry_favorite);
        tripView = itemView.findViewById(R.id.directions_query_history_entry_trip);
        contextButton = itemView.findViewById(R.id.directions_query_history_entry_context_button);

        this.swipeLayout = (SwipeLayout) itemView;
        swipeListener = new MySwipeListener();
        swipeLayout.addSwipeListener(swipeListener);
    }

    public void bind(final long rowId, final Location from, final Location to, final Location via, final boolean isFavorite,
            final long savedTripDepartureTime, final byte[] serializedSavedTrip, final Integer fromFavState,
            final Integer toFavState, final long selectedRowId, final QueryHistoryClickListener clickListener,
            final QueryHistoryContextMenuItemListener contextMenuItemListener) {
        this.contextMenuItemListener = contextMenuItemListener;
        this.from = from;
        this.to = to;
        this.serializedSavedTrip = serializedSavedTrip;
        this.isFavorite = isFavorite;
        this.fromFavState = fromFavState;
        this.toFavState = toFavState;

        fromView.setLocation(from);
        toView.setLocation(to);

        favoriteView.setVisibility(isFavorite ? View.VISIBLE : View.INVISIBLE);

        hasSavedTrip = savedTripDepartureTime > 0;
        if (hasSavedTrip) {
            tripView.setVisibility(View.VISIBLE);
            final long now = System.currentTimeMillis();
            tripView.setText(Formats.formatDate(context, now, savedTripDepartureTime) + "\n"
                    + Formats.formatTime(context, savedTripDepartureTime));
            tripView.setOnClickListener(v -> {
                final int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION)
                    clickListener.onSavedTripClick(position, serializedSavedTrip);
            });
        } else {
            tripView.setVisibility(View.GONE);
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
            final SubMenu mapMenu = fromMenu.findItem(R.id.directions_query_history_location_context_map)
                    .getSubMenu();
            StationContextMenu.prepareMapMenu(context, mapMenu, network, from);
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
            final SubMenu mapMenu = toMenu.findItem(R.id.directions_query_history_location_context_map)
                    .getSubMenu();
            StationContextMenu.prepareMapMenu(context, mapMenu, network, to);
        } else {
            toMenu = null;
        }
        contextMenu.setOnMenuItemClickListener(item -> {
            final int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                if (fromMenu != null && item == fromMenu.findItem(item.getItemId()))
                    return contextMenuItemListener.onQueryHistoryContextMenuItemClick(position, from, to,
                            serializedSavedTrip, item.getItemId(), from);
                else if (toMenu != null && item == toMenu.findItem(item.getItemId()))
                    return contextMenuItemListener.onQueryHistoryContextMenuItemClick(position, from, to,
                            serializedSavedTrip, item.getItemId(), to);
                else
                    return contextMenuItemListener.onQueryHistoryContextMenuItemClick(position, from, to,
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

    private class MySwipeListener extends SimpleSwipeListener {
        final ImageView starView;
        boolean starOpened;
        boolean removeOpened;
        boolean isFavorite;

        public MySwipeListener() {
            this.starView = swipeLayout.findViewById(R.id.directions_query_history_entry_swipe_star);
            setStarDrawable();

            swipeLayout.addRevealListener(R.id.directions_query_history_entry_swipe_star,
                    (child, edge, fraction, distance) -> {
                        starOpened = fraction > 0.999;
                    });
            swipeLayout.addRevealListener(R.id.directions_query_history_entry_swipe_remove,
                    (child, edge, fraction, distance) -> {
                        removeOpened = fraction > 0.999;
                    });
        }

        @Override
        public void onStartOpen(final SwipeLayout layout) {
            super.onStartOpen(layout);
            if (contextMenu != null) {
                contextMenu.dismiss();
                contextMenu = null;
            }
        }

        @Override
        public void onHandRelease(final SwipeLayout layout, final float xvel, final float yvel) {
            super.onHandRelease(layout, xvel, yvel);
            final int position = getAdapterPosition();

            if (starOpened) {
                starOpened = false;
                isFavorite = !isFavorite;
                setStarDrawable();
                contextMenuItemListener.onQueryHistoryContextMenuItemClick(
                        position, from, to,
                        serializedSavedTrip,
                        isFavorite
                            ? R.id.directions_query_history_context_add_favorite
                            : R.id.directions_query_history_context_remove_favorite,
                        null);
            }

            if (removeOpened) {
                removeOpened = false;
                contextMenuItemListener.onQueryHistoryContextMenuItemClick(
                        position, from, to,
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
}
