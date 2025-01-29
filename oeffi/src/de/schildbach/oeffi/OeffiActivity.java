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

import android.app.ActivityManager.TaskDescription;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;

import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.network.NetworkResources;
import de.schildbach.oeffi.util.ErrorReporter;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.ResultHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class OeffiActivity extends ComponentActivity {
    protected Application application;
    protected SharedPreferences prefs;
    protected NetworkId network;
    protected Set<Product> savedProducts;

    private static final Logger log = LoggerFactory.getLogger(OeffiActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);

        network = prefsGetNetworkId();
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

    protected void updateFragments(final int listFrameResId, final int mapFrameResId) {
        final Resources res = getResources();

        final View listFrame = findViewById(listFrameResId);
        final boolean listShow = res.getBoolean(R.bool.layout_list_show);
        listFrame.setVisibility(isInMultiWindowMode() || listShow ? View.VISIBLE : View.GONE);

        final View mapFrame = findViewById(mapFrameResId);
        final boolean mapShow = res.getBoolean(R.bool.layout_map_show);
        mapFrame.setVisibility(!isInMultiWindowMode() && mapShow ? View.VISIBLE : View.GONE);

        listFrame.getLayoutParams().width = listShow && mapShow ? res.getDimensionPixelSize(R.dimen.layout_list_width)
                : LinearLayout.LayoutParams.MATCH_PARENT;

        final ViewGroup navigationDrawer = findViewById(R.id.navigation_drawer_layout);
        if (navigationDrawer != null) {
            final View child = navigationDrawer.getChildAt(1);
            child.getLayoutParams().width = res.getDimensionPixelSize(R.dimen.layout_navigation_drawer_width);
        }
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

    protected Collection<Product> getNetworkDefaultProducts() {
        final NetworkProvider networkProvider = network != null ? NetworkProviderFactory.provider(network) : null;
        return networkProvider != null ? networkProvider.defaultProducts() : Product.ALL;
    }

    protected Set<Product> loadProductFilter() {
        Collection<Product> networkDefaultProducts = getNetworkDefaultProducts();
        Collection<Product> keepProducts;
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
        if (networkRes.cooperation && networkResIcon != null) {
            final Drawable icon = networkResIcon.mutate();
            final int size = getResources().getDimensionPixelSize(R.dimen.disclaimer_network_icon_size);
            icon.setBounds(0, 0, size, size);
            disclaimerSourceView.setCompoundDrawables(icon, null, null, null);
            disclaimerSourceView.setText(getString(R.string.disclaimer_network, networkRes.label));
        } else {
            disclaimerSourceView.setCompoundDrawables(null, null, null, null);
            disclaimerSourceView.setText(defaultLabel);
        }
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
}
