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

package de.schildbach.oeffi.util.locationview;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import de.schildbach.oeffi.R;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;

public class AutoCompleteLocationAdapter extends BaseAdapter implements Filterable {
    private final LocationView locationView;
    private final NetworkId network;

    private ImageButton filterStationButton;
    private ImageButton filterAddressButton;
    private ImageButton filterPoiButton;
    private boolean filterStations, filterAddresses, filterPois;

    private List<Location> locations = new LinkedList<>();

    public AutoCompleteLocationAdapter(final LocationView locationView, final NetworkId network) {
        this.locationView = locationView;
        this.network = network;
    }

    public Activity getActivity() {
        return locationView.getActivity();
    }

    public int getCount() {
        return locations.size();
    }

    public Object getItem(final int position) {
        return locations.get(position);
    }

    public long getItemId(final int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(final int position) {
        return position == 0 ? 0 : 1;
    }

    @Override
    public View getView(final int position, final View aRow, final ViewGroup parent) {
        final LinearLayout row;

        if (aRow != null) {
            row = (LinearLayout) aRow;
        } else {
            row = (LinearLayout) getActivity().getLayoutInflater().inflate(R.layout.location_dropdown_entry, null);
            if (position == 0) {
                filterStationButton = row.findViewById(R.id.location_view_filter_station);
                filterAddressButton = row.findViewById(R.id.location_view_filter_address);
                filterPoiButton = row.findViewById(R.id.location_view_filter_poi);
                filterStationButton.setVisibility(View.VISIBLE);
                filterAddressButton.setVisibility(View.VISIBLE);
                filterPoiButton.setVisibility(View.VISIBLE);
                filterStationButton.setOnClickListener(this::onFilterButtonClicked);
                filterAddressButton.setOnClickListener(this::onFilterButtonClicked);
                filterPoiButton.setOnClickListener(this::onFilterButtonClicked);
            }
        }

        final Location location = locations.get(position);
        final LocationTextView textView = row.findViewById(R.id.location_dropdown_entry_text);
        textView.setLocation(location);

        return row;
    }

    public void onFilterButtonClicked(final View clickedView) {
        final ImageButton filterButton = (ImageButton) clickedView;
        final int filterButtonId = filterButton.getId();
        boolean filterOn;
        if (filterButtonId == R.id.location_view_filter_station) {
            filterOn = filterStations = !filterStations;
        } else if (filterButtonId == R.id.location_view_filter_address) {
            filterOn = filterAddresses = !filterAddresses;
        } else if (filterButtonId == R.id.location_view_filter_poi) {
            filterOn = filterPois = !filterPois;
        } else {
            return;
        }
        filterButton.setBackgroundColor(filterOn ? getActivity().getColor(R.color.bg_selected) : 0);
        locationView.refreshAutoCompleteResults();
    }

    public class LocationFilter extends Filter {
        @Override
        protected FilterResults performFiltering(final CharSequence constraint) {
            final FilterResults filterResults = new FilterResults();
            final EnumSet<LocationType> suggestedLocationTypes = EnumSet.noneOf(LocationType.class);
            if (filterStations) suggestedLocationTypes.add(LocationType.STATION);
            if (filterAddresses) suggestedLocationTypes.add(LocationType.ADDRESS);
            if (filterPois) suggestedLocationTypes.add(LocationType.POI);
            final List<Location> results = LocationSuggestionsCollector.collectSuggestions(
                    constraint, suggestedLocationTypes, network);
            if (results != null) {
                filterResults.values = results;
                filterResults.count = results.size();
            }
            if (filterResults.count == 0)
                resetFilters();
            return filterResults;
        }

        @Override
        protected void publishResults(final CharSequence constraint, final FilterResults filterResults) {
            if (filterResults.values != null) {
                locations = (List<Location>) filterResults.values;
                notifyDataSetChanged();
            }
        }
    }

    public void resetFilters() {
        filterStations = false;
        filterAddresses = false;
        filterPois = false;
        if (filterStationButton != null)
            filterStationButton.setBackgroundColor(0);
        if (filterAddressButton != null)
            filterAddressButton.setBackgroundColor(0);
        if (filterPoiButton != null)
            filterPoiButton.setBackgroundColor(0);
    }

    private LocationFilter currentFilter;

    public LocationFilter getFilter() {
        if (currentFilter == null)
            currentFilter = new LocationFilter();
        return currentFilter;
    }
}

