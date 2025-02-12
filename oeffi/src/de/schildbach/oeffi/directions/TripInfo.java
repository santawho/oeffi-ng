package de.schildbach.oeffi.directions;

import de.schildbach.pte.dto.Trip;

public class TripInfo {
    public final Trip trip;
    public int addedInRound;
    public boolean isEarlierOrLater;

    public TripInfo(final Trip trip) {
        this.trip = trip;
    }
}
