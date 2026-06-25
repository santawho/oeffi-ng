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

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Criteria;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.GeocoderThread;
import de.schildbach.oeffi.util.LocationHelper;
import de.schildbach.oeffi.util.LocationUtils;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.provider.NetworkProvider.Accessibility;
import de.schildbach.pte.provider.NetworkProvider.Optimize;
import de.schildbach.pte.provider.NetworkProvider.WalkSpeed;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.TripOptions;

import java.util.Set;

public class DirectionsShortcutActivity extends OeffiActivity implements LocationHelper.Callback {
    public static final String INTENT_EXTRA_NETWORK = "network";
    public static final String INTENT_EXTRA_TYPE = "type";
    public static final String INTENT_EXTRA_ID = "stationid";
    public static final String INTENT_EXTRA_LAT = "lat";
    public static final String INTENT_EXTRA_LON = "lon";
    public static final String INTENT_EXTRA_PLACE = "place";
    public static final String INTENT_EXTRA_NAME = "stationname";

    public static Intent fillIntent(final Intent intent, final NetworkId networkId, final Location location) {
        intent.putExtra(INTENT_EXTRA_NETWORK, networkId.name());
        intent.putExtra(INTENT_EXTRA_TYPE, location.type.name());
        if (location.hasId())
            intent.putExtra(INTENT_EXTRA_ID, location.id);
        if (location.hasCoord()) {
            intent.putExtra(INTENT_EXTRA_LAT, location.getLatAs1E6());
            intent.putExtra(INTENT_EXTRA_LON, location.getLonAs1E6());
        }
        intent.putExtra(INTENT_EXTRA_PLACE, location.place);
        intent.putExtra(INTENT_EXTRA_NAME, location.name);
        return intent;
    }

    private LocationHelper locationHelper;
    private ProgressDialog progressDialog;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted)
                    maybeStartLocation();
                else
                    errorDialog(R.string.acquire_location_no_permission);
            });

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        locationHelper = new LocationHelper(this, this);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            maybeStartLocation();
        else
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public void maybeStartLocation() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;
        if (locationHelper.isRunning())
            return;

        final Criteria criteria = new Criteria();
        criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        locationHelper.startLocation(criteria, false, Constants.LOCATION_FOREGROUND_UPDATE_TIMEOUT_MS);
    }

    public void stopLocation() {
        locationHelper.stop();
    }

    public void onLocationStart(final String provider) {
        progressDialog = ProgressDialog.show(DirectionsShortcutActivity.this, null,
                getString(R.string.acquire_location_start, provider), true, true, dialog -> {
                    locationHelper.stop();
                    finish();
                });
        progressDialog.setCanceledOnTouchOutside(false);
    }

    public void onLocationStop(final boolean timedOut) {
        if (timedOut) {
            progressDialog.dismiss();

            errorDialog(R.string.acquire_location_timeout);
        }
    }

    public void onLocationFail() {
        errorDialog(R.string.acquire_location_no_provider);
    }

    public void onLocation(final Point here) {
        new GeocoderThread(DirectionsShortcutActivity.this, here, new GeocoderThread.Callback() {
            public void onGeocoderResult(final Address address) {
                final Location location = GeocoderThread.addressToLocation(address);
                query(location);
            }

            public void onGeocoderFail(final Exception exception) {
                final Location location = LocationUtils
                        .locationFromCoord(Point.fromDouble(here.getLatAsDouble(), here.getLonAsDouble()));
                query(location);
            }
        });
    }

    private void query(final Location from) {
        final Intent intent = getIntent();

        final NetworkProvider networkProvider = getNetworkExtra(intent);
        final LocationType type = getLocationTypeExtra(intent);
        final String id = getLocationIdExtra(intent);
        final int lat = intent.getIntExtra(INTENT_EXTRA_LAT, 0);
        final int lon = intent.getIntExtra(INTENT_EXTRA_LON, 0);
        final Point coord = lat != 0 || lon != 0 ? Point.from1E6(lat, lon) : null;
        final String place = intent.getStringExtra(INTENT_EXTRA_PLACE);
        final String name = intent.getStringExtra(INTENT_EXTRA_NAME);
        final Location to = new Location(type, id, coord, place, name);

        if (networkProvider != null) {
            final Optimize optimize = prefs.contains(Constants.PREFS_KEY_OPTIMIZE_TRIP)
                    ? Optimize.valueOf(prefs.getString(Constants.PREFS_KEY_OPTIMIZE_TRIP, null)) : null;
            final WalkSpeed walkSpeed = WalkSpeed
                    .valueOf(prefs.getString(Constants.PREFS_KEY_WALK_SPEED, WalkSpeed.NORMAL.name()));
            final int mwd = Integer.parseInt(prefs.getString(Constants.PREFS_KEY_MAX_WALK_DISTANCE, "-1"));
            final Integer maxWalkDistance = mwd < 0 ? null : mwd;
            final int mtt = Integer.parseInt(prefs.getString(Constants.PREFS_KEY_MIN_TRANSFER_TIME, "-1"));
            final Integer minTransferTime = mtt < 0 ? null : mtt;
            final Accessibility accessibility = application.prefsGetAccessibility();
            final Set<Product> products =  loadProductFilter();
            final TripOptions options = new TripOptions(products, optimize, walkSpeed, maxWalkDistance, minTransferTime, accessibility, null);

            // old solution: searches within the DirectionsShortcutActivity
            // and then switches to the TripsOverviewActivity
            //    query(networkProvider, from, to, options);

            // new solution: searches within the TripsOverviewActivity
            final TripsOverviewActivity.RenderConfig newRenderConfig = new TripsOverviewActivity.RenderConfig();
            newRenderConfig.referenceTime = new TimeSpec.Relative(0);
            TripsOverviewActivity.start(this,
                    networkProvider, from, null, to, options, newRenderConfig);
            finishAndRemoveTask();
        } else {
            errorDialog(R.string.directions_shortcut_error_message_network);
        }
    }

    private void errorDialog(final int resId) {
        final DialogBuilder builder = DialogBuilder.warn(this, R.string.directions_shortcut_error_title);
        builder.setMessage(resId);
        builder.setPositiveButton("Ok", (dialog, which) -> finish());
        builder.setOnCancelListener(dialog -> finish());
        builder.show();
    }

    private NetworkProvider getNetworkExtra(final Intent intent) {
        try {
            final NetworkId network = NetworkId.valueOf(intent.getStringExtra(INTENT_EXTRA_NETWORK));
            return NetworkProviderFactory.provider(network);
        } catch (final IllegalArgumentException x) {
            return null;
        }
    }

    private LocationType getLocationTypeExtra(final Intent intent) {
        final String type = intent.getStringExtra(INTENT_EXTRA_TYPE);
        return type != null ? LocationType.valueOf(type) : LocationType.STATION;
    }

    private String getLocationIdExtra(final Intent intent) {
        final String id = intent.getStringExtra(INTENT_EXTRA_ID);
        if (id != null)
            return id;

        // old shortcuts
        final int idInt = intent.getIntExtra(INTENT_EXTRA_ID, -1);
        if (idInt != -1)
            return Integer.toString(idInt);

        return null;
    }
}
