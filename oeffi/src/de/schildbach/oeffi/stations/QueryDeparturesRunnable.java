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

import android.content.SharedPreferences;
import android.os.Handler;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.exception.RateLimitException;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.exception.BlockedException;
import de.schildbach.pte.exception.InternalErrorException;
import de.schildbach.pte.exception.NotFoundException;
import de.schildbach.pte.exception.ParserException;
import de.schildbach.pte.exception.UnexpectedRedirectException;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public abstract class QueryDeparturesRunnable implements Runnable {
    protected final Handler handler;

    protected final NetworkProvider networkProvider;
    protected final Location station;
    protected final NetworkProvider.EquivalentStationsMode equivsMode;
    protected final Date fromTime;
    protected final boolean arrivals;
    private final Set<Product> products;
    protected final int maxEvents;
    protected final boolean includeLinesAtStation;


    protected static final Logger log = LoggerFactory.getLogger(QueryDeparturesRunnable.class);

    public QueryDeparturesRunnable(
            final Handler handler,
            final NetworkProvider networkProvider,
            final Location station,
            final NetworkProvider.EquivalentStationsMode equivsMode,
            final Date time,
            final boolean arrivals,
            final Set<Product> products,
            final int maxEvents,
            final boolean includeLinesAtStation) {
        this.handler = handler;
        this.networkProvider = networkProvider;
        this.station = station;
        this.equivsMode = equivsMode;
        this.fromTime = time != null ? time : new Date();
        this.arrivals = arrivals;
        this.maxEvents = maxEvents;
        this.includeLinesAtStation = includeLinesAtStation;
        this.products = products;
    }

    private static long getTimeOffset() {
        final Application application = Application.getInstance();
        final SharedPreferences prefs = application.getSharedPreferences();
        final String offsetText = prefs.getString(
                "station_events_time_offset_minutes",
                application.getString(R.string.default_station_events_time_offset_minutes));
        long offset;
        try {
            offset = (long) (Float.parseFloat(offsetText) * 60000f);
        } catch (final NumberFormatException nfe) {
            offset = 0;
        }
        return -offset;
    }

    public void run() {
        postOnPreExecute();

        try {
            doRequest();
        } finally {
            postOnPostExecute();
        }
    }

    private void doRequest() {
        int tries = 0;

        while (true) {
            tries++;

            try {
                final QueryDeparturesResult result = networkProvider.queryStationBoard(
                        station, new Date(fromTime.getTime() + getTimeOffset()), arrivals, maxEvents,
                        equivsMode, products, includeLinesAtStation);

                postOnResult(result);
                break;
            } catch (final UnexpectedRedirectException x) {
                postOnRedirect(x.getRedirectedUrl());
                break;
            } catch (final BlockedException x) {
                postOnBlocked(x.getUrl());
                break;
            } catch (final RateLimitException x) {
                postOnRateLimit(x.getUrl());
                break;
            } catch (final InternalErrorException x) {
                postOnInternalError(x.getUrl());
                break;
            } catch (final ParserException x) {
                postOnParserException(x.getMessage());
                break;
            } catch (final IOException x) {
                log.info("IO problem while querying departures on " + station.id + " " + networkProvider + " (try "
                        + tries + ")", x);

                if (tries >= Constants.MAX_TRIES_ON_IO_PROBLEM) {
                    if (x instanceof SocketTimeoutException || x instanceof UnknownHostException
                            || x instanceof SocketException || x instanceof NotFoundException
                            || x instanceof SSLException)
                        postOnInputOutputError(x);

                    break;
                }

                try { TimeUnit.SECONDS.sleep(tries); } catch (InterruptedException ix) {}

                // try again
                continue;
            } catch (final RuntimeException x) {
                final String message = "uncategorized problem while querying departures on " + station.id + " "
                        + networkProvider;
                throw new RuntimeException(message, x);
            }
        }
    }

    private void postOnPreExecute() {
        handler.post(() -> onPreExecute());
    }

    protected void onPreExecute() {
    }

    private void postOnPostExecute() {
        handler.post(() -> onPostExecute());
    }

    protected void onPostExecute() {
    }

    private void postOnResult(final QueryDeparturesResult result) {
        handler.post(() -> onResult(result));
    }

    protected abstract void onResult(QueryDeparturesResult result);

    private void postOnRedirect(final HttpUrl url) {
        handler.post(() -> onRedirect(url));
    }

    protected void onRedirect(final HttpUrl url) {
        onAllErrors();
    }

    private void postOnBlocked(final HttpUrl url) {
        handler.post(() -> onBlocked(url));
    }

    protected void onBlocked(final HttpUrl url) {
        onAllErrors();
    }

    private void postOnRateLimit(final HttpUrl url) {
        handler.post(() -> onRateLimit());
    }

    protected void onRateLimit() {
    }

    private void postOnInternalError(final HttpUrl url) {
        handler.post(() -> onInternalError(url));
    }

    protected void onInternalError(final HttpUrl url) {
        onAllErrors();
    }

    private void postOnParserException(final String message) {
        handler.post(() -> onParserException(message));
    }

    protected void onParserException(final String message) {
        onAllErrors();
    }

    private void postOnInputOutputError(final IOException x) {
        handler.post(() -> onInputOutputError(x));
    }

    protected void onInputOutputError(final IOException x) {
        onAllErrors();
    }

    protected void onAllErrors() {
    }

    public static int statusMsgResId(final QueryDeparturesResult.Status status) {
        switch (status) {
        case SERVICE_DOWN:
            return R.string.result_status_service_down;

        case INVALID_STATION:
            return R.string.result_status_invalid_station;

        default:
            throw new IllegalArgumentException(status.name());
        }
    }
}
