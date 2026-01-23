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

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;

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

    protected int getHistoryEntryLayoutId() {
        return R.layout.directions_query_history_entry_no_trip;
    }
}
