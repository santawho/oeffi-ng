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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.QueryJourneyResult;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.util.GeoUtils;

public class Navigator {
    private static final Logger log = LoggerFactory.getLogger(Navigator.class);
    private static final SimpleDateFormat LOG_TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

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

    public Trip refresh(final boolean forceRefreshAll, final Date startedAt, final long timeoutMs) throws IOException {
        final Long timeoutAt = timeoutMs > 0 ? startedAt.getTime() + timeoutMs : null;
        final List<Trip.Leg> newLegs = new ArrayList<>();
        final Trip latestTrip = getCurrentTrip();
        for (final Trip.Leg leg : latestTrip.legs) {
            Trip.Leg newLeg = leg;
            if (leg instanceof Trip.Public) {
                newLeg = updatePublicLeg((Trip.Public) leg, forceRefreshAll, startedAt);
            }
            if (newLeg == null) {
                // any error, then full trip is error
                return null;
            }
            if (timeoutAt != null) {
                final long now = System.currentTimeMillis();
                if (now > timeoutAt) {
                    // when timeout, then full trip is error
                    log.error("refreshing trip of {} legs took more than {} secs", latestTrip.legs.size(), (now - startedAt.getTime()) / 1000);
                    return null;
                }
            }
            newLegs.add(newLeg);
        }

        log.info("refreshing trip of {} legs took less than {} secs", latestTrip.legs.size(), (System.currentTimeMillis() - startedAt.getTime()) / 1000 + 1);

        currentTrip = new Trip(
                latestTrip.loadedAt,
                latestTrip.getUniqueId(),
                latestTrip.tripRef,
                latestTrip.from,
                latestTrip.to,
                newLegs,
                latestTrip.fares,
                latestTrip.capacity,
                latestTrip.getNumChanges());
        // currentTrip.transferDetails = latestTrip.transferDetails; -- do not keep transfer details, they are outdated
        currentTrip.updatedAt = startedAt;
        currentTrip.setUniqueId(latestTrip.getUniqueId());

        return currentTrip;
    }

    private Trip.Public updatePublicLeg(final Trip.Public oldLeg, final boolean forceRefresh, final Date now) throws IOException {
        final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
        final JourneyRef journeyRef = oldLeg.journeyRef;
        if (journeyRef == null)
            return oldLeg;
        final long nowTime = now.getTime();
        final long legLoadedAt = oldLeg.loadedAt.getTime();
        final long legBeginMinTime = oldLeg.departureStop.getDepartureTime(true).getTime();
        final long legBeginMaxTime = oldLeg.departureStop.getDepartureTime(false).getTime();
        final long legEndMinTime = oldLeg.arrivalStop.getArrivalTime(true).getTime();
        final long legEndMaxTime = oldLeg.arrivalStop.getArrivalTime(false).getTime();

        boolean doRefresh = forceRefresh;
        if (!doRefresh) {
            final long nextEventTime;
            long nextRefreshTimeA = Long.MAX_VALUE;
            if (nowTime < legBeginMinTime) {
                // leg yet to begin
                nextEventTime = legBeginMinTime;
            } else if (nowTime < legEndMaxTime) {
                // leg active
                if (nowTime < legBeginMaxTime + 300000) {
                    // still within 5 minutes after begin
                    nextRefreshTimeA = legLoadedAt + 60000;
                }
                nextEventTime = legEndMinTime;
            } else {
                // leg over
                if (nowTime < legEndMaxTime + 300000) {
                    // still within 5 minutes after end
                    nextRefreshTimeA = legLoadedAt + 60000;
                }
                nextEventTime = 0;
            }

            long nextRefreshTime = Long.MAX_VALUE;
            if (nextEventTime > 0) {
                final long timeLeft = nextEventTime - nowTime;
                if (timeLeft < 240000) {
                    // last 4 minutes and after, 30 secs refresh interval
                    nextRefreshTime = legLoadedAt + 30000;
                } else if (timeLeft < 600000) {
                    // last 10 minutes and after, 60 secs refresh interval
                    nextRefreshTime = legLoadedAt + 60000;
                } else {
                    // approaching, refresh after 25% of the remaining time
                    nextRefreshTime = nowTime + timeLeft / 4;
                }
            }
            if (nextRefreshTimeA < nextRefreshTime)
                nextRefreshTime = nextRefreshTimeA;

            if (nextRefreshTime <= nowTime)
                doRefresh = true;

            if (doRefresh) {
                log.info("updating leg loaded {} secs ago, required since {} secs ago, begin at {}/{}, end at {}/{}",
                        (nowTime - legLoadedAt) / 1000, (nowTime - nextRefreshTime) / 1000,
                        LOG_TIME_FORMAT.format(new Date(legBeginMinTime)), LOG_TIME_FORMAT.format(new Date(legBeginMaxTime)),
                        LOG_TIME_FORMAT.format(new Date(legEndMinTime)), LOG_TIME_FORMAT.format(new Date(legEndMaxTime)));
            } else {
                oldLeg.updateDelayedUntil = new Date(nextRefreshTime);
                log.info("not updating leg loaded {} secs ago, required in {} secs, begin at {}/{}, end at {}/{}",
                        (nowTime - legLoadedAt) / 1000, (nextRefreshTime - nowTime) / 1000,
                        LOG_TIME_FORMAT.format(new Date(legBeginMinTime)), LOG_TIME_FORMAT.format(new Date(legBeginMaxTime)),
                        LOG_TIME_FORMAT.format(new Date(legEndMinTime)), LOG_TIME_FORMAT.format(new Date(legEndMaxTime)));
            }
        } else {
            log.info("force updating leg, begin at {}/{}, end at {}/{}",
                    LOG_TIME_FORMAT.format(new Date(legBeginMinTime)), LOG_TIME_FORMAT.format(new Date(legBeginMaxTime)),
                    LOG_TIME_FORMAT.format(new Date(legEndMinTime)), LOG_TIME_FORMAT.format(new Date(legEndMaxTime)));
        }
        if (!doRefresh)
            return oldLeg;
        final boolean mustLoadPath = oldLeg.getPath() == null;
        final QueryJourneyResult result = networkProvider.queryJourney(journeyRef, mustLoadPath);
        if (result == null
                || result.status != QueryJourneyResult.Status.OK
                || result.journeyLeg == null) {
            // signal error
            return null;
        }
        return buildUpdatedLeg(oldLeg, result.journeyLeg, now);
    }

    public static Trip.Public buildUpdatedLeg(
            final Trip.Public initialLeg,
            final Trip.Public journeyLeg,
            final Date loadedAt) {
        final List<Stop> journeyStops = new ArrayList<>();
        journeyStops.add(journeyLeg.departureStop);
        if (journeyLeg.intermediateStops != null)
            journeyStops.addAll(journeyLeg.intermediateStops);
        journeyStops.add(journeyLeg.arrivalStop);

        final int numStops = journeyStops.size();

        final long initialDepartureTime = initialLeg.departureStop.getDepartureTime(true).getTime();
        int departureIndex = -1;
        final String depId = initialLeg.departureStop.location.id;
        if (depId != null) {
            // find original departure by ID
            long minTimeDiff = Long.MAX_VALUE;
            for (int index = 0; index < numStops; ++index) {
                final Stop stop = journeyStops.get(index);
                if (!depId.equals(stop.location.id))
                    continue;
                final PTDate depTime = stop.getDepartureTime(true);
                if (depTime == null) {
                    if (departureIndex < 0)
                        departureIndex = index;
                } else {
                    final long timeDiff = Math.abs(depTime.getTime() - initialDepartureTime);
                    if (timeDiff < minTimeDiff) {
                        departureIndex = index;
                        minTimeDiff = timeDiff;
                    }
                }
            }
        }
        if (departureIndex < 0) {
            final Point depCoord = initialLeg.departureStop.location.coord;
            // not found, find departure by coordinate
            // why? as found on INSA-Hafas Tram line 7 "to Franckeplatz" is a different station
            // than the journey stop at "Franckeplatz": different ID, different coordinates, but very near
            if (depCoord != null) {
                double minDist = Double.MAX_VALUE;
                for (int index = 0; index < numStops; ++index) {
                    final Stop stop = journeyStops.get(index);
                    final Point locCoord = stop.location.coord;
                    if (locCoord == null)
                        continue;
                    final double dist = GeoUtils.geoDistanceInMeters(depCoord, locCoord);
                    if (dist >= minDist)
                        continue;
                    final PTDate depTime = stop.getDepartureTime(true);
                    if (depTime != null) {
                        final long timeDiff = Math.abs(depTime.getTime() - initialDepartureTime);
                        if (timeDiff > 120000)
                            continue;
                    }
                    departureIndex = index;
                    minDist = dist;
                }
            }
        }
        final Stop departureStop;
        if (departureIndex >= 0) {
            departureStop = journeyStops.get(departureIndex);
        } else {
            // big fail
            departureStop = initialLeg.departureStop;
            log.error("cannot find departure {} in reloaded journey", departureStop);
            throw new RuntimeException("unable to find departure stop in reloaded journey");
        }

        final long initialArrivalTime = initialLeg.arrivalStop.getArrivalTime(true).getTime();
        int arrivalIndex = -1;
        final String arrId = initialLeg.arrivalStop.location.id;
        if (arrId != null) {
            // find original arrival by ID
            long minTimeDiff = Long.MAX_VALUE;
            for (int index = departureIndex + 1; index < numStops; ++index) {
                final Stop stop = journeyStops.get(index);
                if (!arrId.equals(stop.location.id))
                    continue;
                final PTDate arrTime = stop.getArrivalTime(true);
                if (arrTime == null) {
                    if (arrivalIndex < 0)
                        arrivalIndex = index;
                } else {
                    final long timeDiff = Math.abs(arrTime.getTime() - initialArrivalTime);
                    if (timeDiff < minTimeDiff) {
                        arrivalIndex = index;
                        minTimeDiff = timeDiff;
                    }
                }
            }
        }
        if (arrivalIndex < 0) {
            final Point arrCoord = initialLeg.arrivalStop.location.coord;
            // not found, find arrival by coordinate
            if (arrCoord != null) {
                double minDist = Double.MAX_VALUE;
                for (int index = 0; index < numStops; ++index) {
                    final Stop stop = journeyStops.get(index);
                    final Point locCoord = stop.location.coord;
                    if (locCoord == null)
                        continue;
                    final double dist = GeoUtils.geoDistanceInMeters(arrCoord, locCoord);
                    if (dist >= minDist)
                        continue;
                    final PTDate arrTime = stop.getArrivalTime(true);
                    if (arrTime != null) {
                        final long timeDiff = Math.abs(arrTime.getTime() - initialArrivalTime);
                        if (timeDiff > 120000)
                            continue;
                    }
                    arrivalIndex = index;
                    minDist = dist;
                }
            }
        }
        final Stop arrivalStop;
        if (arrivalIndex >= 0) {
            arrivalStop = journeyStops.get(arrivalIndex);
        } else {
            // big fail
            arrivalStop = initialLeg.arrivalStop;
            log.error("cannot find arrival {} in reloaded journey", arrivalStop);
            throw new RuntimeException("unable to find arrival stop in reloaded journey");
        }

        final List<Stop> intermediateStops;
        if (departureIndex >= 0 && arrivalIndex >= 0) {
            intermediateStops = new ArrayList<>();
            for (int index = departureIndex + 1; index < arrivalIndex; ++ index)
                intermediateStops.add(journeyStops.get(index));
        } else {
            intermediateStops = initialLeg.intermediateStops;
        }

        final Trip.Public newLeg = new Trip.Public(
                journeyLeg.line,
                journeyLeg.destination,
                departureStop, arrivalStop, intermediateStops,
                journeyLeg.message,
                initialLeg.journeyRef,
                loadedAt);
        final List<Point> journeyLegPath = journeyLeg.getPath();
        newLeg.setPath(journeyLegPath != null ? journeyLegPath : initialLeg.getPath());
        newLeg.setEntryAndExit(initialLeg.entryLocation, initialLeg.exitLocation);
        return newLeg;
    }
}
