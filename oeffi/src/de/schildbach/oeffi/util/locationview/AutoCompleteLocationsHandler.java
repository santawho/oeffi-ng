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

package de.schildbach.oeffi.util.locationview;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Handler;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import de.schildbach.oeffi.R;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Product;

public class AutoCompleteLocationsHandler {
    public static class Result {
        public boolean success;
        public Location location;
    }
    private final Activity activity;
    private final NetworkId network;
    private final Handler handler;
    private final Set<Product> preferredProducts;
    private final AtomicInteger numAwaitedLocations = new AtomicInteger();
    private final AtomicInteger numBadLocations = new AtomicInteger();
    private final List<Runnable> jobs = new ArrayList<>();
    private Consumer<Result> readyListener;
    private ProgressDialog progressDialog;

    public AutoCompleteLocationsHandler(
            final Activity activity,
            final NetworkId network,
            final Handler handler,
            final Set<Product> preferredProducts) {
        this.activity = activity;
        this.network = network;
        this.handler = handler;
        this.preferredProducts = preferredProducts;
    }

    public void addJob(final CharSequence constraint, final LocationView locationView) {
        if (locationView != null)
            locationView.reset();
        if (constraint == null)
            return;
        if (locationView != null) {
            locationView.setLocation(null);
            locationView.setText(constraint);
        }

        jobs.add(() -> {
            final List<Location> locations = LocationSuggestionsCollector.collectSuggestions(
                    constraint,
                    EnumSet.of(LocationType.STATION, LocationType.ADDRESS, LocationType.POI),
                    network, null);
            final Location location = getMatchingLocation(locations);
            if (location != null) {
                activity.runOnUiThread(() -> {
                    if (locationView != null)
                        locationView.setLocation(location);
                    jobFinished(location);
                });
            } else {
                jobFinished(null);
            }
        });
    }

    private Location getMatchingLocation(final List<Location> locations) {
        if (locations == null || locations.isEmpty())
            return null;
        int numLocationsChecked = 0;
        int bestMatchNumProducts = 0;
        Location bestMatchLocation = null;
        for (final Location location : locations) {
            final Set<Product> locationProducts = location.products;
            if (locationProducts == null) {
                // no products, then this location was maybe loaded from the history database
                // a bit hacky, but then this is a bestmatch
                bestMatchLocation = location;
                break;
            }
            int numMatches = 0;
            for (final Product preferredProduct : preferredProducts) {
                if (locationProducts.contains(preferredProduct))
                    numMatches += 1;
            }
            if (bestMatchLocation == null || numMatches > bestMatchNumProducts) {
                bestMatchLocation = location;
                bestMatchNumProducts = numMatches;
            }

            numLocationsChecked += 1;
            if (numLocationsChecked >= 3)
                break;
        }
        return bestMatchLocation;
    }

    public void start(final Consumer<Result> readyListener) {
        this.readyListener = readyListener;
        final int numJobs = jobs.size();
        numAwaitedLocations.set(numJobs);
        numBadLocations.set(numJobs);

        progressDialog = ProgressDialog.show(activity, null,
                activity.getString(R.string.locations_query_progress),
                true, true, dialog -> {
                    // cancelled
                    numAwaitedLocations.set(-1); // set so that it never will decrement to zero
                });
        progressDialog.setCanceledOnTouchOutside(false);

        for (final Runnable job : jobs)
            handler.post(job);
    }

    private void jobFinished(final Location location) {
        if (location != null)
            numBadLocations.decrementAndGet();
        final boolean allReady = numAwaitedLocations.decrementAndGet() == 0;
        if (allReady) {
            progressDialog.dismiss();
            if (readyListener != null) {
                final Result result = new Result();
                result.success = numBadLocations.get() <= 0;
                result.location = location;
                readyListener.accept(result);
            }
        }
    }
}
