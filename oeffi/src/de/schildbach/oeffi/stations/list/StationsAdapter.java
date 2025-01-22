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
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.stations.CompassNeedleView;
import de.schildbach.oeffi.stations.Station;
import de.schildbach.pte.dto.Product;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class StationsAdapter extends RecyclerView.Adapter<StationViewHolder> implements CompassNeedleView.Callback {
    private final Context context;
    private final int maxDepartures;
    private final Set<Product> productsFilter;
    private final StationContextMenuItemListener contextMenuItemListener;
    private final JourneyClickListener journeyClickListener;
    private final StationsAware stationsAware;

    private android.location.Location deviceLocation = null;
    private Float deviceBearing = null;
    private boolean showPlaces = false;
    private boolean faceDown = false;

    private final LayoutInflater inflater;

    public StationsAdapter(
            final Context context, final int maxDepartures, final Set<Product> productsFilter,
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
    }

    public void setDeviceLocation(final android.location.Location deviceLocation) {
        this.deviceLocation = deviceLocation;
    }

    public void setDeviceBearing(final Float deviceBearing, final boolean faceDown) {
        this.deviceBearing = deviceBearing;
        this.faceDown = faceDown;
    }

    public void setShowPlaces(final boolean showPlaces) {
        this.showPlaces = showPlaces;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return stationsAware.getStations().size();
    }

    @Override
    public long getItemId(final int position) {
        checkArgument(position != RecyclerView.NO_POSITION);
        return getItem(position).location.hashCode();
    }

    public Station getItem(final int position) {
        checkArgument(position != RecyclerView.NO_POSITION);
        final List<Station> stations = stationsAware.getStations();
        return stations.get(position);
    }

    @Override
    public StationViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new StationViewHolder(context, inflater.inflate(R.layout.stations_station_entry, parent, false),
                maxDepartures, contextMenuItemListener, journeyClickListener);
    }

    @Override
    public void onBindViewHolder(final StationViewHolder holder, final int position) {
        checkArgument(position != RecyclerView.NO_POSITION);
        final Station station = getItem(position);

        // select stations
        holder.itemView.setActivated(stationsAware.isSelectedStation(station.location.id));
        holder.itemView.setOnClickListener(v -> {
            final boolean isSelected = stationsAware.isSelectedStation(station.location.id);
            stationsAware.selectStation(isSelected ? null : station);
        });

        // populate view
        final Integer favState = stationsAware.getFavoriteState(station.location.id);
        holder.bind(station, productsFilter, showPlaces, favState, deviceLocation, this);
    }

    public Float getDeviceBearing() {
        return deviceBearing;
    }

    public boolean isFaceDown() {
        return faceDown;
    }
}
