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
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.base.Joiner;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.stations.CompassNeedleView;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.stations.QueryDeparturesRunnable;
import de.schildbach.oeffi.stations.Station;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.pte.Standard;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryDeparturesResult;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class StationViewHolder extends RecyclerView.ViewHolder {
    public final View favoriteView;
    public final TextView nameView;
    public final TextView name2View;
    public final LineView linesView;
    public final TextView distanceView;
    public final CompassNeedleView bearingView;
    public final ImageButton contextButton;
    public final View contextButtonSpace;
    public final ViewGroup departuresViewGroup;
    public final TextView departuresStatusView;
    public final ViewGroup messagesViewGroup;

    private final Context context;
    private final Resources res;
    private final int maxDepartures;
    private final StationContextMenuItemListener contextMenuItemListener;
    private final JourneyClickListener journeyClickListener;

    private final LayoutInflater inflater;
    private final Display display;
    private final int colorArrow;
    private final int colorSignificant, colorLessSignificant, colorInsignificant, colorHighlighted;
    private final int listEntryVerticalPadding;

    private static final int CONDENSE_LINES_THRESHOLD = 5;
    private static final int MESSAGE_INDEX_COLOR = Color.parseColor("#c08080");

    public StationViewHolder(
            final Context context, final View itemView, final int maxDepartures,
            final StationContextMenuItemListener contextMenuItemListener,
            final JourneyClickListener journeyClickListener) {
        super(itemView);

        favoriteView = itemView.findViewById(R.id.station_entry_favorite);
        nameView = itemView.findViewById(R.id.station_entry_name);
        name2View = itemView.findViewById(R.id.station_entry_name2);
        linesView = itemView.findViewById(R.id.station_entry_lines);
        distanceView = itemView.findViewById(R.id.station_entry_distance);
        bearingView = itemView.findViewById(R.id.station_entry_bearing);
        contextButton = itemView.findViewById(R.id.station_entry_context_button);
        contextButtonSpace = itemView.findViewById(R.id.station_entry_context_button_space);
        departuresViewGroup = itemView.findViewById(R.id.station_entry_departures);
        departuresStatusView = itemView.findViewById(R.id.station_entry_status);
        messagesViewGroup = itemView.findViewById(R.id.station_entry_messages);

        this.context = context;
        this.res = context.getResources();
        this.maxDepartures = maxDepartures;
        this.contextMenuItemListener = contextMenuItemListener;
        this.journeyClickListener = journeyClickListener;

        this.inflater = LayoutInflater.from(context);
        this.display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        this.colorArrow = res.getColor(R.color.fg_arrow);
        this.colorSignificant = res.getColor(R.color.fg_significant);
        this.colorLessSignificant = res.getColor(R.color.fg_less_significant);
        this.colorInsignificant = res.getColor(R.color.fg_insignificant);
        this.colorHighlighted = res.getColor(R.color.fg_highlighted);
        this.listEntryVerticalPadding = res.getDimensionPixelOffset(R.dimen.text_padding_vertical);
    }

    public void bind(final Station station, final Set<Product> productsFilter, final boolean forceShowPlace,
            final Integer favState, final android.location.Location deviceLocation,
            final CompassNeedleView.Callback compassCallback) {
        final long currentTime = System.currentTimeMillis();

        final boolean queryNotOk = station.departureQueryStatus != null
                && station.departureQueryStatus != QueryDeparturesResult.Status.OK;
        final boolean isFavorite = favState != null && favState == FavoriteStationsProvider.TYPE_FAVORITE;
        final boolean isIgnored = favState != null && favState == FavoriteStationsProvider.TYPE_IGNORE;
        final boolean isGhosted = isIgnored || queryNotOk;

        final int colorSignificant = !isGhosted ? this.colorSignificant : colorInsignificant;
        final int colorLessSignificant = !isGhosted ? this.colorLessSignificant : colorInsignificant;
        final int colorHighlighted = !isGhosted ? this.colorHighlighted : colorInsignificant;

        // favorite
        favoriteView.setVisibility(isFavorite ? View.VISIBLE : View.GONE);

        // name/place
        final boolean showPlace = forceShowPlace || itemView.isActivated();
        nameView.setText(showPlace ? station.location.place : station.location.uniqueShortName());
        nameView.setTypeface(showPlace ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
        nameView.setTextColor(colorSignificant);
        name2View.setVisibility(showPlace ? View.VISIBLE : View.GONE);
        name2View.setText(station.location.name);
        name2View.setTextColor(colorSignificant);

        // lines
        final Set<Line> lines = new TreeSet<>();
        final Set<Product> products = station.location.products;
        if (products != null)
            for (final Product product : products)
                lines.add(new Line(null, null, product, null, Standard.STYLES.get(product)));
        final List<LineDestination> stationLines = station.getLines();
        if (stationLines != null) {
            for (final LineDestination lineDestination : stationLines) {
                final Line line = lineDestination.line;
                lines.add(line);
                lines.remove(new Line(null, null, line.product, null, Standard.STYLES.get(line.product)));
            }
        }
        linesView.setGhosted(isGhosted);
        linesView.setCondenseThreshold(CONDENSE_LINES_THRESHOLD);
        linesView.setLines(!lines.isEmpty() ? lines : null);

        // distance
        distanceView.setText(station.hasDistanceAndBearing ? Formats.formatDistance(station.distance) : null);
        distanceView.setVisibility(station.hasDistanceAndBearing ? View.VISIBLE : View.GONE);
        distanceView.setTextColor(colorSignificant);

        // bearing
        if (deviceLocation != null && station.hasDistanceAndBearing) {
            if (!deviceLocation.hasAccuracy()
                    || (deviceLocation.getAccuracy() / station.distance) < Constants.BEARING_ACCURACY_THRESHOLD)
                bearingView.setStationBearing(station.bearing);
            else
                bearingView.setStationBearing(null);
            bearingView.setCallback(compassCallback);
            bearingView.setDisplayRotation(display.getRotation());
            bearingView.setArrowColor(!isGhosted ? colorArrow : colorInsignificant);
            bearingView.setVisibility(View.VISIBLE);
        } else {
            bearingView.setVisibility(View.GONE);
        }

        // context button
        contextButton.setVisibility(itemView.isActivated() ? View.VISIBLE : View.GONE);
        contextButtonSpace.setVisibility(itemView.isActivated() ? View.VISIBLE : View.GONE);
        contextButton.setOnClickListener(itemView.isActivated() ? v -> {
            final PopupMenu contextMenu = new StationContextMenu(context, v, station.network, station.location,
                    favState, true, true, true, true, true);
            contextMenu.setOnMenuItemClickListener(item -> {
                final int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION)
                    return contextMenuItemListener.onStationContextMenuItemClick(position, station.network,
                            station.location, station.departures, item.getItemId());
                else
                    return false;
            });
            contextMenu.show();
        } : null);

        // departures
        final List<Departure> stationDepartures = station.departures;

        final List<String> messages = new LinkedList<>();
        if (queryNotOk) {
            departuresViewGroup.setVisibility(View.GONE);
            departuresStatusView.setVisibility(View.VISIBLE);
            departuresStatusView.setText("("
                    + context.getString(QueryDeparturesRunnable.statusMsgResId(station.departureQueryStatus)) + ")");
        } else if (stationDepartures != null && (!isGhosted || itemView.isActivated())) {
            int iDepartureView = 0;

            if (!stationDepartures.isEmpty()) {
                final int maxGroups = itemView.isActivated() ? maxDepartures : 1;
                final Map<LineDestination, List<Departure>> departureGroups = groupDeparturesByLineDestination(
                        stationDepartures, maxGroups, productsFilter);
                if (!departureGroups.isEmpty()) {
                    final int maxDeparturesPerGroup = !itemView.isActivated() ? 1
                            : 1 + (maxDepartures / departureGroups.size());
                    final int departuresChildCount = departuresViewGroup.getChildCount();

                    departuresViewGroup.setVisibility(View.VISIBLE);
                    departuresStatusView.setVisibility(View.GONE);

                    for (final Map.Entry<LineDestination, List<Departure>> departureGroup : departureGroups
                            .entrySet()) {
                        int iDeparture = 0;
                        final int interval = determineInterval(departureGroup.getValue());
                        for (final Departure departure : departureGroup.getValue()) {
                            final ViewGroup departureView;
                            final DepartureViewHolder departureViewHolder;
                            if (iDepartureView < departuresChildCount) {
                                departureView = (ViewGroup) departuresViewGroup.getChildAt(iDepartureView++);
                                departureViewHolder = (DepartureViewHolder) departureView.getTag();
                            } else {
                                departureView = (ViewGroup) inflater.inflate(R.layout.stations_station_entry_departure,
                                        departuresViewGroup, false);
                                departureViewHolder = new DepartureViewHolder();
                                departureViewHolder.line = departureView
                                        .findViewById(R.id.departure_entry_line);
                                departureViewHolder.destination = departureView
                                        .findViewById(R.id.departure_entry_destination);
                                departureViewHolder.messageIndex = departureView
                                        .findViewById(R.id.departure_entry_message_index);
                                departureViewHolder.time = departureView
                                        .findViewById(R.id.departure_entry_time);
                                departureViewHolder.delay = departureView
                                        .findViewById(R.id.departure_entry_delay);
                                departureView.setTag(departureViewHolder);

                                departuresViewGroup.addView(departureView);
                            }
                            departureView.setPadding(0, iDeparture == 0 ? listEntryVerticalPadding : 0, 0, 0);

                            // line & destination
                            final LineView lineView = departureViewHolder.line;
                            final TextView destinationView = departureViewHolder.destination;
                            final LineDestination lineDestination = departureGroup.getKey();
                            if (iDeparture == 0) {
                                lineView.setVisibility(View.VISIBLE);
                                lineView.setLine(lineDestination.line);
                                lineView.setGhosted(isGhosted);

                                destinationView.setVisibility(View.VISIBLE);
                                final Location destination = lineDestination.destination;
                                if (destination != null) {
                                    final String destinationName = destination.uniqueShortName();
                                    destinationView.setText(destinationName != null
                                            ? Constants.DESTINATION_ARROW_PREFIX + destinationName : null);
                                } else {
                                    destinationView.setText(null);
                                }
                                destinationView.setTextColor(colorSignificant);
                            } else if (iDeparture == 1 && interval > 0) {
                                lineView.setVisibility(View.INVISIBLE);
                                lineView.setLine(lineDestination.line); // Padding only
                                destinationView.setVisibility(View.VISIBLE);
                                destinationView.setText(Constants.DESTINATION_ARROW_INVISIBLE_PREFIX
                                        + res.getString(R.string.stations_list_entry_interval, interval));
                                destinationView.setTextColor(colorLessSignificant);
                            } else {
                                lineView.setVisibility(View.INVISIBLE);
                                lineView.setLine(lineDestination.line); // Padding only
                                destinationView.setVisibility(View.INVISIBLE);
                            }
                            if (departure.journeyRef != null) {
                                lineView.setClickable(true);
                                lineView.setOnClickListener(clickedView ->
                                        journeyClickListener.onJourneyClick(
                                                clickedView, departure.journeyRef, station.location));
                            }

                            // message index
                            final TextView messageIndexView = (TextView) departureViewHolder.messageIndex;
                            if (departure.message != null || departure.line.message != null) {
                                messageIndexView.setVisibility(View.VISIBLE);

                                final String indexText;

                                if (itemView.isActivated()) {
                                    final String message = Joiner.on('\n').skipNulls().join(departure.message,
                                            departure.line.message);
                                    final int index = messages.indexOf(message);
                                    if (index == -1) {
                                        messages.add(message);
                                        indexText = Integer.toString(messages.size());
                                    } else {
                                        indexText = Integer.toString(index + 1);
                                    }
                                } else {
                                    indexText = "!";
                                }

                                messageIndexView.setText(indexText);
                                messageIndexView.setBackgroundColor(isGhosted ? colorSignificant : MESSAGE_INDEX_COLOR);
                            } else {
                                messageIndexView.setVisibility(View.GONE);
                            }

                            long time;
                            final Date predictedTime = departure.predictedTime;
                            final Date plannedTime = departure.plannedTime;
                            final boolean isPredicted = predictedTime != null;
                            if (predictedTime != null)
                                time = predictedTime.getTime();
                            else if (plannedTime != null)
                                time = plannedTime.getTime();
                            else
                                throw new IllegalStateException();

                            // time
                            final TextView timeView = departureViewHolder.time;
                            timeView.setText(Formats.formatTimeDiff(context, currentTime, time));
                            timeView.setTypeface(Typeface.DEFAULT, isPredicted ? Typeface.ITALIC : Typeface.NORMAL);
                            final Date updatedAt = station.updatedAt;
                            final boolean isStale = updatedAt != null
                                    && System.currentTimeMillis() - updatedAt.getTime() > Constants.STALE_UPDATE_MS;
                            timeView.setTextColor(isStale ? colorLessSignificant : colorSignificant);

                            // delay
                            final TextView delayView = departureViewHolder.delay;
                            final long delay = predictedTime != null && plannedTime != null
                                    ? predictedTime.getTime() - plannedTime.getTime() : 0;
                            final long delayMins = delay / DateUtils.MINUTE_IN_MILLIS;
                            delayView.setText(delayMins != 0 ? String.format("(%+d)", delayMins) + ' ' : "");
                            delayView.setTypeface(Typeface.DEFAULT, isPredicted ? Typeface.ITALIC : Typeface.NORMAL);
                            delayView.setTextColor(isStale ? colorLessSignificant : (isGhosted ? colorSignificant :
                                    colorHighlighted));

                            if (++iDeparture == maxDeparturesPerGroup)
                                break;
                        }
                    }

                    if (iDepartureView < departuresChildCount)
                        departuresViewGroup.removeViews(iDepartureView, departuresChildCount - iDepartureView);
                } else {
                    departuresViewGroup.setVisibility(View.GONE);
                    departuresStatusView.setVisibility(View.VISIBLE);
                    departuresStatusView.setText(R.string.stations_list_entry_product_filtered);
                }
            } else {
                departuresViewGroup.setVisibility(View.GONE);
                departuresStatusView.setVisibility(View.VISIBLE);
                departuresStatusView.setText(R.string.stations_list_entry_no_departures);
            }
        } else {
            departuresViewGroup.setVisibility(View.GONE);
            departuresStatusView.setVisibility(View.INVISIBLE);
        }

        // messages
        messagesViewGroup.removeAllViews();

        if (!messages.isEmpty()) {
            messagesViewGroup.setVisibility(View.VISIBLE);

            int index = 0;
            for (final String message : messages) {
                index++;

                final TextView messageView = (TextView) inflater.inflate(R.layout.stations_station_entry_message,
                        messagesViewGroup, false);
                messageView.setText(index + ". " + message);
                messageView.setTextColor(colorSignificant);
                messagesViewGroup.addView(messageView);
            }
        } else {
            messagesViewGroup.setVisibility(View.GONE);
        }

        // allow context menu
        itemView.setLongClickable(true);
    }

    private Map<LineDestination, List<Departure>> groupDeparturesByLineDestination(final List<Departure> departures,
            final int maxGroups, @Nullable final Set<Product> productsFilter) {
        final Map<LineDestination, List<Departure>> departureGroups = new LinkedHashMap<>();
        for (final Departure departure : departures) {
            if (productsFilter != null && departure.line.product != null
                    && !productsFilter.contains(departure.line.product))
                continue;
            final LineDestination lineDestination = new LineDestination(departure.line, departure.destination);
            List<Departure> departureGroup = departureGroups.get(lineDestination);
            if (departureGroup == null) {
                if (departureGroups.size() == maxGroups)
                    continue;
                departureGroup = new LinkedList<>();
                departureGroups.put(lineDestination, departureGroup);
            }
            departureGroup.add(departure);
        }
        return departureGroups;
    }

    private int determineInterval(final List<Departure> departures) {
        if (departures.size() < 3)
            return 0;
        int interval = 0;
        Date lastPlannedTime = null;
        for (final Departure departure : departures) {
            final Date plannedTime = departure.plannedTime;
            if (plannedTime == null)
                return 0;
            if (lastPlannedTime != null) {
                final int diff = (int) ((plannedTime.getTime() - lastPlannedTime.getTime())
                        / DateUtils.MINUTE_IN_MILLIS);
                if (interval == 0)
                    interval = diff;
                else if (Math.abs(diff - interval) > 1)
                    return 0;
            }
            lastPlannedTime = plannedTime;
        }
        return interval;
    }

    private static class DepartureViewHolder {
        public LineView line;
        public TextView destination;
        public View messageIndex;
        public TextView time;
        public TextView delay;
    }
}
