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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
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
import android.text.Html;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewAnimator;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.DeviceLocationAware;
import de.schildbach.oeffi.OeffiActionBar;
import de.schildbach.oeffi.OeffiMainActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.directions.QueryJourneyRunnable;
import de.schildbach.oeffi.mapview.OeffiMapView;
import de.schildbach.oeffi.stations.list.JourneysAdapter;
import de.schildbach.oeffi.util.FilterSearchView;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.GeoUtils;
import de.schildbach.oeffi.util.KeyWordMatcher;
import de.schildbach.oeffi.util.LocationUtils;
import de.schildbach.oeffi.util.ResourceUtil;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.list.JourneyClickListener;
import de.schildbach.oeffi.stations.list.StationContextMenuItemListener;
import de.schildbach.oeffi.stations.list.StationsAdapter;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.oeffi.util.locationview.AutoCompleteLocationsHandler;
import de.schildbach.oeffi.util.ConnectivityBroadcastReceiver;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.ExternalMapsUtils;
import de.schildbach.oeffi.util.LocationUriParser;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.locationview.LocationView;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.provider.NetworkProvider.Capability;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyLocationsResult;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;
import de.schildbach.pte.dto.SuggestLocationsResult;
import okhttp3.HttpUrl;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    public static final String PREF_KEY_SORT_BY_WALK = "stations_sort_by_walk";
    public static final String PREF_KEY_WALK_DISTANCE_FACTOR = "walk_distance_factor";

    public static final String INTENT_EXTRA_OPEN_FAVORITES = StationsActivity.class.getName() + ".open_favorites";
    public static final String INTENT_EXTRA_NETWORK = StationsActivity.class.getName() + ".network";
    public static final String INTENT_EXTRA_LOCATION = StationsActivity.class.getName() + ".location";
    public static final String INTENT_EXTRA_TIME = StationsActivity.class.getName() + ".time";
    private static final String INTENT_EXTRA_COMMAND = StationsActivity.class.getName() + ".command";
    private ConnectivityManager connectivityManager;
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
    private Double deviceBearing = null;
    private String filterByText;
    private String searchQuery;
    private KeyWordMatcher.Query filterQuery;
    private boolean sortByWalkAccess;
    private boolean anyProviderEnabled = false;
    private boolean loading = true;
    private boolean queryFailed = false;

    private final Set<Product> products = new HashSet<>(Product.ALL_SELECTABLE);
    private String accurateLocationProvider, lowPowerLocationProvider;

    private OeffiActionBar actionBar;
    private ViewGroup stationsContainer;
    private RecyclerView stationList;
    private LinearLayoutManager stationListLayoutManager;
    private StationsAdapter stationListAdapter;
    private ViewGroup journeysContainer;
    private RecyclerView journeyList;
    private LinearLayoutManager journeyListLayoutManager;
    private JourneysAdapter journeyListAdapter;
    private TextView connectivityWarningView;
    private TextView disclaimerSourceView;
    private View filterActionButton;
    private ViewGroup locationProvidersView;
    private ViewGroup legacySearchBoxView;
    private FilterSearchView newFilterSearchView;
    private EditText searchEditView;
    private LocationView locationView;
    private SwipeRefreshLayout swipeRefresh;

    private QueryJourneyRunnable queryJourneyRunnable;
    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private BroadcastReceiver connectivityReceiver;
    private BroadcastReceiver tickReceiver;

    private boolean expandAllEntries;
    private int maxStationsToLoad;
    private int maxDeparturesPerStationCollapsed, maxDeparturesPerStationExpanded;
    private int maxDeparturesPerJourney;
    private float walkPaceMillisPerMeter;

    public Drawable drawableSwipeStar;
    public Drawable drawableSwipeClear;
    public Drawable drawableSwipeBlock;

    final LocationView.Listener locationViewListener = new LocationView.Listener() {
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
            final Location location = locationView.getLocation();
            if (location != null)
                setLocationByUser(location);
        }

        @Override
        public OeffiMapView.MapPointSelectionProvider getMapPointSelectionProvider() {
            return StationsActivity.this;
        }
    };

    private void setupLocationViews() {
        locationView.setListener(locationViewListener);

        resetLocationViewsBehaviour();
    }

    private void resetLocationViewsBehaviour() {
        locationView.resetBehaviour();
    }

    private static final int DIALOG_NEARBY_STATIONS_ERROR = 1;

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

    public static void start(
            final Context context,
            final NetworkId networkId, final Location location, final Date time,
            final Intent returnToIntent) {
        Intent intent = new Intent(context, StationsActivity.class);
        if (networkId != null)
            intent.putExtra(StationsActivity.INTENT_EXTRA_NETWORK, networkId.name());
        if (location != null)
            intent.putExtra(StationsActivity.INTENT_EXTRA_LOCATION, location);
        if (time != null)
            intent.putExtra(StationsActivity.INTENT_EXTRA_TIME, time);
        intent = addFinishingPendingIntent(context, intent, returnToIntent);
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
        updateWalkSpeed();

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMagnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        res = getResources();
        drawableSwipeStar = ResourceUtil.getDrawableIconInTextColor(this, R.drawable.ic_star_border_black_24dp);
        drawableSwipeClear = ResourceUtil.getDrawableIconInTextColor(this, R.drawable.ic_clear_black_24dp);
        drawableSwipeBlock = ResourceUtil.getDrawableIconInTextColor(this, R.drawable.ic_block_black_24dp);

        setContentView(R.layout.stations_content);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        actionBar = getMyActionBar();
        addNetworkStatisticsDisplay();
        setPrimaryColor(R.color.bg_action_bar_stations);
        actionBar.setPrimaryTitle(R.string.stations_activity_title);
        actionBar.setTitlesOnClickListener(v -> startNetworkPicker());
        actionBar.addProgressButton().setOnClickListener(v -> requestRefresh());
        // actionBar.addButton(R.drawable.ic_star_white_24dp, R.string.stations_options_favorites_title)
        //         .setOnClickListener(view -> FavoriteStationsActivity.start(StationsActivity.this));
        sortByWalkAccess = prefs.getBoolean(PREF_KEY_SORT_BY_WALK, false);
        final ToggleImageButton sortModeButton = actionBar.addToggleButton(R.drawable.ic_walk_on_off_white_24dp, R.string.stations_sort_by_walk_time_title);
        sortModeButton.setChecked(sortByWalkAccess);
        sortModeButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sortByWalkAccess = isChecked;
            prefs.edit().putBoolean(PREF_KEY_SORT_BY_WALK, sortByWalkAccess).apply();
            updateGUI();
        });
        addShowMapButtonToActionBar(true, false);
        actionBar.addButton(R.drawable.ic_search_white_24dp, R.string.stations_action_search_title)
                .setOnClickListener(v -> {
                    if (DO_FILTER_BY_SEARCH_ON_NETWORK) {
                        onSearchRequested();
                    } else {
                        if (legacySearchBoxView.getVisibility() == View.VISIBLE) {
                            final Editable text = searchEditView.getText();
                            if (text == null || text.length() == 0) {
                                ViewUtils.setVisibility(locationView, true);
                                ViewUtils.setVisibility(legacySearchBoxView, false);
                                ViewUtils.setVisibility(newFilterSearchView, false);
                            }
                        } else if (newFilterSearchView.getVisibility() == View.VISIBLE) {
                            final Editable text = newFilterSearchView.getText();
                            if (text == null || text.length() == 0) {
                                ViewUtils.setVisibility(locationView, true);
                                ViewUtils.setVisibility(legacySearchBoxView, false);
                                ViewUtils.setVisibility(newFilterSearchView, false);
                            }
                        } else {
                            ViewUtils.setVisibility(locationView, false);

                            // ViewUtils.setVisibility(legacySearchBoxView, true);
                            ViewUtils.setVisibility(newFilterSearchView, true);
                        }
                    }
                });
        newFilterSearchView = findViewById(R.id.stations_filter_text);
        newFilterSearchView.setHint(R.string.stations_filter_hint);
        ViewUtils.setVisibility(newFilterSearchView, false);
        if (!DO_FILTER_BY_SEARCH_ON_NETWORK) {
            newFilterSearchView.setTextChangeListener(this::setListFilter);
            newFilterSearchView.setOnClearListener(v -> {
                ViewUtils.setVisibility(locationView, true);
                ViewUtils.setVisibility(legacySearchBoxView, false);
                ViewUtils.setVisibility(newFilterSearchView, false);
            });
        }
        legacySearchBoxView = findViewById(R.id.stations_search_box);
        ViewUtils.setVisibility(legacySearchBoxView, false);
        ViewUtils.setVisibility(findViewById(R.id.stations_search_text), DO_FILTER_BY_SEARCH_ON_NETWORK);
        searchEditView = findViewById(R.id.stations_search_edit);
        findViewById(R.id.stations_search_clear).setOnClickListener(v -> {
            if (!DO_FILTER_BY_SEARCH_ON_NETWORK) {
                ViewUtils.setVisibility(locationView, true);
                ViewUtils.setVisibility(legacySearchBoxView, false);
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
                                if (!station.filter(products)) {
                                    i.remove();
                                    stationsMap.remove(station.location.id);
                                }
                            }

                            stationListAdapter.notifyDataSetChanged();
                            journeyListAdapter.notifyDataSetChanged();
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
        actionBar.concludeSetup();

        swipeRefresh = findViewById(R.id.stations_refresh);
        swipeRefresh.setOnRefreshListener(this::requestRefresh);

        locationProvidersView = findViewById(R.id.stations_list_location_providers);

        final Button locationPermissionRequestButton = findViewById(
                R.id.stations_location_permission_request_button);
        locationPermissionRequestButton.setOnClickListener(v -> requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION));

        final Button locationSettingsButton = findViewById(R.id.stations_list_location_settings);
        locationSettingsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));

        final OnClickListener selectNetworkListener = v -> startNetworkPicker();
        final Button networkSettingsButton = findViewById(R.id.stations_list_empty_network_settings);
        networkSettingsButton.setOnClickListener(selectNetworkListener);
        final Button missingCapabilityButton = findViewById(R.id.stations_network_missing_capability_button);
        missingCapabilityButton.setOnClickListener(selectNetworkListener);

        locationView = findViewById(R.id.stations_location);
        locationView.setHint(R.string.stations_location_hint);
        setupLocationViews();

        getMapView().setStationsAware(this);
        getMapView().setDeviceLocationAware(this);
        getMapView().setStationsOverlay(locationView);

        connectivityWarningView = findViewById(R.id.stations_connectivity_warning_box);
        final View disclaimerView = findViewById(R.id.stations_disclaimer_group);
        ViewCompat.setOnApplyWindowInsetsListener(disclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
        disclaimerSourceView = findViewById(R.id.stations_disclaimer_source);

        // initialize stations list
        setupStationsList();
        setupJourneysList();

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
    }

    private void setupStationsList() {
        expandAllEntries = res.getBoolean(R.bool.expand_all_stationboard_entries);
        maxStationsToLoad = res.getInteger(R.integer.max_stations_to_load_per_stationboard);
        maxDeparturesPerStationCollapsed = res.getInteger(R.integer.max_departures_per_station_collapsed);
        maxDeparturesPerStationExpanded = res.getInteger(R.integer.max_departures_per_station_expanded);

        stationsContainer = findViewById(R.id.stations_container);
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
        stationListAdapter = new StationsAdapter(this,
                maxDeparturesPerStationCollapsed, maxDeparturesPerStationExpanded,
                expandAllEntries, isShowOriginalDirectionText(), products,
                this,
                NetworkProviderFactory.provider(network).hasCapabilities(NetworkProvider.Capability.JOURNEY) ? this : null,
                this);
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
    }

    private void setupJourneysList() {
        maxDeparturesPerJourney = res.getInteger(R.integer.max_departures_per_journey);
        journeysContainer = findViewById(R.id.journeys_container);
        final TextView descriptionView = findViewById(R.id.journeys_description);
        descriptionView.setText(Html.fromHtml(getString(R.string.stations_journeys_description), Html.FROM_HTML_MODE_COMPACT));
        journeyList = findViewById(R.id.journeys_list);
        journeyListLayoutManager = new LinearLayoutManager(this) {
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
        journeyList.setLayoutManager(journeyListLayoutManager);
        // stationList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        journeyListAdapter = new JourneysAdapter(this,
                maxDeparturesPerJourney, isShowOriginalDirectionText(), products,
                this,
                NetworkProviderFactory.provider(network).hasCapabilities(NetworkProvider.Capability.JOURNEY) ? this : null,
                this);
        journeyList.setAdapter(journeyListAdapter);
        ViewCompat.setOnApplyWindowInsetsListener(journeyList, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    insets.bottom + (int) (48 * res.getDisplayMetrics().density));
            return windowInsets;
        });
        journeyList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(final RecyclerView recyclerView, final int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE)
                    postLoadNextVisible(0);
            }
        });
    }

    @Override
    protected int getGlobalOptionsId() {
        return R.id.global_options_stations_nearby;
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
        // updateGUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWalkSpeed();

        updateGUI();
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
        journeyListAdapter.setBaseTime(null);
        journeyListAdapter.notifyDataSetChanged();
        getMapView().invalidate();
        loading = true;

        stationList.scrollToPosition(0);
        journeyList.scrollToPosition(0);
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
        journeyList.clearOnScrollListeners();

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
        final Date presetTime = (Date) intent.getSerializableExtra(INTENT_EXTRA_TIME);

        final String networkName = intent.getStringExtra(INTENT_EXTRA_NETWORK);
        if (networkName != null)
            this.network = NetworkId.valueOf(networkName);

        if (presetLocation != null)
            setFixedLocation(presetLocation, presetTime);

        if (Intent.ACTION_SEND.equals(intentAction) && intentExtraText != null
                && intentExtraText.startsWith(ExternalMapsUtils.GMAPS_SHORT_LOCATION_URL_PREFIX)) {
            // location shared from Google Maps app
            startBackgroundHandler();
            fixedLocationResolving = true;
            backgroundHandler.post(() -> {
                final Location location = ExternalMapsUtils.resolveLocationUrl(intentExtraText);
                runOnUiThread(() -> {
                    setFixedLocation(location, presetTime);
                    updateGUI();
                });
            });
        } else if (intentUri != null) {
            final Location[] locations = LocationUriParser.parseLocations(intentUri.toString());
            setFixedLocation(locations != null && locations.length >= 1 ? locations[0] : null, presetTime);
        } else {
            command = (Command) intent.getSerializableExtra(INTENT_EXTRA_COMMAND);
        }

        if (command != null) {
            startBackgroundHandler();
            final AutoCompleteLocationsHandler autoCompleteLocationsHandler = new AutoCompleteLocationsHandler(
                    this,
                    network, null,
                    backgroundHandler,
                    getNetworkDefaultProducts());
            autoCompleteLocationsHandler.addJob(command.atText, false);
            final Date time = command.time == null ? null : command.time.date();
            autoCompleteLocationsHandler.start(result -> {
                if (!result.success) return;
                StationDetailsActivity.start(this, network, result.location, time, null);
            });
        }

        if (intent.getBooleanExtra(INTENT_EXTRA_OPEN_FAVORITES, false)) {
            FavoriteStationsActivity.start(this);
        }

        updateGUI();
    }

    private void setFixedLocation(final Location location, final Date presetTime) {
        locationView.setLocation(location);
        this.presetTime = presetTime;
        stationListAdapter.setBaseTime(presetTime);
        journeyListAdapter.setBaseTime(presetTime);
    }

    private void setLocationByUser(final Location location) {
        fixedLocation = location;
        fixedLocationResolving = false;

        stationList.scrollToPosition(0);
        journeyList.scrollToPosition(0);

        // clear the time preset
        final boolean hadPresetTime = presetTime != null;
        presetTime = null;
        stationListAdapter.setBaseTime(null);
        journeyListAdapter.setBaseTime(null);

        // remove non-favorites and re-calculate distances
        for (final Iterator<Station> i = stations.iterator(); i.hasNext(); ) {
            final Station station = i.next();

            final Integer favState = favorites.get(station.location.id);
            if (favState != null && favState == FavoriteStationsProvider.TYPE_FAVORITE) {
                if (hadPresetTime)
                    station.setDepartures(null);

                if (station.location.hasCoord())
                    station.setDistanceAndBearing(GeoUtils.distanceBetween(deviceLocation, station.location.coord), walkPaceMillisPerMeter);
            } else {
                i.remove();
                stationsMap.remove(station.location.id);
            }
        }

        stationListAdapter.notifyDataSetChanged();
        handler.post(initStationsRunnable);

        if (fixedLocation != null) {
            if (fixedLocation.hasCoord())
                getMapView().animateToLocation(fixedLocation.getLatAsDouble(), fixedLocation.getLonAsDouble());
        } else {
            if (deviceLocation != null)
                getMapView().animateToLocation(deviceLocation.getLatAsDouble(), deviceLocation.getLonAsDouble());
        }

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

        journeyListAdapter.setShowPlaces(searchQuery != null);
        journeyListAdapter.setFilterQuery(filterQuery);
        journeyListAdapter.notifyDataSetChanged();

        if (searchQueryModified) {
            handler.post(initStationsRunnable);
        }

        updateGUI();
    }

    private void updateGUI() {
        // fragments
        updateFragments();

        ViewUtils.setVisibility(stationsContainer, !sortByWalkAccess);
        ViewUtils.setVisibility(journeysContainer, sortByWalkAccess);

        // filter indicator
        filterActionButton.setSelected(!productsAreNetworkDefault(products));

        final ViewAnimator viewAnimator = findViewById(R.id.stations_list_layout);
        final int childViewIndex;
        if (network == null || !NetworkProviderFactory.provider(network).hasCapabilities(Capability.DEPARTURES)) {
            childViewIndex = 1; // Missing capability
        } else if (searchQuery == null && fixedLocation == null && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            childViewIndex = 2; // Location permission denied
        } else if (searchQuery == null && fixedLocation == null && loading && deviceLocation == null && !anyProviderEnabled) {
            childViewIndex = 3; // Location providers disabled
        } else if (searchQuery == null && fixedLocation == null && loading && deviceLocation == null && anyProviderEnabled) {
            childViewIndex = 4; // Acquiring location
        } else if (stations.isEmpty() && searchQuery == null && loading && deviceLocation != null) {
            childViewIndex = 5; // Querying nearby stations
        } else if (stations.isEmpty() && searchQuery != null && loading) {
            childViewIndex = 6; // Matching stations
        } else if (stations.isEmpty() && queryFailed) {
            childViewIndex = 9; // network error
        } else if (stations.isEmpty() && searchQuery == null) {
            childViewIndex = 7; // List empty, no nearby stations
        } else if (stations.isEmpty() && searchQuery != null) {
            childViewIndex = 8; // List empty, no query match
        } else {
            childViewIndex = 0; // Stations list
        }
        viewAnimator.setDisplayedChild(childViewIndex); // Stations list

        // location box
        if (presetTime != null && fixedLocation != null) {
            final String locationName = fixedLocation.name != null ? fixedLocation.uniqueShortName()
                    : String.format(Locale.ENGLISH, "%.6f, %.6f", fixedLocation.getLatAsDouble(), fixedLocation.getLonAsDouble());
            final String text = presetTime == null ? locationName
                    : String.format("%s @ %s", locationName, Formats.formatTime(timeZoneSelector, presetTime, PTDate.NETWORK_OFFSET));
            locationView.setText(text);
        }

        // search box
        if (DO_FILTER_BY_SEARCH_ON_NETWORK) {
            final boolean haveFilterText = filterByText != null;
            ViewUtils.setVisibility(locationView, !haveFilterText);
            ViewUtils.setVisibility(findViewById(R.id.stations_search_box), haveFilterText);
            if (haveFilterText)
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
                final OeffiActionBar actionBar = getMyActionBar();

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
                        queryFailed = true;
                        final NearbyLocationsResult result =
                                networkProvider.queryNearbyLocations(
                                        EnumSet.of(LocationType.STATION),
                                        referenceLocation,
                                        NetworkProvider.EquivalentStationsMode.META_IF_SAME_NAME,
                                        0,
                                        0,
                                        products);
                        if (result.status == NearbyLocationsResult.Status.OK) {
                            log.info("Got {}", result.toShortString());

                            final List<Station> freshStations = new ArrayList<>(result.locations.size());

                            for (final Location location : result.locations) {
                                if (location.type == LocationType.STATION) {
                                    final Point coord = location.coord;
                                    if (coord != null) {
                                        final Station station = new Station(network, location);
                                        station.setDistanceAndBearing(GeoUtils.distanceBetween(referenceLocation.coord, coord), walkPaceMillisPerMeter);
                                        freshStations.add(station);
                                    }
                                }
                            }

                            queryFailed = false;
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
                        station.setDistanceAndBearing(GeoUtils.distanceBetween(deviceLocation, location.coord), walkPaceMillisPerMeter);
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
                                freshStation.setDistanceAndBearing(GeoUtils.distanceBetween(referenceLocation.coord, freshStation.location.coord), walkPaceMillisPerMeter);
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
                        station.setDistanceAndBearing(freshStation.distance, freshStation.bearing, walkPaceMillisPerMeter);
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
            } else if (freshStation.filter(products)) {
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
            journeyListAdapter.notifyDataSetChanged();
            getMapView().invalidate();
        }

        if (added) {
            handler.postDelayed(() -> getMapView().zoomToStations(
                    stations.stream().map(station -> station.location).collect(Collectors.toList()),
                    0),500);
        }

        updateGUI();
    }

    public void updateWalkSpeed() {
        final NetworkProvider.WalkSpeed walkSpeed = application.prefsGetWalkSpeed();
        final float speedMetersPerMinute;
        switch (walkSpeed) {
            case FAST:
                speedMetersPerMinute = JourneysAdapter.WALK_SPEED_FAST_METERS_PER_MINUTE;
                break;
            case SLOW:
                speedMetersPerMinute = JourneysAdapter.WALK_SPEED_SLOW_METERS_PER_MINUTE;
                break;
            case NORMAL:
            default:
                speedMetersPerMinute = JourneysAdapter.WALK_SPEED_NORMAL_METERS_PER_MINUTE;
                break;
        }
        final float walkDistanceFactor = ((float) (prefs.getInt(PREF_KEY_WALK_DISTANCE_FACTOR, 50) + 100)) / 100f;
        walkPaceMillisPerMeter = walkDistanceFactor * 60000f / speedMetersPerMinute;
    }

    private static void sortStations(final List<Station> stations) {
        Collections.sort(stations, (station1, station2) ->
                Comparator
                        // order by distance
                        .<Station, Boolean>comparing(station -> station.hasDistanceAndBearing)
                        .thenComparing(station -> station.distance)
                        // order by product
                        .thenComparing(Station::getRelevantProduct, Comparator.nullsLast(Product::compareTo))
                        .compare(station1, station2)
        );
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
            final Station nextStation = nextStationToLoad();

            if (nextStation != null) {
                final Location requestedStation = nextStation.location;
                if (requestedStation.hasId()) {
                    final Date now = new Date();
                    nextStation.requestedAt = now;

                    final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);

                    final Set<Product> reqProducts = new HashSet<>();
                    if (requestedStation.products == null) {
                        reqProducts.addAll(products);
                    } else {
                        reqProducts.addAll(requestedStation.products);
                        reqProducts.retainAll(products);
                    }
                    final int maxDepartures = maxDeparturesPerStationExpanded * (reqProducts.isEmpty() ? 2 : products.size());
                    backgroundHandler.post(
                            new QueryDeparturesRunnable(
                                    handler, networkProvider,
                                    requestedStation,
                                    NetworkProvider.EquivalentStationsMode.KEEP_DISTINCT, // META_IF_SAME_NAME,
                                    presetTime, false,
                                    reqProducts.isEmpty() ? null : reqProducts,
                                    maxDepartures, false) {
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
                                    if (result.header != null) {
                                        updateDisclaimerSource(disclaimerSourceView, network,
                                                product(result.header));
                                    }

                                    if (result.status == QueryDeparturesResult.Status.OK) {
                                        List<Departure> newDepartures = null;
                                        for (final StationDepartures subStationDepartures : result.stationDepartures) {
                                            final String subStationId = subStationDepartures.location.id;
                                            final List<Departure> departures = subStationDepartures.getNonCancelledDepartures();
                                            // Trim departures
                                            while (departures.size() > maxEvents)
                                                departures.remove(departures.size() - 1);
                                            final Station subStation = stationsMap.get(subStationId);
                                            if (subStation != null && !subStationId.equals(requestedStation.id)) {
                                                if (subStation.requestedAt == null && !departures.isEmpty()) {
                                                    subStation.setDepartures(departures);
                                                    subStation.departureQueryStatus = QueryDeparturesResult.Status.OK;
                                                    subStation.updatedAt = now;
                                                }
                                            } else {
                                                newDepartures = departures;
                                            }
                                        }

                                        final Station requestedStation = stationsMap.get(station.id);
                                        if (requestedStation != null) {
                                            if (newDepartures == null) {
                                                // requested station is not contained in response, may be meta-station
                                                requestedStation.setDepartures(null);
                                                requestedStation.departureQueryStatus = QueryDeparturesResult.Status.INVALID_STATION;
                                                requestedStation.updatedAt = now;
                                            } else {
                                                requestedStation.setDepartures(newDepartures);
                                                requestedStation.departureQueryStatus = QueryDeparturesResult.Status.OK;
                                                requestedStation.updatedAt = now;
                                            }
                                        }

                                        stationListAdapter.notifyDataSetChanged();
                                        journeyListAdapter.notifyDataSetChanged();
                                    } else if (result.status == QueryDeparturesResult.Status.INVALID_STATION) {
                                        final Station resultStation = stationsMap.get(requestedStation.id);
                                        if (resultStation != null) {
                                            resultStation.departureQueryStatus = QueryDeparturesResult.Status.INVALID_STATION;
                                            resultStation.updatedAt = now;

                                            stationListAdapter.notifyDataSetChanged();
                                            journeyListAdapter.notifyDataSetChanged();
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
                                    log.info("Redirect while querying departures on {}", requestedStation.id);

                                    handler.post(() -> new Toast(StationsActivity.this).toast(R.string.toast_network_problem));
                                }

                                @Override
                                protected void onBlocked(final HttpUrl url) {
                                    log.info("Blocked querying departures on {}", requestedStation.id);

                                    handler.post(() -> new Toast(StationsActivity.this).toast(R.string.toast_network_blocked,
                                            url.host()));
                                }

                                protected void onRateLimit(final HttpUrl url) {
                                    log.info("Rate limited querying departures on {}", requestedStation.id);

                                    handler.post(() -> new Toast(StationsActivity.this).toast(R.string.toast_network_rate_limit,
                                            url.host()));
                                }

                                @Override
                                protected void onInternalError(final HttpUrl url) {
                                    log.info("Internal error querying departures on {}", requestedStation.id);

                                    handler.post(() -> new Toast(StationsActivity.this).toast(R.string.toast_internal_error,
                                            url.host()));
                                }

                                @Override
                                protected void onParserException(final String message) {
                                    log.info("Cannot parse departures on {}: {}", requestedStation.id, message);

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

            int firstVisible;
            int lastVisible;
            final int maxDistance;
            if (sortByWalkAccess) {
                firstVisible = 0;
                lastVisible = Integer.MAX_VALUE;
                maxDistance = application.prefsGetMaxWalkDistance();
            } else {
                firstVisible = stationListLayoutManager.findFirstVisibleItemPosition();
                lastVisible = stationListLayoutManager.findLastVisibleItemPosition();
                maxDistance = Integer.MAX_VALUE;
            }

            if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION)
                return null;
            final int size = stations.size();
            if (firstVisible >= size)
                firstVisible = size - 1;
            if (lastVisible >= size)
                lastVisible = size - 1;

            final long now = System.currentTimeMillis();

            for (int i = firstVisible; i <= lastVisible; i++) { // first load selected
                final Station station = stations.get(i);
                final Date requestedAt = station.requestedAt;
                if (requestedAt != null && now - requestedAt.getTime() < Constants.MINUTE_IN_MILLIS)
                    continue;

                if (selectedStation != null
                        && selectedStation.location.id != null
                        && selectedStation.location.id.equals(station.location.id)) {
                    return station;
                }
            }

            for (int i = firstVisible; i <= lastVisible; i++) { // then load favorites
                final Station station = stations.get(i);

                final Date requestedAt = station.requestedAt;
                if (requestedAt != null && now - requestedAt.getTime() < Constants.MINUTE_IN_MILLIS)
                    continue;

                final Integer favState = favorites.get(station.location.id);
                if (favState != null && favState == FavoriteStationsProvider.TYPE_FAVORITE)
                    return station;
            }

            if (sortByWalkAccess) {
                if (lastVisible > 25)
                    lastVisible = 25;
            }

            for (int i = firstVisible; i <= lastVisible; i++) { // then load others
                if (i >= firstVisible + maxStationsToLoad)
                    break;

                final Station station = stations.get(i);
                if (station.distance > maxDistance)
                    continue;

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
        journeyListAdapter.notifyDataSetChanged();

        if (selectedStation != null) {
            // scroll list into view
            for (int position = 0; position < stations.size(); position++) {
                if (stations.get(position).equals(station)) {
                    stationList.smoothScrollToPosition(position);
                    break;
                }
            }
        }

        // scroll map
        if (station != null && station.location.hasCoord())
            getMapView().zoomToStations(List.of(station.location), 0);
        else if (!stations.isEmpty())
            getMapView().zoomToStations(stations.stream().map(s -> s.location).collect(Collectors.toList()), 0);
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
            return LocationUtils.locationFromCoord(deviceLocation);
        return null;
    }

    public final Double getDeviceBearing() {
        if (fixedLocationResolving || fixedLocation != null)
            return null;
        return deviceBearing;
    }

    @Override
    public Double getDeviceSpeed() {
        return null;
    }

    private void startLocationProvider() {
        // determine location providers
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (locationManager.getProviders(true).contains(LocationManager.GPS_PROVIDER)) {
                accurateLocationProvider = LocationManager.GPS_PROVIDER;
            } else {
                final Criteria accurateCriteria = new Criteria();
                accurateCriteria.setAccuracy(Criteria.ACCURACY_FINE);
                accurateLocationProvider = locationManager.getBestProvider(accurateCriteria, true);
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            final Criteria lowPowerCriteria = new Criteria();
            lowPowerCriteria.setPowerRequirement(Criteria.POWER_LOW);
            lowPowerLocationProvider = locationManager.getBestProvider(lowPowerCriteria, true);
        }

        if (accurateLocationProvider != null && accurateLocationProvider.equals(lowPowerLocationProvider))
            lowPowerLocationProvider = null;

        // request update on location change
        if (accurateLocationProvider != null) {
            locationManager.requestLocationUpdates(accurateLocationProvider, Constants.LOCATION_UPDATE_FREQ_MS,
                    Constants.LOCATION_UPDATE_DISTANCE, locationListener);
        }
        if (lowPowerLocationProvider != null) {
            locationManager.requestLocationUpdates(lowPowerLocationProvider, Constants.LOCATION_UPDATE_FREQ_MS,
                    Constants.LOCATION_UPDATE_DISTANCE, locationListener);
        }

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

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
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
                    network, station, null, null, null, null, false,
                    getReturnHereIntent(),
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return true;
        } else if (menuItemId == R.id.station_context_directions_to) {
            DirectionsActivity.start(StationsActivity.this,
                    network, null, station, null, null, null, false,
                    getReturnHereIntent(),
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return true;
        } else if (menuItemId == R.id.station_context_launcher_shortcut) {
            StationContextMenu.showLauncherShortcutDialog(StationsActivity.this, network, station);
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
            setMapVisible(true);
            if (station != null && station.hasCoord())
                getMapView().zoomToStations(List.of(station), 0);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onJourneyClick(
            final View clickedView, final JourneyRef journeyRef,
            final Location entryLocation, final Date entryTime) {
        queryJourneyRunnable = QueryJourneyRunnable.startShowJourney(
                this, clickedView, queryJourneyRunnable,
                handler, backgroundHandler,
                network, journeyRef, isDriverMode,
                entryLocation, entryTime,
                null, null,
                true,
                false,
                getReturnHereIntent());
    }

    private final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(final android.location.Location here) {
            log.info("Got relevant location: {}", here);

            deviceLocation = Point.fromDouble(here.getLatitude(), here.getLongitude());
            stationListAdapter.setDeviceLocation(here);
            journeyListAdapter.setDeviceLocation(here);

            // re-calculate distances for sorting
            if (fixedLocation == null) {
                for (final Station station : stations) {
                    if (station.location.hasCoord())
                        station.setDistanceAndBearing(GeoUtils.distanceBetween(here, station.location.coord), walkPaceMillisPerMeter);
                }

                stationListAdapter.notifyDataSetChanged();
                journeyListAdapter.notifyDataSetChanged();

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
            final double azimuth = Math.toDegrees(orientation[0]);

            lastTime = System.currentTimeMillis();

            runOnUiThread(() -> {
                deviceBearing = azimuth;
                stationListAdapter.setDeviceBearing(azimuth, faceDown);
                journeyListAdapter.setDeviceBearing(azimuth, faceDown);

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
