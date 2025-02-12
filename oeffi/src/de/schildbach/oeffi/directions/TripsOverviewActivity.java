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

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Uninterruptibles;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryTripsRunnable.TripRequestData;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.QueryTripsContext;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.TripOptions;
import de.schildbach.pte.exception.InternalErrorException;
import de.schildbach.pte.exception.InvalidDataException;
import de.schildbach.pte.exception.NotFoundException;
import de.schildbach.pte.exception.SessionExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class TripsOverviewActivity extends OeffiActivity {
    private static final int DETAILS_NEW_NAVIGATION = 4711;

    public static class RenderConfig implements Serializable {
        public boolean isAlternativeConnectionSearch;
        public TimeSpec referenceTime;
        public JourneyRef feederJourneyRef;
        public JourneyRef connectionJourneyRef;
        public int actionBarColor;
    }

    private static final String INTENT_EXTRA_NETWORK = TripsOverviewActivity.class.getName() + ".network";
    private static final String INTENT_EXTRA_RESULT = TripsOverviewActivity.class.getName() + ".result";
    private static final String INTENT_EXTRA_ARR_DEP = TripsOverviewActivity.class.getName() + ".arr_dep";
    private static final String INTENT_EXTRA_HISTORY_URI = TripsOverviewActivity.class.getName() + ".history";
    private static final String INTENT_EXTRA_RELOAD_REQUEST_DATA = TripsOverviewActivity.class.getName() + ".reqdata";
    private static final String INTENT_EXTRA_RENDERCONFIG = TripDetailsActivity.class.getName() + ".config";

    private static String nameAndPlace(final Location location) {
        return location.place != null ? (location.place + ", " + location.name) : location.name;
    }

    public static void start(final Context context, final NetworkId network, final TimeSpec.DepArr depArr,
            final QueryTripsResult result, final Uri historyUri, final TripRequestData reloadRequestData) {
        start(context, network, depArr, result, historyUri, reloadRequestData, new RenderConfig());
    }

    public static void start(final Context context, final NetworkId network, final TimeSpec.DepArr depArr,
            final QueryTripsResult result, final Uri historyUri, final TripRequestData reloadRequestData,
            final RenderConfig renderConfig) {
        final Intent intent = new Intent(context, TripsOverviewActivity.class);
        if (result.queryUri != null)
            intent.setData(Uri.parse(result.queryUri));
        intent.putExtra(INTENT_EXTRA_NETWORK, checkNotNull(network));
        intent.putExtra(INTENT_EXTRA_RESULT, result);
        intent.putExtra(INTENT_EXTRA_ARR_DEP, depArr == TimeSpec.DepArr.DEPART);
        if (historyUri != null)
            intent.putExtra(INTENT_EXTRA_HISTORY_URI, historyUri.toString());
        intent.putExtra(INTENT_EXTRA_RELOAD_REQUEST_DATA, reloadRequestData);
        intent.putExtra(INTENT_EXTRA_RENDERCONFIG, checkNotNull(renderConfig));
        context.startActivity(intent);
    }

    private NetworkId network;
    private SearchMoreContext searchMoreContext;
    private RenderConfig renderConfig;

    private MyActionBar actionBar;
    private ImageButton searchMoreButton;
    private @Nullable QueryTripsContext context;
    private TripsGallery barView;

    private final NavigableSet<TripInfo> trips = new TreeSet<>((tripC1, tripC2) -> {
        final Trip trip1 = tripC1.trip;
        final Trip trip2 = tripC2.trip;
//        if (trip1.equals(trip2))
        if (trip1.getUniqueId().equals(trip2.getUniqueId()))
            return 0;
        else
            return ComparisonChain.start() //
                    .compare(trip1.getFirstDepartureTime(), trip2.getFirstDepartureTime()) //
                    .compare(trip1.getLastArrivalTime(), trip2.getLastArrivalTime()) //
                    .compare(trip1.numChanges, trip2.numChanges, Ordering.natural().nullsLast()) //
                    .result();
    });
    private boolean queryMoreTripsRunning = false;
    private boolean reloadRequested = false;
    private boolean searchMoreRequested = false;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            barView.invalidate();
        }
    };

    private static final Logger log = LoggerFactory.getLogger(TripsOverviewActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        renderConfig = (RenderConfig) intent.getSerializableExtra(INTENT_EXTRA_RENDERCONFIG);
        network = (NetworkId) intent.getSerializableExtra(INTENT_EXTRA_NETWORK);
        final QueryTripsResult result = (QueryTripsResult) intent.getSerializableExtra(INTENT_EXTRA_RESULT);
        final boolean dep = intent.getBooleanExtra(INTENT_EXTRA_ARR_DEP, true);
        final TripRequestData reloadRequestData = (TripRequestData) intent.getSerializableExtra(INTENT_EXTRA_RELOAD_REQUEST_DATA);
        final String historyUriStr = intent.getStringExtra(INTENT_EXTRA_HISTORY_URI);
        final Uri historyUri = historyUriStr != null ? Uri.parse(historyUriStr) : null;

        this.searchMoreContext = new SearchMoreContext(NetworkProviderFactory.provider(network), reloadRequestData, dep);

        setContentView(R.layout.directions_trip_overview_content);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, 0, insets.right, 0);
            return windowInsets;
        });

        actionBar = getMyActionBar();
        setPrimaryColor(renderConfig.actionBarColor > 0 ? renderConfig.actionBarColor : R.color.bg_action_bar_directions_darkdefault);
        actionBar.setBack(v -> finish());
        actionBar.setCustomTitles(R.layout.directions_trip_overview_custom_title);
        if (searchMoreContext.canProvideSearchMore()) {
            searchMoreButton = actionBar.addButton(R.drawable.ic_search_more_white_24dp, R.string.directions_overview_search_more_title);
            setSearchMoreButtonEnabled(false);
            searchMoreButton.setOnClickListener(view -> {
                setSearchMoreButtonEnabled(false);
                searchMoreRequested = true;
                handler.post(checkMoreRunnable);
            });
        }
        actionBar.addProgressButton().setOnClickListener(v -> {
            setSearchMoreButtonEnabled(false);
            reloadRequested = true;
            handler.post(checkMoreRunnable);
        });

        barView = findViewById(R.id.trips_bar_view);
        barView.setRenderConfig(renderConfig);
        barView.setOnItemClickListener((parent, v, position, id) -> {
            final TripInfo tripInfo = (TripInfo) barView.getAdapter().getItem(position);
            if (tripInfo != null) {
                final Trip trip = tripInfo.trip;
                if (trip.legs != null) {
                    TripDetailsActivity.RenderConfig config = new TripDetailsActivity.RenderConfig();
                    config.isAlternativeConnectionSearch = renderConfig.isAlternativeConnectionSearch;
                    config.queryTripsRequestData = reloadRequestData;
                    if (renderConfig.isAlternativeConnectionSearch) {
                        TripDetailsActivity.startForResult(TripsOverviewActivity.this, DETAILS_NEW_NAVIGATION, network, trip, config);
                    } else {
                        TripDetailsActivity.start(TripsOverviewActivity.this, network, trip, config);
                    }

                    final Date firstPublicLegDepartureTime = trip.getFirstPublicLegDepartureTime();
                    final Date lastPublicLegArrivalTime = trip.getLastPublicLegArrivalTime();

                    // save last trip to history
                    if (firstPublicLegDepartureTime != null && lastPublicLegArrivalTime != null && historyUri != null) {
                        final ContentValues values = new ContentValues();
                        values.put(QueryHistoryProvider.KEY_LAST_DEPARTURE_TIME, firstPublicLegDepartureTime.getTime());
                        values.put(QueryHistoryProvider.KEY_LAST_ARRIVAL_TIME, lastPublicLegArrivalTime.getTime());
                        values.put(QueryHistoryProvider.KEY_LAST_TRIP, serialize(trip));
                        getContentResolver().update(historyUri, values, null, null);
                    }
                }
            }
        });
        barView.setOnScrollListener(() -> handler.post(checkMoreRunnable));

        final View disclaimerView = findViewById(R.id.directions_trip_overview_disclaimer_group);
        ViewCompat.setOnApplyWindowInsetsListener(disclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });

        processResult(result, false, false, searchMoreContext);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DETAILS_NEW_NAVIGATION && resultCode == RESULT_OK) {
            // user has started another navigation
            // then close this overview (and return to initial navigation in the background)
            finish();
        }
    }

    private byte[] serialize(final Object object) {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final ObjectOutputStream os = new ObjectOutputStream(baos);
            os.writeObject(object);
            os.close();
            return baos.toByteArray();
        } catch (final IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // background thread
        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        // regular refresh
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        barView.invalidate();

        // delay because GUI is not initialized immediately
        handler.postDelayed(checkMoreRunnable, 50);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(tickReceiver);

        // cancel background thread
        backgroundHandler = null;
        handler.removeCallbacks(checkMoreRunnable);
        backgroundThread.getLooper().quit();
        queryMoreTripsRunning = false;

        super.onStop();
    }

    private final Runnable checkMoreRunnable = new Runnable() {
        public void run() {
            if (!queryMoreTripsRunning && backgroundHandler != null) {
                final QueryTripsContext context = TripsOverviewActivity.this.context;

                final int positionOffset = context != null && context.canQueryEarlier() ? 0 : 1;
                final int lastVisiblePosition = barView.getLastVisiblePosition() - positionOffset;
                final int firstVisiblePosition = barView.getFirstVisiblePosition() - positionOffset;

                Runnable queryTripsRunnable = null;
                if (context != null && context.canQueryLater() && (lastVisiblePosition == AdapterView.INVALID_POSITION
                        || lastVisiblePosition + 1 >= trips.size())) {
                    queryTripsRunnable = new QueryMoreTripsRunnable(context, false, true, searchMoreContext);
                } else if (context != null && context.canQueryEarlier()
                        && (firstVisiblePosition == AdapterView.INVALID_POSITION || firstVisiblePosition <= 0)) {
                    queryTripsRunnable = new QueryMoreTripsRunnable(context, true, false, searchMoreContext);
                } else if (reloadRequested) {
                    reloadRequested = false;
                    searchMoreContext.reset();
                    queryTripsRunnable = new QueryMoreTripsRunnable(context, false, false, searchMoreContext);
                } else if (searchMoreRequested) {
                    searchMoreRequested = false;
                    if (searchMoreContext.prepareNextRound()) {
                        queryTripsRunnable = new QueryMoreTripsRunnable(context, false, false, searchMoreContext);
                    }
                } else if (searchMoreContext.searchMorePossible()) {
                    runOnUiThread(() -> setSearchMoreButtonEnabled(true));
                }

                if (queryTripsRunnable != null && backgroundHandler != null) {
                    queryMoreTripsRunning = true;
                    backgroundHandler.post(queryTripsRunnable);
                }
            }
        }
    };

    private class QueryMoreTripsRunnable implements Runnable {
        final private MyActionBar actionBar = getMyActionBar();
        final private QueryTripsContext context;
        final private boolean earlier, later;
        final private SearchMoreContext searchMoreContext;

        public QueryMoreTripsRunnable(
                final QueryTripsContext context,
                final boolean earlier, final boolean later,
                final SearchMoreContext searchMoreContext) {
            this.context = context;
            this.earlier = earlier;
            this.later = later;
            this.searchMoreContext = searchMoreContext;
        }

        public void run() {
            runOnUiThread(() -> actionBar.startProgress());

            try {
                doRequest();
            } finally {
                queryMoreTripsRunning = false;

                runOnUiThread(() -> {
                    actionBar.stopProgress();
                });
            }
        }

        private void doRequest() {
            int tries = 0;

            while (true) {
                tries++;

                try {
                    final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
                    final TripRequestData requestData = searchMoreContext.getNextRequestData();
                    final QueryTripsResult result = (earlier || later)
                        ? networkProvider.queryMoreTrips(
                                context,
                                later)
                        : networkProvider.queryTrips(
                            requestData.from,
                            requestData.via,
                            requestData.to,
                            requestData.date,
                            requestData.dep,
                            requestData.options);

                    runOnUiThread(() -> {
                        log.debug("Got {} ({})", result.toShortString(), later ? "later" : "earlier");
                        if (result.status == QueryTripsResult.Status.OK) {
                            processResult(result, earlier, later, searchMoreContext);

                            // fetch more
                            handler.postDelayed(checkMoreRunnable, 50);
                        } else if (result.status == QueryTripsResult.Status.NO_TRIPS) {
                            // ignore
                        } else {
                            new Toast(TripsOverviewActivity.this).toast(R.string.toast_network_problem);
                        }
                    });
                } catch (final SessionExpiredException | NotFoundException x) {
                    runOnUiThread(() -> new Toast(TripsOverviewActivity.this).longToast(R.string.toast_session_expired));
                } catch (final InvalidDataException x) {
                    runOnUiThread(() -> new Toast(TripsOverviewActivity.this).longToast(R.string.toast_invalid_data,
                            x.getMessage()));
                } catch (final IOException x) {
                    final String message = "IO problem while processing " + context + " on " + network + " (try "
                            + tries + ")";
                    log.info(message, x);
                    if (tries >= Constants.MAX_TRIES_ON_IO_PROBLEM) {
                        if (x instanceof SocketTimeoutException || x instanceof UnknownHostException
                                || x instanceof SocketException || x instanceof SSLException) {
                            runOnUiThread(() -> new Toast(TripsOverviewActivity.this).toast(R.string.toast_network_problem));
                        } else if (x instanceof InternalErrorException) {
                            runOnUiThread(() -> new Toast(TripsOverviewActivity.this).toast(R.string.toast_internal_error,
                                    ((InternalErrorException) x).getUrl().host()));
                        } else {
                            throw new RuntimeException(message, x);
                        }

                        break;
                    }

                    Uninterruptibles.sleepUninterruptibly(tries, TimeUnit.SECONDS);

                    // try again
                    continue;
                } catch (final RuntimeException x) {
                    final String message = "uncategorized problem while processing " + context + " on " + network;
                    throw new RuntimeException(message, x);
                }

                break;
            }
        }
    }

    private void processResult(
            final QueryTripsResult result,
            final boolean earlier, final boolean later,
            final SearchMoreContext searchMoreContext) {
        // update header
        if (result.from != null)
            ((TextView) findViewById(R.id.directions_trip_overview_custom_title_from))
                    .setText(nameAndPlace(result.from));
        findViewById(R.id.directions_trip_overview_custom_title_via_row)
                .setVisibility(result.via != null ? View.VISIBLE : View.GONE);
        if (result.via != null)
            ((TextView) findViewById(R.id.directions_trip_overview_custom_title_via)).setText(nameAndPlace(result.via));
        if (result.to != null)
            ((TextView) findViewById(R.id.directions_trip_overview_custom_title_to)).setText(nameAndPlace(result.to));

        // update server product
        if (result.header != null) {
            final TextView serverProductView = findViewById(R.id.trips_server_product);
            serverProductView.setText(product(result.header));
            serverProductView.setVisibility(View.VISIBLE);
        }

        boolean earlierOrLater = earlier || later;
        boolean initial = searchMoreContext.currentRound == 0 && !earlierOrLater;
        if (initial) {
            trips.clear();
            searchMoreContext.reset();
        }

        // remove implausible trips and adjust untravelable legs
        for (final Iterator<Trip> i = result.trips.iterator(); i.hasNext();) {
            final Trip trip = i.next();
            final long duration = trip.getDuration();
            if (duration < 0 || duration > DateUtils.DAY_IN_MILLIS * 5) {
                log.info("Not showing implausible trip: {}", trip);
                i.remove();
            } else {
                trip.adjustUntravelableIndividualLegs();
            }
        }

        // determine new trips
        int countNew = 0;
        for (Trip trip : result.trips) {
            if (trips.add(searchMoreContext.newTripInfo(trip, earlierOrLater)))
                countNew += 1;
        }
        searchMoreContext.tellNewTripsAdded(countNew, trips);

        // redraw
        barView.setTrips(new ArrayList<>(trips), result.context != null && result.context.canQueryLater(),
                result.context != null && result.context.canQueryEarlier());

        // initial cursor positioning
        if (initial && !trips.isEmpty())
            barView.setSelection(searchMoreContext.departureBased ? 1 : trips.size() - 1);

        // save context for next request
        context = result.context;
    }

    private void setSearchMoreButtonEnabled(final boolean enabled) {
        if (searchMoreButton == null)
            return;

        if (enabled) {
            searchMoreButton.setEnabled(true);
            searchMoreButton.setImageDrawable(getDrawable(R.drawable.ic_search_more_white_24dp));
        } else {
            searchMoreButton.setEnabled(false);
            searchMoreButton.setImageDrawable(getDrawable(R.drawable.ic_search_more_grey_24dp));
        }
    }

    public static class SearchMoreContext {
        public static final int MAX_MIN_TRANSFER_TIME_MINUTES = 30;

        public final NetworkProvider provider;
        public final TripRequestData reloadRequestData;
        public final boolean departureBased;
        private int currentRound = 0;
        private List<Integer> attemptableTransferTimes = null;
        private int lastRequestedMinTransferTime = 0;
        public TripRequestData nextRequestData;

        public SearchMoreContext(
                final NetworkProvider provider,
                final TripRequestData reloadRequestData,
                final boolean departureBased) {
            this.provider = provider;
            this.reloadRequestData = reloadRequestData;
            this.departureBased = departureBased;
            reset();
        }

        public void reset() {
            currentRound = 0;
            attemptableTransferTimes = null;
            final Integer minTransferTimeMinutes = reloadRequestData.options.minTransferTimeMinutes;
            lastRequestedMinTransferTime = minTransferTimeMinutes == null ? 0 : minTransferTimeMinutes;
        }

        public boolean canProvideSearchMore() {
            if (!provider.hasCapabilities(NetworkProvider.Capability.MIN_TRANSFER_TIMES))
                return false;

            // return departureBased;
            return true;
        }

        public TripInfo newTripInfo(final Trip trip, final boolean isEarlierOrLater) {
            TripInfo tripInfo = new TripInfo(trip);
            tripInfo.isEarlierOrLater = isEarlierOrLater;
            tripInfo.addedInRound = isEarlierOrLater ? 0 : currentRound;
            return tripInfo;
        }

        public void tellNewTripsAdded(final int countNew, final NavigableSet<TripInfo> trips) {
//            if (countNew <= 0)
//                return;

            attemptableTransferTimes = new LinkedList<>();
            long minPlanned = Long.MAX_VALUE;
            long minPredicted = Long.MAX_VALUE;
            long limitLast = (long) lastRequestedMinTransferTime * 60000;
            int n=0;
            for (TripInfo tripInfo : trips) {
                if (tripInfo.addedInRound < currentRound || tripInfo.isEarlierOrLater)
                    continue;

                final Trip trip = tripInfo.trip;
                Trip.Public prevLeg = null;
                for (Trip.Leg aLeg : trip.legs) {
                    if (aLeg instanceof Trip.Public) {
                        Trip.Public leg = (Trip.Public) aLeg;
                        if (prevLeg != null) {
                            final long plannedArrivalTime = prevLeg.arrivalStop.plannedArrivalTime.getTime();
                            final long predictedArrivalTime = prevLeg.arrivalStop.getArrivalTime().getTime();
                            final long plannedDepartureTime = leg.departureStop.plannedDepartureTime.getTime();
                            final long predictedDepartureTime = leg.departureStop.getDepartureTime().getTime();
                            final long diffPlanned = plannedDepartureTime - plannedArrivalTime;
                            final long diffPredicted = predictedDepartureTime - predictedArrivalTime;
                            if (diffPlanned >= limitLast && diffPlanned < minPlanned)
                                minPlanned = diffPlanned;
                            if (diffPredicted >= limitLast && diffPredicted < minPredicted)
                                minPredicted = diffPredicted;
                        }
                        prevLeg = leg;
                    }
                }
            }
            long t1 = 0;
            long t2 = 0;
            if (minPlanned < Long.MAX_VALUE) {
                if (minPredicted == Long.MAX_VALUE || minPredicted == minPlanned) {
                    t1 = minPlanned;
                } else if (minPredicted < minPlanned) {
                    t1 = minPredicted;
                    t2 = minPlanned;
                } else {
                    t1 = minPlanned;
                    t2 = minPredicted;
                }

                t1 = t1 / 60000 + 1;
                if (t1 > lastRequestedMinTransferTime && t1 <= MAX_MIN_TRANSFER_TIME_MINUTES) {
                    attemptableTransferTimes.add((int) t1);
                    if (t2 > 0) {
                        t2 = t2 / 60000 + 1;
                        if (t2 <= MAX_MIN_TRANSFER_TIME_MINUTES)
                            attemptableTransferTimes.add((int) t2);
                    }
                }
            }
        }

        public boolean searchMorePossible() {
            if (attemptableTransferTimes == null)
                return false;

            if (attemptableTransferTimes.isEmpty())
                return false;

            return true;
        }

        public boolean prepareNextRound() {
            currentRound += 1;

            if (attemptableTransferTimes != null && !attemptableTransferTimes.isEmpty()) {
                lastRequestedMinTransferTime = attemptableTransferTimes.get(0);
                attemptableTransferTimes.remove(0);

                nextRequestData = reloadRequestData.clone();
                final TripOptions options = nextRequestData.options;
                nextRequestData.options = new TripOptions(
                        options.products,
                        options.optimize,
                        options.walkSpeed,
                        lastRequestedMinTransferTime,
                        options.accessibility,
                        options.flags);

                return true;
            }

            nextRequestData = null;
            return false;
        }

        public TripRequestData getNextRequestData() {
            if (currentRound <= 0)
                return reloadRequestData;

            return nextRequestData;
        }
    }
}
