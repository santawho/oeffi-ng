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
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Set;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.stations.CompassNeedleView;
import de.schildbach.oeffi.stations.Station;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.stations.StationsActivity;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.LineView;
import de.schildbach.oeffi.util.OverflowTextView;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Destination;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.Product;

public class JourneyViewHolder extends RecyclerView.ViewHolder {
    private static final Logger log = LoggerFactory.getLogger(JourneyViewHolder.class);
    public final View itemFrameView;
    public final LineView lineView;
    public final OverflowTextView destinationView;
    public final ViewGroup departuresViewGroup;

    private final StationsActivity context;
    private final Resources res;
    private final int maxDepartures;
    private final StationContextMenuItemListener contextMenuItemListener;
    private final JourneyClickListener journeyClickListener;

    private final LayoutInflater inflater;
    private final Display display;
    private final int colorArrow;
    private final int colorSignificant, colorLessSignificant, colorInsignificant, colorHighlighted;
    private final int colorWalkTimeGood, colorWalkTimeBad;
    private final int listEntryVerticalPadding;
    private StationContextMenu contextMenu;

    private static final int CONDENSE_LINES_THRESHOLD = 5;
    private static final int MESSAGE_INDEX_COLOR = Color.parseColor("#c08080");

    public JourneyViewHolder(
            final StationsActivity context, final View itemView, final int maxDepartures,
            final StationContextMenuItemListener contextMenuItemListener,
            final JourneyClickListener journeyClickListener) {
        super(itemView);

        itemFrameView = itemView.findViewById(R.id.journey_entry_item_frame);
        departuresViewGroup = itemView.findViewById(R.id.journey_entry_departures);
        lineView = itemView.findViewById(R.id.journey_entry_line);
        destinationView = itemView.findViewById(R.id.journey_entry_destination);

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
        this.colorWalkTimeBad = res.getColor(R.color.fg_walk_departure_bad);
        this.colorWalkTimeGood = res.getColor(R.color.fg_walk_departure_good);
        this.listEntryVerticalPadding = res.getDimensionPixelOffset(R.dimen.text_padding_vertical);
    }

    public void bind(
            final StationsAware stationsAware, final JourneysAdapter.JourneyC journey, final Date aBaseTime,
            final Set<Product> productsFilter, final boolean forceShowPlace,
            final android.location.Location deviceLocation,
            final CompassNeedleView.Callback compassCallback) {
        if (contextMenu != null) {
            contextMenu.dismiss();
            contextMenu = null;
        }

        final boolean baseIsNow = aBaseTime == null;
        final Date baseTime = baseIsNow ? new Date() : aBaseTime;

//        log.debug("---- bind ----");
//        journey.log(baseTime.getTime());

        final boolean isGhosted = false;
        final int colorSignificant = !isGhosted ? this.colorSignificant : colorInsignificant;
        final int colorLessSignificant = !isGhosted ? this.colorLessSignificant : colorInsignificant;
        final int colorHighlighted = !isGhosted ? this.colorHighlighted : colorInsignificant;

        final JourneysAdapter.DepartureC firstDepC = journey.departures.get(0);
        final Departure firstDeparture = firstDepC.departure;
        final Station firstStation = firstDepC.station;

        // line & destination
        lineView.setLine(firstDeparture.line);
        lineView.setGhosted(isGhosted);

        final String text;
        final Destination destination = firstDeparture.destination;
        if (destination == null) {
            text = null;
        } else {
            final String destinationName = Formats.makeBreakableStationName(
                    Formats.fullLocationNameIfDifferentPlace(destination.location, firstStation.location));
            if (destinationName == null) {
                text = null;
            } else if (!destination.isNotCommonType) {
                text = Constants.DESTINATION_ARROW_PREFIX + destinationName;
            } else if (destination.location.type == LocationType.STATION) {
                text = Constants.DESTINATION_STATION_ARROW_PREFIX + destinationName;
            } else {
                text = Constants.DESTINATION_DIRECTION_ARROW_PREFIX + destinationName;
            }
        }
        destinationView.setText(text);
        destinationView.setTextColor(colorSignificant);
        if (firstDeparture.journeyRef != null && journeyClickListener != null) {
            View.OnClickListener onClickListener = clickedView ->
                    journeyClickListener.onJourneyClick(
                            clickedView, firstDeparture.journeyRef,
                            firstStation.location, firstDeparture.plannedTime);
            lineView.setClickable(true);
            lineView.setOnClickListener(onClickListener);
            destinationView.setClickable(true);
            destinationView.setOnClickListener(onClickListener);
        }

        // departures
        final List<JourneysAdapter.DepartureC> departures = journey.departures;
        if (!isGhosted) {
            departuresViewGroup.setVisibility(View.VISIBLE);
            final int departuresChildCount = departuresViewGroup.getChildCount();

            int iDepartureView = 0;
            int iDeparture = 0;
            for (final JourneysAdapter.DepartureC depC : departures) {
                final Departure departure = depC.departure;
                final Station station = depC.station;

                final ViewGroup departureView;
                final DepartureViewHolder departureViewHolder;
                if (iDepartureView < departuresChildCount) {
                    departureView = (ViewGroup) departuresViewGroup.getChildAt(iDepartureView++);
                    departureViewHolder = (DepartureViewHolder) departureView.getTag();
                } else {
                    departureView = (ViewGroup) inflater.inflate(R.layout.stations_journey_entry_departure,
                            departuresViewGroup, false);
                    departureViewHolder = new DepartureViewHolder();
                    departureViewHolder.nameView = departureView.findViewById(R.id.jny_departure_entry_name);
                    departureViewHolder.name2View = departureView.findViewById(R.id.jny_departure_entry_name2);
                    departureViewHolder.distanceView = departureView.findViewById(R.id.jny_departure_entry_distance);
                    departureViewHolder.bearingView = departureView.findViewById(R.id.jny_departure_entry_bearing);
                    departureViewHolder.time = departureView.findViewById(R.id.jny_departure_entry_time);
                    departureViewHolder.delay = departureView.findViewById(R.id.jny_departure_entry_delay);
                    departureViewHolder.position = departureView.findViewById(R.id.jny_departure_entry_position);
                    departureViewHolder.remaining = departureView.findViewById(R.id.jny_departure_entry_remaining);
                    departureView.setTag(departureViewHolder);
                    departuresViewGroup.addView(departureView);
                }
                departureView.setPadding(0, iDeparture == 0 ? listEntryVerticalPadding : 0, 0, 0);

                // name/place
                final boolean showPlace = forceShowPlace;
                final String name = Formats.makeBreakableStationName(showPlace ? station.location.place : station.location.uniqueShortName());
                final OverflowTextView nameView = departureViewHolder.nameView;
                nameView.setText(name);
                nameView.setTypeface(showPlace ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
                nameView.setTextColor(colorSignificant);
                final TextView name2View = departureViewHolder.name2View;
                name2View.setVisibility(showPlace ? View.VISIBLE : View.GONE);
                name2View.setText(station.location.name);
                name2View.setTextColor(colorSignificant);

                // distance
                final TextView distanceView = departureViewHolder.distanceView;
                distanceView.setText(station.hasDistanceAndBearing ? Formats.formatDistance(station.distance, false) : null);
                distanceView.setVisibility(station.hasDistanceAndBearing ? View.VISIBLE : View.GONE);
                distanceView.setTextColor(colorSignificant);

                // bearing
                final CompassNeedleView bearingView = departureViewHolder.bearingView;
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

                final PTDate departureTime;
                final PTDate predictedTime = departure.predictedTime;
                final PTDate plannedTime = departure.plannedTime;
                final boolean isPredicted = predictedTime != null;
                if (predictedTime != null)
                    departureTime = predictedTime;
                else if (plannedTime != null)
                    departureTime = plannedTime;
                else
                    throw new IllegalStateException();

                // time
                final TextView timeView = departureViewHolder.time;
                timeView.setText(Formats.formatTimeDiff(context, baseTime, departureTime, baseIsNow));
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
                delayView.setTextColor(isStale ? colorLessSignificant : (isGhosted ? colorSignificant : colorHighlighted));

                // position
                final TextView positionView = departureViewHolder.position;
                final Position position = departure.getPosition();
                if (position != null) {
                    positionView.setVisibility(View.VISIBLE);
                    positionView.setText(position.toString());
                    positionView.setBackgroundColor(context.getColor(
                            position.equals(departure.plannedPosition)
                                    ? R.color.bg_position
                                    : R.color.bg_position_changed));
                } else {
                    positionView.setVisibility(View.GONE);
                }

                // remaining
                final long walkStartTime = departureTime.getTime() - station.walkTimeMillis;
                final long timeRemaining = walkStartTime - baseTime.getTime();
                final TextView remaining = departureViewHolder.remaining;
                remaining.setText(Formats.formatTimeDiff(context, baseTime.getTime(), walkStartTime, baseIsNow));
                remaining.setTypeface(Typeface.DEFAULT, isPredicted ? Typeface.BOLD_ITALIC : Typeface.BOLD);
                remaining.setTextColor(timeRemaining < 60000L ? colorWalkTimeBad : colorWalkTimeGood);

                departureView.setOnClickListener(v -> {
                    StationDetailsActivity.start(
                            context,
                            context.getNetwork(), station.location,
                            departureTime,
                            null,
                            false,
                            null);
                });
                departureView.setOnLongClickListener(v -> {
                    onContextClick(v, station);
                    return true;
                });

                if (++iDeparture == maxDepartures)
                    break;
            }

            while (iDepartureView < departuresChildCount) {
                final ViewGroup departureView = (ViewGroup) departuresViewGroup.getChildAt(iDepartureView++);
                departureView.setVisibility(View.GONE);
            }
        } else {
            departuresViewGroup.setVisibility(View.GONE);
        }
    }

    private void onContextClick(final View contextView, final Station station) {
        if (contextMenu != null)
            return;
        contextMenu = new StationContextMenu(context, contextView, station.network, station.location,
                null, true, true, false, true,
                true, false,
                false, false,
                true, false,
                false, false, true);
        contextMenu.setOnMenuItemClickListener(item -> {
            final int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                return contextMenuItemListener.onStationContextMenuItemClick(position, station.network,
                        station.location, station.getDepartures(), item.getItemId());
            }
            return false;
        });
        contextMenu.setOnDismissListener(menu -> {
            contextMenu = null;
        });
        contextMenu.show();
    }

    private static class DepartureViewHolder {
        public OverflowTextView nameView;
        public TextView name2View;
        public TextView time;
        public TextView delay;
        public TextView position;
        public TextView distanceView;
        public CompassNeedleView bearingView;
        public TextView remaining;
    }
}
