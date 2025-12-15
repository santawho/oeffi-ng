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

import android.os.Handler;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.pte.NetworkProvider;
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
import java.util.concurrent.TimeUnit;

public abstract class QueryDeparturesRunnable implements Runnable {
    private final Handler handler;

    private final NetworkProvider networkProvider;
    private final String stationId;
    private final Date fromTime;
    private final int maxDepartures;

    private static final Logger log = LoggerFactory.getLogger(QueryDeparturesRunnable.class);

    public QueryDeparturesRunnable(
            final Handler handler, final NetworkProvider networkProvider,
            final String stationId, final Date time, final int maxDepartures) {
        this.handler = handler;
        this.networkProvider = networkProvider;
        this.stationId = stationId;
        this.fromTime = time != null ? time : new Date();
        this.maxDepartures = maxDepartures;
    }

    public void run() {
        postOnPreExecute();

        try {
            doRequest();
        } finally {
            postOnPostExecute();
        }
    }

    private final void doRequest() {
        int tries = 0;

        while (true) {
            tries++;

            try {
                final QueryDeparturesResult result = networkProvider.queryDepartures(
                        stationId, fromTime, maxDepartures, true);

                postOnResult(result);
                break;
            } catch (final UnexpectedRedirectException x) {
                postOnRedirect(x.getRedirectedUrl());
                break;
            } catch (final BlockedException x) {
                postOnBlocked(x.getUrl());
                break;
            } catch (final InternalErrorException x) {
                postOnInternalError(x.getUrl());
                break;
            } catch (final ParserException x) {
                postOnParserException(x.getMessage());
                break;
            } catch (final IOException x) {
                log.info("IO problem while querying departures on " + stationId + " " + networkProvider + " (try "
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
                final String message = "uncategorized problem while querying departures on " + stationId + " "
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
