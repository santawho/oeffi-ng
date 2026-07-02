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
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Criteria;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.provider.CalendarContract;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.Lifecycle;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.DeviceAdmin;
import de.schildbach.oeffi.DeviceLocationAware;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.TripAware;
import de.schildbach.oeffi.trampoline.AppLinkActivity;
import de.schildbach.oeffi.tripeval.TripGeoUtils;
import de.schildbach.oeffi.util.GoogleMapsUtils;
import de.schildbach.oeffi.util.HorizontalPager;
import de.schildbach.oeffi.util.geofiles.GeoFileProducer;
import de.schildbach.oeffi.util.geofiles.GpxProducer;
import de.schildbach.oeffi.util.geofiles.KmlProducer;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.util.TimeSpec.DepArr;
import de.schildbach.oeffi.tripeval.TripRenderer;
import de.schildbach.oeffi.directions.navigation.TripNavigatorActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.stations.StationsActivity;
import de.schildbach.oeffi.util.ClockUtils;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.HtmlUtils;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.oeffi.util.locationview.LocationTextView;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Destination;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.TripRef;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.dto.Fare;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Style;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.TransferDetails;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.TripShare;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class TripDetailsActivity extends OeffiActivity implements LocationListener, DeviceLocationAware {
    public static class RenderConfig implements Serializable {
        private static final long serialVersionUID = 6006525041994219717L;

        public boolean isJourney;
        public boolean isOperation;
        public boolean isNavigation;
        public boolean isAlternativeConnectionSearch;
        public int actionBarColorId;
        public QueryTripRunnable.TripRequestData queryTripsRequestData;
    }

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
                    NetworkId.valueOf(intent.getStringExtra(INTENT_EXTRA_NETWORK_NAME)),
                    (Trip) intent.getSerializableExtra(INTENT_EXTRA_TRIP),
                    (RenderConfig) intent.getSerializableExtra(INTENT_EXTRA_RENDERCONFIG));
        }
    }

    public static void startJourney(
            final Context context,
            final NetworkId network,
            final Trip.Public journeyLeg,
            final Date loadedAt,
            final int intentFlags) {
        final Trip trip = TripUtils.createTripFromJourney(loadedAt, journeyLeg);
        final RenderConfig renderConfig = new RenderConfig();
        renderConfig.isJourney = true;
        renderConfig.isOperation = false;
        final Intent intent = buildStartIntent(TripDetailsActivity.class, context, network, trip, renderConfig);
        intent.addFlags(intentFlags);
        context.startActivity(intent);
    }

    public static void start(
            final Context context,
            final NetworkId network,
            final Trip trip,
            final int intentFlags) {
        start(context, network, trip, new RenderConfig(), intentFlags);
    }

    public static void start(
            final Context context,
            final NetworkId network, final Trip trip,
            final RenderConfig renderConfig) {
        start(context, network, trip, renderConfig, 0);
    }

    protected static void start(
            final Context context,
            final NetworkId network, final Trip trip,
            final RenderConfig renderConfig,
            final int intentFlags) {
        final Intent intent = buildStartIntent(TripDetailsActivity.class, context, network, trip, renderConfig);
        intent.addFlags(intentFlags);
        context.startActivity(intent);
    }

    public static void startForResult(
            final Activity context, final int requestCode,
            final NetworkId network, final Trip trip,
            final RenderConfig renderConfig) {
        context.startActivityForResult(buildStartIntent(TripDetailsActivity.class, context, network, trip, renderConfig), requestCode);
    }

    protected static Intent buildStartIntent(
            final Class<? extends TripDetailsActivity> activityClass, final Context context,
            final NetworkId network, final Trip trip, final RenderConfig renderConfig) {
        final Intent intent = new Intent(context, activityClass);
        intent.putExtra(INTENT_EXTRA_NETWORK_NAME, requireNonNull(network).name());
        intent.putExtra(INTENT_EXTRA_TRIP, requireNonNull(trip));
        intent.putExtra(INTENT_EXTRA_RENDERCONFIG, requireNonNull(renderConfig));
        return intent;
    }

    protected MyActionBar actionBar;
    protected LayoutInflater inflater;
    protected HorizontalPager viewPager;
    protected Resources res;
    protected int colorDefaultBackground;
    protected int colorSignificant;
    protected int colorInsignificant;
    protected int colorHighlighted;
    protected int colorCancelled;
    protected int colorSimulated;
    protected int colorPosition, colorPositionBackground, colorPositionBackgroundChanged;
    protected int colorLegPublicPastBackground, colorLegPublicNowBackground, colorLegPublicFutureBackground;
    protected int colorLegIndividualPastBackground, colorLegIndividualNowBackground,
            colorLegIndividualFutureBackground, colorLegIndividualTransferCriticalBackground;
    protected DisplayMetrics displayMetrics;
    private BroadcastReceiver tickReceiver;
    private int showScreenIdWhenUnlocked = R.id.directions_trip_details_list_frame;
    private int showScreenIdWhenLocked = R.id.navigation_next_event;

    private NestedScrollView legsScrollView;
    protected View legsScrollFocusView;
    private ViewGroup legsGroup;
    private Space marginView;
    private ToggleImageButton trackButton;
    protected boolean mustEnableTrackButton;

    protected TripRenderer tripRenderer;
    protected RenderConfig renderConfig;
    private PTDate highlightedTime;
    private boolean locationTrackingEnabled;
    private String locationProvider;
    private Point deviceLocation;
    private Double deviceBearingDegrees;
    private Double deviceSpeedMetersPerSecond;
    private Date deviceLocationTime;
    private int selectedLegIndex = -1;
    protected boolean isPaused = false;
    protected SwipeRefreshLayout swipeRefreshForTripList;
    protected SwipeRefreshLayout swipeRefreshForNextEvent;

    private int LEGSGROUP_INSERT_INDEX;

    private QueryJourneyRunnable queryJourneyRunnable;
    private HandlerThread backgroundThread;
    protected Handler backgroundHandler;
    protected final Handler handler = new Handler();

    protected boolean isShowCompactTimes;

    protected static final Logger log = LoggerFactory.getLogger(TripDetailsActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                && prefs.getBoolean("user_interface_trip_details_on_lockscreen_enabled", false)) {
            setShowWhenLocked(true);
        }

        backgroundThread = new HandlerThread("TripDetails.queryTripsThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        inflater = getLayoutInflater();
        res = getResources();
        colorDefaultBackground = ViewUtils.getAttrColor(this, R.attr.bg_level0);
        colorSignificant = res.getColor(R.color.fg_significant);
        colorInsignificant = res.getColor(R.color.fg_insignificant);
        colorHighlighted = res.getColor(R.color.fg_highlighted);
        colorCancelled = res.getColor(R.color.fg_cancelled);
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
        colorLegIndividualTransferCriticalBackground = res.getColor(R.color.bg_trip_details_individual_transfer_critical);
        displayMetrics = res.getDisplayMetrics();

        final IntentData intentData = new IntentData(getIntent());
        renderConfig = intentData.renderConfig;
        network = intentData.network;
        final Trip baseTrip = intentData.trip;

        log.info(
                "Showing {} from {} to {}",
                renderConfig.isOperation ? "operation"
                        : renderConfig.isJourney ? "journey"
                        : "trip",
                baseTrip.from, baseTrip.to);

        setupFromTrip(baseTrip);

        final View contentView = setContentView(R.layout.directions_trip_details_content, isTaskRoot());
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            marginView.setMinimumHeight(insets.bottom);
            return windowInsets;
        });

        marginView = findViewById(R.id.directions_trip_details_footer_margin);

        actionBar = getMyActionBar();
        actionBar.setBack(isTaskRoot() ? null : v -> goBack());

        final long duration = tripRenderer.trip.getDuration();
        final Long publicDuration = tripRenderer.trip.getPublicDuration();
        final String durationFormatted = (publicDuration == null || publicDuration == duration)
                ? Formats.formatTimeSpanHM(duration, false)
                : Formats.formatTimeSpanHM(duration, false)
                    + " / " + Formats.formatTimeSpanHM(publicDuration, false);
        final String durationText = getString(R.string.directions_trip_details_duration, durationFormatted);
        final Integer numChanges = tripRenderer.trip.getNumChanges();
        final String numChangesText = numChanges == null || numChanges <= 0 ? null :
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
                disableTracking();
            }

            getMapView().zoomToAll();
            updateGUI();
        });

        addShowMapButtonToActionBar();

        final boolean isShareCalendarVisible = true;
        final boolean isShareCalendarWithLink = true; // !renderConfig.isNavigation && !renderConfig.isAlternativeConnectionSearch;

        actionBar.addButton(R.drawable.ic_share_white_24dp, R.string.directions_trip_details_action_share_title)
                .setOnClickListener(v -> {
                    final PopupMenu popupMenu = new PopupMenu(TripDetailsActivity.this, v);
                    final Menu menu = popupMenu.getMenu();
                    new MenuInflater(this).inflate(R.menu.directions_trip_details_action_share, menu);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        popupMenu.setForceShowIcon(true);
                    if (isShareCalendarVisible)
                        menu.findItem(R.id.directions_trip_details_action_add_to_calendar).setVisible(true);
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
                                            @SuppressLint("UnsafeImplicitIntentLaunch")
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
                                            @SuppressLint("UnsafeImplicitIntentLaunch")
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
                        } else if (itemId == R.id.directions_trip_details_action_open_external_kml_maps_app) {
                            intentSupplier = () -> showInExternalMapsApp(new KmlProducer(), R.string.directions_trip_details_action_open_external_kml_maps_app_title);
                        } else if (itemId == R.id.directions_trip_details_action_open_external_gpx_maps_app) {
                            intentSupplier = () -> showInExternalMapsApp(new GpxProducer(), R.string.directions_trip_details_action_open_external_gpx_maps_app_title);
                        } else if (itemId == R.id.directions_trip_details_action_add_to_calendar) {
                            shareCalendarEntry(isShareCalendarWithLink);
                            return true;
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
//        if (isShareCalendarVisible) {
//            actionBar.addButton(R.drawable.ic_today_white_24dp, R.string.directions_trip_details_action_calendar_title)
//                    .setOnClickListener(v -> shareCalendarEntry());
//        }
        addActionBarButtons();

        legsScrollView = findViewById(R.id.directions_trip_details_legs_scroll);
        legsGroup = findViewById(R.id.directions_trip_details_legs_group);

        updateDeveloperInfo();
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
                .setText(Html.fromHtml(getString(R.string.directions_trip_details_realtime), Html.FROM_HTML_MODE_COMPACT));

//        final View disclaimerView = findViewById(R.id.directions_trip_details_disclaimer_group);
//        ViewCompat.setOnApplyWindowInsetsListener(disclaimerView, (v, windowInsets) -> {
//            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(0, 0, 0, insets.bottom);
//            return windowInsets;
//        });
//        disclaimerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
//            final int height = disclaimerView.getHeight() - disclaimerView.getPaddingBottom();
//            final Space marginView = findViewById(R.id.directions_trip_details_footer_margin);
//            marginView.setMinimumHeight(height);
//        });
        final TextView disclaimerSourceView = findViewById(R.id.directions_trip_details_disclaimer_source);
        updateDisclaimerSource(disclaimerSourceView, network, null);

        getMapView().setTripAware(new TripAware() {
            public Trip getTrip() {
                return tripRenderer.trip;
            }

            @Override
            public int getNumberOfLegs() {
                return tripRenderer.legs.size();
            }

            @Override
            public LegInfo getLegInfo(final int legIndex) {
                return new LegInfo() {
                    @Override
                    public boolean isPublicLeg() {
                        final TripRenderer.LegContainer legC = tripRenderer.legs.get(legIndex);
                        return legC.publicLeg != null;
                    }

                    @Override
                    public Trip.Leg getLeg() {
                        final TripRenderer.LegContainer legC = tripRenderer.legs.get(legIndex);
                        final Trip.Individual individualLeg = legC.individualLeg;
                        if (individualLeg != null)
                            return individualLeg;
                        return legC.publicLeg;
                    }

                    @Override
                    public List<Point> getPath() {
                        final TripRenderer.LegContainer legC = tripRenderer.legs.get(legIndex);
                        final Trip.Individual individualLeg = legC.individualLeg;
                        if (individualLeg != null)
                            return TripGeoUtils.getPathForLeg(individualLeg);

                        final Trip.Public publicLeg = legC.publicLeg;
                        if (publicLeg == null)
                            return null;
                        return TripGeoUtils.getPathForLegStops(publicLeg);
                    }
                };
            }

            public void selectLeg(final int partIndex) {
                selectedLegIndex = partIndex;
                getMapView().zoomToAll();
            }

            public boolean hasSelection() {
                return selectedLegIndex != -1;
            }

            @Override
            public boolean isSelectedLeg(final int legIndex) {
                return selectedLegIndex == legIndex;
            }
        });

        final View.OnClickListener exitNextEventViewClicked = view -> setShowPage(R.id.directions_trip_details_list_frame);
        final View.OnLongClickListener lockDeviceNextEventViewLongClicked = view -> {
            DeviceAdmin.enterLockScreenAndShutOffDisplay(this, true);
            return true;
        };

        viewPager = findViewById(R.id.directions_trip_details_pager);

        swipeRefreshForTripList = findViewById(R.id.directions_trip_details_list_content);
        swipeRefreshForTripList.setEnabled(false);

        inflater.inflate(getNextEventLayoutId(), viewPager);

        final View bottomOffset = findViewById(R.id.navigation_next_event_bottom_offset);
        ViewCompat.setOnApplyWindowInsetsListener(bottomOffset, (view, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            final ViewGroup.LayoutParams layoutParams = bottomOffset.getLayoutParams();
            layoutParams.height = insets.bottom;
            bottomOffset.setLayoutParams(layoutParams);
            return windowInsets;
        });

        swipeRefreshForNextEvent = findViewById(R.id.navigation_next_event);
        swipeRefreshForNextEvent.setOnClickListener(exitNextEventViewClicked);
        swipeRefreshForNextEvent.setEnabled(false);

        final View nextEventContainerView = findViewById(R.id.navigation_next_event_container);
        nextEventContainerView.setOnClickListener(exitNextEventViewClicked);
        final View nextEventBackView = findViewById(R.id.navigation_next_event_back_to_itinerary);
        nextEventBackView.setOnClickListener(exitNextEventViewClicked);

        if (allowScreenLock()) {
            swipeRefreshForNextEvent.setLongClickable(true);
            swipeRefreshForNextEvent.setOnLongClickListener(lockDeviceNextEventViewLongClicked);

            nextEventContainerView.setLongClickable(true);
            nextEventContainerView.setOnLongClickListener(lockDeviceNextEventViewLongClicked);

            nextEventBackView.setLongClickable(true);
            nextEventBackView.setOnLongClickListener(lockDeviceNextEventViewLongClicked);
        }

        viewPager.addDragExceptionView(findViewById(R.id.vertical_map_frame));
        viewPager.setOnScreenSwitchListener((prevScreen, newScreen) -> {
            final int newId = viewPager.getCurrentView().getId();
            shownPageChanged(newId);
        });

        if (isTripDetailsLoadingEnabled()) {
            backgroundHandler.post(() -> {
                final Trip newTrip = loadTripDetails(tripRenderer.trip);
                runOnUiThread(() -> {
                    setupFromTrip(newTrip);
                    updateGUI();
                });
            });
        }
    }

    protected int getNextEventLayoutId() {
        return R.layout.navigation_next_event_trip;
    }

    @Override
    protected void setMapVisible(final boolean visible) {
        if (!isShowingItinerary()) {
            super.setMapVisible(true);
            setShowPage(Page.ITINERARY);
        } else {
            super.setMapVisible(visible);
        }
    }

    private boolean keepDisplayOn;

    protected void setMustKeepDisplayOn(final boolean keepDisplayOn) {
        if (keepDisplayOn != this.keepDisplayOn) {
            this.keepDisplayOn = keepDisplayOn;
            getWindow().setFlags(keepDisplayOn ? -1 : 0, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    protected boolean isTripDetailsLoadingEnabled() {
        return prefs.getBoolean(Constants.PREFS_KEY_USER_INTERFACE_DIRECTIONS_TRIPEXTRAINFO_ENABLED, false);
    }

    protected Trip loadTripDetails(final Trip trip) {
        final NetworkProvider provider = NetworkProviderFactory.provider(network);
        if (!provider.hasCapabilities(NetworkProvider.Capability.TRIP_DETAILS))
            return trip;
        Trip tripWithDetails = null;
        try {
            tripWithDetails = provider.queryTripDetails(trip, null);
        } catch (final IOException e) {
            log.error("loadTripDetails", e);
        }
        if (tripWithDetails == null) {
            new Toast(this).longToast(R.string.directions_trip_details_extra_data_error);
            return trip;
        }
        return tripWithDetails;
    }

    private void shareCalendarEntry(final boolean withLink) {
        backgroundHandler.post(() -> {
            try {
                final Intent intent = getSendTripToCalendarIntent(tripRenderer.trip, withLink);
                if (intent != null)
                    runOnUiThread(() -> startActivity(intent));
            } catch (final ActivityNotFoundException x) {
                new Toast(this).longToast(R.string.directions_trip_details_action_calendar_notfound);
            }
        });
    }

    protected boolean allowScreenLock() {
        return false;
    }

    private void updateDeveloperInfo() {
        final TextView devinfoView = findViewById(R.id.directions_trip_details_devinfo);
        if (isDeveloperElementsEnabled()) {
            devinfoView.setVisibility(View.VISIBLE);
            final long loadedAt = tripRenderer.trip.loadedAt.getTime();
            final long updatedAt = tripRenderer.trip.updatedAt.getTime();
            devinfoView.setText(String.format("loaded at %s, updated at %s",
                    Formats.formatTime(timeZoneSelector, loadedAt, PTDate.SYSTEM_OFFSET),
                    Formats.formatTime(timeZoneSelector, updatedAt, PTDate.SYSTEM_OFFSET)));
        } else {
            devinfoView.setVisibility(View.GONE);
        }
    }

    protected View.OnClickListener getStartNavigationClickListener() {
        if (!renderConfig.isJourney) {
            return v -> {
                final Trip trip = tripRenderer.trip;
                QueryStoredTripsProvider.put(getContentResolver(),
                        network, getStoredTripsUsage(),
                        trip, renderConfig.queryTripsRequestData, 0);
                startNavigation(trip, renderConfig);
            };
        }
        return null;
    }

    protected int getActionBarColorId() {
        return renderConfig.isJourney ? R.color.bg_action_bar_journey
                : renderConfig.isAlternativeConnectionSearch ? R.color.bg_action_alternative_directions
                : R.color.bg_action_bar_directions;
    }

    protected int getActionBarTitleStringId() {
        return renderConfig.isJourney ? R.string.journey_details_title : R.string.trip_details_title;
    }

    protected void setupActionBar() {
        setPrimaryColor(renderConfig.actionBarColorId > 0 ? renderConfig.actionBarColorId : getActionBarColorId());
        actionBar.setPrimaryTitle(getString(getActionBarTitleStringId()));

        final View.OnClickListener navigationClickListener = getStartNavigationClickListener();
        if (navigationClickListener != null) {
            final ImageButton navigateButton = actionBar.addButton(
                    R.drawable.ic_navigation_white_24dp,
                    R.string.directions_trip_details_action_start_routing);
            navigateButton.setOnClickListener(navigationClickListener);
        }

        if (!isDriverMode && application.isDriverMode()
                && renderConfig.isJourney && !renderConfig.isOperation) {
            // action to open journey as operation
            final ImageButton openOperationButton = actionBar.addButton(
                    R.drawable.ic_operation_white_24dp,
                    R.string.operation_open_journey_as_operation);
            openOperationButton.setOnClickListener(v -> {
                final Trip.Public journeyLeg = tripRenderer.trip.getFirstPublicLeg();
                if (journeyLeg != null) {
                    queryJourneyRunnable = QueryJourneyRunnable.startShowJourney(
                            this, v, queryJourneyRunnable,
                            handler, backgroundHandler,
                            network, journeyLeg.journeyRef,
                            true,
                            journeyLeg.departure, journeyLeg.arrival,
                            false);
                }
            });
        }
    }

    protected void addBookmarkActionBarButton() {
        final String tripId = tripRenderer.trip.getUniqueId();
        if (tripId != null) {
            final ToggleImageButton bookmarkButton = actionBar.addToggleButton(
                    R.drawable.ic_bookmark_white_24dp,
                    R.string.directions_trip_details_action_bookmark);
            final Long rowId = QueryStoredTripsProvider.getRowId(getContentResolver(),
                    network, getStoredTripsUsage(), tripId);
            bookmarkButton.setChecked(rowId != null);
            bookmarkButton.setOnCheckedChangeListener((v, isChecked) -> {
                final Trip trip = tripRenderer.trip;
                if (isChecked) {
                    QueryStoredTripsProvider.put(getContentResolver(),
                            network, getStoredTripsUsage(),
                            trip, renderConfig.queryTripsRequestData, 0);
                } else {
                    QueryStoredTripsProvider.delete(getContentResolver(),
                            network, getStoredTripsUsage(),
                            trip.getUniqueId());
                }
            });
        }
    }

    protected void addActionBarButtons() {
        if (!renderConfig.isJourney)
            addBookmarkActionBarButton();
    }

    protected String getStoredTripsUsage() {
        return null;
    }

    private long fastRefreshIntervalMs;

    public boolean startPeriodicUpdateGuiRunnable() {
        fastRefreshIntervalMs = getPeriodicUpdateIntervalMs();
        if (fastRefreshIntervalMs > 0) {
            periodicUpdateGuiRunnable.run();
            return true;
        } else {
            stopPeriodicUpdateGuiRunnable();
            return false;
        }
    }

    private void stopPeriodicUpdateGuiRunnable() {
        handler.removeCallbacks(periodicUpdateGuiRunnable);
    }

    private final Runnable periodicUpdateGuiRunnable = new Runnable() {
        @Override
        public void run() {
            fastRefreshIntervalMs = getPeriodicUpdateIntervalMs();
            updateGuiIfApplicable(false);
            if (fastRefreshIntervalMs > 0) {
                requestLocationUpdates(fastRefreshIntervalMs);
                handler.postDelayed(this, fastRefreshIntervalMs);
            } else {
                stopPeriodicUpdateGuiRunnable();
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();

        // periodic refresh
        tickReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                updateGuiIfApplicable(false);
            }
        };
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        updateFragments();
    }

    protected boolean checkAutoRefresh() {
        return true;
    }

    private boolean updateGuiIfApplicable(final boolean force) {
        if (isPaused)
            return false;
        if (!checkAutoRefresh() && !force)
            return false;
        return updateGUI();
    }

    protected long getPeriodicUpdateIntervalMs() {
        return -1;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        requestLocationUpdates();
        checkAutoRefresh();
        if (!startPeriodicUpdateGuiRunnable())
            updateGUI();

        if (mustEnableTrackButton) {
            mustEnableTrackButton = false;
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                trackButton.setChecked(true);
            } else {
                new Toast(this).toast(R.string.stations_location_permission_missing);
            }
        }
    }

    @Override
    protected void onPause() {
        isPaused = true;
        removeLocationUpdates();
        stopPeriodicUpdateGuiRunnable();
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
        removeLocationUpdates();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(final Configuration config) {
        super.onConfigurationChanged(config);
        updateFragments();
    }

    @Override
    public void onBackPressedEvent() {
        if (isShowingNextEvent())
            setShowPage(R.id.directions_trip_details_list_frame);
        else
            super.onBackPressedEvent();
    }

    protected boolean isShowingItinerary() {
        return viewPager.getCurrentView().getId() == R.id.directions_trip_details_list_frame;
    }

    protected boolean isShowingNextEvent() {
        return viewPager.getCurrentView().getId() == R.id.navigation_next_event;
    }

    private void goBack() {
        if (isShowingNextEvent())
            setShowPage(R.id.directions_trip_details_list_frame);
        else
            finish();
    }

    @SuppressLint("MissingPermission")
    protected void enableTracking() {
        if (locationProvider == null) {
            final Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            locationProvider = locationManager.getBestProvider(criteria, true);
        } else {
            new Toast(this).toast(R.string.acquire_location_no_provider);
            disableTracking();
            trackButton.setChecked(false);
        }

        if (!locationTrackingEnabled) {
            locationTrackingEnabled = true;
            requestLocationUpdates();
        }
    }

    protected void disableTracking() {
        if (locationTrackingEnabled) {
            locationTrackingEnabled = false;
            locationProvider = null;
            getMapView().setDeviceLocationAware(null);
            removeLocationUpdates();
        }
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates() {
        if (!locationTrackingEnabled || locationProvider == null)
            return;

        final android.location.Location lastKnownLocation = locationManager.getLastKnownLocation(locationProvider);
        if (lastKnownLocation != null
                && (lastKnownLocation.getLatitude() != 0 || lastKnownLocation.getLongitude() != 0)) {
            onLocationChanged(lastKnownLocation);
        }

        requestLocationUpdates(getPeriodicUpdateIntervalMs());
    }

    private long lastLocationUpdateIntervalMs;

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates(final long updateIntervalMs) {
        if (updateIntervalMs == lastLocationUpdateIntervalMs)
            return;
        if (!locationTrackingEnabled || locationProvider == null)
            return;
        lastLocationUpdateIntervalMs = updateIntervalMs;
        locationManager.removeUpdates(this);
        final Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        locationManager.requestLocationUpdates(updateIntervalMs, 5.0f, criteria, this, null);
    }

    private void removeLocationUpdates() {
        lastLocationUpdateIntervalMs = 0;
        locationManager.removeUpdates(this);
        updateDeviceLocationDependencies(null, null, null, null);
    }

    @Override
    public void onLocationChanged(@NonNull final android.location.Location location) {
        final Point newDeviceLocation = Point.fromDouble(location.getLatitude(), location.getLongitude());
        final Double bearingDegrees;
        if (location.hasBearing()) {
            bearingDegrees = Double.valueOf(location.getBearing());
        } else if (deviceLocation == null) {
            bearingDegrees = null;
        } else {
            final double dist = TripGeoUtils.geoDistanceInMeters(deviceLocation, newDeviceLocation);
            if (dist > 0) {
                bearingDegrees = TripGeoUtils.getBearing(deviceLocation, newDeviceLocation);
            } else {
                return;
            }
        }
        final Double speedMetersPerSecond = location.hasSpeed() ? (double) location.getSpeed() : null;
        updateDeviceLocationDependencies(newDeviceLocation, bearingDegrees, speedMetersPerSecond, new Date());
        updateGuiIfApplicable(true);
    }

    @Override
    public void onProviderEnabled(@NonNull final String provider) {
    }

    @Override
    public void onProviderDisabled(@NonNull final String provider) {
        removeLocationUpdates();

        if (locationTrackingEnabled) {
            disableTracking();
            enableTracking();
        }
    }

    public void onStatusChanged(final String provider, final int status, final Bundle extras) {
    }

    public final Point getDeviceLocation() {
        return deviceLocation;
    }

    public final Location getReferenceLocation() {
        return null;
    }

    public final Double getDeviceBearing() {
        return deviceBearingDegrees;
    }

    @Override
    public Double getDeviceSpeed() {
        return deviceSpeedMetersPerSecond;
    }

    protected boolean mustOpenActivityInNewTask() {
        return false;
    }

    protected void updateFragments() {
        if (mapIsAtBottom) {
            final boolean mapShowing = updateFragments(R.id.directions_trip_details_list_content);
            ViewUtils.setVisibility(marginView, !mapShowing);
        } else {
            updateFragments(R.id.directions_trip_details_content_frame);
        }
    }

    protected boolean isShowCompactTimes() {
        return prefs.getBoolean("user_interface_directions_compact_times_enabled", false);
    }

    protected boolean mustScrollIntoView() {
        return false;
    }

    protected boolean updateGUI() {
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
            return false;

        legsScrollFocusView = null;

        isShowCompactTimes = isShowCompactTimes();

        final Date now = new Date();
        updateDeviceLocationDependencies(deviceLocation, deviceBearingDegrees, deviceSpeedMetersPerSecond, now);
        updateHighlightedTime(now);
        updateDeveloperInfo();

        final boolean isFeasible = tripRenderer.isJourney || tripRenderer.isFeasible();
        ViewUtils.setVisibility(findViewById(R.id.directions_trip_details_not_feasible), !isFeasible);

        TripRenderer.LegContainer currentLeg = null;
        int i = LEGSGROUP_INSERT_INDEX;
        for (int iLeg = 0; iLeg < tripRenderer.legs.size(); ++iLeg) {
            final TripRenderer.LegContainer legC = tripRenderer.legs.get(iLeg);
            final View legView = legsGroup.getChildAt(i);
            final boolean isCurrent;
            if (legC.publicLeg != null) {
                final int iWalk = iLeg + 1;
                final TripRenderer.LegContainer walkLegC = (iWalk < tripRenderer.legs.size()) ? tripRenderer.legs.get(iWalk) : null;
                final int iNext = iLeg + 2;
                final TripRenderer.LegContainer nextLegC = (iNext < tripRenderer.legs.size()) ? tripRenderer.legs.get(iNext) : null;
                isCurrent = updatePublicLeg(legView, legC, walkLegC, nextLegC, now);
            } else {
                isCurrent = updateIndividualLeg(legView, legC, now);
            }
            if (isCurrent)
                currentLeg = legC;
            i++;
        }

        if (legsScrollFocusView != null && mustScrollIntoView()) {
            handler.post(() -> {
                final int currentScrollY = legsScrollView.getScrollY();
                int y = 0;
                for (View v = legsScrollFocusView; v != null && v != legsScrollView; v = (View) v.getParent()) {
                    y += v.getTop();
                }
                y = Math.max(0, y - 200);
                if (y != currentScrollY)
                    legsScrollView.scrollTo(0, y);
            });
        }

        float totalProbability = 1f;
        final TransferDetails[] transferDetails = tripRenderer.trip.transferDetails;
        if (transferDetails != null) {
            for (final TransferDetails details : transferDetails) {
                if (details == null)
                    continue;
                final Float probability = details.feasibilityProbability;
                if (probability == null)
                    continue;
                totalProbability *= probability;
            }
        }
        setGradientBackground(findViewById(R.id.directions_trip_details_summary),
                getColor(R.color.bg_divider), totalProbability);

        tripRenderer.evaluateByTime(now);

        updateNavigationInstructions();

        final int showId;
        if (currentLeg == null) {
            // showScreenIdWhenUnlocked = R.id.directions_trip_details_list_frame;
            showScreenIdWhenLocked = R.id.navigation_next_event;
            // showId = R.id.directions_trip_details_list_frame;
            if (DeviceAdmin.isScreenLocked()) {
                showId = R.id.navigation_next_event;
            } else {
                showId = showScreenIdWhenUnlocked;
            }
            findViewById(R.id.directions_trip_details_finished).setVisibility(View.VISIBLE);
//            findViewById(R.id.navigation_next_event).setVisibility(View.GONE);
        } else {
            if (DeviceAdmin.isScreenLocked()) {
                showId = showScreenIdWhenLocked;
            } else {
                showId = showScreenIdWhenUnlocked;
                showScreenIdWhenLocked = R.id.navigation_next_event;
            }
            findViewById(R.id.directions_trip_details_finished).setVisibility(View.GONE);
//            findViewById(R.id.navigation_next_event).setVisibility(View.VISIBLE);
        }

        // delay setting the horizontal scrolling page after the layout has been done
        viewPager.post(() -> viewPager.setCurrentView(showId, true));
        return true;
    }

    private void updateHighlightedTime(final Date now) {
        highlightedTime = null;

        final PTDate firstPublicLegDepartureTime = tripRenderer.trip.getFirstPublicLegDepartureTime();
        if (firstPublicLegDepartureTime == null
                || firstPublicLegDepartureTime.getTime() - now.getTime() > 10 * DateUtils.MINUTE_IN_MILLIS)
            return;

        for (final TripRenderer.LegContainer legC : tripRenderer.legs) {
            if (legC.publicLeg == null)
                continue;

            // final Trip.Public publicLeg = legC.simulatedPublicLeg != null ? legC.simulatedPublicLeg : legC.publicLeg;
            final Trip.Public publicLeg = legC.publicLeg;
            PTDate arrivalTime, departureTime;

            departureTime = publicLeg.getDepartureTime();
            if (departureTime.after(now)) {
                highlightedTime = departureTime;
                return;
            }

            final List<Stop> intermediateStops = publicLeg.intermediateStops;
            if (intermediateStops != null) {
                for (final Stop stop : intermediateStops) {
                    arrivalTime = stop.getArrivalTime();
                    if (arrivalTime != null && arrivalTime.after(now)) {
                        highlightedTime = arrivalTime;
                        return;
                    }

                    departureTime = stop.getDepartureTime();
                    if (departureTime != null && departureTime.after(now)) {
                        highlightedTime = departureTime;
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

    private void updateDeviceLocationDependencies(
            final Point newDeviceLocation,
            final Double bearingDegrees,
            final Double speedMetersPerSecond,
            final Date now) {
        deviceLocation = newDeviceLocation;
        deviceLocationTime = now;
        deviceBearingDegrees = bearingDegrees;
        deviceSpeedMetersPerSecond = speedMetersPerSecond;

        setupTripRenderer();

        if (locationTrackingEnabled)
            getMapView().setDeviceLocationAware(this);
    }

    private void setupTripRenderer() {
        tripRenderer.setRefPoint(
                deviceLocation,
                deviceBearingDegrees == null ? 0 : deviceBearingDegrees,
                deviceSpeedMetersPerSecond == null ? 0 : deviceSpeedMetersPerSecond,
                deviceLocationTime);
    }

    protected void setupFromTrip(final Trip trip) {
        this.tripRenderer = new TripRenderer(tripRenderer, trip, renderConfig.isJourney, new Date());
        setupTripRenderer();
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
                unitView.setText(String.format("(%s)", Optional.ofNullable(fare.units).orElse(fare.unitName)));
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
        final TripRenderer.LegContainer nearestPublicLeg = tripRenderer.nearestPublicLeg;
        final boolean isHighlightedLeg = nearestPublicLeg == legC;
        final int highlightedLocationIndex = isHighlightedLeg ? nearestPublicLeg.nearestStopIndex : -1;
        final Trip.Public leg = legC.publicLeg;
        final Trip.Public simulatedLeg = legC.simulatedPublicLeg;
        final Destination destination = leg.destination;
        final Location destinationLocation = destination == null ? null : destination.location;
        final String destinationName = Formats.fullLocationName(destinationLocation);
        final boolean showDestination = destinationName != null;
        final boolean showAccessibility = leg.line.hasAttr(Line.Attr.WHEEL_CHAIR_ACCESS)
                && !NetworkProvider.Accessibility.NEUTRAL.equals(application.prefsGetAccessibility());
        final boolean showBicycleCarriage = leg.line.hasAttr(Line.Attr.BICYCLE_CARRIAGE)
                && application.prefsIsBicycleTravel();
        final List<Stop> intermediateStops = leg.intermediateStops;
        final List<Stop> intermediateSimulatedStops = simulatedLeg == null ? null : simulatedLeg.intermediateStops;
        final String message = leg.message != null ? leg.message : leg.line.message;
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
        if (destinationLocation != null) {
            destinationView.setVisibility(View.VISIBLE);
            final String prefix;
            if (!destination.isNotCommonType)
                prefix = Constants.DESTINATION_ARROW_PREFIX;
            else if (destinationLocation.type == LocationType.STATION)
                prefix = Constants.DESTINATION_STATION_ARROW_PREFIX;
            else
                prefix = Constants.DESTINATION_DIRECTION_ARROW_PREFIX;
            destinationView.setText(prefix + Formats.makeBreakableStationName(destinationName));
            if (destinationLocation.hasId()) {
                destinationView.setOnLongClickListener(v -> {
                    final StationContextMenu contextMenu = new StationContextMenu(TripDetailsActivity.this, v, network, destinationLocation, null,
                            false, false, false, true,
                            true, true,
                            false, false,
                            false, false, false,
                            renderConfig.isJourney && ((Trip.Public) tripRenderer.trip.legs.get(0)).exitLocation == null,
                            false);
                    contextMenu.setOnMenuItemClickListener(item -> {
                        final int itemId = item.getItemId();
                        if (itemId == R.id.station_context_show_departures) {
                            StationDetailsActivity.start(TripDetailsActivity.this, network, destinationLocation, leg.getArrivalTime(), null,
                                    shallShowChildActivitiesInNewTask());
                            return true;
                        } else if (itemId == R.id.station_context_nearby_departures) {
                            StationsActivity.start(TripDetailsActivity.this, network, destinationLocation, leg.getArrivalTime());
                            return true;
                        } else if (itemId == R.id.station_map_context_maps_internal) {
                            setMapVisible(true);
                            getMapView().zoomToStations(List.of(destinationLocation), 0);
                            return true;
                        } else {
                            return false;
                        }
                    });
                    contextMenu.show();
                    return true;
                });
            }
        } else {
            destinationView.setVisibility(View.GONE);
        }

        final View accessibilityView = row.findViewById(R.id.directions_trip_details_public_entry_accessibility);
        accessibilityView.setVisibility(showAccessibility ? View.VISIBLE : View.GONE);

        final View bicycleCarriageView = row.findViewById(R.id.directions_trip_details_public_entry_bicycle_carriage);
        bicycleCarriageView.setVisibility(showBicycleCarriage ? View.VISIBLE : View.GONE);

        if (!renderConfig.isJourney && leg.journeyRef != null
                && NetworkProviderFactory.provider(network).hasCapabilities(NetworkProvider.Capability.JOURNEY)) {
            final View.OnClickListener onClickListener = clickedView -> {
                queryJourneyRunnable = QueryJourneyRunnable.startShowJourney(
                        this, clickedView, queryJourneyRunnable,
                        handler, backgroundHandler,
                        network, leg.journeyRef, false, leg.departure, leg.arrival,
                        mustOpenActivityInNewTask());
            };
            lineView.setOnClickListener(onClickListener);
            destinationView.setOnClickListener(onClickListener);
        }

        final ToggleImageButton expandButton = row
                .findViewById(R.id.directions_trip_details_public_entry_expand);
        final Integer legExpandStates = tripRenderer.legExpandStates.get(new TripRenderer.LegKey(leg));
        final boolean isStopsExpanded = legExpandStates != null && (legExpandStates & TripRenderer.LEG_EXPAND_STATE_STOPS) != 0;
        final boolean isMessagesExpanded = legExpandStates != null && (legExpandStates & TripRenderer.LEG_EXPAND_STATE_MESSAGES) != 0;
        ViewUtils.setVisibility(expandButton, !renderConfig.isJourney
                && ((intermediateStops != null && !intermediateStops.isEmpty()) || (message != null)));
        expandButton.setChecked(isStopsExpanded || isMessagesExpanded);
        expandButton.setOnClickListener(v -> {
            tripRenderer.legExpandStates.put(new TripRenderer.LegKey(leg),
                    isStopsExpanded || isMessagesExpanded ? 0 :
                        TripRenderer.LEG_EXPAND_STATE_STOPS | TripRenderer.LEG_EXPAND_STATE_MESSAGES);
            updateGUI();
        });

        final TableLayout stopsView = row.findViewById(R.id.directions_trip_details_public_entry_stops);
        stopsView.removeAllViews();
        final CollapseColumns collapseColumns = new CollapseColumns(NetworkProviderFactory.provider(network).getTimeZone());
        // collapseColumns.dateChanged(now);

        final boolean preferPlanTime = renderConfig.isOperation;

        final Stop departureStop = leg.departureStop;
        final Location departureLocation = departureStop.location;
        final Location entryLocation = leg.entryLocation != null ? leg.entryLocation : departureLocation;

        boolean isArrivalSection = false;

        boolean isHighlightedLocation = highlightedLocationIndex == 0;
        isRowSimulated |= isHighlightedLocation;
        addStopRow(stopsView,
                PearlView.Type.DEPARTURE, false,
                0, departureStop, "-", legC,
                leg.getDepartureTime().equals(highlightedTime),
                isHighlightedLocation,
                preferPlanTime,
                now, collapseColumns,
                isRowSimulated && simulatedLeg != null ? simulatedLeg.departureStop : null);

        if (highlightedLocationIndex >= 0)
            isArrivalSection |= isRowSimulated;
        else
            isArrivalSection |= departureLocation.id != null && departureLocation.id.equals(entryLocation.id);

        String previousPlace = departureLocation.place;

        final int numIntermediateStops = intermediateStops == null ? 0 : intermediateStops.size();
        if (numIntermediateStops > 0) {
            if (isStopsExpanded) {
                for (int intermediateIndex = 0; intermediateIndex < numIntermediateStops; intermediateIndex++) {
                    final int stopIndex = intermediateIndex + 1;
                    final Stop stop = intermediateStops.get(intermediateIndex);
                    final Location stopLocation = stop.location;
                    final PTDate arrivalTime = stop.getArrivalTime();
                    final PTDate departureTime = stop.getDepartureTime();
                    final boolean hasStopTime = arrivalTime != null || departureTime != null;
                    final boolean isArrivalTimeHighlighted = arrivalTime != null && arrivalTime.equals(highlightedTime);
                    final boolean isDepartureTimeHighlighted = departureTime != null && departureTime.equals(highlightedTime);

                    isHighlightedLocation = highlightedLocationIndex == stopIndex;
                    // if (isHighlightedLocation && isDriverJourney)
                    //    isRowSimulated = true;
                    isRowSimulated |= isHighlightedLocation;

                    if (highlightedLocationIndex >= 0)
                        isArrivalSection |= highlightedLocationIndex == stopIndex;

                    final Stop possiblySimulatedStop = intermediateSimulatedStops == null ? null : intermediateSimulatedStops.get(intermediateIndex);
                    final Stop simulatedStop = isRowSimulated ? possiblySimulatedStop : null;
                    final boolean showLongStay = isShowLongStay(stop, isRowSimulated);

                    if (isArrivalSection) {
                        addStopRow(stopsView,
                                hasStopTime ? PearlView.Type.INTERMEDIATE_ARRIVAL : PearlView.Type.PASSING,
                                showLongStay,
                                stopIndex, stop, previousPlace, legC,
                                isArrivalTimeHighlighted || (!showLongStay && isDepartureTimeHighlighted),
                                isHighlightedLocation,
                                preferPlanTime,
                                now, collapseColumns, simulatedStop);

                        if (showLongStay) {
                            addStopRow(stopsView,
                                    PearlView.Type.DEPARTURE_FOR_INTERMEDIATE_ARRIVAL,
                                    showLongStay,
                                    stopIndex, stop, previousPlace, legC,
                                    isDepartureTimeHighlighted,
                                    isHighlightedLocation,
                                    preferPlanTime,
                                    now, collapseColumns, simulatedStop);
                        }
                    } else {
                        if (showLongStay) {
                            addStopRow(stopsView,
                                    PearlView.Type.ARRIVAL_FOR_INTERMEDIATE_DEPARTURE,
                                    showLongStay,
                                    stopIndex, stop, previousPlace, legC,
                                    isArrivalTimeHighlighted,
                                    isHighlightedLocation,
                                    preferPlanTime,
                                    now, collapseColumns, simulatedStop);
                        }

                        addStopRow(stopsView,
                                hasStopTime ? PearlView.Type.INTERMEDIATE_DEPARTURE : PearlView.Type.PASSING,
                                showLongStay,
                                stopIndex, stop, previousPlace, legC,
                                isDepartureTimeHighlighted || (!showLongStay && isArrivalTimeHighlighted),
                                isHighlightedLocation,
                                preferPlanTime,
                                now, collapseColumns, simulatedStop);
                    }
                    isRowSimulated |= isHighlightedLocation;

                    if (stopLocation.place != null)
                        previousPlace = stopLocation.place;

                    if (highlightedLocationIndex < 0)
                        isArrivalSection |= stopLocation.id.equals(entryLocation.id);
                }
            } else {
                int numIntermediateStopsWithStopTime = 0;
                for (final Stop stop : intermediateStops) {
                    final boolean hasStopTime = stop.getArrivalTime() != null || stop.getDepartureTime() != null;
                    if (hasStopTime)
                        numIntermediateStopsWithStopTime++;
                }

                final View collapsedIntermediateStopsRow = collapsedIntermediateStopsRow(
                        leg.getArrivalTime().getTime() - leg.getDepartureTime().getTime(),
                        numIntermediateStopsWithStopTime,
                        leg.line.style);
                stopsView.addView(collapsedIntermediateStopsRow);
                if (numIntermediateStopsWithStopTime > 0) {
                    collapsedIntermediateStopsRow.setOnClickListener(v -> {
                        tripRenderer.legExpandStates.put(new TripRenderer.LegKey(leg),
                                TripRenderer.LEG_EXPAND_STATE_STOPS |
                                    (isMessagesExpanded ? TripRenderer.LEG_EXPAND_STATE_MESSAGES : 0));
                        updateGUI();
                    });
                }
            }
        } else {
            final View collapsedIntermediateStopsRow = collapsedIntermediateStopsRow(
                    leg.getArrivalTime().getTime() - leg.getDepartureTime().getTime(),
                    0, leg.line.style);
            stopsView.addView(collapsedIntermediateStopsRow);
        }

        isArrivalSection = true;

        isHighlightedLocation = highlightedLocationIndex == numIntermediateStops + 1;
        isRowSimulated |= isHighlightedLeg;
        addStopRow(stopsView,
                PearlView.Type.ARRIVAL, false,
                numIntermediateStops + 1, leg.arrivalStop, previousPlace, legC,
                leg.getArrivalTime().equals(highlightedTime),
                isHighlightedLocation,
                preferPlanTime,
                now, collapseColumns,
                isRowSimulated && simulatedLeg != null ? simulatedLeg.arrivalStop : null);
        isRowSimulated |= isHighlightedLocation;

        stopsView.setColumnCollapsed(1, collapseColumns.collapseDateColumn);
        stopsView.setColumnCollapsed(3, collapseColumns.collapseDelayColumn);
        stopsView.setColumnCollapsed(4, collapseColumns.collapsePositionColumn);

        final TextView messageView = row.findViewById(R.id.directions_trip_details_public_entry_message);
        if (message != null) {
            messageView.setVisibility(View.VISIBLE);
            final String displayMessage;
            if (isMessagesExpanded || message.length() < 80) {
                displayMessage = message;
                messageView.setTextColor(colorSignificant);
            } else {
                displayMessage = getString(R.string.directions_trip_details_shortened_message,
                        message.substring(0, Math.min(message.length(), 60)));
                messageView.setTextColor(colorInsignificant);
            };
            final Spanned html = Html.fromHtml(HtmlUtils.makeLinksClickableInHtml(displayMessage), Html.FROM_HTML_MODE_COMPACT);
            messageView.setText(html);
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            messageView.setOnClickListener(v -> {
                tripRenderer.legExpandStates.put(new TripRenderer.LegKey(leg),
                        TripRenderer.LEG_EXPAND_STATE_MESSAGES |
                                (isStopsExpanded ? TripRenderer.LEG_EXPAND_STATE_STOPS : 0));
                updateGUI();
            });

        } else {
            messageView.setVisibility(View.GONE);
        }

        final TextView devinfoView = row.findViewById(R.id.directions_trip_details_public_entry_devinfo);
        if (isDeveloperElementsEnabled()) {
            devinfoView.setVisibility(View.VISIBLE);
            final Date updateDelayedUntil = leg.updateDelayedUntil;
            devinfoView.setText(String.format("loaded at %s, %s",
                    Formats.formatTime(timeZoneSelector, leg.loadedAt.getTime(), PTDate.SYSTEM_OFFSET),
                    updateDelayedUntil == null ? "is fresh" : String.format("next update at %s",
                            Formats.formatTime(timeZoneSelector, updateDelayedUntil.getTime(), PTDate.SYSTEM_OFFSET))));
        } else {
            devinfoView.setVisibility(View.GONE);
        }

        final View progressView = row.findViewById(R.id.directions_trip_details_public_entry_progress);
        progressView.setVisibility(View.GONE);
        final PTDate beginTime = departureStop.getDepartureTime();
        final PTDate endTime = leg.arrivalStop.getArrivalTime();
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
        progressView.setOnClickListener(view -> setShowPage(R.id.navigation_next_event));

        final TextView progressText = row.findViewById(R.id.directions_trip_details_public_entry_progress_text);
        progressText.setText(getLeftTimeFormatted(now, endTime));

        return true;
    }

    protected boolean isLongStay(final PTDate arrivalTime, final PTDate departureTime, final long longStayMinMs) {
        if (arrivalTime == null)
            return false;
        if (departureTime == null)
            return false;
        final long stayMillis = departureTime.getTime() - arrivalTime.getTime();
        return stayMillis >= longStayMinMs;
    }

    protected boolean isShowLongStay(final Stop stop, final boolean isSimulatedRow) {
        // more than 5 minutes stay, then show departure row
        return isLongStay(stop.plannedArrivalTime, stop.plannedDepartureTime, 300000)
                || isLongStay(stop.predictedArrivalTime, stop.predictedDepartureTime, 300000);
    }

    @SuppressLint("DefaultLocale")
    protected boolean updateIndividualLeg(
            final View row,
            final TripRenderer.LegContainer legC,
            final Date now) {
        final Trip.Individual leg = legC.individualLeg;
        final TripRenderer.LegContainer transferFromLegC = legC.transferFrom;
        final TripRenderer.LegContainer transferToLegC = legC.transferTo;
        final Trip.Public transferFromLeg = transferFromLegC == null ? null : transferFromLegC.publicLeg;
        final Trip.Public transferFromSimulatedLeg =
                transferFromLegC != null && transferFromLegC == tripRenderer.nearestPublicLeg
                        ? transferFromLegC.simulatedPublicLeg : null;
        final Trip.Public transferToLeg = transferToLegC == null ? null : transferToLegC.publicLeg;
        final Stop transferFrom = transferFromLeg == null ? null : transferFromLeg.arrivalStop;
        final Stop transferFromSimulated = transferFromSimulatedLeg == null ? null : transferFromSimulatedLeg.arrivalStop;
        final Stop transferTo = transferToLeg == null ? null : transferToLeg.departureStop;

        final Float feasibilityProbability = legC.transferDetails == null ? null
                : legC.transferDetails.feasibilityProbability;

        final ViewGroup mainElement = row.findViewById(R.id.directions_trip_details_individual_entry_main_element);
        updateIndividualElement(mainElement, false, leg, transferFrom, transferTo, feasibilityProbability);
        final ViewGroup simulatedElement = row.findViewById(R.id.directions_trip_details_individual_entry_simulated_element);
        if (transferFromSimulated != null) {
            updateIndividualElement(simulatedElement, true, leg, transferFromSimulated, transferTo, null);
        } else {
            simulatedElement.setVisibility(View.GONE);
        }

        final ImageButton mapView = row.findViewById(R.id.directions_trip_details_individual_entry_map);
        final Location transferFromLocation = transferFrom == null ? null : transferFrom.location;
        final Location transferToLocation = transferTo == null ? null : transferTo.location;
        if (transferFromLocation != null || transferToLocation != null) {
            mapView.setVisibility(View.VISIBLE);
            final List<Location> mapBoundingLocations = new ArrayList<>();
            if (transferFromLocation != null && transferFromLocation.hasCoord())
                mapBoundingLocations.add(transferFromLocation);
            if (transferToLocation != null && transferToLocation.hasCoord())
                mapBoundingLocations.add(transferToLocation);
            mapView.setOnClickListener(v -> {
                setMapVisible(true);
                getMapView().zoomToStations(mapBoundingLocations, 0);
            });
            final View.OnLongClickListener onLongClickListener = v -> {
                final PopupMenu popupMenu = new PopupMenu(TripDetailsActivity.this, v);
                StationContextMenu.prepareMapMenu(TripDetailsActivity.this, popupMenu.getMenu(),
                        network, transferToLocation != null ? transferToLocation : transferFromLocation);
                popupMenu.setOnMenuItemClickListener(item -> {
                    setMapVisible(true);
                    getMapView().zoomToStations(mapBoundingLocations, 0);
                    return true;
                });
                popupMenu.setForceShowIcon(true);
                popupMenu.show();
                return true;
            };
            mapView.setOnLongClickListener(onLongClickListener);
            row.setOnLongClickListener(onLongClickListener);
        } else {
            mapView.setVisibility(View.GONE);
        }

        final View progressView = row.findViewById(R.id.directions_trip_details_individual_entry_progress);
        progressView.setVisibility(View.GONE);
        final PTDate beginTime = transferFrom != null ? transferFrom.getArrivalTime() : leg == null ? null : leg.departureTime;
        final PTDate endTime = transferTo != null ? transferTo.getDepartureTime() : leg == null ? null : leg.arrivalTime;
        final boolean isNow;
        final int backgroundColor;
        if (transferFrom != null && beginTime != null && now.before(beginTime)) {
            // leg is in the future
            isNow = false;
            backgroundColor = colorLegIndividualFutureBackground;
        } else if (endTime != null && now.after(endTime)) {
            // leg is in the past
            isNow = false;
            backgroundColor = colorLegIndividualPastBackground;
        } else {
            // leg is now
            isNow = true;
            backgroundColor = colorLegIndividualNowBackground;
            progressView.setVisibility(View.VISIBLE);
            progressView.setOnClickListener(view -> setShowPage(R.id.navigation_next_event));

            final TextView progressText = row.findViewById(R.id.directions_trip_details_individual_entry_progress_text);
            progressText.setText(endTime == null ? "???" : getLeftTimeFormatted(now, endTime));
        }

        if (renderConfig.isJourney && !isNow) {
            mainElement.setVisibility(View.GONE);
            simulatedElement.setVisibility(View.GONE);
        }

        setGradientBackground(row, backgroundColor, feasibilityProbability == null ? 1f : feasibilityProbability);
        return isNow;
    }

    protected void updateIndividualElement(
            final ViewGroup elementView,
            final boolean isSimulated,
            final Trip.Individual leg,
            final Stop transferFrom,
            final Stop transferTo,
            final Float feasibilityProbability) {
        elementView.setVisibility(View.VISIBLE);
        final int textColor = isSimulated ? colorSimulated : colorSignificant;
        String legText = null;
        int iconResId;
        int requiredSecs = 0;
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
                if (leg.distance <= application.prefsGetMaxWalkDistance()) {
                    textResId = R.string.directions_trip_details_walk;
                    iconResId = R.drawable.ic_directions_walk_grey600_24dp;
                } else {
                    textResId = R.string.directions_trip_details_transfer;
                    iconResId = R.drawable.ic_local_taxi_grey600_24dp;
                }
            } else {
                throw new IllegalStateException("unknown type: " + leg.type);
            }
            legText = getString(textResId, leg.min, distanceStr, Formats.makeBreakableStationName(leg.arrival.uniqueShortName()));
        } else if (transferFrom != null) {
            // no time walk after some public transport
            iconResId = R.drawable.ic_directions_walk_grey600_24dp;
        } else {
            // walk before anything
            iconResId = R.drawable.ic_stopwatch_black_24;
        }

        String transferText = null;
        String timeText = null;
        boolean timeIsCritical = false;
        boolean isSamePlatform = false;
        if (transferFrom == null) {
            // walk at the beginning
            if (legText == null) {
                if (renderConfig.isJourney) {
                    legText = getString(R.string.directions_trip_details_start_journey);
                } else if (transferTo == null) {
                    legText = getString(R.string.directions_trip_details_start);
                } else {
                    legText = getString(R.string.directions_trip_details_start_at,
                            Formats.makeBreakableStationName(transferTo.location.uniqueShortName()));
                }
            }
        } else if (transferTo == null) {
            // walk at the end
        } else {
            final boolean isWalkIcon;
            isSamePlatform = requiredSecs == 0 && Stop.isSamePlatform(transferFrom, transferTo);
            if (isSamePlatform) {
                isWalkIcon = false;
                iconResId = R.drawable.ic_directions_stay_grey600_24dp;
            } else {
                isWalkIcon = iconResId == R.drawable.ic_directions_walk_grey600_24dp;
            }
            final long arrMinTime = transferFrom.plannedArrivalTime.getTime();
            final long arrMaxTime = transferFrom.predictedArrivalTime != null ? transferFrom.predictedArrivalTime.getTime() : arrMinTime;
            final long arrDelaySecs = (arrMaxTime - arrMinTime) / 1000;
            final long depMinTime = transferTo.plannedDepartureTime.getTime();
            final long depMaxTime = transferTo.predictedDepartureTime != null ? transferTo.predictedDepartureTime.getTime() : depMinTime;
            final long depDelaySecs = (depMaxTime - depMinTime) / 1000;
            final long diffOrgSecs = (depMinTime - arrMinTime) / 1000 - 60;
            final long diffMinSecs = (depMinTime - arrMaxTime) / 1000 - 60;
            final long diffMaxSecs = (depMaxTime - arrMaxTime) / 1000 - 60;
            final long leftMinSecs = diffMinSecs - requiredSecs;
            final long leftMaxSecs = diffMaxSecs - requiredSecs;
            timeText = Long.toString((diffMaxSecs + 30) / 60);
            final String timeExplainText;
            if (arrDelaySecs != 0) {
                if (depDelaySecs != 0) {
                    timeExplainText = String.format(" (%d-%d+%d)", diffOrgSecs / 60, arrDelaySecs / 60, depDelaySecs / 60);
                } else {
                    timeExplainText = String.format(" (%d-%d)", diffOrgSecs / 60, arrDelaySecs / 60);
                }
            } else if (depDelaySecs != 0) {
                timeExplainText = String.format(" (%d+%d)", diffOrgSecs / 60, depDelaySecs / 60);
            } else {
                timeExplainText = "";
            }
            if (diffMaxSecs < 0) {
                timeIsCritical = true;
                transferText = getString(R.string.directions_trip_conneval_missed, (-diffMaxSecs) / 60, timeExplainText);
                timeText = Long.toString(-((-diffMaxSecs + 30) / 60));
                if (isWalkIcon)
                    iconResId = R.drawable.ic_directions_walk_sprint_grey600_24dp;
            } else {
                final long diffMaxMins = diffMaxSecs / 60;
                if (leftMaxSecs < 0) {
                    timeIsCritical = true;
                    if (diffMinSecs < 0) {
                        transferText = getString(R.string.directions_trip_conneval_difficult_possibly_missed, diffMaxMins, timeExplainText);
                    } else {
                        transferText = getString(R.string.directions_trip_conneval_difficult, diffMaxMins, timeExplainText);
                    }
                    if (isWalkIcon || isSamePlatform)
                        iconResId = R.drawable.ic_directions_walk_sprint_grey600_24dp;
                } else if (leftMaxSecs < 180) {
                    timeIsCritical = true;
                    if (leftMinSecs < 0) {
                        transferText = getString(R.string.directions_trip_conneval_endangered_possibly_difficult, diffMaxMins, timeExplainText);
                    } else {
                        transferText = getString(R.string.directions_trip_conneval_endangered, diffMaxMins, timeExplainText);
                    }
                    if (isWalkIcon)
                        iconResId = R.drawable.ic_directions_walk_run_grey600_24dp;
                } else if (leftMinSecs < 0) {
                    timeIsCritical = true;
                    transferText = getString(R.string.directions_trip_conneval_possibly_difficult, diffMaxMins, timeExplainText);
                } else if (leftMinSecs < 180) {
                    transferText = getString(R.string.directions_trip_conneval_possibly_endangered, diffMaxMins, timeExplainText);
                } else if (leftMinSecs != leftMaxSecs) {
                    transferText = getString(R.string.directions_trip_conneval_possibly_good, diffMaxMins, timeExplainText);
                } else {
                    transferText = getString(R.string.directions_trip_conneval_good, diffMaxMins, timeExplainText);
                }
            }
        }

        String text =
                transferText == null
                        ? legText
                        : legText == null
                        ? transferText
                        : getString(R.string.directions_trip_conneval_with_transfer, transferText, legText);
        if (text != null) {
            if (isSamePlatform)
                text = getString(R.string.directions_trip_conneval_same_platform, text);

            if (feasibilityProbability != null) {
                text = getString(R.string.directions_trip_conneval_with_transfer_probability,
                        text, (int) ((feasibilityProbability * 100f) + 0.5f));
            }
        }

        final TextView textView = elementView.findViewById(R.id.directions_trip_details_individual_entry_element_text);
        if (text == null) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            textView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT));
            textView.setTextColor(textColor);
        }

        final ImageView iconView = elementView.findViewById(R.id.directions_trip_details_individual_entry_element_icon);
        final Drawable drawable = getDrawable(iconResId);
        if (drawable != null)
            drawable.setTint(textColor);
        iconView.setImageDrawable(drawable);

        final View timeView = elementView.findViewById(R.id.directions_trip_details_individual_entry_element_time);
        if (timeText == null) {
            timeView.setVisibility(View.GONE);
        } else {
            timeView.setVisibility(View.VISIBLE);
            final int timeColor = timeIsCritical ? getColor(R.color.fg_trip_next_event_important) : textColor;
            final TextView timeTextView = elementView.findViewById(R.id.directions_trip_details_individual_entry_element_time_text);
            final TextView timeUnitView = elementView.findViewById(R.id.directions_trip_details_individual_entry_element_time_unit);
            timeTextView.setText(timeText);
            timeTextView.setTextColor(timeColor);
            timeUnitView.setTextColor(timeColor);
        }
    }

    protected void setGradientBackground(final View view, final int backgroundColor, final float probability) {
        if (probability < 0.99) {
            view.setBackground(createGradientDrawable(backgroundColor, probability));
        } else {
            view.setBackgroundColor(backgroundColor);
        }
    }

    protected Drawable createGradientDrawable(final int backgroundColor, final float probability) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        drawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        final int[] colors = new int[5];
        colors[0] = colorLegIndividualTransferCriticalBackground;
        colors[1] = ColorUtils.blendARGB(
                colorLegIndividualTransferCriticalBackground, backgroundColor,
                probability * 0.25f);
        colors[2] = ColorUtils.blendARGB(
                colorLegIndividualTransferCriticalBackground, backgroundColor,
                probability * 0.5f);
        colors[3] = ColorUtils.blendARGB(
                colorLegIndividualTransferCriticalBackground, backgroundColor,
                probability * 0.75f);
        colors[4] = backgroundColor;
        drawable.setColors(colors);
        final int level = 10000 - Math.clamp((long) (probability * 10000f), 0, 10000);
        drawable.setLevel(level);
        drawable.setUseLevel(true);
        return drawable;
    }

    protected void shownPageChanged(final int showId) {
        if (DeviceAdmin.isScreenLocked()) {
            showScreenIdWhenLocked = showId;
            showScreenIdWhenUnlocked = showId;
        } else {
            showScreenIdWhenUnlocked = showId;
            showScreenIdWhenLocked = R.id.navigation_next_event;
        }
    }

    public enum Page {
        ITINERARY(1, R.id.directions_trip_details_list_frame),
        NEXT_EVENT(2, R.id.navigation_next_event),
        EVENT_LOG(3, R.id.navigation_event_log);

        public final int pageNum;
        public final int resId;

        Page(final int pageNum, final int resId) {
            this.pageNum = pageNum;
            this.resId = resId;
        }

        public static Page getPageForNum(final int pageNum) {
            if (pageNum <= 0)
                return null;
            for (final Page page : values()) {
                if (page.pageNum == pageNum)
                    return page;
            }
            return null;
        }

        static Page getPageForResId(final int resId) {
            if (resId == 0)
                return null;
            for (final Page page : values()) {
                if (page.resId == resId)
                    return page;
            }
            return null;
        }
    }

    protected void setShowPage(final Page page) {
        if (page != null)
            setShowPage(page.resId);
    }

    protected void setShowPage(final int showId) {
        shownPageChanged(showId);
        updateGUI();
    }

    private void setViewBackgroundColor(final int viewId, final int color) {
        findViewById(viewId).setBackgroundColor(color);
    }

    private boolean globalLayoutListenerAdded;

    protected void updateNavigationInstructions() {
        final int colorHighlight = getColor(R.color.bg_trip_details_public_now);
        final int colorNormal = ViewUtils.getAttrColor(this, R.attr.bg_level0);
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

        final TextView clock = findViewById(R.id.navigation_next_event_clock);
        clock.setText(Formats.formatTime(timeZoneSelector, tripRenderer.nextEventClock.getTime(), PTDate.SYSTEM_OFFSET));

        final TextView currentAction = findViewById(R.id.navigation_next_event_current_action);
        if (tripRenderer.nextEventCurrentStringId > 0) {
            final String s = getString(tripRenderer.nextEventCurrentStringId);
            currentAction.setText(s);
            currentAction.setVisibility(View.VISIBLE);
        } else {
            currentAction.setVisibility(View.GONE);
        }

        final TextView nextAction = findViewById(R.id.navigation_next_event_next_action);
        if (tripRenderer.nextEventNextStringId > 0) {
            final String s = getString(tripRenderer.nextEventNextStringId);
            nextAction.setText(s);
            nextAction.setVisibility(View.VISIBLE);
        } else {
            nextAction.setVisibility(View.GONE);
        }

        final View timeView = findViewById(R.id.navigation_next_event_time);
        final TextView valueView = findViewById(R.id.navigation_next_event_time_value);
        final View finishedView = findViewById(R.id.navigation_next_event_finished);
        final Chronometer valueChronoView = findViewById(R.id.navigation_next_event_time_chronometer);
        valueChronoView.stop();
        final String valueStr = tripRenderer.nextEventTimeLeftValue;
        final String chronoFormat = tripRenderer.nextEventTimeLeftChronometerFormat;
        if (valueStr == null) {
            timeView.setVisibility(View.GONE);
            finishedView.setVisibility(View.VISIBLE);
        } else {
            timeView.setVisibility(View.VISIBLE);
            finishedView.setVisibility(View.GONE);
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
        final TextView unitView = findViewById(R.id.navigation_next_event_time_unit);
        unitView.setText(tripRenderer.nextEventTimeLeftUnit);
        findViewById(R.id.navigation_next_event_time_hourglass)
                .setVisibility(tripRenderer.nextEventTimeHourglassVisible ? View.VISIBLE : View.GONE);
        final TextView explainView = findViewById(R.id.navigation_next_event_time_explain);
        if (tripRenderer.nextEventTimeLeftExplainStr != null) {
            explainView.setVisibility(View.VISIBLE);
            explainView.setText(tripRenderer.nextEventTimeLeftExplainStr);
        } else {
            explainView.setVisibility(View.GONE);
        }

        final TextView distanceView = findViewById(R.id.navigation_next_event_time_distance);
        final Location nextEventSimulatedArrivalLocation = tripRenderer.nextEventSimulatedArrivalLocation;
        final Point locationCoord = nextEventSimulatedArrivalLocation == null ? null : nextEventSimulatedArrivalLocation.coord;
        final Point deviceCoord = getDeviceLocation();
        final String distance = locationCoord == null || deviceCoord == null ? null :
                Formats.formatDistance(
                        TripGeoUtils.geoDistanceInMeters(locationCoord, deviceCoord),
                        true);
        final Date nextEventSimulatedArrivalTime = tripRenderer.nextEventSimulatedArrivalTime;
        final String remainingTime = nextEventSimulatedArrivalTime == null ? null :
                Formats.formatTimeSpanMorS(
                        nextEventSimulatedArrivalTime.getTime() - System.currentTimeMillis(),
                        false);
        if (distance != null || remainingTime != null) {
            distanceView.setVisibility(View.VISIBLE);
            final StringBuilder builder = new StringBuilder();
            if (remainingTime != null) {
                builder.append(remainingTime);
                if (distance != null)
                    builder.append("  ");
            }
            if (distance != null)
                builder.append(distance);
            distanceView.setText(builder.toString());
        } else {
            distanceView.setVisibility(View.GONE);
        }

        final TextView targetView = findViewById(R.id.navigation_next_event_target);
        if (tripRenderer.nextEventTargetName != null) {
            targetView.setText(tripRenderer.nextEventTargetName);
            targetView.setVisibility(View.VISIBLE);
        } else {
            targetView.setVisibility(View.GONE);
        }

        final LinearLayout nextEventPositionsLayout = findViewById(R.id.navigation_next_event_positions);
        if (tripRenderer.nextEventPositionsAvailable) {
            nextEventPositionsLayout.setVisibility(View.VISIBLE);

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
            final View positionsFromWalkArrow = findViewById(R.id.navigation_next_event_positions_from_walk_arrow);
            final View positionsToWalkArrow = findViewById(R.id.navigation_next_event_positions_to_walk_arrow);
            final int iconId = tripRenderer.nextEventTransferIconId;
            if (tripRenderer.nextEventStopChange) {
                positionsWalkIcon.setImageDrawable(res.getDrawable(iconId != 0 ? iconId : R.drawable.ic_directions_walk_grey600_24dp));
                positionsWalkIcon.setVisibility(View.VISIBLE);
                positionsFromWalkArrow.setVisibility(View.VISIBLE);
                positionsToWalkArrow.setVisibility(View.VISIBLE);
            } else if (iconId == 0) {
                positionsWalkIcon.setVisibility(View.GONE);
                if (tripRenderer.nextEventDeparturePosName != null) {
                    positionsToWalkArrow.setVisibility(View.VISIBLE);
                    positionsFromWalkArrow.setVisibility(View.GONE);
                } else {
                    positionsToWalkArrow.setVisibility(View.GONE);
                    ViewUtils.setVisibility(positionsFromWalkArrow, tripRenderer.nextEventArrivalPosName != null);
                }
            } else {
                positionsWalkIcon.setImageDrawable(res.getDrawable(iconId));
                positionsWalkIcon.setVisibility(View.VISIBLE);
                ViewUtils.setVisibility(positionsToWalkArrow, tripRenderer.nextEventDeparturePosName != null);
                ViewUtils.setVisibility(positionsFromWalkArrow, tripRenderer.nextEventArrivalPosName != null);
            }

            final LinearLayout fromLayout = findViewById(R.id.navigation_next_event_positions_from_layout);
            final LinearLayout toLayout = findViewById(R.id.navigation_next_event_positions_to_layout);

            nextEventPositionsLayout.setOrientation(LinearLayout.VERTICAL); // try vertical first to measure both sub-layouts
            if (!globalLayoutListenerAdded) {
                globalLayoutListenerAdded = true;
                nextEventPositionsLayout.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                    final int fromWidth = fromLayout.getWidth();
                    final int toWidth = toLayout.getWidth();
                    final int parentWidth = nextEventPositionsLayout.getWidth();

                    if (parentWidth == 0)
                        return;

                    if (nextEventPositionsLayout.getOrientation() == LinearLayout.HORIZONTAL)
                        return; // already horizontal

                    if (fromWidth + toWidth < parentWidth)
                        nextEventPositionsLayout.setOrientation(LinearLayout.HORIZONTAL);
                });
            }
        } else {
            nextEventPositionsLayout.setVisibility(View.GONE);
        }

        if (tripRenderer.nextEventTransportLine == null) {
            findViewById(R.id.navigation_next_event_connection).setVisibility(View.GONE);
        } else {
            findViewById(R.id.navigation_next_event_connection).setVisibility(View.VISIBLE);

            final LineView lineView = findViewById(R.id.navigation_next_event_connection_line);
            lineView.setVisibility(View.VISIBLE);
            lineView.setLine(tripRenderer.nextEventTransportLine);

            final TextView destView = findViewById(R.id.navigation_next_event_connection_to);
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

                final TextView transferValueView = findViewById(R.id.navigation_next_event_transfer_value);
                transferValueView.setText(tripRenderer.nextEventTransferLeftTimeValue);
                transferValueView.setTextColor(getColor(tripRenderer.nextEventTransferLeftTimeCritical
                        ? R.color.fg_trip_next_event_important
                        : R.color.fg_trip_next_event_normal));

                if (tripRenderer.nextEventTransferLeftTimeFromNowValue != null) {
                    findViewById(R.id.navigation_next_event_transfer_time)
                            .setVisibility(View.VISIBLE);

                    final TextView transferTimeValueView = findViewById(R.id.navigation_next_event_transfer_time_value);
                    transferTimeValueView.setText(tripRenderer.nextEventTransferLeftTimeFromNowValue);
                } else {
                    findViewById(R.id.navigation_next_event_transfer_time)
                            .setVisibility(View.GONE);
                }

                final TextView transferExplainView = findViewById(R.id.navigation_next_event_transfer_explain);
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

        final TextView depView = findViewById(R.id.navigation_next_event_departure);
        depView.setText(tripRenderer.nextEventDepartureName);
        depView.setVisibility(tripRenderer.nextEventDepartureName != null ? View.VISIBLE : View.GONE);

        final ImageView eventCriticalIcon = findViewById(R.id.navigation_next_event_critical);
        ViewUtils.setVisibility(eventCriticalIcon,
                tripRenderer.futureTransferCritical || tripRenderer.servicesCancelled);
        eventCriticalIcon.setImageResource(
                tripRenderer.servicesCancelled
                    ? R.drawable.ic_no_transfer_black_24dp
                    : R.drawable.ic_warning_black_24px);
    }

    private Spanned getLeftTimeFormatted(final Date now, final Date endTime) {
        final long leftSeconds = (endTime.getTime() - now.getTime()) / 1000;
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
        return Html.fromHtml(leftText, Html.FROM_HTML_MODE_COMPACT);
    }

    private static class CollapseColumns {
        private boolean collapseDateColumn = true;
        private boolean collapsePositionColumn = true;
        private boolean collapseDelayColumn = true;
        private final Calendar calendar;

        public CollapseColumns(final TimeZone timeZone) {
            calendar = new GregorianCalendar(timeZone);
        }

        public boolean dateChanged(final PTDate time) {
            final int oldYear = calendar.get(Calendar.YEAR);
            final int oldDayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
            calendar.setTime(time);
            return calendar.get(Calendar.YEAR) != oldYear || calendar.get(Calendar.DAY_OF_YEAR) != oldDayOfYear;
        }
    }

    protected TableRow makeStopRowArrival(
            final TripRenderer.LegContainer legC,
            final int stopIndex,
            final Stop stop,
            final Stop simulatedStop,
            final Date now) {
        // do nothing here, may be overridden by sub-classes
        return null;
    }

    protected TableRow makeStopRowDeparture(
            final TripRenderer.LegContainer legC,
            final int stopIndex,
            final Stop stop,
            final Stop simulatedStop,
            final Date now) {
        // do nothing here, may be overridden by sub-classes
        return null;
    }

    private void addStopRow(
            final TableLayout tableLayout,
            final TableRow tableRow,
            final int backgroundColor,
            final PearlView.Type pearlType,
            final Style style,
            final Paint.FontMetrics fontMetrics) {
        // pearl
        final PearlView pearlView = tableRow.findViewById(R.id.directions_trip_details_public_entry_stop_pearl);
        pearlView.setType(pearlType);
        pearlView.setStyle(style);
        pearlView.setFontMetrics(fontMetrics);

        tableLayout.addView(tableRow);

        if (backgroundColor != Color.TRANSPARENT)
            tableRow.setBackgroundColor(backgroundColor);
    }

    private void addStopRow(
            final TableLayout tableLayout,
            final PearlView.Type aPearlType,
            final boolean isLongStay,
            final int stopIndex,
            final Stop stop,
            final String previousPlace,
            final TripRenderer.LegContainer legC,
            final boolean highlightTime,
            final boolean highlightLocation,
            final boolean preferPlanTime,
            final Date now,
            final CollapseColumns collapseColumns,
            final Stop simulatedStop) {
        final TableRow row = (TableRow) inflater.inflate(R.layout.directions_trip_details_public_entry_stop, null);

        final Trip.Public leg = legC.publicLeg;
        final int nearestStopIndex = legC.nearestStopIndex;

        final PearlView.Type pearlType;
        if (aPearlType == PearlView.Type.INTERMEDIATE_DEPARTURE && stop.departureCancelled && !stop.arrivalCancelled)
            pearlType = PearlView.Type.INTERMEDIATE_ARRIVAL;
        else if (aPearlType == PearlView.Type.INTERMEDIATE_ARRIVAL && stop.arrivalCancelled && !stop.departureCancelled)
            pearlType = PearlView.Type.INTERMEDIATE_DEPARTURE;
        else
            pearlType = aPearlType;

        final boolean isTimePredicted;
        final PTDate providedTime;
        final PTDate simulatedTime;
        final boolean isTimeDeparture;
        final Long providedDelay;
        final Long simulatedDelay;
        final boolean isCancelled;
        final boolean isPositionPredicted;
        final Position position;
        final boolean positionChanged;
        final Location location = stop.location;
        final Style style = leg.line.style;

        TableRow arrivalRow = null;
        TableRow departureRow = null;
        int backgroundColor = Color.TRANSPARENT;

        if (simulatedStop != null && nearestStopIndex >= 0) {
            if (stopIndex == nearestStopIndex) {
                backgroundColor = colorLegPublicNowBackground;
            } else if (stopIndex > nearestStopIndex) {
                backgroundColor = colorLegPublicFutureBackground;
            } else {
                backgroundColor = colorLegPublicPastBackground;
            }
        }

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
            isTimePredicted = !preferPlanTime && stop.isDepartureTimePredicted();
            providedTime = stop.getDepartureTime(preferPlanTime);
            providedDelay = stop.getDepartureDelay();
            if (simulatedStop != null) {
                simulatedTime = simulatedStop.getDepartureTime();
                simulatedDelay = simulatedStop.getDepartureDelay();
            } else {
                simulatedTime = null;
                simulatedDelay = null;
            }
            isCancelled = stop.departureCancelled;

            isPositionPredicted = stop.isDeparturePositionPredicted();
            position = stop.getDeparturePosition();
            positionChanged = position != null && !position.equals(stop.plannedDeparturePosition);

            if (!isLongStay && pearlType == PearlView.Type.INTERMEDIATE_DEPARTURE)
                arrivalRow = makeStopRowArrival(legC, stopIndex, stop, simulatedStop, now);

            departureRow = makeStopRowDeparture(legC, stopIndex, stop, simulatedStop, now);
        } else if ((pearlType == PearlView.Type.ARRIVAL
                        || pearlType == PearlView.Type.INTERMEDIATE_ARRIVAL
                        || pearlType == PearlView.Type.ARRIVAL_FOR_INTERMEDIATE_DEPARTURE
                        || pearlType == PearlView.Type.PASSING)
                    && stop.plannedArrivalTime != null) {
            isTimeDeparture = false;
            isTimePredicted = !preferPlanTime && stop.isArrivalTimePredicted();
            providedTime = stop.getArrivalTime(preferPlanTime);
            providedDelay = stop.getArrivalDelay();
            if (simulatedStop != null) {
                simulatedTime = simulatedStop.getArrivalTime();
                simulatedDelay = simulatedStop.getArrivalDelay();
            } else {
                simulatedTime = null;
                simulatedDelay = null;
            }
            isCancelled = stop.arrivalCancelled;

            isPositionPredicted = stop.isArrivalPositionPredicted();
            position = stop.getArrivalPosition();
            positionChanged = position != null && !position.equals(stop.plannedArrivalPosition);

            arrivalRow = makeStopRowArrival(legC, stopIndex, stop, simulatedStop, now);

            if (!isLongStay && pearlType == PearlView.Type.INTERMEDIATE_ARRIVAL)
                departureRow = makeStopRowDeparture(legC, stopIndex, stop, simulatedStop, now);
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
        ViewUtils.setCancelledStrikeThru(stopNameView, isCancelled);
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
        stopNameView.setOnClickListener(null);
        stopNameView.setOnLongClickListener(null);
        if (location.hasId()) {
            boolean stopIsLegDeparture = false;
            boolean stopIsLegArrival = false;
            JourneyRef feederJourneyRef = leg.journeyRef;
            JourneyRef connectionJourneyRef = null;
            if (stop.location.id.equals(leg.departureStop.location.id)
                    && stop.plannedDepartureTime != null
                    && stop.plannedDepartureTime.equals(leg.departureStop.plannedDepartureTime)) {
                // departure stop of a journey
                stopIsLegDeparture = true;
                feederJourneyRef = null;
                connectionJourneyRef = leg.journeyRef;
            } else if (stop.location.id.equals(leg.arrivalStop.location.id)
                    && stop.plannedArrivalTime != null
                    && stop.plannedArrivalTime.equals(leg.arrivalStop.plannedArrivalTime)) {
                // arrival stop of a journey
                stopIsLegArrival = true;
            }
            if (feederJourneyRef == null) {
                // find previous journey as feeder
                for (final TripRenderer.LegContainer iLegC : tripRenderer.legs) {
                    if (iLegC.publicLeg != null) {
                        if (iLegC.publicLeg == leg)
                            break;
                        else
                            feederJourneyRef = iLegC.publicLeg.journeyRef;
                    }
                }
            }
            if (connectionJourneyRef == null) {
                // find next journey as connection
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
            if (pearlType != PearlView.Type.PASSING) {
                final StopClickListener clickListener = newStopClickListener(
                        legC, stop, stopIsLegDeparture, stopIsLegArrival,
                        leg.journeyRef, feederJourneyRef, connectionJourneyRef);
                stopNameView.setOnClickListener(v -> clickListener.onClick(v, false));
                stopNameView.setOnLongClickListener(v -> clickListener.onClick(v, true));
            }
        }

        // time
        final ViewGroup stopDateFrameView = row.findViewById(R.id.directions_trip_details_public_entry_stop_date);
        final ViewGroup stopTimeFrameView = row.findViewById(R.id.directions_trip_details_public_entry_stop_time);
        final ViewGroup stopDelayFrameView = row.findViewById(R.id.directions_trip_details_public_entry_stop_delay);
        final TextView stopDateProvidedView = row.findViewById(R.id.directions_trip_details_public_entry_stop_date_provided);
        final TextView stopTimeProvidedView = row.findViewById(R.id.directions_trip_details_public_entry_stop_time_provided);
        final TextView stopDelayProvidedView = row.findViewById(R.id.directions_trip_details_public_entry_stop_delay_provided);
        final TextView stopDateSimulatedView = row.findViewById(R.id.directions_trip_details_public_entry_stop_date_simulated);
        final TextView stopTimeSimulatedView = row.findViewById(R.id.directions_trip_details_public_entry_stop_time_simulated);
        final TextView stopDelaySimulatedView = row.findViewById(R.id.directions_trip_details_public_entry_stop_delay_simulated);

        final boolean isShowSimulatedLine;

        if (providedTime != null) {
            int stopTimeColor = highlightTime ? colorHighlighted
                    : isCancelled ? colorCancelled
                    : colorSignificant;
            if (simulatedTime == null) {
                isShowSimulatedLine = false;
            } else if (isShowCompactTimes) {
                if (Math.abs(simulatedTime.getTime() - providedTime.getTime()) <= 175000) {
                    isShowSimulatedLine = false;
                    stopTimeColor = colorSimulated;
                } else {
                    isShowSimulatedLine = true;
                }
            } else {
                isShowSimulatedLine = true;
            }

            final PTDate displayTime = timeZoneSelector.getDisplay(providedTime);
            if (collapseColumns.dateChanged(displayTime)) {
                stopDateProvidedView.setText(Formats.formatDate(timeZoneSelector,
                        now.getTime(), displayTime, true, res.getString(R.string.time_today_abbrev)));
                collapseColumns.collapseDateColumn = false;
            }
            final String timeText = Formats.formatTime(timeZoneSelector, displayTime);
            stopTimeProvidedView.setText(isTimeDeparture ? timeText + "°" : timeText);
            ViewUtils.setCancelledStrikeThru(stopDateProvidedView, isCancelled);
            ViewUtils.setCancelledStrikeThru(stopTimeProvidedView, isCancelled);
            stopDateProvidedView.setTextColor(stopTimeColor);
            stopTimeProvidedView.setTextColor(stopTimeColor);
            final boolean stopTimeBold = highlightTime || isCancelled || (renderConfig.isJourney ? isEntryOrExit
                    : (pearlType == PearlView.Type.DEPARTURE || pearlType == PearlView.Type.ARRIVAL));
            stopDateProvidedView.setTypeface(null, (highlightTime ? Typeface.BOLD : 0) + (isTimePredicted ? Typeface.ITALIC : 0));
            stopTimeProvidedView.setTypeface(null, (stopTimeBold ? Typeface.BOLD : 0) + (isTimePredicted ? Typeface.ITALIC : 0));
        } else {
            stopDateProvidedView.setText(null);
            stopTimeProvidedView.setText(null);
            isShowSimulatedLine = simulatedTime != null;
        }

        if (isShowSimulatedLine && simulatedTime != null) {
            final PTDate displayTime = timeZoneSelector.getDisplay(simulatedTime);
            if (collapseColumns.dateChanged(displayTime)) {
                stopDateSimulatedView.setText(Formats.formatDate(timeZoneSelector,
                        now.getTime(), displayTime, true, res.getString(R.string.time_today_abbrev)));
                collapseColumns.collapseDateColumn = false;
            }
            final String timeText = Formats.formatTime(timeZoneSelector, displayTime);
            stopTimeSimulatedView.setText(isTimeDeparture ? timeText + "°" : timeText);
            // ViewUtils.setCancelledStrikeThru(stopTimeSimulatedView, isCancelled);
            final boolean stopTimeBold = highlightTime || (renderConfig.isJourney ? isEntryOrExit
                    : (pearlType == PearlView.Type.DEPARTURE || pearlType == PearlView.Type.ARRIVAL));
            stopDateSimulatedView.setTypeface(null, (highlightTime ? Typeface.BOLD : 0) + (isTimePredicted ? Typeface.ITALIC : 0));
            stopTimeSimulatedView.setTypeface(null, (stopTimeBold ? Typeface.BOLD : 0) + (isTimePredicted ? Typeface.ITALIC : 0));

            final int stopTimeColor = getStopTimeColor(simulatedDelay);
            stopDateSimulatedView.setTextColor(stopTimeColor);
            stopTimeSimulatedView.setTextColor(stopTimeColor);
            stopDelaySimulatedView.setTextColor(stopTimeColor);
        } else {
            stopDateSimulatedView.setVisibility(View.GONE);
            stopTimeSimulatedView.setVisibility(View.GONE);
        }

        // delay
        if (providedDelay != null) {
            final long delayMins = providedDelay / DateUtils.MINUTE_IN_MILLIS;
            if (delayMins != 0) {
                collapseColumns.collapseDelayColumn = false;
                stopDelayProvidedView.setText(String.format("(%+d)", delayMins));
                stopDelayProvidedView.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            }
        }

        if (isShowSimulatedLine && simulatedDelay != null) {
            final long delayMins = simulatedDelay / DateUtils.MINUTE_IN_MILLIS;
            if (delayMins != 0) {
                collapseColumns.collapseDelayColumn = false;
                stopDelaySimulatedView.setText(String.format("(%+d)", delayMins));
                stopDelaySimulatedView.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            }
        } else {
            stopDelaySimulatedView.setVisibility(View.GONE);
        }

        if (providedTime != null || simulatedTime != null) {
            final View.OnClickListener onClickListener = v -> {
                final Spanned tooltip = getTooltipForStop(stop, simulatedStop, now.getTime());
                new AlertDialog.Builder(this)
                        .setMessage(tooltip)
                        .create().show();
            };
            stopDateFrameView.setOnClickListener(onClickListener);
            stopTimeFrameView.setOnClickListener(onClickListener);
            stopDelayFrameView.setOnClickListener(onClickListener);
        }

        // position
        final TextView stopPositionView = row
                .findViewById(R.id.directions_trip_details_public_entry_stop_position);
        if (position != null
                && !isCancelled
                && pearlType != PearlView.Type.DEPARTURE_FOR_INTERMEDIATE_ARRIVAL
                && pearlType != PearlView.Type.ARRIVAL_FOR_INTERMEDIATE_DEPARTURE) {
            collapseColumns.collapsePositionColumn = false;
            final SpannableStringBuilder positionStr = new SpannableStringBuilder(
                    Formats.makeBreakablePositionName(position.name)
                            .replace('\u200B', '\n'));
            if (position.section != null) {
                positionStr.append('\n');
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

        // remaining time and distance
        final TextView remainingView = row.findViewById(R.id.directions_trip_details_public_entry_stop_remaining);
        if (isShowSimulatedLine && simulatedTime != null) {
            final long remainingTime = simulatedTime.getTime() - now.getTime();
            final Point locationCoord = stop.location.coord;
            final Point deviceCoord = getDeviceLocation();
            final StringBuilder builder = new StringBuilder();
            builder.append(Formats.formatTimeSpanMorS(remainingTime, false));
            if (locationCoord != null && deviceCoord != null) {
                builder.append("  ");
                builder.append(Formats.formatDistance(
                        TripGeoUtils.geoDistanceInMeters(locationCoord, deviceCoord), true));
            }
            remainingView.setText(builder.toString());
        } else {
            remainingView.setVisibility(View.GONE);
        }

        final Paint.FontMetrics fontMetrics = stopNameView.getPaint().getFontMetrics();

        if (arrivalRow != null)
            addStopRow(tableLayout, arrivalRow, backgroundColor, PearlView.Type.PASSING, style, fontMetrics);

        addStopRow(tableLayout, row, backgroundColor, pearlType, style, fontMetrics);

        if (departureRow != null)
            addStopRow(tableLayout, departureRow, backgroundColor, PearlView.Type.PASSING, style, fontMetrics);

        if (isShowSimulatedLine && pearlType != PearlView.Type.ARRIVAL) {
            // add a divider row
            final TableRow divider = (TableRow) inflater.inflate(R.layout.directions_trip_details_public_entry_stop_divider, null);
            addStopRow(tableLayout, divider, backgroundColor, PearlView.Type.PASSING, style, fontMetrics);
        }
    }

    protected int getStopTimeColor(final Long simulatedDelay) {
        return colorSimulated;
    }

    private Spanned getTooltipForStop(final Stop realStop, final Stop simulatedStop, final long now) {
        final String locationName = realStop.location.uniqueShortName();
        final String arrivalEvent = getTooltipForStopEvent(realStop,
                getString(R.string.directions_trip_details_public_entry_tooltip_label_arrival),
                realStop.getArrivalTime(true),
                realStop.predictedArrivalTime,
                realStop.getArrivalDelay(),
                simulatedStop == null ? null : simulatedStop.predictedArrivalTime,
                simulatedStop == null ? null : simulatedStop.getArrivalDelay(),
                now);
        final String departureEvent = getTooltipForStopEvent(realStop,
                getString(R.string.directions_trip_details_public_entry_tooltip_label_departure),
                realStop.getDepartureTime(true),
                realStop.predictedDepartureTime,
                realStop.getDepartureDelay(),
                simulatedStop == null ? null : simulatedStop.predictedDepartureTime,
                simulatedStop == null ? null : simulatedStop.getDepartureDelay(),
                now);

        final String eventText;
        if (arrivalEvent != null && departureEvent != null) {
            eventText = getString(
                    R.string.directions_trip_details_public_entry_tooltip_format_two_events,
                    locationName, arrivalEvent, departureEvent);
        } else {
            final String event = arrivalEvent != null ? arrivalEvent : departureEvent;
            if (event == null)
                return null;
            eventText = getString(
                    R.string.directions_trip_details_public_entry_tooltip_format_one_event,
                    locationName, event);
        }

        return Html.fromHtml(eventText, Html.FROM_HTML_MODE_COMPACT);
    }

    private String getTooltipForStopEvent(
            final Stop stop, final String eventLabel,
            final PTDate plannedTime,
            final PTDate predictedTime, final Long delay,
            final PTDate simulatedTime, final Long simulatedDelay,
            final long now) {
        if (plannedTime == null && predictedTime == null)
            return null;

        final String noDataString = res.getString(R.string.directions_trip_details_public_entry_tooltip_no_data);
        final String todayString = res.getString(R.string.time_today);
        String plannedText = getTooltipForPTDate(plannedTime, todayString, now);
        if (plannedText == null)
            plannedText = noDataString;

        String predictedText = getTooltipForPTDate(predictedTime, todayString, now);
        final String delayText;
        if (predictedText == null) {
            predictedText = noDataString;
            delayText = noDataString;
        } else {
            final long delayMins = delay == null ? 0 : (delay / DateUtils.MINUTE_IN_MILLIS);
            delayText = getString(R.string.directions_trip_details_public_entry_tooltip_delay_format, delayMins);
        }

        String simulatedText = simulatedTime == null ? null : getTooltipForPTDate(simulatedTime, todayString, now);
        final String simulatedDelayText;
        if (simulatedText == null) {
            simulatedText = noDataString;
            simulatedDelayText = noDataString;
        } else {
            final long delayMins = simulatedDelay == null ? 0 : (simulatedDelay / DateUtils.MINUTE_IN_MILLIS);
            simulatedDelayText = getString(R.string.directions_trip_details_public_entry_tooltip_delay_format, delayMins);
        }

        return getString(R.string.directions_trip_details_public_entry_tooltip_event_format,
                eventLabel, plannedText,
                predictedText, delayText,
                simulatedText, simulatedDelayText);
    }

    private String getTooltipForPTDate(final PTDate timestamp, final String todayString, final long now) {
        if (timestamp == null)
            return null;
        final PTDate displayTime = timeZoneSelector.getDisplay(timestamp);
        final String dateText = Formats.formatDate(timeZoneSelector, now, displayTime, true, todayString);
        final String timeText = Formats.formatTime(timeZoneSelector, displayTime);
        return dateText + " " + timeText;
    }

    private View collapsedIntermediateStopsRow(final Long duration, final int numIntermediateStops, final Style style) {
        final View row = inflater.inflate(R.layout.directions_trip_details_public_entry_collapsed, null);

        // message
        final String durationString = duration == null ? null :
                getString(R.string.directions_trip_details_public_entry_duration, Formats.formatTimeSpanHM(duration, false));
        final String quantityString = numIntermediateStops == 0 ? null : res.getQuantityString(
                R.plurals.directions_trip_details_public_entry_collapsed_intermediate_stops,
                numIntermediateStops, numIntermediateStops);
        final String collapsedText;
        if (durationString != null) {
            if (quantityString != null) {
                collapsedText = durationString + "\n" + quantityString;
            } else {
                collapsedText = durationString;
            }
        } else if (quantityString != null) {
            collapsedText = quantityString;
        } else {
            collapsedText = "-";
        }

        final TextView stopNameView = row
                .findViewById(R.id.directions_trip_details_public_entry_collapsed_message);
        stopNameView.setText(collapsedText);
        stopNameView.setTextColor(colorInsignificant);

        // pearl
        final PearlView pearlView = row
                .findViewById(R.id.directions_trip_details_public_entry_collapsed_pearl);
        pearlView.setType(PearlView.Type.PASSING);
        pearlView.setStyle(style);
        pearlView.setFontMetrics(stopNameView.getPaint().getFontMetrics());

        return row;
    }

    protected StopClickListener newStopClickListener(
            final TripRenderer.LegContainer legC,
            final Stop stop,
            final boolean stopIsLegDeparture,
            final boolean stopIsLegArrival,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef) {
        return new StopClickListener(
                legC,
                stop,
                stopIsLegDeparture,
                stopIsLegArrival,
                currentJourneyRef,
                feederJourneyRef,
                connectionJourneyRef);
    }

    protected class StopClickListener {
        protected final TripRenderer.LegContainer legC;
        protected final Stop stop;
        protected final boolean stopIsLegDeparture, stopIsLegArrival;
        protected final JourneyRef currentJourneyRef;
        protected final JourneyRef feederJourneyRef;
        protected final JourneyRef connectionJourneyRef;

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

        public boolean onClick(final View v, final boolean isLongClick) {
            final boolean showNavigateTo;
            if (renderConfig.isJourney) {
                final Trip.Public journeyLeg = (Trip.Public) tripRenderer.trip.legs.get(0);
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
            final StationContextMenu contextMenu = new StationContextMenu(
                    TripDetailsActivity.this, v, network, stop.location,
                    null, false, false, false,true,
                    true, true,
                    showTravelAlarm && stopIsLegDeparture, showTravelAlarm && stopIsLegArrival,
                    true, true,
                    renderConfig.isNavigation && !isLastPublicStop,
                    showNavigateTo, false);
            contextMenu.setOnMenuItemClickListener(item -> {
                final int menuItemId = item.getItemId();
                PTDate time = stop.getArrivalTime();
                if (time == null)
                    time = stop.getDepartureTime(true);
                if (menuItemId == R.id.station_context_show_departures) {
                    StationDetailsActivity.start(TripDetailsActivity.this, network, stop.location,
                            time,
                            null,
                            shallShowChildActivitiesInNewTask());
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
                    final String infoUrl = NetworkProviderFactory.provider(network).getLocationInfoUrl(stop.location);
                    if (infoUrl != null) {
                        @SuppressLint("UnsafeImplicitIntentLaunch")
                        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(infoUrl));
                        startActivity(intent);
                    }
                    return true;
                } else if (menuItemId == R.id.station_context_set_departure_travel_alarm) {
                    return onStationContextMenuItemClicked(menuItemId, legC, stop);
                } else if (menuItemId == R.id.station_context_set_arrival_travel_alarm) {
                    return onStationContextMenuItemClicked(menuItemId, legC, stop);
                } else if (menuItemId == R.id.station_map_context_maps_internal) {
                    setMapVisible(true);
                    getMapView().zoomToStations(List.of(stop.location), 0);
                    return true;
                } else {
                    return false;
                }
            });
            contextMenu.show();
            return true;
        }
    }

    protected boolean isShowTravelAlarm() {
        return false;
    }

    protected boolean shallShowChildActivitiesInNewTask() {
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

    private Intent getSendTripToCalendarIntent(final Trip trip, final boolean withLink) {
        Integer calendarId = null;
//        if (CalendarHelper.hasCalendarPermissions(this)) {
//            calendarId = CalendarHelper.findCalendarForName(this, "Reisen");
//        } else if (CalendarHelper.requestCalendarPermissions(this)) {
//            return null;
//        }

        final Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setData(CalendarContract.Events.CONTENT_URI);
        if (calendarId != null)
            intent.putExtra(CalendarContract.Events.CALENDAR_ID, calendarId.intValue());
        intent.putExtra(CalendarContract.Events.TITLE, getString(R.string.directions_trip_details_text_long_title,
                trip.from.uniqueShortName(), trip.to.uniqueShortName()));
        intent.putExtra(CalendarContract.Events.DESCRIPTION, tripToLongText(trip, withLink));
        intent.putExtra(CalendarContract.Events.EVENT_LOCATION, trip.from.uniqueShortName());
        final PTDate firstDepartureTime = trip.getFirstDepartureTime();
        if (firstDepartureTime != null)
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, firstDepartureTime.getTime());
        final PTDate lastArrivalTime = trip.getLastArrivalTime();
        if (lastArrivalTime != null)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, lastArrivalTime.getTime());
        intent.putExtra(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT);
        intent.putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);
        return intent;
    }

    private Intent showInExternalMapsApp(final GeoFileProducer producer, final int titleId) {
        String extension = "?";
        try {
            producer.setApplication(application);
            extension = producer.getFilenameExtension();
            final File geoFile = new File(Application.getInstance().getShareDir(),
                    "shareroute." + extension);
            producer.writeTrip(tripRenderer.trip, geoFile);
            final Intent kmlIntent = GoogleMapsUtils.getOpenGeoFileIntent(geoFile);
            return Intent.createChooser(kmlIntent, getString(titleId));
        } catch (final Exception e) {
            log.error("cannot create shared {} file", extension, e);
            return null;
        }
    }

    private String tripToShortText(final Trip trip) {
        final Trip.Public firstPublicLeg = trip.getFirstPublicLeg();
        final Trip.Public lastPublicLeg = trip.getLastPublicLeg();
        if (firstPublicLeg == null || lastPublicLeg == null)
            return null;

        final PTDate departureTime = timeZoneSelector.getDisplay(firstPublicLeg.getDepartureTime(true));
        final String departureDateStr = Formats.formatDate(timeZoneSelector, departureTime);
        final String departureTimeStr = Formats.formatTime(timeZoneSelector, departureTime);
        final String departureLineStr = firstPublicLeg.line.label;
        final String departureNameStr = firstPublicLeg.departure.uniqueShortName();

        final PTDate arrivalTime = timeZoneSelector.getDisplay(lastPublicLeg.getArrivalTime(true));
        final String arrivalDateStr = Formats.formatDate(timeZoneSelector, arrivalTime);
        final String arrivalTimeStr = Formats.formatTime(timeZoneSelector, arrivalTime);
        final String arrivalLineStr = lastPublicLeg.line.label;
        final String arrivalNameStr = lastPublicLeg.arrival.uniqueShortName();

        return getString(R.string.directions_trip_details_text_short, departureDateStr, departureTimeStr,
                departureLineStr, departureNameStr, arrivalDateStr, arrivalTimeStr, arrivalLineStr, arrivalNameStr);
    }

    private String tripToLongText(final Trip trip, final boolean withLink) {
        final StringBuilder description = new StringBuilder();

        for (final TripRenderer.LegContainer legC : tripRenderer.legs) {
            final Trip.Leg leg = legC.publicLeg != null ? legC.publicLeg : legC.individualLeg;
            String legStr = null;

            if (leg instanceof Trip.Public) {
                final Trip.Public publicLeg = (Trip.Public) leg;

                final String lineStr = publicLeg.line.label;
                final Destination destination = publicLeg.destination;
                final String lineDestinationStr = destination == null ? "" : destination.uniqueShortName();

                final PTDate departureTime = timeZoneSelector.getDisplay(publicLeg.getDepartureTime(true));
                final String departureDateStr = Formats.formatDate(timeZoneSelector, departureTime);
                final String departureTimeStr = Formats.formatTime(timeZoneSelector, departureTime);
                final String departureNameStr = publicLeg.departure.uniqueShortName();
                final String departurePositionStr = publicLeg.getDeparturePosition() != null
                        ? publicLeg.getDeparturePosition().toString() : "";

                final PTDate arrivalTime = timeZoneSelector.getDisplay(publicLeg.getArrivalTime(true));
                final String arrivalDateStr = Formats.formatDate(timeZoneSelector, arrivalTime);
                final String arrivalTimeStr = Formats.formatTime(timeZoneSelector, arrivalTime);
                final String arrivalNameStr = publicLeg.arrival.uniqueShortName();
                final String arrivalPositionStr = publicLeg.getArrivalPosition() != null
                        ? publicLeg.getArrivalPosition().toString() : "";

                legStr = getString(R.string.directions_trip_details_text_long_public,
                        lineStr, lineDestinationStr,
                        departureDateStr, departureTimeStr, departurePositionStr, departureNameStr,
                        arrivalDateStr, arrivalTimeStr, arrivalPositionStr, arrivalNameStr);
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
                else if (individualLeg.type == Trip.Individual.Type.TRANSFER) {
                    if (((Trip.Individual) leg).distance <= application.prefsGetMaxWalkDistance())
                        legStrResId = R.string.directions_trip_details_text_long_walk;
                    else
                        legStrResId = R.string.directions_trip_details_text_long_transfer;
                } else
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

    protected void startNavigationForJourneyToExit(final Stop exitStop) {
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
        for (final Stop stop: journeyLeg.intermediateStops) {
            final String stopId = stop.location.id;
            if (stopId.equals(entryId))
                inIntermediates = true;
            else if (stopId.equals(exitId))
                break;
            else if (inIntermediates)
                intermediateStops.add(stop);
        }
        final Trip.Public leg = new Trip.Public(
                journeyLeg.line, new Destination(exitLocation), entryStop, exitStop,
                intermediateStops,
                journeyLeg.message,
                journeyLeg.journeyRef, journeyLeg.loadedAt);
        leg.setPath(journeyLeg.getPath());
        final Trip journeyTrip = TripUtils.createTripFromJourney(
                tripRenderer.trip.loadedAt,
                leg,
                entryLocation,
                exitLocation);
        final RenderConfig navigationRenderConfig = new RenderConfig();
        navigationRenderConfig.isJourney = true;
        navigationRenderConfig.isOperation = renderConfig.isOperation;
        startNavigation(journeyTrip, navigationRenderConfig);
    }

    protected void startNavigation(final Trip trip, final RenderConfig renderConfig) {
        if (TripNavigatorActivity.startNavigation(this, network, trip, renderConfig, isTaskRoot())) {
            setResult(RESULT_OK, new Intent());
            finish();
        }
    }

    public void onTripUpdated(final Trip updatedTrip) {
        if (updatedTrip == null)
            return;
        setupFromTrip(updatedTrip);
        final List<Trip.Leg> updatedPublicLegs = new ArrayList<>();
        for (final Trip.Leg leg : updatedTrip.legs) {
            if (leg instanceof Trip.Public)
                updatedPublicLegs.add(leg);
        }
        int iUpdatedLeg = 0;
        for (final TripRenderer.LegContainer legC: tripRenderer.legs) {
            if (legC.initialLeg != null) {
                final Trip.Public updatedLeg = (Trip.Public) updatedPublicLegs.get(iUpdatedLeg);
                legC.setCurrentLegState(updatedLeg);
                iUpdatedLeg += 1;
            }
        }
        updateGUI();
    }

    protected boolean onFindAlternativeConnections(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final QueryTripRunnable.TripRequestData queryTripsRequestData) {
        // override if implemented
        return false;
    }

    protected TripsOverviewActivity.RenderConfig getOverviewConfig(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final TimeSpec time) {
        final TripsOverviewActivity.RenderConfig overviewConfig = new TripsOverviewActivity.RenderConfig();
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

    protected NetworkId getRequestedNetwork() {
        final TripRef tripRef = tripRenderer.trip.tripRef;
        return tripRef == null ? this.network : tripRef.network;
    }

    protected boolean onDirectionsFrom(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final QueryTripRunnable.TripRequestData queryTripsRequestData,
            final TripsOverviewActivity.RenderConfig overviewConfig) {
        final PTDate arrivalTime = stop.getArrivalTime();
        final TimeSpec time = new TimeSpec.Absolute(DepArr.DEPART,
                arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
        DirectionsActivity.start(TripDetailsActivity.this,
                getRequestedNetwork(),
                stop.location,
                renderConfig.isJourney
                        ? ((Trip.Public) tripRenderer.trip.legs.get(0)).exitLocation
                        : tripRenderer.trip.to, // DirectionsActivity.EMPTY_LOCATION,
                renderConfig.isJourney ? DirectionsActivity.EMPTY_LOCATION : null,
                time,
                getOverviewConfig(stop, isLegDeparture, currentJourneyRef, feederJourneyRef, connectionJourneyRef, time),
                false,
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return true;
    }

    private boolean onDirectionsTo(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final QueryTripRunnable.TripRequestData queryTripsRequestData,
            final TripsOverviewActivity.RenderConfig overviewConfig) {
        final Location entry;
        final TimeSpec time;
        if (renderConfig.isJourney) {
            final Trip.Public journeyLeg = (Trip.Public) tripRenderer.trip.legs.get(0);
            entry = journeyLeg.entryLocation;
            final Stop entryStop = journeyLeg.findStopByLocation(entry);
            final PTDate arrivalTime = entryStop.getArrivalTime();
            time = new TimeSpec.Absolute(DepArr.DEPART,
                    arrivalTime != null ? arrivalTime.getTime() : entryStop.getDepartureTime().getTime());
        } else {
            entry = tripRenderer.trip.from;
            final PTDate arrivalTime = stop.getArrivalTime();
            time = new TimeSpec.Absolute(DepArr.ARRIVE,
                    arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
        }
        DirectionsActivity.start(this,
                getRequestedNetwork(),
                entry,
                stop.location,
                null,
                time,
                null,
                false,
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return true;
    }

    private boolean onDirectionsVia(
            final Stop stop,
            final boolean isLegDeparture,
            final JourneyRef currentJourneyRef,
            final JourneyRef feederJourneyRef,
            final JourneyRef connectionJourneyRef,
            final QueryTripRunnable.TripRequestData queryTripsRequestData,
            final TripsOverviewActivity.RenderConfig overviewConfig) {
        final Location exit;
        final TimeSpec time;
        if (renderConfig.isJourney) {
            final Trip.Public journeyLeg = (Trip.Public) tripRenderer.trip.legs.get(0);
            exit = journeyLeg.exitLocation;
            final Stop exitStop = journeyLeg.findStopByLocation(exit);
            final PTDate departureTime = exitStop.getDepartureTime();
            time = new TimeSpec.Absolute(DepArr.DEPART,
                    departureTime != null ? departureTime.getTime() : exitStop.getArrivalTime().getTime());
        } else {
            exit = tripRenderer.trip.to;
            final PTDate departureTime = stop.getDepartureTime();
            time = new TimeSpec.Absolute(DepArr.DEPART,
                    departureTime != null ? departureTime.getTime() : stop.getArrivalTime().getTime());
        }
        DirectionsActivity.start(this,
                getRequestedNetwork(),
                null,
                exit,
                stop.location,
                time,
                null,
                false,
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return true;
    }
}
