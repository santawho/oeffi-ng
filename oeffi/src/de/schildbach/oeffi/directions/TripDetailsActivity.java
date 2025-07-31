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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.provider.CalendarContract;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.common.base.MoreObjects;
import com.google.common.base.Supplier;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.DeviceAdmin;
import de.schildbach.oeffi.LocationAware;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.TripAware;
import de.schildbach.oeffi.util.GoogleMapsUtils;
import de.schildbach.oeffi.util.KmlProducer;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.util.TimeSpec.DepArr;
import de.schildbach.oeffi.directions.navigation.TripRenderer;
import de.schildbach.oeffi.directions.navigation.TripNavigatorActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.stations.StationsActivity;
import de.schildbach.oeffi.util.ClockUtils;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.HtmlUtils;
import de.schildbach.oeffi.util.LocationHelper;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.oeffi.util.locationview.LocationTextView;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Fare;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Style;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.TripShare;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkNotNull;

public class TripDetailsActivity extends OeffiActivity implements LocationListener, LocationAware  {
    public static class RenderConfig implements Serializable {
        private static final long serialVersionUID = 6006525041994219717L;

        public boolean isJourney;
        public boolean isNavigation;
        public boolean isAlternativeConnectionSearch;
        public int actionBarColor;
        public QueryTripsRunnable.TripRequestData queryTripsRequestData;
    }

    public static final String INTENT_EXTRA_NETWORK = TripDetailsActivity.class.getName() + ".network";
    public static final String INTENT_EXTRA_TRIP = TripDetailsActivity.class.getName() + ".trip";
    public static final String INTENT_EXTRA_RENDERCONFIG = TripDetailsActivity.class.getName() + ".config";

    public static class IntentData implements Serializable {
        private static final long serialVersionUID = 8180214631776887395L;

        public final NetworkId network;
        public final Trip trip;
        public final RenderConfig renderConfig;

        public IntentData(
                final NetworkId network,
                final Trip trip,
                final RenderConfig renderConfig) {
            this.network = network;
            this.trip = trip;
            this.renderConfig = renderConfig;
        }

        public IntentData(final Intent intent) {
            this(
                    (NetworkId) intent.getSerializableExtra(INTENT_EXTRA_NETWORK),
                    (Trip) intent.getSerializableExtra(INTENT_EXTRA_TRIP),
                    (RenderConfig) intent.getSerializableExtra(INTENT_EXTRA_RENDERCONFIG));
        }
    }

    public static void start(final Context context, final NetworkId network, final Trip.Public journeyLeg, final Date loadedAt) {
        final Trip trip = new Trip(
                loadedAt,
                null,
                null,
                journeyLeg.departure,
                journeyLeg.arrival,
                Collections.singletonList(journeyLeg),
                null,
                null,
                null);
        final RenderConfig renderConfig = new RenderConfig();
        renderConfig.isJourney = true;
        start(context, network, trip, renderConfig);
    }

    public static void start(
            final Context context,
            final NetworkId network,
            final Trip trip) {
        start(context, network, trip, 0);
    }

    public static void start(
            final Context context,
            final NetworkId network,
            final Trip trip,
            final int intentFlags) {
        start(context, network, trip, new RenderConfig(), intentFlags);
    }

    public static void start(final Context context, final NetworkId network, final Trip trip, final RenderConfig renderConfig) {
        start(context, network, trip, renderConfig, 0);
    }

    public static void start(final Context context, final NetworkId network, final Trip trip, final RenderConfig renderConfig, final int intentFlags) {
        final Intent intent = buildStartIntent(TripDetailsActivity.class, context, network, trip, renderConfig);
        intent.addFlags(intentFlags);
        context.startActivity(intent);
    }

    public static void startForResult(final Activity context, int requestCode, final NetworkId network, final Trip trip, final RenderConfig renderConfig) {
        context.startActivityForResult(buildStartIntent(TripDetailsActivity.class, context, network, trip, renderConfig), requestCode);
    }

    protected static Intent buildStartIntent(
            final Class<? extends TripDetailsActivity> activityClass, final Context context,
            final NetworkId network, final Trip trip, final RenderConfig renderConfig) {
        final Intent intent = new Intent(context, activityClass);
        intent.putExtra(INTENT_EXTRA_NETWORK, checkNotNull(network));
        intent.putExtra(INTENT_EXTRA_TRIP, checkNotNull(trip));
        intent.putExtra(INTENT_EXTRA_RENDERCONFIG, checkNotNull(renderConfig));
        return intent;
    }

    protected MyActionBar actionBar;
    private LayoutInflater inflater;
    private Resources res;
    private int colorSignificant;
    private int colorInsignificant;
    private int colorHighlighted;
    private int colorSimulated;
    private int colorPosition, colorPositionBackground, colorPositionBackgroundChanged;
    private int colorLegPublicPastBackground, colorLegPublicNowBackground, colorLegPublicFutureBackground;
    private int colorLegIndividualPastBackground, colorLegIndividualNowBackground, colorLegIndividualFutureBackground;
    private DisplayMetrics displayMetrics;
    private LocationManager locationManager;
    private BroadcastReceiver tickReceiver;
    private boolean showNextEventWhenUnlocked = false;
    private boolean showNextEventWhenLocked = true;

    private ViewGroup legsGroup;
    private ToggleImageButton trackButton;

    protected NetworkId network;
    protected TripRenderer tripRenderer;
    protected RenderConfig renderConfig;
    private Date highlightedTime;
    private Location highlightedLocation;
    private Point deviceLocation;
    private Date deviceLocationTime;
    private int selectedLegIndex = -1;
    protected boolean isPaused = false;

    private int LEGSGROUP_INSERT_INDEX;

    private QueryJourneyRunnable queryJourneyRunnable;
    private HandlerThread backgroundThread;
    protected Handler backgroundHandler;
    protected final Handler handler = new Handler();

    private static final Logger log = LoggerFactory.getLogger(TripDetailsActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        backgroundThread = new HandlerThread("TripDetails.queryTripsThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        inflater = getLayoutInflater();
        res = getResources();
        colorSignificant = res.getColor(R.color.fg_significant);
        colorInsignificant = res.getColor(R.color.fg_insignificant);
        colorHighlighted = res.getColor(R.color.fg_highlighted);
        colorSimulated = res.getColor(R.color.fg_simulated);
        colorPosition = res.getColor(R.color.fg_position);
        colorPositionBackground = res.getColor(R.color.bg_position);
        colorPositionBackgroundChanged = res.getColor(R.color.bg_position_changed);
        colorLegPublicPastBackground = res.getColor(R.color.bg_trip_details_public_past);
        colorLegPublicNowBackground = res.getColor(R.color.bg_trip_details_public_now);
        colorLegPublicFutureBackground = res.getColor(R.color.bg_trip_details_public_future);
        colorLegIndividualPastBackground = res.getColor(R.color.bg_trip_details_individual_past);
        colorLegIndividualNowBackground = res.getColor(R.color.bg_trip_details_individual_now);
        colorLegIndividualFutureBackground = res.getColor(R.color.bg_trip_details_individual_future);
        displayMetrics = res.getDisplayMetrics();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        final IntentData intentData = new IntentData(getIntent());
        renderConfig = intentData.renderConfig;
        network = intentData.network;
        final Trip baseTrip = intentData.trip;

        log.info("Showing {} from {} to {}", renderConfig.isJourney ? "journey" : "trip", baseTrip.from, baseTrip.to);

        setupFromTrip(baseTrip);

        final View contentView = setContentView(R.layout.directions_trip_details_content, isTaskRoot());
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        actionBar = getMyActionBar();
        actionBar.setBack(isTaskRoot() ? null : v -> goBack());

        final Long duration = tripRenderer.trip.getPublicDuration();
        final String durationText = duration == null ? null :
                getString(R.string.directions_trip_details_duration, formatTimeSpan(duration));
        final int numChanges = tripRenderer.trip.numChanges == null ? 0 : tripRenderer.trip.numChanges;
        final String numChangesText = numChanges <= 0 ? null :
                getString(R.string.directions_trip_details_num_changes, numChanges);

        ((TextView)findViewById(R.id.directions_trip_details_duration)).setText(durationText);
        ((TextView)findViewById(R.id.directions_trip_details_numchanges)).setText(numChangesText);

        // action bar secondary title
        //  final StringBuilder secondaryTitle = new StringBuilder();
        //  if (duration != null)
        //    secondaryTitle.append(durationText);
        //  if (numChangesText != null) {
        //    if (secondaryTitle.length() > 0)
        //        secondaryTitle.append(" / ");
        //    secondaryTitle.append(numChangesText);
        //  }
        //  actionBar.setSecondaryTitle(secondaryTitle.length() > 0 ? secondaryTitle : null);

        setupActionBar();

        final ActivityResultLauncher<String> requestLocationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted)
                        enableTracking();
                    else {
                        trackButton.setChecked(false);
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.stations_list_cannot_acquire_location)
                                .setMessage(R.string.stations_list_cannot_acquire_location_hint)
                                .setPositiveButton(android.R.string.ok, null)
                                .create().show();
                    }
                });

        trackButton = actionBar.addToggleButton(R.drawable.ic_location_white_24dp,
                R.string.directions_trip_details_action_track_title);
        trackButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    enableTracking();
                } else {
                    requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                }
            } else {
                locationManager.removeUpdates(TripDetailsActivity.this);
                updateDeviceLocationDependencies(null, null);

                getMapView().setLocationAware(null);
            }

            getMapView().zoomToAll();
            updateGUI();
        });

        addShowMapButtonToActionBar();

        actionBar.addButton(R.drawable.ic_share_white_24dp, R.string.directions_trip_details_action_share_title)
                .setOnClickListener(v -> {
                    final PopupMenu popupMenu = new PopupMenu(TripDetailsActivity.this, v);
                    popupMenu.inflate(R.menu.directions_trip_details_action_share);
                    popupMenu.setOnMenuItemClickListener(item -> {
                        final int itemId = item.getItemId();
                        final Supplier<Intent> intentSupplier;
                        if (itemId == R.id.directions_trip_details_action_share_short) {
                            intentSupplier = () -> shareTripShort();
                        } else if (itemId == R.id.directions_trip_details_action_share_long) {
                            intentSupplier = () -> shareTripLong(false);
                        } else if (itemId == R.id.directions_trip_details_action_share_link) {
                            intentSupplier = () -> shareTripLong(true);
                        } else if (itemId == R.id.directions_trip_details_action_open_direct_link) {
                            final NetworkProvider provider = NetworkProviderFactory.provider(network);
                            if (provider.hasCapabilities(NetworkProvider.Capability.TRIP_LINKING)) {
                                backgroundHandler.post(() -> {
                                    try {
                                        final String link = provider.getOpenLink(tripRenderer.trip);
                                        runOnUiThread(() -> {
                                            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(intent);
                                        });
                                    } catch (Exception e) {
                                        log.error("cannot get open link", e);
                                    }
                                });
                            }
                            return true;
                        } else if (itemId == R.id.directions_trip_details_action_open_share_link) {
                            final NetworkProvider provider = NetworkProviderFactory.provider(network);
                            if (provider.hasCapabilities(NetworkProvider.Capability.TRIP_SHARING)) {
                                backgroundHandler.post(() -> {
                                    try {
                                        final String link = provider.getShareLink(tripRenderer.trip);
                                        runOnUiThread(() -> {
                                            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            startActivity(intent);
                                        });
                                    } catch (Exception e) {
                                        log.error("cannot get share link", e);
                                    }
                                });
                            }
                            return true;
                        } else if (itemId == R.id.directions_trip_details_action_open_external_maps_app) {
                            intentSupplier = () -> showKmlInExternalMapsApp();
                        } else {
                            return false;
                        }
                        if (intentSupplier == null)
                            return false;

                        backgroundHandler.post(() -> {
                            final Intent intent = intentSupplier.get();
                            runOnUiThread(() -> startActivity(intent));
                        });
                        return true;
                    });
                    popupMenu.show();
                });
        if (!renderConfig.isNavigation && !renderConfig.isAlternativeConnectionSearch) {
            actionBar.addButton(R.drawable.ic_today_white_24dp, R.string.directions_trip_details_action_calendar_title)
                    .setOnClickListener(v -> {
                        backgroundHandler.post(() -> {
                            try {
                                final Intent intent = scheduleTripIntent(tripRenderer.trip, true);
                                runOnUiThread(() -> startActivity(intent));
                            } catch (final ActivityNotFoundException x) {
                                new Toast(this).longToast(R.string.directions_trip_details_action_calendar_notfound);
                            }
                        });
                    });
        }
        addActionBarButtons();

        findViewById(R.id.directions_trip_details_not_feasible).setVisibility(tripRenderer.isFeasible() ? View.GONE : View.VISIBLE);

        legsGroup = findViewById(R.id.directions_trip_details_legs_group);

        updateDevInfo();
        updateLocations();
        updateFares(tripRenderer.trip.fares);

        LEGSGROUP_INSERT_INDEX = legsGroup.indexOfChild(findViewById(R.id.directions_trip_details_legs_start_here)) + 1;
        int i = LEGSGROUP_INSERT_INDEX;
        for (final TripRenderer.LegContainer legC : tripRenderer.legs) {
            final View row;
            if (legC.publicLeg != null)
                row = inflater.inflate(R.layout.directions_trip_details_public_entry, null);
            else
                row = inflater.inflate(R.layout.directions_trip_details_individual_entry, null);
            legsGroup.addView(row, i++);
        }
        ((TextView) findViewById(R.id.directions_trip_details_footer))
                .setText(Html.fromHtml(getString(R.string.directions_trip_details_realtime)));

        final View disclaimerView = findViewById(R.id.directions_trip_details_disclaimer_group);
        ViewCompat.setOnApplyWindowInsetsListener(disclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
        final TextView disclaimerSourceView = findViewById(R.id.directions_trip_details_disclaimer_source);
        updateDisclaimerSource(disclaimerSourceView, network.name(), null);

        getMapView().setTripAware(new TripAware() {
            public Trip getTrip() {
                return tripRenderer.trip;
            }

            public void selectLeg(final int partIndex) {
                selectedLegIndex = partIndex;
                getMapView().zoomToAll();
            }

            public boolean hasSelection() {
                return selectedLegIndex != -1;
            }

            public boolean isSelectedLeg(final Trip.Leg part) {
                if (!hasSelection())
                    return false;

                return tripRenderer.legs.get(selectedLegIndex).equals(part);
            }
        });

        final View.OnClickListener exitNextEventViewClicked = view -> setShowNextEvent(false);
        final View.OnLongClickListener lockDeviceNextEventViewLongClicked = view -> {
            DeviceAdmin.enterLockScreenAndShutOffDisplay(this);
            return true;
        };

        final View nextEventView = findViewById(R.id.navigation_next_event);
        final View nextEventContainerView = findViewById(R.id.navigation_next_event_container);
        final View nextEventBackView = findViewById(R.id.navigation_next_event_back_to_itinerary);

        nextEventView.setOnClickListener(exitNextEventViewClicked);
        nextEventContainerView.setOnClickListener(exitNextEventViewClicked);
        nextEventBackView.setOnClickListener(exitNextEventViewClicked);

        if (allowScreenLock()) {
            nextEventView.setLongClickable(true);
            nextEventView.setOnLongClickListener(lockDeviceNextEventViewLongClicked);

            nextEventContainerView.setLongClickable(true);
            nextEventContainerView.setOnLongClickListener(lockDeviceNextEventViewLongClicked);

            nextEventBackView.setLongClickable(true);
            nextEventBackView.setOnLongClickListener(lockDeviceNextEventViewLongClicked);
        }
    }

    protected boolean allowScreenLock() {
        return false;
    }

    private void updateDevInfo() {
        final TextView devinfoView = findViewById(R.id.directions_trip_details_devinfo);
        if (isDeveloperElementsEnabled()) {
            devinfoView.setVisibility(View.VISIBLE);
            final long loadedAt = tripRenderer.trip.loadedAt.getTime();
            final long updatedAt = tripRenderer.trip.updatedAt.getTime();
            devinfoView.setText(String.format("loaded at %s, updated at %s",
                    Formats.formatTime(this, loadedAt),
                    Formats.formatTime(this, updatedAt)));
        } else {
            devinfoView.setVisibility(View.GONE);
        }
    }

    protected void setupActionBar() {
        setPrimaryColor(renderConfig.actionBarColor > 0 ? renderConfig.actionBarColor : R.color.bg_action_bar_directions);
        actionBar.setPrimaryTitle(getString(renderConfig.isJourney
                        ? R.string.journey_details_title
                        : R.string.trip_details_title));
        if (!renderConfig.isJourney
                // && tripRenderer.isFeasible()
                && NetworkProviderFactory.provider(network).hasCapabilities(NetworkProvider.Capability.JOURNEY)) {
            final ImageButton navigateButton = actionBar.addButton(
                    R.drawable.ic_navigation_white_24dp,
                    R.string.directions_trip_details_action_start_routing);
            navigateButton.setOnClickListener(buttonView -> startNavigation(tripRenderer.trip, renderConfig));
        }
    }

    protected void addActionBarButtons() {
    }

    @Override
    protected void onStart() {
        super.onStart();

        // regular refresh
        tickReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isPaused) {
                    if (!checkAutoRefresh())
                        updateGUI();
                }
            }
        };
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        updateGUI();
        updateFragments();
    }

    protected boolean checkAutoRefresh() {
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        checkAutoRefresh();
        updateGUI();
    }

    @Override
    protected void onPause() {
        isPaused = true;
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (tickReceiver != null) {
            unregisterReceiver(tickReceiver);
            tickReceiver = null;
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        backgroundThread.getLooper().quit();

        locationManager.removeUpdates(TripDetailsActivity.this);

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(final Configuration config) {
        super.onConfigurationChanged(config);

        updateFragments();
    }

    @Override
    public void onBackPressed() {
        if (isShowingNextEvent())
            setShowNextEvent(false);
        else
            super.onBackPressed();
    }

    protected boolean isShowingNextEvent() {
        return findViewById(R.id.navigation_next_event).getVisibility() == View.VISIBLE;
    }

    private void goBack() {
        if (isShowingNextEvent())
            setShowNextEvent(false);
        else
            finish();
    }

    private void enableTracking() {
        final String provider = requestLocationUpdates();
        if (provider != null) {
            @SuppressLint("MissingPermission")
            final android.location.Location lastKnownLocation = locationManager
                    .getLastKnownLocation(provider);
            updateDeviceLocationDependencies(lastKnownLocation == null
                    || (lastKnownLocation.getLatitude() == 0 && lastKnownLocation.getLongitude() == 0)
                    ? null : LocationHelper.locationToPoint(lastKnownLocation), new Date());
            getMapView().setLocationAware(TripDetailsActivity.this);
        }
    }

    @SuppressLint("MissingPermission")
    private String requestLocationUpdates() {
        final Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        final String provider = locationManager.getBestProvider(criteria, true);
        if (provider != null) {
            locationManager.requestLocationUpdates(provider, 5000, 5, TripDetailsActivity.this);
            return provider;
        } else {
            new Toast(this).toast(R.string.acquire_location_no_provider);
            trackButton.setChecked(false);

            return null;
        }
    }

    public void onLocationChanged(final android.location.Location location) {
        updateDeviceLocationDependencies(LocationHelper.locationToPoint(location), new Date());
        updateGUI();
    }

    public void onProviderEnabled(final String provider) {
    }

    public void onProviderDisabled(final String provider) {
        locationManager.removeUpdates(TripDetailsActivity.this);

        final String newProvider = requestLocationUpdates();
        if (newProvider == null)
            getMapView().setLocationAware(null);
    }

    public void onStatusChanged(final String provider, final int status, final Bundle extras) {
    }

    public final Point getDeviceLocation() {
        return deviceLocation;
    }

    public final Location getReferenceLocation() {
        return null;
    }

    public final Float getDeviceBearing() {
        return null;
    }

    protected void updateFragments() {
        updateFragments(R.id.directions_trip_details_list_frame);
    }

    protected void updateGUI() {
        final Date now = new Date();
        updateHighlightedTime(now);
        updateDevInfo();

        TripRenderer.LegContainer currentLeg = null;
        int i = LEGSGROUP_INSERT_INDEX;
        for (int iLeg = 0; iLeg < tripRenderer.legs.size(); ++iLeg) {
            final TripRenderer.LegContainer legC = tripRenderer.legs.get(iLeg);
            final View legView = legsGroup.getChildAt(i);
            final boolean isCurrent;
            if (legC.publicLeg != null) {
                final int iWalk = iLeg + 1;
                TripRenderer.LegContainer walkLegC = (iWalk < tripRenderer.legs.size()) ? tripRenderer.legs.get(iWalk) : null;
                final int iNext = iLeg + 2;
                TripRenderer.LegContainer nextLegC = (iNext < tripRenderer.legs.size()) ? tripRenderer.legs.get(iNext) : null;
                isCurrent = updatePublicLeg(legView, legC, walkLegC, nextLegC, now);
            } else {
                isCurrent = updateIndividualLeg(legView, legC, now);
            }
            if (isCurrent)
                currentLeg = legC;
            i++;
        }

        tripRenderer.evaluateByTime(now);
        updateNavigationInstructions();

        final boolean showEvent;
        if (currentLeg == null) {
            showEvent = false;
            showNextEventWhenUnlocked = false;
            showNextEventWhenLocked = false;
            findViewById(R.id.directions_trip_details_finished).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.directions_trip_details_finished).setVisibility(View.GONE);
            final KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (keyguard.isKeyguardLocked()) {
                showEvent = showNextEventWhenLocked;
            } else {
                showEvent = showNextEventWhenUnlocked;
                showNextEventWhenLocked = true;
            }
        }

        View listContent = findViewById(R.id.directions_trip_details_list_content);
        listContent.setVisibility(showEvent ? View.GONE : View.VISIBLE);

        View nextEvent = findViewById(R.id.navigation_next_event);
        nextEvent.setVisibility(showEvent ? View.VISIBLE : View.GONE);
    }

    private void updateHighlightedTime(final Date now) {
        highlightedTime = null;

        final Date firstPublicLegDepartureTime = tripRenderer.trip.getFirstPublicLegDepartureTime();
        if (firstPublicLegDepartureTime == null
                || firstPublicLegDepartureTime.getTime() - now.getTime() > 10 * DateUtils.MINUTE_IN_MILLIS)
            return;

        for (final TripRenderer.LegContainer legC : tripRenderer.legs) {
            if (legC.publicLeg == null)
                continue;

            final Trip.Public publicLeg = legC.simulatedPublicLeg != null ? legC.simulatedPublicLeg : legC.publicLeg;
            Date arrivalTime, departureTime;

            departureTime = publicLeg.getDepartureTime();
            if (departureTime.after(now)) {
                highlightedTime = departureTime;
                return;
            }

            final List<Stop> intermediateStops = publicLeg.intermediateStops;
            if (intermediateStops != null) {
                for (final Stop stop : intermediateStops) {
                    departureTime = stop.getDepartureTime();
                    if (departureTime != null && departureTime.after(now)) {
                        highlightedTime = departureTime;
                        return;
                    }

                    arrivalTime = stop.getArrivalTime();
                    if (arrivalTime != null && arrivalTime.after(now)) {
                        highlightedTime = arrivalTime;
                        return;
                    }
                }
            }

            arrivalTime = publicLeg.getArrivalTime();
            if (arrivalTime.after(now)) {
                highlightedTime = arrivalTime;
                return;
            }
        }
    }

    private void updateDeviceLocationDependencies(final Point newDeviceLocation, final Date now) {
        deviceLocation = newDeviceLocation;
        deviceLocationTime = now;

        tripRenderer.setRefPoint(deviceLocation, deviceLocationTime);
        final TripRenderer.LegContainer nearestPublicLeg = tripRenderer.nearestPublicLeg;
        highlightedLocation = nearestPublicLeg == null ? null : nearestPublicLeg.nearestStop.location;
    }

    private void updateLocations() {
        final LocationTextView fromView = findViewById(R.id.directions_trip_details_location_from);
        fromView.setLabel(R.string.directions_overview_from);
        fromView.setLocation(tripRenderer.trip.from);
        final LocationTextView toView = findViewById(R.id.directions_trip_details_location_to);
        toView.setLabel(R.string.directions_overview_to);
        toView.setLocation(tripRenderer.trip.to);
    }

    private void updateFares(final List<Fare> fares) {
        final TableLayout faresTable = findViewById(R.id.directions_trip_details_fares);
        if (tripRenderer.trip.fares == null || tripRenderer.trip.fares.isEmpty())
            return;

        final String[] fareTypes = res.getStringArray(R.array.fare_types);

        int i = 0;
        for (final Fare fare : fares) {
            final View fareRow = inflater.inflate(R.layout.directions_trip_details_fares_row, null);
            ((TextView) fareRow.findViewById(R.id.directions_trip_details_fare_entry_row_type))
                    .setText(fareTypes[fare.type.ordinal()]);
            ((TextView) fareRow.findViewById(R.id.directions_trip_details_fare_entry_row_name)).setText(fare.name);
            ((TextView) fareRow.findViewById(R.id.directions_trip_details_fare_entry_row_fare))
                    .setText(String.format(Locale.US, "%s%.2f", fare.currency.getSymbol(), fare.fare));
            final TextView unitView = fareRow
                    .findViewById(R.id.directions_trip_details_fare_entry_row_unit);
            if (fare.units != null && fare.unitName != null)
                unitView.setText(String.format("(%s %s)", fare.units, fare.unitName));
            else if (fare.units == null && fare.unitName == null)
                unitView.setText(null);
            else
                unitView.setText(String.format("(%s)", MoreObjects.firstNonNull(fare.units, fare.unitName)));
            faresTable.addView(fareRow, i++);
        }

        findViewById(R.id.directions_trip_details_toggle_fares).setVisibility(View.VISIBLE);
        ((ToggleImageButton)findViewById(R.id.directions_trip_details_toggle_fares_button))
                .setOnCheckedChangeListener((buttonView, isChecked) -> {
                    findViewById(R.id.directions_trip_details_fares)
                            .setVisibility(isChecked ? View.VISIBLE : View.GONE);
                });
        ((TextView)findViewById(R.id.directions_trip_details_toggle_fares_symbol))
                .setText(fares.get(0).currency.getSymbol());
    }

    protected boolean updatePublicLeg(
            final View row,
            final TripRenderer.LegContainer legC,
            final TripRenderer.LegContainer walkLegC,
            final TripRenderer.LegContainer nextLegC,
            final Date now) {
        final boolean isSimulatedLeg = legC.simulatedPublicLeg != null;
        final Trip.Public leg = isSimulatedLeg ? legC.simulatedPublicLeg : legC.publicLeg;
        final Location destination = leg.destination;
        final String destinationName = Formats.fullLocationName(destination);
        final boolean showDestination = destinationName != null;
        final boolean showAccessibility = leg.line.hasAttr(Line.Attr.WHEEL_CHAIR_ACCESS);
        final boolean showBicycleCarriage = leg.line.hasAttr(Line.Attr.BICYCLE_CARRIAGE);
        final List<Stop> intermediateStops = leg.intermediateStops;
        boolean isRowSimulated = false;

        final LineView lineView = row.findViewById(R.id.directions_trip_details_public_entry_line);
        lineView.setLine(leg.line);
        if (showDestination || showAccessibility)
            lineView.setMaxWidth(res.getDimensionPixelSize(R.dimen.line_max_width));

        final LinearLayout lineGroup = row
                .findViewById(R.id.directions_trip_details_public_entry_line_group);
        if (showDestination)
            lineGroup.setBaselineAlignedChildIndex(0);
        else if (showAccessibility)
            lineGroup.setBaselineAlignedChildIndex(1);
        else if (showBicycleCarriage)
            lineGroup.setBaselineAlignedChildIndex(2);

        final TextView destinationView = row
                .findViewById(R.id.directions_trip_details_public_entry_destination);
        if (destination != null) {
            destinationView.setVisibility(View.VISIBLE);
            destinationView.setText(Constants.DESTINATION_ARROW_PREFIX + Formats.makeBreakableStationName(destinationName));
            destinationView.setOnClickListener(destination.hasId() ? new LocationClickListener(destination, leg.getArrivalTime()) : null);
        } else {
            destinationView.setVisibility(View.GONE);
        }

        final View accessibilityView = row.findViewById(R.id.directions_trip_details_public_entry_accessibility);
        accessibilityView.setVisibility(showAccessibility ? View.VISIBLE : View.GONE);

        final View bicycleCarriageView = row.findViewById(R.id.directions_trip_details_public_entry_bicycle_carriage);
        bicycleCarriageView.setVisibility(showBicycleCarriage ? View.VISIBLE : View.GONE);

        if (!renderConfig.isJourney && leg.journeyRef != null) {
            View.OnClickListener onClickListener = clickedView -> {
                queryJourneyRunnable = QueryJourneyRunnable.startShowJourney(
                        this, clickedView, queryJourneyRunnable,
                        handler, backgroundHandler,
                        network, leg.journeyRef, leg.departure, leg.arrival);
            };
            lineView.setClickable(true);
            lineView.setOnClickListener(onClickListener);
            destinationView.setClickable(true);
            destinationView.setOnClickListener(onClickListener);
        }

        final ToggleImageButton expandButton = row
                .findViewById(R.id.directions_trip_details_public_entry_expand);
        final Boolean checked = tripRenderer.legExpandStates.get(new TripRenderer.LegKey(leg));
        expandButton.setVisibility(
                !renderConfig.isJourney && intermediateStops != null && !intermediateStops.isEmpty()
                        ? View.VISIBLE
                        : View.GONE);
        expandButton.setChecked(checked != null ? checked : false);
        expandButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tripRenderer.legExpandStates.put(new TripRenderer.LegKey(leg), isChecked);
            updateGUI();
        });

        final TableLayout stopsView = row.findViewById(R.id.directions_trip_details_public_entry_stops);
        stopsView.removeAllViews();
        final CollapseColumns collapseColumns = new CollapseColumns();
        collapseColumns.dateChanged(now);

        final Stop departureStop = leg.departureStop;
        final Location departureLocation = departureStop.location;
        final Location entryLocation = leg.entryLocation != null ? leg.entryLocation : departureLocation;

        boolean isArrivalSection = false;

        boolean isHighlightedLocation = departureLocation.equals(highlightedLocation);
        final View departureRow = stopRow(PearlView.Type.DEPARTURE, departureStop, "-", legC,
                leg.getDepartureTime().equals(highlightedTime),
                isHighlightedLocation, now, collapseColumns, isRowSimulated);
        stopsView.addView(departureRow);
        isRowSimulated |= isHighlightedLocation;

        isArrivalSection |= departureLocation.id.equals(entryLocation.id);

        String previousPlace = departureLocation.place;

        if (intermediateStops != null) {
            if (expandButton.isChecked()) {
                for (final Stop stop : intermediateStops) {
                    final Location stopLocation = stop.location;
                    final Date arrivalTime = stop.getArrivalTime();
                    final Date departureTime = stop.getDepartureTime();
                    final boolean hasStopTime = arrivalTime != null || departureTime != null;
                    final boolean isArrivalTimeHighlighted = arrivalTime != null && arrivalTime.equals(highlightedTime);
                    final boolean isDepartureTimeHighlighted = departureTime != null && departureTime.equals(highlightedTime);

                    // more than 5 minutes stay, then show departure row
                    final boolean isLongStay = isLongStay(stop.plannedArrivalTime, stop.plannedDepartureTime)
                            || isLongStay(stop.predictedArrivalTime, stop.predictedDepartureTime);

                    isHighlightedLocation = stopLocation.equals(highlightedLocation);
                    if (isArrivalSection) {
                        final View stopRow = stopRow(hasStopTime ? PearlView.Type.INTERMEDIATE_ARRIVAL : PearlView.Type.PASSING,
                                stop, previousPlace, legC,
                                isArrivalTimeHighlighted || (!isLongStay && isDepartureTimeHighlighted),
                                isHighlightedLocation, now, collapseColumns, isRowSimulated);
                        stopsView.addView(stopRow);

                        if (isLongStay) {
                            final View depRow = stopRow(PearlView.Type.DEPARTURE_FOR_INTERMEDIATE_ARRIVAL,
                                    stop, previousPlace, legC,
                                    isDepartureTimeHighlighted,
                                    isHighlightedLocation, now, collapseColumns, isRowSimulated);
                            stopsView.addView(depRow);
                        }
                    } else {
                        if (isLongStay) {
                            final View depRow = stopRow(PearlView.Type.ARRIVAL_FOR_INTERMEDIATE_DEPARTURE,
                                    stop, previousPlace, legC,
                                    isDepartureTimeHighlighted,
                                    isHighlightedLocation, now, collapseColumns, isRowSimulated);
                            stopsView.addView(depRow);
                        }

                        final View stopRow = stopRow(hasStopTime ? PearlView.Type.INTERMEDIATE_DEPARTURE : PearlView.Type.PASSING,
                                stop, previousPlace, legC,
                                isDepartureTimeHighlighted || (!isLongStay && isArrivalTimeHighlighted),
                                isHighlightedLocation, now, collapseColumns, isRowSimulated);
                        stopsView.addView(stopRow);
                    }
                    isRowSimulated |= isHighlightedLocation;

                    previousPlace = stopLocation.place;
                    isArrivalSection |= stopLocation.id.equals(entryLocation.id);
                }
            } else {
                int numIntermediateStops = 0;
                for (final Stop stop : intermediateStops) {
                    final boolean hasStopTime = stop.getArrivalTime() != null || stop.getDepartureTime() != null;
                    if (hasStopTime)
                        numIntermediateStops++;
                }

                if (numIntermediateStops > 0) {
                    final View collapsedIntermediateStopsRow = collapsedIntermediateStopsRow(numIntermediateStops,
                            leg.line.style);
                    stopsView.addView(collapsedIntermediateStopsRow);
                    collapsedIntermediateStopsRow.setOnClickListener(v -> expandButton.setChecked(true));
                }
            }
        }

        isArrivalSection = true;

        isHighlightedLocation = leg.arrivalStop.location.equals(highlightedLocation);
        final View arrivalRow = stopRow(PearlView.Type.ARRIVAL, leg.arrivalStop, previousPlace, legC,
                leg.getArrivalTime().equals(highlightedTime),
                isHighlightedLocation, now, collapseColumns, isRowSimulated);
        stopsView.addView(arrivalRow);
        isRowSimulated |= isHighlightedLocation;

        stopsView.setColumnCollapsed(1, collapseColumns.collapseDateColumn);
        stopsView.setColumnCollapsed(3, collapseColumns.collapseDelayColumn);
        stopsView.setColumnCollapsed(4, collapseColumns.collapsePositionColumn);

        final TextView messageView = row.findViewById(R.id.directions_trip_details_public_entry_message);
        final String message = leg.message != null ? leg.message : leg.line.message;
        if (message != null) {
            messageView.setVisibility(View.VISIBLE);
            Spanned html = Html.fromHtml(HtmlUtils.makeLinksClickableInHtml(message), Html.FROM_HTML_MODE_COMPACT);
            messageView.setText(html);
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            messageView.setVisibility(View.GONE);
        }

        final TextView devinfoView = row.findViewById(R.id.directions_trip_details_public_entry_devinfo);
        if (isDeveloperElementsEnabled()) {
            devinfoView.setVisibility(View.VISIBLE);
            final Date updateDelayedUntil = leg.updateDelayedUntil;
            devinfoView.setText(String.format("loaded at %s, %s",
                    Formats.formatTime(this, leg.loadedAt.getTime()),
                    updateDelayedUntil == null ? "is fresh"
                            : String.format("next update at %s", Formats.formatTime(this, updateDelayedUntil.getTime()))));
        } else {
            devinfoView.setVisibility(View.GONE);
        }

        final View progressView = row.findViewById(R.id.directions_trip_details_public_entry_progress);
        progressView.setVisibility(View.GONE);
        Date beginTime = departureStop.getDepartureTime();
        Date endTime = leg.arrivalStop.getArrivalTime();
        if (now.before(beginTime)) {
            // leg is in the future
            row.setBackgroundColor(colorLegPublicFutureBackground);
            return false;
        } else if (now.after(endTime)) {
            // leg is in the past
            row.setBackgroundColor(colorLegPublicPastBackground);
            return false;
        }

        // leg is now
        row.setBackgroundColor(colorLegPublicNowBackground);
        progressView.setVisibility(View.VISIBLE);
        progressView.setOnClickListener(view -> setShowNextEvent(true));

        final TextView progressText = row.findViewById(R.id.directions_trip_details_public_entry_progress_text);
        progressText.setText(getLeftTimeFormatted(now, endTime));

        return true;
    }

    private boolean isLongStay(final Date arrivalTime, final Date departureDate) {
        if (arrivalTime == null)
            return false;
        if (departureDate == null)
            return false;
        final long stayMillis = departureDate.getTime() - arrivalTime.getTime();
        return stayMillis >= 300000; // 5 minutes
    }

    protected boolean updateIndividualLeg(final View row, final TripRenderer.LegContainer legC, final Date now) {
        final Trip.Individual leg = legC.individualLeg;
        String legText = null;
        final int iconResId;
        int requiredSecs = 0;
        final ImageButton mapView = row.findViewById(R.id.directions_trip_details_individual_entry_map);
        mapView.setVisibility(View.GONE);
        mapView.setOnClickListener(null);
        if (leg != null) {
            requiredSecs = leg.min * 60;
            final String distanceStr = leg.distance != 0 ? "(" + leg.distance + "m) " : "";
            final int textResId;
            if (leg.type == Trip.Individual.Type.WALK) {
                textResId = R.string.directions_trip_details_walk;
                iconResId = R.drawable.ic_directions_walk_grey600_24dp;
            } else if (leg.type == Trip.Individual.Type.BIKE) {
                textResId = R.string.directions_trip_details_bike;
                iconResId = R.drawable.ic_directions_bike_grey600_24dp;
            } else if (leg.type == Trip.Individual.Type.CAR) {
                textResId = R.string.directions_trip_details_car;
                iconResId = R.drawable.ic_local_taxi_grey600_24dp;
            } else if (leg.type == Trip.Individual.Type.TRANSFER) {
                textResId = R.string.directions_trip_details_transfer;
                iconResId = R.drawable.ic_local_taxi_grey600_24dp;
            } else {
                throw new IllegalStateException("unknown type: " + leg.type);
            }
            legText = getString(textResId, leg.min, distanceStr, Formats.makeBreakableStationName(leg.arrival.uniqueShortName()));

            if (leg.arrival.hasCoord()) {
                mapView.setVisibility(View.VISIBLE);
                mapView.setOnClickListener(new MapClickListener(leg.arrival));
            }
        } else if (legC.transferFrom != null) {
            // no time walk after some public transport
//            iconResId = R.drawable.ic_directions_walk_grey600_24dp;
            iconResId = 0;
        } else {
            // walk before anything
            iconResId = 0;
        }

        String transferText = null;
        String timeText = null;
        boolean timeIsCritical = false;
        final Stop transferFrom = legC.transferFrom != null ? legC.transferFrom.publicLeg.arrivalStop : null;
        final Stop transferTo = legC.transferTo != null ? legC.transferTo.publicLeg.departureStop : null;
        if (transferFrom == null) {
            // walk at the beginning
        } else if (transferTo == null) {
            // walk at the end
        } else {
            final Date arrMinTime = transferFrom.plannedArrivalTime;
            final Date arrMaxTime = transferFrom.predictedArrivalTime != null ? transferFrom.predictedArrivalTime : arrMinTime;
            final Date depMinTime = transferTo.plannedDepartureTime;
            final Date depMaxTime = transferTo.predictedDepartureTime != null ? transferTo.predictedDepartureTime : depMinTime;
            long diffMinSecs = (depMinTime.getTime() - arrMaxTime.getTime()) / 1000 - 60;
            long diffMaxSecs = (depMaxTime.getTime() - arrMaxTime.getTime()) / 1000 - 60;
            long leftMinSecs = diffMinSecs - requiredSecs;
            long leftMaxSecs = diffMaxSecs - requiredSecs;
            timeText = Long.toString((diffMaxSecs + 30) / 60);
            if (diffMaxSecs < 0) {
                timeIsCritical = true;
                transferText = getString(R.string.directions_trip_conneval_missed, (-diffMaxSecs - 60) / 60);
                timeText = Long.toString(-((-diffMaxSecs - 30) / 60));
            } else if (leftMaxSecs < 0) {
                timeIsCritical = true;
                if (diffMinSecs < 0) {
                    transferText = getString(R.string.directions_trip_conneval_difficult_possibly_missed, diffMaxSecs / 60);
                } else {
                    transferText = getString(R.string.directions_trip_conneval_difficult, diffMaxSecs / 60);
                }
            } else if (leftMaxSecs < 180) {
                timeIsCritical = true;
                if (leftMinSecs < 0) {
                    transferText = getString(R.string.directions_trip_conneval_endangered_possibly_difficult, diffMaxSecs / 60);
                } else {
                    transferText = getString(R.string.directions_trip_conneval_endangered, diffMaxSecs / 60);
                }
            } else if (leftMinSecs < 0) {
                timeIsCritical = true;
                transferText = getString(R.string.directions_trip_conneval_possibly_difficult, diffMaxSecs / 60);
            } else if (leftMinSecs < 180) {
                transferText = getString(R.string.directions_trip_conneval_possibly_endangered, diffMaxSecs / 60);
            } else if (leftMinSecs != leftMaxSecs) {
                transferText = getString(R.string.directions_trip_conneval_possibly_good, diffMaxSecs / 60);
            } else {
                transferText = getString(R.string.directions_trip_conneval_good, diffMaxSecs / 60);
            }
        }

        final String text =
                  transferText == null ? legText
                : legText == null ? transferText
                : getString(R.string.directions_trip_conneval_with_transfer, transferText, legText);

        final TextView textView = row.findViewById(R.id.directions_trip_details_individual_entry_text);
        if (text == null) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            textView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT));
        }

        final ImageView iconView = row.findViewById(R.id.directions_trip_details_individual_entry_icon);
        if (iconResId == 0) {
            iconView.setVisibility(View.GONE);
        } else {
            iconView.setVisibility(View.VISIBLE);
            iconView.setImageDrawable(getDrawable(iconResId));
        }

        final View timeView = row.findViewById(R.id.directions_trip_details_individual_entry_time);
        if (timeText == null) {
            timeView.setVisibility(View.GONE);
        } else {
            timeView.setVisibility(View.VISIBLE);
            final TextView timeTextView = row.findViewById(R.id.directions_trip_details_individual_entry_time_text);
            timeTextView.setText(timeText);
            timeTextView.setTextColor(getColor(timeIsCritical ? R.color.fg_trip_next_event_important : R.color.fg_significant));
        }

        final View progressView = row.findViewById(R.id.directions_trip_details_individual_entry_progress);
        progressView.setVisibility(View.GONE);
        Date beginTime = transferFrom != null ? transferFrom.getArrivalTime() : leg == null ? null : leg.departureTime;
        Date endTime = transferTo != null ? transferTo.getDepartureTime() : leg == null ? null : leg.arrivalTime;
        if (transferFrom != null && beginTime != null && now.before(beginTime)) {
            // leg is in the future
            row.setBackgroundColor(colorLegIndividualFutureBackground);
            return false;
        } else if (endTime != null && now.after(endTime)) {
            // leg is in the past
            row.setBackgroundColor(colorLegIndividualPastBackground);
            return false;
        }

        // leg is now
        row.setBackgroundColor(colorLegIndividualNowBackground);
        progressView.setVisibility(View.VISIBLE);
        progressView.setOnClickListener(view -> setShowNextEvent(true));

        final TextView progressText = row.findViewById(R.id.directions_trip_details_individual_entry_progress_text);
        progressText.setText(getLeftTimeFormatted(now, endTime));

        return true;
    }

    protected void setShowNextEvent(final boolean showNextEvent) {
        final KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguard.isKeyguardLocked()) {
            showNextEventWhenLocked = showNextEvent;
            showNextEventWhenUnlocked = showNextEvent;
        } else {
            showNextEventWhenUnlocked = showNextEvent;
            showNextEventWhenLocked = true;
        }
        updateGUI();
    }

    private void setViewBackgroundColor(final int viewId, final int color) {
        findViewById(viewId).setBackgroundColor(color);
    }

    protected void updateNavigationInstructions() {
        final int colorHighlight = getColor(R.color.bg_trip_details_public_now);
        final int colorNormal = getColor(R.color.bg_level0);
        final int colorHighIfPublic = tripRenderer.nextEventTypeIsPublic ? colorHighlight : colorNormal;
        final int colorHighIfChangeover = tripRenderer.nextEventTypeIsPublic ? colorNormal : colorHighlight;
        setViewBackgroundColor(R.id.navigation_next_event_current_action, colorHighlight);
        setViewBackgroundColor(R.id.navigation_next_event_next_action, colorNormal);
        setViewBackgroundColor(R.id.navigation_next_event_time, colorHighlight);
        setViewBackgroundColor(R.id.navigation_next_event_target, colorHighlight);
        setViewBackgroundColor(R.id.navigation_next_event_positions, colorHighIfChangeover);
        setViewBackgroundColor(R.id.navigation_next_event_departure, colorNormal);
        setViewBackgroundColor(R.id.navigation_next_event_connection, colorHighIfChangeover);
        setViewBackgroundColor(R.id.navigation_next_event_changeover, colorHighIfChangeover);
        setViewBackgroundColor(R.id.navigation_next_event_clock, colorNormal);

        TextView clock = findViewById(R.id.navigation_next_event_clock);
        clock.setText(Formats.formatTime(this, tripRenderer.nextEventClock.getTime()));

        TextView currentAction = findViewById(R.id.navigation_next_event_current_action);
        if (tripRenderer.nextEventCurrentStringId > 0) {
            final String s = getString(tripRenderer.nextEventCurrentStringId);
            currentAction.setText(s);
            currentAction.setVisibility(View.VISIBLE);
        } else {
            currentAction.setVisibility(View.GONE);
        }

        TextView nextAction = findViewById(R.id.navigation_next_event_next_action);
        if (tripRenderer.nextEventNextStringId > 0) {
            final String s = getString(tripRenderer.nextEventNextStringId);
            nextAction.setText(s);
            nextAction.setVisibility(View.VISIBLE);
        } else {
            nextAction.setVisibility(View.GONE);
        }

        final TextView valueView = findViewById(R.id.navigation_next_event_time_value);
        final Chronometer valueChronoView = findViewById(R.id.navigation_next_event_time_chronometer);
        valueChronoView.stop();
        final String valueStr = tripRenderer.nextEventTimeLeftValue;
        final String chronoFormat = tripRenderer.nextEventTimeLeftChronometerFormat;
        if (valueStr != null) {
            if (chronoFormat != null) {
                valueChronoView.setVisibility(View.VISIBLE);
                valueView.setVisibility(View.GONE);
                valueChronoView.setTextColor(getColor(tripRenderer.nextEventTimeLeftCritical ? R.color.fg_trip_next_event_important : R.color.fg_trip_next_event_normal));
                valueChronoView.setBase(ClockUtils.clockToElapsedTime(tripRenderer.nextEventEstimatedTime.getTime()));
                valueChronoView.setCountDown(true);
                valueChronoView.setFormat(chronoFormat);
                valueChronoView.start();
            } else {
                valueView.setVisibility(View.VISIBLE);
                valueChronoView.setVisibility(View.GONE);
                valueView.setTextColor(getColor(tripRenderer.nextEventTimeLeftCritical ? R.color.fg_trip_next_event_important : R.color.fg_trip_next_event_normal));
                if (TripRenderer.NO_TIME_LEFT_VALUE.equals(valueStr))
                    valueView.setText(R.string.navigation_next_event_no_time_left);
                else
                    valueView.setText(valueStr);
            }
        }
        // valueView.setTextColor(getColor(tripRenderer.nextEventTimeLeftCritical ? R.color.fg_trip_next_event_important : R.color.fg_trip_next_event_normal));
        TextView unitView = findViewById(R.id.navigation_next_event_time_unit);
        unitView.setText(tripRenderer.nextEventTimeLeftUnit);
        findViewById(R.id.navigation_next_event_time_hourglass)
                .setVisibility(tripRenderer.nextEventTimeHourglassVisible ? View.VISIBLE : View.GONE);
        TextView explainView = findViewById(R.id.navigation_next_event_time_explain);
        if (tripRenderer.nextEventTimeLeftExplainStr != null) {
            explainView.setVisibility(View.VISIBLE);
            explainView.setText(tripRenderer.nextEventTimeLeftExplainStr);
        } else {
            explainView.setVisibility(View.GONE);
        }

        TextView targetView = findViewById(R.id.navigation_next_event_target);
        if (tripRenderer.nextEventTargetName != null) {
            targetView.setText(tripRenderer.nextEventTargetName);
            targetView.setVisibility(View.VISIBLE);
        } else {
            targetView.setVisibility(View.GONE);
        }

        findViewById(R.id.navigation_next_event_positions)
            .setVisibility(tripRenderer.nextEventPositionsAvailable ? View.VISIBLE : View.GONE);

        final TextView from = findViewById(R.id.navigation_next_event_position_from);
        final TextView to = findViewById(R.id.navigation_next_event_position_to);

        if (tripRenderer.nextEventArrivalPosName != null) {
            from.setVisibility(View.VISIBLE);
            from.setText(tripRenderer.nextEventArrivalPosName);
            from.setBackgroundColor(tripRenderer.nextEventArrivalPosChanged ? colorPositionBackgroundChanged : colorPositionBackground);
        } else {
            from.setVisibility(View.GONE);
        }

        if (tripRenderer.nextEventDeparturePosName != null) {
            to.setVisibility(View.VISIBLE);
            to.setText(tripRenderer.nextEventDeparturePosName);
            to.setBackgroundColor(tripRenderer.nextEventDeparturePosChanged ? colorPositionBackgroundChanged : colorPositionBackground);
        } else {
            to.setVisibility(View.GONE);
        }

        final ImageView positionsWalkIcon = findViewById(R.id.navigation_next_event_positions_walk_icon);
        final View positionsWalkArrow = findViewById(R.id.navigation_next_event_positions_walk_arrow);
        if (tripRenderer.nextEventStopChange) {
            final int iconId = tripRenderer.nextEventTransferIconId;
            positionsWalkIcon.setImageDrawable(res.getDrawable(iconId != 0 ? iconId : R.drawable.ic_directions_walk_grey600_24dp));
            positionsWalkIcon.setVisibility(View.VISIBLE);
            positionsWalkArrow.setVisibility(View.VISIBLE);
        } else {
            positionsWalkIcon.setVisibility(View.GONE);
            positionsWalkArrow.setVisibility(View.GONE);
        }

        if (tripRenderer.nextEventTransportLine == null) {
            findViewById(R.id.navigation_next_event_connection).setVisibility(View.GONE);
        } else {
            findViewById(R.id.navigation_next_event_connection).setVisibility(View.VISIBLE);

            final LineView lineView = findViewById(R.id.navigation_next_event_connection_line);
            lineView.setVisibility(View.VISIBLE);
            lineView.setLine(tripRenderer.nextEventTransportLine);

            TextView destView = findViewById(R.id.navigation_next_event_connection_to);
            destView.setText(tripRenderer.nextEventTransportDestinationName);
        }

        if (!tripRenderer.nextEventChangeOverAvailable) {
            findViewById(R.id.navigation_next_event_changeover)
                    .setVisibility(View.GONE);
        } else {
            findViewById(R.id.navigation_next_event_changeover)
                    .setVisibility(View.VISIBLE);

            if (tripRenderer.nextEventTransferAvailable) {
                findViewById(R.id.navigation_next_event_transfer)
                        .setVisibility(View.VISIBLE);

                TextView transferValueView = findViewById(R.id.navigation_next_event_transfer_value);
                transferValueView.setText(tripRenderer.nextEventTransferLeftTimeValue);
                transferValueView.setTextColor(getColor(tripRenderer.nextEventTransferLeftTimeCritical
                        ? R.color.fg_trip_next_event_important
                        : R.color.fg_trip_next_event_normal));

                if (tripRenderer.nextEventTransferLeftTimeFromNowValue != null) {
                    findViewById(R.id.navigation_next_event_transfer_time)
                            .setVisibility(View.VISIBLE);

                    TextView transferTimeValueView = findViewById(R.id.navigation_next_event_transfer_time_value);
                    transferTimeValueView.setText(tripRenderer.nextEventTransferLeftTimeFromNowValue);
                } else {
                    findViewById(R.id.navigation_next_event_transfer_time)
                            .setVisibility(View.GONE);
                }

                TextView transferExplainView = findViewById(R.id.navigation_next_event_transfer_explain);
                if (tripRenderer.nextEventTransferExplain != null) {
                    transferExplainView.setVisibility(View.VISIBLE);
                    transferExplainView.setText(tripRenderer.nextEventTransferExplain);
                } else {
                    transferExplainView.setVisibility(View.GONE);
                }
            } else {
                findViewById(R.id.navigation_next_event_transfer)
                        .setVisibility(View.GONE);
            }

            if (tripRenderer.nextEventTransferWalkAvailable) {
                findViewById(R.id.navigation_next_event_walk)
                        .setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.navigation_next_event_walk_value))
                        .setText(tripRenderer.nextEventTransferWalkTimeValue);
                ((ImageView) findViewById(R.id.navigation_next_event_walk_icon))
                        .setImageDrawable(res.getDrawable(tripRenderer.nextEventTransferIconId));
            } else {
                findViewById(R.id.navigation_next_event_walk)
                        .setVisibility(View.GONE);
            }

            if (tripRenderer.nextPublicLegDurationTimeValue != null) {
                findViewById(R.id.navigation_next_event_upcoming_ride)
                        .setVisibility(View.VISIBLE);

                ((TextView) findViewById(R.id.navigation_next_event_upcoming_ride_value))
                        .setText(tripRenderer.nextPublicLegDurationTimeValue);
            } else {
                findViewById(R.id.navigation_next_event_upcoming_ride)
                        .setVisibility(View.GONE);
            }
        }

        TextView depView = findViewById(R.id.navigation_next_event_departure);
        depView.setText(tripRenderer.nextEventDepartureName);
        depView.setVisibility(tripRenderer.nextEventDepartureName != null ? View.VISIBLE : View.GONE);
    }

    private Spanned getLeftTimeFormatted(final Date now, final Date endTime) {
        long leftSeconds = (endTime.getTime() - now.getTime()) / 1000;
        final String leftText;
        if (leftSeconds < 70) {
            leftText = getString(R.string.directions_trip_details_no_time_left);
        } else {
            final long leftMinutes = leftSeconds / 60;
            if (leftMinutes < 120) {
                leftText = getString(R.string.directions_trip_details_time_left_minutes, leftMinutes);
            } else {
                final long leftHours = leftMinutes / 60;
                if (leftHours < 24) {
                    leftText = getString(R.string.directions_trip_details_time_left_hours, leftHours);
                } else {
                    final long leftDays = leftHours / 24;
                    leftText = getString(R.string.directions_trip_details_time_left_days, leftDays);
                }
            }
        }
        return Html.fromHtml(leftText);
    }

    private class CollapseColumns {
        private boolean collapseDateColumn = true;
        private boolean collapsePositionColumn = true;
        private boolean collapseDelayColumn = true;
        private Calendar c = new GregorianCalendar();

        public boolean dateChanged(final Date time) {
            final int oldYear = c.get(Calendar.YEAR);
            final int oldDayOfYear = c.get(Calendar.DAY_OF_YEAR);
            c.setTime(time);
            return c.get(Calendar.YEAR) != oldYear || c.get(Calendar.DAY_OF_YEAR) != oldDayOfYear;
        }
    }

    private View stopRow(
            final PearlView.Type pearlType,
            final Stop stop, final String previousPlace, final TripRenderer.LegContainer legC,
            final boolean highlightTime, final boolean highlightLocation,
            final Date now, final CollapseColumns collapseColumns, final boolean isSimulatedLeg) {
        final View row = inflater.inflate(R.layout.directions_trip_details_public_entry_stop, null);
        final Trip.Public leg = legC.publicLeg;

        final boolean isTimePredicted;
        final Date time;
        final boolean isTimeDeparture;
        final Long delay;
        final boolean isCancelled;
        final boolean isPositionPredicted;
        final Position position;
        final boolean positionChanged;
        final Location location = stop.location;
        final Style style = leg.line.style;

        final boolean isEntryOrExit =
                ((leg.entryLocation != null && location.id.equals(leg.entryLocation.id))
                    || (leg.exitLocation != null && location.id.equals(leg.exitLocation.id)))
                && !(pearlType == PearlView.Type.DEPARTURE_FOR_INTERMEDIATE_ARRIVAL
                    || pearlType == PearlView.Type.ARRIVAL_FOR_INTERMEDIATE_DEPARTURE);

        if (pearlType == PearlView.Type.DEPARTURE
                || pearlType == PearlView.Type.INTERMEDIATE_DEPARTURE
                || pearlType == PearlView.Type.DEPARTURE_FOR_INTERMEDIATE_ARRIVAL
                || (pearlType != PearlView.Type.ARRIVAL && stop.plannedArrivalTime == null)) {
            isTimeDeparture = true;
            isTimePredicted = stop.isDepartureTimePredicted();
            time = stop.getDepartureTime();
            delay = stop.getDepartureDelay();
            isCancelled = stop.departureCancelled;

            isPositionPredicted = stop.isDeparturePositionPredicted();
            position = stop.getDeparturePosition();
            positionChanged = position != null && !position.equals(stop.plannedDeparturePosition);
        } else if ((pearlType == PearlView.Type.ARRIVAL
                        || pearlType == PearlView.Type.INTERMEDIATE_ARRIVAL
                        || pearlType == PearlView.Type.ARRIVAL_FOR_INTERMEDIATE_DEPARTURE
                        || pearlType == PearlView.Type.PASSING)
                    && stop.plannedArrivalTime != null) {
            isTimeDeparture = false;
            isTimePredicted = stop.isArrivalTimePredicted();
            time = stop.getArrivalTime();
            delay = stop.getArrivalDelay();
            isCancelled = stop.arrivalCancelled;

            isPositionPredicted = stop.isArrivalPositionPredicted();
            position = stop.getArrivalPosition();
            positionChanged = position != null && !position.equals(stop.plannedArrivalPosition);
        } else {
            throw new IllegalStateException("cannot handle: " + pearlType);
        }

        // name
        final TextView stopNameView = row.findViewById(R.id.directions_trip_details_public_entry_stop_name);
        final String name = location.name;
        final String place = location.place;
        final String uniqueShortName;
        if (place != null && name != null) {
            if (place.equals(previousPlace))
                uniqueShortName = name;
            else
                uniqueShortName = "<i><u>" + place + "</u></i><br>" + name;
        } else {
            uniqueShortName = location.uniqueShortName();
        }
        stopNameView.setText(Html.fromHtml(Formats.makeBreakableStationName(
                pearlType == PearlView.Type.DEPARTURE_FOR_INTERMEDIATE_ARRIVAL
                        ? getString(R.string.directions_trip_details_departure_row_name_format, uniqueShortName)
                        : pearlType == PearlView.Type.ARRIVAL_FOR_INTERMEDIATE_DEPARTURE
                        ? getString(R.string.directions_trip_details_arrival_row_name_format, uniqueShortName)
                        : uniqueShortName), Html.FROM_HTML_MODE_COMPACT));
        setStrikeThru(stopNameView, isCancelled);
        if (highlightLocation) {
            stopNameView.setTextColor(colorHighlighted);
            stopNameView.setTypeface(null, Typeface.BOLD);
        } else if (renderConfig.isJourney ? isEntryOrExit
                : (pearlType == PearlView.Type.DEPARTURE || pearlType == PearlView.Type.ARRIVAL)) {
            stopNameView.setTextColor(colorSignificant);
            stopNameView.setTypeface(null, Typeface.BOLD);
        } else if (pearlType == PearlView.Type.PASSING) {
            stopNameView.setTextColor(colorInsignificant);
            stopNameView.setTypeface(null, Typeface.NORMAL);
        } else {
            stopNameView.setTextColor(colorSignificant);
            stopNameView.setTypeface(null, Typeface.NORMAL);
        }
        if (location.hasId()) {
            JourneyRef feederJourneyRef = leg.journeyRef;
            JourneyRef connectionJourneyRef = leg.journeyRef;
            boolean stopIsLegDeparture = false;
            boolean stopIsLegArrival = false;
            if (stop.location.id.equals(leg.departureStop.location.id)
                    && stop.plannedDepartureTime != null
                    && stop.plannedDepartureTime.equals(leg.departureStop.plannedDepartureTime)) {
                // departure stop of a journey, find previous journey as feeder
                stopIsLegDeparture = true;
                feederJourneyRef = null;
                for (final TripRenderer.LegContainer iLegC : tripRenderer.legs) {
                    if (iLegC.publicLeg != null) {
                        if (iLegC.publicLeg == leg)
                            break;
                        else
                            feederJourneyRef = iLegC.publicLeg.journeyRef;
                    }
                }
            } else if (stop.location.id.equals(leg.arrivalStop.location.id)
                    && stop.plannedArrivalTime != null
                    && stop.plannedArrivalTime.equals(leg.arrivalStop.plannedArrivalTime)) {
                // arrival stop of a journey, find next journey as connection
                stopIsLegArrival = true;
                connectionJourneyRef = null;
                boolean found = false;
                for (final TripRenderer.LegContainer iLegC : tripRenderer.legs) {
                    if (iLegC.publicLeg != null) {
                        if (found) {
                            connectionJourneyRef = iLegC.publicLeg.journeyRef;
                            break;
                        } else if (iLegC.publicLeg == leg) {
                            found = true;
                        }
                    }
                }
            }
            stopNameView.setOnClickListener(new StopClickListener(
                    legC, stop, stopIsLegDeparture, stopIsLegArrival,
                    leg.journeyRef, feederJourneyRef, connectionJourneyRef));
        } else {
            stopNameView.setOnClickListener(null);
        }

        // pearl
        final PearlView pearlView = row.findViewById(R.id.directions_trip_details_public_entry_stop_pearl);
        pearlView.setType(pearlType);
        pearlView.setStyle(style);
        pearlView.setFontMetrics(stopNameView.getPaint().getFontMetrics());

        // time
        final TextView stopDateView = row.findViewById(R.id.directions_trip_details_public_entry_stop_date);
        final TextView stopTimeView = row.findViewById(R.id.directions_trip_details_public_entry_stop_time);
        stopDateView.setText(null);
        stopTimeView.setText(null);
        if (time != null) {
            if (collapseColumns.dateChanged(time)) {
                stopDateView.setText(Formats.formatDate(TripDetailsActivity.this, now.getTime(), time.getTime(), true,
                        res.getString(R.string.time_today_abbrev)));
                collapseColumns.collapseDateColumn = false;
            }
            final String timeText = Formats.formatTime(TripDetailsActivity.this, time.getTime());
            stopTimeView.setText(isTimeDeparture ? timeText + "°" : timeText);
            setStrikeThru(stopTimeView, isCancelled);
        }
        final int stopTimeColor =
                highlightTime ? colorHighlighted
                : isSimulatedLeg ? colorSimulated
                : colorSignificant;
        stopDateView.setTextColor(stopTimeColor);
        stopTimeView.setTextColor(stopTimeColor);
        final boolean stopTimeBold = highlightTime || (renderConfig.isJourney ? isEntryOrExit
                : (pearlType == PearlView.Type.DEPARTURE || pearlType == PearlView.Type.ARRIVAL));
        stopDateView.setTypeface(null, (highlightTime ? Typeface.BOLD : 0) + (isTimePredicted ? Typeface.ITALIC : 0));
        stopTimeView.setTypeface(null, (stopTimeBold ? Typeface.BOLD : 0) + (isTimePredicted ? Typeface.ITALIC : 0));

        // delay
        final TextView stopDelayView = row
                .findViewById(R.id.directions_trip_details_public_entry_stop_delay);
        if (delay != null) {
            final long delayMins = delay / DateUtils.MINUTE_IN_MILLIS;
            if (delayMins != 0) {
                collapseColumns.collapseDelayColumn = false;
                stopDelayView.setText(String.format("(%+d)", delayMins));
                stopDelayView.setTypeface(Typeface.DEFAULT, isTimePredicted ? Typeface.ITALIC : Typeface.NORMAL);
            }
        }

        // position
        final TextView stopPositionView = row
                .findViewById(R.id.directions_trip_details_public_entry_stop_position);
        if (position != null
                && !isCancelled
                && pearlType != PearlView.Type.DEPARTURE_FOR_INTERMEDIATE_ARRIVAL
                && pearlType != PearlView.Type.ARRIVAL_FOR_INTERMEDIATE_DEPARTURE) {
            collapseColumns.collapsePositionColumn = false;
            final SpannableStringBuilder positionStr = new SpannableStringBuilder(position.name);
            if (position.section != null) {
                final int sectionStart = positionStr.length();
                positionStr.append(position.section);
                positionStr.setSpan(new RelativeSizeSpan(0.85f), sectionStart, positionStr.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            stopPositionView.setText(positionStr);
            stopPositionView.setTypeface(null, Typeface.BOLD + (isPositionPredicted ? Typeface.ITALIC : 0));
            final int padding = (int) (2 * displayMetrics.density);
            stopPositionView.setBackgroundDrawable(new ColorDrawable(
                    positionChanged ? colorPositionBackgroundChanged : colorPositionBackground));
            stopPositionView.setTextColor(colorPosition);
            stopPositionView.setPadding(padding, 0, padding, 0);
        }

        return row;
    }

    private View collapsedIntermediateStopsRow(final int numIntermediateStops, final Style style) {
        final View row = inflater.inflate(R.layout.directions_trip_details_public_entry_collapsed, null);

        // message
        final TextView stopNameView = row
                .findViewById(R.id.directions_trip_details_public_entry_collapsed_message);
        stopNameView.setText(
                res.getQuantityString(R.plurals.directions_trip_details_public_entry_collapsed_intermediate_stops,
                        numIntermediateStops, numIntermediateStops));
        stopNameView.setTextColor(colorInsignificant);

        // pearl
        final PearlView pearlView = row
                .findViewById(R.id.directions_trip_details_public_entry_collapsed_pearl);
        pearlView.setType(PearlView.Type.PASSING);
        pearlView.setStyle(style);
        pearlView.setFontMetrics(stopNameView.getPaint().getFontMetrics());

        return row;
    }

    private void setStrikeThru(final TextView view, final boolean strikeThru) {
        if (strikeThru)
            view.setPaintFlags(view.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        else
            view.setPaintFlags(view.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }

    private class LocationClickListener implements android.view.View.OnClickListener {
        private final Location location;
        private final Date time;

        public LocationClickListener(final Location location, final Date time) {
            this.location = location;
            this.time = time;
        }

        public void onClick(final View v) {
            final PopupMenu contextMenu = new StationContextMenu(TripDetailsActivity.this, v, network, location, null,
                    false, false, true,
                    true, true,
                    false, false,
                    false, false, false,
                    renderConfig.isJourney && ((Trip.Public) tripRenderer.trip.legs.get(0)).exitLocation == null,
                    false);
            contextMenu.setOnMenuItemClickListener(item -> {
                final int itemId = item.getItemId();
                if (itemId == R.id.station_context_show_departures) {
                    StationDetailsActivity.start(TripDetailsActivity.this, network, location, time, null);
                    return true;
                } else if (itemId == R.id.station_context_nearby_departures) {
                    StationsActivity.start(TripDetailsActivity.this, network, location, time);
                    return true;
                } else {
                    return false;
                }
            });
            contextMenu.show();
        }
    }

    private class StopClickListener implements android.view.View.OnClickListener {
        private final TripRenderer.LegContainer legC;
        private final Stop stop;
        private final boolean stopIsLegDeparture, stopIsLegArrival;
        private final JourneyRef currentJourneyRef;
        private final JourneyRef feederJourneyRef;
        private final JourneyRef connectionJourneyRef;

        public StopClickListener(
                final TripRenderer.LegContainer legC,
                final Stop stop,
                final boolean stopIsLegDeparture,
                final boolean stopIsLegArrival,
                final JourneyRef currentJourneyRef,
                final JourneyRef feederJourneyRef,
                final JourneyRef connectionJourneyRef) {
            this.legC = legC;
            this.stop = stop;
            this.stopIsLegDeparture = stopIsLegDeparture;
            this.stopIsLegArrival = stopIsLegArrival;
            this.currentJourneyRef = currentJourneyRef;
            this.feederJourneyRef = feederJourneyRef;
            this.connectionJourneyRef = connectionJourneyRef;
        }

        public void onClick(final View v) {
            final boolean showNavigateTo;
            if (renderConfig.isJourney) {
                Trip.Public journeyLeg = (Trip.Public) tripRenderer.trip.legs.get(0);
                if (journeyLeg.exitLocation == null) {
                    final Location entry = journeyLeg.entryLocation;
                    showNavigateTo = journeyLeg.isStopAfterOther(stop, entry);
                } else {
                    showNavigateTo = false;
                }
            } else {
                showNavigateTo = false;
            }
            final boolean isLastPublicStop = stop.location.equals(getLastPublicLocation());
            final boolean showTravelAlarm = isShowTravelAlarm();
            final PopupMenu contextMenu = new StationContextMenu(
                    TripDetailsActivity.this, v, network, stop.location,
                    null, false, false, true,
                    true, true,
                    showTravelAlarm && stopIsLegDeparture, showTravelAlarm && stopIsLegArrival,
                    true, true,
                    renderConfig.isNavigation && !isLastPublicStop,
                    showNavigateTo, false);
            contextMenu.setOnMenuItemClickListener(item -> {
                int menuItemId = item.getItemId();
                Date time = stop.getArrivalTime();
                if (time == null)
                    time = stop.getDepartureTime(true);
                if (menuItemId == R.id.station_context_show_departures) {
                    StationDetailsActivity.start(TripDetailsActivity.this, network, stop.location, time, null);
                    return true;
                } else if (menuItemId == R.id.station_context_nearby_departures) {
                    StationsActivity.start(TripDetailsActivity.this, network, stop.location, time);
                    return true;
                } else if (menuItemId == R.id.station_context_navigate_to) {
                    startNavigationForJourneyToExit(stop);
                    return true;
                } else if (menuItemId == R.id.station_context_directions_alternative_from) {
                    return onFindAlternativeConnections(
                            stop, stopIsLegDeparture,
                            currentJourneyRef, feederJourneyRef, connectionJourneyRef,
                            renderConfig.queryTripsRequestData);
                } else if (menuItemId == R.id.station_context_directions_from) {
                    return onDirectionsFrom(
                            stop, stopIsLegDeparture,
                            currentJourneyRef, feederJourneyRef, connectionJourneyRef,
                            renderConfig.queryTripsRequestData, null);
                } else if (menuItemId == R.id.station_context_directions_to) {
                    return onDirectionsTo(
                            stop, stopIsLegDeparture,
                            currentJourneyRef, feederJourneyRef, connectionJourneyRef,
                            renderConfig.queryTripsRequestData, null);
                } else if (menuItemId == R.id.station_context_directions_via) {
                    return onDirectionsVia(
                            stop, stopIsLegDeparture,
                            currentJourneyRef, feederJourneyRef, connectionJourneyRef,
                            renderConfig.queryTripsRequestData, null);
                } else if (menuItemId == R.id.station_context_infopage) {
                    String infoUrl = stop.location.infoUrl;
                    if (infoUrl != null) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(infoUrl)));
                    }
                    return true;
                } else if (menuItemId == R.id.station_context_set_departure_travel_alarm) {
                    return onStationContextMenuItemClicked(menuItemId, legC, stop);
                } else if (menuItemId == R.id.station_context_set_arrival_travel_alarm) {
                    return onStationContextMenuItemClicked(menuItemId, legC, stop);
                } else {
                    return false;
                }
            });
            contextMenu.show();
        }
    }

    protected boolean isShowTravelAlarm() {
        return false;
    }

    protected boolean onStationContextMenuItemClicked(
            final int menuItemId, final TripRenderer.LegContainer legC, final Stop stop) {
        return false;
    }

    @Nullable
    protected Location getLastPublicLocation() {
        final Trip.Public lastPublicLeg = tripRenderer.trip.getLastPublicLeg();
        if (lastPublicLeg == null)
            return null;
        return lastPublicLeg.arrivalStop.location;
    }

    private class MapClickListener implements android.view.View.OnClickListener {
        private final Location location;

        public MapClickListener(final Location location) {
            this.location = location;
        }

        public void onClick(final View v) {
            final PopupMenu popupMenu = new PopupMenu(TripDetailsActivity.this, v);
            StationContextMenu.prepareMapMenu(TripDetailsActivity.this, popupMenu.getMenu(), network, location);
            popupMenu.show();
        }
    }

    private Intent shareTripShort() {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, tripToShortText(tripRenderer.trip));
        return Intent.createChooser(intent, getString(R.string.directions_trip_details_action_share_short_title));
    }

    private Intent shareTripLong(final boolean withLink) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.directions_trip_details_text_long_title,
                tripRenderer.trip.from.uniqueShortName(), tripRenderer.trip.to.uniqueShortName()));
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, tripToLongText(tripRenderer.trip, withLink));
        return Intent.createChooser(intent, getString(withLink
                        ? R.string.directions_trip_details_action_share_link_title
                        : R.string.directions_trip_details_action_share_long_title));
    }

    private Intent scheduleTripIntent(final Trip trip, final boolean withLink) {
        final Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setData(CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.Events.TITLE, getString(R.string.directions_trip_details_text_long_title,
                trip.from.uniqueShortName(), trip.to.uniqueShortName()));
        intent.putExtra(CalendarContract.Events.DESCRIPTION, tripToLongText(trip, withLink));
        intent.putExtra(CalendarContract.Events.EVENT_LOCATION, trip.from.uniqueShortName());
        final Date firstDepartureTime = trip.getFirstDepartureTime();
        if (firstDepartureTime != null)
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, firstDepartureTime.getTime());
        final Date lastArrivalTime = trip.getLastArrivalTime();
        if (lastArrivalTime != null)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, lastArrivalTime.getTime());
        intent.putExtra(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT);
        intent.putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);
        return intent;
    }

    private Intent showKmlInExternalMapsApp() {
        try {
            final File kmlFile = new File(Application.getInstance().getShareDir(), "shareroute.kml");
            new KmlProducer(application).writeTrip(tripRenderer.trip, kmlFile);
            final Intent kmlIntent = GoogleMapsUtils.getOpenKmlIntent(kmlFile);
            return Intent.createChooser(kmlIntent, "xxx");
        } catch (Exception e) {
            log.error("cannot create shared KML file", e);
            return null;
        }
    }

    private String tripToShortText(final Trip trip) {
        final java.text.DateFormat dateFormat = DateFormat.getDateFormat(TripDetailsActivity.this);
        final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(TripDetailsActivity.this);

        final Trip.Public firstPublicLeg = trip.getFirstPublicLeg();
        final Trip.Public lastPublicLeg = trip.getLastPublicLeg();
        if (firstPublicLeg == null || lastPublicLeg == null)
            return null;

        final String departureDateStr = dateFormat.format(firstPublicLeg.getDepartureTime(true));
        final String departureTimeStr = timeFormat.format(firstPublicLeg.getDepartureTime(true));
        final String departureLineStr = firstPublicLeg.line.label;
        final String departureNameStr = firstPublicLeg.departure.uniqueShortName();

        final String arrivalDateStr = dateFormat.format(lastPublicLeg.getArrivalTime(true));
        final String arrivalTimeStr = timeFormat.format(lastPublicLeg.getArrivalTime(true));
        final String arrivalLineStr = lastPublicLeg.line.label;
        final String arrivalNameStr = lastPublicLeg.arrival.uniqueShortName();

        return getString(R.string.directions_trip_details_text_short, departureDateStr, departureTimeStr,
                departureLineStr, departureNameStr, arrivalDateStr, arrivalTimeStr, arrivalLineStr, arrivalNameStr);
    }

    private String tripToLongText(final Trip trip, final boolean withLink) {
        final java.text.DateFormat dateFormat = DateFormat.getDateFormat(TripDetailsActivity.this);
        final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(TripDetailsActivity.this);

        final StringBuilder description = new StringBuilder();

        for (final TripRenderer.LegContainer legC : tripRenderer.legs) {
            final Trip.Leg leg = legC.publicLeg != null ? legC.publicLeg : legC.individualLeg;
            String legStr = null;

            if (leg instanceof Trip.Public) {
                final Trip.Public publicLeg = (Trip.Public) leg;

                final String lineStr = publicLeg.line.label;
                final Location lineDestination = publicLeg.destination;
                final String lineDestinationStr = lineDestination != null
                        ? " " + Constants.CHAR_RIGHTWARDS_ARROW + " " + lineDestination.uniqueShortName() : "";

                final String departureDateStr = dateFormat.format(publicLeg.getDepartureTime(true));
                final String departureTimeStr = timeFormat.format(publicLeg.getDepartureTime(true));
                final String departureNameStr = publicLeg.departure.uniqueShortName();
                final String departurePositionStr = publicLeg.getDeparturePosition() != null
                        ? publicLeg.getDeparturePosition().toString() : "";

                final String arrivalDateStr = dateFormat.format(publicLeg.getArrivalTime(true));
                final String arrivalTimeStr = timeFormat.format(publicLeg.getArrivalTime(true));
                final String arrivalNameStr = publicLeg.arrival.uniqueShortName();
                final String arrivalPositionStr = publicLeg.getArrivalPosition() != null
                        ? publicLeg.getArrivalPosition().toString() : "";

                legStr = getString(R.string.directions_trip_details_text_long_public, lineStr + lineDestinationStr,
                        departureDateStr, departureTimeStr, departurePositionStr, departureNameStr, arrivalDateStr,
                        arrivalTimeStr, arrivalPositionStr, arrivalNameStr);
            } else if (leg instanceof Trip.Individual) {
                final Trip.Individual individualLeg = (Trip.Individual) leg;

                final String distanceStr = individualLeg.distance != 0 ? "(" + individualLeg.distance + "m) " : "";
                final int legStrResId;
                if (individualLeg.type == Trip.Individual.Type.WALK)
                    legStrResId = R.string.directions_trip_details_text_long_walk;
                else if (individualLeg.type == Trip.Individual.Type.BIKE)
                    legStrResId = R.string.directions_trip_details_text_long_bike;
                else if (individualLeg.type == Trip.Individual.Type.CAR)
                    legStrResId = R.string.directions_trip_details_text_long_car;
                else if (individualLeg.type == Trip.Individual.Type.TRANSFER)
                    legStrResId = R.string.directions_trip_details_text_long_transfer;
                else
                    throw new IllegalStateException("unknown type: " + individualLeg.type);
                legStr = getString(legStrResId, individualLeg.min, distanceStr,
                        individualLeg.arrival.uniqueShortName());
            } else if (leg != null) {
                throw new IllegalStateException("cannot handle: " + leg);
            }

            if (legStr != null) {
                description.append(legStr);
                description.append("\n\n");
            }
        }

        if (withLink) {
            final String linkUrl = getTripLinkUrl(this, network, trip);
            if (linkUrl != null) {
                description.append(linkUrl);
                description.append("\n\n");
            }
        }

        if (description.length() > 0)
            description.setLength(description.length() - 2);

        return description.toString();
    }

    public static String getTripLinkUrl(final Context context, final NetworkId network, final Trip trip) {
        try {
            final NetworkProvider provider = NetworkProviderFactory.provider(network);
            if (provider.hasCapabilities(NetworkProvider.Capability.TRIP_SHARING)) {
                final TripShare tripShare = provider.shareTrip(trip);
                final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
                tripShare.packToMessage(packer);
                packer.close();
                final String stringifiedTripShare = Objects.compressToString(packer.toByteArray());
                return AppLinkActivity.getNetworkLinkUrl(context, network,
                        DirectionsActivity.LINK_IDENTIFIER_SHARE_TRIP, stringifiedTripShare).toString();
            } else if (provider.hasCapabilities(NetworkProvider.Capability.TRIP_RELOAD)) {
                // final String stringifiedTripRef Objects.serializeAndCompressToString(tripRef);
                final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
                trip.tripRef.packToMessage(packer);
                packer.close();
                final String stringifiedTripRef = Objects.compressToString(packer.toByteArray());
                return AppLinkActivity.getNetworkLinkUrl(context, network,
                        DirectionsActivity.LINK_IDENTIFIER_TRIP, stringifiedTripRef).toString();
            } else {
                return null;
            }
        } catch (Exception e) {
            log.error("cannot build URL from tripref", e);
            return null;
        }
    }

    private static String formatTimeSpan(final long millis) {
        final long mins = millis / DateUtils.MINUTE_IN_MILLIS;
        return String.format(Locale.ENGLISH, "%d:%02d", mins / 60, mins % 60);
    }

    private void startNavigationForJourneyToExit(final Stop exitStop) {
        if (!renderConfig.isJourney)
            return;

        final Trip.Public journeyLeg = (Trip.Public) tripRenderer.trip.legs.get(0);
        final Location entryLocation = journeyLeg.entryLocation;
        final Location exitLocation = exitStop.location;
        final Stop entryStop = journeyLeg.findStopByLocation(entryLocation);
        final String entryId = entryLocation.id;
        final String exitId = exitLocation.id;
        final List<Stop> intermediateStops = new ArrayList<>();
        boolean inIntermediates = false;
        for (Stop stop: journeyLeg.intermediateStops) {
            final String stopId = stop.location.id;
            if (stopId.equals(entryId))
                inIntermediates = true;
            else if (stopId.equals(exitId))
                break;
            else if (inIntermediates)
                intermediateStops.add(stop);
        }
        final Trip.Public leg = new Trip.Public(
                journeyLeg.line, exitLocation, entryStop, exitStop,
                intermediateStops, journeyLeg.path,
                journeyLeg.message,
                journeyLeg.journeyRef, journeyLeg.loadedAt);
        final Trip journeyTrip = new Trip(
                tripRenderer.trip.loadedAt,
                null,
                null,
                entryLocation,
                exitLocation,
                Collections.singletonList(leg),
                null,
                null,
                0);
        startNavigation(journeyTrip, new RenderConfig());
    }

    private void startNavigation(final Trip trip, final RenderConfig renderConfig) {
        if (TripNavigatorActivity.startNavigation(this, network, trip, renderConfig, isTaskRoot())) {
            setResult(RESULT_OK, new Intent());
            finish();
        }
    }

    public void onTripUpdated(final Trip updatedTrip) {
        if (updatedTrip == null) return;
        tripRenderer = new TripRenderer(tripRenderer, updatedTrip, renderConfig.isJourney, new Date());
        final List<Trip.Leg> updatedPublicLegs = new ArrayList<>();
        for (Trip.Leg leg : updatedTrip.legs) {
            if (leg instanceof Trip.Public)
                updatedPublicLegs.add(leg);
        }
        int iUpdatedLeg = 0;
        for (TripRenderer.LegContainer legC: tripRenderer.legs) {
            if (legC.initialLeg != null) {
                final Trip.Public updatedLeg = (Trip.Public) updatedPublicLegs.get(iUpdatedLeg);
                legC.setCurrentLegState(updatedLeg);
                iUpdatedLeg += 1;
            }
        }
        updateGUI();
    }

    protected void setupFromTrip(final Trip trip) {
        this.tripRenderer = new TripRenderer(tripRenderer, trip, renderConfig.isJourney, new Date());
    }

    protected boolean onFindAlternativeConnections(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final QueryTripsRunnable.TripRequestData queryTripsRequestData) {
        // override if implemented
        return false;
    }

    protected TripsOverviewActivity.RenderConfig getOverviewConfig(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final TimeSpec.Absolute time) {
        TripsOverviewActivity.RenderConfig overviewConfig = new TripsOverviewActivity.RenderConfig();
        overviewConfig.isAlternativeConnectionSearch = true;
        overviewConfig.referenceTime = time;
        overviewConfig.feederJourneyRef = feederJourneyRef;
        overviewConfig.connectionJourneyRef = connectionJourneyRef;
        if (currentJourneyRef != null) {
            overviewConfig.prependTrip = tripRenderer.trip;
            overviewConfig.prependToJourneyRef = currentJourneyRef;
            overviewConfig.prependToStop = stop;
            overviewConfig.prependToStopIsLegDeparture = isLegDeparture;
        }
        return overviewConfig;
    }

    protected boolean onDirectionsFrom(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final QueryTripsRunnable.TripRequestData queryTripsRequestData,
            final TripsOverviewActivity.RenderConfig overviewConfig) {
        final Date arrivalTime = stop.getArrivalTime();
        final TimeSpec.Absolute time = new TimeSpec.Absolute(DepArr.DEPART,
                arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
        DirectionsActivity.start(TripDetailsActivity.this,
                stop.location,
                renderConfig.isJourney ? ((Trip.Public) tripRenderer.trip.legs.get(0)).entryLocation : tripRenderer.trip.to,
                null,
                time,
                getOverviewConfig(stop, isLegDeparture, currentJourneyRef, feederJourneyRef, connectionJourneyRef, time),
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return true;
    }

    private boolean onDirectionsTo(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final QueryTripsRunnable.TripRequestData queryTripsRequestData,
            final TripsOverviewActivity.RenderConfig overviewConfig) {
        final Location entry;
        final TimeSpec.Absolute time;
        if (renderConfig.isJourney) {
            Trip.Public journeyLeg = (Trip.Public) tripRenderer.trip.legs.get(0);
            entry = journeyLeg.entryLocation;
            final Stop entryStop = journeyLeg.findStopByLocation(entry);
            final Date arrivalTime = entryStop.getArrivalTime();
            time = new TimeSpec.Absolute(DepArr.DEPART,
                    arrivalTime != null ? arrivalTime.getTime() : entryStop.getDepartureTime().getTime());
        } else {
            entry = tripRenderer.trip.from;
            final Date arrivalTime = stop.getArrivalTime();
            time = new TimeSpec.Absolute(DepArr.ARRIVE,
                    arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
        }
        DirectionsActivity.start(this,
                entry,
                stop.location,
                null,
                time,
                null,
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return true;
    }

    private boolean onDirectionsVia(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final QueryTripsRunnable.TripRequestData queryTripsRequestData,
            final TripsOverviewActivity.RenderConfig overviewConfig) {
        final Location exit;
        final TimeSpec.Absolute time;
        if (renderConfig.isJourney) {
            Trip.Public journeyLeg = (Trip.Public) tripRenderer.trip.legs.get(0);
            exit = journeyLeg.exitLocation;
            final Stop exitStop = journeyLeg.findStopByLocation(exit);
            final Date departureTime = exitStop.getDepartureTime();
            time = new TimeSpec.Absolute(DepArr.DEPART,
                    departureTime != null ? departureTime.getTime() : exitStop.getArrivalTime().getTime());
        } else {
            exit = tripRenderer.trip.to;
            final Date departureTime = stop.getDepartureTime();
            time = new TimeSpec.Absolute(DepArr.DEPART,
                    departureTime != null ? departureTime.getTime() : stop.getArrivalTime().getTime());
        }
        DirectionsActivity.start(this,
                null,
                exit,
                stop.location,
                time,
                null,
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return true;
    }
}
