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
import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.LocationHelper;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Fare;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Style;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.Trip.Individual;
import de.schildbach.pte.dto.Trip.Leg;
import de.schildbach.pte.dto.Trip.Public;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

public class TripDetailsActivity extends OeffiActivity implements LocationListener, LocationAware {
    public static class RenderConfig implements Serializable {
        public boolean isJourney;
    }

    public static class LegContainer {
        public final @Nullable Trip.Leg leg;
        public final Stop transferFrom;
        public final Stop transferTo;

        public LegContainer(final @Nullable Trip.Public baseLeg) {
            this.leg = baseLeg;
            this.transferFrom = null;
            this.transferTo = null;
        }

        public LegContainer(final @Nullable Trip.Individual baseLeg, final Stop transferFrom, final Stop transferTo) {
            this.leg = baseLeg;
            this.transferFrom = transferFrom;
            this.transferTo = transferTo;
        }

        public boolean isTransfer() {
            return leg == null || leg instanceof Trip.Individual;
        }
    }

    private static final String INTENT_EXTRA_NETWORK = TripDetailsActivity.class.getName() + ".network";
    private static final String INTENT_EXTRA_TRIP = TripDetailsActivity.class.getName() + ".trip";
    private static final String INTENT_EXTRA_RENDERCONFIG = TripDetailsActivity.class.getName() + ".isJourney";

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

    private static void start(final Context context, final NetworkId network, final Trip trip, final RenderConfig renderConfig) {
        final Intent intent = new Intent(context, TripDetailsActivity.class);
        intent.putExtra(INTENT_EXTRA_NETWORK, checkNotNull(network));
        intent.putExtra(INTENT_EXTRA_TRIP, checkNotNull(trip));
        intent.putExtra(INTENT_EXTRA_RENDERCONFIG, checkNotNull(renderConfig));
        context.startActivity(intent);
    }

    private LayoutInflater inflater;
    private Resources res;
    private int colorSignificant;
    private int colorInsignificant;
    private int colorHighlighted;
    private int colorPosition, colorPositionBackground;
    private DisplayMetrics displayMetrics;
    private LocationManager locationManager;
    private BroadcastReceiver tickReceiver;

    private ViewGroup legsGroup;
    private OeffiMapView mapView;
    private ToggleImageButton trackButton;

    private NetworkId network;
    private Trip trip;
    private List<LegContainer> legs = new ArrayList<>();
    private RenderConfig renderConfig;
    private Date highlightedTime;
    private Location highlightedLocation;
    private Point location;
    private int selectedLegIndex = -1;

    private final Map<Leg, Boolean> legExpandStates = new HashMap<>();
    private Intent scheduleTripIntent;

    private QueryJourneyRunnable queryJourneyRunnable;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final Handler handler = new Handler();

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
        displayMetrics = res.getDisplayMetrics();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        final Intent intent = getIntent();
        network = (NetworkId) intent.getSerializableExtra(INTENT_EXTRA_NETWORK);
        trip = (Trip) intent.getSerializableExtra(INTENT_EXTRA_TRIP);
        renderConfig = (RenderConfig) intent.getSerializableExtra(INTENT_EXTRA_RENDERCONFIG);

        log.info("Showing {} from {} to {}", renderConfig.isJourney ? "journey" : "trip", trip.from, trip.to);

        // try to build up paths
        for (int iLeg = 0; iLeg < trip.legs.size(); ++iLeg) {
            final Leg prevLeg = (iLeg > 0) ? trip.legs.get(iLeg - 1) : null;
            final Leg leg = trip.legs.get(iLeg);
            final Leg nextLeg = (iLeg + 1 < trip.legs.size()) ? trip.legs.get(iLeg + 1) : null;

            if (leg instanceof Individual) {
                final Stop transferFrom = (prevLeg instanceof Public) ? ((Public) prevLeg).arrivalStop : null;
                final Stop transferTo = (nextLeg instanceof Public) ? ((Public) nextLeg).departureStop : null;
                if (transferFrom != null && transferTo != null)
                    legs.add(new LegContainer((Individual) leg, transferFrom, transferTo));
                else
                    legs.add(new LegContainer((Individual) leg, null, null));
            } else if (leg instanceof Public){
                if (prevLeg instanceof Public) {
                    legs.add(new LegContainer(null,
                            ((Public) prevLeg).arrivalStop, ((Public) leg).departureStop));
                }
                legs.add(new LegContainer((Public) leg));

                if (renderConfig.isJourney) {
                    legExpandStates.put(leg, true);
                }
            }

            if (leg.path == null) {
                leg.path = new ArrayList<>();

                if (leg.departure != null) {
                    final Point departurePoint = pointFromLocation(leg.departure);
                    if (departurePoint != null)
                        leg.path.add(departurePoint);
                }

                if (leg instanceof Public) {
                    final Public publicLeg = (Public) leg;
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

        scheduleTripIntent = scheduleTripIntent(trip);

        setContentView(R.layout.directions_trip_details_content);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        final MyActionBar actionBar = getMyActionBar();
        setPrimaryColor(R.color.bg_action_bar_directions);
        actionBar.setPrimaryTitle(getString(renderConfig.isJourney ? R.string.journey_details_title : R.string.trip_details_title)); // getTitle()
        actionBar.setBack(v -> finish());

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
        actionBar.addButton(R.drawable.ic_today_white_24dp, R.string.directions_trip_details_action_calendar_title)
                .setOnClickListener(v -> {
                    try {
                        startActivity(scheduleTripIntent);
                    } catch (final ActivityNotFoundException x) {
                        new Toast(this).longToast(R.string.directions_trip_details_action_calendar_notfound);
                    }
                });

        legsGroup = findViewById(R.id.directions_trip_details_legs_group);

        updateLocations();
        updateFares(trip.fares);
        int i = LEGSGROUP_INSERT_INDEX;
        for (final LegContainer legC : legs) {
            final View row;
            if (legC.leg instanceof Public)
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

            public boolean isSelectedLeg(final Leg part) {
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
    }

    @Override
    protected void onStart() {
        super.onStart();

        // regular refresh
        tickReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateGUI();
            }
        };
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        updateGUI();
        updateFragments();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
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

    private void updateGUI() {
        final Date now = new Date();
        updateHighlightedTime(now);
        updateHighlightedLocation();

        int i = LEGSGROUP_INSERT_INDEX;
        for (final LegContainer legC : legs) {
            final Leg leg = legC.leg;
            if (leg instanceof Public)
                updatePublicLeg(legsGroup.getChildAt(i), (Public) leg, now);
            else
                updateIndividualLeg(legsGroup.getChildAt(i), legC);
            i++;
        }
    }

    private void updateHighlightedTime(final Date now) {
        highlightedTime = null;

        final Date firstPublicLegDepartureTime = trip.getFirstPublicLegDepartureTime();
        if (firstPublicLegDepartureTime == null
                || firstPublicLegDepartureTime.getTime() - now.getTime() > 10 * DateUtils.MINUTE_IN_MILLIS)
            return;

        for (final LegContainer legC : legs) {
            final Leg leg = legC.leg;
            if (leg instanceof Trip.Public) {
                final Trip.Public publicLeg = (Trip.Public) leg;
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
                if (legC.leg instanceof Trip.Public) {
                    final Trip.Public publicLeg = (Trip.Public) legC.leg;

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

    private void updatePublicLeg(final View row, final Public leg, final Date now) {
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
        if (!renderConfig.isJourney && leg.journeyRef != null) {
            lineView.setClickable(true);
            lineView.setOnClickListener(clickedView -> {
                queryJourneyRunnable = QueryJourneyRunnable.startShowJourney(
                        this, clickedView, queryJourneyRunnable,
                        handler, backgroundHandler,
                        network, leg.journeyRef, leg.departure, leg.arrival);
            });
        }

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
            destinationView.setText(Constants.DESTINATION_ARROW_PREFIX + destinationName);
            destinationView.setOnClickListener(destination.hasId() ? new LocationClickListener(destination) : null);
        } else {
            destinationView.setVisibility(View.GONE);
        }

        final View accessibilityView = row.findViewById(R.id.directions_trip_details_public_entry_accessibility);
        accessibilityView.setVisibility(showAccessibility ? View.VISIBLE : View.GONE);

        final View bicycleCarriageView = row.findViewById(R.id.directions_trip_details_public_entry_bicycle_carriage);
        bicycleCarriageView.setVisibility(showBicycleCarriage ? View.VISIBLE : View.GONE);

        final ToggleImageButton expandButton = row
                .findViewById(R.id.directions_trip_details_public_entry_expand);
        final Boolean checked = legExpandStates.get(leg);
        expandButton.setVisibility(
                !renderConfig.isJourney && intermediateStops != null && !intermediateStops.isEmpty()
                        ? View.VISIBLE
                        : View.GONE);
        expandButton.setChecked(checked != null ? checked : false);
        expandButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            legExpandStates.put(leg, isChecked);
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
    }

    private void updateIndividualLeg(final View row, final LegContainer legContainer) {
        final TextView textView = row.findViewById(R.id.directions_trip_details_individual_entry_text);
        String legText = null;
        final Individual leg = (Individual) legContainer.leg;
        final int iconResId;
        int requiredSecs = 0;
        final ImageButton mapView = row.findViewById(R.id.directions_trip_details_individual_entry_map);
        mapView.setVisibility(View.GONE);
        mapView.setOnClickListener(null);
        if (leg != null) {
            requiredSecs = leg.min * 60;
            final String distanceStr = leg.distance != 0 ? "(" + leg.distance + "m) " : "";
            final int textResId;
            if (leg.type == Individual.Type.WALK) {
                textResId = R.string.directions_trip_details_walk;
                iconResId = R.drawable.ic_directions_walk_grey600_24dp;
            } else if (leg.type == Individual.Type.BIKE) {
                textResId = R.string.directions_trip_details_bike;
                iconResId = R.drawable.ic_directions_bike_grey600_24dp;
            } else if (leg.type == Individual.Type.CAR) {
                textResId = R.string.directions_trip_details_car;
                iconResId = R.drawable.ic_local_taxi_grey600_24dp;
            } else if (leg.type == Individual.Type.TRANSFER) {
                textResId = R.string.directions_trip_details_transfer;
                iconResId = R.drawable.ic_local_taxi_grey600_24dp;
            } else {
                throw new IllegalStateException("unknown type: " + leg.type);
            }
            legText = getString(textResId, leg.min, distanceStr, leg.arrival.uniqueShortName());

            if (leg.arrival.hasCoord()) {
                mapView.setVisibility(View.VISIBLE);
                mapView.setOnClickListener(new MapClickListener(leg.arrival));
            }
        } else {
            iconResId = R.drawable.ic_directions_walk_grey600_24dp;
        }

        String transferText = null;
        final Stop transferFrom = legContainer.transferFrom;
        final Stop transferTo = legContainer.transferTo;
        if (transferFrom != null) {
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

        textView.setText(Html.fromHtml(text));
        textView.setCompoundDrawablesWithIntrinsicBounds(iconResId, 0, 0, 0);
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

    private View stopRow(final PearlView.Type pearlType, final Stop stop, final Public leg, final Date highlightedTime,
            final boolean highlightLocation, final Date now, final CollapseColumns collapseColumns) {
        final View row = inflater.inflate(R.layout.directions_trip_details_public_entry_stop, null);

        final boolean isTimePredicted;
        final Date time;
        final Long delay;
        final boolean isCancelled;
        final boolean isPositionPredicted;
        final Position position;
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
        } else if (pearlType == PearlView.Type.ARRIVAL
                || ((pearlType == PearlView.Type.INTERMEDIATE || pearlType == PearlView.Type.PASSING)
                        && stop.plannedArrivalTime != null)) {
            isTimePredicted = stop.isArrivalTimePredicted();
            time = stop.getArrivalTime();
            delay = stop.getArrivalDelay();
            isCancelled = stop.arrivalCancelled;

            isPositionPredicted = stop.isArrivalPositionPredicted();
            position = stop.getArrivalPosition();
        } else {
            throw new IllegalStateException("cannot handle: " + pearlType);
        }

        // name
        final TextView stopNameView = row.findViewById(R.id.directions_trip_details_public_entry_stop_name);
        stopNameView.setText(location.uniqueShortName());
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
        if (location.hasId())
            stopNameView.setOnClickListener(new StopClickListener(stop));
        else
            stopNameView.setOnClickListener(null);

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
            stopPositionView.setBackgroundDrawable(new ColorDrawable(colorPositionBackground));
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
                    false, false, true, false, false);
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

        public StopClickListener(final Stop stop) {
            this.stop = stop;
        }

        public void onClick(final View v) {
            final PopupMenu contextMenu = new StationContextMenu(TripDetailsActivity.this, v, network, stop.location,
                    null, false, false, true, true, false);
            contextMenu.setOnMenuItemClickListener(item -> {
                int menuItemId = item.getItemId();
                if (menuItemId == R.id.station_context_details) {
                    StationDetailsActivity.start(TripDetailsActivity.this, network, stop.location);
                    return true;
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

        final Public firstPublicLeg = trip.getFirstPublicLeg();
        final Public lastPublicLeg = trip.getLastPublicLeg();
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
            final Leg leg = legC.leg;
            String legStr = null;

            if (leg instanceof Public) {
                final Public publicLeg = (Public) leg;

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
            } else if (leg instanceof Individual) {
                final Individual individualLeg = (Individual) leg;

                final String distanceStr = individualLeg.distance != 0 ? "(" + individualLeg.distance + "m) " : "";
                final int legStrResId;
                if (individualLeg.type == Individual.Type.WALK)
                    legStrResId = R.string.directions_trip_details_text_long_walk;
                else if (individualLeg.type == Individual.Type.BIKE)
                    legStrResId = R.string.directions_trip_details_text_long_bike;
                else if (individualLeg.type == Individual.Type.CAR)
                    legStrResId = R.string.directions_trip_details_text_long_car;
                else if (individualLeg.type == Individual.Type.TRANSFER)
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
}
