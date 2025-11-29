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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.Html;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.ViewAnimator;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.common.base.Joiner;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.directions.QueryJourneyRunnable;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.HtmlUtils;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;
import de.schildbach.pte.dto.PTDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class StationDetailsActivity extends OeffiActivity implements StationsAware {
    public static final String INTENT_EXTRA_NETWORK = StationDetailsActivity.class.getName() + ".network";
    public static final String INTENT_EXTRA_STATION = StationDetailsActivity.class.getName() + ".station";
    public static final String INTENT_EXTRA_PRESETTIME = StationDetailsActivity.class.getName() + ".presettime";
    public static final String INTENT_EXTRA_DEPARTURES = StationDetailsActivity.class.getName() + ".departures";
    public static final String INTENT_EXTRA_JOURNEYREF = StationDetailsActivity.class.getName() + ".journeyref";

    public static void start(
            final Context context, final NetworkId networkId,
            final Location station, final Date presetTime,
            final List<Departure> departures,
            final boolean newTask) {
        if (station.type != LocationType.STATION) {
            StationsActivity.start(context, networkId, station, presetTime);
            return;
        }

        final Intent intent = StationDetailsActivity.fillIntent(
                new Intent(context, StationDetailsActivity.class),
                networkId, station, presetTime);
        if (departures != null)
            intent.putExtra(StationDetailsActivity.INTENT_EXTRA_DEPARTURES, (Serializable) departures);
        if (newTask)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void start(
            final Context context, final NetworkId networkId,
            final Location station, final Date presetTime,
            final List<Departure> departures) {
        start(context, networkId, station, presetTime, departures, false);
    }

    public static Intent fillIntent(
            Intent intent,
            final NetworkId networkId,
            final Location station,
            final Date presetTime) {
        checkArgument(station.type == LocationType.STATION);
        checkNotNull(networkId);
        intent.putExtra(StationDetailsActivity.INTENT_EXTRA_NETWORK, networkId.name());
        intent.putExtra(StationDetailsActivity.INTENT_EXTRA_STATION, Objects.serializeToString(station));
        if (presetTime != null)
            intent.putExtra(StationDetailsActivity.INTENT_EXTRA_PRESETTIME, presetTime);
        return intent;
    }

    public static final int MAX_DEPARTURES = 200;

    private final List<Station> stations = new ArrayList<>();

    private NetworkId selectedNetwork;
    private Location selectedStation;
    private Location selectedCoord;
    @Nullable
    private List<Departure> selectedAllDepartures = null;
    private List<Departure> selectedFilteredDepartures = null;
    @Nullable
    private Integer selectedFavState = null;
    @Nullable
    private LinkedHashMap<Line, List<Location>> selectedLines = null;

    private MyActionBar actionBar;
    private ImageButton loadLaterButton, loadEarlierButton;
    private ImageButton nearbyButton;
    private ToggleImageButton favoriteButton;
    private ToggleImageButton hideCancelledDeparturesButton;
    private ViewAnimator viewAnimator;
    private RecyclerView listView;
    private DeparturesAdapter listAdapter;
    private TextView resultStatusView;
    private TextView disclaimerSourceView;
    private boolean hideCancelledDepartures;
    private SwipeRefreshLayout swipeRefresh;

    private BroadcastReceiver tickReceiver;
    private boolean autoRefreshDisabled = false;
    private Date nextLaterTime, nextEarlierTime;
    private Date presetTime;
    private JourneyRef presetJourneyRef;

    private QueryJourneyRunnable queryJourneyRunnable;
    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private static final Logger log = LoggerFactory.getLogger(StationDetailsActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        backgroundThread = new HandlerThread("queryDeparturesThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        setContentView(R.layout.stations_station_details_content);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        actionBar = getMyActionBar();
        setPrimaryColor(R.color.bg_action_bar_stations);
        actionBar.setBack(isTaskRoot() ? null : v -> finish());
        actionBar.swapTitles();
        actionBar.addProgressButton().setOnClickListener(v -> requestRefresh());
        nearbyButton = actionBar.addButton(R.drawable.ic_radar_white_24dp, R.string.stations_station_details_action_explore_nearby_title);
        nearbyButton.setOnClickListener(v -> {
            if (selectedCoord != null)
                StationsActivity.start(this, selectedNetwork, selectedCoord, presetTime);
        });
        nearbyButton.setVisibility(View.GONE);
        addShowMapButtonToActionBar();
        favoriteButton = actionBar.addToggleButton(R.drawable.ic_star_24dp,
                R.string.stations_station_details_action_favorite_title);
        favoriteButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                final Uri rowUri = FavoriteUtils.persist(getContentResolver(),
                        FavoriteStationsProvider.TYPE_FAVORITE, selectedNetwork, selectedStation);
                if (rowUri != null) {
                    selectedFavState = FavoriteStationsProvider.TYPE_FAVORITE;
                    NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
                }
            } else {
                final int numRows = FavoriteUtils.delete(getContentResolver(), selectedNetwork, selectedStation.id);
                if (numRows > 0) {
                    selectedFavState = null;
                    NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
                }
            }
        });
        hideCancelledDeparturesButton = actionBar.addToggleButton(R.drawable.ic_cancelled_24dp,
                R.string.stations_station_details_action_cancelled_title);
        hideCancelledDeparturesButton.setChecked(hideCancelledDepartures);
        hideCancelledDeparturesButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            hideCancelledDepartures = isChecked;
            selectedFilteredDepartures = null;
            updateGUI();
        });
        loadLaterButton = actionBar.addButton(R.drawable.ic_later_white_24dp, R.string.stations_station_details_action_load_later);
        loadLaterButton.setOnClickListener(buttonView -> {
            autoRefreshDisabled = true;
            load(nextLaterTime, false);
        });
        loadEarlierButton = actionBar.addButton(R.drawable.ic_earlier_white_24dp, R.string.stations_station_details_action_load_earlier);
        loadEarlierButton.setOnClickListener(buttonView -> {
            autoRefreshDisabled = true;
            load(nextEarlierTime, true);
        });

        swipeRefresh = findViewById(R.id.stations_station_details_refresh);
        swipeRefresh.setOnRefreshListener(this::requestRefresh);

        viewAnimator = findViewById(R.id.stations_station_details_list_layout);

        listView = findViewById(R.id.stations_station_details_list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        listAdapter = new DeparturesAdapter(this);
        listView.setAdapter(listAdapter);
        ViewCompat.setOnApplyWindowInsetsListener(listView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    insets.bottom + (int) (48 * getResources().getDisplayMetrics().density));
            return windowInsets;
        });

        getMapView().setStationsAware(this);

        resultStatusView = findViewById(R.id.stations_station_details_result_status);

        final Intent intent = getIntent();
        final String networkName = intent.getStringExtra(INTENT_EXTRA_NETWORK);
        final NetworkId network = NetworkId.valueOf(checkNotNull(networkName));
        this.presetTime = (Date) intent.getSerializableExtra(INTENT_EXTRA_PRESETTIME);
        final String stationSerialized = intent.getStringExtra(INTENT_EXTRA_STATION);
        final Station station = new Station(network, (Location) Objects.deserializeFromString(stationSerialized));
        this.presetJourneyRef = (JourneyRef) Objects.deserializeFromString(intent.getStringExtra(INTENT_EXTRA_JOURNEYREF));
        if (intent.hasExtra(INTENT_EXTRA_DEPARTURES)) {
            station.setDepartures(filterDeparturesByProducts(
                    (List<Departure>) intent.getSerializableExtra(INTENT_EXTRA_DEPARTURES),
                    loadProductFilter()));
        }
        selectStation(station);
        statusMessage(getString(R.string.stations_station_details_progress));

        if (presetTime != null) {
            final TextView timeView = findViewById(R.id.stations_station_details_time_text);
            timeView.setVisibility(View.VISIBLE);
            final long presetTimeMs = presetTime.getTime();
            final String text = String.format("%s %s",
                    Formats.formatDate(timeZoneSelector, System.currentTimeMillis(), presetTimeMs, PTDate.NETWORK_OFFSET),
                    Formats.formatTime(timeZoneSelector, presetTimeMs, PTDate.NETWORK_OFFSET));
            timeView.setText(text);
        }

        final View disclaimerView = findViewById(R.id.stations_station_details_disclaimer_group);
        ViewCompat.setOnApplyWindowInsetsListener(disclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
        disclaimerSourceView = findViewById(R.id.stations_station_details_disclaimer_source);
        updateDisclaimerSource(disclaimerSourceView, selectedNetwork, null);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // regular refresh
        tickReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                if (!autoRefreshDisabled)
                    load(null, false);
            }
        };
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        if (!autoRefreshDisabled)
            load(null, false);

        updateFragments();
    }

    @Override
    protected void onResume() {
        super.onResume();

        final JourneyRef journeyRef = presetJourneyRef;
        presetJourneyRef = null;
        if (journeyRef != null) {
            queryJourneyRunnable = QueryJourneyRunnable.startShowJourney(
                    this, null, queryJourneyRunnable,
                    handler, backgroundHandler,
                    network, journeyRef, selectedStation, null);
        }
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

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(final Configuration config) {
        super.onConfigurationChanged(config);

        updateFragments();
    }

    protected void updateFragments() {
        updateFragments(R.id.stations_station_details_list_fragment);
    }

    private void updateGUI() {
        final List<Departure> selectedDepartures = this.getFilteredDepartures();
        if (selectedDepartures != null && !selectedDepartures.isEmpty()) {
            viewAnimator.setDisplayedChild(0);
            listAdapter.notifyDataSetChanged();
        } else {
            statusMessage(getString(R.string.stations_station_details_list_empty));
        }
    }

    private static List<Departure> filterDeparturesByProducts(final Collection<Departure> departures, final Collection<Product> filter) {
        return departures.stream()
                .filter(departure -> filter.contains(departure.line.product))
                .collect(Collectors.toList());
    }

    private List<Departure> getFilteredDepartures() {
        if (selectedFilteredDepartures == null) {
            if (selectedAllDepartures != null) {
                selectedFilteredDepartures = selectedAllDepartures.stream()
                        .filter(departure -> !hideCancelledDepartures || !departure.cancelled)
                        .collect(Collectors.toList());
            }
        }
        return selectedFilteredDepartures;
    }

    private void requestRefresh() {
        autoRefreshDisabled = false;
        load(null, false);
    }

    private void load(final Date time, final boolean earlier) {
        final boolean modeAppend;
        final Date fromTime;
        if (time != null) {
            modeAppend = true;
            fromTime = time;
        } else {
            modeAppend = false;
            fromTime = presetTime != null ? presetTime : new Date();
            nextLaterTime = null;
            nextEarlierTime = new Date(fromTime.getTime() - 30 * 60 * 1000);
        }
        final String requestedStationId = selectedStation.id;
        final NetworkProvider networkProvider = NetworkProviderFactory.provider(selectedNetwork);

        backgroundHandler.removeCallbacksAndMessages(null);
        backgroundHandler
                .post(new QueryDeparturesRunnable(handler, networkProvider, requestedStationId, fromTime, MAX_DEPARTURES) {
                    @Override
                    protected void onPreExecute() {
                        swipeRefresh.setRefreshing(true);
                        actionBar.startProgress();
                    }

                    @Override
                    protected void onPostExecute() {
                        swipeRefresh.setRefreshing(false);
                        actionBar.stopProgress();
                    }

                    @Override
                    protected void onResult(final QueryDeparturesResult result) {
                        if (result.header != null) {
                            updateDisclaimerSource(disclaimerSourceView, selectedNetwork,
                                    product(result.header));
                        }

                        final Set<Product> productFilter = loadProductFilter();
                        if (result.status == QueryDeparturesResult.Status.OK) {
                            boolean somethingAdded = false;
                            Location newSelectedStation = null;
                            for (final StationDepartures stationDepartures : result.stationDepartures) {
                                final Location location = stationDepartures.location;
                                if (location.hasId()) {
                                    Station station = findStation(location.id);
                                    if (station == null) {
                                        station = new Station(selectedNetwork, location);
                                        stations.add(station);
                                    }

                                    final List<Departure> departures = filterDeparturesByProducts(stationDepartures.departures, productFilter);

                                    if (modeAppend && station.getDepartures() != null) {
                                        final Set<JourneyRef> oldJourneyRefs = station.getDepartures().stream().map(
                                                departure -> departure.journeyRef).collect(Collectors.toSet());
                                        for (final Departure departure : departures) {
                                            if (!oldJourneyRefs.contains(departure.journeyRef))
                                                station.getDepartures().add(departure);
                                        }
                                    } else {
                                        station.setDepartures(departures);
                                    }
                                    final List<Departure> unsortedDepartures = station.getDepartures();
                                    if (unsortedDepartures != null) {
                                        unsortedDepartures.sort((d1, d2) ->
                                                Math.toIntExact(d1.getTime().getTime() - d2.getTime().getTime()));
                                    }

                                    final List<LineDestination> stationLines = station.getLines();
                                    final List<LineDestination> lines = stationDepartures.lines;
                                    if (modeAppend && stationLines != null && lines != null) {
                                        final Set<LineDestination> oldLineDestinations = new HashSet<>(stationLines);
                                        for (final LineDestination lineDestination : lines) {
                                            if (!oldLineDestinations.contains(lineDestination))
                                                stationLines.add(lineDestination);
                                        }
                                        station.setLines(stationLines);
                                    } else {
                                        station.setLines(lines);
                                    }

                                    if (location.equals(selectedStation) || selectedAllDepartures == null) {
                                        somethingAdded = true;
                                        newSelectedStation = location;
                                        selectedAllDepartures = station.getDepartures();
                                        selectedLines = groupDestinationsByLine(station.getLines());
                                        selectedFilteredDepartures = null;
                                    }
                                }
                            }

                            if (newSelectedStation != null) {
                                selectedStation = newSelectedStation;
                                if (selectedStation.hasCoord()) {
                                    selectedCoord = selectedStation;
                                    nearbyButton.setVisibility(View.VISIBLE);
                                }
                            }

                            if (earlier) {
                                nextEarlierTime = new Date(fromTime.getTime() - 30 * 60 * 1000);
                            } else if (!somethingAdded) {
                                nextLaterTime = new Date(fromTime.getTime() + 30 * 60 * 1000);
                            } else {
                                long maxTime = fromTime.getTime();
                                for (final Departure departure : selectedAllDepartures) {
                                    final long depTime = departure.plannedTime.getTime();
                                    if (depTime > maxTime)
                                        maxTime = depTime;
                                }
                                nextLaterTime = new Date(maxTime);
                            }

                            updateGUI();
                        } else {
                            log.info("Got {}", result.toShortString());
                            statusMessage(getString(QueryDeparturesRunnable.statusMsgResId(result.status)));
                        }
                    }

                    @Override
                    protected void onInputOutputError(final IOException x) {
                        statusMessage(x.getMessage());
                    }

                    @Override
                    protected void onAllErrors() {
                        statusMessage(getString(R.string.toast_network_problem));
                    }

                    private Station findStation(final String stationId) {
                        for (final Station station : stations)
                            if (stationId.equals(station.location.id))
                                return station;

                        return null;
                    }
                });
    }

    public List<Station> getStations() {
        return stations;
    }

    public Integer getFavoriteState(final String stationId) {
        throw new UnsupportedOperationException();
    }

    private void statusMessage(final String message) {
        final List<Departure> selectedDepartures = this.getFilteredDepartures();
        if (selectedDepartures == null || selectedDepartures.isEmpty()) {
            viewAnimator.setDisplayedChild(1);
            resultStatusView.setText(message);
        }
    }

    public void selectStation(final Station station) {
        final boolean changed = !station.location.equals(selectedStation);

        selectedNetwork = station.network;
        selectedStation = station.location;
        if (selectedStation.hasCoord()) {
            selectedCoord = selectedStation;
            nearbyButton.setVisibility(View.VISIBLE);
        }
        selectedAllDepartures = station.getDepartures();
        selectedFilteredDepartures = null;
        selectedLines = groupDestinationsByLine(station.getLines());

        selectedFavState = FavoriteStationsProvider.favState(getContentResolver(), selectedNetwork, selectedStation);
        favoriteButton.setChecked
                (selectedFavState != null && selectedFavState == FavoriteStationsProvider.TYPE_FAVORITE);

        if (selectedStation.hasCoord()) {
            getMapView().animateToLocation(selectedStation.getLatAsDouble(), selectedStation.getLonAsDouble());
        }

        updateGUI();

        actionBar.setPrimaryTitle(selectedStation.name);
        actionBar.setSecondaryTitle(selectedStation.place);

        if (changed) {
            autoRefreshDisabled = false;
            load(null, false);
        }
    }

    public boolean isSelectedStation(final String stationId) {
        return selectedStation != null && stationId.equals(selectedStation.id);
    }

    private LinkedHashMap<Line, List<Location>> groupDestinationsByLine(final List<LineDestination> lineDestinations) {
        if (lineDestinations == null)
            return null;

        final LinkedHashMap<Line, List<Location>> groups = new LinkedHashMap<>();
        for (final LineDestination lineDestination : lineDestinations) {
            if (lineDestination.destination != null) {
                List<Location> list = groups.get(lineDestination.line);
                if (list == null) {
                    list = new ArrayList<>(2); // A typical line will have two destinations.
                    groups.put(lineDestination.line, list);
                }
                list.add(lineDestination.destination);
            }
        }
        return groups;
    }

    private class DeparturesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final StationDetailsActivity context;
        private final LayoutInflater inflater;

        public DeparturesAdapter(final StationDetailsActivity context) {
            this.context = context;
            this.inflater = LayoutInflater.from(context);

            setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            final List<Departure> selectedDepartures = StationDetailsActivity.this.getFilteredDepartures();
            final int numDepartures = selectedDepartures != null ? selectedDepartures.size() : 0;
            return numDepartures + 1; // account for header
        }

        @Override
        public int getItemViewType(final int position) {
            if (position == 0)
                return R.layout.stations_station_details_header;
            return R.layout.stations_station_details_entry;
        }

        public Departure getItem(final int position) {
            if (position == 0)
                return null;
            return checkNotNull(getFilteredDepartures()).get(position - 1);
        }

        @Override
        public long getItemId(final int position) {
            if (position == 0)
                return RecyclerView.NO_ID;
            return getItem(position).hashCode();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            if (viewType == R.layout.stations_station_details_header)
                return new HeaderViewHolder(context,
                        inflater.inflate(R.layout.stations_station_details_header, parent, false));
            else
                return new DepartureViewHolder(context,
                        inflater.inflate(R.layout.stations_station_details_entry, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind(selectedStation, selectedLines, null, StationDetailsActivity.this);
            } else {
                final Departure departure = getItem(position);
                ((DepartureViewHolder) holder).bind(selectedNetwork, selectedStation, departure);
            }
        }
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameView;
        private final TextView idView;
        private final LinearLayout linesGroup;
        private final LineView additionalLinesView;
        private final ImageView multiStationsView;

        private final LayoutInflater inflater;

        private final LinearLayout.LayoutParams LINES_LAYOUT_PARAMS = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        public HeaderViewHolder(final Context context, final View itemView) {
            super(itemView);

            nameView = itemView.findViewById(R.id.stations_station_details_header_name);
            idView = itemView.findViewById(R.id.stations_station_details_header_id);
            linesGroup = itemView.findViewById(R.id.stations_station_details_header_lines);
            additionalLinesView = itemView
                    .findViewById(R.id.stations_station_details_header_additional_lines);
            multiStationsView = itemView.findViewById(R.id.stations_station_details_header_multistations);
            multiStationsView.setVisibility(View.GONE);

            inflater = LayoutInflater.from(context);
            final Resources res = context.getResources();
            LINES_LAYOUT_PARAMS.setMargins(0, res.getDimensionPixelSize(R.dimen.text_padding_vertical_cram), 0,
                    0);
        }

        public void bind(final Location station, @Nullable final LinkedHashMap<Line, List<Location>> lines,
                         @Nullable final List<Line> additionalLines,
                         final StationDetailsActivity activity) {
            final List<Station> stations = activity.stations;
            // name and id
            nameView.setText(station.uniqueShortName());
            idView.setText(station.displayId != null ? station.displayId : "");
            if (stations.size() > 1) {
                multiStationsView.setVisibility(View.VISIBLE);
                itemView.setOnClickListener(v -> {
                    final PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
                    final Menu menu = popupMenu.getMenu();
                    for (int i = 0; i < stations.size(); i++)
                        menu.add(Menu.NONE, i, Menu.NONE, stations.get(i).location.uniqueShortName());
                    popupMenu.setOnMenuItemClickListener(item -> {
                        final Station newStation = stations.get(item.getItemId());
                        activity.selectStation(newStation);
                        return true;
                    });
                    popupMenu.show();
                });
            } else {
                multiStationsView.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
            }

            // lines
            linesGroup.removeAllViews();
            if (lines != null) {
                linesGroup.setVisibility(View.VISIBLE);
                for (final Map.Entry<Line, List<Location>> linesEntry : lines.entrySet()) {
                    final Line line = linesEntry.getKey();
                    final List<Location> destinations = linesEntry.getValue();

                    final View lineRow = inflater.inflate(R.layout.stations_station_details_header_line, null);
                    linesGroup.addView(lineRow, LINES_LAYOUT_PARAMS);

                    final LineView lineView = lineRow
                            .findViewById(R.id.stations_station_details_header_line_line);
                    lineView.setLine(line);

                    final TextView destinationView = lineRow
                            .findViewById(R.id.stations_station_details_header_line_destination);
                    final StringBuilder text = new StringBuilder();
                    for (final Location destination : destinations) {
                        if (text.length() > 0)
                            text.append(Constants.CHAR_THIN_SPACE).append(Constants.CHAR_LEFT_RIGHT_ARROW)
                                    .append(Constants.CHAR_THIN_SPACE);
                        text.append(destination.uniqueShortName());
                    }
                    destinationView.setText(text);
                }
            } else {
                linesGroup.setVisibility(View.GONE);
            }

            // additional lines
            additionalLinesView.setLines(additionalLines);
        }
    }

    private class DepartureViewHolder extends RecyclerView.ViewHolder {
        private final TextView timeRelView;
        private final TextView timeAbsView;
        private final TextView delayView;
        private final LineView lineView;
        private final TextView destinationView;
        private final TextView positionView;
        private final TextView capacity1stView;
        private final TextView capacity2ndView;
        private final TextView msgView;
        private String msgViewMessageText;
        private boolean msgViewExpanded;

        private final StationDetailsActivity context;

        public DepartureViewHolder(final StationDetailsActivity context, final View itemView) {
            super(itemView);

            timeRelView = itemView.findViewById(R.id.stations_station_entry_time_rel);
            timeAbsView = itemView.findViewById(R.id.stations_station_entry_time_abs);
            delayView = itemView.findViewById(R.id.stations_station_entry_delay);
            lineView = itemView.findViewById(R.id.stations_station_entry_line);
            destinationView = itemView.findViewById(R.id.stations_station_entry_destination);
            positionView = itemView.findViewById(R.id.stations_station_entry_position);
            capacity1stView = itemView.findViewById(R.id.stations_station_entry_capacity_1st_class);
            capacity2ndView = itemView.findViewById(R.id.stations_station_entry_capacity_2nd_class);
            msgView = itemView.findViewById(R.id.stations_station_entry_msg);
            msgViewExpanded = false;
            msgViewMessageText = null;
            msgView.setOnClickListener(v -> {
                msgViewExpanded = !msgViewExpanded;
                renderMsgView();
            });

            this.context = context;
        }

        public void bind(final NetworkId network, final Location station, final Departure departure) {
            final boolean refIsNow = context.presetTime == null;
            final long referenceTime = (refIsNow ? new Date() : context.presetTime).getTime();

            final PTDate predictedTime = departure.predictedTime;
            final PTDate plannedTime = departure.plannedTime;
            final boolean cancelled = departure.cancelled;

            final PTDate time;
            final boolean isPredicted = predictedTime != null;
            if (predictedTime != null)
                time = predictedTime;
            else if (plannedTime != null)
                time = plannedTime;
            else
                throw new IllegalStateException();

            final long timeMillis = time.getTime();
            final boolean isPast = timeMillis < referenceTime;

            itemView.setBackgroundColor(context.getColor(isPast ? R.color.bg_station_before : R.color.bg_level0));

            // time rel
            timeRelView.setText(Formats.formatTimeDiff(context, referenceTime, timeMillis, refIsNow));
            timeRelView.setTypeface(Typeface.DEFAULT, isPredicted ? Typeface.ITALIC : Typeface.NORMAL);
            setStrikeThru(timeRelView, cancelled);

            // time abs
            final PTDate displayTime = timeZoneSelector.getDisplay(time);
            final StringBuilder timeAbs = new StringBuilder(Formats.formatDate(timeZoneSelector, referenceTime, displayTime, false, ""));
            if (timeAbs.length() > 0)
                timeAbs.append(',').append(Constants.CHAR_HAIR_SPACE);
            timeAbs.append(Formats.formatTime(timeZoneSelector, displayTime));
            timeAbsView.setText(timeAbs);
            timeAbsView.setTypeface(Typeface.DEFAULT, isPredicted ? Typeface.ITALIC : Typeface.NORMAL);
            setStrikeThru(timeAbsView, cancelled);

            // delay
            final long delay = predictedTime != null && plannedTime != null
                    ? predictedTime.getTime() - plannedTime.getTime() : 0;
            final long delayMins = delay / DateUtils.MINUTE_IN_MILLIS;
            delayView.setText(delayMins != 0 ? String.format(Locale.US, "(%+d)", delayMins) + ' ' : "");
            delayView.setTypeface(Typeface.DEFAULT, isPredicted ? Typeface.ITALIC : Typeface.NORMAL);
            setStrikeThru(delayView, cancelled);

            // line
            lineView.setLine(departure.line);
            setStrikeThru(lineView, cancelled);

            // destination
            final Location destination = departure.destination;
            if (destination != null) {
                destinationView.setText(Constants.DESTINATION_ARROW_PREFIX + Formats.fullLocationNameIfDifferentPlace(destination, station));
                itemView.setOnClickListener(destination.id == null ? null : v ->
                        start(context, network, destination, null, null));
                setStrikeThru(destinationView, cancelled);
            } else {
                destinationView.setText(null);
                itemView.setOnClickListener(null);
            }

            if (departure.journeyRef != null) {
                final View.OnClickListener onClickListener = clickedView -> {
                    context.queryJourneyRunnable = QueryJourneyRunnable.startShowJourney(
                            context, clickedView, context.queryJourneyRunnable,
                            context.handler, context.backgroundHandler,
                            network, departure.journeyRef, station, null);
                };
                lineView.setClickable(true);
                lineView.setOnClickListener(onClickListener);
                destinationView.setClickable(true);
                destinationView.setOnClickListener(onClickListener);
            }

            final Position position = departure.position;
            if (position != null) {
                positionView.setText(position.toString());
                setStrikeThru(positionView, cancelled);
            } else {
                positionView.setText(null);
            }

            // capacity
            final int[] capacity = departure.capacity;
            if (capacity != null) {
                capacity1stView.setVisibility(View.VISIBLE);
                capacity2ndView.setVisibility(View.VISIBLE);
                capacity(capacity1stView, capacity[0]);
                capacity(capacity2ndView, capacity[1]);
            } else {
                capacity1stView.setVisibility(View.GONE);
                capacity2ndView.setVisibility(View.GONE);
            }

            // message
            msgViewMessageText = (departure.message == null && departure.line.message == null) ? null
                : Joiner.on('\n').skipNulls().join(departure.message, departure.line.message);
            msgViewExpanded = false;
            renderMsgView();
        }

        private void renderMsgView() {
            if (msgViewMessageText == null) {
                msgView.setVisibility(View.GONE);
                msgView.setText(null);
                return;
            }

            msgView.setVisibility(View.VISIBLE);
            final String displayMessage;
            if (msgViewExpanded || msgViewMessageText.length() < 80) {
                displayMessage = msgViewMessageText;
                msgView.setTextColor(context.getColor(R.color.fg_significant));
            } else {
                displayMessage = context.getString(R.string.directions_trip_details_shortened_message,
                        msgViewMessageText.substring(0, Math.min(msgViewMessageText.length(), 60)));
                msgView.setTextColor(context.getColor(R.color.fg_insignificant));
            };
            final Spanned html = Html.fromHtml(HtmlUtils.makeLinksClickableInHtml(displayMessage), Html.FROM_HTML_MODE_COMPACT);
            msgView.setText(html);
            msgView.setMovementMethod(LinkMovementMethod.getInstance());
        }

        private void setStrikeThru(final TextView view, final boolean strikeThru) {
            if (strikeThru)
                view.setPaintFlags(view.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            else
                view.setPaintFlags(view.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }

        private void capacity(final TextView capacityView, final int capacity) {
            if (capacity == 1)
                capacityView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.capacity_1, 0);
            else if (capacity == 2)
                capacityView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.capacity_2, 0);
            else if (capacity == 3)
                capacityView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.capacity_3, 0);
        }
    }

    private static String bvgStationIdNfcToQr(final String stationIdStr) {
        final int stationId = Integer.parseInt(stationIdStr);

        if (stationId < 100000000 || stationId >= 1000000000)
            return stationIdStr;
        final int low = stationId % 100000;
        final int middle = (stationId % 100000000) - low;

        if (middle != 1000000)
            return stationIdStr;

        final int high = stationId - (stationId % 100000000);

        return Integer.toString(high / 1000 + low);
    }
}
