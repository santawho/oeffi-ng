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

package de.schildbach.oeffi.plans;

import android.Manifest;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Criteria;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiMainActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.URLs;
import de.schildbach.oeffi.plans.list.PlanClickListener;
import de.schildbach.oeffi.plans.list.PlanContextMenuItemListener;
import de.schildbach.oeffi.plans.list.PlansAdapter;
import de.schildbach.oeffi.util.ConnectivityBroadcastReceiver;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.Downloader;
import de.schildbach.oeffi.util.LocationHelper;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.UiThreadExecutor;
import de.schildbach.pte.dto.Point;
import okhttp3.Cache;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.net.HttpURLConnection;

public class PlansPickerActivity extends OeffiMainActivity implements LocationHelper.Callback, PlanClickListener,
        PlanContextMenuItemListener {
    private ConnectivityManager connectivityManager;
    private LocationHelper locationHelper;

    private MyActionBar actionBar;
    private RecyclerView listView;
    private PlansAdapter listAdapter;
    private TextView connectivityWarningView;
    private View filterBox;

    private Cache thumbCache;
    private BroadcastReceiver connectivityReceiver;
    private Point location;
    private String filter;

    private Cursor cursor;

    private static final int THUMB_CACHE_SIZE = 2 * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(PlansPickerActivity.class);

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                maybeStartLocation();
            });

    @Override
    protected String taskName() {
        return "plans";
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        locationHelper = new LocationHelper((LocationManager) getSystemService(Context.LOCATION_SERVICE), this);

        final File cacheDir = new File(getCacheDir(), "thumbs");
        thumbCache = new Cache(cacheDir, THUMB_CACHE_SIZE);

        setContentView(R.layout.plans_picker_content);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        actionBar = getMyActionBar();
        setPrimaryColor(R.color.bg_action_bar);
        actionBar.setPrimaryTitle(R.string.plans_activity_title);
        actionBar.addButton(R.drawable.ic_search_white_24dp, R.string.plans_picker_action_search_title)
                .setOnClickListener(v -> onSearchRequested());

        cursor = getContentResolver().query(PlanContentProvider.CONTENT_URI(), null, null, null, null);

        listView = findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        listAdapter = new PlansAdapter(this, cursor, thumbCache, this, this, application.okHttpClient());
        listView.setAdapter(listAdapter);
        ViewCompat.setOnApplyWindowInsetsListener(listView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });

        connectivityWarningView = findViewById(R.id.plans_picker_connectivity_warning_box);
        filterBox = findViewById(R.id.plans_picker_filter_box);

        findViewById(R.id.plans_picker_filter_clear).setOnClickListener(v -> clearListFilter());

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

        handleIntent();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    @Override
    public void onNewIntent(final Intent intent) {
        setIntent(intent);
        handleIntent();
    }

    @Override
    protected void onStart() {
        super.onStart();

        requery();
        maybeStartLocation();
    }

    @Override
    protected void onStop() {
        locationHelper.stop();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(connectivityReceiver);

        super.onDestroy();
    }

    public void maybeStartLocation() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;
        if (locationHelper.isRunning())
            return;

        final Criteria criteria = new Criteria();
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        locationHelper.startLocation(criteria, true, Constants.LOCATION_FOREGROUND_UPDATE_TIMEOUT_MS);
    }

    public void stopLocation() {
        locationHelper.stop();
    }

    public void onLocationStart(final String provider) {
    }

    public void onLocationStop(final boolean timedOut) {
    }

    public void onLocationFail() {
    }

    public void onLocation(final Point here) {
        this.location = here;
        requery();
    }

    private void handleIntent() {
        final Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction()))
            setListFilter(intent.getStringExtra(SearchManager.QUERY).trim().toLowerCase(Constants.DEFAULT_LOCALE));
    }

    @Override
    public void onBackPressedEvent() {
        if (isNavigationOpen())
            closeNavigation();
        else if (filter != null)
            clearListFilter();
        else
            super.onBackPressedEvent();
    }

    private void setListFilter(final String filter) {
        this.filter = filter;
        filterBox.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.plans_picker_filter_text)).setText(filter);
        requery();
    }

    private void clearListFilter() {
        filter = null;
        filterBox.setVisibility(View.GONE);
        requery();
    }

    private void requery() {
        final String sortOrder = location != null
                ? Double.toString(location.getLatAsDouble()) + "," + Double.toString(location.getLonAsDouble()) : null;
        final Uri.Builder uri = PlanContentProvider.CONTENT_URI().buildUpon();
        if (filter != null)
            uri.appendPath(SearchManager.SUGGEST_URI_PATH_QUERY).appendPath(filter);
        cursor = getContentResolver().query(uri.build(), null, null, null, sortOrder);
        listAdapter = new PlansAdapter(this, cursor, thumbCache, this, this, application.okHttpClient());
        listView.setAdapter(listAdapter);

        findViewById(android.R.id.empty).setVisibility(cursor.getCount() > 0 ? View.GONE : View.VISIBLE);
    }

    public void onPlanClick(final PlansAdapter.Plan plan) {
        openPlan(plan);
    }

    public boolean onPlanContextMenuItemClick(final PlansAdapter.Plan plan, final int menuItemId) {
        if (menuItemId == R.id.plans_picker_context_open) {
            openPlan(plan);
            return true;
        } else if (menuItemId == R.id.plans_picker_context_remove) {
            Downloader.deleteDownload(plan.localFile);
            final int position = listView.findViewHolderForItemId(plan.rowId).getAdapterPosition();
            if (position != RecyclerView.NO_POSITION)
                listAdapter.setLoaded(position, false);
            return true;
        } else if (menuItemId == R.id.plans_picker_context_launcher_shortcut) {
            final String shortcutId = "plan-" + plan.planId;
            ShortcutManagerCompat.requestPinShortcut(this,
                    new ShortcutInfoCompat.Builder(this, shortcutId).setShortLabel(plan.name)
                            .setActivity(new ComponentName(this, PlansPickerActivity.class))
                            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_oeffi_plans_color_48dp))
                            .setIntent(PlanActivity.intent(PlansPickerActivity.this, plan.planId, null)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK))
                            .build(),
                    null);
            return true;
        } else {
            return false;
        }
    }

    private void openPlan(final PlansAdapter.Plan plan) {
        final String planFilename = plan.planId + ".png";
        final File planFile = new File(getDir(Constants.PLANS_DIR, MODE_PRIVATE), planFilename);

        if (planFile.exists()) {
            PlanActivity.start(this, plan.planId, null);
        } else {
            final Downloader downloader = new Downloader(getCacheDir());
            final HttpUrl remoteUrl = plan.url != null ? plan.url
                    : URLs.getPlansBaseUrl().newBuilder().addEncodedPathSegment(planFilename).build();
            final ListenableFuture<Integer> download = downloader.download(application.okHttpClient(), remoteUrl,
                    planFile, false, (contentRead, contentLength) -> runOnUiThread(() -> {
                        final RecyclerView.ViewHolder holder = listView.findViewHolderForItemId(plan.rowId);
                        if (holder != null) {
                            final int position = holder.getAdapterPosition();
                            if (position != RecyclerView.NO_POSITION)
                                listAdapter.setProgressPermille(position,
                                        (int) (contentRead * 1000 / contentLength));
                        }
                    }));
            actionBar.startProgress();
            Futures.addCallback(download, new FutureCallback<Integer>() {
                public void onSuccess(final @Nullable Integer status) {
                    if (status == HttpURLConnection.HTTP_OK) {
                        PlanActivity.start(PlansPickerActivity.this, plan.planId, null);
                        final RecyclerView.ViewHolder holder = listView.findViewHolderForItemId(plan.rowId);
                        if (holder != null) {
                            final int position = holder.getAdapterPosition();
                            if (position != RecyclerView.NO_POSITION) {
                                listAdapter.setProgressPermille(position, 1000);
                                listAdapter.setLoaded(position, true);
                            }
                        }
                    } else if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                        new Toast(PlansPickerActivity.this).longToast(R.string.alert_network_file_not_found);
                    } else if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                        new Toast(PlansPickerActivity.this).longToast(R.string.alert_network_forbidden);
                    }
                    actionBar.stopProgress();
                }

                public void onFailure(final Throwable t) {
                    new Toast(PlansPickerActivity.this).longToast(R.string.alert_network_connection_unavailable);
                    actionBar.stopProgress();
                }
            }, new UiThreadExecutor());
        }
    }
}
