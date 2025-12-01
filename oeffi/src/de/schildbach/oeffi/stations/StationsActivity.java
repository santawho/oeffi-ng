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

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewAnimator;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.DeviceLocationAware;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiMainActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.directions.QueryJourneyRunnable;
import de.schildbach.oeffi.util.GeoUtils;
import de.schildbach.oeffi.util.KeyWordMatcher;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.network.NetworkPickerActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.list.JourneyClickListener;
import de.schildbach.oeffi.stations.list.StationContextMenuItemListener;
import de.schildbach.oeffi.stations.list.StationsAdapter;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.oeffi.util.locationview.AutoCompleteLocationsHandler;
import de.schildbach.oeffi.util.ConnectivityBroadcastReceiver;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.GoogleMapsUtils;
import de.schildbach.oeffi.util.LocationUriParser;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.locationview.LocationView;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.NetworkProvider.Capability;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyLocationsResult;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;
import de.schildbach.pte.dto.SuggestLocationsResult;
import de.schildbach.pte.dto.PTDate;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StationsActivity extends OeffiMainActivity implements StationsAware, DeviceLocationAware,
        StationContextMenuItemListener, JourneyClickListener {
    // if this would be set to TRUE, the search function in this activity would behave
    // like the original Öffi app, i.e. search on the network:
    // for example "berlin" would find all stations in Berlin, but also all "Berliner Str."
    // or "Berliner Platz" in all over the country, and then sort the result by distance
    // to the device location. As the result set is limited in size, the desired stations
    // near the device location may not event by included in the set, and thus never shown.
    // Setting to FALSE brings the new behaviour, which is a local filtering by station names
    // and line labels, i.e. you can also search for something like "U1"
    public static final boolean DO_FILTER_BY_SEARCH_ON_NETWORK = false;

    public static final String INTENT_EXTRA_OPEN_FAVORITES = StationsActivity.class.getName() + ".open_favorites";
    public static final String INTENT_EXTRA_NETWORK = StationsActivity.class.getName() + ".network";
    public static final String INTENT_EXTRA_LOCATION = StationsActivity.class.getName() + ".location";
    public static final String INTENT_EXTRA_TIME = StationsActivity.class.getName() + ".time";
    private static final String INTENT_EXTRA_COMMAND = StationsActivity.class.getName() + ".command";
    private ConnectivityManager connectivityManager;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor sensorAccelerometer;
    private Sensor sensorMagnetometer;
    private Resources res;

    private Date presetTime;
    private final List<Station> stations = new ArrayList<>();
    private final Map<String, Station> stationsMap = new HashMap<>();
    private final Map<String, Integer> favorites = new HashMap<>();
    private Station selectedStation;
    private Point deviceLocation;
    private Location fixedLocation;
    private boolean fixedLocationResolving;
    private Float deviceBearing = null;
    private String filterByText;
    private String searchQuery;
    private KeyWordMatcher.Query filterQuery;
    private boolean anyProviderEnabled = false;
    private boolean loading = true;

    private final Set<Product> products = new HashSet<>(Product.ALL_SELECTABLE);
    private String accurateLocationProvider, lowPowerLocationProvider;

    private MyActionBar actionBar;
    private RecyclerView stationList;
    private LinearLayoutManager stationListLayoutManager;
    private StationsAdapter stationListAdapter;
    private TextView connectivityWarningView;
    private TextView disclaimerSourceView;
    private View filterActionButton;
    private ViewGroup locationProvidersView;
    private LocationView viewLocation;
    private SwipeRefreshLayout swipeRefresh;

    private QueryJourneyRunnable queryJourneyRunnable;
    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private BroadcastReceiver connectivityReceiver;
    private BroadcastReceiver tickReceiver;

    private int maxDeparturesPerStation;

    final LocationView.Listener locationViewListener = new LocationView.Listener() {
        @Override
        public NetworkId getNetwork() {
            return network;
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
            fixedLocation = viewLocation.getLocation();
            if (fixedLocation != null) {
                onFixedLocationSet();
            } else {
                setToDeviceLocation();
            }
            updateGUI();
        }
    };

    private void setupLocationViews() {
        viewLocation.setListener(locationViewListener);

        resetLocationViewsBehaviour();
    }

    private void resetLocationViewsBehaviour() {
        viewLocation.resetBehaviour();
    }

    private static final int DIALOG_NEARBY_STATIONS_ERROR = 1;

    private static final Logger log = LoggerFactory.getLogger(StationsActivity.class);

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                startLocationProvider();
            });

    @Override
    protected String taskName() {
        return "stations";
    }

    public static void start(final Activity context, final boolean openFavorites) {
        final Intent intent = new Intent(context, StationsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(StationsActivity.INTENT_EXTRA_OPEN_FAVORITES, openFavorites);
        context.startActivity(intent);
    }

    public static void start(final Context context, final NetworkId networkId, final Location location, final Date time) {
        final Intent intent = new Intent(context, StationsActivity.class);
        if (networkId != null)
            intent.putExtra(StationsActivity.INTENT_EXTRA_NETWORK, networkId.name());
        if (location != null)
            intent.putExtra(StationsActivity.INTENT_EXTRA_LOCATION, location);
        if (time != null)
            intent.putExtra(StationsActivity.INTENT_EXTRA_TIME, time);
        context.startActivity(intent);
    }

    public static class Command implements Serializable {
        private static final long serialVersionUID = 4782653146464112314L;
        public String atText;
        public TimeSpec time;
    }

    public static void start(
            final Context context,
            final StationsActivity.Command command,
            final int intentFlags) {
        final Intent intent = new Intent(context, StationsActivity.class).addFlags(intentFlags);
        intent.putExtra(StationsActivity.INTENT_EXTRA_COMMAND, command);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMagnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        res = getResources();

        setContentView(R.layout.stations_content);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        actionBar = getMyActionBar();
        setPrimaryColor(R.color.bg_action_bar_stations);
        actionBar.setPrimaryTitle(R.string.stations_activity_title);
        actionBar.setTitlesOnClickListener(v -> NetworkPickerActivity.start(StationsActivity.this));
        actionBar.addProgressButton().setOnClickListener(v -> requestRefresh());
        actionBar.addButton(R.drawable.ic_star_white_24dp, R.string.stations_options_favorites_title)
                .setOnClickListener(view -> FavoriteStationsActivity.start(StationsActivity.this));
        addShowMapButtonToActionBar();
        actionBar.addButton(R.drawable.ic_search_white_24dp, R.string.stations_action_search_title)
                .setOnClickListener(v -> {
                    if (DO_FILTER_BY_SEARCH_ON_NETWORK) {
                        onSearchRequested();
                    } else {
                        findViewById(R.id.stations_search_box).setVisibility(View.VISIBLE);
                    }
                });
        ViewUtils.setVisibility(findViewById(R.id.stations_search_box), false);
        ViewUtils.setVisibility(findViewById(R.id.stations_search_text), DO_FILTER_BY_SEARCH_ON_NETWORK);
        final EditText searchEditView = findViewById(R.id.stations_search_edit);
        findViewById(R.id.stations_search_clear).setOnClickListener(v -> {
            if (!DO_FILTER_BY_SEARCH_ON_NETWORK) {
                findViewById(R.id.stations_search_box).setVisibility(View.GONE);
                searchEditView.setText(null);
            }
            setListFilter(null);
        });
        ViewUtils.setVisibility(searchEditView, !DO_FILTER_BY_SEARCH_ON_NETWORK);
        searchEditView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {}

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
                setListFilter(s.toString());
            }

            @Override
            public void afterTextChanged(final Editable s) {}
        });
        filterActionButton = actionBar.addButton(R.drawable.ic_filter_list_24dp, R.string.stations_filter_title);
        filterActionButton.setOnClickListener(v -> {
            final StationsFilterPopup popup = new StationsFilterPopup(StationsActivity.this, products,
                    filter -> {
                        final Set<Product> added = new HashSet<>(filter);
                        added.removeAll(products);

                        final Set<Product> removed = new HashSet<>(products);
                        removed.removeAll(filter);

                        products.clear();
                        products.addAll(filter);

                        if (!added.isEmpty()) {
                            handler.post(initStationsRunnable);
                        }

                        if (!removed.isEmpty()) {
                            for (final Iterator<Station> i = stations.iterator(); i.hasNext(); ) {
                                final Station station = i.next();
                                if (!filter(station, products)) {
                                    i.remove();
                                    stationsMap.remove(station.location.id);
                                }
                            }

                            stationListAdapter.notifyDataSetChanged();
                            getMapView().invalidate();
                        }

                        updateGUI();
                    });
            popup.showAsDropDown(v);
        });
        //        actionBar.overflow(R.menu.stations_options, item -> {
        //            if (item.getItemId() == R.id.stations_options_favorites) {
        //                FavoriteStationsActivity.start(StationsActivity.this);
        //                return true;
        //            } else {
        //                return false;
        //            }
        //        });

        swipeRefresh = findViewById(R.id.stations_refresh);
        swipeRefresh.setOnRefreshListener(this::requestRefresh);

        locationProvidersView = findViewById(R.id.stations_list_location_providers);

        final Button locationPermissionRequestButton = findViewById(
                R.id.stations_location_permission_request_button);
        locationPermissionRequestButton.setOnClickListener(v -> requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION));

        final Button locationSettingsButton = findViewById(R.id.stations_list_location_settings);
        locationSettingsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));

        final OnClickListener selectNetworkListener = v -> NetworkPickerActivity.start(StationsActivity.this);
        final Button networkSettingsButton = findViewById(R.id.stations_list_empty_network_settings);
        networkSettingsButton.setOnClickListener(selectNetworkListener);
        final Button missingCapabilityButton = findViewById(R.id.stations_network_missing_capability_button);
        missingCapabilityButton.setOnClickListener(selectNetworkListener);

        viewLocation = findViewById(R.id.stations_location);
        setupLocationViews();

        getMapView().setStationsAware(this);
        getMapView().setDeviceLocationAware(this);
        getMapView().setStationsOverlay(viewLocation);

        connectivityWarningView = findViewById(R.id.stations_connectivity_warning_box);
        final View disclaimerView = findViewById(R.id.stations_disclaimer_group);
        ViewCompat.setOnApplyWindowInsetsListener(disclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
        disclaimerSourceView = findViewById(R.id.stations_disclaimer_source);

        // initialize stations list
        maxDeparturesPerStation = res.getInteger(R.integer.max_departures_per_station);

        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

                    private float lastDX = 0;
                    private int lastDirection = 0;

                    private final Drawable drawableStar;
                    private final Drawable drawableClear;
                    private final Drawable drawableBlock;
                    private final int starMargin;
                    private final int actionTriggerThreshold;

                    {
                        final Resources resources = getResources();
                        drawableStar = resources.getDrawable(R.drawable.ic_star_border_black_24dp);
                        drawableClear = resources.getDrawable(R.drawable.ic_clear_black_24dp);
                        drawableBlock = resources.getDrawable(R.drawable.ic_block_black_24dp);

                        final int fgColor = resources.getColor(R.color.fg_significant);
                        drawableStar.setTint(fgColor);
                        drawableClear.setTint(fgColor);
                        drawableBlock.setTint(fgColor);
                        starMargin = resources.getDimensionPixelOffset(R.dimen.text_padding_horizontal_lax);
                        actionTriggerThreshold = starMargin * 2 + Ints.max(
                                drawableStar.getIntrinsicWidth(),
                                drawableClear.getIntrinsicWidth(),
                                drawableBlock.getIntrinsicWidth());
                    }

                    @Override
                    public boolean isItemViewSwipeEnabled() {
                        return true;
                    }

                    @Override
                    public float getSwipeEscapeVelocity(final float defaultValue) {
                        return Float.MAX_VALUE; // disable swipe by flinging
                    }

                    @Override
                    public float getSwipeThreshold(final RecyclerView.ViewHolder viewHolder) {
                        return Float.MAX_VALUE; // disable swipe by dragging
                    }

                    @Override
                    public void onChildDraw(final Canvas c, final RecyclerView recyclerView,
                            final RecyclerView.ViewHolder viewHolder, float dX, final float dY, final int actionState,
                            final boolean isCurrentlyActive) {
                        final int adapterPosition = viewHolder.getAdapterPosition();
                        if (adapterPosition == RecyclerView.NO_POSITION)
                            return;
                        final Station station = stationListAdapter.getItem(adapterPosition);
                        final Integer favState = favorites.get(station.location.id);
                        final Drawable drawable;
                        if (favState != null)
                            drawable = drawableClear;
                        else if (dX > 0)
                            drawable = drawableStar;
                        else
                            drawable = drawableBlock;

                        final int drawableHeight = drawable.getIntrinsicHeight();
                        final int drawableWidth = drawable.getIntrinsicWidth();
                        final int drawableTop = viewHolder.itemView.getTop() + viewHolder.itemView.getHeight() / 2
                                - drawableHeight / 2;
                        if (dX > 0 && (favState == null || favState == FavoriteStationsProvider.TYPE_IGNORE)) {
                            // drag right
                            if (dX > actionTriggerThreshold) {
                                dX = actionTriggerThreshold;
                                if (isCurrentlyActive) {
                                    drawable.setBounds(starMargin, drawableTop, starMargin + drawableWidth,
                                            drawableTop + drawableHeight);
                                    drawable.draw(c);
                                }
                            }
                        } else if (dX < 0 && (favState == null || favState == FavoriteStationsProvider.TYPE_FAVORITE)) {
                            // drag left
                            if (dX < -actionTriggerThreshold) {
                                dX = -actionTriggerThreshold;
                                if (isCurrentlyActive) {
                                    final int right = viewHolder.itemView.getWidth() - starMargin;
                                    drawable.setBounds(right - drawableWidth, drawableTop, right,
                                            drawableTop + drawableHeight);
                                    drawable.draw(c);
                                }
                            }
                        } else {
                            dX = 0;
                        }

                        lastDX = dX;
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                        if (dX == 0 && lastDirection != 0) {
                            onAction(adapterPosition, lastDirection);
                            lastDirection = 0;
                        }
                    }

                    @Override
                    public void onSelectedChanged(final RecyclerView.ViewHolder viewHolder, final int actionState) {
                        super.onSelectedChanged(viewHolder, actionState);

                        if (actionState == ItemTouchHelper.ACTION_STATE_IDLE
                                && Math.abs(lastDX) >= actionTriggerThreshold)
                            lastDirection = lastDX > 0 ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT;
                    }

                    private void onAction(final int adapterPosition, final int direction) {
                        final Station station = stationListAdapter.getItem(adapterPosition);
                        final Location location = station.location;
                        final Integer favState = favorites.get(location.id);
                        if (direction == ItemTouchHelper.RIGHT && favState == null)
                            addFavorite(location);
                        else if (direction == ItemTouchHelper.RIGHT && favState == FavoriteStationsProvider.TYPE_IGNORE)
                            removeIgnore(location);
                        else if (direction == ItemTouchHelper.LEFT && favState == null)
                            addIgnore(location);
                        else if (direction == ItemTouchHelper.LEFT
                                && favState == FavoriteStationsProvider.TYPE_FAVORITE)
                            removeFavorite(location);
                        stationListAdapter.notifyItemChanged(adapterPosition);
                    }

                    @Override
                    public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int direction) {
                        throw new IllegalStateException();
                    }

                    @Override
                    public boolean onMove(final RecyclerView recyclerView, final RecyclerView.ViewHolder viewHolder,
                            final RecyclerView.ViewHolder target) {
                        throw new IllegalStateException();
                    }
                });
        stationList = findViewById(R.id.stations_list);
        stationListLayoutManager = new LinearLayoutManager(this) {
            // override the layout manger, so that scrolling to the top of an item is always preferred
            @Override
            public void smoothScrollToPosition(final RecyclerView recyclerView, final RecyclerView.State state, final int position) {
                // this is like the method from super.smoothScrollToPosition() ...
                final LinearSmoothScroller linearSmoothScroller =
                        new LinearSmoothScroller(recyclerView.getContext()) {
                            @Override
                            protected int getVerticalSnapPreference() {
                                // ... but with this override
                                return SNAP_TO_START;
                            }
                        };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        };
        stationList.setLayoutManager(stationListLayoutManager);
        // stationList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        stationListAdapter = new StationsAdapter(this, maxDeparturesPerStation, products,
                this, this, this);
        stationList.setAdapter(stationListAdapter);
        ViewCompat.setOnApplyWindowInsetsListener(stationList, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    insets.bottom + (int) (48 * res.getDisplayMetrics().density));
            return windowInsets;
        });
        stationList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(final RecyclerView recyclerView, final int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE)
                    postLoadNextVisible(0);
            }
        });
        itemTouchHelper.attachToRecyclerView(stationList);

        connectivityReceiver = new ConnectivityBroadcastReceiver(connectivityManager) {
            @Override
            protected void onConnected() {
                connectivityWarningView.setVisibility(View.GONE);
                postLoadNextVisible(0);
            }

            @Override
            protected void onDisconnected() {
                connectivityWarningView.setVisibility(View.VISIBLE);
            }
        };
        registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        products.clear();
        products.addAll(loadProductFilter());

        final Intent intent = getIntent();
        handleIntent(intent);
        if (intent.getBooleanExtra(INTENT_EXTRA_OPEN_FAVORITES, false)) {
            FavoriteStationsActivity.start(this);
        }

        updateGUI();
    }

    private void startBackgroundHandler() {
        if (backgroundThread == null) {
            backgroundThread = new HandlerThread("queryDeparturesThread", Process.THREAD_PRIORITY_BACKGROUND);
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startBackgroundHandler();

        if (network != null && NetworkProviderFactory.provider(network).hasCapabilities(Capability.DEPARTURES)) {
            startLocationProvider();

            // request update on orientation change
            sensorManager.registerListener(orientationListener, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(orientationListener, sensorMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);

            // regular refresh
            tickReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(final Context context, final Intent intent) {
                    postLoadNextVisible(0);
                }
            };
            registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        }

        setActionBarSecondaryTitleFromNetwork();
        updateGUI();
    }

    @Override
    protected void onResume() {
        super.onResume();

        postLoadNextVisible(0);
    }

    private void requestRefresh() {
        for (final Station station : stations)
            station.requestedAt = null;
        handler.post(initStationsRunnable);
    }

    private void resetContent() {
        setListFilter(null);

        stations.clear();
        stationsMap.clear();
        products.clear();
        products.addAll(loadProductFilter());

        stationListAdapter.setBaseTime(null);
        stationListAdapter.notifyDataSetChanged();
        getMapView().invalidate();
        loading = true;
    }

    @Override
    protected void onChangeNetwork(final NetworkId network) {
        setupLocationViews();

        resetContent();

        updateDisclaimerSource(disclaimerSourceView, network, null);
        updateGUI();
        setActionBarSecondaryTitleFromNetwork();

        handler.removeCallbacksAndMessages(null);
        handler.post(initStationsRunnable);
    }

    protected void updateFragments() {
        updateFragments(R.id.stations_content_layout);
    }

    @Override
    protected void onPause() {
        saveProductFilter(products);

        super.onPause();
    }

    @Override
    protected void onStop() {
        // cancel refresh
        if (tickReceiver != null) {
            unregisterReceiver(tickReceiver);
            tickReceiver = null;
        }

        stopLocationProvider();

        // cancel update on orientation change
        sensorManager.unregisterListener(orientationListener);

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(connectivityReceiver);

        stations.clear();
        stationsMap.clear();

        stationList.clearOnScrollListeners();

        handler.removeCallbacksAndMessages(null);

        // cancel background thread
        if (backgroundThread != null) {
            final Looper looper = backgroundThread.getLooper();
            if (looper != null)
                looper.quit();
        }

        super.onDestroy();
    }

    @Override
    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        resetContent();

        final String query = intent.getStringExtra(SearchManager.QUERY);
        if (query != null)
            setListFilter(query.trim());

        Command command = null;
        final String intentAction = intent.getAction();
        final Uri intentUri = intent.getData();
        final String intentExtraText = intent.getStringExtra(Intent.EXTRA_TEXT);
        final Location presetLocation = (Location) intent.getSerializableExtra(INTENT_EXTRA_LOCATION);
        presetTime = (Date) intent.getSerializableExtra(INTENT_EXTRA_TIME);
        stationListAdapter.setBaseTime(presetTime);

        final String networkName = intent.getStringExtra(INTENT_EXTRA_NETWORK);
        if (networkName != null)
            this.network = NetworkId.valueOf(networkName);

        if (presetLocation != null) {
            setFixedLocation(presetLocation);
        }

        if (Intent.ACTION_SEND.equals(intentAction) && intentExtraText != null
                && intentExtraText.startsWith(GoogleMapsUtils.GMAPS_SHORT_LOCATION_URL_PREFIX)) {
            // location shared from Google Maps app
            startBackgroundHandler();
            fixedLocationResolving = true;
            backgroundHandler.post(() -> {
                final Location location = GoogleMapsUtils.resolveLocationUrl(intentExtraText);
                runOnUiThread(() -> {
                    setFixedLocation(location);
                    updateGUI();
                });
            });
        } else if (intentUri != null) {
            final Location[] locations = LocationUriParser.parseLocations(intentUri.toString());
            setFixedLocation(locations != null && locations.length >= 1 ? locations[0] : null);
        } else {
            command = (Command) intent.getSerializableExtra(INTENT_EXTRA_COMMAND);
        }

        if (command != null) {
            startBackgroundHandler();
            final AutoCompleteLocationsHandler autoCompleteLocationsHandler = new AutoCompleteLocationsHandler(
                    this, network, backgroundHandler,
                    getNetworkDefaultProducts());
            autoCompleteLocationsHandler.addJob(command.atText, null);
            final Date time = command.time == null ? null : command.time.date();
            autoCompleteLocationsHandler.start(result -> {
                if (!result.success) return;
                StationDetailsActivity.start(this, network, result.location, time, null);
            });
        }
    }

    private void setFixedLocation(final Location location) {
        viewLocation.setLocation(location);
    }

    private void onFixedLocationSet() {
        fixedLocationResolving = false;

        if (fixedLocation != null && fixedLocation.hasCoord())
            getMapView().animateToLocation(fixedLocation.getLatAsDouble(), fixedLocation.getLonAsDouble());

        viewLocation.setVisibility(View.VISIBLE);
        // findViewById(R.id.stations_location_clear).setOnClickListener(v -> setToDeviceLocation());

        handler.post(initStationsRunnable);
    }

    private void setToDeviceLocation() {
        viewLocation.setVisibility(View.GONE);
        fixedLocation = null;
        final boolean hadPresetTime = presetTime != null;
        presetTime = null;
        stationListAdapter.setBaseTime(presetTime);

        if (deviceLocation != null) {
            getMapView().animateToLocation(deviceLocation.getLatAsDouble(), deviceLocation.getLonAsDouble());

            // remove non-favorites and re-calculate distances
            for (final Iterator<Station> i = stations.iterator(); i.hasNext(); ) {
                final Station station = i.next();

                final Integer favState = favorites.get(station.location.id);
                if (favState == null || favState != FavoriteStationsProvider.TYPE_FAVORITE) {
                    i.remove();
                    stationsMap.remove(station.location.id);
                } else {
                    if (hadPresetTime)
                        station.setDepartures(null);

                    if (station.location.hasCoord())
                        station.setDistanceAndBearing(GeoUtils.distanceBetween(deviceLocation, station.location.coord));
                }
            }
        }

        stationListAdapter.notifyDataSetChanged();
        handler.post(initStationsRunnable);
        updateGUI();
    }

    @Override
    public void onBackPressedEvent() {
        if (isNavigationOpen())
            closeNavigation();
        else if (searchQuery != null || filterQuery != null)
            setListFilter(null);
        else
            super.onBackPressedEvent();
    }

    private void setListFilter(final String filter) {
        filterByText = filter;

        final boolean searchQueryModified;
        if (DO_FILTER_BY_SEARCH_ON_NETWORK) {
            searchQuery = filterByText;
            searchQueryModified = true;
            filterQuery = null;
        } else {
            filterQuery = filterByText == null ? null : KeyWordMatcher.createQuery(filterByText);
            searchQueryModified = searchQuery != null;
            searchQuery = null;
        }

        if (searchQueryModified) {
            stations.clear();
            stationsMap.clear();
        }

        stationListAdapter.setShowPlaces(searchQuery != null);
        stationListAdapter.setFilterQuery(filterQuery);
        stationListAdapter.notifyDataSetChanged();

        if (searchQueryModified) {
            handler.post(initStationsRunnable);
        }

        updateGUI();
    }

    private void updateGUI() {
        // fragments
        updateFragments();

        // filter indicator
        filterActionButton.setSelected(!productsAreNetworkDefault(products));

        final ViewAnimator viewAnimator = findViewById(R.id.stations_list_layout);
        if (network == null || !NetworkProviderFactory.provider(network).hasCapabilities(Capability.DEPARTURES)) {
            viewAnimator.setDisplayedChild(1); // Missing capability
        } else if (searchQuery == null && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            viewAnimator.setDisplayedChild(2); // Location permission denied
        } else if (searchQuery == null && loading && deviceLocation == null && !anyProviderEnabled) {
            viewAnimator.setDisplayedChild(3); // Location providers disabled
        } else if (searchQuery == null && loading && deviceLocation == null && anyProviderEnabled) {
            viewAnimator.setDisplayedChild(4); // Acquiring location
        } else if (stations.isEmpty() && searchQuery == null && loading && deviceLocation != null) {
            viewAnimator.setDisplayedChild(5); // Querying nearby stations
        } else if (stations.isEmpty() && searchQuery != null && loading) {
            viewAnimator.setDisplayedChild(6); // Matching stations
        } else if (stations.isEmpty() && searchQuery == null) {
            viewAnimator.setDisplayedChild(7); // List empty, no nearby stations
        } else if (stations.isEmpty() && searchQuery != null) {
            viewAnimator.setDisplayedChild(8); // List empty, no query match
        } else {
            viewAnimator.setDisplayedChild(0); // Stations list
        }

        // location box
//        findViewById(R.id.stations_location_box).setVisibility(fixedLocation != null ? View.VISIBLE : View.GONE);
//        if (fixedLocation != null) {
//            final String locationName = fixedLocation.name != null ? fixedLocation.uniqueShortName()
//                    : String.format(Locale.ENGLISH, "%.6f, %.6f", fixedLocation.getLatAsDouble(), fixedLocation.getLonAsDouble());
//            final String text = presetTime == null ? locationName
//                    : String.format("%s @ %s", locationName, Formats.formatTime(timeZoneSelector, presetTime, PTDate.NETWORK_OFFSET));
//            ((TextView) findViewById(R.id.stations_location_text)).setText(text);
//        }

        // search box
        if (DO_FILTER_BY_SEARCH_ON_NETWORK) {
            findViewById(R.id.stations_search_box).setVisibility(filterByText != null ? View.VISIBLE : View.GONE);
            if (filterByText != null)
                ((TextView) findViewById(R.id.stations_search_text)).setText(filterByText);
        }
    }

    private boolean addFavorite(final Location location) {
        final Uri rowUri = FavoriteUtils.persist(getContentResolver(), FavoriteStationsProvider.TYPE_FAVORITE, network,
                location);
        if (rowUri != null) {
            favorites.put(location.id, FavoriteStationsProvider.TYPE_FAVORITE);
            postLoadNextVisible(0);
            NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
            return true;
        } else {
            return false;
        }
    }

    private boolean removeFavorite(final Location location) {
        final int numRows = FavoriteUtils.delete(getContentResolver(), network, location.id);
        if (numRows > 0) {
            favorites.remove(location.id);
            NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
            return true;
        } else {
            return false;
        }
    }

    private boolean addIgnore(final Location location) {
        final Uri rowUriIgnored = FavoriteUtils.persist(getContentResolver(), FavoriteStationsProvider.TYPE_IGNORE,
                network, location);
        if (rowUriIgnored != null) {
            favorites.put(location.id, FavoriteStationsProvider.TYPE_IGNORE);
            NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
            return true;
        } else {
            return false;
        }
    }

    private boolean removeIgnore(final Location location) {
        final int numRowsIgnored = FavoriteUtils.delete(getContentResolver(), network, location.id);
        if (numRowsIgnored > 0) {
            favorites.remove(location.id);
            postLoadNextVisible(0);
            NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        switch (id) {
            case DIALOG_NEARBY_STATIONS_ERROR:
                final DialogBuilder builder = DialogBuilder.warn(this, R.string.stations_nearby_stations_error_title);
                builder.setMessage(getString(R.string.stations_nearby_stations_error_message));
                builder.setPositiveButton(getString(R.string.stations_nearby_stations_error_continue),
                        (dialog, _id) -> dialog.dismiss());
                builder.setNegativeButton(getString(R.string.stations_nearby_stations_error_exit),
                        (dialog, _id) -> {
                            dialog.dismiss();
                            finish();
                        });
                return builder.create();
            default:
                return super.onCreateDialog(id);
        }
    }

    private final Runnable initStationsRunnable = new Runnable() {
        public void run() {
            if (network != null) {
                if (searchQuery == null)
                    runNearbyQuery();
                else
                    runSearchQuery();
            }
        }

        private void runNearbyQuery() {
            final Location referenceLocation = getReferenceLocation();

            if (referenceLocation != null) {
                final MyActionBar actionBar = getMyActionBar();

                final StringBuilder favoriteIds = new StringBuilder();
                for (final Map.Entry<String, Integer> entry : favorites.entrySet())
                    if (entry.getValue() == FavoriteStationsProvider.TYPE_FAVORITE)
                        favoriteIds.append(entry.getKey()).append(',');
                if (favoriteIds.length() != 0)
                    favoriteIds.setLength(favoriteIds.length() - 1);

                backgroundHandler.post(() -> {
                    runOnUiThread(() -> {
                        actionBar.startProgress();
                        // swipeRefresh.setRefreshing(true);
                        loading = true;
                        updateGUI();
                    });

                    final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
                    try {
                        final NearbyLocationsResult result =
                                networkProvider.queryNearbyLocations(EnumSet.of(LocationType.STATION),
                                referenceLocation, 0, 0);
                        if (result.status == NearbyLocationsResult.Status.OK) {
                            log.info("Got {}", result.toShortString());

                            final List<Station> freshStations = new ArrayList<>(result.locations.size());

                            for (final Location location : result.locations) {
                                if (location.type == LocationType.STATION) {
                                    final Station station = new Station(network, location);
                                    if (deviceLocation != null)
                                        station.setDistanceAndBearing(GeoUtils.distanceBetween(referenceLocation.coord, location.coord));
                                    freshStations.add(station);
                                }
                            }

                            runOnUiThread(() -> mergeIntoStations(freshStations, true));
                        }
                    } catch (final IOException x) {
                        log.info("IO problem while querying for nearby locations to " + referenceLocation, x);
                    } finally {
                        runOnUiThread(() -> {
                            swipeRefresh.setRefreshing(false);
                            actionBar.stopProgress();
                            loading = false;
                            updateGUI();
                        });
                    }
                });
            }

            // (do not, why?) refresh favorites
            // addFavoriteStationsToResult();
        }

        private void addFavoriteStationsToResult() {
            final Map<Location, Integer> favoriteMap = FavoriteUtils.loadAll(getContentResolver(), network);
            final List<Station> freshStations = new ArrayList<>(favoriteMap.size());

            final float[] distanceBetweenResults = new float[2];

            for (final Map.Entry<Location, Integer> entry : favoriteMap.entrySet()) {
                final Location location = entry.getKey();
                final String stationId = location.id;
                final int favType = entry.getValue();
                favorites.put(stationId, favType);

                if (favType == FavoriteStationsProvider.TYPE_FAVORITE) {
                    final Station station = new Station(network, location);
                    if (deviceLocation != null && location.hasCoord())
                        station.setDistanceAndBearing(GeoUtils.distanceBetween(deviceLocation, location.coord));
                    freshStations.add(station);
                }
            }
            mergeIntoStations(freshStations, false);
        }

        private void runSearchQuery() {
            loading = true;

            new SearchTask() {
                @Override
                protected void onPostExecute(final List<Station> freshStations) {
                    final Location referenceLocation = getReferenceLocation();
                    if (referenceLocation != null) {
                        for (final Station freshStation : freshStations) {
                            if (freshStation.location.hasCoord())
                                freshStation.setDistanceAndBearing(GeoUtils.distanceBetween(referenceLocation.coord, freshStation.location.coord));
                        }
                    }

                    loading = false;
                    mergeIntoStations(freshStations, true);
                }
            }.execute(searchQuery);
        }
    };

    private void mergeIntoStations(final List<Station> freshStations, final boolean updateExisting) {
        boolean added = false;
        boolean changed = false;

        for (final Station freshStation : freshStations) {
            final Station station = stationsMap.get(freshStation.location.id);
            if (station != null) {
                if (updateExisting) {
                    if (freshStation.location != null) {
                        station.location = freshStation.location;
                        changed = true;
                    }
                    if (freshStation.hasDistanceAndBearing) {
                        station.setDistanceAndBearing(freshStation.distance, freshStation.bearing);
                        changed = true;
                    }
                    if (freshStation.getDepartures() != null) {
                        station.setDepartures(freshStation.getDepartures());
                        changed = true;
                    }
                    if (freshStation.getLines() != null) {
                        station.setLines(freshStation.getLines());
                        changed = true;
                    }
                }
            } else if (filter(freshStation, products)) {
                stations.add(freshStation);
                stationsMap.put(freshStation.location.id, freshStation);

                added = true;
                changed = true;
            }
        }

        if (changed) {
            // need to sort again
            sortStations(stations);
        }

        if (added) {
            // clip list at end, retaining favorites
            int stationToRemove = stations.size() - 1;
            while (stations.size() > Constants.MAX_NUMBER_OF_STOPS && stationToRemove >= 0) {
                final Integer favState = favorites.get(stations.get(stationToRemove).location.id);
                if (favState == null || favState != FavoriteStationsProvider.TYPE_FAVORITE)
                    // remove from list & map at once
                    stationsMap.remove(stations.remove(stationToRemove).location.id);

                stationToRemove--;
            }

            postLoadNextVisible(100); // List needs time to initialize.
        }

        if (added || changed) {
            stationListAdapter.notifyDataSetChanged();
            getMapView().invalidate();
        }

        if (added) {
            handler.postDelayed(() -> getMapView().zoomToStations(
                    stations.stream().map(station -> station.location).collect(Collectors.toList())),
                    500);
        }

        updateGUI();
    }

    private static boolean filter(final Station station, final Collection<Product> productFilter) {
        // if station has products declared, use that for matching
        final Set<Product> products = station.location.products;
        if (products != null) {
            if (products.isEmpty())
                return false;
            final Set<Product> copy = EnumSet.copyOf(products);
            copy.retainAll(productFilter);
            return !copy.isEmpty();
        }

        // if station has lines, go through them and try to match each
        final List<LineDestination> lines = station.getLines();
        if (lines != null) {
            for (final LineDestination line : lines) {
                final Product product = line.line.product;
                if (product != null)
                    for (final Product filterProduct : productFilter)
                        if (product == filterProduct)
                            return true;
            }
        }

        // special case: if station has no metadata suitable for product filtering, match always
        if (products == null && lines == null)
            return true;

        return false;
    }

    private static void sortStations(final List<Station> stations) {
        Collections.sort(stations, (station1, station2) -> {
            ComparisonChain chain = ComparisonChain.start();

            // order by distance
            chain = chain.compareTrueFirst(station1.hasDistanceAndBearing, station2.hasDistanceAndBearing)
                    .compare(station1.distance, station2.distance);

            // order by lines
            final List<LineDestination> lines1 = station1.getLines();
            final List<LineDestination> lines2 = station2.getLines();
            final List<LineDestination> lineDestinations1 = lines1 != null ? lines1
                    : Collections.emptyList();
            final List<LineDestination> lineDestinations2 = lines2 != null ? lines2
                    : Collections.emptyList();
            final int length1 = lineDestinations1.size();
            final int length2 = lineDestinations2.size();
            final int length = Math.max(length1, length2);

            for (int i = 0; i < length; i++) {
                final Line line1 = i < length1 ? lineDestinations1.get(i).line : null;
                final Line line2 = i < length2 ? lineDestinations2.get(i).line : null;
                chain = chain.compare(line1, line2, Ordering.natural().nullsLast());
            }

            return chain.result();
        });
    }

    private void postLoadNextVisible(final long delay) {
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected())
            return;

        if (delay == 0)
            handler.post(loadVisibleRunnable);
        else
            handler.postDelayed(loadVisibleRunnable, delay);
    }

    private final Runnable loadVisibleRunnable = new Runnable() {
        public void run() {
            final Station station = nextStationToLoad();

            if (station != null) {
                final String requestedStationId = station.location.id;
                if (requestedStationId != null) {
                    station.requestedAt = new Date();

                    final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
                    final int maxDepartures = maxDeparturesPerStation * 2;

                    backgroundHandler.post(
                            new QueryDeparturesRunnable(handler, networkProvider, requestedStationId, presetTime, maxDepartures) {
                                @Override
                                protected void onPreExecute() {
                                    actionBar.startProgress();
                                    // swipeRefresh.setRefreshing(true);
                                }

                                @Override
                                protected void onPostExecute() {
                                    swipeRefresh.setRefreshing(false);
                                    actionBar.stopProgress();
                                }

                                @Override
                                protected void onResult(final QueryDeparturesResult result) {
                                    if (result.header != null)
                                        updateDisclaimerSource(disclaimerSourceView, network,
                                                product(result.header));

                                    if (result.status == QueryDeparturesResult.Status.OK) {
                                        if (!result.stationDepartures.isEmpty()) {
                                            for (final StationDepartures stationDepartures : result.stationDepartures) {
                                                final String stationId = stationDepartures.location.id;
                                                final Station resultStation = stationsMap.get(stationId);
                                                final List<Departure> departures = stationDepartures.getNonCancelledDepartures();
                                                if (resultStation != null && (requestedStationId.equals(stationId)
                                                        || (resultStation.requestedAt == null
                                                                && !departures.isEmpty()))) {
                                                    // Trim departures
                                                    while (departures.size() > maxDepartures)
                                                        departures.remove(departures.size() - 1);

                                                    resultStation.setDepartures(departures);
                                                    resultStation.departureQueryStatus = QueryDeparturesResult.Status.OK;
                                                    resultStation.updatedAt = new Date();
                                                }
                                            }
                                        } else {
                                            // Station is existing but yields no StationDepartures
                                            station.setDepartures(Collections.emptyList());
                                            station.departureQueryStatus = QueryDeparturesResult.Status.OK;
                                            station.updatedAt = new Date();
                                        }

                                        stationListAdapter.notifyDataSetChanged();
                                    } else if (result.status == QueryDeparturesResult.Status.INVALID_STATION) {
                                        final Station resultStation = stationsMap.get(requestedStationId);
                                        if (resultStation != null) {
                                            resultStation.departureQueryStatus = QueryDeparturesResult.Status.INVALID_STATION;
                                            resultStation.updatedAt = new Date();

                                            stationListAdapter.notifyDataSetChanged();
                                        }
                                    } else {
                                        log.info("Got {}", result.toShortString());
                                        new Toast(StationsActivity.this)
                                                .toast(QueryDeparturesRunnable.statusMsgResId(result.status));
                                    }

                                    postLoadNextVisible(0);
                                }

                                @Override
                                protected void onRedirect(final HttpUrl url) {
                                    log.info("Redirect while querying departures on {}", requestedStationId);

                                    handler.post(() -> new Toast(StationsActivity.this).toast(R.string.toast_network_problem));
                                }

                                @Override
                                protected void onBlocked(final HttpUrl url) {
                                    log.info("Blocked querying departures on {}", requestedStationId);

                                    handler.post(() -> new Toast(StationsActivity.this).toast(R.string.toast_network_blocked,
                                            url.host()));
                                }

                                @Override
                                protected void onInternalError(final HttpUrl url) {
                                    log.info("Internal error querying departures on {}", requestedStationId);

                                    handler.post(() -> new Toast(StationsActivity.this).toast(R.string.toast_internal_error,
                                            url.host()));
                                }

                                @Override
                                protected void onParserException(final String message) {
                                    log.info("Cannot parse departures on {}: {}", requestedStationId, message);

                                    handler.post(() -> {
                                        final String limitedMessage = message != null
                                                ? message.substring(0, Math.min(100, message.length())) : null;
                                        new Toast(StationsActivity.this).toast(R.string.toast_invalid_data,
                                                limitedMessage);
                                    });
                                }

                                @Override
                                protected void onInputOutputError(final IOException x) {
                                    handler.post(() -> new Toast(StationsActivity.this).toast(R.string.toast_network_problem));
                                }
                            });
                }
            }
        }

        private Station nextStationToLoad() {
            if (stations.isEmpty())
                return null;

            int firstVisible = stationListLayoutManager.findFirstVisibleItemPosition();
            int lastVisible = stationListLayoutManager.findLastVisibleItemPosition();
            if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION)
                return null;
            if (firstVisible >= stations.size())
                firstVisible = stations.size() - 1;
            if (lastVisible >= stations.size())
                lastVisible = stations.size() - 1;

            final long now = System.currentTimeMillis();

            for (int i = firstVisible; i <= lastVisible; i++) // first load selected
            {
                final Station station = stations.get(i);

                final Date requestedAt = station.requestedAt;
                if ((requestedAt == null || now - requestedAt.getTime() > DateUtils.MINUTE_IN_MILLIS)) {
                    if (selectedStation != null && selectedStation.location.id.equals(station.location.id))
                        return station;
                }
            }

            for (int i = firstVisible; i <= lastVisible; i++) // then load favorites
            {
                final Station station = stations.get(i);

                final Date requestedAt = station.requestedAt;
                if ((requestedAt == null || now - requestedAt.getTime() > DateUtils.MINUTE_IN_MILLIS)) {
                    final Integer favState = favorites.get(station.location.id);
                    if (favState != null && favState == FavoriteStationsProvider.TYPE_FAVORITE)
                        return station;
                }
            }

            for (int i = firstVisible; i <= lastVisible; i++) // then load others
            {
                final Station station = stations.get(i);

                if (station.requestedAt == null) {
                    final Integer favState = favorites.get(station.location.id);
                    if (favState == null)
                        return station;
                }
            }

            return null;
        }
    };

    public final List<Station> getStations() {
        return stations;
    }

    public final Integer getFavoriteState(final String stationId) {
        return favorites.get(stationId);
    }

    @Override
    protected void onMapSetVisible() {
        selectStation(selectedStation);
    }

    public final void selectStation(final Station station) {
        selectedStation = station;
        stationListAdapter.notifyDataSetChanged();

        // scroll list into view
        for (int position = 0; position < stations.size(); position++) {
            if (stations.get(position).equals(station)) {
                stationList.smoothScrollToPosition(position);
                break;
            }
        }

        // scroll map
        if (station != null && station.location.hasCoord())
            getMapView().zoomToStations(List.of(station.location));
        else if (!stations.isEmpty())
            getMapView().zoomToStations(stations.stream().map(s -> s.location).collect(Collectors.toList()));
        else if (station == null && deviceLocation != null)
            getMapView().animateToLocation(deviceLocation.getLatAsDouble(), deviceLocation.getLonAsDouble());

        postLoadNextVisible(0);
    }

    public final boolean isSelectedStation(final String stationId) {
        return selectedStation != null && stationId != null && stationId.equals(selectedStation.location.id);
    }

    public final Point getDeviceLocation() {
        return deviceLocation;
    }

    public final Location getReferenceLocation() {
        if (fixedLocationResolving)
            return null;
        if (fixedLocation != null)
            return fixedLocation;
        if (deviceLocation != null)
            return Location.coord(deviceLocation);
        return null;
    }

    public final Float getDeviceBearing() {
        if (fixedLocationResolving || fixedLocation != null)
            return null;
        return deviceBearing;
    }

    private void startLocationProvider() {
        // determine location providers
        final Criteria accurateCriteria = new Criteria();
        accurateCriteria.setAccuracy(Criteria.ACCURACY_FINE);
        accurateLocationProvider = locationManager.getBestProvider(accurateCriteria, true);
        final Criteria lowPowerCriteria = new Criteria();
        lowPowerCriteria.setPowerRequirement(Criteria.POWER_LOW);
        lowPowerLocationProvider = locationManager.getBestProvider(lowPowerCriteria, true);

        if (accurateLocationProvider != null && accurateLocationProvider.equals(lowPowerLocationProvider))
            accurateLocationProvider = null;

        // request update on location change
        if (accurateLocationProvider != null)
            locationManager.requestLocationUpdates(accurateLocationProvider, Constants.LOCATION_UPDATE_FREQ_MS,
                    Constants.LOCATION_UPDATE_DISTANCE, locationListener);
        if (lowPowerLocationProvider != null)
            locationManager.requestLocationUpdates(lowPowerLocationProvider, Constants.LOCATION_UPDATE_FREQ_MS,
                    Constants.LOCATION_UPDATE_DISTANCE, locationListener);

        // last known location
        final android.location.Location here = determineLastKnownLocation();
        if (here != null)
            locationListener.onLocationChanged(here);

        // display state of location providers
        locationProvidersView.removeAllViews();
        anyProviderEnabled = false;
        for (final String provider : locationManager.getAllProviders()) {
            if (provider.equals(LocationManager.PASSIVE_PROVIDER))
                continue;

            final boolean enabled = locationManager.isProviderEnabled(provider);
            final boolean acquiring = provider.equals(accurateLocationProvider)
                    || provider.equals(lowPowerLocationProvider);
            final View row = getLayoutInflater().inflate(R.layout.stations_location_provider_row, null);
            ((TextView) row.findViewById(R.id.stations_location_provider_row_provider)).setText(provider + ":");
            final TextView enabledView = row.findViewById(R.id.stations_location_provider_row_enabled);
            enabledView.setText(enabled
                    ? (acquiring ? R.string.stations_location_provider_acquiring
                            : R.string.stations_location_provider_enabled)
                    : R.string.stations_location_provider_disabled);
            enabledView.setTypeface(acquiring ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            locationProvidersView.addView(row);

            if (enabled)
                anyProviderEnabled = true;
        }
    }

    private void stopLocationProvider() {
        locationManager.removeUpdates(locationListener);
    }

    private android.location.Location determineLastKnownLocation() {
        android.location.Location accurateLocation = null;
        if (accurateLocationProvider != null) {
            accurateLocation = locationManager.getLastKnownLocation(accurateLocationProvider);
            if (accurateLocation != null
                    && (accurateLocation.getLatitude() == 0 && accurateLocation.getLongitude() == 0))
                accurateLocation = null;
        }

        android.location.Location lowPowerLocation = null;
        if (lowPowerLocationProvider != null) {
            lowPowerLocation = locationManager.getLastKnownLocation(lowPowerLocationProvider);
            if (lowPowerLocation != null
                    && (lowPowerLocation.getLatitude() == 0 && lowPowerLocation.getLongitude() == 0))
                lowPowerLocation = null;
        }

        if (lowPowerLocation != null || accurateLocation != null) {
            final long accurateLocationTime = accurateLocation != null ? accurateLocation.getTime() : -1;
            final long lowPowerLocationTime = lowPowerLocation != null ? lowPowerLocation.getTime() : -1;
            final android.location.Location location = lowPowerLocationTime > accurateLocationTime ? lowPowerLocation
                    : accurateLocation;
            if (location != null)
                return location;
        }

        return null;
    }

    public boolean onStationContextMenuItemClick(final int adapterPosition, final NetworkId network,
            final Location station, final @Nullable List<Departure> departures, final int menuItemId) {
        if (menuItemId == R.id.station_context_add_favorite) {
            if (StationsActivity.this.addFavorite(station))
                stationListAdapter.notifyItemChanged(adapterPosition);
            return true;
        } else if (menuItemId == R.id.station_context_remove_favorite) {
            if (StationsActivity.this.removeFavorite(station))
                stationListAdapter.notifyItemChanged(adapterPosition);
            return true;
        } else if (menuItemId == R.id.station_context_add_ignore) {
            if (StationsActivity.this.addIgnore(station))
                stationListAdapter.notifyItemChanged(adapterPosition);
            return true;
        } else if (menuItemId == R.id.station_context_remove_ignore) {
            if (StationsActivity.this.removeIgnore(station))
                stationListAdapter.notifyItemChanged(adapterPosition);
            return true;
        } else if (menuItemId == R.id.station_context_show_departures) {
            StationDetailsActivity.start(StationsActivity.this, network, station, presetTime, departures);
            return true;
        } else if (menuItemId == R.id.station_context_directions_from) {
            DirectionsActivity.start(StationsActivity.this,
                    station, null, null, null, null, false,
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return true;
        } else if (menuItemId == R.id.station_context_directions_to) {
            DirectionsActivity.start(StationsActivity.this,
                    null, station, null, null, null, false,
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return true;
        } else if (menuItemId == R.id.station_context_launcher_shortcut) {
            StationContextMenu.createLauncherShortcutDialog(StationsActivity.this, network, station).show();
            return true;
        } else if (menuItemId == R.id.station_context_infopage) {
            final String infoUrl = station.infoUrl;
            if (infoUrl != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(infoUrl)));
            }
            return true;
        } else if (menuItemId == R.id.station_map_context_maps_internal) {
            setMapVisible(true);
            if (station != null && station.hasCoord())
                getMapView().zoomToStations(List.of(station));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onJourneyClick(final View clickedView, final JourneyRef journeyRef, final Location entryLocation) {
        queryJourneyRunnable = QueryJourneyRunnable.startShowJourney(
                this, clickedView, queryJourneyRunnable,
                handler, backgroundHandler,
                network, journeyRef, entryLocation, null);
    }

    private final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(final android.location.Location here) {
            log.info("Got relevant location: {}", here);

            deviceLocation = Point.fromDouble(here.getLatitude(), here.getLongitude());
            stationListAdapter.setDeviceLocation(here);

            // re-calculate distances for sorting
            if (fixedLocation == null) {
                for (final Station station : stations) {
                    if (station.location.hasCoord())
                        station.setDistanceAndBearing(GeoUtils.distanceBetween(here, station.location.coord));
                }

                stationListAdapter.notifyDataSetChanged();

                handler.post(initStationsRunnable);
            }

            updateGUI();
        }

        public void onStatusChanged(final String provider, final int status, final Bundle extras) {
        }

        public void onProviderEnabled(final String provider) {
        }

        public void onProviderDisabled(final String provider) {
        }
    };

    private final SensorEventListener orientationListener = new SensorEventListener() {
        private final float[] accelerometerValues = new float[3];
        private final float[] magnetometerValues = new float[3];

        private final float accelerometerFactor = 0.2f;
        private final float accelerometerCofactor = 1f - accelerometerFactor;

        private float[] rotationMatrix = new float[9];
        private float[] orientation = new float[3];

        private long lastTime = 0;

        public void onSensorChanged(final SensorEvent event) {
            if (event.sensor == sensorAccelerometer) {
                accelerometerValues[0] = event.values[0] * accelerometerFactor
                        + accelerometerValues[0] * accelerometerCofactor;
                accelerometerValues[1] = event.values[1] * accelerometerFactor
                        + accelerometerValues[1] * accelerometerCofactor;
                accelerometerValues[2] = event.values[2] * accelerometerFactor
                        + accelerometerValues[2] * accelerometerCofactor;
            } else if (event.sensor == sensorMagnetometer) {
                System.arraycopy(event.values, 0, magnetometerValues, 0, event.values.length);
            }

            if (System.currentTimeMillis() - lastTime < 50)
                return;

            final boolean faceDown = accelerometerValues[2] < 0;

            final boolean success = SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValues,
                    magnetometerValues);
            if (!success)
                return;

            SensorManager.getOrientation(rotationMatrix, orientation);
            final float azimuth = (float) Math.toDegrees(orientation[0]);

            lastTime = System.currentTimeMillis();

            runOnUiThread(() -> {
                deviceBearing = azimuth;
                stationListAdapter.setDeviceBearing(azimuth, faceDown);

                // refresh compass needles
                final int childCount = stationList.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final View childAt = stationList.getChildAt(i);
                    final View bearingView = childAt.findViewById(R.id.station_entry_bearing);
                    if (bearingView != null)
                        bearingView.invalidate();
                }
            });
        }

        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        }
    };

    private class SearchTask extends AsyncTask<String, Void, List<Station>> {
        @Override
        protected List<Station> doInBackground(final String... params) {
            if (params.length != 1)
                throw new IllegalArgumentException();

            final String query = params[0];

            final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);

            final List<Station> stations = new LinkedList<>();
            try {
                final SuggestLocationsResult result = networkProvider.suggestLocations(query,
                        EnumSet.of(LocationType.STATION), 0);
                if (result.status == SuggestLocationsResult.Status.OK) {
                    log.info("Got {}", result.toShortString());
                    for (final Location l : result.getLocations())
                        if (l.type == LocationType.STATION)
                            stations.add(new Station(network, l));
                }
            } catch (final IOException x) {
                x.printStackTrace();
            }

            return stations;
        }
    }
}
