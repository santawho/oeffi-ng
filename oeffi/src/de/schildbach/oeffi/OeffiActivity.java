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

package de.schildbach.oeffi;

import android.Manifest;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.ActivityManager.TaskDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.directions.navigation.NavigationNotification;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.network.NetworkResources;
import de.schildbach.oeffi.plans.PlansPickerActivity;
import de.schildbach.oeffi.preference.AboutFragment;
import de.schildbach.oeffi.preference.DonateFragment;
import de.schildbach.oeffi.preference.PreferenceActivity;
import de.schildbach.oeffi.stations.FavoriteStationsActivity;
import de.schildbach.oeffi.stations.StationsActivity;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.ErrorReporter;
import de.schildbach.oeffi.util.NavigationMenuAdapter;
import de.schildbach.oeffi.util.SpeechInput;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.oeffi.util.ZoomControls;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.ResultHeader;
import de.schildbach.pte.dto.TripOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class OeffiActivity extends ComponentActivity {
    protected static final String INTENT_EXTRA_LINK_ARGS = OeffiActivity.class.getName() + ".link_args";
    protected static final String INTENT_EXTRA_NETWORK_NAME = OeffiActivity.class.getName() + ".network";

    protected static final String PREFS_KEY_VOICE_CONTROL_MODE = "user_interface_voice_control_mode";
    protected static final String PREFS_KEY_VOICE_TOGGLE_STATE = "user_interface_voice_control_toggle";
    public static final String VOICE_CONTROL_OPTION_ALWAYS = "always";
    public static final String VOICE_CONTROL_OPTION_TOGGLE = "toggle";
    public static final String VOICE_CONTROL_OPTION_TRIGGER = "trigger";
    public static final String VOICE_CONTROL_OPTION_HIDDEN = "hidden";
    public static final String VOICE_CONTROL_OPTION_OFF = "off";

    protected Application application;
    private final Handler handler = new Handler();

    protected SharedPreferences prefs;
    protected NetworkId network;
    protected String[] linkArgs;
    protected Set<Product> savedProducts;

    protected boolean isPortrait;
    private boolean mapEnabled = false;
    private OeffiMapView mapView;
    private DrawerLayout navigationDrawerLayout;
    private MenuProvider navigationDrawerMenuProvider;
    private View navigationDrawerFooterView;

    protected LocationManager locationManager;

    protected Logger log;

    protected OeffiActivity() {
         log = LoggerFactory.getLogger(this.getClass());
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        this.application = (Application) getApplication();
        SplashScreen.installSplashScreen(this);

        this.prefs = Application.getInstance().getSharedPreferences();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        final Intent intent = getIntent();
        linkArgs = intent.getStringArrayExtra(INTENT_EXTRA_LINK_ARGS);
        final String networkName = intent.getStringExtra(INTENT_EXTRA_NETWORK_NAME);

        if (network == null) {
            if (networkName != null) {
                try {
                    network = NetworkId.valueOf(networkName);
                } catch (IllegalArgumentException e) {
                    log.warn("ignoring bad network from intent: {}", networkName);
                }
            }
            if (network == null)
                network = prefsGetNetworkId();
        }
        ErrorReporter.getInstance().setNetworkId(network);
        savedProducts = loadProductFilter();

        EdgeToEdge.enable(this, Constants.STATUS_BAR_STYLE);
        super.onCreate(savedInstanceState);

        ErrorReporter.getInstance().check(this, applicationVersionCode(), application.okHttpClient());
    }

    @Override
    public void setContentView(final int layoutResID) {
        setContentView(layoutResID, true);
    }

    public View setContentView(final int layoutResID, final boolean showNavigation) {
        isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        // called in the final phase of onCreate by sub-classes
        navigationDrawerLayout = null;
        super.setContentView(layoutResID);

        final View contentView = findViewById(android.R.id.content);
        setupMapView(contentView);

        // chance to perform general setup
        if (showNavigation)
            initNavigation();
        else
            hideNavigation();

        return contentView;
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateFragments();
        updateNavigation();
    }

    @Override
    protected void onResume() {
        checkChangeNetwork();
        updateNavigation();

        super.onResume();
        if (mapView != null)
            mapView.onResume();
    }

    @Override
    protected void onPause() {
        savedProducts = loadProductFilter();
        if (mapView != null)
            mapView.onPause();
        super.onPause();
    }

    private void hideNavigation() {
        final DrawerLayout drawerLayout = findViewById(R.id.navigation_drawer_layout);
        if (drawerLayout != null)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    private void initNavigation() {
        if (navigationDrawerLayout != null)
            return;

        navigationDrawerLayout = findViewById(R.id.navigation_drawer_layout);
        if (navigationDrawerLayout == null)
            return;

        final View statusBarOffsetView = navigationDrawerLayout.findViewById(R.id.navigation_drawer_status_bar_offset);
        if (statusBarOffsetView != null) {
            final Resources res = getResources();
            final int statusHeight = res.getDimensionPixelSize(res.getIdentifier("status_bar_height", "dimen", "android"));
            statusBarOffsetView.setPadding(0, statusHeight, 0, 0);
        }

        navigationDrawerMenuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(final Menu menu, final MenuInflater inflater) {
                inflater.inflate(R.menu.global_options, menu);
            }

            @Override
            public void onPrepareMenu(final Menu menu) {
                final MenuItem stationsNearbyItem = menu.findItem(R.id.global_options_stations_nearby);
                stationsNearbyItem.setChecked(OeffiActivity.this instanceof StationsActivity);
                final MenuItem directionsItem = menu.findItem(R.id.global_options_directions);
                directionsItem.setChecked(OeffiActivity.this instanceof DirectionsActivity);
                final MenuItem plansItem = menu.findItem(R.id.global_options_plans);
                plansItem.setChecked(OeffiActivity.this instanceof PlansPickerActivity);

                final MenuItem aboutItem = menu.findItem(R.id.global_options_about);
                aboutItem.setTitle(getString(R.string.global_options_about_title, Application.getInstance().getAppName()));
            }

            @Override
            public boolean onMenuItemSelected(final MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.global_options_stations_favorites) {
                    if (OeffiActivity.this instanceof StationsActivity) {
                        FavoriteStationsActivity.start(OeffiActivity.this);
                    } else {
                        StationsActivity.start(OeffiActivity.this, true);
                        finish();
                        overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
                    }
                    return true;
                }

                if (itemId == R.id.global_options_stations_nearby) {
                    if (OeffiActivity.this instanceof StationsActivity)
                        return true;
                    StationsActivity.start(OeffiActivity.this, false);
                    finish();
                    overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
                    return true;
                }

                if (itemId == R.id.global_options_directions) {
                    if (OeffiActivity.this instanceof DirectionsActivity)
                        return true;
                    final Intent intent = new Intent(OeffiActivity.this, DirectionsActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    if (OeffiActivity.this instanceof StationsActivity)
                        overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                    else
                        overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
                    return true;
                }

                if (itemId == R.id.global_options_plans) {
                    if (OeffiActivity.this instanceof PlansPickerActivity)
                        return true;
                    final Intent intent = new Intent(OeffiActivity.this, PlansPickerActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                    return true;
                }

                if (itemId == R.id.global_options_donate) {
                    PreferenceActivity.start(OeffiActivity.this, DonateFragment.class);
                    return true;
                }

                if (itemId == R.id.global_options_report_bug) {
                    ErrorReporter.sendBugMail(OeffiActivity.this, application.packageInfo());
                    return true;
                }

                if (itemId == R.id.global_options_show_log) {
                    LogViewerActivity.start(OeffiActivity.this);
                    return true;
                }

                if (itemId == R.id.global_options_preferences) {
                    PreferenceActivity.start(OeffiActivity.this);
                    return true;
                }

                if (itemId == R.id.global_options_about) {
                    PreferenceActivity.start(OeffiActivity.this, AboutFragment.class);
                    return true;
                }

                if (itemId == R.id.global_options_extras) {
                    final View actionView = item.getActionView();
                    final PopupMenu popupMenu = new PopupMenu(OeffiActivity.this, actionView);
                    popupMenu.setGravity(Gravity.RIGHT);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        popupMenu.setForceShowIcon(true);
                    popupMenu.inflate(R.menu.global_extras);
                    popupMenu.setOnMenuItemClickListener(subItem -> {
                        navigationDrawerLayout.closeDrawers();
                        final int subItemId = subItem.getItemId();
                        if (subItemId == R.id.global_options_clear_navigation) {
                            NavigationNotification.removeAll(OeffiActivity.this);
                            return true;
                        }
                        if (subItemId == R.id.global_options_show_log) {
                            LogViewerActivity.start(OeffiActivity.this);
                            return true;
                        }
                        return false;
                    });
                    popupMenu.show();
                    return false;
                }

                return true;
            }
        };

        navigationDrawerFooterView = findViewById(R.id.navigation_drawer_footer);
        final View navigationDrawerFooterHeartView = findViewById(R.id.navigation_drawer_footer_heart);

        final AnimatorSet heartbeat = (AnimatorSet) AnimatorInflater.loadAnimator(OeffiActivity.this,
                R.animator.heartbeat);
        heartbeat.setTarget(navigationDrawerFooterHeartView);

        final NavigationMenuAdapter menuAdapter = new NavigationMenuAdapter(this,
                item -> {
                    if (navigationDrawerMenuProvider.onMenuItemSelected(item)) {
                        navigationDrawerLayout.closeDrawers();
                    }
                    return false;
                });
        final Menu menu = menuAdapter.getMenu();
        navigationDrawerMenuProvider.onCreateMenu(menu, getMenuInflater());
        navigationDrawerMenuProvider.onPrepareMenu(menu);

        final RecyclerView navigationDrawerListView = findViewById(R.id.navigation_drawer_list);
        navigationDrawerListView.setLayoutManager(new LinearLayoutManager(this));
        navigationDrawerListView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        navigationDrawerListView.setAdapter(menuAdapter);

        navigationDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            public void onDrawerOpened(final View drawerView) {
                handler.postDelayed(() -> heartbeat.start(), 2000);
                openDrawerForVoiceControl();
            }

            public void onDrawerClosed(final View drawerView) {
                Application.getInstance().getSpeechInput().stopSpeechRecognition();
            }

            public void onDrawerSlide(final View drawerView, final float slideOffset) {
            }

            public void onDrawerStateChanged(final int newState) {
            }
        });

        navigationDrawerFooterView.setOnClickListener(v -> {
            handler.removeCallbacksAndMessages(null);
            heartbeat.start();
        });

        getMyActionBar().setDrawer(v -> toggleNavigation());

        final View bottomOffset = findViewById(R.id.navigation_drawer_bottom_offset);
        ViewCompat.setOnApplyWindowInsetsListener(bottomOffset, (view, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            final ViewGroup.LayoutParams layoutParams = bottomOffset.getLayoutParams();
            layoutParams.height = insets.bottom;
            bottomOffset.setLayoutParams(layoutParams);
            return windowInsets;
        });

        updateNavigation();
    }

    private void prepareDrawerForVoiceInput() {
        if (navigationDrawerLayout == null)
            return;

        final Switch toggleSwitch = navigationDrawerLayout.findViewById(R.id.navigation_drawer_voice_control_toggle);
        final TextView triggerView = navigationDrawerLayout.findViewById(R.id.navigation_drawer_voice_control_trigger);
        boolean showTrigger = false;
        boolean showToggle = false;
        switch (getVoiceControlOption()) {
            case VOICE_CONTROL_OPTION_ALWAYS:
                break;
            case VOICE_CONTROL_OPTION_TOGGLE:
                showToggle = true;
                final boolean toggleState = prefs.getBoolean(PREFS_KEY_VOICE_TOGGLE_STATE, true);
                toggleSwitch.setChecked(toggleState);
                toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    prefs.edit().putBoolean(PREFS_KEY_VOICE_TOGGLE_STATE, isChecked).apply();
                    if (isChecked) {
                        toggleSwitch.setText(R.string.user_interface_voice_control_toggle_on_title);
                        startVoiceInput();
                    } else {
                        toggleSwitch.setText(R.string.user_interface_voice_control_toggle_off_title);
                    }
                });
                break;
            case VOICE_CONTROL_OPTION_TRIGGER:
                showTrigger = true;
                triggerView.setOnClickListener(v ->
                        startVoiceInput());
                break;
            case VOICE_CONTROL_OPTION_HIDDEN:
                // do not show anything
                break;
            case VOICE_CONTROL_OPTION_OFF:
            default:
                break;
        }
        ViewUtils.setVisibility(navigationDrawerLayout.findViewById(R.id.navigation_drawer_voice_control), showTrigger || showToggle);
        ViewUtils.setVisibility(toggleSwitch, showToggle);
        ViewUtils.setVisibility(triggerView, showTrigger);
    }

    private void openDrawerForVoiceControl() {
        switch (getVoiceControlOption()) {
            case VOICE_CONTROL_OPTION_ALWAYS:
                startVoiceInput();
                break;
            case VOICE_CONTROL_OPTION_TOGGLE:
                final boolean toggleState = prefs.getBoolean(PREFS_KEY_VOICE_TOGGLE_STATE, true);
                final Switch toggleSwitch = navigationDrawerLayout.findViewById(R.id.navigation_drawer_voice_control_toggle);
                toggleSwitch.setText(getString(toggleState
                        ? R.string.user_interface_voice_control_toggle_on_title
                        : R.string.user_interface_voice_control_toggle_off_title));
                if (toggleState)
                    startVoiceInput();
                break;
            case VOICE_CONTROL_OPTION_TRIGGER:
                break;
            case VOICE_CONTROL_OPTION_HIDDEN:
                // do not show anything
                break;
            case VOICE_CONTROL_OPTION_OFF:
            default:
                new Toast(OeffiActivity.this).toast(R.string.user_interface_voice_control_not_enabled);
                break;
        }
    }

    @NonNull
    private String getVoiceControlOption() {
        return prefs.getString(PREFS_KEY_VOICE_CONTROL_MODE, "false");
    }

    private void startVoiceInput() {
        Application.getInstance().getSpeechInput()
                .startSpeechRecognition(OeffiActivity.this, new SpeechInput.SpeechInputTerminationListener() {
                    @Override
                    public void onSpeechInputTermination(final boolean success) {
                        if (success)
                            closeNavigation();
                    }

                    @Override
                    public void onSpeechInputError(final String hint) {
                        new Toast(OeffiActivity.this).toast(R.string.user_interface_voice_control_error, hint);
                    }
                });
        new Toast(OeffiActivity.this).toast(R.string.user_interface_voice_control_is_enabled);
    }

    protected void updateNavigation() {
        if (navigationDrawerLayout == null)
            return;

        if (navigationDrawerFooterView != null) {
            final Resources resources = getResources();
            final boolean showFooter =
                       resources.getBoolean(R.bool.flags_show_navigation_drawer_footer)
                    && resources.getBoolean(R.bool.layout_navigation_drawer_footer_show);
            navigationDrawerFooterView.setVisibility(showFooter ? View.VISIBLE : View.GONE);
        }

        if (prefs.getBoolean(Constants.PREFS_KEY_USER_INTERFACE_MAINMENU_SHAREAPP_ENABLED, false)) {
            final View drawerView = findViewById(R.id.navigation_drawer_share);
            drawerView.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.navigation_drawer_share_title)).setText(application.getShareTitle());
            drawerView.setOnClickListener(v -> {
                closeNavigation();
                application.shareApp(this);
            });
            findViewById(R.id.navigation_drawer_share_qrcode).setOnClickListener(v -> {
                closeNavigation();
                application.showImageDialog(this, R.drawable.qr_update, null);
            });
        }

        prepareDrawerForVoiceInput();
    }

    protected boolean isNavigationOpen() {
        return navigationDrawerLayout != null && navigationDrawerLayout.isDrawerOpen(Gravity.LEFT);
    }

    private void toggleNavigation() {
        if (isNavigationOpen())
            closeNavigation();
        else
            openNavigation();
    }

    protected void openNavigation() {
        if (navigationDrawerLayout != null)
            navigationDrawerLayout.openDrawer(Gravity.LEFT);
    }

    protected void closeNavigation() {
        if (navigationDrawerLayout != null)
            navigationDrawerLayout.closeDrawer(Gravity.LEFT);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            toggleNavigation();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    protected void updateFragments() {
    }

    protected void updateFragments(final int listFrameResId) {
        updateFragments(listFrameResId, R.id.map_frame);
    }

    protected OeffiMapView getMapView() {
        if (mapView == null)
            mapView = findViewById(R.id.map_view);

        return mapView;
    }

    protected void onMapSetVisible() {
    }

    protected void updateFragments(final int listFrameResId, final int mapFrameResId) {
        final Resources res = getResources();

        final View listFrame = findViewById(listFrameResId);
        final boolean listShow = res.getBoolean(R.bool.layout_list_show);
        ViewUtils.setVisibility(listFrame, isInMultiWindowMode() || listShow);

        final boolean mapShow;
        final View mapFrame = findViewById(mapFrameResId);
        if (mapFrame != null) {
            mapShow = !isInMultiWindowMode() && isMapEnabled(res);
            ViewUtils.setVisibility(mapFrame, mapShow);
            ViewUtils.setVisibility(mapView, mapShow);
        } else {
            mapShow = false;
        }

        if (isPortrait) {
            listFrame.getLayoutParams().height = listShow && mapShow
                    ? res.getDimensionPixelSize(R.dimen.layout_list_height)
                    : LinearLayout.LayoutParams.MATCH_PARENT;
        } else {
            listFrame.getLayoutParams().width = listShow && mapShow
                    ? res.getDimensionPixelSize(R.dimen.layout_list_width)
                    : LinearLayout.LayoutParams.MATCH_PARENT;
        }

        final ViewGroup navigationDrawer = findViewById(R.id.navigation_drawer_layout);
        if (navigationDrawer != null) {
            final View child = navigationDrawer.getChildAt(1);
            child.getLayoutParams().width = res.getDimensionPixelSize(R.dimen.layout_navigation_drawer_width);
        }
    }

    private void setupMapView(final View contentView) {
        final View mapFrame = contentView.findViewById(R.id.map_frame);
        if (mapFrame == null)
            return;

        mapView = findViewById(R.id.map_view);
        final LinearLayout mapFrameContainer = (LinearLayout) mapFrame.getParent();
        mapFrameContainer.setOrientation(isPortrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            android.location.Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (location != null)
                mapView.animateToLocation(location.getLatitude(), location.getLongitude());
        }

        final TextView mapDisclaimerView = findViewById(R.id.map_disclaimer);
        mapDisclaimerView.setText(mapView.getTileProvider().getTileSource().getCopyrightNotice());
        ViewCompat.setOnApplyWindowInsetsListener(mapDisclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });

        final ZoomControls zoom = findViewById(R.id.map_zoom);
        ViewCompat.setOnApplyWindowInsetsListener(zoom, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
        mapView.setZoomControls(zoom);
    }

    protected void addShowMapButtonToActionBar() {
        if (isPortrait && getResources().getBoolean(R.bool.layout_map_show_toggleable)) {
            getMyActionBar().addButton(R.drawable.ic_map_white_24dp, R.string.directions_trip_details_action_showmap_title)
                    .setOnClickListener(v -> {
                        mapEnabled = !mapEnabled;
                        updateFragments();
                        if (mapEnabled)
                            handler.post(this::onMapSetVisible);
                    });
        }
    }

    protected boolean isMapEnabled(final Resources res) {
        return mapEnabled || res.getBoolean(R.bool.layout_map_show);
    }

    protected String prefsGetNetwork() {
        return prefs.getString(Constants.PREFS_KEY_NETWORK_PROVIDER, null);
    }

    protected NetworkId prefsGetNetworkId() {
        final String id = prefsGetNetwork();
        if (id == null)
            return null;

        try {
            return NetworkId.valueOf(id);
        } catch (final IllegalArgumentException x) {
            log.warn("Ignoring unkown selected network: {}", id);
            return null;
        }
    }

    protected NetworkProvider.Optimize prefsGetOptimizeTrip() {
        final String optimize = prefs.getString(Constants.PREFS_KEY_OPTIMIZE_TRIP, null);
        if (optimize != null)
            return NetworkProvider.Optimize.valueOf(optimize);
        else
            return null;
    }

    protected NetworkProvider.WalkSpeed prefsGetWalkSpeed() {
        return NetworkProvider.WalkSpeed.valueOf(prefs.getString(Constants.PREFS_KEY_WALK_SPEED, NetworkProvider.WalkSpeed.NORMAL.name()));
    }

    protected Integer prefsGetMinTranfserTime() {
        final int value = Integer.parseInt(prefs.getString(Constants.PREFS_KEY_MIN_TRANSFER_TIME, "-1"));
        return value < 0 ? null : value;
    }

    protected NetworkProvider.Accessibility prefsGetAccessibility() {
        return NetworkProvider.Accessibility.valueOf(prefs.getString(Constants.PREFS_KEY_ACCESSIBILITY, NetworkProvider.Accessibility.NEUTRAL.name()));
    }

    protected Set<Product> getNetworkDefaultProducts() {
        final NetworkProvider networkProvider = network != null ? NetworkProviderFactory.provider(network) : null;
        return networkProvider != null ? networkProvider.defaultProducts() : Product.ALL;
    }

    protected Set<Product> loadProductFilter() {
        final Set<Product> networkDefaultProducts = getNetworkDefaultProducts();
        final Set<Product> keepProducts;
        final String networkSpecificKey = Constants.PREFS_KEY_PRODUCT_FILTER + "_" + network;
        String value = prefs.getString(networkSpecificKey, null);
        if (value != null) {
            keepProducts = Product.ALL;
        } else {
            // do not load the default preference, instead let value be null to take the network defaults
            // value = prefs.getString(Constants.PREFS_KEY_PRODUCT_FILTER, null);
            keepProducts = networkDefaultProducts;
        }

        Set<Product> products = new HashSet<>(Product.values().length);
        if (value != null) {
            for (final char c : value.toCharArray())
                products.add(Product.fromCode(c));
            products = products.stream().filter(product -> keepProducts.contains(product)).collect(Collectors.toSet());
        } else {
            products.addAll(networkDefaultProducts);
        }
        return products;
    }

    protected void saveProductFilter(final Set<Product> products) {
        final StringBuilder p = new StringBuilder();
        for (final Product product : products)
            p.append(product.code);
        String value = p.toString();
        String networkSpecificKey = Constants.PREFS_KEY_PRODUCT_FILTER + "_" + network;
        prefs.edit()
                .putString(Constants.PREFS_KEY_PRODUCT_FILTER, value)
                .putString(networkSpecificKey, value)
                .apply();
    }

    @NonNull
    protected TripOptions getTripOptionsFromPrefs() {
        return new TripOptions(loadProductFilter(), prefsGetOptimizeTrip(), prefsGetWalkSpeed(),
                prefsGetMinTranfserTime(), prefsGetAccessibility(), null);
    }

    protected void checkChangeNetwork() {
        final boolean haveChanges;

        final NetworkId newNetwork = prefsGetNetworkId();
        if (newNetwork != null && newNetwork != network) {
            log.info("Network change detected: {} -> {}", network, newNetwork);
            ErrorReporter.getInstance().setNetworkId(newNetwork);

            network = newNetwork;
            savedProducts = loadProductFilter();
            haveChanges = true;
        } else {
            Set<Product> newProducts = loadProductFilter();
            if (newProducts.size() != savedProducts.size()) {
                haveChanges = true;
            } else {
                haveChanges = !newProducts.containsAll(savedProducts);
            }
            if (haveChanges) {
                log.info("products change detected: {} -> {}", network, newNetwork);
                savedProducts = newProducts;
            }
        }

        if (haveChanges)
            onChangeNetwork(network);
    }

    protected void onChangeNetwork(final NetworkId network) {
    }

    protected final String applicationVersionName() {
        return Application.versionName(application);
    }

    protected final int applicationVersionCode() {
        return Application.versionCode(application);
    }

    protected final long applicationFirstInstallTime() {
        return application.packageInfo().firstInstallTime;
    }

    protected final MyActionBar getMyActionBar() {
        return findViewById(R.id.action_bar);
    }

    protected final void setPrimaryColor(final int colorResId) {
        final int color = getResources().getColor(colorResId);
        getMyActionBar().setBackgroundColor(color);
        setTaskDescription(new TaskDescription(null, null, color));
    }

    protected void updateDisclaimerSource(final TextView disclaimerSourceView, final String network,
            final CharSequence defaultLabel) {
        final NetworkResources networkRes = NetworkResources.instance(this, network);
        final Drawable networkResIcon = networkRes.icon;
        final String label = getString(R.string.disclaimer_network, networkRes.label != null ? networkRes.label : defaultLabel);
        if (networkRes.cooperation && networkResIcon != null) {
            final Drawable icon = networkResIcon.mutate();
            final int size = getResources().getDimensionPixelSize(R.dimen.disclaimer_network_icon_size);
            icon.setBounds(0, 0, size, size);
            disclaimerSourceView.setCompoundDrawables(icon, null, null, null);
        } else {
            disclaimerSourceView.setCompoundDrawables(null, null, null, null);
        }
        disclaimerSourceView.setText(label);
        disclaimerSourceView.setVisibility(View.VISIBLE);
    }

    protected final CharSequence product(final ResultHeader header) {
        final StringBuilder str = new StringBuilder();

        // time delta
        if (header.serverTime != 0) {
            final long delta = (System.currentTimeMillis() - header.serverTime) / DateUtils.MINUTE_IN_MILLIS;
            if (Math.abs(delta) > 0)
                str.append("\u0394 ").append(delta).append(" min\n");
        }

        // name or product
        if (header.serverName != null)
            str.append(header.serverName);
        else
            str.append(header.serverProduct);

        // version
        if (header.serverVersion != null) {
            str.append(' ').append(header.serverVersion);
        }

        return str;
    }

    protected boolean isDeveloperElementsEnabled() {
        return application.isDeveloperElementsEnabled();
    }

    public boolean isDarkMode() {
        return Application.getInstance().isDarkMode();
    }
}
