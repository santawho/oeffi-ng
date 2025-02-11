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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TableLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.common.base.MoreObjects;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.LocationAware;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.OeffiMapView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.TripAware;
import de.schildbach.oeffi.directions.TimeSpec.DepArr;
import de.schildbach.oeffi.directions.navigation.Navigator;
import de.schildbach.oeffi.directions.navigation.TripNavigatorActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.LocationHelper;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ToggleImageButton;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

public class TripDetailsActivity extends OeffiActivity implements LocationListener, LocationAware  {
    public static class RenderConfig implements Serializable {
        public boolean isJourney;
        public boolean isNavigation;
        public boolean isAlternativeConnectionSearch;
        public QueryTripsRunnable.TripRequestData queryTripsRequestData;
    }

    public static class LegContainer {
        public @Nullable Trip.Individual individualLeg;
        public @Nullable Trip.Public publicLeg;
        public final @Nullable Trip.Public initialLeg;
        public final LegContainer transferFrom;
        public final LegContainer transferTo;

        public LegContainer(
                final @Nullable Trip.Public baseLeg) {
            this.publicLeg = baseLeg;
            this.initialLeg = baseLeg;
            this.individualLeg = null;
            this.transferFrom = null;
            this.transferTo = null;
        }

        public LegContainer(
                final @Nullable Trip.Individual baseLeg,
                final LegContainer transferFrom, final LegContainer transferTo) {
            this.individualLeg = baseLeg;
            this.transferFrom = transferFrom;
            this.transferTo = transferTo;
            this.publicLeg = null;
            this.initialLeg = null;
        }

        public boolean isTransfer() {
            return initialLeg == null;
        }

        public void setCurrentLegState(Trip.Public updatedLeg) {
            if (initialLeg != null)
                publicLeg = updatedLeg;
        }
    }

    private static final long NAVIGATION_AUTO_REFRESH_INTERVAL_SECS = 110;
    private static final String INTENT_EXTRA_NETWORK = TripDetailsActivity.class.getName() + ".network";
    private static final String INTENT_EXTRA_TRIP = TripDetailsActivity.class.getName() + ".trip";
    private static final String INTENT_EXTRA_RENDERCONFIG = TripDetailsActivity.class.getName() + ".config";

    public static void start(final Context context, final NetworkId network, final Trip.Public journeyLeg) {
        final Trip trip = new Trip(
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

    public static void start(final Context context, final NetworkId network, final Trip trip) {
        start(context, network, trip, new RenderConfig());
    }

    public static void start(final Context context, final NetworkId network, final Trip trip, final RenderConfig renderConfig) {
        context.startActivity(buildStartIntent(TripDetailsActivity.class, context, network, trip, renderConfig));
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

    private MyActionBar actionBar;
    private LayoutInflater inflater;
    private Resources res;
    private int colorSignificant;
    private int colorInsignificant;
    private int colorHighlighted;
    private int colorPosition, colorPositionBackground, colorPositionBackgroundChanged;
    private int colorLegPublicPastBackground, colorLegPublicNowBackground, colorLegPublicFutureBackground;
    private int colorLegIndividualPastBackground, colorLegIndividualNowBackground, colorLegIndividualFutureBackground;
    private DisplayMetrics displayMetrics;
    private LocationManager locationManager;
    private BroadcastReceiver tickReceiver;
    private long nextNavigationRefreshTime = 0;
    private boolean showNextEventWhenUnlocked = false;
    private boolean showNextEventWhenLocked = true;

    private ViewGroup legsGroup;
    private OeffiMapView mapView;
    private ToggleImageButton trackButton;

    protected NetworkId network;
    private Navigator navigator;
    private Trip trip;
    private List<LegContainer> legs = new ArrayList<>();
    private RenderConfig renderConfig;
    private Date highlightedTime;
    private Location highlightedLocation;
    private Point location;
    private int selectedLegIndex = -1;
    private boolean mapEnabled = false;
    private boolean isPaused = false;

    private static class LegKey {
        private String key;
        public LegKey(Trip.Leg leg) {
            key = leg.departure.id + "/" + leg.arrival.id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LegKey)) return false;
            LegKey legKey = (LegKey) other;
            return Objects.equals(key, legKey.key);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key);
        }
    };
    private final Map<LegKey, Boolean> legExpandStates = new HashMap<>();

    private QueryJourneyRunnable queryJourneyRunnable;
    private Runnable navigationRefreshRunnable;
    private HandlerThread backgroundThread;
    protected Handler backgroundHandler;
    protected final Handler handler = new Handler();

    private static final int LEGSGROUP_INSERT_INDEX = 2;

    private static final Logger log = LoggerFactory.getLogger(TripDetailsActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        backgroundThread = new HandlerThread("queryTripsThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        inflater = getLayoutInflater();
        res = getResources();
        colorSignificant = res.getColor(R.color.fg_significant);
        colorInsignificant = res.getColor(R.color.fg_insignificant);
        colorHighlighted = res.getColor(R.color.fg_highlighted);
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

        final Intent intent = getIntent();
        renderConfig = (RenderConfig) intent.getSerializableExtra(INTENT_EXTRA_RENDERCONFIG);
        network = (NetworkId) intent.getSerializableExtra(INTENT_EXTRA_NETWORK);
        Trip baseTrip = (Trip) intent.getSerializableExtra(INTENT_EXTRA_TRIP);

        log.info("Showing {} from {} to {}", renderConfig.isJourney ? "journey" : "trip", baseTrip.from, baseTrip.to);

        NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
        if (renderConfig.isNavigation) {
            navigator = new Navigator(network, baseTrip);
            trip = navigator.getCurrentTrip();
        } else {
            trip = baseTrip;
        }
        setupFromTrip(trip);

        final boolean isPortrait = res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        setContentView(isPortrait
                ? R.layout.directions_trip_details_content_portrait
                : R.layout.directions_trip_details_content_landscape);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        actionBar = getMyActionBar();
        setPrimaryColor(
                renderConfig.isAlternativeConnectionSearch
                        ? R.color.bg_action_alternative_directions
                        : renderConfig.isNavigation
                        ? R.color.bg_action_bar_navigation
                        : R.color.bg_action_bar_directions);
        actionBar.setPrimaryTitle(getString(
                renderConfig.isNavigation
                    ? R.string.navigation_details_title
                    : renderConfig.isJourney
                        ? R.string.journey_details_title
                        : R.string.trip_details_title)); // getTitle()
        actionBar.setBack(isTaskRoot() ? null : v -> goBack());

        // action bar secondary title
        final StringBuilder secondaryTitle = new StringBuilder();
        final Long duration = trip.getPublicDuration();
        if (duration != null)
            secondaryTitle.append(getString(R.string.directions_trip_details_duraton, formatTimeSpan(duration)));

        if (trip.numChanges != null && trip.numChanges > 0) {
            if (secondaryTitle.length() > 0)
                secondaryTitle.append(" / ");
            secondaryTitle.append(getString(R.string.directions_trip_details_num_changes, trip.numChanges));
        }

        actionBar.setSecondaryTitle(secondaryTitle.length() > 0 ? secondaryTitle : null);

        // action bar buttons
        if (renderConfig.isNavigation) {
            actionBar.addProgressButton().setOnClickListener(buttonView -> refreshNavigation());
        } else {
            if (networkProvider.hasCapabilities(NetworkProvider.Capability.JOURNEY)) {
                actionBar.addButton(R.drawable.ic_navigation_white_24dp, R.string.directions_trip_details_action_start_routing)
                        .setOnClickListener(buttonView -> startNavigation());
            }
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            trackButton = actionBar.addToggleButton(R.drawable.ic_location_white_24dp,
                    R.string.directions_trip_details_action_track_title);
            trackButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    final String provider = requestLocationUpdates();
                    if (provider != null) {
                        final android.location.Location lastKnownLocation = locationManager
                                .getLastKnownLocation(provider);
                        if (lastKnownLocation != null
                                && (lastKnownLocation.getLatitude() != 0 || lastKnownLocation.getLongitude() != 0))
                            location = LocationHelper.locationToPoint(lastKnownLocation);
                        else
                            location = null;
                        mapView.setLocationAware(TripDetailsActivity.this);
                    }
                } else {
                    locationManager.removeUpdates(TripDetailsActivity.this);
                    location = null;

                    mapView.setLocationAware(null);
                }

                mapView.zoomToAll();
                updateGUI();
            });
        }

        if (isPortrait && res.getBoolean(R.bool.layout_map_show_toggleable)) {
            actionBar.addButton(R.drawable.ic_map_white_24dp, R.string.directions_trip_details_action_showmap_title)
                    .setOnClickListener(v -> {
                        mapEnabled = !mapEnabled;
                        updateFragments();
                    });
        }

        actionBar.addButton(R.drawable.ic_share_white_24dp, R.string.directions_trip_details_action_share_title)
                .setOnClickListener(v -> {
                    final PopupMenu popupMenu = new PopupMenu(TripDetailsActivity.this, v);
                    popupMenu.inflate(R.menu.directions_trip_details_action_share);
                    popupMenu.setOnMenuItemClickListener(item -> {
                        if (item.getItemId() == R.id.directions_trip_details_action_share_short) {
                            shareTripShort();
                            return true;
                        } else if (item.getItemId() == R.id.directions_trip_details_action_share_long) {
                            shareTripLong();
                            return true;
                        } else {
                            return false;
                        }
                    });
                    popupMenu.show();
                });
        if (!renderConfig.isNavigation && !renderConfig.isAlternativeConnectionSearch) {
            actionBar.addButton(R.drawable.ic_today_white_24dp, R.string.directions_trip_details_action_calendar_title)
                    .setOnClickListener(v -> {
                        try {
                            startActivity(scheduleTripIntent(trip));
                        } catch (final ActivityNotFoundException x) {
                            new Toast(this).longToast(R.string.directions_trip_details_action_calendar_notfound);
                        }
                    });
        }

        legsGroup = findViewById(R.id.directions_trip_details_legs_group);

        updateLocations();
        updateFares(trip.fares);
        int i = LEGSGROUP_INSERT_INDEX;
        for (final LegContainer legC : legs) {
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

        mapView = findViewById(R.id.directions_trip_details_map);
        mapView.setTripAware(new TripAware() {
            public Trip getTrip() {
                return trip;
            }

            public void selectLeg(final int partIndex) {
                selectedLegIndex = partIndex;
                mapView.zoomToAll();
            }

            public boolean hasSelection() {
                return selectedLegIndex != -1;
            }

            public boolean isSelectedLeg(final Trip.Leg part) {
                if (!hasSelection())
                    return false;

                return legs.get(selectedLegIndex).equals(part);
            }
        });
        final TextView mapDisclaimerView = findViewById(R.id.directions_trip_details_map_disclaimer);
        mapDisclaimerView.setText(mapView.getTileProvider().getTileSource().getCopyrightNotice());
        ViewCompat.setOnApplyWindowInsetsListener(mapDisclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });

        View nextEvent = findViewById(R.id.directions_trip_details_next_event_container);
        nextEvent.setOnClickListener(view -> setShowNextEvent(false));
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

    private boolean checkAutoRefresh() {
        if (isPaused) return false;
        if (!renderConfig.isNavigation) return false;
        if (nextNavigationRefreshTime < 0) return false;
        long now = new Date().getTime();
        if (now < nextNavigationRefreshTime) return false;
        refreshNavigation();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        checkAutoRefresh();
        mapView.onResume();
        updateGUI();
    }

    @Override
    protected void onPause() {
        isPaused = true;
        mapView.onPause();
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
        return findViewById(R.id.directions_trip_details_next_event).getVisibility() == View.VISIBLE;
    }

    private void goBack() {
        if (isShowingNextEvent())
            setShowNextEvent(false);
        else
            finish();
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
        this.location = LocationHelper.locationToPoint(location);

        updateGUI();
    }

    public void onProviderEnabled(final String provider) {
    }

    public void onProviderDisabled(final String provider) {
        locationManager.removeUpdates(TripDetailsActivity.this);

        final String newProvider = requestLocationUpdates();
        if (newProvider == null)
            mapView.setLocationAware(null);
    }

    public void onStatusChanged(final String provider, final int status, final Bundle extras) {
    }

    public final Point getDeviceLocation() {
        return location;
    }

    public final Location getReferenceLocation() {
        return null;
    }

    public final Float getDeviceBearing() {
        return null;
    }

    private void updateFragments() {
        updateFragments(R.id.directions_trip_details_list_frame, R.id.directions_trip_details_map_frame);
    }

    @Override
    protected boolean isMapEnabled(Resources res) {
        return mapEnabled || super.isMapEnabled(res);
    }

    private void updateGUI() {
        final Date now = new Date();
        updateHighlightedTime(now);
        updateHighlightedLocation();

        LegContainer currentLeg = null;
        int i = LEGSGROUP_INSERT_INDEX;
        for (int iLeg = 0; iLeg < legs.size(); ++iLeg) {
            final LegContainer legC = legs.get(iLeg);
            final View legView = legsGroup.getChildAt(i);
            final boolean isCurrent;
            if (legC.publicLeg != null) {
                final int iWalk = iLeg + 1;
                LegContainer walkLegC = (iWalk < legs.size()) ? legs.get(iWalk) : null;
                final int iNext = iLeg + 2;
                LegContainer nextLegC = (iNext < legs.size()) ? legs.get(iNext) : null;
                isCurrent = updatePublicLeg(legView, legC, walkLegC, nextLegC, now);
            } else {
                isCurrent = updateIndividualLeg(legView, legC, now);
            }
            if (isCurrent)
                currentLeg = legC;
            i++;
        }

        final boolean showEvent;
        if (currentLeg == null) {
            showEvent = false;
            showNextEventWhenUnlocked = false;
            showNextEventWhenLocked = false;
        } else {
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

        View nextEvent = findViewById(R.id.directions_trip_details_next_event);
        nextEvent.setVisibility(showEvent ? View.VISIBLE : View.GONE);
    }

    private void updateHighlightedTime(final Date now) {
        highlightedTime = null;

        final Date firstPublicLegDepartureTime = trip.getFirstPublicLegDepartureTime();
        if (firstPublicLegDepartureTime == null
                || firstPublicLegDepartureTime.getTime() - now.getTime() > 10 * DateUtils.MINUTE_IN_MILLIS)
            return;

        for (final LegContainer legC : legs) {
            if (legC.publicLeg != null) {
                final Trip.Public publicLeg = legC.publicLeg;
                final Date departureTime = publicLeg.getDepartureTime();
                final Date arrivalTime = publicLeg.getArrivalTime();

                if (departureTime.after(now)) {
                    highlightedTime = departureTime;
                    return;
                }

                final List<Stop> intermediateStops = publicLeg.intermediateStops;
                if (intermediateStops != null) {
                    for (final Stop stop : intermediateStops) {
                        Date stopTime = stop.getArrivalTime();
                        if (stopTime == null)
                            stopTime = stop.getDepartureTime();

                        if (stopTime != null && stopTime.after(now)) {
                            highlightedTime = stopTime;
                            return;
                        }
                    }
                }

                if (arrivalTime.after(now)) {
                    highlightedTime = arrivalTime;
                    return;
                }
            }
        }
    }

    private void updateHighlightedLocation() {
        highlightedLocation = null;

        if (location != null) {
            float minDistance = Float.MAX_VALUE;

            final float[] distanceBetweenResults = new float[1];

            for (final LegContainer legC : legs) {
                if (legC.publicLeg != null) {
                    final Trip.Public publicLeg = legC.publicLeg;

                    if (publicLeg.departure.hasCoord()) {
                        android.location.Location.distanceBetween(publicLeg.departure.getLatAsDouble(),
                                publicLeg.departure.getLonAsDouble(), location.getLatAsDouble(),
                                location.getLonAsDouble(), distanceBetweenResults);
                        final float distance = distanceBetweenResults[0];
                        if (distance < minDistance) {
                            minDistance = distance;
                            highlightedLocation = publicLeg.departure;
                        }
                    }

                    final List<Stop> intermediateStops = publicLeg.intermediateStops;
                    if (intermediateStops != null) {
                        for (final Stop stop : intermediateStops) {
                            if (stop.location.hasCoord()) {
                                android.location.Location.distanceBetween(stop.location.getLatAsDouble(),
                                        stop.location.getLonAsDouble(), location.getLatAsDouble(),
                                        location.getLonAsDouble(), distanceBetweenResults);
                                final float distance = distanceBetweenResults[0];
                                if (distance < minDistance) {
                                    minDistance = distance;
                                    highlightedLocation = stop.location;
                                }
                            }
                        }
                    }

                    if (publicLeg.arrival.hasCoord()) {
                        android.location.Location.distanceBetween(publicLeg.arrival.getLatAsDouble(),
                                publicLeg.arrival.getLonAsDouble(), location.getLatAsDouble(),
                                location.getLonAsDouble(), distanceBetweenResults);
                        final float distance = distanceBetweenResults[0];
                        if (distance < minDistance) {
                            minDistance = distance;
                            highlightedLocation = publicLeg.arrival;
                        }
                    }
                }
            }
        }
    }

    private void updateLocations() {
        final LocationTextView fromView = findViewById(R.id.directions_trip_details_location_from);
        fromView.setLabel(R.string.directions_overview_from);
        fromView.setLocation(trip.from);
        final LocationTextView toView = findViewById(R.id.directions_trip_details_location_to);
        toView.setLabel(R.string.directions_overview_to);
        toView.setLocation(trip.to);
    }

    private void updateFares(final List<Fare> fares) {
        final TableLayout faresTable = findViewById(R.id.directions_trip_details_fares);
        if (trip.fares != null && !trip.fares.isEmpty()) {
            faresTable.setVisibility(View.VISIBLE);

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
        } else {
            faresTable.setVisibility(View.GONE);
        }
    }

    private boolean updatePublicLeg(
            final View row,
            final LegContainer legC, final LegContainer walkLegC, final LegContainer nextLegC,
            final Date now) {
        Trip.Public leg = legC.publicLeg;
        final Location destination = leg.destination;
        final String destinationName = destination != null ? destination.uniqueShortName() : null;
        final boolean showDestination = destinationName != null;
        final boolean showAccessibility = leg.line.hasAttr(Line.Attr.WHEEL_CHAIR_ACCESS);
        final boolean showBicycleCarriage = leg.line.hasAttr(Line.Attr.BICYCLE_CARRIAGE);
        final List<Stop> intermediateStops = leg.intermediateStops;

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
            destinationView.setOnClickListener(destination.hasId() ? new LocationClickListener(destination) : null);
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
        final Boolean checked = legExpandStates.get(new LegKey(leg));
        expandButton.setVisibility(
                !renderConfig.isJourney && intermediateStops != null && !intermediateStops.isEmpty()
                        ? View.VISIBLE
                        : View.GONE);
        expandButton.setChecked(checked != null ? checked : false);
        expandButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            legExpandStates.put(new LegKey(leg), isChecked);
            updateGUI();
        });

        final TableLayout stopsView = row.findViewById(R.id.directions_trip_details_public_entry_stops);
        stopsView.removeAllViews();
        final CollapseColumns collapseColumns = new CollapseColumns();
        collapseColumns.dateChanged(now);

        final View departureRow = stopRow(PearlView.Type.DEPARTURE, leg.departureStop, leg, highlightedTime,
                leg.departureStop.location.equals(highlightedLocation), now, collapseColumns);
        stopsView.addView(departureRow);

        if (intermediateStops != null) {
            if (expandButton.isChecked()) {
                for (final Stop stop : intermediateStops) {
                    final boolean hasStopTime = stop.getArrivalTime() != null || stop.getDepartureTime() != null;

                    final View stopRow = stopRow(hasStopTime ? PearlView.Type.INTERMEDIATE : PearlView.Type.PASSING,
                            stop, leg, highlightedTime, stop.location.equals(highlightedLocation), now,
                            collapseColumns);
                    stopsView.addView(stopRow);
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

        final View arrivalRow = stopRow(PearlView.Type.ARRIVAL, leg.arrivalStop, leg, highlightedTime,
                leg.arrivalStop.location.equals(highlightedLocation), now, collapseColumns);
        stopsView.addView(arrivalRow);

        stopsView.setColumnCollapsed(1, collapseColumns.collapseDateColumn);
        stopsView.setColumnCollapsed(3, collapseColumns.collapseDelayColumn);
        stopsView.setColumnCollapsed(4, collapseColumns.collapsePositionColumn);

        final TextView messageView = row.findViewById(R.id.directions_trip_details_public_entry_message);
        final String message = leg.message != null ? leg.message : leg.line.message;
        if (message != null) {
            Spanned html = Html.fromHtml(message, Html.FROM_HTML_MODE_COMPACT);
            messageView.setText(html);
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
        }
        messageView.setVisibility(message != null ? View.VISIBLE : View.GONE);

        final TextView progress = row.findViewById(R.id.directions_trip_details_public_entry_progress);
        progress.setVisibility(View.GONE);
        Date beginTime = leg.departureStop.getDepartureTime();
        Date endTime = leg.arrivalStop.getArrivalTime();
        if (now.before(beginTime)) {
            // leg is in the future
            row.setBackgroundColor(colorLegPublicFutureBackground);
        } else if (now.after(endTime)) {
            // leg is in the past
            row.setBackgroundColor(colorLegPublicPastBackground);
        } else {
            // leg is now
            row.setBackgroundColor(colorLegPublicNowBackground);
            progress.setText(getLeftTimeFormatted(now, endTime));
            progress.setVisibility(View.VISIBLE);
            progress.setOnClickListener(view -> setShowNextEvent(true));

            setNextEventType(true);
            setNextEventClock(now);
            setNextEventTimeLeft(now, endTime, leg.arrivalStop.plannedArrivalTime, 0);
            String targetName = leg.arrivalStop.location.uniqueShortName();
            setNextEventTarget(targetName);
            String depName = (nextLegC != null) ? nextLegC.publicLeg.departureStop.location.uniqueShortName() : null;
            boolean depChanged = depName != null && !depName.equals(targetName);
            setNextEventDeparture(depChanged ? depName : null);
            final Position arrPos = leg.arrivalStop.getArrivalPosition();
            final Position depPos = (nextLegC != null) ? nextLegC.publicLeg.getDeparturePosition() : null;
            setNextEventPositions(arrPos, depPos, depPos != null && !depPos.equals(nextLegC.publicLeg.departureStop.plannedDeparturePosition));
            setNextEventTransport((nextLegC != null) ? nextLegC.publicLeg : null);
            setNextEventTransferTimes(walkLegC, false);
            setNextEventActions(
                    nextLegC != null
                            ? R.string.directions_trip_details_next_event_action_ride
                            : R.string.directions_trip_details_next_event_action_arrival,
                    walkLegC == null ? 0
                            : depChanged ? R.string.directions_trip_details_next_event_action_next_transfer
                            : R.string.directions_trip_details_next_event_action_next_interchange
            );

            return true;
        }
        return false;
    }

    private boolean updateIndividualLeg(final View row, final LegContainer legC, final Date now) {
        final TextView textView = row.findViewById(R.id.directions_trip_details_individual_entry_text);
        String legText = null;
        final Trip.Individual leg = legC.individualLeg;
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
            // walk after some public transport
            iconResId = R.drawable.ic_directions_walk_grey600_24dp;
        } else {
            // walk before anything
            iconResId = 0;
        }

        String transferText = null;
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
            if (diffMaxSecs < 0) {
                transferText = getString(R.string.directions_trip_conneval_missed, (-diffMaxSecs - 60) / 60);
            } else if (leftMaxSecs < 0) {
                if (diffMinSecs < 0) {
                    transferText = getString(R.string.directions_trip_conneval_difficult_possibly_missed, diffMaxSecs / 60);
                } else {
                    transferText = getString(R.string.directions_trip_conneval_difficult, diffMaxSecs / 60);
                }
            } else if (leftMaxSecs < 180) {
                if (leftMinSecs < 0) {
                    transferText = getString(R.string.directions_trip_conneval_endangered_possibly_difficult, diffMaxSecs / 60);
                } else {
                    transferText = getString(R.string.directions_trip_conneval_endangered, diffMaxSecs / 60);
                }
            } else if (leftMinSecs < 0) {
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

        if (text == null && iconResId == 0) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            textView.setText(Html.fromHtml(text != null ? text : ""));
            textView.setCompoundDrawablesWithIntrinsicBounds(iconResId, 0, 0, 0);
        }

        final TextView progress = row.findViewById(R.id.directions_trip_details_individual_entry_progress);
        progress.setVisibility(View.GONE);
        Date beginTime = transferFrom != null ? transferFrom.getArrivalTime() : null;
        Date endTime = transferTo != null ? transferTo.getDepartureTime() : null;
        if (beginTime != null && now.before(beginTime)) {
            // leg is in the future
            row.setBackgroundColor(colorLegIndividualFutureBackground);
        } else if (endTime != null && now.after(endTime)) {
            // leg is in the past
            row.setBackgroundColor(colorLegIndividualPastBackground);
        } else {
            // leg is now
            row.setBackgroundColor(colorLegIndividualNowBackground);
            progress.setText(getLeftTimeFormatted(now, endTime));
            progress.setVisibility(View.VISIBLE);
            progress.setOnClickListener(view -> setShowNextEvent(true));

            setNextEventType(false);
            setNextEventClock(now);
            setNextEventTimeLeft(now, endTime, transferTo != null ? transferTo.plannedDepartureTime : null, leg != null ? leg.min : 0);
            final String targetName = (transferTo != null) ? transferTo.location.uniqueShortName() : null;
            setNextEventTarget(targetName);
            final String arrName = (transferFrom != null) ? transferFrom.location.uniqueShortName() : null;
            final boolean depChanged = arrName != null && !arrName.equals(targetName);
            setNextEventDeparture(null);
            final Position arrPos = transferFrom != null ? transferFrom.getArrivalPosition() : null;
            final Position depPos = transferTo != null ? transferTo.getDeparturePosition() : null;
            setNextEventPositions(arrPos, depPos, depPos != null && !depPos.equals(transferTo.plannedArrivalPosition));
            setNextEventTransport(legC.transferTo != null ? legC.transferTo.publicLeg : null);
            setNextEventTransferTimes(legC, true);
            setNextEventActions(transferTo == null ? 0
                    : transferFrom == null ? R.string.directions_trip_details_next_event_action_departure
                    : depChanged ? R.string.directions_trip_details_next_event_action_transfer
                    : R.string.directions_trip_details_next_event_action_interchange,
                    0);

            return true;
        }
        return false;
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

    private void setNextEventType(boolean isPublic) {
        final int colorHighlight = getColor(R.color.bg_trip_details_public_now);
        final int colorNormal = getColor(R.color.bg_level0);
        final int colorHighIfPublic = isPublic ? colorHighlight : colorNormal;
        final int colorHighIfChangeover = isPublic ? colorNormal : colorHighlight;
        setViewBackgroundColor(R.id.directions_trip_details_next_event_current_action, colorHighlight);
        setViewBackgroundColor(R.id.directions_trip_details_next_event_next_action, colorNormal);
        setViewBackgroundColor(R.id.directions_trip_details_next_event_time, colorHighlight);
        setViewBackgroundColor(R.id.directions_trip_details_next_event_target, colorHighlight);
        setViewBackgroundColor(R.id.directions_trip_details_next_event_positions, colorHighIfChangeover);
        setViewBackgroundColor(R.id.directions_trip_details_next_event_departure, colorNormal);
        setViewBackgroundColor(R.id.directions_trip_details_next_event_connection, colorHighIfChangeover);
        setViewBackgroundColor(R.id.directions_trip_details_next_event_changeover, colorHighIfChangeover);
        setViewBackgroundColor(R.id.directions_trip_details_next_event_clock, colorNormal);
    }

    private void setNextEventClock(final Date time) {
        TextView clock = findViewById(R.id.directions_trip_details_next_event_clock);
        clock.setText(Formats.formatTime(this, time.getTime()));
    }

    private void setNextEventActions(final int currentId, final int nextId) {
        TextView currentAction = findViewById(R.id.directions_trip_details_next_event_current_action);
        if (currentId > 0) {
            final String s = getString(currentId);
            currentAction.setText(s);
            currentAction.setVisibility(View.VISIBLE);
        } else {
            currentAction.setVisibility(View.GONE);
        }

        TextView nextAction = findViewById(R.id.directions_trip_details_next_event_next_action);
        if (nextId > 0) {
            final String s = getString(nextId);
            nextAction.setText(s);
            nextAction.setVisibility(View.VISIBLE);
        } else {
            nextAction.setVisibility(View.GONE);
        }
    }

    @SuppressLint("DefaultLocale")
    private void setNextEventTimeLeft(final Date now, final Date endTime, final Date plannedEndTime, final int walkMins) {
        long leftSecs = (endTime.getTime() - now.getTime()) / 1000;
        long delaySecs = (plannedEndTime == null) ? 0 : (endTime.getTime() - plannedEndTime.getTime()) / 1000;
        leftSecs += 5;
        boolean isNegative = false;
        if (leftSecs < 0) {
            isNegative = true;
            leftSecs = -leftSecs;
        }
        long value = 0;
        String valueStr = null;
        final String unit;
        String explainStr = null;
        final boolean hourglassVisible;
        if (leftSecs < 70) {
            valueStr = getString(R.string.directions_trip_details_next_event_no_time_left);
            unit = "";
            hourglassVisible = false;
        } else {
            hourglassVisible = true;
            final long leftMins = leftSecs / 60;
            if (leftMins <= 60) {
                value = leftMins;
                unit = "min";
                final long delayMins = delaySecs / 60;
                if (delayMins != 0)
                    explainStr = String.format("(%d%+d)", leftMins - delayMins, delayMins);
            } else {
                final long leftHours = leftMins / 60;
                if (leftHours < 3) {
                    valueStr = String.format("%d:%02d", leftHours, leftMins - leftHours * 60);
                    unit = "h";
                } else if (leftHours < 24) {
                    value = leftHours;
                    unit = "h";
                } else {
                    value = (leftHours + 12) / 24;
                    unit = "d";
                }
            }
        }
        if (valueStr == null)
            valueStr = "" + value;
        if (isNegative)
            valueStr = "-" + valueStr;
        TextView valueView = findViewById(R.id.directions_trip_details_next_event_time_value);
        valueView.setText(valueStr);
        valueView.setTextColor(getColor(leftSecs - walkMins * 60 < 60 ? R.color.fg_arrow : R.color.fg_significant));
        TextView unitView = findViewById(R.id.directions_trip_details_next_event_time_unit);
        unitView.setText(unit);
        findViewById(R.id.directions_trip_details_next_event_time_hourglass)
                .setVisibility(hourglassVisible ? View.VISIBLE : View.GONE);
        TextView explainView = findViewById(R.id.directions_trip_details_next_event_time_explain);
        if (explainStr != null) {
            explainView.setVisibility(View.VISIBLE);
            explainView.setText(explainStr);
        } else {
            explainView.setVisibility(View.GONE);
        }
    }

    private void setNextEventTarget(final String name) {
        TextView targetView = findViewById(R.id.directions_trip_details_next_event_target);
        if (name != null) {
            targetView.setText(Formats.makeBreakableStationName(name));
            targetView.setVisibility(View.VISIBLE);
        } else {
            targetView.setVisibility(View.GONE);
        }
    }

    private void setNextEventPositions(final Position arrPos, final Position depPos, boolean depChanged) {
        findViewById(R.id.directions_trip_details_next_event_positions)
                .setVisibility((arrPos != null || depPos != null) ? View.VISIBLE : View.GONE);

        final TextView from = findViewById(R.id.directions_trip_details_next_event_position_from);
        final TextView to = findViewById(R.id.directions_trip_details_next_event_position_to);

        if (arrPos != null) {
            from.setVisibility(View.VISIBLE);
            from.setText(arrPos.name);
        } else {
            from.setVisibility(View.GONE);
        }

        if (depPos != null) {
            to.setVisibility(View.VISIBLE);
            to.setText(Formats.makeBreakableStationName(depPos.name));
            to.setBackgroundColor(depChanged ? colorPositionBackgroundChanged : colorPositionBackground);
        } else {
            to.setVisibility(View.GONE);
        }
    }

    private void setNextEventTransport(final Trip.Public leg) {
        if (leg == null || leg.line == null) {
            findViewById(R.id.directions_trip_details_next_event_connection).setVisibility(View.GONE);
        } else {
            findViewById(R.id.directions_trip_details_next_event_connection).setVisibility(View.VISIBLE);

            final LineView lineView = findViewById(R.id.directions_trip_details_next_event_connection_line);
            lineView.setVisibility(View.VISIBLE);
            lineView.setLine(leg.line);

            final Location dest = leg.destination;
            TextView destView = findViewById(R.id.directions_trip_details_next_event_connection_to);
            destView.setText(dest != null ? Formats.makeBreakableStationName(dest.uniqueShortName()) : null);
        }
    }

    private void setNextEventTransferTimes(final LegContainer walkLegC, final boolean forWalkLeg) {
        if (walkLegC == null) {
            findViewById(R.id.directions_trip_details_next_event_changeover)
                    .setVisibility(View.GONE);
        } else {
            findViewById(R.id.directions_trip_details_next_event_changeover)
                    .setVisibility(View.VISIBLE);

            final Trip.Individual individualLeg = walkLegC.individualLeg;
            final int walkMins = individualLeg != null ? individualLeg.min : 0;

            if (!forWalkLeg && walkLegC.transferFrom != null && walkLegC.transferTo != null) {
                findViewById(R.id.directions_trip_details_next_event_transfer)
                        .setVisibility(View.VISIBLE);
                final Stop arrivalStop = walkLegC.transferFrom.publicLeg.arrivalStop;
                final Stop departureStop = walkLegC.transferTo.publicLeg.departureStop;
                final Date arrTime = arrivalStop.getArrivalTime();
                final Date depTime = departureStop.getDepartureTime();
                long leftMins = (depTime.getTime() - arrTime.getTime()) / 60000 - 1;
                TextView valueView = findViewById(R.id.directions_trip_details_next_event_transfer_value);
                valueView.setText(Long.toString(leftMins));
                valueView.setTextColor(getColor(leftMins - walkMins < 3
                        ? R.color.fg_arrow
                        : R.color.fg_significant));

                TextView explainView = findViewById(R.id.directions_trip_details_next_event_transfer_explain);
                final long arrDelay = (arrTime.getTime() - arrivalStop.plannedArrivalTime.getTime()) / 60000;
                final long depDelay = (depTime.getTime() - departureStop.plannedDepartureTime.getTime()) / 60000;
                if (arrDelay != 0 || depDelay != 0) {
                    explainView.setVisibility(View.VISIBLE);
                    String explainStr = String.format("(%d", leftMins + arrDelay - depDelay);
                    if (depDelay != 0) explainStr += String.format("%+d", depDelay);
                    if (arrDelay != 0) explainStr += String.format("%+d", -arrDelay);
                    explainStr += ")";
                    explainView.setText(explainStr);
                } else {
                    explainView.setVisibility(View.GONE);
                }
            } else {
                findViewById(R.id.directions_trip_details_next_event_transfer)
                        .setVisibility(View.GONE);
            }

            if (walkMins > 0) {
                findViewById(R.id.directions_trip_details_next_event_walk)
                        .setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.directions_trip_details_next_event_walk_value))
                        .setText(Integer.toString(walkMins));
                final int iconId;
                if (individualLeg == null)
                    iconId = R.drawable.ic_directions_walk_grey600_24dp;
                else switch (individualLeg.type) {
                    case WALK:
                    default:
                        iconId = R.drawable.ic_directions_walk_grey600_24dp;
                        break;
                    case BIKE:
                        iconId = R.drawable.ic_directions_bike_grey600_24dp;
                        break;
                    case CAR:
                    case TRANSFER:
                        iconId = R.drawable.ic_local_taxi_grey600_24dp;
                        break;
                }
                ((ImageView) findViewById(R.id.directions_trip_details_next_event_walk_icon))
                        .setImageDrawable(res.getDrawable(iconId));

            } else {
                findViewById(R.id.directions_trip_details_next_event_walk)
                        .setVisibility(View.GONE);
            }
        }
    }

    private void setNextEventDeparture(String name) {
        TextView depView = findViewById(R.id.directions_trip_details_next_event_departure);
        depView.setText(name);
        depView.setVisibility(name != null ? View.VISIBLE : View.GONE);
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

    private View stopRow(final PearlView.Type pearlType, final Stop stop, final Trip.Public leg, final Date highlightedTime,
            final boolean highlightLocation, final Date now, final CollapseColumns collapseColumns) {
        final View row = inflater.inflate(R.layout.directions_trip_details_public_entry_stop, null);

        final boolean isTimePredicted;
        final Date time;
        final Long delay;
        final boolean isCancelled;
        final boolean isPositionPredicted;
        final Position position;
        final boolean positionChanged;
        final Location location = stop.location;
        final Style style = leg.line.style;

        final boolean isEntryOrExit =
                (leg.entryLocation != null && location.id.equals(leg.entryLocation.id))
             || (leg.exitLocation != null && location.id.equals(leg.exitLocation.id));

        if (pearlType == PearlView.Type.DEPARTURE
                || ((pearlType == PearlView.Type.INTERMEDIATE || pearlType == PearlView.Type.PASSING)
                        && stop.plannedArrivalTime == null)) {
            isTimePredicted = stop.isDepartureTimePredicted();
            time = stop.getDepartureTime();
            delay = stop.getDepartureDelay();
            isCancelled = stop.departureCancelled;

            isPositionPredicted = stop.isDeparturePositionPredicted();
            position = stop.getDeparturePosition();
            positionChanged = position != null && !position.equals(stop.plannedDeparturePosition);
        } else if (pearlType == PearlView.Type.ARRIVAL
                || ((pearlType == PearlView.Type.INTERMEDIATE || pearlType == PearlView.Type.PASSING)
                        && stop.plannedArrivalTime != null)) {
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
        stopNameView.setText(Formats.makeBreakableStationName(location.uniqueShortName()));
        setStrikeThru(stopNameView, isCancelled);
        if (highlightLocation) {
            stopNameView.setTextColor(colorHighlighted);
            stopNameView.setTypeface(null, Typeface.BOLD);
        } else if (renderConfig.isJourney ? isEntryOrExit : (pearlType == PearlView.Type.DEPARTURE || pearlType == PearlView.Type.ARRIVAL)) {
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
            if (stop.getArrivalTime() == null) {
                // departure stop of a journey, find previous journey as feeder
                feederJourneyRef = null;
                for (final LegContainer legC : legs) {
                    if (legC.publicLeg != null) {
                        if (legC.publicLeg == leg)
                            break;
                        else
                            feederJourneyRef = legC.publicLeg.journeyRef;
                    }
                }
            } else if (stop.getDepartureTime() == null) {
                // arrival stop of a journey, find next journey as connection
                connectionJourneyRef = null;
                boolean found = false;
                for (final LegContainer legC : legs) {
                    if (legC.publicLeg != null) {
                        if (found) {
                            connectionJourneyRef = legC.publicLeg.journeyRef;
                            break;
                        } else if (legC.publicLeg == leg) {
                            found = true;
                        }
                    }
                }
            }
            stopNameView.setOnClickListener(new StopClickListener(stop, feederJourneyRef, connectionJourneyRef));
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
        boolean highlightTime = false;
        if (time != null) {
            if (collapseColumns.dateChanged(time)) {
                stopDateView.setText(Formats.formatDate(TripDetailsActivity.this, now.getTime(), time.getTime(), true,
                        res.getString(R.string.time_today_abbrev)));
                collapseColumns.collapseDateColumn = false;
            }
            stopTimeView.setText(Formats.formatTime(TripDetailsActivity.this, time.getTime()));
            setStrikeThru(stopTimeView, isCancelled);
            highlightTime = time.equals(highlightedTime);
        }
        final int stopTimeColor = highlightTime ? colorHighlighted : colorSignificant;
        stopDateView.setTextColor(stopTimeColor);
        stopTimeView.setTextColor(stopTimeColor);
        final boolean stopTimeBold = highlightTime || (renderConfig.isJourney ? isEntryOrExit : (pearlType != PearlView.Type.INTERMEDIATE));
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
        if (position != null && !isCancelled) {
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

        public LocationClickListener(final Location location) {
            this.location = location;
        }

        public void onClick(final View v) {
            final PopupMenu contextMenu = new StationContextMenu(TripDetailsActivity.this, v, network, location, null,
                    false, false, true, false, false, false);
            contextMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.station_context_details) {
                    StationDetailsActivity.start(TripDetailsActivity.this, network, location);
                    return true;
                } else {
                    return false;
                }
            });
            contextMenu.show();
        }
    }

    private class StopClickListener implements android.view.View.OnClickListener {
        private final Stop stop;
        final JourneyRef feederJourneyRef;
        final JourneyRef connectionJourneyRef;

        public StopClickListener(
                final Stop stop,
                final JourneyRef feederJourneyRef, final JourneyRef connectionJourneyRef) {
            this.stop = stop;
            this.feederJourneyRef = feederJourneyRef;
            this.connectionJourneyRef = connectionJourneyRef;
        }

        public void onClick(final View v) {
            final PopupMenu contextMenu = new StationContextMenu(TripDetailsActivity.this, v, network, stop.location,
                    null, false, false, true, true, renderConfig.isNavigation, false);
            contextMenu.setOnMenuItemClickListener(item -> {
                int menuItemId = item.getItemId();
                if (menuItemId == R.id.station_context_details) {
                    StationDetailsActivity.start(TripDetailsActivity.this, network, stop.location);
                    return true;
                } else if (menuItemId == R.id.station_context_directions_alternative_from) {
                    return onFindAlternativeConnections(stop, feederJourneyRef, connectionJourneyRef, renderConfig.queryTripsRequestData);
                } else if (menuItemId == R.id.station_context_directions_from) {
                    final Date arrivalTime = stop.getArrivalTime();
                    final TimeSpec.Absolute time = new TimeSpec.Absolute(DepArr.DEPART,
                            arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
                    DirectionsActivity.start(TripDetailsActivity.this, stop.location, trip.to, time,
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    return true;
                } else if (menuItemId == R.id.station_context_directions_to) {
                    final Date arrivalTime = stop.getArrivalTime();
                    final TimeSpec.Absolute time = new TimeSpec.Absolute(DepArr.ARRIVE,
                            arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
                    DirectionsActivity.start(TripDetailsActivity.this, trip.from, stop.location, time,
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    return true;
                } else if (menuItemId == R.id.station_context_infopage) {
                    String infoUrl = stop.location.infoUrl;
                    if (infoUrl != null) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(infoUrl)));
                    }
                    return true;
                } else {
                    return false;
                }
            });
            contextMenu.show();
        }
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

    private void shareTripShort() {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, tripToShortText(trip));
        startActivity(
                Intent.createChooser(intent, getString(R.string.directions_trip_details_action_share_short_title)));

    }

    private void shareTripLong() {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.directions_trip_details_text_long_title,
                trip.from.uniqueShortName(), trip.to.uniqueShortName()));
        intent.putExtra(Intent.EXTRA_TEXT, tripToLongText(trip));
        startActivity(
                Intent.createChooser(intent, getString(R.string.directions_trip_details_action_share_long_title)));
    }

    private Intent scheduleTripIntent(final Trip trip) {
        final Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setData(CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.Events.TITLE, getString(R.string.directions_trip_details_text_long_title,
                trip.from.uniqueShortName(), trip.to.uniqueShortName()));
        intent.putExtra(CalendarContract.Events.DESCRIPTION, tripToLongText(trip));
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

    private String tripToLongText(final Trip trip) {
        final java.text.DateFormat dateFormat = DateFormat.getDateFormat(TripDetailsActivity.this);
        final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(TripDetailsActivity.this);

        final StringBuilder description = new StringBuilder();

        for (final LegContainer legC : legs) {
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

        if (description.length() > 0)
            description.setLength(description.length() - 2);

        return description.toString();
    }

    private Point pointFromLocation(final Location location) {
        if (location.hasCoord())
            return location.coord;

        return null;
    }

    private static String formatTimeSpan(final long millis) {
        final long mins = millis / DateUtils.MINUTE_IN_MILLIS;
        return String.format(Locale.ENGLISH, "%d:%02d", mins / 60, mins % 60);
    }

    private void startNavigation() {
        TripNavigatorActivity.start(this, network, trip, renderConfig);
        setResult(RESULT_OK, new Intent());
        finish();
    }

    private void refreshNavigation() {
        if (navigationRefreshRunnable != null)
            return;

        nextNavigationRefreshTime = -1; // block auto-refresh
        actionBar.startProgress();

        navigationRefreshRunnable = () -> {
            try {
                Trip updatedTrip = navigator.refresh();
                if (updatedTrip == null) {
                    handler.post(() -> new Toast(this).toast(R.string.toast_network_problem));
                } else {
                    runOnUiThread(() -> onTripUpdated(updatedTrip));
                }
            } catch (IOException e) {
                handler.post(() -> new Toast(this).toast(R.string.toast_network_problem));
            } finally {
                navigationRefreshRunnable = null;
                runOnUiThread(() -> {
                    actionBar.stopProgress();
                    nextNavigationRefreshTime = new Date().getTime()
                            + NAVIGATION_AUTO_REFRESH_INTERVAL_SECS * 1000;
                });
            }
        };
        backgroundHandler.post(navigationRefreshRunnable);
    }

    public void onTripUpdated(Trip updatedTrip) {
        if (updatedTrip == null) return;
        trip = updatedTrip;
        final List<Trip.Leg> updatedPublicLegs = new ArrayList<>();
        for (Trip.Leg leg : updatedTrip.legs) {
            if (leg instanceof Trip.Public)
                updatedPublicLegs.add(leg);
        }
        int iUpdatedLeg = 0;
        for (LegContainer legC: legs) {
            if (legC.initialLeg != null) {
                final Trip.Public updatedLeg = (Trip.Public) updatedPublicLegs.get(iUpdatedLeg);
                legC.setCurrentLegState(updatedLeg);
                iUpdatedLeg += 1;
            }
        }
        updateGUI();
    }

    private void setupFromTrip(final Trip trip) {
        // try to build up paths
        LegContainer prevC = null;
        for (int iLeg = 0; iLeg < trip.legs.size(); ++iLeg) {
            final Trip.Leg prevLeg = (iLeg > 0) ? trip.legs.get(iLeg - 1) : null;
            Trip.Leg leg = trip.legs.get(iLeg);
            final Trip.Leg nextLeg = (iLeg + 1 < trip.legs.size()) ? trip.legs.get(iLeg + 1) : null;

            if (leg instanceof Trip.Individual) {
                final LegContainer transferFrom = (prevLeg instanceof Trip.Public) ? prevC : null;
                final LegContainer transferTo = (nextLeg instanceof Trip.Public) ? new LegContainer((Trip.Public) nextLeg) : null;
                legs.add(new LegContainer((Trip.Individual) leg, transferFrom, transferTo));
                if (transferTo != null) {
                    setupPath(nextLeg);
                    legs.add(transferTo);
                    ++iLeg;

                    if (renderConfig.isJourney) {
                        legExpandStates.put(new LegKey(nextLeg), true);
                    }
                }
                prevC = transferTo;
            } else if (leg instanceof Trip.Public){
                final LegContainer newC = new LegContainer((Trip.Public) leg);
                if (prevC != null || iLeg == 0) {
                    legs.add(new LegContainer(null, prevC, newC));
                }
                legs.add(newC);
                prevC = newC;

                if (renderConfig.isJourney) {
                    legExpandStates.put(new LegKey(leg), true);
                }
            }

            setupPath(leg);
        }
    }

    private void setupPath(final Trip.Leg leg) {
        if (leg.path == null) {
            leg.path = new ArrayList<>();

            if (leg.departure != null) {
                final Point departurePoint = pointFromLocation(leg.departure);
                if (departurePoint != null)
                    leg.path.add(departurePoint);
            }

            if (leg instanceof Trip.Public) {
                final Trip.Public publicLeg = (Trip.Public) leg;
                final List<Stop> intermediateStops = publicLeg.intermediateStops;

                if (intermediateStops != null) {
                    for (final Stop stop : intermediateStops) {
                        final Point stopPoint = pointFromLocation(stop.location);
                        if (stopPoint != null)
                            leg.path.add(stopPoint);
                    }
                }
            }

            if (leg.arrival != null) {
                final Point arrivalPoint = pointFromLocation(leg.arrival);
                if (arrivalPoint != null)
                    leg.path.add(arrivalPoint);
            }
        }
    }

    protected boolean onFindAlternativeConnections(
            final Stop stop,
            final JourneyRef feederJourneyRef, final JourneyRef connectionJourneyRef,
            final QueryTripsRunnable.TripRequestData queryTripsRequestData) {
        // override if implemented
        return false;
    }
}
