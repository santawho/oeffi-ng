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

package de.schildbach.oeffi.widget;

import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NearestFavoriteStationWidgetProvider extends AppWidgetProvider {

    private static final Logger log = LoggerFactory.getLogger(NearestFavoriteStationWidgetProvider.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        log.info("got broadcast: {}", action);
        NearestFavoriteStationWidgetService.schedulePeriodic(context);
    }
}
