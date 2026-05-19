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
import java.util.Locale;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;

public class KmlProducer extends GeoXmlProducer {
    private static final String STYLE_PUBLIC_LEG_NAME = "publeg";
    public static final int STYLE_PUBLIC_LEG_COLOR = 0x60ff00ff;
    public static final int STYLE_PUBLIC_LEG_WIDTH = 6;
    private static final String STYLE_INDIVIDUAL_LEG_NAME = "idvleg";
    public static final int STYLE_INDIVIDUAL_LEG_COLOR = 0x6000ffff;
    public static final int STYLE_INDIVIDUAL_LEG_WIDTH = 8;

    @Override
    public String getFilenameExtension() {
        return "kml";
    }

    protected void writeTrip(final Trip trip, final OutputStream outputStream) throws IOException {
        xs.setOutput(outputStream, StandardCharsets.UTF_8.name());
        xs.startDocument(null, null);

        xs.startTag(null, "kml");
        xs.startTag(null, "Document");
        final String tripName = application.getString(R.string.kml_trip_name,
                Formats.fullLocationName(trip.from),
                Formats.fullLocationName(trip.to));
        xmlTextNode("name", tripName);

        kmlLineStyle(STYLE_PUBLIC_LEG_NAME, STYLE_PUBLIC_LEG_COLOR, STYLE_PUBLIC_LEG_WIDTH);
        kmlLineStyle(STYLE_INDIVIDUAL_LEG_NAME, STYLE_INDIVIDUAL_LEG_COLOR, STYLE_INDIVIDUAL_LEG_WIDTH);

        for (final Trip.Leg leg : trip.legs) {
            if (leg instanceof Trip.Public) {
                kmlPlacemarkForPublicLeg((Trip.Public) leg);
            } else if (leg instanceof Trip.Individual) {
                kmlPlacemarkForIndividualLeg((Trip.Individual) leg);
            }
        }

        xs.endTag(null, "Document");
        xs.endTag(null, "kml");

        xs.endDocument();
        xs.flush();
        outputStream.close();
    }

    private void kmlLineStyle(final String id, final int color, final int width) throws IOException {
        xs.startTag(null, "Style");
        xs.attribute(null, "id", id);
        xs.startTag(null, "LineStyle");
        xmlTextNode("width", Integer.toString(width));
        xmlTextNode("color", kmlColor(color));
        xmlTextNode("colorMode", "normal");
        xs.endTag(null, "LineStyle");
        xs.endTag(null, "Style");
    }

    private String kmlColor(final int color) {
        final int a = (color >> 24) & 255;
        final int r = (color >> 16) & 255;
        final int g = (color >> 8) & 255;
        final int b = (color) & 255;
        return String.format("%02x%02x%02x%02x", a, b, g, r);
    }

    private void kmlCoordinateForPoint(final Point point) throws IOException {
        xs.text(point == null ? "\n" : String.format(Locale.US, "\n%f,%f,0",
                point.getLonAsDouble(), point.getLatAsDouble()));
    }

    private void kmlPlacemarkForPublicLeg(final Trip.Public leg) throws IOException {
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
        kmlLineStringPlacemark(legName, STYLE_PUBLIC_LEG_NAME, points);
    }

    private void kmlPlacemarkForIndividualLeg(final Trip.Individual leg) throws IOException {
        final int typeResId;
        switch (leg.type) {
            case WALK: typeResId =  R.string.kml_individual_type_walk; break;
            case CAR: typeResId =  R.string.kml_individual_type_car; break;
            case BIKE: typeResId =  R.string.kml_individual_type_bike; break;
            default: typeResId =  R.string.kml_individual_type_transfer; break;
        }
        final String legName = application.getString(R.string.kml_individual_leg_name,
                application.getString(typeResId),
                Formats.fullLocationName(leg.departure),
                Formats.fullLocationName(leg.arrival));
        final List<Point> points = new ArrayList<>();
        points.add(leg.departure.coord);
        points.add(leg.arrival.coord);
        kmlLineStringPlacemark(legName, STYLE_INDIVIDUAL_LEG_NAME, points);
    }

    private void kmlLineStringPlacemark(final String name, final String style, final List<Point> points) throws IOException {
        xs.startTag(null, "Placemark");
        xmlTextNode("name", name);
        xmlTextNode("visibility", "1");
        xmlTextNode("styleUrl", "#" + style);
        xs.startTag(null, "LineString");
        xs.startTag(null, "coordinates");

        for (final Point point : points)
            kmlCoordinateForPoint(point);

        xs.endTag(null, "coordinates");
        xs.endTag(null, "LineString");
        xs.endTag(null, "Placemark");
    }
}
