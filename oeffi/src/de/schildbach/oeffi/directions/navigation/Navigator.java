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

package de.schildbach.oeffi.directions.navigation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.QueryJourneyResult;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;

public class Navigator {
    private final NetworkId network;
    private final Trip baseTrip;
    private Trip currentTrip;

    public Navigator(final NetworkId network, final Trip trip) {
        this.network = network;
        baseTrip = trip;
    }

    public Trip getCurrentTrip() {
        if (currentTrip == null) {
            currentTrip = Objects.clone(baseTrip);
        }

        return currentTrip;
    }

    public Trip refresh() throws IOException {
        NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
        final List<Trip.Leg> newLegs = new ArrayList<>();
        for (Trip.Leg leg : baseTrip.legs) {
            Trip.Leg newLeg = leg;
            if (leg instanceof Trip.Public) {
                Trip.Public publicLeg = (Trip.Public) leg;
                JourneyRef journeyRef = publicLeg.journeyRef;
                if (journeyRef != null) {
                    final QueryJourneyResult result = networkProvider.queryJourney(journeyRef);
                    if (result != null) {
                        switch (result.status) {
                            case OK:
                                if (result.journeyLeg != null)
                                    newLeg = buildUpdatedLeg(publicLeg, result.journeyLeg);
                                break;
                            case NO_JOURNEY:
                                break;
                            case SERVICE_DOWN:
                                return null;
                        }
                    }
                }
            }
            newLegs.add(newLeg);
        }

        currentTrip = new Trip(
                new Date(),
                baseTrip.getUniqueId(),
                baseTrip.tripRef,
                baseTrip.from,
                baseTrip.to,
                newLegs,
                baseTrip.fares,
                baseTrip.capacity,
                baseTrip.numChanges);

        return currentTrip;
    }

    private Trip.Public buildUpdatedLeg(Trip.Public initialLeg, Trip.Public journeyLeg) {
        final List<Stop> journeyStops = new ArrayList<>();
        journeyStops.add(journeyLeg.departureStop);
        if (journeyLeg.intermediateStops != null)
            journeyStops.addAll(journeyLeg.intermediateStops);
        journeyStops.add(journeyLeg.arrivalStop);

        final String depId = initialLeg.departureStop.location.id;
        final String arrId = initialLeg.arrivalStop.location.id;

        Stop departureStop = null;
        Stop arrivalStop = null;
        final List<Stop> intermediateStops = new ArrayList<>();
        for (Stop stop : journeyStops) {
            String locId = stop.location.id;
            if (locId != null) {
                if (locId.equals(depId)) {
                    departureStop = stop;
                    continue;
                } else if (departureStop != null && locId.equals(arrId)) {
                    arrivalStop = stop;
                    break;
                }
            }
            if (departureStop != null) {
                intermediateStops.add(stop);
            }
        }

        if (departureStop == null)
            departureStop = initialLeg.departureStop;

        if (arrivalStop == null)
            arrivalStop = initialLeg.arrivalStop;

        return new Trip.Public(
                journeyLeg.line,
                journeyLeg.destination,
                departureStop, arrivalStop, intermediateStops,
                initialLeg.path,
                journeyLeg.message,
                initialLeg.journeyRef);
    }
}
