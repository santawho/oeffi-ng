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

package de.schildbach.oeffi.directions.driverops;

import android.content.Context;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.directions.QueryStoredTripsProvider;
import de.schildbach.oeffi.directions.QueryTripsRunnable;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.TripsOverviewActivity;
import de.schildbach.oeffi.directions.navigation.NavigationNotification;
import de.schildbach.oeffi.directions.navigation.TripNavigatorActivity;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.Trip;

public class OperationsActivity extends DirectionsActivity {

    @Override
    protected int getGlobalOptionsId() {
        return R.id.global_options_operations;
    }

    @Override
    protected int getActionBarColorId() {
        return R.color.bg_action_bar_operations;
    }

    @Override
    protected int getActionBarTitleStringId() {
        return R.string.operations_activity_title;
    }

    @Override
    protected boolean isForceDirectOption() {
        return true;
    }

    @Override
    protected void setupTripsOverviewRenderConfig(final TripsOverviewActivity.RenderConfig renderConfig) {
        super.setupTripsOverviewRenderConfig(renderConfig);
        renderConfig.isOperationsPlanning = true;
    }

    @Override
    protected void setupTripDetailsRenderConfig(final TripDetailsActivity.RenderConfig renderConfig) {
        super.setupTripDetailsRenderConfig(renderConfig);
        renderConfig.isOperation = true;
        renderConfig.isJourney = true;
    }

    @Override
    protected String getStoredTripsUsage() {
        return QueryStoredTripsProvider.USAGE_OPERATION;
    }

    protected boolean getStoredTripsCanBeMarkedAsDone() {
        return true;
    }

    @Override
    public boolean isTripUnderNavigation(final Context context, final String tripId) {
        return OperationNotification.isTripUnderOperation(context, tripId);
    }

    protected int getHistoryEntryLayoutId() {
        return R.layout.directions_query_history_entry_no_trip;
    }

    @Override
    protected String get_PREFS_KEY_STORED_TRIPS_RETENTION_HOURS() {
        return "extras_drivermode_stored_operations_retention_hours";
    }

    protected long getUpcomingStoredTripsTimeLimitMs() {
        return 30 * 60000; // show operations of next 30 minutes as upcoming
    }

    @Override
    protected Set<Product> getNetworkDefaultProducts() {
        return new HashSet<>(); // empty set
    }

    @Override
    protected boolean productsAreNetworkDefault(final Collection<Product> products) {
        return !products.isEmpty();
    }

    @Override
    protected String getProductsPrefsKey() {
        return super.getProductsPrefsKey() + "@OP";
    }

    @Override
    protected void handleShowSavedTrip(
            final Location from, final Location to, final Location via,
            final PTDate tripDepartureTime, final PTDate tripArrivalTime,
            final byte[] serializedTrip, final String tripId,
            final byte[] serializedReloadRequest) {
        final Trip trip = (Trip) Objects.deserialize(serializedTrip, true);
        if (trip == null) {
            new Toast(this).longToast(R.string.directions_query_history_invalid_blob);
            return;
        }
        loadTripByTripRef(trip.tripRef, (loadedTrip) -> {
            final Trip useTrip = loadedTrip != null ? loadedTrip : trip;
            final OperationDetailsActivity.RenderConfig config = new OperationDetailsActivity.RenderConfig();
            config.queryTripsRequestData = (QueryTripsRunnable.TripRequestData) Objects.deserialize(serializedReloadRequest, true);
            setupTripDetailsRenderConfig(config);
            final Trip.Public journeyLeg = useTrip.getFirstPublicLeg();
            OperationDetailsActivity.startOperation(OperationsActivity.this, network, journeyLeg, new Date(), 0);
        });
    }

    @Override
    public void onSavedTripStartNavigation(
            final int adapterPosition,
            final Trip trip,
            final QueryTripsRunnable.TripRequestData queryTripsRequestData) {
        final TripDetailsActivity.RenderConfig renderConfig = new TripDetailsActivity.RenderConfig();
        renderConfig.isOperation = true;
        renderConfig.isJourney = true;
        renderConfig.queryTripsRequestData = queryTripsRequestData;
        OperationNavigatorActivity.startNavigation(this, network, trip, renderConfig, false);
    }
}
