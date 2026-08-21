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

package de.schildbach.oeffi.util.geofiles;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.ResourceUtil;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;

public class GpxProducer extends GeoXmlProducer {
    @Override
    public String getFilenameExtension() {
        return "gpx";
    }

    protected void writeTrip(final Trip trip, final OutputStream outputStream) throws IOException {
        xs.setOutput(outputStream, StandardCharsets.UTF_8.name());
        xs.startDocument(null, null);

        xs.startTag(null, "gpx");

        xs.startTag(null, "metadata");
        final String tripName = application.getString(R.string.kml_trip_name,
                Formats.fullLocationName(trip.from),
                Formats.fullLocationName(trip.to));
        xmlTextNode("name", tripName);
        xs.endTag(null, "metadata");

        gpxWaypoint(trip.legs.get(0).departure, "Flag, Green");
        final List<Trip.Leg> legs = trip.legs;
        final int numLegs = legs.size();
        for (int index = 0; index < numLegs; index++) {
            final Trip.Leg leg = legs.get(index);
            if (leg instanceof Trip.Public) {
                gpxRteForPublicLeg((Trip.Public) leg);
            } else if (leg instanceof Trip.Individual) {
                gpxRteForIndividualLeg((Trip.Individual) leg);
            }
            gpxWaypoint(leg.arrival, index == numLegs - 1 ? "Flag, Red" : "Flag, Blue");
        }

        xs.endTag(null, "gpx");

        xs.endDocument();
        xs.flush();
        outputStream.close();
    }

    private void gpxWaypoint(final Location location, final String symbol) throws IOException {
        gpxPoint("wpt", location, symbol);
    }

    private void gpxPoint(final String tagName, final Location location, final String symbol) throws IOException {
        final Point coord = location.coord;
        if (coord == null)
            return;

        gpxPoint(tagName, location.coord, Formats.fullLocationName(location), symbol);
    }

    private void gpxPoint(final String tagName, final Point point) throws IOException {
        gpxPoint(tagName, point, null, null);
    }

    private void gpxPoint(final String tagName, final Point point, final String name, final String symbol) throws IOException {
        xs.startTag(null, tagName);
        xs.attribute(null, "lat", Double.toString(point.getLatAsDouble()));
        xs.attribute(null, "lon", Double.toString(point.getLonAsDouble()));
        if (name != null)
            xmlTextNode("name", name);
        if (symbol != null)
            xmlTextNode("sym", symbol);
        xs.endTag(null, tagName);
    }

    private void gpxRteForPublicLeg(final Trip.Public leg) throws IOException {
        final String typeName = ResourceUtil.getProductName(leg.line.product);
        final String legName = application.getString(R.string.kml_public_leg_name,
                leg.line.label,
                Formats.fullLocationName(leg.departure),
                Formats.fullLocationName(leg.arrival));
        final List<Location> locations = new ArrayList<>();
        locations.add(new Location(LocationType.STATION, null, leg.departureStop.location.coord));
        final List<Stop> intermediateStops = leg.intermediateStops;
        if (intermediateStops != null) {
            for (final Stop stop : intermediateStops)
                locations.add(stop.location);
        }
        locations.add(new Location(LocationType.STATION, null, leg.arrivalStop.location.coord));
        gpxRteForLocations(legName, typeName, locations);
    }

    private void gpxRteForIndividualLeg(final Trip.Individual leg) throws IOException {
        final int typeResId;
        switch (leg.type) {
            case WALK: typeResId =  R.string.kml_individual_type_walk; break;
            case CAR: typeResId =  R.string.kml_individual_type_car; break;
            case BIKE: typeResId =  R.string.kml_individual_type_bike; break;
            default: typeResId =  R.string.kml_individual_type_transfer; break;
        }
        final String typeName = application.getString(typeResId);
        final String legName = application.getString(R.string.kml_individual_leg_name,
                typeName,
                Formats.fullLocationName(leg.departure),
                Formats.fullLocationName(leg.arrival));
        final List<Point> points = new ArrayList<>();
        points.add(leg.departure.coord);
        points.add(leg.arrival.coord);
        gpxRteForPoints(legName, typeName, points);
    }

    private void gpxRteForLocations(final String name, final String typeName, final List<Location> locations) throws IOException {
        gpxRteStart(name, typeName);
        for (final Location location : locations)
            gpxRtePoint(location);
        gpxRteEnd();
    }

    private void gpxRteForPoints(final String name, final String typeName, final List<Point> points) throws IOException {
        gpxRteStart(name, typeName);
        for (final Point point : points)
            gpxRtePoint(point);
        gpxRteEnd();
    }

    private void gpxRteStart(final String name, final String typeName) throws IOException {
        xs.startTag(null, "rte");
        xmlTextNode("name", name);
        if (typeName != null)
            xmlTextNode("type", typeName);
    }

    private void gpxRteEnd() throws IOException {
        xs.endTag(null, "rte");
    }

    private void gpxRtePoint(final Location location) throws IOException {
        gpxPoint("rtept", location, null);
    }

    private void gpxRtePoint(final Point point) throws IOException {
        gpxPoint("rtept", point);
    }
}
