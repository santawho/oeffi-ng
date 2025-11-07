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

package de.schildbach.oeffi.stations;

import de.schildbach.oeffi.util.GeoUtils;
import de.schildbach.oeffi.util.KeyWordMatcher;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryDeparturesResult;

import javax.annotation.Nullable;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class Station {
    public final NetworkId network;
    public Location location;
    public @Nullable QueryDeparturesResult.Status departureQueryStatus = null;
    private @Nullable List<Departure> departures = null;
    private @Nullable List<LineDestination> lines = null;
    private @Nullable Product relevantProduct = null;
    public boolean hasDistanceAndBearing = false;
    public float distance;
    public float bearing;
    public @Nullable Date requestedAt = null;
    public @Nullable Date updatedAt = null;
    private KeyWordMatcher.SearchableItem searchableItem;
    private KeyWordMatcher.Query lastQuery;
    private boolean matchedByQuery;

    public Station(final NetworkId network, final Location location) {
        this.network = network;
        this.location = checkNotNull(location);
    }

    public List<LineDestination> getLines() {
        return lines;
    }

    public void setLines(List<LineDestination> lines) {
        this.lines = lines;

        relevantProduct = null;
    }

    public void setDepartures(@Nullable final List<Departure> departures) {
        this.departures = departures;
        searchableItem = null;
    }

    @Nullable
    public List<Departure> getDepartures() {
        return departures;
    }

    public boolean keyWordMatch(final KeyWordMatcher.Query query) {
        if (query == null) {
            matchedByQuery = true;
            lastQuery = null;
            return true;
        }
        if (searchableItem == null) {
            searchableItem = KeyWordMatcher.createSearchableItem();
            searchableItem.addIndexableText(location.name);
            searchableItem.addIndexableText(location.place);
            if (departures != null) {
                for (final Departure departure : departures) {
                    final Line line = departure.line;
                    if (line != null) {
                        final String label = line.label;
                        if (label != null) {
                            searchableItem.addIndexableString(label.replace(" ", ""));
                        }
                    }
                }
            }
        }
        if (query == lastQuery)
            return matchedByQuery;

        lastQuery = query;
        matchedByQuery = query.matchTo(searchableItem).matches;
        return matchedByQuery;
    }

    public void setDistanceAndBearing(final GeoUtils.DistanceResult distanceResult) {
        setDistanceAndBearing(distanceResult.distanceInMeters, distanceResult.initialBearing);
    }

    public void setDistanceAndBearing(final float distance, final float bearing) {
        this.distance = distance;
        this.bearing = bearing;
        this.hasDistanceAndBearing = true;
    }

    public Product getRelevantProduct() {
        if (relevantProduct != null)
            return relevantProduct;

        // collect all products
        final EnumSet<Product> products = EnumSet.noneOf(Product.class);
        if (location.products != null)
            products.addAll(location.products);
        final List<LineDestination> lines = this.lines;
        if (lines != null) {
            for (final LineDestination line : lines) {
                final Product product = line.line.product;
                if (product != null)
                    products.add(product);
            }
        }

        relevantProduct = !products.isEmpty() ? products.iterator().next() : null;
        return relevantProduct;
    }

    @Override
    public String toString() {
        return location.toString();
    }
}
