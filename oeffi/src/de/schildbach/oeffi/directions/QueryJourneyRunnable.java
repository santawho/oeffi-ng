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
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLException;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.QueryJourneyResult;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.exception.BlockedException;
import de.schildbach.pte.exception.InternalErrorException;
import de.schildbach.pte.exception.NotFoundException;
import de.schildbach.pte.exception.UnexpectedRedirectException;
import okhttp3.HttpUrl;

public class QueryJourneyRunnable implements Runnable {
    final Activity parentActivity;
    final View clickedView;
    private final Resources res;
    private final ProgressDialog progressDialog;
    private final Handler handler;

    private final NetworkProvider networkProvider;

    private final JourneyRef journeyRef;
    private Location entryLocation;
    private Location exitLocation;

    private AtomicBoolean cancelled = new AtomicBoolean(false);

    private static final Logger log = LoggerFactory.getLogger(QueryJourneyRunnable.class);

    public static QueryJourneyRunnable startShowJourney(
            final Activity parentActivity, final View clickedView,
            final QueryJourneyRunnable prevInstance, final Handler handler, final Handler backgroundHandler,
            final NetworkId networkId, final JourneyRef journeyRef,
            final Location entryLocation, final Location exitLocation) {
        final ProgressDialog progressDialog = ProgressDialog.show(parentActivity, null,
                parentActivity.getString(R.string.directions_query_progress), true, true, dialog -> {
                    if (prevInstance != null)
                        prevInstance.cancel();
                });
        progressDialog.setCanceledOnTouchOutside(false);

        final NetworkProvider networkProvider = NetworkProviderFactory.provider(networkId);
        final QueryJourneyRunnable queryJourneyRunnable = new QueryJourneyRunnable(
            parentActivity, clickedView,
            progressDialog, handler, networkProvider,
            journeyRef, entryLocation, exitLocation);

        log.info("Executing: {}", queryJourneyRunnable);

        backgroundHandler.post(queryJourneyRunnable);
        return queryJourneyRunnable;
    }

    public QueryJourneyRunnable(
            final Activity parentActivity, final View clickedView,
            final ProgressDialog progressDialog, final Handler handler,
            final NetworkProvider networkProvider,
            final JourneyRef journeyRef,
            final Location entryLocation, final Location exitLocation) {
        this.parentActivity = parentActivity;
        this.clickedView = clickedView;
        this.res = parentActivity.getResources();
        this.progressDialog = progressDialog;
        this.handler = handler;

        this.networkProvider = networkProvider;

        this.journeyRef = journeyRef;
        this.entryLocation = entryLocation;
        this.exitLocation = exitLocation;
    }

    public void run() {
        postOnPreExecute();

        int tries = 0;

        while (!cancelled.get()) {
            tries++;

            try {
                final QueryJourneyResult result = networkProvider.queryJourney(journeyRef);

                if (!cancelled.get())
                    postOnResult(result);

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
                        final QueryJourneyResult result = new QueryJourneyResult(null,
                                QueryJourneyResult.Status.SERVICE_DOWN);

                        if (!cancelled.get())
                            postOnResult(result);

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
            final SpannableStringBuilder progressMessage = new SpannableStringBuilder(
                    res.getString(R.string.directions_query_progress));
            progressMessage.setSpan(new StyleSpan(Typeface.BOLD), 0, progressMessage.length(),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);

            progressDialog.setMessage(progressMessage);

            onPreExecute();
        });
    }

    protected void onPreExecute() {
        if (clickedView != null)
            clickedView.setClickable(false);
    }

    private void postOnPostExecute() {
        handler.post(() -> onPostExecute());
    }

    protected void onPostExecute() {
        if (clickedView != null)
            clickedView.setClickable(true);

        if (!parentActivity.isDestroyed())
            progressDialog.dismiss();
    }

    private void postOnResult(final QueryJourneyResult result) {
        handler.post(() -> onResult(result));
    }

    protected void onResult(final QueryJourneyResult result) {
        if (result == null || result.status == QueryJourneyResult.Status.NO_JOURNEY) {
            new Toast(parentActivity).longToast(R.string.directions_message_no_trips);
        } else if (result.status == QueryJourneyResult.Status.OK) {
            log.debug("Got {}", result.toShortString());

            final Trip.Public journeyLeg = result.journeyLeg;
            journeyLeg.setEntryAndExit(entryLocation, exitLocation);
            TripDetailsActivity.start(parentActivity, networkProvider.id(), journeyLeg, new Date());
        } else if (result.status == QueryJourneyResult.Status.SERVICE_DOWN) {
            networkProblem();
        }
    }

    private void postOnRedirect(final HttpUrl url) {
        handler.post(() -> onRedirect(url));
    }

    protected void onRedirect(final HttpUrl url) {
        final DialogBuilder builder = DialogBuilder.warn(parentActivity,
                R.string.directions_alert_redirect_title);
        builder.setMessage(parentActivity.getString(R.string.directions_alert_redirect_message, url.host()));
        builder.setPositiveButton(R.string.directions_alert_redirect_button_follow,
                (dialog, which) -> parentActivity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))));
        builder.setNegativeButton(R.string.directions_alert_redirect_button_dismiss, null);
        builder.show();
    }

    private void postOnBlocked(final HttpUrl url) {
        handler.post(() -> onBlocked(url));
    }

    protected void onBlocked(final HttpUrl url) {
        final DialogBuilder builder = DialogBuilder.warn(parentActivity,
                R.string.directions_alert_blocked_title);
        builder.setMessage(parentActivity.getString(R.string.directions_alert_blocked_message, url.host()));
        builder.setPositiveButton(R.string.directions_alert_blocked_button_retry,
                (dialog, which) -> clickedView.performClick());
        builder.setNegativeButton(R.string.directions_alert_blocked_button_dismiss, null);
        builder.show();
    }

    private void postOnInternalError(final HttpUrl url) {
        handler.post(() -> onInternalError(url));
    }

    protected void onInternalError(final HttpUrl url) {
        final DialogBuilder builder = DialogBuilder.warn(parentActivity,
                R.string.directions_alert_internal_error_title);
        builder.setMessage(parentActivity.getString(R.string.directions_alert_internal_error_message, url.host()));
        builder.setPositiveButton(R.string.directions_alert_internal_error_button_retry,
                (dialog, which) -> clickedView.performClick());
        builder.setNegativeButton(R.string.directions_alert_internal_error_button_dismiss, null);
        builder.show();
    }

    private void postOnSSLException(final SSLException x) {
        handler.post(() -> onSSLException(x));
    }

    protected void onSSLException(final SSLException x) {
        final DialogBuilder builder = DialogBuilder.warn(parentActivity,
                R.string.directions_alert_ssl_exception_title);
        builder.setMessage(parentActivity.getString(R.string.directions_alert_ssl_exception_message, x.getMessage()));
        builder.setNeutralButton(R.string.directions_alert_ssl_exception_button_dismiss, null);
        builder.show();
    }

    public void cancel() {
        cancelled.set(true);

        handler.post(() -> onCancelled());
    }

    protected void onCancelled() {
    }

    private void networkProblem() {
        final DialogBuilder builder = DialogBuilder.warn(parentActivity,
                R.string.alert_network_problem_title);
        builder.setMessage(R.string.alert_network_problem_message);
        builder.setPositiveButton(R.string.alert_network_problem_retry, (dialog, which) -> {
            dialog.dismiss();
            clickedView.performClick();
        });
        builder.setOnCancelListener(dialog -> dialog.dismiss());
        builder.show();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(getClass().getName()).append('[');
        builder.append("j:").append(journeyRef).append('|');
        return builder.toString();
    }
}
