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

import static de.schildbach.pte.util.Preconditions.checkArgument;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.stations.CompassNeedleView;
import de.schildbach.oeffi.stations.Station;
import de.schildbach.oeffi.util.KeyWordMatcher;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Product;

public class JourneysAdapter extends RecyclerView.Adapter<JourneyViewHolder> implements CompassNeedleView.Callback {
    private final Activity context;
    private final int maxDepartures;
    private final Set<Product> productsFilter;
    private final StationContextMenuItemListener contextMenuItemListener;
    private final JourneyClickListener journeyClickListener;
    private final StationsAware stationsAware;
    private Date baseTime;

    private android.location.Location deviceLocation = null;
    private Double deviceBearing = null;
    private boolean showPlaces = false;
    private boolean faceDown = false;
    private KeyWordMatcher.Query filterQuery;

    private List<JourneyC> journeys;

    private final LayoutInflater inflater;

    public JourneysAdapter(
            final Activity context, final int maxDepartures, final Set<Product> productsFilter,
            final StationContextMenuItemListener contextMenuItemListener,
            final JourneyClickListener journeyClickListener,
            final StationsAware stationsAware) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.maxDepartures = maxDepartures;
        this.productsFilter = productsFilter;
        this.contextMenuItemListener = contextMenuItemListener;
        this.journeyClickListener = journeyClickListener;
        this.stationsAware = stationsAware;

        setHasStableIds(true);

        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                JourneysAdapter.this.onChanged();
            }
        });
    }

    public void setBaseTime(final Date baseTime) {
        this.baseTime = baseTime;
    }

    public void setFilterQuery(final KeyWordMatcher.Query filterQuery) {
        this.filterQuery = filterQuery;
    }

    public void setDeviceLocation(final android.location.Location deviceLocation) {
        this.deviceLocation = deviceLocation;
    }

    public void setDeviceBearing(final Double deviceBearing, final boolean faceDown) {
        this.deviceBearing = deviceBearing;
        this.faceDown = faceDown;
    }

    public void setShowPlaces(final boolean showPlaces) {
        this.showPlaces = showPlaces;
    }

    @Override
    public int getItemCount() {
        return journeys.size();
    }

    public void onChanged() {
        final Map<JourneyRef, JourneyC> journeyMap = new HashMap<>();
        final List<Station> stations = stationsAware.getStations();
        for (final Station station : stations) {
            final List<Departure> departures = station.getDepartures();
            if (departures == null || departures.isEmpty())
                continue;
            for (final Departure departure : departures) {
                final JourneyRef journeyRef = departure.journeyRef;
                JourneyC journeyContainer = journeyMap.get(journeyRef);
                if (journeyContainer == null) {
                    journeyContainer = new JourneyC();
                    journeyMap.put(journeyRef, journeyContainer);
                }
                journeyContainer.departures.add(new DepartureC(departure, station));
            }
        }
        final ArrayList<JourneyC> journeys = new ArrayList<>(journeyMap.values());
        for (final JourneyC journey : journeys) {
            // latest possible start of walk first
            Collections.sort(journey.departures, (d1, d2) -> -diffDepartureTimes(d1, d2));
        }
        // earliest of latest first
        Collections.sort(journeys, (j1, j2) -> diffDepartureTimes(j1.departures.get(0), j2.departures.get(0)));
        this.journeys = journeys;
    }

    private int diffDepartureTimes(final DepartureC d1, final DepartureC d2) {
        final long walkMinutes1 = d1.getDepartureTimeMillis();
        final long walkMinutes2 = d2.getDepartureTimeMillis();
        final long diff = walkMinutes1 - walkMinutes2;
        if (diff < 0)
            return -1;
        if (diff > 0)
            return 1;
        return 0;
    }

    @Override
    public long getItemId(final int position) {
        checkArgument(position != RecyclerView.NO_POSITION);
        final JourneyRef journeyRef = getItem(position).departures.get(0).departure.journeyRef;
        return journeyRef == null ? 0 : journeyRef.hashCode();
    }

    public JourneyC getItem(final int position) {
        checkArgument(position != RecyclerView.NO_POSITION);
        return journeys.get(position);
    }

    @Override
    public JourneyViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new JourneyViewHolder(context, inflater.inflate(R.layout.stations_journey_entry, parent, false),
                maxDepartures, contextMenuItemListener, journeyClickListener);
    }

    @Override
    public void onBindViewHolder(final JourneyViewHolder holder, final int position) {
        checkArgument(position != RecyclerView.NO_POSITION);
        final JourneyC journey = getItem(position);
        holder.bind(stationsAware, journey, baseTime, productsFilter, showPlaces, deviceLocation, this);
    }

    public Double getDeviceBearing() {
        return deviceBearing;
    }

    public boolean isFaceDown() {
        return faceDown;
    }

    public static class DepartureC {
        public final Departure departure;
        public final Station station;

        public DepartureC(
                final Departure departure,
                final Station station) {
            this.departure = departure;
            this.station = station;
        }

        public long getDepartureTimeMillis() {
            return departure.getTime().getTime() + station.walkTimeMillis;
        }
    }

    public static class JourneyC {
        public final List<DepartureC> departures = new ArrayList<>();
    }
}
