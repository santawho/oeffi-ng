package de.schildbach.oeffi.util;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;

public class KmlProducer {
    private static final String STYLE_PUBLIC_LEG_NAME = "publeg";
    public static final int STYLE_PUBLIC_LEG_COLOR = 0x60ff00ff;
    public static final int STYLE_PUBLIC_LEG_WIDTH = 6;
    private static final String STYLE_INDIVIDUAL_LEG_NAME = "idvleg";
    public static final int STYLE_INDIVIDUAL_LEG_COLOR = 0x6000ffff;
    public static final int STYLE_INDIVIDUAL_LEG_WIDTH = 8;

    private final Application application;
    private final XmlSerializer xs;

    public KmlProducer(final Application application) {
        this.application = application;
        try {
            this.xs = XmlPullParserFactory.newInstance().newSerializer();
        } catch (XmlPullParserException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeTrip(final Trip trip, final File kmlFile) throws IOException {
        final FileOutputStream fos = new FileOutputStream(kmlFile);
        writeTrip(trip, fos);
        fos.close();
    }

    public void writeTrip(final Trip trip, final OutputStream outputStream) throws IOException {
        xs.setOutput(outputStream, StandardCharsets.UTF_8.name());
        xs.startDocument(null, null);

        xs.startTag(null, "kml");
        xs.startTag(null, "Document");
        final String tripName = application.getString(R.string.kml_trip_name,
                Formats.fullLocationName(trip.from),
                Formats.fullLocationName(trip.to));
        kmlTextNode("name", tripName);

        kmlLineStyle(STYLE_PUBLIC_LEG_NAME, STYLE_PUBLIC_LEG_COLOR, STYLE_PUBLIC_LEG_WIDTH);
        kmlLineStyle(STYLE_INDIVIDUAL_LEG_NAME, STYLE_INDIVIDUAL_LEG_COLOR, STYLE_INDIVIDUAL_LEG_WIDTH);

        for (Trip.Leg leg : trip.legs) {
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

    private void kmlTextNode(final String name, final String value) throws IOException {
        xs.startTag(null, name);
        xs.text(value);
        xs.endTag(null, name);
    }

    private void kmlLineStyle(final String id, final int color, final int width) throws IOException {
        xs.startTag(null, "Style");
        xs.attribute(null, "id", id);
        xs.startTag(null, "LineStyle");
        kmlTextNode("width", Integer.toString(width));
        kmlTextNode("color", kmlColor(color));
        kmlTextNode("colorMode", "normal");
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
            for (Stop stop : intermediateStops)
                points.add(stop.location.coord);
        }
        points.add(leg.arrivalStop.location.coord);
        kmlLineStringPlacemark(legName, STYLE_PUBLIC_LEG_NAME, points);
    }

    private void kmlPlacemarkForIndividualLeg(final Trip.Individual leg) throws IOException {
        final Trip.Individual.Type type = leg.type;
        final int typeResId;
        switch (leg.type) {
            case WALK: typeResId =  R.string.kml_individual_type_walk; break;
            case CAR: typeResId =  R.string.kml_individual_type_walk; break;
            case BIKE: typeResId =  R.string.kml_individual_type_walk; break;
            default: typeResId =  R.string.kml_individual_type_walk; break;
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
        kmlTextNode("name", name);
        kmlTextNode("visibility", "1");
        kmlTextNode("styleUrl", "#" + style);
        xs.startTag(null, "LineString");
        xs.startTag(null, "coordinates");

        for (Point point : points)
            kmlCoordinateForPoint(point);

        xs.endTag(null, "coordinates");
        xs.endTag(null, "LineString");
        xs.endTag(null, "Placemark");
    }
}
