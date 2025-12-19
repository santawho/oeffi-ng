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

import android.app.ProgressDialog;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.provider.NetworkProvider.Accessibility;
import de.schildbach.pte.provider.NetworkProvider.WalkSpeed;
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

public abstract class QueryTripsRunnable implements Runnable {
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

    protected final Resources res;
    protected final ProgressDialog progressDialog;
    private final Handler handler;

    private final NetworkProvider networkProvider;

    protected final Location from;
    protected final Location via;
    protected final Location to;
    protected final TimeSpec time;
    protected final TripOptions options;
    protected final TripRef tripRef;
    protected final TripShare tripShare;

    private AtomicBoolean cancelled = new AtomicBoolean(false);

    private static final Logger log = LoggerFactory.getLogger(QueryTripsRunnable.class);

    public QueryTripsRunnable(
            final Resources res, final ProgressDialog dialog, final Handler handler,
            final NetworkProvider networkProvider,
            final Location from, final Location via, final Location to, final TimeSpec time,
            final TripOptions options) {
        this.res = res;
        this.progressDialog = dialog;
        this.handler = handler;

        this.networkProvider = networkProvider;
        this.options = options;

        this.from = from;
        this.via = via;
        this.to = to;
        this.time = time;

        this.tripRef = null;
        this.tripShare = null;
    }

    public QueryTripsRunnable(
            final Resources res, final ProgressDialog dialog, final Handler handler,
            final NetworkProvider networkProvider,
            final TripRef tripRef,
            final TripOptions options) {
        this.res = res;
        this.progressDialog = dialog;
        this.handler = handler;

        this.networkProvider = networkProvider;
        this.options = options;

        this.tripRef = tripRef;
        this.tripShare = null;

        this.from = null;
        this.via = null;
        this.to = null;
        this.time = null;

    }

    public QueryTripsRunnable(
            final Resources res, final ProgressDialog dialog, final Handler handler,
            final NetworkProvider networkProvider,
            final TripShare tripShare,
            final TripOptions options) {
        this.res = res;
        this.progressDialog = dialog;
        this.handler = handler;

        this.networkProvider = networkProvider;
        this.options = options;

        this.tripRef = null;
        this.tripShare = tripShare;

        this.from = null;
        this.via = null;
        this.to = null;
        this.time = null;
    }

    public void run() {
        postOnPreExecute();

        int tries = 0;

        while (!cancelled.get()) {
            tries++;

            try {
                final QueryTripsResult result;
                final TripRequestData reloadRequestData;
                if (tripShare != null) {
                    result = networkProvider.loadSharedTrip(tripShare);
                    reloadRequestData = null;
                } else if (tripRef != null) {
                    result = networkProvider.queryReloadTrip(tripRef);
                    reloadRequestData = null;
                } else {
                    final boolean depArr = time.depArr == TimeSpec.DepArr.DEPART;
                    final Date date = new Date(time.timeInMillis());
                    reloadRequestData = new TripRequestData();
                    reloadRequestData.from = from;
                    reloadRequestData.via = via;
                    reloadRequestData.to = to;
                    reloadRequestData.date = date;
                    reloadRequestData.dep = depArr;
                    reloadRequestData.options = options;
                    result = networkProvider.queryTrips(from, via, to, date, depArr, options);
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
            final boolean hasOptimize = options.optimize != null;
            final boolean hasWalkSpeed = options.walkSpeed != null && options.walkSpeed != WalkSpeed.NORMAL;
            final boolean hasAccessibility = options.accessibility != null
                    && options.accessibility != Accessibility.NEUTRAL;

            final SpannableStringBuilder progressMessage = new SpannableStringBuilder(
                    res.getString(R.string.directions_query_progress));
            progressMessage.setSpan(new StyleSpan(Typeface.BOLD), 0, progressMessage.length(),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (hasOptimize || hasWalkSpeed || hasAccessibility) {
                progressMessage.append('\n');
                if (hasOptimize) {
                    progressMessage.append('\n')
                            .append(res.getString(R.string.directions_preferences_optimize_trip_title))
                            .append(": ");
                    final int begin = progressMessage.length();
                    progressMessage.append(
                            res.getStringArray(R.array.directions_optimize_trip)[options.optimize.ordinal()]);
                    progressMessage.setSpan(new StyleSpan(Typeface.BOLD), begin, progressMessage.length(),
                            SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (hasWalkSpeed) {
                    progressMessage.append('\n')
                            .append(res.getString(R.string.directions_preferences_walk_speed_title)).append(": ");
                    final int begin = progressMessage.length();
                    progressMessage
                            .append(res.getStringArray(R.array.directions_walk_speed)[options.walkSpeed.ordinal()]);
                    progressMessage.setSpan(new StyleSpan(Typeface.BOLD), begin, progressMessage.length(),
                            SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (hasAccessibility) {
                    progressMessage.append('\n')
                            .append(res.getString(R.string.directions_preferences_accessibility_title))
                            .append(": ");
                    final int begin = progressMessage.length();
                    progressMessage.append(
                            res.getStringArray(R.array.directions_accessibility)[options.accessibility.ordinal()]);
                    progressMessage.setSpan(new StyleSpan(Typeface.BOLD), begin, progressMessage.length(),
                            SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            progressDialog.setMessage(progressMessage);

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

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(getClass().getName()).append('[');
        builder.append("f:").append(from).append('|');
        builder.append("v:").append(via).append('|');
        builder.append("t:").append(to).append('|');
        builder.append(time).append('|');
        builder.append(options).append(']');
        return builder.toString();
    }
}
