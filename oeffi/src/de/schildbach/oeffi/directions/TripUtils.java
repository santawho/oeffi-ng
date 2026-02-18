package de.schildbach.oeffi.directions;

import java.util.Collections;
import java.util.Date;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Trip;

public class TripUtils {
    public static Trip createTripFromJourney(
            final Date loadedAt,
            final Trip.Public journeyLeg,
            final Location entryLocation,
            final Location exitLocation) {
        final Trip trip = new Trip(
                loadedAt,
                null,
                null,
                entryLocation,
                exitLocation,
                Collections.singletonList(journeyLeg),
                null,
                null,
                0);
        if (journeyLeg.journeyRef != null)
            trip.setUniqueId(journeyLeg.journeyRef.getUniqueId());
        return trip;
    }

    public static Trip createTripFromJourney(
            final Date loadedAt,
            final Trip.Public journeyLeg) {
        return createTripFromJourney(
                loadedAt,
                journeyLeg,
                journeyLeg.entryLocation != null ? journeyLeg.entryLocation : journeyLeg.departure,
                journeyLeg.exitLocation != null ? journeyLeg.exitLocation : journeyLeg.arrival);
    }

    public static Trip createTripFromJourneyTrip(final Trip trip) {
        final Trip.Public publicLeg = trip.getFirstPublicLeg();
        if (publicLeg == null || publicLeg != trip.getLastPublicLeg()
                || publicLeg.journeyRef == null) {
            return trip;
        }
        return createTripFromJourney(trip.loadedAt, publicLeg);
    }
}
