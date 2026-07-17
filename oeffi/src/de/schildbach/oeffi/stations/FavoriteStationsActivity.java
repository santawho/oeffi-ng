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

package de.schildbach.oeffi.stations;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ViewAnimator;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.mapview.OeffiMapView;
import de.schildbach.oeffi.network.NetworkPickerActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.locationview.LocationView;
import de.schildbach.oeffi.stations.list.FavoriteStationsAdapter;
import de.schildbach.oeffi.stations.list.StationClickListener;
import de.schildbach.oeffi.stations.list.StationContextMenuItemListener;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Product;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class FavoriteStationsActivity extends OeffiActivity
        implements StationClickListener, StationContextMenuItemListener {
    public static class Main extends FavoriteStationsActivity {
        public static void start(final Context context) {
            final Intent intent = new Intent(context, Main.class);
            context.startActivity(intent);
        }
    }

    public static void start(final Context context) {
        final Intent intent = new Intent(context, FavoriteStationsActivity.class);
        context.startActivity(intent);
    }

    public static class PickFavoriteStationContract extends ActivityResultContract<NetworkId, Uri> {
        @Override
        public Intent createIntent(final Context context, final NetworkId network) {
            final Intent intent = new Intent(context, FavoriteStationsActivity.class);
            intent.putExtra(INTENT_EXTRA_NETWORK_NAME, requireNonNull(network).name());
            return intent;
        }

        @Override
        public Uri parseResult(final int resultCode, @Nullable final Intent intent) {
            if (resultCode == Activity.RESULT_OK && intent != null)
                return intent.getData();
            else
                return null;
        }
    }

    boolean shouldReturnResult;
    private ViewAnimator viewAnimator;
    private RecyclerView listView;
    private LocationView viewNewLocation;
    private FavoriteStationsAdapter adapter;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    final LocationView.Listener locationListener = new LocationView.Listener() {
        @Override
        public NetworkId getNetwork() {
            return network;
        }

        @Override
        public String getUsage() {
            return null;
        }

        @Override
        public Set<Product> getPreferredProducts() {
            return getNetworkDefaultProducts();
        }

        @Override
        public Handler getHandler() {
            return backgroundHandler;
        }

        @Override
        public void changed(final LocationView view) {
            final Location location = viewNewLocation.getLocation();
            if (location == null || location.coord == null || !location.hasId())
                return;

            viewNewLocation.setVisibility(View.GONE);
            viewNewLocation.reset();
            onNewStationAdded(location);
            updateGUI();
        }

        @Override
        public OeffiMapView.MapPointSelectionProvider getMapPointSelectionProvider() {
            return null;
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().getStringExtra(INTENT_EXTRA_NETWORK_NAME) != null)
            shouldReturnResult = true; // TODO a bit hacky

        backgroundThread = new HandlerThread("FavoriteStations", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        final boolean isRootActivity = isTaskRoot() && !shouldReturnResult;

        setContentView(R.layout.favorites_content, isRootActivity);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        final MyActionBar actionBar = getMyActionBar();
        setPrimaryColor(R.color.bg_action_bar_station_favorites);
        actionBar.setPrimaryTitle(R.string.stations_favorite_stations_title);
        if (this instanceof Main) {
            actionBar.setTitlesOnClickListener(
                    v -> NetworkPickerActivity.start(FavoriteStationsActivity.this));
        }
        actionBar.setBack(isRootActivity ? null : v -> finish());
        actionBar.addButton(R.drawable.ic_add_white_24dp, R.string.stations_favorite_stations_add_title)
                .setOnClickListener(view -> viewNewLocation.setVisibility(
                    viewNewLocation.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        viewAnimator = findViewById(R.id.favorites_layout);

        listView = findViewById(R.id.favorites_list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        resetAdapter();
        ViewCompat.setOnApplyWindowInsetsListener(listView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });

        viewNewLocation = findViewById(R.id.favorites_new);
        viewNewLocation.setVisibility(View.GONE);
        viewNewLocation.setHint(R.string.stations_favorite_stations_add_location_hint);
        viewNewLocation.setImeOptions(EditorInfo.IME_ACTION_GO);
        viewNewLocation.setOnEditorActionListener((v, actionId, event) -> {
            final Location location = viewNewLocation.getLocation();
            if (location == null || location.coord == null || !location.hasId())
                return false;
            locationListener.changed(viewNewLocation);
            return true;
        });

        setupLocationViews();
        updateGUI();
    }

    private void setupLocationViews() {
        viewNewLocation.setListener(locationListener);

        resetLocationViewsBehaviour();
    }

    private void resetLocationViewsBehaviour() {
        viewNewLocation.resetBehaviour();
    }

    @Override
    protected int getGlobalOptionsId() {
        return R.id.global_options_stations_favorites;
    }

    @Override
    protected boolean acceptNetworkChanges() {
        return !shouldReturnResult;
    }

    @Override
    protected void onChangeNetwork(final NetworkId network) {
        resetAdapter();
        setupLocationViews();
        updateGUI();
        setActionBarSecondaryTitleFromNetwork();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setActionBarSecondaryTitleFromNetwork();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetAdapter();
    }

    @Override
    protected void onDestroy() {
        backgroundThread.getLooper().quit();
        super.onDestroy();
    }

    private void resetAdapter() {
        adapter = new FavoriteStationsAdapter(this, network, this, shouldReturnResult ? null : this);
        listView.setAdapter(adapter);
    }

    private void onNewStationAdded(final Location newFavoriteStation) {
        final Uri uri = FavoriteUtils.persist(getContentResolver(),
                FavoriteStationsProvider.TYPE_FAVORITE, network, newFavoriteStation);

        resetAdapter();

        if (shouldReturnResult) {
            final Intent intent = new Intent();
            intent.setData(uri);
            setResult(RESULT_OK, intent);
            finish();
        } else if (newFavoriteStation.type == LocationType.STATION) {
            StationDetailsActivity.start(FavoriteStationsActivity.this, network, newFavoriteStation, null, null);
        }
    }

    public void onStationClick(final int adapterPosition, final NetworkId stationNetwork, final Location station) {
        if (shouldReturnResult) {
            final Intent intent = new Intent();
            final Uri uri = Uri.withAppendedPath(FavoriteStationsProvider.CONTENT_URI(),
                    String.valueOf(adapter.getItemId(adapterPosition)));
            intent.setData(uri);
            setResult(RESULT_OK, intent);
            finish();
            return;
        }

        if (station.type != LocationType.STATION && !station.hasCoord()) {
            new Toast(this).longToast(R.string.stations_no_departures_for_address);
            return;
        }

        StationDetailsActivity.start(FavoriteStationsActivity.this, stationNetwork, station, null, null);
    }

    public boolean onStationContextMenuItemClick(final int adapterPosition, final NetworkId stationNetwork,
            final Location station, final @Nullable List<Departure> departures, final int menuItemId) {
        if (menuItemId == R.id.station_context_show_departures) {
            StationDetailsActivity.start(FavoriteStationsActivity.this, stationNetwork, station, null, departures);
            return true;
        } else if (menuItemId == R.id.station_context_nearby_departures) {
            StationsActivity.start(FavoriteStationsActivity.this, stationNetwork, station, null);
            return true;
        } else if (menuItemId == R.id.station_context_rename_favorite) {
            adapter.renameEntry(adapterPosition);
            updateGUI();
            NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
            return true;
        } else if (menuItemId == R.id.station_context_remove_favorite) {
            adapter.removeEntry(adapterPosition);
            updateGUI();
            NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
            return true;
        } else if (menuItemId == R.id.station_context_directions_from) {
            DirectionsActivity.start(FavoriteStationsActivity.this,
                    network, station, null, null, null, null, false,
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return true;
        } else if (menuItemId == R.id.station_context_directions_to) {
            DirectionsActivity.start(FavoriteStationsActivity.this,
                    network, null, station, null, null, null, false,
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return true;
        } else if (menuItemId == R.id.station_context_launcher_shortcut) {
            StationContextMenu.showLauncherShortcutDialog(FavoriteStationsActivity.this, network, station);
            return true;
        } else if (menuItemId == R.id.station_context_infopage) {
            final String infoUrl = NetworkProviderFactory.provider(network).getLocationInfoUrl(station);
            if (infoUrl != null) {
                @SuppressLint("UnsafeImplicitIntentLaunch")
                final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(infoUrl));
                startActivity(intent);
            }
            return true;
        } else if (menuItemId == R.id.station_map_context_maps_internal) {
            // no map on this activity, sorry
            return true;
        } else {
            return false;
        }
    }

    public void updateGUI() {
        viewAnimator.setDisplayedChild(adapter.getItemCount() > 0 ? 0 : 1);
    }
}
