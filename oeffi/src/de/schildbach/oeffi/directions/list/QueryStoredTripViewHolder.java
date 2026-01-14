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

import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.daimajia.swipe.SimpleSwipeListener;
import com.daimajia.swipe.SwipeLayout;

import javax.annotation.Nullable;

import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryStoredTripsProvider;
import de.schildbach.oeffi.directions.QueryTripsRunnable;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.navigation.TripNavigatorActivity;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.locationview.LocationTextView;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Trip;

public class QueryStoredTripViewHolder extends RecyclerView.ViewHolder {
    public interface ContextMenuItemListener {
        boolean onQueryStoredTripContextMenuItemClick(
                int adapterPosition,
                Location from, Location to, Location via,
                @Nullable byte[] serializedSavedTrip, int menuItemId, @Nullable Location menuItemLocation);
    }

    private final OeffiActivity context;
    private final NetworkId network;
    private final TextView dateView;
    private final TextView timeView;
    private final LocationTextView fromView;
    private final LocationTextView toView;
    private final SwipeLayout swipeLayout;
    private final MySwipeListener swipeListener;
    public ContextMenuItemListener contextMenuItemListener;
    private Location from;
    private Location to;
    private Location via;
    private PTDate tripDepartureTime;
    private PTDate tripArrivalTime;
    private byte[] serializedSavedTrip;
    private byte[] serializedReloadRequest;
    private String tripId;
    private PopupMenu contextMenu;

    public QueryStoredTripViewHolder(
            final OeffiActivity context,
            final NetworkId network,
            final View itemView) {
        super(itemView);
        this.context = context;
        this.network = network;

        dateView = itemView.findViewById(R.id.directions_query_stored_trip_entry_date);
        timeView = itemView.findViewById(R.id.directions_query_stored_trip_entry_time);
        fromView = itemView.findViewById(R.id.directions_query_stored_trip_entry_from);
        toView = itemView.findViewById(R.id.directions_query_stored_trip_entry_to);

        this.swipeLayout = (SwipeLayout) itemView;
        swipeListener = new MySwipeListener();
        swipeLayout.addSwipeListener(swipeListener);
    }

    public void bind(
            final long rowId,
            final Location from, final Location to, final Location via,
            final PTDate tripDepartureTime, final PTDate tripArrivalTime,
            final byte[] serializedSavedTrip, final String tripId,
            final byte[] serializedReloadRequest,
            final long selectedRowId, final QueryHistoryClickListener clickListener,
            final ContextMenuItemListener contextMenuItemListener) {
        this.contextMenuItemListener = contextMenuItemListener;
        this.from = from;
        this.to = to;
        this.via = via;
        this.tripDepartureTime = tripDepartureTime;
        this.tripArrivalTime = tripArrivalTime;
        this.serializedSavedTrip = serializedSavedTrip;
        this.serializedReloadRequest = serializedReloadRequest;
        this.tripId = tripId;

        fromView.setLocation(from);
        toView.setLocation(to);

        final long now = System.currentTimeMillis();
        final String departureDate = Formats.formatDate(context.getTimeZoneSelector(), now, tripDepartureTime, PTDate.NETWORK_OFFSET);
        final String departureTime = Formats.formatTime(context.getTimeZoneSelector(), tripDepartureTime, PTDate.NETWORK_OFFSET);
        dateView.setText(departureDate);
        timeView.setText(departureTime);

        final boolean selected = rowId == selectedRowId;
        itemView.setActivated(selected);
        itemView.setOnClickListener(v -> {
            swipeLayout.close(true, false);
            final int position = getAdapterPosition();
            if (navigationOpened) {
                navigationOpened = false;
                startNavigation();
            } else if (removeOpened) {
                removeOpened = false;
                if (tripId != null) {
                    QueryStoredTripsProvider.delete(context.getContentResolver(), network, tripId);
                }
            } else if (position != RecyclerView.NO_POSITION) {
                clickListener.onSavedTripClick(position, serializedSavedTrip);
            }
        });
//        itemView.setOnLongClickListener(v -> {
//            if (navigationOpened) {
//                navigationOpened = false;
//                openNavigation();
//            } else if (removeOpened) {
//                removeOpened = false;
//                deleteEntry();
//            } else {
//                showContextMenu(v);
//            }
//            return true;
//        });
    }

    private void startNavigation() {
        final TripDetailsActivity.RenderConfig renderConfig = new TripDetailsActivity.RenderConfig();
        final Trip trip = (Trip) Objects.deserialize(serializedSavedTrip, true);
        renderConfig.queryTripsRequestData = (QueryTripsRunnable.TripRequestData) Objects.deserialize(serializedReloadRequest, true);
        if (trip == null || renderConfig.queryTripsRequestData == null) {
            new Toast(context).longToast(R.string.directions_query_history_invalid_blob);
            return;
        }
        TripNavigatorActivity.startNavigation(context, network, trip, renderConfig, false);

    }

    private void showContextMenu(final View view) {
//        final PopupMenu contextMenu = new PopupMenu(context, view);
//        final MenuInflater inflater = contextMenu.getMenuInflater();
//        final Menu menu = contextMenu.getMenu();
//        inflater.inflate(R.menu.directions_query_stored_trip_context, menu);
//        menu.findItem(R.id.directions_query_stored_trip_context_show_trip).setVisible(hasSavedTrip);
//        menu.findItem(R.id.directions_query_stored_trip_context_remove_trip).setVisible(hasSavedTrip);
//        menu.findItem(R.id.directions_query_stored_trip_context_add_favorite).setVisible(!isFavorite);
//        menu.findItem(R.id.directions_query_stored_trip_context_remove_favorite).setVisible(isFavorite);
//        final SubMenu fromMenu;
//        if (from.isIdentified()) {
//            fromMenu = menu.addSubMenu(from.uniqueShortName());
//            inflater.inflate(R.menu.directions_query_stored_trip_location_context, fromMenu);
//            fromMenu.findItem(R.id.directions_query_stored_trip_location_context_details)
//                    .setVisible(from.type == LocationType.STATION);
//            fromMenu.findItem(R.id.directions_query_stored_trip_location_context_add_favorite)
//                    .setVisible(from.type == LocationType.STATION && (fromFavState == null
//                            || fromFavState != FavoriteStationsProvider.TYPE_FAVORITE));
//            final MenuItem mapMenuItem = fromMenu.findItem(R.id.directions_query_stored_trip_location_context_map);
//            if (from.hasCoord())
//                StationContextMenu.prepareMapMenu(context, mapMenuItem.getSubMenu(), network, from);
//            else
//                mapMenuItem.setVisible(false);
//        } else {
//            fromMenu = null;
//        }
//        final SubMenu toMenu;
//        if (to.isIdentified()) {
//            toMenu = menu.addSubMenu(to.uniqueShortName());
//            inflater.inflate(R.menu.directions_query_stored_trip_location_context, toMenu);
//            toMenu.findItem(R.id.directions_query_stored_trip_location_context_details)
//                    .setVisible(to.type == LocationType.STATION);
//            toMenu.findItem(R.id.directions_query_stored_trip_location_context_add_favorite)
//                    .setVisible(to.type == LocationType.STATION
//                            && (toFavState == null || toFavState != FavoriteStationsProvider.TYPE_FAVORITE));
//            final MenuItem mapMenuItem = toMenu.findItem(R.id.directions_query_stored_trip_location_context_map);
//            if (to.hasCoord())
//                StationContextMenu.prepareMapMenu(context, mapMenuItem.getSubMenu(), network, to);
//            else
//                mapMenuItem.setVisible(false);
//        } else {
//            toMenu = null;
//        }
//        contextMenu.setOnMenuItemClickListener(item -> {
//            final int position = getAdapterPosition();
//            if (position != RecyclerView.NO_POSITION) {
//                if (fromMenu != null && item == fromMenu.findItem(item.getItemId()))
//                    return contextMenuItemListener.onQueryStoredTripContextMenuItemClick(position, from, to, via,
//                            serializedSavedTrip, item.getItemId(), from);
//                else if (toMenu != null && item == toMenu.findItem(item.getItemId()))
//                    return contextMenuItemListener.onQueryStoredTripContextMenuItemClick(position, from, to, via,
//                            serializedSavedTrip, item.getItemId(), to);
//                else
//                    return contextMenuItemListener.onQueryStoredTripContextMenuItemClick(position, from, to, via,
//                            serializedSavedTrip, item.getItemId(), null);
//            } else {
//                return false;
//            }
//        });
//        contextMenu.setOnDismissListener(popupMenu -> {
//            this.contextMenu = null;
//        });
//        contextMenu.show();
//        this.contextMenu = contextMenu;
    }

    boolean navigationOpened;
    boolean removeOpened;

    private class MySwipeListener extends SimpleSwipeListener {
        public MySwipeListener() {
            swipeLayout.addRevealListener(R.id.directions_query_stored_trip_entry_swipe_navigate,
                    (child, edge, fraction, distance) -> {
                        navigationOpened = fraction > 0.999;
                    });
            swipeLayout.addRevealListener(R.id.directions_query_stored_trip_entry_swipe_remove,
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

//            final int position = getAdapterPosition();

//            if (navigationOpened) {
//                navigationOpened = false;
//                isFavorite = !isFavorite;
//                setStarDrawable();
//                contextMenuItemListener.onQueryStoredTripContextMenuItemClick(
//                        position, from, to, via,
//                        serializedSavedTrip,
//                        isFavorite
//                            ? R.id.directions_query_stored_trip_context_add_favorite
//                            : R.id.directions_query_stored_trip_context_remove_favorite,
//                        null);
//            }

//            if (removeOpened) {
//                removeOpened = false;
//                if (tripId != null) {
//                    QueryStoredTripsProvider.delete(context.getContentResolver(), network, tripId);
//                }
//            }
        }
    }
}
