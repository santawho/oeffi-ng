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
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import de.schildbach.oeffi.util.SwipeLayout;

import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryStoredTripsProvider;
import de.schildbach.oeffi.directions.QueryTripRunnable;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.locationview.LocationTextView;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Trip;

public class QueryStoredTripViewHolder extends RecyclerView.ViewHolder
    implements SwipeLayout.SwipeListener {
    public interface ContextListener {
        boolean isTripUnderNavigation(final Context context, final String tripId);
    }

    private final OeffiActivity context;
    private final NetworkId network;
    private final String usage;
    private final View frameView;
    private final SwipeLayout swipeLayout;
    private final ImageView iconView;
    private final TextView timeLeftView;
    private final TextView dateView;
    private final TextView timeView;
    private final LocationTextView fromView;
    private final LocationTextView toView;
    private final long upcomingTimeLimitMs;

    private long rowId;
    private long selectedRowId;
    private QueryHistoryClickListener clickListener;
    private ContextListener contextListener;
    private Location from;
    private Location to;
    private Location via;
    private PTDate tripDepartureTime;
    private PTDate tripArrivalTime;
    private byte[] serializedSavedTrip;
    private byte[] serializedReloadRequest;
    private String tripId;
    private boolean canBeMarkedAsDone;
    private int stateFlags;
    private PopupMenu contextMenu;

    private boolean navigationOpened;
    private boolean removeOpened;

    public QueryStoredTripViewHolder(
            final OeffiActivity context,
            final NetworkId network,
            final String usage,
            final long upcomingTimeLimitMs,
            final View itemView) {
        super(itemView);
        this.context = context;
        this.network = network;
        this.usage = usage;
        this.upcomingTimeLimitMs = upcomingTimeLimitMs;

        iconView = itemView.findViewById(R.id.directions_query_stored_trip_entry_icon);
        timeLeftView = itemView.findViewById(R.id.directions_query_stored_trip_entry_time_left);
        dateView = itemView.findViewById(R.id.directions_query_stored_trip_entry_date);
        timeView = itemView.findViewById(R.id.directions_query_stored_trip_entry_time);
        fromView = itemView.findViewById(R.id.directions_query_stored_trip_entry_from);
        toView = itemView.findViewById(R.id.directions_query_stored_trip_entry_to);

        swipeLayout = (SwipeLayout) itemView;
        swipeLayout.addSwipeListener(this);

        swipeLayout.addRevealListener(R.id.directions_query_stored_trip_entry_swipe_navigate,
                (child, edge, fraction, distance) -> {
                    navigationOpened = fraction > 0.999;
                });
        swipeLayout.addRevealListener(R.id.directions_query_stored_trip_entry_swipe_remove,
                (child, edge, fraction, distance) -> {
                    removeOpened = fraction > 0.999;
                });

        frameView = itemView.findViewById(R.id.directions_query_stored_trip_entry_frame);
    }

    public void bind(
            final long rowId,
            final Location from, final Location to, final Location via,
            final PTDate tripDepartureTime, final PTDate tripArrivalTime,
            final byte[] serializedSavedTrip, final String tripId,
            final byte[] serializedReloadRequest,
            final boolean canBeMarkedAsDone, final int stateFlags,
            final long selectedRowId, final QueryHistoryClickListener clickListener,
            final ContextListener contextListener) {
        swipeLayout.resetCaches();
        this.rowId = rowId;
        this.from = from;
        this.to = to;
        this.via = via;
        this.tripDepartureTime = tripDepartureTime;
        this.tripArrivalTime = tripArrivalTime;
        this.serializedSavedTrip = serializedSavedTrip;
        this.serializedReloadRequest = serializedReloadRequest;
        this.tripId = tripId;
        this.canBeMarkedAsDone = canBeMarkedAsDone;
        this.stateFlags = stateFlags;
        this.selectedRowId = selectedRowId;
        this.clickListener = clickListener;
        this.contextListener = contextListener;

        render();
    }

    private void render() {
        fromView.setLocation(from);
        toView.setLocation(to);

        final long now = System.currentTimeMillis();
        final long departureTime = tripDepartureTime.getTime();
        final long arrivalTime = tripArrivalTime.getTime();
        final long timeToShow;

        final int backgroundId;
        int iconResId;
        String sTimeLeft = null;
        final long msTimeLeft;
        final long msLeftToArrival = arrivalTime - now;
        int timeLeftColorId = R.color.fg_significant;
        if (canBeMarkedAsDone
                ? (stateFlags & QueryStoredTripsProvider.STATE_FLAG_DONE) != 0
                : msLeftToArrival < 0) {
            // done
            timeToShow = arrivalTime;
            msTimeLeft = -msLeftToArrival;
            sTimeLeft = context.getString(R.string.directions_stored_trip_over_or_done);
            backgroundId = R.drawable.stored_trip_entry_background_finished;
            iconResId = R.drawable.ic_bookmarked_over_white_24dp;
        } else if (canBeMarkedAsDone && msLeftToArrival < -3600000) {
            // not marked as done and over since less than one hour
            timeToShow = arrivalTime;
            msTimeLeft = -msLeftToArrival;
            sTimeLeft = context.getString(R.string.directions_stored_trip_over_or_done);
            backgroundId = R.drawable.stored_trip_entry_background_current;
            iconResId = R.drawable.ic_bookmarked_over_white_24dp;
        } else {
            // present or future
            timeToShow = departureTime;
            iconResId = R.drawable.ic_bookmarked_white_24dp;
            final long msLeftToDeparture = departureTime - now;
            if (msLeftToDeparture < 0) {
                // present
                msTimeLeft = msLeftToArrival;
                backgroundId = R.drawable.stored_trip_entry_background_current;
                timeLeftColorId = R.color.fg_highlighted;
            } else {
                // future
                msTimeLeft = msLeftToDeparture;
                if (msLeftToDeparture < upcomingTimeLimitMs) {
                    // near future
                    backgroundId = R.drawable.stored_trip_entry_background_upcoming;
                } else {
                    // far future
                    backgroundId = R.drawable.stored_trip_entry_background_future;
                }
            }
        }
        if (contextListener.isTripUnderNavigation(context, tripId)) {
            iconResId = R.drawable.ic_navigation_white_24dp;
        }
        if (sTimeLeft == null && msTimeLeft >= 0) {
            sTimeLeft = Formats.formatTimeDiff(context, msTimeLeft, true, true);
        }
        final int timeLeftColor = context.getColor(timeLeftColorId);
        frameView.setBackground(AppCompatResources.getDrawable(context, backgroundId));
        timeLeftView.setText(sTimeLeft);
        timeLeftView.setTextColor(timeLeftColor);
        iconView.setImageResource(iconResId);
        iconView.setColorFilter(timeLeftColor);

        dateView.setText(Formats.formatDate(context.getTimeZoneSelector(), now, timeToShow, PTDate.NETWORK_OFFSET, true));
        timeView.setText(Formats.formatTime(context.getTimeZoneSelector(), timeToShow, PTDate.NETWORK_OFFSET));

        final boolean selected = rowId == selectedRowId;
        itemView.setActivated(selected);
        itemView.setOnClickListener(v -> {
            if (navigationWasOpened || removeWasOpened)
                return;
            final int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                if (contextListener.isTripUnderNavigation(context, tripId)) {
                    startNavigation(position, clickListener);
                } else {
                    clickListener.onSavedTripClick(position,
                            from, to, via,
                            tripDepartureTime, tripArrivalTime,
                            serializedSavedTrip, tripId,
                            serializedReloadRequest);
                }
            }
        });
        itemView.setOnLongClickListener(v -> {
            if (navigationOpened || removeOpened)
                return false;
            showContextMenu(v, clickListener);
            return true;
        });

        ((ImageView) itemView.findViewById(R.id.directions_query_stored_trip_entry_swipe_remove))
                .setImageDrawable(AppCompatResources.getDrawable(context,
                        canBeMarkedAsDone && (stateFlags & QueryStoredTripsProvider.STATE_FLAG_DONE) == 0
                                ? R.drawable.ic_check_black_24dp
                                : R.drawable.ic_delete_black_24dp));
    }

    private void startNavigation(final int position, final QueryHistoryClickListener clickListener) {
        final Trip trip = (Trip) Objects.deserialize(serializedSavedTrip, true);
        if (trip == null) {
            new Toast(context).longToast(R.string.directions_query_history_invalid_blob);
            return;
        }
        final QueryTripRunnable.TripRequestData requestData =
                (QueryTripRunnable.TripRequestData) Objects.deserialize(serializedReloadRequest, true);
        clickListener.onSavedTripStartNavigation(position, trip, requestData);
    }

    private void showContextMenu(
            final View view,
            final QueryHistoryClickListener clickListener) {
        final PopupMenu contextMenu = new PopupMenu(context, view);
        final MenuInflater inflater = contextMenu.getMenuInflater();
        final Menu menu = contextMenu.getMenu();
        inflater.inflate(R.menu.directions_query_stored_trip_context, menu);
        menu.findItem(R.id.directions_query_stored_trip_context_set_done).setVisible(canBeMarkedAsDone && (stateFlags & QueryStoredTripsProvider.STATE_FLAG_DONE) == 0);
        menu.findItem(R.id.directions_query_stored_trip_context_unset_done).setVisible(canBeMarkedAsDone && (stateFlags & QueryStoredTripsProvider.STATE_FLAG_DONE) != 0);
        contextMenu.setOnMenuItemClickListener(item -> {
            final int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                final int menuItemId = item.getItemId();
                if (menuItemId == R.id.directions_query_stored_trip_context_show) {
                    clickListener.onSavedTripClick(position,
                            from, to, via,
                            tripDepartureTime, tripArrivalTime,
                            serializedSavedTrip, tripId,
                            serializedReloadRequest);
                    return true;
                }
                if (menuItemId == R.id.directions_query_stored_trip_context_navigate) {
                    startNavigation(position, clickListener);
                    return true;
                }
                if (menuItemId == R.id.directions_query_stored_trip_context_remove) {
                    if (tripId != null) {
                        QueryStoredTripsProvider.delete(context.getContentResolver(), network, usage, tripId);
                    }
                    return true;
                }
                if (menuItemId == R.id.directions_query_stored_trip_context_search) {
                    final QueryTripRunnable.TripRequestData requestData = (QueryTripRunnable.TripRequestData)
                            Objects.deserialize(serializedReloadRequest, true);
                    if (requestData != null)
                        clickListener.onSearchAgainClick(position,
                                tripDepartureTime, tripArrivalTime, requestData);
                    return true;
                }
                if (menuItemId == R.id.directions_query_stored_trip_context_set_done) {
                    stateFlags |= QueryStoredTripsProvider.STATE_FLAG_DONE;
                    QueryStoredTripsProvider.updateStateFlags(context.getContentResolver(), network, usage, tripId, stateFlags);
                    return true;
                }
                if (menuItemId == R.id.directions_query_stored_trip_context_unset_done) {
                    stateFlags &=~ QueryStoredTripsProvider.STATE_FLAG_DONE;
                    QueryStoredTripsProvider.updateStateFlags(context.getContentResolver(), network, usage, tripId, stateFlags);
                    return true;
                }
            }
            return false;
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

        handWasReleased = false;
        navigationWasOpened = false;
        removeWasOpened = false;
    }

    private boolean handWasReleased;
    private boolean navigationWasOpened;
    private boolean removeWasOpened;

    @Override
    public void onHandRelease(final SwipeLayout layout, final float xvel, final float yvel) {
        handWasReleased = true;

        navigationWasOpened = navigationOpened;
        navigationOpened = false;

        removeWasOpened = removeOpened;
        removeOpened = false;

        swipeLayout.close();
    }

    @Override
    public boolean onLongClick(final SwipeLayout layout) {
        return !(navigationOpened || removeOpened);
    }

    @Override
    public void onEndClose(final SwipeLayout layout) {
        if (!handWasReleased)
            return;
        handWasReleased = false;

        final int position = getAdapterPosition();

        if (navigationWasOpened) {
            navigationWasOpened = false;
            startNavigation(position, clickListener);
        } else if (removeWasOpened) {
            removeWasOpened = false;
            if (tripId != null) {
                if (canBeMarkedAsDone && (stateFlags & QueryStoredTripsProvider.STATE_FLAG_DONE) == 0) {
                    this.stateFlags |= QueryStoredTripsProvider.STATE_FLAG_DONE;
                    QueryStoredTripsProvider.updateStateFlags(context.getContentResolver(), network, usage, tripId, stateFlags);
                } else {
                    QueryStoredTripsProvider.delete(context.getContentResolver(), network, usage, tripId);
                }
            }
//        } else {
//            if (contextListener.isTripUnderNavigation(context, tripId)) {
//                startNavigation(position, clickListener);
//            } else {
//                clickListener.onSavedTripClick(position,
//                        from, to, via,
//                        tripDepartureTime, tripArrivalTime,
//                        serializedSavedTrip, tripId,
//                        serializedReloadRequest);
//            }
        }
    }
}
