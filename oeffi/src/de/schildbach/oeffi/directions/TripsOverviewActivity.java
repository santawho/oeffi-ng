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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Uninterruptibles;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryTripsRunnable.TripRequestData;
import de.schildbach.oeffi.directions.navigation.Navigator;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.QueryJourneyResult;
import de.schildbach.pte.dto.QueryTripsContext;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.PTDate;
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

import java.io.IOException;
import java.io.Serializable;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class TripsOverviewActivity extends OeffiActivity {
    private static final int DETAILS_NEW_NAVIGATION = 4711;

    public static class RenderConfig implements Serializable {
        private static final long serialVersionUID = 1567241493704301720L;

        public boolean isAlternativeConnectionSearch;
        public TimeSpec referenceTime;
        public JourneyRef feederJourneyRef;
        public JourneyRef connectionJourneyRef;
        public Trip prependTrip;
        public JourneyRef prependToJourneyRef;
        public Stop prependToStop;
        public boolean prependToStopIsLegDeparture;
        public int actionBarColor;
    }

    private static final String INTENT_EXTRA_NETWORK = TripsOverviewActivity.class.getName() + ".network";
    private static final String INTENT_EXTRA_RESULT = TripsOverviewActivity.class.getName() + ".result";
    private static final String INTENT_EXTRA_ARR_DEP = TripsOverviewActivity.class.getName() + ".arr_dep";
    private static final String INTENT_EXTRA_HISTORY_URI = TripsOverviewActivity.class.getName() + ".history";
    private static final String INTENT_EXTRA_RELOAD_REQUEST_DATA = TripsOverviewActivity.class.getName() + ".reqdata";
    private static final String INTENT_EXTRA_RENDERCONFIG = TripDetailsActivity.class.getName() + ".config";

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

    private ImageButton searchMoreButton;
    private @Nullable QueryTripsContext queryTripsContextEarlier;
    private @Nullable QueryTripsContext queryTripsContextLater;
    private TripsGallery barView;
    private SwipeRefreshLayout swipeRefresh;

    private final NavigableSet<TripInfo> trips = new TreeSet<>((tripC1, tripC2) -> {
        Trip trip1 = tripC1.trip;
        Trip trip2 = tripC2.trip;

        final String id1 = trip1.getUniqueId();
        final String id2 = trip2.getUniqueId();

        // if (trip1.equals(trip2))
        if (id1.equals(id2))
            return 0;

        if (tripC1.isAlternativelyFed) {
            final Trip baseTrip1 = tripC1.baseTrip;
            String baseId1 = baseTrip1.getUniqueId();
            if (!baseId1.equals(id2)) {
                if (tripC2.isAlternativelyFed) {
                    final Trip baseTrip2 = tripC2.baseTrip;
                    String baseId2 = baseTrip2.getUniqueId();
                    if (!baseId1.equals(baseId2)) {
                        trip1 = baseTrip1;
                        trip2 = baseTrip2;
                    }
                } else {
                    trip1 = baseTrip1;
                }
            }
        } else if (tripC2.isAlternativelyFed) {
            final Trip baseTrip2 = tripC2.baseTrip;
            String baseId2 = baseTrip2.getUniqueId();
            if (!baseId2.equals(id1)) {
                trip2 = baseTrip2;
            }
        }

        return ComparisonChain.start() //
                .compare(trip1.getFirstDepartureTime(), trip2.getFirstDepartureTime()) //
                .compare(trip1.getLastArrivalTime(), trip2.getLastArrivalTime()) //
                .compare(trip1.numChanges, trip2.numChanges, Ordering.natural().nullsLast()) //
                // trips with equal duration and change-overs, ...
                // make sure they are different, otherwise one of them would be dropped ...
                .compare(id1, id2) // ... and ensure same order every time (IDs are never equal)
                .result();
    });

    private boolean queryMoreTripsEnabled = false;
    private boolean queryMoreTripsRunning = false;
    private boolean reloadRequested = false;
    private boolean searchMoreRequested = false;

    private final Handler foregroundHandler = new Handler();
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

        // background thread
        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        final Intent intent = getIntent();
        renderConfig = (RenderConfig) intent.getSerializableExtra(INTENT_EXTRA_RENDERCONFIG);
        network = (NetworkId) intent.getSerializableExtra(INTENT_EXTRA_NETWORK);
        final QueryTripsResult result = preprocessResult((QueryTripsResult) intent.getSerializableExtra(INTENT_EXTRA_RESULT));
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

        final MyActionBar actionBar = getMyActionBar();
        setPrimaryColor(renderConfig.actionBarColor > 0 ? renderConfig.actionBarColor : R.color.bg_action_bar_directions_darkdefault);
        actionBar.setBack(v -> finish());
        actionBar.setCustomTitles(R.layout.directions_trip_overview_custom_title);
        if (searchMoreContext.canProvideSearchMore()) {
            searchMoreButton = actionBar.addButton(R.drawable.ic_search_more_white_24dp, R.string.directions_overview_search_more_title);
            setSearchMoreButtonEnabled(false);
            searchMoreButton.setOnClickListener(view -> {
                setSearchMoreButtonEnabled(false);
                searchMoreRequested = true;
                postCheckMoreRunnable(false);
            });
        }
        actionBar.addProgressButton().setOnClickListener(v -> requestReload());

        swipeRefresh = findViewById(R.id.trips_refresh);
        swipeRefresh.setOnRefreshListener(this::requestReload);

        barView = findViewById(R.id.trips_bar_view);
        barView.setRenderConfig(renderConfig);
        barView.setOnItemClickListener((parent, v, position, id) -> {
            final TripInfo tripInfo = (TripInfo) barView.getAdapter().getItem(position);
            if (tripInfo != null) {
                final Trip trip = tripInfo.trip;
                if (trip.legs != null) {
                    final TripDetailsActivity.RenderConfig config = new TripDetailsActivity.RenderConfig();
                    config.isAlternativeConnectionSearch = renderConfig.isAlternativeConnectionSearch;
                    config.queryTripsRequestData = reloadRequestData;
                    if (config.isAlternativeConnectionSearch) {
                        TripDetailsActivity.startForResult(TripsOverviewActivity.this, DETAILS_NEW_NAVIGATION, network, trip, config);
                    } else {
                        TripDetailsActivity.start(TripsOverviewActivity.this, network, trip, config);
                    }

                    final PTDate firstPublicLegDepartureTime = trip.getFirstPublicLegDepartureTime();
                    final PTDate lastPublicLegArrivalTime = trip.getLastPublicLegArrivalTime();

                    // save last trip to history
                    if (firstPublicLegDepartureTime != null && lastPublicLegArrivalTime != null && historyUri != null) {
                        final ContentValues values = new ContentValues();
                        values.put(QueryHistoryProvider.KEY_LAST_DEPARTURE_TIME, firstPublicLegDepartureTime.getTime());
                        values.put(QueryHistoryProvider.KEY_LAST_ARRIVAL_TIME, lastPublicLegArrivalTime.getTime());
                        values.put(QueryHistoryProvider.KEY_LAST_TRIP, Objects.serialize(trip));
                        getContentResolver().update(historyUri, values, null, null);
                    }
                }
            }
        });
        barView.setOnScrollListener(() -> {
//            log.info("barView.onScrollListener -> foregroundHandler.post(checkMoreRunnable)");
            postCheckMoreRunnable(false);
        });

        final View disclaimerView = findViewById(R.id.directions_trip_overview_disclaimer_group);
        ViewCompat.setOnApplyWindowInsetsListener(disclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });

        processInitialResult(result, searchMoreContext);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DETAILS_NEW_NAVIGATION && resultCode == RESULT_OK) {
            // user has started another navigation
            // then close this overview (and return to initial navigation in the background)
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // regular refresh
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        queryMoreTripsEnabled = true;
        barView.invalidate();

        // delay because GUI is not initialized immediately
//        log.info("onStart -> foregroundHandler.postDelayed(checkMoreRunnable, 50)");
        postCheckMoreRunnable(true);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(tickReceiver);

        queryMoreTripsEnabled = false;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // cancel background thread
        backgroundHandler = null;
        foregroundHandler.removeCallbacks(checkMoreRunnable);
        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    private void requestReload() {
        setSearchMoreButtonEnabled(false);
        reloadRequested = true;
        postCheckMoreRunnable(false);
    }

    public void postCheckMoreRunnable(final boolean delayed) {
//        log.info("fetch more");
        foregroundHandler.postDelayed(checkMoreRunnable, delayed ? 50 : 0);
    }

    private final Runnable checkMoreRunnable = new Runnable() {
        public void run() {
            if (!queryMoreTripsEnabled || queryMoreTripsRunning || backgroundHandler == null)
                return;

            final int positionOffset = queryTripsContextEarlier != null && queryTripsContextEarlier.canQueryEarlier() ? 0 : 1;
            final int lastVisiblePosition = barView.getLastVisiblePosition() - positionOffset;
            final int firstVisiblePosition = barView.getFirstVisiblePosition() - positionOffset;

            Runnable queryTripsRunnable = null;
            if (reloadRequested) {
                reloadRequested = false;
                searchMoreContext.reset();
                queryTripsRunnable = new QueryMoreTripsRunnable(queryTripsContextLater, false, false, true, searchMoreContext);
            } else if (searchMoreRequested) {
                searchMoreRequested = false;
                final SearchMoreContext.NextRoundInfo nextRoundInfo = searchMoreContext.prepareNextRound(TripsOverviewActivity.this);
                if (nextRoundInfo != null) {
                    queryTripsRunnable = new QueryMoreTripsRunnable(queryTripsContextLater, false, false, false, searchMoreContext);
                    if (nextRoundInfo.infoText != null)
                        runOnUiThread(() -> new Toast(TripsOverviewActivity.this).toast(nextRoundInfo.infoText));
                }
            } else if (queryTripsContextLater != null && queryTripsContextLater.canQueryLater()
                    && (lastVisiblePosition == AdapterView.INVALID_POSITION || lastVisiblePosition + 1 >= trips.size())) {
                queryTripsRunnable = new QueryMoreTripsRunnable(queryTripsContextLater, false, true, false, searchMoreContext);
            } else if (queryTripsContextEarlier != null && queryTripsContextEarlier.canQueryEarlier()
                    && (firstVisiblePosition == AdapterView.INVALID_POSITION || firstVisiblePosition <= 0)) {
                queryTripsRunnable = new QueryMoreTripsRunnable(queryTripsContextEarlier, true, false, false, searchMoreContext);
            } else if (searchMoreContext.searchMorePossible()) {
                runOnUiThread(() -> setSearchMoreButtonEnabled(true));
            }

            if (queryTripsRunnable != null && backgroundHandler != null) {
                queryMoreTripsRunning = true;
//                log.info("backgroundHandler.post(queryTripsRunnable)");
                backgroundHandler.post(queryTripsRunnable);
            }
        }
    };

    private class QueryMoreTripsRunnable implements Runnable {
        final private MyActionBar actionBar = getMyActionBar();
        final private QueryTripsContext context;
        final private boolean earlier, later, refreshPrepend;
        final private SearchMoreContext searchMoreContext;

        public QueryMoreTripsRunnable(
                final QueryTripsContext context,
                final boolean earlier, final boolean later, final boolean refreshPrepend,
                final SearchMoreContext searchMoreContext) {
            this.context = context;
            this.earlier = earlier;
            this.later = later;
            this.refreshPrepend = refreshPrepend;
            this.searchMoreContext = searchMoreContext;
        }

        public void run() {
            runOnUiThread(() -> actionBar.startProgress());

            boolean foregroundRunning = false;
            try {
                if (refreshPrepend)
                    setupPrepend(true);

                foregroundRunning = doRequest();
            } finally {
                if (!foregroundRunning) {
                    queryMoreTripsRunning = false;
                    runOnUiThread(() -> {
                        swipeRefresh.setRefreshing(false);
                        actionBar.stopProgress();
                    });
                }
            }
        }

        private boolean doRequest() {
            int tries = 0;

            while (true) {
                tries++;

                try {
                    final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
                    final QueryTripsResult netResult;
                    if (earlier || later) {
                        netResult = networkProvider.queryMoreTrips(context, later);
                    } else {
                        final TripRequestData requestData = searchMoreContext.getNextRequestData();
                        netResult = networkProvider.queryTrips(
                                requestData.from,
                                requestData.via,
                                requestData.to,
                                requestData.date,
                                requestData.dep,
                                requestData.options);
                    }

                    final QueryTripsResult result = (netResult.status == QueryTripsResult.Status.OK)
                            ? preprocessResult(netResult)
                            : netResult;

                    runOnUiThread(() -> {
                        log.debug("Got {} ({})", result.toShortString(), later ? "later" : "earlier");
                        final int countNew;
                        if (result.status == QueryTripsResult.Status.OK) {
                            countNew = processResult(result, earlier, later, searchMoreContext);
                        } else if (result.status == QueryTripsResult.Status.NO_TRIPS) {
                            countNew = 0;
                            // ignore
                        } else {
                            countNew = 0;
                            new Toast(TripsOverviewActivity.this).toast(R.string.toast_network_problem);
                        }
                        queryMoreTripsRunning = false;

                        swipeRefresh.setRefreshing(false);
                        actionBar.stopProgress();

                        // fetch more
                        if (countNew > 0)
                            postCheckMoreRunnable(true);
                    });
                    return true;
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
                            // DO NOT throw an exception here, because we are trying to query MORE trips.
                            // This might fail, if the query-next-context has timed out.
                            // Actually, there is a SessionExpiredException, but almost all network providers in PTE
                            // do not throw this, as they do not detect the cause of the situation

                            // throw new RuntimeException(message, x);

                            // Instead, show a warning, so the user can reload completely.
                            runOnUiThread(() -> new Toast(TripsOverviewActivity.this).longToast(R.string.toast_session_expired));
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
            return false;
        }
    }

    private QueryTripsResult preprocessResult(final QueryTripsResult in) {
        QueryTripsResult out = in;
        if (renderConfig.prependTrip != null)
            out = prependTripToAllTrips(in);
        return out;
    }

    private List<Trip.Leg> prependLegs;
    private int prependNumChanges;

    private void setupPrepend(final boolean doRefresh) {
        prependLegs = new LinkedList<>();
        if (renderConfig == null || renderConfig.prependTrip == null) {
            prependNumChanges = 0;
            return;
        }
        final JourneyRef prependToJourneyRef = renderConfig.prependToJourneyRef;
        Trip.Public foundLeg = null;
        prependNumChanges = -1;
        final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
        final Date now = new Date();
        for (final Trip.Leg leg : renderConfig.prependTrip.legs) {
            if (leg instanceof Trip.Public) {
                Trip.Public publicLeg = (Trip.Public) leg;
                if (doRefresh) {
                    try {
                        final QueryJourneyResult result = networkProvider.queryJourney(publicLeg.journeyRef);
                        if (result != null) {
                            switch (result.status) {
                                case OK:
                                    if (result.journeyLeg != null)
                                        publicLeg = Navigator.buildUpdatedLeg(publicLeg, result.journeyLeg, now);
                                    break;
                                case NO_JOURNEY:
                                case SERVICE_DOWN:
                                    break;
                            }
                        }
                    } catch (IOException e) {
                        log.error("unable to refresh leg", e);
                    }
                }
                if (prependToJourneyRef.equals(publicLeg.journeyRef)) {
                    foundLeg = publicLeg;
                    break;
                }
                prependNumChanges += 1;
            }
            prependLegs.add(leg);
        }
        if (foundLeg == null) {
            prependLegs.clear();
            prependNumChanges = 0;
        }
        final Stop prependToStop = renderConfig.prependToStop;
        final String prependToId = prependToStop.location.id;
        // if the stop is the departure of the found leg, then do not include this leg
        if (!(prependToId.equals(foundLeg.departureStop.location.id)
                && prependToStop.plannedDepartureTime.equals(foundLeg.departureStop.plannedDepartureTime))) {
            final Stop newArrivalStop = new Stop(
                    prependToStop.location,
                    prependToStop.plannedArrivalTime,
                    prependToStop.predictedArrivalTime,
                    prependToStop.plannedArrivalPosition,
                    prependToStop.predictedArrivalPosition,
                    prependToStop.arrivalCancelled,
                    null,
                    null,
                    null,
                    null,
                    false);
            final List<Stop> newIntermediateStops = new LinkedList<>();
            for (final Stop intermediateStop : foundLeg.intermediateStops) {
                if (prependToId.equals(intermediateStop.location.id)
                        && prependToStop.plannedArrivalTime.equals(intermediateStop.plannedArrivalTime)) {
                    break;
                }
                newIntermediateStops.add(intermediateStop);
            }
            prependLegs.add(new Trip.Public(
                    foundLeg.line,
                    foundLeg.destination,
                    foundLeg.departureStop,
                    newArrivalStop,
                    newIntermediateStops,
                    foundLeg.path,
                    foundLeg.message,
                    foundLeg.journeyRef,
                    foundLeg.loadedAt));
            prependNumChanges += 1;
        }
    }

    private QueryTripsResult prependTripToAllTrips(final QueryTripsResult in) {
        if (prependLegs == null)
            setupPrepend(false);
        if (in.trips == null)
            return in;
        final List<Trip> newTrips = new LinkedList<>();
        for (final Trip inTrip : in.trips) {
            final Trip newTrip = prependToTrip(inTrip);
            if (newTrip != null)
                newTrips.add(newTrip);
        }
        if (newTrips.isEmpty()) {
            return new QueryTripsResult(in.header, QueryTripsResult.Status.NO_TRIPS);
        }
        return new QueryTripsResult(
                in.header,
                in.queryUri,
                in.from,
                in.via,
                in.to,
                in.context,
                newTrips);
    }

    private Trip prependToTrip(final Trip trip) {
        final Trip.Public firstPublicLeg = trip.getFirstPublicLeg();
        if (firstPublicLeg == null)
            return null;
        final PTDate departureTime = firstPublicLeg.getDepartureTime();
        if (departureTime == null)
            return null;
        final PTDate prependTime = renderConfig.prependToStopIsLegDeparture
                ? renderConfig.prependToStop.plannedDepartureTime
                : renderConfig.prependToStop.plannedArrivalTime;
        if (prependTime != null) {
            final long diff = departureTime.getTime() - prependTime.getTime();
            if (diff < -300000) // 5 mins tolerance
                return null;
        }

        final List<Trip.Leg> newLegs = new LinkedList<>();

        final int prependLegsLastIndex = prependLegs.size() - 1;
        for (int i = 0; i < prependLegsLastIndex; i += 1)
            newLegs.add(prependLegs.get(i));
        final Trip.Leg lastPrependLeg = prependLegsLastIndex < 0 ? null : prependLegs.get(prependLegsLastIndex);

        final List<Trip.Leg> tripLegs = trip.legs;
        final Trip.Leg firstContinuationLeg = tripLegs.get(0);

        int newNumChanges = prependNumChanges + trip.numChanges;
        if (firstPublicLeg.journeyRef.equals(renderConfig.feederJourneyRef)
                && firstContinuationLeg instanceof Trip.Public
                && lastPrependLeg instanceof Trip.Public) {
            // continue with same journey, then combine both legs into one
            final Trip.Public feedLeg = (Trip.Public) lastPrependLeg;
            final Trip.Public contLeg = (Trip.Public) firstContinuationLeg;
            final Stop feedStop = feedLeg.arrivalStop;
            final Stop contStop = contLeg.departureStop;
            final List<Stop> intermediateStops = new ArrayList<>();
            if (feedLeg.intermediateStops != null)
                intermediateStops.addAll(feedLeg.intermediateStops);
            intermediateStops.add(new Stop(
                    contStop.location,
                    feedStop.plannedArrivalTime, feedStop.predictedArrivalTime,
                    feedStop.plannedArrivalPosition, feedStop.predictedArrivalPosition,
                    feedStop.arrivalCancelled,
                    contStop.plannedDepartureTime, contStop.predictedDepartureTime,
                    contStop.plannedDeparturePosition, contStop.predictedDeparturePosition,
                    contStop.departureCancelled));
            if (contLeg.intermediateStops != null)
                intermediateStops.addAll(contLeg.intermediateStops);
            final List<Point> path = new ArrayList<>();
            if (feedLeg.path != null)
                path.addAll(feedLeg.path);
            if (contLeg.path != null)
                path.addAll(contLeg.path);
            newLegs.add(new Trip.Public(
                    feedLeg.line,
                    contLeg.destination,
                    feedLeg.departureStop,
                    contLeg.arrivalStop,
                    intermediateStops,
                    path.isEmpty() ? null : path,
                    contLeg.message,
                    contLeg.journeyRef,
                    contLeg.loadedAt));
        } else {
            newLegs.add(lastPrependLeg);
            newLegs.add(firstContinuationLeg);
            newNumChanges += 1;
        }

        for (int i = 1; i < tripLegs.size(); i += 1)
            newLegs.add(tripLegs.get(i));

        return new Trip(
                trip.loadedAt,
                trip.getId(),
                trip.tripRef,
                trip.from,
                trip.to,
                newLegs,
                trip.fares,
                trip.capacity,
                newNumChanges);
    }

    private int processInitialResult(
            final QueryTripsResult result,
            final SearchMoreContext searchMoreContext) {
        // update header
        if (result.from != null)
            ((TextView) findViewById(R.id.directions_trip_overview_custom_title_from))
                    .setText(Formats.fullLocationName(result.from));
        findViewById(R.id.directions_trip_overview_custom_title_via_row)
                .setVisibility(result.via != null ? View.VISIBLE : View.GONE);
        if (result.via != null)
            ((TextView) findViewById(R.id.directions_trip_overview_custom_title_via)).setText(Formats.fullLocationName(result.via));
        if (result.to != null)
            ((TextView) findViewById(R.id.directions_trip_overview_custom_title_to)).setText(Formats.fullLocationName(result.to));

        // update server product
        if (result.header != null) {
            final TextView serverProductView = findViewById(R.id.trips_server_product);
            updateDisclaimerSource(serverProductView, network, null);
//            serverProductView.setText(product(result.header));
//            serverProductView.setVisibility(View.VISIBLE);
        }

        return processResult(result, false, false, searchMoreContext);
    }

    private int processResult(
            final QueryTripsResult result,
            final boolean earlier, final boolean later,
            final SearchMoreContext searchMoreContext) {
        final boolean earlierOrLater = earlier || later;
        final boolean initial = searchMoreContext.currentRound == 0 && !earlierOrLater;
        if (initial) {
            trips.clear();
            searchMoreContext.reset();
        }

        final boolean showAccessibility = !NetworkProvider.Accessibility.NEUTRAL.equals(prefsGetAccessibility());
        final boolean showBicycleCarriage = prefsIsBicycleTravel();

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
        for (final Trip trip : result.trips) {
            final TripInfo tripInfo = searchMoreContext.newTripInfos(trip, earlierOrLater);
            if (tripInfo != null && trips.add(tripInfo))
                countNew += 1;
        }
        if (!earlierOrLater)
            searchMoreContext.tellNewTripsAddedAndAddMoreIfNecessary(countNew, trips);

        if (countNew > 0) {
            // redraw
            barView.setTrips(
                    new ArrayList<>(trips),
                    result.context != null && result.context.canQueryLater(),
                    result.context != null && result.context.canQueryEarlier(),
                    showAccessibility, showBicycleCarriage);

            // initial cursor positioning
            if (initial && !trips.isEmpty())
                barView.setSelection(searchMoreContext.departureBased ? 1 : trips.size() - 1);
        }


        // save context for next request
        if (!earlier)
            queryTripsContextLater = result.context;

        if (!later)
            queryTripsContextEarlier = result.context;

        return countNew;
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
        private final List<Integer> attemptableTransferTimes = new LinkedList<>();
        private int lastRequestedMinTransferTime = 0;
        private final Map<String, Location> firstTransferStations = new HashMap<>();
        private String lastRequestedFirstTransferStationId = null;
        private final Map<String, List<Trip>> tripsTofirstTransferStations = new HashMap<>();
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
            attemptableTransferTimes.clear();
            final Integer minTransferTimeMinutes = reloadRequestData.options.minTransferTimeMinutes;
            lastRequestedMinTransferTime = minTransferTimeMinutes == null ? 0 : minTransferTimeMinutes;
            firstTransferStations.clear();
            lastRequestedFirstTransferStationId = null;
            tripsTofirstTransferStations.clear();
        }

        public boolean canProvideSearchMore() {
            if (!provider.hasCapabilities(NetworkProvider.Capability.MIN_TRANSFER_TIMES))
                return false;

            // return departureBased;
            return true;
        }

        public TripInfo newTripInfos(final Trip trip, final boolean isEarlierOrLater) {
            final ArrayList<TripInfo> list = new ArrayList<>();
            if (!isEarlierOrLater && lastRequestedFirstTransferStationId != null) {
                // a new short trip was found to one of the first transfer stations
                List<Trip> tripsForStation = tripsTofirstTransferStations.get(lastRequestedFirstTransferStationId);
                if (tripsForStation == null) {
                    tripsForStation = new LinkedList<>();
                    tripsTofirstTransferStations.put(lastRequestedFirstTransferStationId, tripsForStation);
                }
                tripsForStation.add(trip);
                return null;
            }

            final TripInfo tripInfo = new TripInfo(trip);
            tripInfo.isEarlierOrLater = isEarlierOrLater;
            tripInfo.addedInRound = isEarlierOrLater ? 0 : currentRound;
            return tripInfo;
        }

        public void tellNewTripsAddedAndAddMoreIfNecessary(final int countNew, final NavigableSet<TripInfo> trips) {
            if (lastRequestedFirstTransferStationId != null) {
                // search was for short trip to one of the first transfer stations
                // add forged trips from existing trips and this one if applicable
                addForgedTripsWithTripsToStation(
                        lastRequestedFirstTransferStationId,
                        tripsTofirstTransferStations.get(lastRequestedFirstTransferStationId),
                        trips);
            } else {
                // search was for full trips
                setupAvailableNextSearchByIncreasingTransferTimes(trips);
            }
        }

        private void setupAvailableNextSearchByIncreasingTransferTimes(final NavigableSet<TripInfo> trips) {
            attemptableTransferTimes.clear();
            long minPlanned = Long.MAX_VALUE;
            long minPredicted = Long.MAX_VALUE;
            final long limitLast = (long) lastRequestedMinTransferTime * 60000;
            int n = 0;
            for (final TripInfo tripInfo : trips) {
                if (tripInfo.addedInRound < currentRound || tripInfo.isEarlierOrLater)
                    continue;

                final Trip trip = tripInfo.trip;
                Trip.Public prevLeg = null;
                int publicLegIndex = 0;
                for (final Trip.Leg aLeg : trip.legs) {
                    if (aLeg instanceof Trip.Public) {
                        final Trip.Public leg = (Trip.Public) aLeg;
                        publicLegIndex += 1;
                        if (publicLegIndex == 2) {
                            final Location location = leg.departureStop.location;
                            final String locationId = location.id;
                            if (!tripsTofirstTransferStations.containsKey(locationId))
                                firstTransferStations.put(locationId, location);
                        }
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

        private void addForgedTripsWithTripsToStation(
                final String transferStationId,
                final List<Trip> tripsToStation,
                final NavigableSet<TripInfo> trips) {
            final List<TripInfo> newTrips = new LinkedList<>();
            for (final TripInfo tripInfo: trips) {
                if (tripInfo.isEarlierOrLater)
                    continue;

                final Trip baseTrip = tripInfo.trip;
                Trip.Public prevLeg = null;
                for (final Trip.Leg aLeg: baseTrip.legs) {
                    if (!(aLeg instanceof Trip.Public))
                        continue;

                    final Trip.Public leg = (Trip.Public) aLeg;
                    if (prevLeg != null) {
                        if (transferStationId.equals(leg.departureStop.location.id)) {
                            final PTDate departureTime = leg.departureStop.getDepartureTime();
                            for (final Trip feedingTrip: tripsToStation) {
                                final Trip.Leg lastLeg = feedingTrip.legs.get(feedingTrip.legs.size() - 1);
                                final Trip.Public lastPublicLeg;
                                final Trip.Individual connectingWalkLeg;
                                final PTDate arrivalTime;
                                if (lastLeg instanceof Trip.Public) {
                                    lastPublicLeg = (Trip.Public) lastLeg;
                                    arrivalTime = lastPublicLeg.arrivalStop.plannedArrivalTime;
                                } else if (lastLeg instanceof Trip.Individual){
                                    connectingWalkLeg = (Trip.Individual) lastLeg;
                                    final PTDate walkArrivalTime = connectingWalkLeg.arrivalTime;
                                    final Trip.Leg secondLastLeg = feedingTrip.legs.get(feedingTrip.legs.size() - 2);
                                    if (!(secondLastLeg instanceof Trip.Public))
                                        continue;
                                    arrivalTime = walkArrivalTime != null ? walkArrivalTime
                                            : ((Trip.Public) secondLastLeg).arrivalStop.plannedArrivalTime;
                                } else {
                                    continue;
                                }
                                final long diff = (departureTime.getTime() - arrivalTime.getTime()) / 60000;
                                if (diff < 0 || diff > MAX_MIN_TRANSFER_TIME_MINUTES)
                                    continue;
                                final Trip newTrip = makeNewTripByReplacingInitialLegsFromFeedingTrip(baseTrip, leg, feedingTrip);
                                final TripInfo newTripInfo = new TripInfo(newTrip);
                                newTripInfo.addedInRound = currentRound;
                                newTripInfo.isAlternativelyFed = true;
                                newTripInfo.baseTrip = baseTrip;
                                newTrips.add(newTripInfo);
                            }
                        }
                        break;
                    }
                    prevLeg = leg;
                }
            }
            for (final TripInfo newTrip : newTrips) {
                if (!trips.contains(newTrip))
                    trips.add(newTrip);
            }
        }

        private Trip makeNewTripByReplacingInitialLegsFromFeedingTrip(
                final Trip baseTrip,
                final Trip.Public firstLegToKeep,
                final Trip feedingTrip) {
            final List<Trip.Leg> legs = new LinkedList<>(feedingTrip.legs);
            boolean copy = false;
            for (final Trip.Leg baseLeg : baseTrip.legs) {
                if (baseLeg == firstLegToKeep)
                    copy = true;
                if (copy)
                    legs.add(baseLeg);
            }

            return new Trip(
                    baseTrip.loadedAt,
                    null,
                    null,
                    feedingTrip.from,
                    baseTrip.to,
                    legs,
                    null, null,
                    feedingTrip.numChanges + 1 + baseTrip.numChanges);
        }

        public boolean searchMorePossible() {
            if (!firstTransferStations.isEmpty())
                return true;

            if (!attemptableTransferTimes.isEmpty())
                return true;

            return false;
        }

        public static class NextRoundInfo {
            public String infoText;
        }

        public NextRoundInfo prepareNextRound(final Context context) {
            currentRound += 1;

            if (departureBased && !firstTransferStations.isEmpty()) {
                final Location location = firstTransferStations.values().iterator().next();
                lastRequestedFirstTransferStationId = location.id;
                firstTransferStations.remove(lastRequestedFirstTransferStationId);

                nextRequestData = reloadRequestData.clone();
                nextRequestData.to = location;
                nextRequestData.via = null;

                final NextRoundInfo nextRoundInfo = new NextRoundInfo();
                nextRoundInfo.infoText = context.getString(R.string.toast_trip_additional_feeder, location.uniqueShortName());
                return nextRoundInfo;
            } else {
                lastRequestedFirstTransferStationId = null;
            }

            if (!attemptableTransferTimes.isEmpty()) {
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

                final NextRoundInfo nextRoundInfo = new NextRoundInfo();
                nextRoundInfo.infoText = context.getString(R.string.toast_trip_additional_transfertime, lastRequestedMinTransferTime);
                return nextRoundInfo;
            }

            nextRequestData = null;
            return null;
        }

        public TripRequestData getNextRequestData() {
            if (currentRound <= 0) {
                final Date now = new Date();
                if (reloadRequestData.date.after(now))
                    return reloadRequestData;

                final TripRequestData clone = reloadRequestData.clone();
                clone.date = now;
                return clone;
            }

            return nextRequestData;
        }
    }
}
