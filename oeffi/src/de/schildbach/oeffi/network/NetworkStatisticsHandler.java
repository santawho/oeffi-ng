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

package de.schildbach.oeffi.network;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.provider.ApiProvider;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.util.HttpClient;
import de.schildbach.pte.util.RateLimiter;

public class NetworkStatisticsHandler {
    private final Activity context;
    private final View bannerView;
    private final LinearLayout containerView;
    private final int backGroundColor;
    private final int meterColor;
    private boolean haveStats;
    private int numRateLimits;

    public NetworkStatisticsHandler(final Activity context) {
        this.context = context;
        bannerView = context.getLayoutInflater().inflate(R.layout.network_statistics_banner, null);
        containerView = bannerView.findViewById(R.id.network_statistics_banner_container);
        backGroundColor = context.getColor(R.color.bg_network_statistics_entry);
        meterColor = context.getColor(R.color.bg_network_statistics_entry_meter);
    }

    public View getRootView() {
        return bannerView;
    }

    public void refresh(final long nowMs, final NetworkId networkId) {
        final NetworkProvider networkProvider = NetworkProviderFactory.provider(networkId);
        if (networkProvider instanceof ApiProvider) {
            final HttpClient.UsageStatistics stats = ((ApiProvider) networkProvider).getUsageStatistics(nowMs);
            final boolean haveStats = stats != null;
            final int numRateLimits = haveStats ? stats.rateLimits == null ? 0 : stats.rateLimits.size() : 0;
            if (haveStats != this.haveStats || numRateLimits != this.numRateLimits) {
                this.haveStats = haveStats;
                this.numRateLimits = numRateLimits;
                createViews();
            }
            if (haveStats)
                updateViews(nowMs, stats);
        }
    }

    private void createViews() {
        containerView.removeAllViews();
        if (!haveStats)
            return;

        final LayoutInflater layoutInflater = context.getLayoutInflater();
        for (int i = 0; i <= numRateLimits; ++i) {
            containerView.addView(layoutInflater.inflate(R.layout.network_statistics_banner_entry, null));
        }
    }

    private void updateViews(final long nowMs, final HttpClient.UsageStatistics stats) {
        final long timeSpanMinutes = stats.timeSpanMillis / Constants.MINUTE_IN_MILLIS;
        int childIndex = 0;
        final String requestsString = context.getString(R.string.network_statistics_requests, stats.requests, timeSpanMinutes);
        updateEntry(childIndex++, requestsString, 0);

        final List<RateLimiter.Statistics> rateLimits = stats.rateLimits;
        if (rateLimits != null) {
            for (final RateLimiter.Statistics rateLimit : rateLimits) {
                final long intervalMillis = rateLimit.intervalMillis;
                final long seconds = intervalMillis / Constants.SECOND_IN_MILLIS;
                final String unit;
                final long intervalLength;
                if (seconds < 60) {
                    unit = "sec";
                    intervalLength = seconds;
                } else {
                    unit = "min";
                    intervalLength = intervalMillis / Constants.MINUTE_IN_MILLIS;
                }
                final String rateLimitString = context.getString(R.string.network_statistics_rate_limit,
                        rateLimit.requests,
                        rateLimit.allowedRequests,
                        intervalLength, unit,
                        rateLimit.fails,
                        timeSpanMinutes);
                final float meterValue = ((float) rateLimit.requests) / rateLimit.allowedRequests;
                updateEntry(childIndex++, rateLimitString, meterValue);
            }
        }
    }

    private void updateEntry(final int childIndex, final String text, final float meterValue) {
        final View entryView = containerView.getChildAt(childIndex);
        final TextView textView = entryView.findViewById(R.id.network_statistics_entry_text);
        textView.setText(text);
        entryView.setBackground(new MeterDrawable(meterValue));
    }

    private class MeterDrawable extends Drawable {
        private final float meterValue;

        public MeterDrawable(final float meterValue) {
            this.meterValue = meterValue;
        }

        @Override
        public void draw(@NonNull final Canvas canvas) {
            final Rect clipBounds = canvas.getClipBounds();
            final float meterPos = (clipBounds.right - clipBounds.left) * meterValue + clipBounds.left;
            final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(backGroundColor);
            canvas.drawRect(meterPos, clipBounds.top, clipBounds.right, clipBounds.bottom, backgroundPaint);
            final Paint meterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            meterPaint.setColor(meterColor);
            canvas.drawRect(clipBounds.left, clipBounds.top, meterPos, clipBounds.bottom, meterPaint);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void setAlpha(final int i) {
        }

        @Override
        public void setColorFilter(@Nullable final ColorFilter colorFilter) {
        }
    }
}
