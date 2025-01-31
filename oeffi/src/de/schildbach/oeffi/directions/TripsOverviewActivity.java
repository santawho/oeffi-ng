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
import de.schildbach.oeffi.directions.QueryTripsRunnable.ReloadRequestData;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.QueryTripsContext;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.Trip;
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

    public static void start(final Context context, final NetworkId network, final TimeSpec.DepArr depArr,
            final QueryTripsResult result, final Uri historyUri, final ReloadRequestData reloadRequestData) {
        start(context, network, depArr, result, historyUri, reloadRequestData, new RenderConfig());
    }

    public static void start(final Context context, final NetworkId network, final TimeSpec.DepArr depArr,
            final QueryTripsResult result, final Uri historyUri, final ReloadRequestData reloadRequestData,
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
    private ReloadRequestData reloadRequestData;
    private RenderConfig renderConfig;

    private @Nullable QueryTripsContext context;
    private TripsGallery barView;
    private final NavigableSet<Trip> trips = new TreeSet<>((trip1, trip2) -> {
        if (trip1.equals(trip2))
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
        this.reloadRequestData = (ReloadRequestData) intent.getSerializableExtra(INTENT_EXTRA_RELOAD_REQUEST_DATA);
        final String historyUriStr = intent.getStringExtra(INTENT_EXTRA_HISTORY_URI);
        final Uri historyUri = historyUriStr != null ? Uri.parse(historyUriStr) : null;

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
        actionBar.addProgressButton().setOnClickListener(v -> {
            reloadRequested = true;
            handler.post(checkMoreRunnable);
        });

        barView = findViewById(R.id.trips_bar_view);
        barView.setRenderConfig(renderConfig);
        barView.setOnItemClickListener((parent, v, position, id) -> {
            final Trip trip = (Trip) barView.getAdapter().getItem(position);

            if (trip != null && trip.legs != null) {
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
        });
        barView.setOnScrollListener(() -> handler.post(checkMoreRunnable));

        final View disclaimerView = findViewById(R.id.directions_trip_overview_disclaimer_group);
        ViewCompat.setOnApplyWindowInsetsListener(disclaimerView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });

        processResult(result, true, dep);
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
                    queryTripsRunnable = new QueryMoreTripsRunnable(context, true, null);
                } else if (context != null && context.canQueryEarlier()
                        && (firstVisiblePosition == AdapterView.INVALID_POSITION || firstVisiblePosition <= 0)) {
                    queryTripsRunnable = new QueryMoreTripsRunnable(context, false, null);
                } else if (reloadRequested) {
                    reloadRequested = false;
                    queryTripsRunnable = new QueryMoreTripsRunnable(context, true, reloadRequestData);
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
        final private boolean later;
        final private ReloadRequestData reloadRequestData;

        public QueryMoreTripsRunnable(final QueryTripsContext context, final boolean later, final ReloadRequestData reloadRequestData) {
            this.context = context;
            this.later = later;
            this.reloadRequestData = reloadRequestData;
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

        private final void doRequest() {
            int tries = 0;

            while (true) {
                tries++;

                try {
                    final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
                    final QueryTripsResult result = (reloadRequestData != null)
                        ? networkProvider.queryTrips(
                                reloadRequestData.from,
                                reloadRequestData.via,
                                reloadRequestData.to,
                                reloadRequestData.date,
                                reloadRequestData.dep,
                                reloadRequestData.options)
                        : networkProvider.queryMoreTrips(
                                context,
                                later);

                    runOnUiThread(() -> {
                        log.debug("Got {} ({})", result.toShortString(), later ? "later" : "earlier");
                        if (result.status == QueryTripsResult.Status.OK) {
                            processResult(result, reloadRequestData != null, later);

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

    private void processResult(final QueryTripsResult result, final boolean initial, final boolean later) {
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

        if (initial) {
            trips.clear();
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
        trips.addAll(result.trips);

        // redraw
        barView.setTrips(new ArrayList<>(trips), result.context != null && result.context.canQueryLater(),
                result.context != null && result.context.canQueryEarlier());

        // initial cursor positioning
        if (initial && !trips.isEmpty())
            barView.setSelection(later ? 1 : trips.size() - 1);

        // save context for next request
        context = result.context;
    }

    private static String nameAndPlace(final Location location) {
        return location.place != null ? (location.place + ", " + location.name) : location.name;
    }
}
