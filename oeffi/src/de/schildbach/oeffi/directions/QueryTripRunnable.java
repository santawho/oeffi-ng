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

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Handler;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.TripOptions;
import de.schildbach.pte.dto.TripRef;
import de.schildbach.pte.dto.TripShare;
import de.schildbach.pte.exception.BlockedException;
import de.schildbach.pte.exception.InternalErrorException;
import de.schildbach.pte.exception.NotFoundException;
import de.schildbach.pte.exception.UnexpectedRedirectException;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.Serializable;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class QueryTripRunnable implements Runnable {
    public static class TripRequestData implements Serializable {
        private static final long serialVersionUID = -7668471328026069655L;

        public Location from;
        public Location via;
        public Location to;
        public Date date;
        public boolean dep;
        public TripOptions options;

        @NonNull
        public TripRequestData clone() {
            return Objects.clone(this);
        }
    }

    protected final Activity contextActivity;
    protected final SpannableStringBuilder progressMessage;
    protected ProgressDialog progressDialog;
    private final Handler handler;

    private final NetworkProvider networkProvider;

    protected final TripRef tripRef;
    protected final TripShare tripShare;

    private AtomicBoolean cancelled = new AtomicBoolean(false);

    private static final Logger log = LoggerFactory.getLogger(QueryTripRunnable.class);

    protected QueryTripRunnable(
            final Activity contextActivity, final Handler handler,
            final NetworkProvider networkProvider,
            final TripRef tripRef) {
        this.contextActivity = contextActivity;
        this.handler = handler;
        this.progressMessage = new SpannableStringBuilder(contextActivity
                .getString(R.string.directions_query_travel_info_progress));

        this.networkProvider = networkProvider;

        this.tripRef = tripRef;
        this.tripShare = null;
    }

    protected QueryTripRunnable(
            final Activity contextActivity, final Handler handler,
            final NetworkProvider networkProvider,
            final TripShare tripShare) {
        this.contextActivity = contextActivity;
        this.handler = handler;
        this.progressMessage = new SpannableStringBuilder(contextActivity
                .getString(R.string.directions_query_travel_info_progress));

        this.networkProvider = networkProvider;

        this.tripRef = null;
        this.tripShare = tripShare;
    }

    public void run() {
        postOnPreExecute();

        contextActivity.runOnUiThread(() -> {
            progressDialog = ProgressDialog.show(contextActivity,
                    null, progressMessage, true, true, dialog -> {
                        QueryTripRunnable.this.cancel();
                    });
            progressDialog.setCanceledOnTouchOutside(false);
        });

        int tries = 0;

        while (!cancelled.get()) {
            tries++;

            try {
                final QueryTripsResult result;
                final TripRequestData reloadRequestData;
                if (tripShare != null) {
                    result = networkProvider.loadSharedTrip(tripShare, true);
                    reloadRequestData = null;
                } else {
                    result = networkProvider.queryReloadTrip(tripRef, true);
                    reloadRequestData = null;
                }

                if (!cancelled.get())
                    postOnResult(result, reloadRequestData);

                break;
            } catch (final UnexpectedRedirectException x) {
                if (!cancelled.get())
                    postOnRedirect(x.getRedirectedUrl());

                break;
            } catch (final BlockedException x) {
                if (!cancelled.get())
                    postOnBlocked(x.getUrl());

                break;
            } catch (final InternalErrorException x) {
                if (!cancelled.get())
                    postOnInternalError(x.getUrl());

                break;
            } catch (final SSLException x) {
                if (!cancelled.get())
                    postOnSSLException(x);

                break;
            } catch (final IOException x) {
                final String message = "IO problem while processing " + this + " on " + networkProvider + " (try "
                        + tries + ")";
                log.info(message, x);
                if (tries >= Constants.MAX_TRIES_ON_IO_PROBLEM) {
                    if (x instanceof SocketTimeoutException || x instanceof UnknownHostException
                            || x instanceof SocketException || x instanceof NotFoundException
                            || x instanceof SSLException) {
                        final QueryTripsResult result = new QueryTripsResult(null,
                                QueryTripsResult.Status.SERVICE_DOWN);

                        if (!cancelled.get())
                            postOnResult(result, null);

                        break;
                    } else {
                        throw new RuntimeException(message, x);
                    }
                }

                try { TimeUnit.SECONDS.sleep(tries); } catch (InterruptedException ix) {}

                // try again
                continue;
            } catch (final RuntimeException x) {
                final String message = "uncategorized problem while processing " + this + " on " + networkProvider;
                throw new RuntimeException(message, x);
            }
        }

        postOnPostExecute();
    }

    private void postOnPreExecute() {
        handler.post(() -> {
            onPreExecute();
        });
    }

    protected void onPreExecute() {
    }

    private void postOnPostExecute() {
        handler.post(() -> onPostExecute());
    }

    protected void onPostExecute() {
    }

    private void postOnResult(final QueryTripsResult result, final TripRequestData reloadRequestData) {
        handler.post(() -> onResult(result, reloadRequestData));
    }

    protected abstract void onResult(QueryTripsResult result, TripRequestData reloadRequestData);

    private void postOnRedirect(final HttpUrl url) {
        handler.post(() -> onRedirect(url));
    }

    protected void onRedirect(final HttpUrl url) {
    }

    private void postOnBlocked(final HttpUrl url) {
        handler.post(() -> onBlocked(url));
    }

    protected void onBlocked(final HttpUrl url) {
    }

    private void postOnInternalError(final HttpUrl url) {
        handler.post(() -> onInternalError(url));
    }

    protected void onInternalError(final HttpUrl url) {
    }

    private void postOnSSLException(final SSLException x) {
        handler.post(() -> onSSLException(x));
    }

    protected void onSSLException(final SSLException x) {
    }

    public void cancel() {
        cancelled.set(true);

        handler.post(() -> onCancelled());
    }

    protected void onCancelled() {
    }
}
