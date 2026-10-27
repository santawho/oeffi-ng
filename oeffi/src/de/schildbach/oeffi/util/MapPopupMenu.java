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

package de.schildbach.oeffi.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.Objects;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.plans.PlanActivity;
import de.schildbach.oeffi.plans.PlanContentProvider;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;

public class MapPopupMenu extends PopupMenu {
    public static MapPopupMenu createPopupMenuIfNotEmpty(
            @NonNull final Activity context, @NonNull final View anchor,
            final NetworkId network, final Location location) {
        final MapPopupMenu popupMenu = new MapPopupMenu(context, anchor, network, location);
        return popupMenu.hasItems ? popupMenu : null;
    }

    public final boolean hasItems;

    public MapPopupMenu(
            @NonNull final Activity context, @NonNull final View anchor,
            final NetworkId network, final Location location) {
        super(context, R.menu.station_map_context, anchor);
        hasItems = prepareMapMenu(context, getMenu(), network, location);
    }

    public static boolean setupSubMenu(
            @NonNull final Activity context, @NonNull final MenuItem subMenuItem,
            final NetworkId network, final Location location) {
        final SubMenu subMenu = Objects.requireNonNull(subMenuItem.getSubMenu());
        new MenuInflater(context).inflate(R.menu.station_map_context, subMenu);
        return prepareMapMenu(context, subMenu, network, location);
    }

    private static boolean prepareMapMenu(
            final Activity context, final Menu menu,
            final NetworkId network, final Location location) {
        final MenuItem mapsItemInternal = menu.findItem(R.id.station_map_context_maps_internal);
        final MenuItem mapsItemExternal = menu.findItem(R.id.station_map_context_maps_external);
        final MenuItem mapsItemChooseExternal = menu.findItem(R.id.station_map_context_maps_choose_external);
        final MenuItem mapsItemInfoUrl = menu.findItem(R.id.station_map_context_info_url);
        boolean haveVisibleItems = false;

        final String infoUrl = NetworkProviderFactory.provider(network).getLocationInfoUrl(location);
        if (infoUrl != null) {
            haveVisibleItems = true;
            mapsItemInfoUrl.setVisible(true);
            mapsItemInfoUrl.setOnMenuItemClickListener(item -> {
                @SuppressLint("UnsafeImplicitIntentLaunch")
                final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(infoUrl));
                context.startActivity(intent);
                return true;
            });
        } else {
            mapsItemInfoUrl.setVisible(false);
        }

        if (location.hasCoord()) {
            haveVisibleItems = true;
            mapsItemExternal.setOnMenuItemClickListener(item -> {
                ExternalMapsUtils.openPointInPreselectedExternalMapsApp(context, location);
                return true;
            });
            mapsItemChooseExternal.setOnMenuItemClickListener(item -> {
                ExternalMapsUtils.openPointByChoosingExternalMapsApp(context, location);
                return true;
            });

            mapsItemInternal.setVisible(true);
            mapsItemExternal.setVisible(true);
            mapsItemChooseExternal.setVisible(true);
        } else {
            mapsItemInternal.setVisible(false);
            mapsItemExternal.setVisible(false);
            mapsItemChooseExternal.setVisible(false);
        }

        final ContentResolver contentResolver = context.getContentResolver();
        final Cursor stationsCursor = contentResolver.query(PlanContentProvider.stationsUri(network, location.id), null,
                null, null, null);
        if (stationsCursor != null) {
            final int planIdColumn = stationsCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_STATION_PLAN_ID);
            while (stationsCursor.moveToNext()) {
                final String planId = stationsCursor.getString(planIdColumn);
                final Cursor plansCursor = contentResolver.query(PlanContentProvider.planUri(planId), null, null, null,
                        null);
                plansCursor.moveToFirst();
                final String planName = plansCursor
                        .getString(plansCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_NAME));
                plansCursor.close();
                haveVisibleItems = true;
                menu.add(planName).setOnMenuItemClickListener(item -> {
                    PlanActivity.start(context, planId, location.id);
                    return true;
                });
            }
            stationsCursor.close();
        }

        return haveVisibleItems;
    }
}
