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

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.app.ActivityManager.TaskDescription;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
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
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.ResultHeader;
import de.schildbach.pte.dto.TripOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class OeffiActivity extends ComponentActivity {
    protected static final String INTENT_EXTRA_LINK_ARGS = OeffiActivity.class.getName() + ".link_args";
    protected static final String INTENT_EXTRA_NETWORK_NAME = OeffiActivity.class.getName() + ".network";

    protected Application application;
    private final Handler handler = new Handler();

    protected SharedPreferences prefs;
    protected NetworkId network;
    protected String[] linkArgs;
    protected Set<Product> savedProducts;

    private DrawerLayout navigationDrawerLayout;
    private MenuProvider navigationDrawerMenuProvider;
    private View navigationDrawerFooterView;

    protected Logger log;

    protected OeffiActivity() {
         log = LoggerFactory.getLogger(this.getClass());
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);

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
        this.application = (Application) getApplication();

        ErrorReporter.getInstance().check(this, applicationVersionCode(), application.okHttpClient());
    }

    @Override
    protected void onResume() {
        checkChangeNetwork();
        super.onResume();
    }

    @Override
    protected void onPause() {
        savedProducts = loadProductFilter();
        super.onPause();
    }

    protected void hideNavigation() {
        final DrawerLayout drawerLayout = findViewById(R.id.navigation_drawer_layout);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    protected void initNavigation() {
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
            }

            @Override
            public boolean onMenuItemSelected(final MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.global_options_stations_favorites) {
                    if (OeffiActivity.this instanceof StationsActivity) {
                        FavoriteStationsActivity.start(OeffiActivity.this);
                    } else {
                        final Intent intent = new Intent(OeffiActivity.this, StationsActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra(StationsActivity.INTENT_EXTRA_OPEN_FAVORITES, true);
                        startActivity(intent);
                        finish();
                        overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
                    }
                    return true;
                }

                if (itemId == R.id.global_options_stations_nearby) {
                    if (OeffiActivity.this instanceof StationsActivity)
                        return true;
                    final Intent intent = new Intent(OeffiActivity.this, StationsActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
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
                    PreferenceActivity.start(OeffiActivity.this, DonateFragment.class.getName());
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
                    PreferenceActivity.start(OeffiActivity.this, AboutFragment.class.getName());
                    return true;
                }

                if (itemId == R.id.global_options_extras) {
                    final View actionView = item.getActionView();
                    final PopupMenu popupMenu = new PopupMenu(OeffiActivity.this, actionView);
                    popupMenu.setGravity(Gravity.RIGHT);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        popupMenu.setForceShowIcon(true);
                    }
                    popupMenu.inflate(R.menu.global_extras);
                    popupMenu.setOnMenuItemClickListener(subItem -> {
                        navigationDrawerLayout.closeDrawers();
                        final int subItemId = subItem.getItemId();
                        if (subItemId == R.id.global_options_clear_navigation) {
                            NavigationNotification.removeAll(OeffiActivity.this);
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

        navigationDrawerLayout = findViewById(R.id.navigation_drawer_layout);
        final RecyclerView navigationDrawerListView = findViewById(R.id.navigation_drawer_list);
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

        navigationDrawerListView.setLayoutManager(new LinearLayoutManager(this));
        navigationDrawerListView
                .addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        navigationDrawerListView.setAdapter(menuAdapter);

        navigationDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            public void onDrawerOpened(final View drawerView) {
                handler.postDelayed(() -> heartbeat.start(), 2000);
            }

            public void onDrawerClosed(final View drawerView) {
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

        findViewById(R.id.navigation_drawer_share).setOnClickListener(v -> {
            navigationDrawerLayout.closeDrawer(Gravity.LEFT);
            shareApp();
        });
        findViewById(R.id.navigation_drawer_share_qrcode).setOnClickListener(v -> {
            navigationDrawerLayout.closeDrawer(Gravity.LEFT);
            showImageDialog(R.drawable.qr_update);
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

    protected void updateNavigation() {
        if (navigationDrawerFooterView != null) {
            final Resources resources = getResources();
            final boolean showFooter =
                       resources.getBoolean(R.bool.flags_show_navigation_drawer_footer)
                    && resources.getBoolean(R.bool.layout_navigation_drawer_footer_show);
            navigationDrawerFooterView.setVisibility(showFooter ? View.VISIBLE : View.GONE);
        }
    }

    protected boolean isNavigationOpen() {
        return navigationDrawerLayout.isDrawerOpen(Gravity.LEFT);
    }

    private void toggleNavigation() {
        if (navigationDrawerLayout.isDrawerOpen(Gravity.LEFT))
            navigationDrawerLayout.closeDrawer(Gravity.LEFT);
        else
            navigationDrawerLayout.openDrawer(Gravity.LEFT);
    }

    protected void closeNavigation() {
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

    protected void updateFragments(final int listFrameResId, final int mapFrameResId) {
        final Resources res = getResources();

        final View listFrame = findViewById(listFrameResId);
        final boolean listShow = res.getBoolean(R.bool.layout_list_show);
        listFrame.setVisibility(isInMultiWindowMode() || listShow ? View.VISIBLE : View.GONE);

        final View mapFrame = findViewById(mapFrameResId);
        boolean mapShow = !isInMultiWindowMode() && isMapEnabled(res);
        mapFrame.setVisibility(mapShow ? View.VISIBLE : View.GONE);

        ViewParent container = mapFrame.getParent();
        final boolean isVertical = (container instanceof LinearLayout
                && ((LinearLayout) container).getOrientation() == LinearLayout.VERTICAL);

        if (isVertical) {
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

    protected boolean isMapEnabled(final Resources res) {
        return res.getBoolean(R.bool.layout_map_show);
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
        Set<Product> networkDefaultProducts = getNetworkDefaultProducts();
        Set<Product> keepProducts;
        Set<Product> products = new HashSet<>(Product.values().length);
        String networkSpecificKey = Constants.PREFS_KEY_PRODUCT_FILTER + "_" + network;
        String value = prefs.getString(networkSpecificKey, null);
        if (value != null) {
            keepProducts = Product.ALL;
        } else {
            value = prefs.getString(Constants.PREFS_KEY_PRODUCT_FILTER, null);
            keepProducts = networkDefaultProducts;
        }
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
        boolean haveChanges = false;

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

    protected void shareApp() {
        final String updateUrl = getString(R.string.about_update_apk_url);
        final String shareTitle = getString(R.string.global_options_share_app_title);
        final String shareText = getString(R.string.global_options_share_app_text, updateUrl);
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.putExtra(Intent.EXTRA_SUBJECT, shareTitle);
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, shareTitle));
    }

    protected void showImageDialog(final int imageResId) {
        final DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
        final int sizePixels = (Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels) * 3) / 4;
        final ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(Bitmap.createScaledBitmap(
                ((BitmapDrawable) getDrawable(imageResId)).getBitmap(),
                sizePixels, sizePixels, false));
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(imageView);
        dialog.show();
    }
}
