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
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewAnimator;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.base.Joiner;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.OeffiMapView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.directions.QueryJourneyRunnable;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.Formats;
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
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;
import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class StationDetailsActivity extends OeffiActivity implements StationsAware {
    private static final String INTENT_EXTRA_NETWORK = StationDetailsActivity.class.getName() + ".network";
    private static final String INTENT_EXTRA_STATION = StationDetailsActivity.class.getName() + ".station";
    private static final String INTENT_EXTRA_DEPARTURES = StationDetailsActivity.class.getName() + ".departures";

    public static void start(final Context context, final NetworkId networkId, final Location station) {
        start(context, networkId, station, null);
    }

    public static void start(final Context context, final NetworkId networkId, final Location station,
            @Nullable final List<Departure> departures) {
        checkArgument(station.type == LocationType.STATION);
        final Intent intent = StationDetailsActivity.fillIntent(
                new Intent(context, StationDetailsActivity.class),
                networkId, station);
        if (departures != null)
            intent.putExtra(StationDetailsActivity.INTENT_EXTRA_DEPARTURES, (Serializable) departures);
        context.startActivity(intent);
    }

    public static Intent fillIntent(Intent intent, final NetworkId networkId, final Location station) {
        checkArgument(station.type == LocationType.STATION);
        checkNotNull(networkId);
        intent.putExtra(StationDetailsActivity.INTENT_EXTRA_NETWORK, networkId.name());
        intent.putExtra(StationDetailsActivity.INTENT_EXTRA_STATION, Objects.serializeToString(station));
        return intent;
    }

    public static final int MAX_DEPARTURES = 200;

    private final List<Station> stations = new ArrayList<>();

    private NetworkId selectedNetwork;
    private Location selectedStation;
    @Nullable
    private List<Departure> selectedAllDepartures = null;
    private List<Departure> selectedFilteredDepartures = null;
    @Nullable
    private Integer selectedFavState = null;
    @Nullable
    private LinkedHashMap<Line, List<Location>> selectedLines = null;

    private MyActionBar actionBar;
    private ImageButton loadLaterButton, loadEarlierButton;
    private ToggleImageButton favoriteButton;
    private ToggleImageButton hideCancelledDeparturesButton;
    private ViewAnimator viewAnimator;
    private RecyclerView listView;
    private DeparturesAdapter listAdapter;
    private TextView resultStatusView;
    private TextView disclaimerSourceView;
    private OeffiMapView mapView;
    private boolean hideCancelledDepartures;

    private BroadcastReceiver tickReceiver;
    private boolean autoRefreshDisabled = false;
    private Date nextLaterTime, nextEarlierTime;

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
        actionBar.addProgressButton().setOnClickListener(v -> {
            autoRefreshDisabled = false;
            load(null, false);
        });
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

        mapView = findViewById(R.id.stations_station_details_map);
        mapView.setStationsAware(this);
        final TextView mapDisclaimerView = findViewById(R.id.stations_station_details_map_disclaimer);
        mapDisclaimerView.setText(mapView.getTileProvider().getTileSource().getCopyrightNotice());
        ViewCompat.setOnApplyWindowInsetsListener(mapDisclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });

        resultStatusView = findViewById(R.id.stations_station_details_result_status);

        final Intent intent = getIntent();
        String networkName = intent.getStringExtra(INTENT_EXTRA_NETWORK);
        final NetworkId network = NetworkId.valueOf(checkNotNull(networkName));
        final String stationSerialized = intent.getStringExtra(INTENT_EXTRA_STATION);
        final Station station = new Station(network, (Location) Objects.deserializeFromString(stationSerialized));
        if (intent.hasExtra(INTENT_EXTRA_DEPARTURES)) {
            station.departures = filterDeparturesByProducts(
                    (List<Departure>) intent.getSerializableExtra(INTENT_EXTRA_DEPARTURES),
                    loadProductFilter());
        }
        selectStation(station);
        statusMessage(getString(R.string.stations_station_details_progress));

        favoriteButton
                .setChecked(selectedFavState != null && selectedFavState == FavoriteStationsProvider.TYPE_FAVORITE);

        final View disclaimerView = findViewById(R.id.stations_station_details_disclaimer_group);
        ViewCompat.setOnApplyWindowInsetsListener(disclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
        disclaimerSourceView = findViewById(R.id.stations_station_details_disclaimer_source);
        updateDisclaimerSource(disclaimerSourceView, selectedNetwork.name(), null);
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

        load(null, false);

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

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(final Configuration config) {
        super.onConfigurationChanged(config);

        updateFragments();
    }

    private void updateFragments() {
        updateFragments(R.id.stations_station_details_list_fragment, R.id.stations_station_details_map_fragment);
    }

    private void updateGUI() {
        final List<Departure> selectedDepartures = this.getFilteredDepartures();
        if (selectedDepartures != null) {
            if (!selectedDepartures.isEmpty()) {
                viewAnimator.setDisplayedChild(0);
                listAdapter.notifyDataSetChanged();
            } else {
                statusMessage(getString(R.string.stations_station_details_list_empty));
            }
        }
    }

    private static List<Departure> filterDeparturesByProducts(final Collection<Departure> departures, final Collection<Product> filter) {
        return departures.stream()
                .filter(departure -> filter.contains(departure.line.product))
                .collect(Collectors.toList());
    }

    private List<Departure> getFilteredDepartures() {
        if (selectedFilteredDepartures == null) {
            if (selectedAllDepartures == null) {
                selectedFilteredDepartures = new ArrayList<>();
            } else {
                selectedFilteredDepartures = selectedAllDepartures.stream()
                        .filter(departure -> !hideCancelledDepartures || !departure.cancelled)
                        .collect(Collectors.toList());
            }
        }
        return selectedFilteredDepartures;
    }

    private void load(final Date time, final boolean earlier) {
        final boolean modeAppend;
        final Date fromTime;
        if (time != null) {
            modeAppend = true;
            fromTime = time;
        } else {
            modeAppend = false;
            fromTime = new Date();
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
                        actionBar.startProgress();
                    }

                    @Override
                    protected void onPostExecute() {
                        actionBar.stopProgress();
                    }

                    @Override
                    protected void onResult(final QueryDeparturesResult result) {
                        if (result.header != null)
                            updateDisclaimerSource(disclaimerSourceView, selectedNetwork.name(),
                                    product(result.header));

                        Set<Product> productFilter = loadProductFilter();
                        if (result.status == QueryDeparturesResult.Status.OK) {
                            boolean somethingAdded = false;
                            for (final StationDepartures stationDepartures : result.stationDepartures) {
                                Location location = stationDepartures.location;
                                if (location.hasId()) {
                                    Station station = findStation(location.id);
                                    if (station == null) {
                                        station = new Station(selectedNetwork, location);
                                        stations.add(station);
                                    }

                                    List<Departure> departures = filterDeparturesByProducts(stationDepartures.departures, productFilter);
                                    station.departures = departures;
                                    station.setLines(stationDepartures.lines);

                                    if (location.equals(selectedStation)) {
                                        somethingAdded = true;
                                        if (modeAppend && selectedAllDepartures != null) {
                                            Set<JourneyRef> oldJourneyRefs = selectedAllDepartures.stream().map(
                                                    departure -> departure.journeyRef).collect(Collectors.toSet());
                                            for (Departure departure : departures) {
                                                if (!oldJourneyRefs.contains(departure.journeyRef))
                                                    selectedAllDepartures.add(departure);
                                            }
                                        } else {
                                            selectedAllDepartures = departures;
                                        }
                                        Collections.sort(selectedAllDepartures, (d1, d2) ->
                                                Math.toIntExact(d1.getTime().getTime() - d2.getTime().getTime()));
                                        selectedLines = groupDestinationsByLine(stationDepartures.lines);
                                        selectedFilteredDepartures = null;
                                    }
                                }
                            }

                            if (earlier) {
                                nextEarlierTime = new Date(fromTime.getTime() - 30 * 60 * 1000);
                            } else if (!somethingAdded) {
                                nextLaterTime = new Date(fromTime.getTime() + 30 * 60 * 1000);
                            } else {
                                long maxTime = fromTime.getTime();
                                for (Departure departure : selectedAllDepartures) {
                                    long depTime = departure.plannedTime.getTime();
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
        selectedAllDepartures = station.departures;
        selectedFilteredDepartures = null;
        selectedLines = groupDestinationsByLine(station.getLines());

        selectedFavState = FavoriteStationsProvider.favState(getContentResolver(), selectedNetwork, selectedStation);

        if (selectedStation.hasCoord())
            mapView.getController()
                    .animateTo(new GeoPoint(selectedStation.getLatAsDouble(), selectedStation.getLonAsDouble()));

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

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            if (viewType == R.layout.stations_station_details_header)
                return new HeaderViewHolder(context,
                        inflater.inflate(R.layout.stations_station_details_header, parent, false));
            else
                return new DepartureViewHolder(context,
                        inflater.inflate(R.layout.stations_station_details_entry, parent, false));
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind(selectedStation, selectedLines, null);
            } else {
                final Departure departure = getItem(position);
                ((DepartureViewHolder) holder).bind(selectedNetwork, selectedStation, departure);
            }
        }
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView idView;
        private final LinearLayout linesGroup;
        private final LineView additionalLinesView;

        private final LayoutInflater inflater;

        private final LinearLayout.LayoutParams LINES_LAYOUT_PARAMS = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        public HeaderViewHolder(final Context context, final View itemView) {
            super(itemView);

            idView = itemView.findViewById(R.id.stations_station_details_header_id);
            linesGroup = itemView.findViewById(R.id.stations_station_details_header_lines);
            additionalLinesView = itemView
                    .findViewById(R.id.stations_station_details_header_additional_lines);

            inflater = LayoutInflater.from(context);
            final Resources res = context.getResources();
            LINES_LAYOUT_PARAMS.setMargins(0, res.getDimensionPixelSize(R.dimen.text_padding_vertical_cram), 0,
                    0);
        }

        public void bind(final Location station, @Nullable final LinkedHashMap<Line, List<Location>> lines,
                @Nullable final List<Line> additionalLines) {
            // id
            idView.setText(station.hasId() ? station.id : "");

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

    private static class DepartureViewHolder extends RecyclerView.ViewHolder {
        private final TextView timeRelView;
        private final TextView timeAbsView;
        private final TextView delayView;
        private final LineView lineView;
        private final TextView destinationView;
        private final TextView positionView;
        private final TextView capacity1stView;
        private final TextView capacity2ndView;
        private final TextView msgView;

        private final StationDetailsActivity context;
        private final java.text.DateFormat timeFormat;

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

            this.context = context;
            this.timeFormat = DateFormat.getTimeFormat(context);
        }

        public void bind(final NetworkId network, final Location station, final Departure departure) {
            final long currentTime = System.currentTimeMillis();

            final Date predictedTime = departure.predictedTime;
            final Date plannedTime = departure.plannedTime;
            final boolean cancelled = departure.cancelled;

            long time;
            final boolean isPredicted = predictedTime != null;
            if (predictedTime != null)
                time = predictedTime.getTime();
            else if (plannedTime != null)
                time = plannedTime.getTime();
            else
                throw new IllegalStateException();

            // time rel
            timeRelView.setText(Formats.formatTimeDiff(context, currentTime, time));
            timeRelView.setTypeface(Typeface.DEFAULT, isPredicted ? Typeface.ITALIC : Typeface.NORMAL);
            setStrikeThru(timeRelView, cancelled);

            // time abs
            final StringBuilder timeAbs = new StringBuilder(Formats.formatDate(context, currentTime, time, false, ""));
            if (timeAbs.length() > 0)
                timeAbs.append(',').append(Constants.CHAR_HAIR_SPACE);
            timeAbs.append(timeFormat.format(time));
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
                destinationView.setText(Constants.DESTINATION_ARROW_PREFIX + destination.uniqueShortName());
                itemView.setOnClickListener(destination.id != null ? v -> start(context, network, destination) : null);
                setStrikeThru(destinationView, cancelled);
            } else {
                destinationView.setText(null);
                itemView.setOnClickListener(null);
            }

            if (departure.journeyRef != null) {
                View.OnClickListener onClickListener = clickedView -> {
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

            positionView.setText(departure.position != null ?
                    Constants.DESTINATION_ARROW_INVISIBLE_PREFIX +
                            Formats.formatPosition(context, departure.position, departure.line) :
                    null);
            setStrikeThru(positionView, cancelled);

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
            if (departure.message != null || departure.line.message != null) {
                msgView.setVisibility(View.VISIBLE);
                Spanned html = Html.fromHtml(Joiner.on('\n').skipNulls().join(departure.message, departure.line.message));
                msgView.setText(html);
                msgView.setMovementMethod(LinkMovementMethod.getInstance());
            } else {
                msgView.setVisibility(View.GONE);
                msgView.setText(null);
            }
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
