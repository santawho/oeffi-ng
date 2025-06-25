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
import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Canvas;
import android.location.Address;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.provider.ContactsContract.CommonDataKinds;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.base.Throwables;
import com.google.common.primitives.Floats;

import de.schildbach.oeffi.FromViaToAware;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiMainActivity;
import de.schildbach.oeffi.OeffiMapView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.util.TimeSpec.DepArr;
import de.schildbach.oeffi.directions.list.QueryHistoryAdapter;
import de.schildbach.oeffi.directions.list.QueryHistoryClickListener;
import de.schildbach.oeffi.directions.list.QueryHistoryContextMenuItemListener;
import de.schildbach.oeffi.network.NetworkPickerActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.FavoriteStationsActivity;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.stations.FavoriteUtils;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.stations.StationsActivity;
import de.schildbach.oeffi.util.AutoCompleteLocationAdapter;
import de.schildbach.oeffi.util.AutoCompleteLocationsHandler;
import de.schildbach.oeffi.util.ConnectivityBroadcastReceiver;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.GeocoderThread;
import de.schildbach.oeffi.util.GoogleMapsUtils;
import de.schildbach.oeffi.util.LocationUriParser;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.oeffi.util.ZoomControls;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.NetworkProvider.Capability;
import de.schildbach.pte.NetworkProvider.TripFlag;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.TripOptions;
import de.schildbach.pte.dto.TripRef;
import de.schildbach.pte.dto.TripShare;
import okhttp3.HttpUrl;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class DirectionsActivity extends OeffiMainActivity implements
        QueryHistoryClickListener,
        QueryHistoryContextMenuItemListener,
        LocationSelector.LocationSelectionListener {
    public static final String LINK_IDENTIFIER_TRIP = "trip";
    public static final String LINK_IDENTIFIER_SHARE_TRIP = "share-trip";

    private ConnectivityManager connectivityManager;
    private LocationManager locationManager;

    private View quickReturnView;
    private LocationSelector locationSelector;
    private ToggleImageButton buttonExpand;
    private LocationView viewFromLocation;
    private LocationView viewViaLocation;
    private LocationView viewToLocation;
    private View viewProducts;
    private List<ToggleImageButton> viewProductToggles = new ArrayList<>(8);
    private CheckBox viewBike;
    private Button viewTimeDepArr;
    private Button viewTime1;
    private Button viewTime2;
    private Button viewGo;
    private RecyclerView viewQueryHistoryList;
    private QueryHistoryAdapter queryHistoryListAdapter;
    private View viewQueryHistoryEmpty;
    private View viewQueryMissingCapability;
    private TextView connectivityWarningView;
    private OeffiMapView mapView;

    private TimeSpec time = null;
    private TripsOverviewActivity.RenderConfig renderConfig;

    private QueryTripsRunnable queryTripsRunnable;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final Handler handler = new Handler();
    private BroadcastReceiver connectivityReceiver;
    private BroadcastReceiver tickReceiver;
    private AutoCompleteLocationAdapter autoCompleteLocationAdapter;

    private static final int DIALOG_CLEAR_HISTORY = 1;

    private static final Logger log = LoggerFactory.getLogger(DirectionsActivity.class);

    private static final String INTENT_EXTRA_FROM_LOCATION = DirectionsActivity.class.getName() + ".from_location";
    private static final String INTENT_EXTRA_TO_LOCATION = DirectionsActivity.class.getName() + ".to_location";
    private static final String INTENT_EXTRA_VIA_LOCATION = DirectionsActivity.class.getName() + ".via_location";
    private static final String INTENT_EXTRA_TIME_SPEC = DirectionsActivity.class.getName() + ".time_spec";
    private static final String INTENT_EXTRA_COMMAND = DirectionsActivity.class.getName() + ".command";
    private static final String INTENT_EXTRA_RENDERCONFIG = DirectionsActivity.class.getName() + ".config";

    private static class PickContact extends ActivityResultContract<Void, Uri> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context, Void unused) {
            return new Intent(Intent.ACTION_PICK, CommonDataKinds.StructuredPostal.CONTENT_URI);
        }

        @Override
        public Uri parseResult(final int resultCode, @Nullable final Intent intent) {
            if (resultCode == Activity.RESULT_OK && intent != null)
                return intent.getData();
            else
                return null;
        }
    }

    private final ActivityResultLauncher<String> requestLocationPermissionFromLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted)
                    viewFromLocation.acquireLocation();
            });
    private final ActivityResultLauncher<String> requestLocationPermissionViaLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted)
                    viewViaLocation.acquireLocation();
            });
    private final ActivityResultLauncher<String> requestLocationPermissionToLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted)
                    viewToLocation.acquireLocation();
            });
    private final ActivityResultLauncher<Void> pickContactFromLauncher =
            registerForActivityResult(new PickContact(), contentUri -> {
                if (contentUri != null)
                    resultPickContact(contentUri, viewFromLocation);
            });
    private final ActivityResultLauncher<Void> pickContactViaLauncher =
            registerForActivityResult(new PickContact(), contentUri -> {
                if (contentUri != null)
                    resultPickContact(contentUri, viewViaLocation);
            });
    private final ActivityResultLauncher<Void> pickContactToLauncher =
            registerForActivityResult(new PickContact(), contentUri -> {
                if (contentUri != null)
                    resultPickContact(contentUri, viewToLocation);
            });
    private final ActivityResultLauncher<NetworkId> pickStationFromLauncher =
            registerForActivityResult(new FavoriteStationsActivity.PickFavoriteStation(), contentUri -> {
                if (contentUri != null)
                    resultPickStation(contentUri, viewFromLocation);
            });
    private final ActivityResultLauncher<NetworkId> pickStationViaLauncher =
            registerForActivityResult(new FavoriteStationsActivity.PickFavoriteStation(), contentUri -> {
                if (contentUri != null)
                    resultPickStation(contentUri, viewViaLocation);
            });
    private final ActivityResultLauncher<NetworkId> pickStationToLauncher =
            registerForActivityResult(new FavoriteStationsActivity.PickFavoriteStation(), contentUri -> {
                if (contentUri != null)
                    resultPickStation(contentUri, viewToLocation);
            });

    public static void start(
            final Context context,
            @Nullable final Location fromLocation,
            @Nullable final Location toLocation,
            @Nullable final Location viaLocation,
            @Nullable final TimeSpec timeSpec,
            @Nullable final TripsOverviewActivity.RenderConfig renderConfig,
            final int intentFlags) {
        final Intent intent = new Intent(context, DirectionsActivity.class).addFlags(intentFlags);
        if (fromLocation != null)
            intent.putExtra(DirectionsActivity.INTENT_EXTRA_FROM_LOCATION, fromLocation);
        if (toLocation != null)
            intent.putExtra(DirectionsActivity.INTENT_EXTRA_TO_LOCATION, toLocation);
        if (viaLocation != null)
            intent.putExtra(DirectionsActivity.INTENT_EXTRA_VIA_LOCATION, viaLocation);
        if (timeSpec != null)
            intent.putExtra(DirectionsActivity.INTENT_EXTRA_TIME_SPEC, timeSpec);
        if (renderConfig != null)
            intent.putExtra(INTENT_EXTRA_RENDERCONFIG, renderConfig);
        context.startActivity(intent);
    }

    public static class Command implements Serializable {
        private static final long serialVersionUID = 4782653146464112314L;
        public String fromText;
        public String toText;
        public String viaText;
        public TimeSpec time;
    }

    public static void start(
            final Context context,
            final Command command,
            final int intentFlags) {
        final Intent intent = new Intent(context, DirectionsActivity.class).addFlags(intentFlags);
        intent.putExtra(DirectionsActivity.INTENT_EXTRA_COMMAND, command);
        context.startActivity(intent);
    }

    public static Intent handleAppLink(
            final Context context,
            final List<String> actionArgs) {
        final String action = actionArgs.get(0);
        if (LINK_IDENTIFIER_TRIP.equals(action)
            || LINK_IDENTIFIER_SHARE_TRIP.equals(action)) {
            return new Intent(context, DirectionsActivity.class);
        }
        return null;
    }

    private class LocationContextMenuItemClickListener implements PopupMenu.OnMenuItemClickListener {
        private final LocationView locationView;
        private final ActivityResultLauncher<String> requestLocationPermissionLauncher;
        private final ActivityResultLauncher<Void> pickContactLauncher;
        private final ActivityResultLauncher<NetworkId> pickStationLauncher;

        public LocationContextMenuItemClickListener(final LocationView locationView,
                final ActivityResultLauncher<String> requestLocationPermissionLauncher,
                final ActivityResultLauncher<Void> pickContactLauncher, final ActivityResultLauncher<NetworkId> pickStationLauncher) {
            this.locationView = locationView;
            this.requestLocationPermissionLauncher = requestLocationPermissionLauncher;
            this.pickContactLauncher = pickContactLauncher;
            this.pickStationLauncher = pickStationLauncher;
        }

        public boolean onMenuItemClick(final MenuItem item) {
            if (item.getItemId() == R.id.directions_location_current_location) {
                if (ContextCompat.checkSelfPermission(DirectionsActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    locationView.acquireLocation();
                else
                    requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                return true;
            } else if (item.getItemId() == R.id.directions_location_contact) {
                pickContactLauncher.launch(null);
                return true;
            } else if (item.getItemId() == R.id.directions_location_favorite_station) {
                if (network != null)
                    pickStationLauncher.launch(network);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    protected String taskName() {
        return "directions";
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (intent.hasExtra(INTENT_EXTRA_RENDERCONFIG))
            renderConfig = (TripsOverviewActivity.RenderConfig) intent.getSerializableExtra(INTENT_EXTRA_RENDERCONFIG);
        else
            renderConfig = new TripsOverviewActivity.RenderConfig();

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            time = new TimeSpec.Relative(0);
        }

        backgroundThread = new HandlerThread("Directions.queryTripsThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        setContentView(R.layout.directions_content);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        final MyActionBar actionBar = getMyActionBar();
        setPrimaryColor(renderConfig.actionBarColor > 0 ? renderConfig.actionBarColor : R.color.bg_action_bar_directions);
        actionBar.setPrimaryTitle(R.string.directions_activity_title);
        actionBar.setTitlesOnClickListener(v -> NetworkPickerActivity.start(DirectionsActivity.this));
        buttonExpand = actionBar.addToggleButton(R.drawable.ic_expand_white_24dp,
                R.string.directions_action_expand_title);
        buttonExpand.setOnCheckedChangeListener((buttonView, isChecked) -> {
            expandForm(isChecked);
            updateMap();
        });
        if (renderConfig.isAlternativeConnectionSearch) {
            actionBar.addButton(R.drawable.ic_clear_white_24dp, R.string.directions_action_restart_planning_title)
                    .setOnClickListener(v -> {
                        finish();
                        final Intent newIntent = new Intent(this, DirectionsActivity.class);
                        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(newIntent);
                    });
        } else {
            actionBar.addButton(R.drawable.ic_shuffle_white_24dp, R.string.directions_action_return_trip_title)
                    .setOnClickListener(v -> viewToLocation.exchangeWith(viewFromLocation));
        }
        actionBar.overflow(R.menu.directions_options, item -> {
            if (item.getItemId() == R.id.directions_options_clear_history) {
                if (network != null)
                    showDialog(DIALOG_CLEAR_HISTORY);
                return true;
            } else {
                return false;
            }
        });

        initNavigation();

        findViewById(R.id.directions_network_missing_capability_button)
                .setOnClickListener(v -> NetworkPickerActivity.start(DirectionsActivity.this));
        connectivityWarningView = findViewById(R.id.directions_connectivity_warning_box);

        initLayoutTransitions();

        autoCompleteLocationAdapter = new AutoCompleteLocationAdapter(this, network);

        final LocationView.Listener locationChangeListener = (view) -> {
            final Location location = view.getLocation();
            if (location != null)
                locationSelector.addLocation(location, System.currentTimeMillis());

            updateMap();
            queryHistoryListAdapter.clearSelectedEntry();
            requestFocusFirst();
        };

        viewFromLocation = findViewById(R.id.directions_from);
        viewFromLocation.setAdapter(autoCompleteLocationAdapter);
        viewFromLocation.setListener(locationChangeListener);
        viewFromLocation.setContextMenuItemClickListener(new LocationContextMenuItemClickListener(viewFromLocation,
                requestLocationPermissionFromLauncher, pickContactFromLauncher, pickStationFromLauncher));
        viewFromLocation.setEnabled(!renderConfig.isAlternativeConnectionSearch);
        viewFromLocation.setStationAsAddressEnabled(true);

        viewViaLocation = findViewById(R.id.directions_via);
        viewViaLocation.setAdapter(autoCompleteLocationAdapter);
        viewViaLocation.setListener(locationChangeListener);
        viewViaLocation.setContextMenuItemClickListener(new LocationContextMenuItemClickListener(viewViaLocation,
                requestLocationPermissionViaLauncher, pickContactViaLauncher, pickStationViaLauncher));

        viewToLocation = findViewById(R.id.directions_to);
        viewToLocation.setAdapter(autoCompleteLocationAdapter);
        viewToLocation.setListener(locationChangeListener);
        viewToLocation.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                viewGo.performClick();
                return true;
            } else if (actionId == EditorInfo.IME_ACTION_DONE) {
                requestFocusFirst();
                return true;
            } else {
                return false;
            }
        });
        viewToLocation.setContextMenuItemClickListener(new LocationContextMenuItemClickListener(viewToLocation,
                requestLocationPermissionToLauncher, pickContactToLauncher, pickStationToLauncher));
        viewToLocation.setStationAsAddressEnabled(true);

        viewProducts = findViewById(R.id.directions_products);
        viewProductToggles.add(findViewById(R.id.directions_products_i));
        viewProductToggles.add(findViewById(R.id.directions_products_r));
        viewProductToggles.add(findViewById(R.id.directions_products_s));
        viewProductToggles.add(findViewById(R.id.directions_products_u));
        viewProductToggles.add(findViewById(R.id.directions_products_t));
        viewProductToggles.add(findViewById(R.id.directions_products_b));
        viewProductToggles.add(findViewById(R.id.directions_products_p));
        viewProductToggles.add(findViewById(R.id.directions_products_f));
        viewProductToggles.add(findViewById(R.id.directions_products_c));

        final OnLongClickListener productLongClickListener = clickedView -> {
            final DialogBuilder builder = DialogBuilder.get(DirectionsActivity.this);
            builder.setTitle(R.string.directions_products_prompt);
            builder.setItems(R.array.directions_products, (dialog, which) -> {
                final Set<Product> networkDefaultProducts = getNetworkDefaultProducts();
                final Function<View, Boolean> checkedStateFunction;
                switch (which) {
                    case 0:
                        checkedStateFunction = (view) -> view.equals(clickedView); // only this
                        break;
                    case 1:
                        checkedStateFunction = (view) -> !view.equals(clickedView); // all except this
                        break;
                    case 2:
                        checkedStateFunction = (view) -> networkDefaultProducts.contains(
                                Product.fromCode(((String) view.getTag()).charAt(0))); // network defaults
                        break;
                    case 3:
                        checkedStateFunction = (view) -> true; // all true
                        break;
                    case 4:
                        checkedStateFunction = (view) -> Product.LOCAL_PRODUCTS.contains(
                                Product.fromCode(((String) view.getTag()).charAt(0))); // only local products
                        break;
                    default:
                        return;
                }
                for (final ToggleImageButton view : viewProductToggles) {
                    view.setChecked(checkedStateFunction.apply(view));
                }
            });
            builder.show();
            return true;
        };
        for (final View view : viewProductToggles)
            view.setOnLongClickListener(productLongClickListener);

        viewBike = findViewById(R.id.directions_option_bike);

        viewTimeDepArr = findViewById(R.id.directions_time_dep_arr);
        viewTimeDepArr.setOnClickListener(v -> {
            final DialogBuilder builder = DialogBuilder.get(DirectionsActivity.this);
            builder.setTitle(R.string.directions_set_time_prompt);
            builder.setItems(R.array.directions_set_time, (dialog, which) -> {
                final String[] parts = getResources().getStringArray(R.array.directions_set_time_values)[which]
                        .split("_");
                final DepArr depArr = DepArr.valueOf(parts[0]);
                if (parts[1].equals("AT")) {
                    time = new TimeSpec.Absolute(depArr, time.timeInMillis());
                } else if (parts[1].equals("IN")) {
                    if (parts.length > 2) {
                        time = new TimeSpec.Relative(depArr,
                                Long.parseLong(parts[2]) * DateUtils.MINUTE_IN_MILLIS);
                    } else {
                        time = new TimeSpec.Relative(depArr, 0);
                        handleDiffClick();
                    }
                } else {
                    throw new IllegalStateException(parts[1]);
                }
                updateGUI();
            });
            builder.show();
        });

        viewTime1 = findViewById(R.id.directions_time_1);
        viewTime2 = findViewById(R.id.directions_time_2);

        viewGo = findViewById(R.id.directions_go);
        viewGo.setOnClickListener(v -> handleGo());

        viewQueryHistoryList = findViewById(android.R.id.list);
        viewQueryHistoryList.setLayoutManager(new LinearLayoutManager(this));
        viewQueryHistoryList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        queryHistoryListAdapter = new QueryHistoryAdapter(this, network, this, this);
        viewQueryHistoryList.setAdapter(queryHistoryListAdapter);
        ViewCompat.setOnApplyWindowInsetsListener(viewQueryHistoryList, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    insets.bottom);
            return windowInsets;
        });

        viewQueryHistoryEmpty = findViewById(R.id.directions_query_history_empty);

        viewQueryMissingCapability = findViewById(R.id.directions_network_missing_capability);

        quickReturnView = findViewById(R.id.directions_quick_return);
        final CoordinatorLayout.LayoutParams layoutParams = new CoordinatorLayout.LayoutParams(
                quickReturnView.getLayoutParams().width, quickReturnView.getLayoutParams().height);
        layoutParams.setBehavior(new QuickReturnBehavior());
        quickReturnView.setLayoutParams(layoutParams);
        quickReturnView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            final int height = bottom - top;
            viewQueryHistoryList.setPadding(viewQueryHistoryList.getPaddingLeft(), height,
                    viewQueryHistoryList.getPaddingRight(), viewQueryHistoryList.getPaddingBottom());
            viewQueryHistoryEmpty.setPadding(viewQueryHistoryEmpty.getPaddingLeft(), height,
                    viewQueryHistoryEmpty.getPaddingRight(), viewQueryHistoryEmpty.getPaddingBottom());
            viewQueryMissingCapability.setPadding(viewQueryMissingCapability.getPaddingLeft(), height,
                    viewQueryMissingCapability.getPaddingRight(), viewQueryMissingCapability.getPaddingBottom());
        });

        locationSelector = findViewById(R.id.directions_location_selector);
        locationSelector.setLocationSelectionListener(this);
        locationSelector.setup(this, prefs);
        locationSelector.setNetwork(network);

        mapView = findViewById(R.id.directions_map);
        if (ContextCompat.checkSelfPermission(DirectionsActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            android.location.Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (location != null)
                mapView.animateToLocation(location.getLatitude(), location.getLongitude());
        }
        mapView.getOverlays().add(new Overlay() {
            private Location pinLocation;
            private View pinView;

            @Override
            public void draw(final Canvas canvas, final MapView mapView, final boolean shadow) {
                if (pinView != null)
                    pinView.requestLayout();
            }

            @Override
            public boolean onSingleTapConfirmed(final MotionEvent e, final MapView mapView) {
                final IGeoPoint p = mapView.getProjection().fromPixels((int) e.getX(), (int) e.getY());
                pinLocation = Location.coord(Point.fromDouble(p.getLatitude(), p.getLongitude()));

                final View view = getLayoutInflater().inflate(R.layout.directions_map_pin, null);
                final LocationTextView locationView = view
                        .findViewById(R.id.directions_map_pin_location);
                final View buttonGroup = view.findViewById(R.id.directions_map_pin_buttons);
                buttonGroup.findViewById(R.id.directions_map_pin_button_from).setOnClickListener(v -> {
                    viewFromLocation.setLocation(pinLocation);
                    mapView.removeAllViews();
                });
                buttonGroup.findViewById(R.id.directions_map_pin_button_to).setOnClickListener(v -> {
                    viewToLocation.setLocation(pinLocation);
                    mapView.removeAllViews();
                });
                locationView.setLocation(pinLocation);
                locationView.setShowLocationType(false);

                // exchange view for the pin
                if (pinView != null)
                    mapView.removeView(pinView);
                pinView = view;
                mapView.addView(pinView, new MapView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, p, MapView.LayoutParams.BOTTOM_CENTER, 0, 0));

                new GeocoderThread(DirectionsActivity.this, p.getLatitude(), p.getLongitude(),
                        new GeocoderThread.Callback() {
                            public void onGeocoderResult(final Address address) {
                                pinLocation = GeocoderThread.addressToLocation(address);
                                locationView.setLocation(pinLocation);
                                locationView.setShowLocationType(false);
                            }

                            public void onGeocoderFail(final Exception exception) {
                                log.info("Problem in geocoder: {}", exception.getMessage());
                            }
                        });

                final IMapController controller = mapView.getController();
                controller.animateTo(p);

                return false;
            }
        });
        final TextView mapDisclaimerView = findViewById(R.id.directions_map_disclaimer);
        mapDisclaimerView.setText(mapView.getTileProvider().getTileSource().getCopyrightNotice());
        ViewCompat.setOnApplyWindowInsetsListener(mapDisclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });

        final ZoomControls zoom = findViewById(R.id.directions_map_zoom);
        ViewCompat.setOnApplyWindowInsetsListener(zoom, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
        mapView.setZoomControls(zoom);

        connectivityReceiver = new ConnectivityBroadcastReceiver(connectivityManager) {
            @Override
            protected void onConnected() {
                connectivityWarningView.setVisibility(View.GONE);
            }

            @Override
            protected void onDisconnected() {
                connectivityWarningView.setVisibility(View.VISIBLE);
            }
        };
        registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        handleIntent(intent);

        // initial focus
        if (!viewToLocation.isInTouchMode()) {
            requestFocusFirst();
        }
    }

    @Override
    public void onNewIntent(@NonNull final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        final ComponentName intentComponentName = intent.getComponent();
        final String intentClassName = intentComponentName.getClassName();
        final boolean isSharingTo = intentClassName.endsWith(".TO");
        final boolean isSharingFrom = intentClassName.endsWith(".FROM");
        final boolean isSharing = isSharingTo || isSharingFrom;
        Command command = null;
        if (isSharing) {
            final String intentAction = intent.getAction();
            final Uri intentUri = intent.getData();
            final String intentExtraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (Intent.ACTION_SEND.equals(intentAction) && intentExtraText != null
                    && intentExtraText.startsWith(GoogleMapsUtils.GMAPS_SHORT_LOCATION_URL_PREFIX)) {
                // location shared from Google Maps app
                if (isSharingTo && viewFromLocation.getLocation() == null) {
                    viewFromLocation.acquireLocation();
                }
                backgroundHandler.post(() -> {
                    final Location location = GoogleMapsUtils.resolveLocationUrl(intentExtraText);
                    if (location != null) {
                        runOnUiThread(() -> {
                            if (isSharingTo) {
                                viewToLocation.setLocation(location);
                            } else {
                                viewFromLocation.setLocation(location);
                            }
                        });
                    }
                });
            } else if (intentUri != null) {
                log.info("Got intent: {}, data/uri={}", intent, intentUri);

                final Location[] locations = LocationUriParser.parseLocations(intentUri.toString());

                if (locations.length == 1) {
                    final Location location = locations[0];
                    if (location != null) {
                        if (isSharingTo) {
                            viewToLocation.setLocation(location);
                            if (viewFromLocation.getLocation() == null)
                                viewFromLocation.acquireLocation();
                        } else {
                            viewFromLocation.setLocation(location);
                        }
                    }
                } else {
                    if (locations[0] != null)
                        viewFromLocation.setLocation(locations[0]);
                    if (locations[1] != null)
                        viewToLocation.setLocation(locations[1]);
                    if (locations.length >= 3 && locations[2] != null)
                        viewViaLocation.setLocation(locations[2]);
                }
            }
        } else {
            if (intent.hasExtra(INTENT_EXTRA_FROM_LOCATION))
                viewFromLocation.setLocation((Location) intent.getSerializableExtra(INTENT_EXTRA_FROM_LOCATION));
            if (intent.hasExtra(INTENT_EXTRA_TO_LOCATION))
                viewToLocation.setLocation((Location) intent.getSerializableExtra(INTENT_EXTRA_TO_LOCATION));
            if (intent.hasExtra(INTENT_EXTRA_VIA_LOCATION))
                viewViaLocation.setLocation((Location) intent.getSerializableExtra(INTENT_EXTRA_VIA_LOCATION));
            if (intent.hasExtra(INTENT_EXTRA_TIME_SPEC))
                time = (TimeSpec) intent.getSerializableExtra(INTENT_EXTRA_TIME_SPEC);

            command = (Command) intent.getSerializableExtra(INTENT_EXTRA_COMMAND);
        }

        boolean haveNonDefaultProducts = initProductToggles();
        expandForm(haveNonDefaultProducts || viewViaLocation.getText() != null);

        if (command != null) {
            final AutoCompleteLocationsHandler autoCompleteLocationsHandler = new AutoCompleteLocationsHandler(
                    autoCompleteLocationAdapter, backgroundHandler,
                    getProductToggles());
            autoCompleteLocationsHandler.addJob(command.fromText, viewFromLocation);
            autoCompleteLocationsHandler.addJob(command.toText, viewToLocation);
            autoCompleteLocationsHandler.addJob(command.viaText, viewViaLocation);
            time = command.time;
            autoCompleteLocationsHandler.start(result -> {
                if (result.success)
                    handleGo();
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (linkArgs != null && network != null) {
            try {
                final String action = linkArgs[0];
                final NetworkProvider provider = NetworkProviderFactory.provider(network);
                final Consumer<Trip> startTripDetailsActivity = (trip) -> {
                    if (trip != null) {
                        TripDetailsActivity.start(DirectionsActivity.this,
                                network, trip,
                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        finish();
                    }
                };
                if (LINK_IDENTIFIER_TRIP.equals(action) && linkArgs.length == 2) {
                    if (provider.hasCapabilities(Capability.TRIP_RELOAD)) {
                        final byte[] bytes = Objects.uncompressFromString(linkArgs[1]);
                        final MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes);
                        final TripRef tripRef = provider.unpackTripRefFromMessage(unpacker);
                        unpacker.close();
                        loadTripByTripRef(tripRef, startTripDetailsActivity);
                    }
                } else if (LINK_IDENTIFIER_SHARE_TRIP.equals(action) && linkArgs.length == 2) {
                    if (provider.hasCapabilities(Capability.TRIP_SHARING)) {
                        final byte[] bytes = Objects.uncompressFromString(linkArgs[1]);
                        final MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(bytes);
                        final TripShare tripShare = provider.unpackTripShareFromMessage(unpacker);
                        unpacker.close();
                        loadTripByTripShare(tripShare, startTripDetailsActivity);
                    }
                }
            } catch (Exception e) {
                log.error("cannot execute link command {}", linkArgs, e);
                DialogBuilder.warn(this, R.string.directions_alert_bad_link_title)
                        .setMessage(R.string.directions_alert_bad_link_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();

        boolean haveNonDefaultProducts = initProductToggles();

        // can do directions?
        final NetworkProvider networkProvider = network != null ? NetworkProviderFactory.provider(network) : null;
        final boolean hasDirectionsCap = networkProvider != null && networkProvider.hasCapabilities(Capability.TRIPS);
        viewFromLocation.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        viewViaLocation.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        viewToLocation.setImeOptions(hasDirectionsCap ? EditorInfo.IME_ACTION_GO : EditorInfo.IME_ACTION_NONE);
        viewGo.setEnabled(hasDirectionsCap);

        viewQueryHistoryList.setVisibility(hasDirectionsCap ? View.VISIBLE : View.GONE);
        viewQueryHistoryEmpty.setVisibility(
                hasDirectionsCap && queryHistoryListAdapter.getItemCount() == 0 ? View.VISIBLE : View.INVISIBLE);
        viewQueryMissingCapability.setVisibility(hasDirectionsCap ? View.GONE : View.VISIBLE);

        // regular refresh
        tickReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateGUI();
            }
        };
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        expandForm(haveNonDefaultProducts || viewViaLocation.getLocation() != null);

        setActionBarSecondaryTitleFromNetwork();
        updateGUI();
        updateMap();
        updateFragments();
    }

    @Override
    protected void onChangeNetwork(final NetworkId network) {
        autoCompleteLocationAdapter = new AutoCompleteLocationAdapter(this, network);

        viewFromLocation.setAdapter(autoCompleteLocationAdapter);
        viewFromLocation.reset();
        viewViaLocation.setAdapter(autoCompleteLocationAdapter);
        viewViaLocation.reset();
        viewToLocation.setAdapter(autoCompleteLocationAdapter);
        viewToLocation.reset();

        viewBike.setChecked(false);

        boolean haveNonDefaultProducts = initProductToggles();
        expandForm(haveNonDefaultProducts);

        queryHistoryListAdapter.close();
        queryHistoryListAdapter = new QueryHistoryAdapter(this, network, this, this);
        viewQueryHistoryList.setAdapter(queryHistoryListAdapter);

        locationSelector.setNetwork(network);

        updateGUI();
        setActionBarSecondaryTitleFromNetwork();
    }

    private boolean initProductToggles() {
        final Collection<Product> defaultProducts = loadProductFilter();
        for (final ToggleImageButton view : viewProductToggles) {
            final Product product = Product.fromCode(((String) view.getTag()).charAt(0));
            final boolean checked = defaultProducts.contains(product);
            view.setChecked(checked);
        }
        return productsAreNetworkDefault(defaultProducts);
    }

    private Set<Product> getProductToggles() {
        final Set<Product> products = new HashSet<>();
        for (final ToggleImageButton view : viewProductToggles)
            if (view.isChecked())
                products.add(Product.fromCode(((String) view.getTag()).charAt(0)));
        return products;
    }

    private boolean productsAreNetworkDefault(final Collection<Product> products) {
        Collection<Product> networkDefaultProducts = getNetworkDefaultProducts();
        return products.size() != networkDefaultProducts.size() || !products.containsAll(networkDefaultProducts);
    }

    @Override
    protected void onPause() {
        saveProductFilter(getProductToggles());

        if (tickReceiver != null) {
            unregisterReceiver(tickReceiver);
            tickReceiver = null;
        }

        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable("time", time);
    }

    private void restoreInstanceState(final Bundle savedInstanceState) {
        time = (TimeSpec) savedInstanceState.getSerializable("time");
    }

    @Override
    protected void onDestroy() {
        backgroundThread.getLooper().quit();

        queryHistoryListAdapter.close();
        unregisterReceiver(connectivityReceiver);

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(final Configuration config) {
        super.onConfigurationChanged(config);

        updateFragments();
    }

    @Override
    public void onBackPressed() {
        if (isNavigationOpen())
            closeNavigation();
        else
            super.onBackPressed();
    }

    private void requestFocusFirst() {
        if (!saneLocation(viewFromLocation.getLocation(), true))
            viewFromLocation.requestFocus();
        else if (!saneLocation(viewToLocation.getLocation(), true))
            viewToLocation.requestFocus();
        else
            viewGo.requestFocus();
    }

    private void updateFragments() {
        updateFragments(R.id.navigation_drawer_layout, R.id.directions_map_fragment);
    }

    private void updateGUI() {
        viewFromLocation.setHint(R.string.directions_from);
        viewViaLocation.setHint(R.string.directions_via);
        viewToLocation.setHint(R.string.directions_to);

        viewTimeDepArr
                .setText(time.depArr == DepArr.DEPART ? R.string.directions_time_dep : R.string.directions_time_arr);

        if (time == null) {
            viewTime1.setVisibility(View.GONE);
            viewTime2.setVisibility(View.GONE);
        } else if (time instanceof TimeSpec.Absolute) {
            final long now = System.currentTimeMillis();
            final long t = ((TimeSpec.Absolute) time).timeMs;
            viewTime1.setVisibility(View.VISIBLE);
            viewTime1.setOnClickListener(dateClickListener);
            viewTime1.setText(Formats.formatDate(this, now, t));
            viewTime2.setVisibility(View.VISIBLE);
            viewTime2.setOnClickListener(timeClickListener);
            viewTime2.setText(Formats.formatTime(this, t));
        } else if (time instanceof TimeSpec.Relative) {
            final long diff = ((TimeSpec.Relative) time).diffMs;
            viewTime1.setVisibility(View.VISIBLE);
            viewTime1
                    .setText(diff > 0 ? getString(R.string.directions_time_relative, Formats.formatTimeDiff(this, diff))
                            : getString(R.string.time_now));
            viewTime1.setOnClickListener(diffClickListener);
            viewTime2.setVisibility(View.GONE);
        }
    }

    private final OnClickListener dateClickListener = v -> {
        final Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(((TimeSpec.Absolute) time).timeMs);
        final int year = calendar.get(Calendar.YEAR);
        final int month = calendar.get(Calendar.MONTH);
        final int day = calendar.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(DirectionsActivity.this, 0, (view, year1, month1, day1) -> {
            calendar.set(Calendar.YEAR, year1);
            calendar.set(Calendar.MONTH, month1);
            calendar.set(Calendar.DAY_OF_MONTH, day1);
            time = new TimeSpec.Absolute(time.depArr, calendar.getTimeInMillis());
            updateGUI();
        }, year, month, day).show();
    };

    private final OnClickListener timeClickListener = v -> {
        final Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(((TimeSpec.Absolute) time).timeMs);
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int minute = calendar.get(Calendar.MINUTE);

        new TimePickerDialog(DirectionsActivity.this, 0, (view, hour1, minute1) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hour1);
            calendar.set(Calendar.MINUTE, minute1);
            time = new TimeSpec.Absolute(time.depArr, calendar.getTimeInMillis());
            updateGUI();
        }, hour, minute, DateFormat.is24HourFormat(DirectionsActivity.this)).show();
    };

    private final OnClickListener diffClickListener = v -> handleDiffClick();

    private void handleDiffClick() {
        final int[] relativeTimeValues = getResources().getIntArray(R.array.directions_set_time_relative);
        final String[] relativeTimeStrings = new String[relativeTimeValues.length + 1];
        relativeTimeStrings[relativeTimeValues.length] = getString(R.string.directions_set_time_relative_fixed);
        for (int i = 0; i < relativeTimeValues.length; i++) {
            if (relativeTimeValues[i] == 0)
                relativeTimeStrings[i] = getString(R.string.time_now);
            else
                relativeTimeStrings[i] = getString(R.string.directions_time_relative,
                        Formats.formatTimeDiff(this, relativeTimeValues[i] * DateUtils.MINUTE_IN_MILLIS));
        }
        final DialogBuilder builder = DialogBuilder.get(this);
        builder.setTitle(R.string.directions_set_time_relative_prompt);
        builder.setItems(relativeTimeStrings, (dialog, which) -> {
            if (which < relativeTimeValues.length) {
                final int mins = relativeTimeValues[which];
                time = new TimeSpec.Relative(mins * DateUtils.MINUTE_IN_MILLIS);
            } else {
                time = new TimeSpec.Absolute(DepArr.DEPART, time.timeInMillis());
            }
            updateGUI();
        });
        builder.show();
    }

    @Override
    public void onLocationSequenceSelected(final List<Location> locations, final boolean longHold, View lastView) {
        final boolean doGo;
        final int numLocations = locations.size();
        if (numLocations == 0) {
            doGo = false;
        } else if (numLocations == 1) {
            // single location clicked
            final Location location = locations.get(0);
            if (viewFromLocation.getLocation() == null) {
                viewFromLocation.setLocation(location);
                doGo = false;
            } else if (viewViaLocation.getLocation() == null && viewViaLocation.getVisibility() == View.VISIBLE) {
                viewViaLocation.setLocation(location);
                viewViaLocation.setVisibility(View.VISIBLE);
                doGo = false;
            } else {
                viewToLocation.setLocation(location);
                doGo = true;
            }
        } else if (numLocations == 2) {
            // 2 locations, from-to
            viewFromLocation.setLocation(locations.get(0));
            viewViaLocation.setLocation(null);
            viewViaLocation.setVisibility(View.GONE);
            viewToLocation.setLocation(locations.get(1));
            doGo = !longHold;
        } else {
            // >= 3, from-via-to
            viewFromLocation.setLocation(locations.get(0));
            viewViaLocation.setLocation(locations.get(1));
            viewViaLocation.setVisibility(View.VISIBLE);
            viewToLocation.setLocation(locations.get(numLocations - 1));
            doGo = !longHold;
        }

        if (doGo)
            handleGo();
        else
            locationSelector.clearSelection();
    }

    @Override
    public void onSingleLocationSelected(final Location location, final boolean longHold, View selectedView) {
        final PopupMenu contextMenu = new PopupMenu(this, selectedView);
        final MenuInflater inflater = contextMenu.getMenuInflater();
        final Menu menu = contextMenu.getMenu();
        inflater.inflate(R.menu.directions_location_selector_context, menu);
        contextMenu.setOnDismissListener((popupMenu) -> {
            locationSelector.clearSelection();
        });
        contextMenu.setOnMenuItemClickListener((menuItem) -> {
            locationSelector.clearSelection();
            final int itemId = menuItem.getItemId();
            if (itemId == R.id.directions_location_selector_context_delete) {
                locationSelector.removeLocation(location);
                locationSelector.persist();
                return true;
            }

            final Date departureDate =
                    (time == null || (time instanceof TimeSpec.Relative && ((TimeSpec.Relative) time).diffMs == 0))
                            ? null
                            : new Date(time.timeInMillis());

            if (itemId == R.id.directions_location_selector_context_show_departures) {
                StationDetailsActivity.start(this, network, location, departureDate);
            } else if (itemId == R.id.directions_location_selector_context_nearby_departures) {
                StationsActivity.start(this, network, location, departureDate);
            }
            return true;
        });
        contextMenu.show();
    }

    private void updateMap() {
        mapView.removeAllViews();
        mapView.setFromViaToAware(new FromViaToAware() {
            public Point getFrom() {
                final Location from = viewFromLocation.getLocation();
                if (from == null || !from.hasCoord())
                    return null;
                return from.coord;
            }

            public Point getVia() {
                final Location via = viewViaLocation.getLocation();
                if (via == null || !via.hasCoord() || viewViaLocation.getVisibility() != View.VISIBLE)
                    return null;
                return via.coord;
            }

            public Point getTo() {
                final Location to = viewToLocation.getLocation();
                if (to == null || !to.hasCoord())
                    return null;
                return to.coord;
            }
        });
        mapView.zoomToAll();
    }

    private void expandForm(final boolean expanded) {
        if (expanded) {
            buttonExpand.setChecked(true);
            initLayoutTransitions(true);

            final NetworkProvider networkProvider = network != null ? NetworkProviderFactory.provider(network) : null;

            viewViaLocation.setVisibility(networkProvider != null && networkProvider.hasCapabilities(NetworkProvider.Capability.TRIPS_VIA) ?
                    View.VISIBLE : View.GONE);
            viewProducts.setVisibility(View.VISIBLE);
            if (networkProvider != null && networkProvider.hasCapabilities(Capability.BIKE_OPTION))
                viewBike.setVisibility(View.VISIBLE);
        } else {
            buttonExpand.setChecked(false);
            initLayoutTransitions(false);

            viewViaLocation.setVisibility(View.GONE);
            viewProducts.setVisibility(View.GONE);
            viewBike.setVisibility(View.GONE);
        }
    }

    private void initLayoutTransitions() {
        final LayoutTransition lt1 = new LayoutTransition();
        lt1.enableTransitionType(LayoutTransition.CHANGING);
        ((ViewGroup) findViewById(R.id.directions_list_layout)).setLayoutTransition(lt1);

        final LayoutTransition lt2 = new LayoutTransition();
        lt2.enableTransitionType(LayoutTransition.CHANGING);
        ((ViewGroup) findViewById(R.id.directions_content_layout)).setLayoutTransition(lt2);

        final LayoutTransition lt3 = new LayoutTransition();
        lt3.enableTransitionType(LayoutTransition.CHANGING);
        ((ViewGroup) findViewById(R.id.directions_form)).setLayoutTransition(lt3);

        final LayoutTransition lt4 = new LayoutTransition();
        ((ViewGroup) findViewById(R.id.directions_form_location_group)).setLayoutTransition(lt4);
    }

    private void initLayoutTransitions(final boolean expand) {
        ((ViewGroup) findViewById(R.id.directions_list_layout)).getLayoutTransition()
                .setStartDelay(LayoutTransition.CHANGING, expand ? 0 : 300);
        ((ViewGroup) findViewById(R.id.directions_content_layout)).getLayoutTransition()
                .setStartDelay(LayoutTransition.CHANGING, expand ? 0 : 300);
    }

    public void onEntryClick(final int adapterPosition, final Location from, final Location to, final Location via) {
        handleReuseQuery(from, to, via);
        queryHistoryListAdapter.setSelectedEntry(queryHistoryListAdapter.getItemId(adapterPosition));
    }

    public void onSavedTripClick(final int adapterPosition, final byte[] serializedSavedTrip) {
        handleShowSavedTrip(serializedSavedTrip);
    }

    public boolean onQueryHistoryContextMenuItemClick(final int adapterPosition, final Location from, final Location to,
            @Nullable final byte[] serializedSavedTrip, final int menuItemId,
            @Nullable final Location menuItemLocation) {
        if (menuItemId == R.id.directions_query_history_context_show_trip) {
            handleShowSavedTrip(serializedSavedTrip);
            return true;
        } else if (menuItemId == R.id.directions_query_history_context_remove_trip) {
            queryHistoryListAdapter.setSavedTrip(adapterPosition, 0, 0, null);
            return true;
        } else if (menuItemId == R.id.directions_query_history_context_remove_entry) {
            queryHistoryListAdapter.removeEntry(adapterPosition);
            return true;
        } else if (menuItemId == R.id.directions_query_history_context_add_favorite) {
            queryHistoryListAdapter.setIsFavorite(adapterPosition, true);
            return true;
        } else if (menuItemId == R.id.directions_query_history_context_remove_favorite) {
            queryHistoryListAdapter.setIsFavorite(adapterPosition, false);
            return true;
        } else if (menuItemId == R.id.directions_query_history_location_context_details && menuItemLocation != null) {
            StationDetailsActivity.start(this, network, menuItemLocation);
            return true;
        } else if (menuItemId == R.id.directions_query_history_location_context_add_favorite
                && menuItemLocation != null) {
            FavoriteUtils.persist(getContentResolver(), FavoriteStationsProvider.TYPE_FAVORITE, network,
                    menuItemLocation);
            new Toast(DirectionsActivity.this).longToast(R.string.toast_add_favorite,
                    menuItemLocation.uniqueShortName());
            queryHistoryListAdapter.notifyDataSetChanged();
            return true;
        } else if (menuItemId == R.id.directions_query_history_location_context_launcher_shortcut
                && menuItemLocation != null) {
            StationContextMenu.createLauncherShortcutDialog(DirectionsActivity.this, network, menuItemLocation).show();
            return true;
        } else {
            return false;
        }
    }

    private void handleReuseQuery(final Location from, final Location to, final Location via) {
        viewFromLocation.setLocation(from);
        viewToLocation.setLocation(to);
        viewViaLocation.setLocation(via);
        quickReturnView.setTranslationY(0); // show
        expandForm(productsAreNetworkDefault(getProductToggles()) || via != null);
    }

    private void handleShowSavedTrip(final byte[] serializedTrip) {
        final Trip trip = (Trip) Objects.deserialize(serializedTrip);
        if (trip == null) {
            new Toast(this).longToast(R.string.directions_query_history_invalid_blob);
            return;
        }
        loadTripByTripRef(trip.tripRef, (loadedTrip) -> {
            final Trip useTrip = loadedTrip != null ? loadedTrip : trip;
            TripDetailsActivity.start(DirectionsActivity.this, network, useTrip);
        });
    }

    private void loadTripByTripRef(final TripRef tripRef, final Consumer<Trip> tripHandler) {
        if (tripRef == null) {
            tripHandler.accept(null);
            return;
        }
        final NetworkProvider networkProvider = NetworkProviderFactory.provider(tripRef.network);
        if (!networkProvider.hasCapabilities(Capability.TRIP_RELOAD)) {
            tripHandler.accept(null);
            return;
        }
        queryTripsRunnable = new MyQueryTripsRunnable(networkProvider, tripRef, getTripOptionsFromPrefs()) {
            @Override
            protected void onResultOk(final QueryTripsResult result, final TripRequestData reloadRequestData) {
                final List<Trip> trips = result.trips;
                final Trip useTrip = (trips != null && trips.size() == 1) ? trips.get(0) : null;
                tripHandler.accept(useTrip);
            }

            @Override
            protected void onResultFailed(final QueryTripsResult result, final TripRequestData reloadRequestData) {
                tripHandler.accept(null);
            }
        };
        backgroundHandler.post(queryTripsRunnable);
    }

    private void loadTripByTripShare(final TripShare tripShare, final Consumer<Trip> tripHandler) {
        if (tripShare == null) {
            tripHandler.accept(null);
            return;
        }
        final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
        if (!networkProvider.hasCapabilities(Capability.TRIP_SHARING)) {
            tripHandler.accept(null);
            return;
        }
        queryTripsRunnable = new MyQueryTripsRunnable(networkProvider, tripShare, getTripOptionsFromPrefs()) {
            @Override
            protected void onResultOk(final QueryTripsResult result, final TripRequestData reloadRequestData) {
                final List<Trip> trips = result.trips;
                final Trip useTrip = (trips != null && trips.size() == 1) ? trips.get(0) : null;
                tripHandler.accept(useTrip);
            }

            @Override
            protected void onResultFailed(final QueryTripsResult result, final TripRequestData reloadRequestData) {
                tripHandler.accept(null);
            }
        };
        backgroundHandler.post(queryTripsRunnable);
    }

    private boolean saneLocation(final @Nullable Location location, final boolean allowIncompleteAddress) {
        if (location == null)
            return false;
        if (location.type == LocationType.ANY && location.name == null)
            return false;
        if (!allowIncompleteAddress && location.type == LocationType.ADDRESS && !location.hasCoord()
                && location.name == null)
            return false;

        return true;
    }

    private void handleGo() {
        locationSelector.persist();
        locationSelector.clearSelection();

        final Location from = viewFromLocation.getLocation();
        if (!saneLocation(from, false)) {
            new Toast(this).longToast(R.string.directions_message_choose_from);
            viewFromLocation.requestFocus();
            return;
        }

        Location via = viewViaLocation.getLocation();
        if (!saneLocation(via, false))
            via = null;

        final Location to = viewToLocation.getLocation();
        if (!saneLocation(to, false)) {
            new Toast(this).longToast(R.string.directions_message_choose_to);
            viewToLocation.requestFocus();
            return;
        }

        final Set<Product> products = getProductToggles();

        final Set<TripFlag> flags;
        if (viewBike.isChecked()) {
            flags = new HashSet<>();
            flags.add(TripFlag.BIKE);
        } else {
            flags = null;
        }

        final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
        final TripOptions options = new TripOptions(products, prefsGetOptimizeTrip(), prefsGetWalkSpeed(),
                prefsGetMinTranfserTime(), prefsGetAccessibility(), flags);
        queryTripsRunnable = new MyQueryTripsRunnable(networkProvider, from, via, to, time, options) {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                viewGo.setClickable(false);
            }

            @Override
            protected void onPostExecute() {
                super.onPostExecute();
                viewGo.setClickable(true);
            }

            @Override
            protected void onResultOk(final QueryTripsResult result, final TripRequestData reloadRequestData) {
                final Uri historyUri;
                if (result.from != null && result.from.name != null && result.to != null && result.to.name != null)
                    historyUri = queryHistoryListAdapter.putEntry(result.from, result.to, result.via);
                else
                    historyUri = null;

                renderConfig.referenceTime = time;
                TripsOverviewActivity.start(DirectionsActivity.this,
                        network, time.depArr, result, historyUri, reloadRequestData,
                        renderConfig);
            }

            @Override
            protected void onResultFailed(final QueryTripsResult result, final TripRequestData reloadRequestData) {
            }
        };
        backgroundHandler.post(queryTripsRunnable);
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        switch (id) {
        case DIALOG_CLEAR_HISTORY:
            final DialogBuilder builder = DialogBuilder.get(this);
            builder.setMessage(R.string.directions_query_history_clear_confirm_message);
            builder.setPositiveButton(R.string.directions_query_history_clear_confirm_button_clear,
                    (dialog, which) -> {
                        queryHistoryListAdapter.removeAllEntries();
                        viewFromLocation.reset();
                        viewViaLocation.reset();
                        viewToLocation.reset();
                    });
            builder.setNegativeButton(R.string.directions_query_history_clear_confirm_button_dismiss, null);
            return builder.create();
        }

        return super.onCreateDialog(id);
    }

    private void resultPickContact(final Uri contentUri, final LocationView targetLocationView) {
        final Cursor c = managedQuery(contentUri, null, null, null, null);
        if (c.moveToFirst()) {
            final String data = c
                    .getString(c.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS));
            final Location location = new Location(LocationType.ADDRESS, null, null, data.replace("\n", " "));
            targetLocationView.setLocation(location);
            log.info("Picked {} from contacts", location);
            requestFocusFirst();
        }
    }

    private void resultPickStation(final Uri contentUri, final LocationView targetLocationView) {
        final Cursor c = managedQuery(contentUri, null, null, null, null);
        if (c.moveToFirst()) {
            final Location location = FavoriteStationsProvider.getLocation(c);
            targetLocationView.setLocation(location);
            log.info("Picked {} from station favorites", location);
            requestFocusFirst();
        }
    }

    private class AmbiguousLocationAdapter extends ArrayAdapter<Location> {
        public AmbiguousLocationAdapter(final Context context, final List<Location> autocompletes) {
            super(context, R.layout.directions_location_dropdown_entry, autocompletes);
        }

        @Override
        public View getView(final int position, View row, final ViewGroup parent) {
            row = super.getView(position, row, parent);

            final Location location = getItem(position);
            ((LocationTextView) row).setLocation(location);

            return row;
        }
    }

    private static final class QuickReturnBehavior extends CoordinatorLayout.Behavior<View> {
        @Override
        public boolean onStartNestedScroll(final CoordinatorLayout coordinatorLayout, final View child,
                final View directTargetChild, final View target, final int nestedScrollAxes, final int type) {
            return (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
        }

        @Override
        public void onNestedScroll(final CoordinatorLayout coordinatorLayout, final View child, final View target,
                final int dxConsumed, final int dyConsumed, final int dxUnconsumed, final int dyUnconsumed,
                final int type) {
            child.setTranslationY(Floats.constrainToRange(child.getTranslationY() - dyConsumed, -child.getHeight(), 0));
        }
    }

    public abstract class MyQueryTripsRunnable extends QueryTripsRunnable {
        public MyQueryTripsRunnable(
                final NetworkProvider networkProvider,
                final Location from, final Location via, final Location to, final TimeSpec time,
                final TripOptions options) {
            super(DirectionsActivity.this.getResources(),
                    DirectionsActivity.this.getProgressDialog(),
                    DirectionsActivity.this.handler,
                    networkProvider, from, via, to, time, options);
        }

        public MyQueryTripsRunnable(
                final NetworkProvider networkProvider,
                final TripRef tripRef,
                final TripOptions options) {
            super(DirectionsActivity.this.getResources(),
                    DirectionsActivity.this.getProgressDialog(),
                    DirectionsActivity.this.handler,
                    networkProvider, tripRef, options);
        }

        public MyQueryTripsRunnable(
                final NetworkProvider networkProvider,
                final TripShare tripShare,
                final TripOptions options) {
            super(DirectionsActivity.this.getResources(),
                    DirectionsActivity.this.getProgressDialog(),
                    DirectionsActivity.this.handler,
                    networkProvider, tripShare, options);
        }

        @Override
        protected void onPostExecute() {
            if (!isDestroyed())
                progressDialog.dismiss();
        }

        protected abstract void onResultOk(final QueryTripsResult result, TripRequestData reloadRequestData);

        protected abstract void onResultFailed(final QueryTripsResult result, TripRequestData reloadRequestData);

        @Override
        protected void onResult(final QueryTripsResult result, TripRequestData reloadRequestData) {
            if (result.status == QueryTripsResult.Status.OK) {
                log.debug("Got {}", result.toShortString());
                onResultOk(result, reloadRequestData);
                return;
            }
            if (result.status == QueryTripsResult.Status.UNKNOWN_FROM) {
                new Toast(DirectionsActivity.this).longToast(R.string.directions_message_unknown_from);
            } else if (result.status == QueryTripsResult.Status.UNKNOWN_VIA) {
                new Toast(DirectionsActivity.this).longToast(R.string.directions_message_unknown_via);
            } else if (result.status == QueryTripsResult.Status.UNKNOWN_TO) {
                new Toast(DirectionsActivity.this).longToast(R.string.directions_message_unknown_to);
            } else if (result.status == QueryTripsResult.Status.UNKNOWN_LOCATION) {
                new Toast(DirectionsActivity.this).longToast(R.string.directions_message_unknown_location);
            } else if (result.status == QueryTripsResult.Status.TOO_CLOSE) {
                new Toast(DirectionsActivity.this).longToast(R.string.directions_message_too_close);
            } else if (result.status == QueryTripsResult.Status.UNRESOLVABLE_ADDRESS) {
                new Toast(DirectionsActivity.this).longToast(R.string.directions_message_unresolvable_address);
            } else if (result.status == QueryTripsResult.Status.NO_TRIPS) {
                new Toast(DirectionsActivity.this).longToast(R.string.directions_message_no_trips);
            } else if (result.status == QueryTripsResult.Status.INVALID_DATE) {
                new Toast(DirectionsActivity.this).longToast(R.string.directions_message_invalid_date);
            } else if (result.status == QueryTripsResult.Status.SERVICE_DOWN) {
                networkProblem();
            } else if (result.status == QueryTripsResult.Status.AMBIGUOUS) {
                final List<Location> autocompletes = result.ambiguousFrom != null ? result.ambiguousFrom
                        : (result.ambiguousVia != null ? result.ambiguousVia : result.ambiguousTo);
                if (autocompletes != null) {
                    final DialogBuilder builder = DialogBuilder.get(DirectionsActivity.this);
                    builder.setTitle(getString(R.string.ambiguous_address_title));
                    builder.setAdapter(new AmbiguousLocationAdapter(DirectionsActivity.this, autocompletes),
                            (dialog, which) -> {
                                final LocationView locationView = result.ambiguousFrom != null
                                        ? viewFromLocation
                                        : (result.ambiguousVia != null ? viewViaLocation : viewToLocation);
                                locationView.setLocation(autocompletes.get(which));
                                viewGo.performClick();
                            });
                    builder.create().show();
                } else {
                    new Toast(DirectionsActivity.this).longToast(R.string.directions_message_ambiguous_location);
                }
            }
            onResultFailed(result, reloadRequestData);
        }

        @Override
        protected void onRedirect(final HttpUrl url) {
            final DialogBuilder builder = DialogBuilder.warn(DirectionsActivity.this,
                    R.string.directions_alert_redirect_title);
            builder.setMessage(getString(R.string.directions_alert_redirect_message, url.host()));
            builder.setPositiveButton(R.string.directions_alert_redirect_button_follow,
                    (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))));
            builder.setNegativeButton(R.string.directions_alert_redirect_button_dismiss, null);
            builder.show();
        }

        @Override
        protected void onBlocked(final HttpUrl url) {
            final DialogBuilder builder = DialogBuilder.warn(DirectionsActivity.this,
                    R.string.directions_alert_blocked_title);
            builder.setMessage(getString(R.string.directions_alert_blocked_message, url.host()));
            builder.setPositiveButton(R.string.directions_alert_blocked_button_retry,
                    (dialog, which) -> viewGo.performClick());
            builder.setNegativeButton(R.string.directions_alert_blocked_button_dismiss, null);
            builder.show();
        }

        @Override
        protected void onInternalError(final HttpUrl url) {
            final DialogBuilder builder = DialogBuilder.warn(DirectionsActivity.this,
                    R.string.directions_alert_internal_error_title);
            builder.setMessage(getString(R.string.directions_alert_internal_error_message, url.host()));
            builder.setPositiveButton(R.string.directions_alert_internal_error_button_retry,
                    (dialog, which) -> viewGo.performClick());
            builder.setNegativeButton(R.string.directions_alert_internal_error_button_dismiss, null);
            builder.show();
        }

        @Override
        protected void onSSLException(final SSLException x) {
            final DialogBuilder builder = DialogBuilder.warn(DirectionsActivity.this,
                    R.string.directions_alert_ssl_exception_title);
            builder.setMessage(getString(R.string.directions_alert_ssl_exception_message,
                    Throwables.getRootCause(x).toString()));
            builder.setNeutralButton(R.string.directions_alert_ssl_exception_button_dismiss, null);
            builder.show();
        }

        private void networkProblem() {
            final DialogBuilder builder = DialogBuilder.warn(DirectionsActivity.this,
                    R.string.alert_network_problem_title);
            builder.setMessage(R.string.alert_network_problem_message);
            builder.setPositiveButton(R.string.alert_network_problem_retry, (dialog, which) -> {
                dialog.dismiss();
                viewGo.performClick();
            });
            builder.setOnCancelListener(dialog -> dialog.dismiss());
            builder.show();
        }
    }

    private ProgressDialog getProgressDialog() {
        final ProgressDialog progressDialog = ProgressDialog.show(this, null,
                getString(R.string.directions_query_progress), true, true, dialog -> {
                    if (queryTripsRunnable != null)
                        queryTripsRunnable.cancel();
                });
        progressDialog.setCanceledOnTouchOutside(false);
        return progressDialog;
    }
}
