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

package de.schildbach.oeffi.directions;

import java.util.Set;

import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Trip;

public class TripInfo {
    public final Trip trip;
    public int addedInRound;
    public boolean isEarlierOrLater;
    public boolean isAlternativelyFed;
    public Trip baseTrip;
    public boolean isTripFullyWheelChairAccessible;
    public boolean isTripFullyBicycleTravelable;

    public TripInfo(final Trip trip) {
        this.trip = trip;

        setup();
    }

    private void setup() {
        boolean tripFullyWheelChairAccessible = true;
        boolean tripFullyBicycleTravelable = true;
        for (Trip.Leg leg : trip.legs) {
            if (leg instanceof Trip.Public) {
                final Trip.Public publicLeg = (Trip.Public) leg;
                final Line line = publicLeg.line;
                if (line == null)
                    continue;
                final Set<Line.Attr> attrs = line.attrs;
                if (attrs == null)
                    continue;
                if (!attrs.contains(Line.Attr.WHEEL_CHAIR_ACCESS))
                    tripFullyWheelChairAccessible = false;
                if (!attrs.contains(Line.Attr.BICYCLE_CARRIAGE))
                    tripFullyBicycleTravelable = false;
            }
        }

        isTripFullyWheelChairAccessible = tripFullyWheelChairAccessible;
        isTripFullyBicycleTravelable = tripFullyBicycleTravelable;
    }
}
