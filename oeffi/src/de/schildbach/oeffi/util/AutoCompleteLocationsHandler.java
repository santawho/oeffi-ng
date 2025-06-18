package de.schildbach.oeffi.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.LocationView;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Product;

public class AutoCompleteLocationsHandler {
    public static class Result {
        public boolean success;
        public Location location;
    }
    private final AutoCompleteLocationAdapter autoCompleteLocationAdapter;
    private final Handler handler;
    private Set<Product> preferredProducts;
    private final AtomicInteger numAwaitedLocations = new AtomicInteger();
    private final AtomicInteger numBadLocations = new AtomicInteger();
    private final List<Runnable> jobs = new ArrayList<>();
    private Consumer<Result> readyListener;
    private ProgressDialog progressDialog;

    public AutoCompleteLocationsHandler(
            final AutoCompleteLocationAdapter autoCompleteLocationAdapter,
            final Handler handler,
            final Set<Product> preferredProducts) {
        this.autoCompleteLocationAdapter = autoCompleteLocationAdapter;
        this.handler = handler;
        this.preferredProducts = preferredProducts;
    }

    public void addJob(final CharSequence constraint, final LocationView locationView) {
        if (locationView != null)
            locationView.reset();
        if (constraint == null)
            return;
        if (locationView != null)
            locationView.setText(constraint);

        jobs.add(() -> {
            final List<Location> locations = autoCompleteLocationAdapter.collectSuggestions(constraint);
            final Location location = getMatchingLocation(locations);
            if (location != null) {
                getActivity().runOnUiThread(() -> {
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
        for (Location location : locations) {
            final Set<Product> locationProducts = location.products;
            if (locationProducts == null) {
                // no products, then this location was maybe loaded from the history database
                // a bit hacky, but then this is a bestmatch
                bestMatchLocation = location;
                break;
            }
            int numMatches = 0;
            for (Product preferredProduct : preferredProducts) {
                if (locationProducts.contains(preferredProduct))
                    numMatches += 1;
            }
            if (numMatches > bestMatchNumProducts) {
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

        progressDialog = ProgressDialog.show(getActivity(), null,
                getActivity().getString(R.string.locations_query_progress),
                true, true, dialog -> {
                    // cancelled
                    numAwaitedLocations.set(-1); // set so that it never will decrement to zero
                });
        progressDialog.setCanceledOnTouchOutside(false);

        for (Runnable job : jobs)
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

    private Activity getActivity() {
        return autoCompleteLocationAdapter.getActivity();
    }
}
