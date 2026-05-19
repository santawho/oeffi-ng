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
import de.schildbach.pte.dto.Location;
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

        gpxWaypoint(trip.legs.get(0).departure);
        for (final Trip.Leg leg : trip.legs) {
            if (leg instanceof Trip.Public) {
                gpxRteForPublicLeg((Trip.Public) leg);
            } else if (leg instanceof Trip.Individual) {
                gpxRteForIndividualLeg((Trip.Individual) leg);
            }
            gpxWaypoint(leg.arrival);
        }

        xs.endTag(null, "gpx");

        xs.endDocument();
        xs.flush();
        outputStream.close();
    }

    private void gpxWaypoint(final Location location) throws IOException {
        final Point coord = location.coord;
        if (coord == null)
            return;

        gpxPoint("wpt", coord, Formats.fullLocationName(location));
    }

    private void gpxPoint(final String tagName, final Point point, final String name) throws IOException {
        xs.startTag(null, tagName);
        xs.attribute(null, "lat", Double.toString(point.getLatAsDouble()));
        xs.attribute(null, "lon", Double.toString(point.getLonAsDouble()));
        if (name != null)
            xmlTextNode("name", name);
        xs.endTag(null, tagName);
    }

    private void gpxRteForPublicLeg(final Trip.Public leg) throws IOException {
        final int productResId = application.getResources().getIdentifier(
                "product_" + Character.toLowerCase(leg.line.productCode()),
                "string", application.getPackageName());
        final String typeName = productResId != 0 ? application.getString(productResId) : null;
        final String legName = application.getString(R.string.kml_public_leg_name,
                leg.line.label,
                Formats.fullLocationName(leg.departure),
                Formats.fullLocationName(leg.arrival));
        final List<Point> points = new ArrayList<>();
        points.add(leg.departureStop.location.coord);
        final List<Stop> intermediateStops = leg.intermediateStops;
        if (intermediateStops != null) {
            for (final Stop stop : intermediateStops)
                points.add(stop.location.coord);
        }
        points.add(leg.arrivalStop.location.coord);
        gpxRte(legName, typeName, points);
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
        gpxRte(legName, typeName, points);
    }

    private void gpxRte(final String name, final String typeName, final List<Point> points) throws IOException {
        xs.startTag(null, "rte");
        xmlTextNode("name", name);
        if (typeName != null)
            xmlTextNode("type", typeName);

        for (final Point point : points)
            gpxPoint("rtept", point, null);

        xs.endTag(null, "rte");
    }
}
