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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Gallery;
import com.google.common.math.LongMath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.util.TimeZoneSelector;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Trip;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

public class TripsGallery extends Gallery {
    private static final Logger log = LoggerFactory.getLogger(TripsGallery.class);

    private TripsOverviewActivity.RenderConfig renderConfig;
    private OnScrollListener onScrollListener;

    private final Paint gridPaint = new Paint();
    private final Paint gridLabelPaint = new Paint();

    private class TimeLine {
        private final Paint paint = new Paint();
        private final Paint labelBackgroundPaint = new Paint();
        private final Paint labelTextPaint = new Paint();

        TimeLine(int fgColor, int bgColor, float strokeWidth, float textSize) {
            paint.setColor(fgColor);
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setAntiAlias(false);

            labelBackgroundPaint.setColor(fgColor);
            labelBackgroundPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            labelBackgroundPaint.setStrokeWidth(strokeWidth);
            labelBackgroundPaint.setAntiAlias(true);

            labelTextPaint.setColor(bgColor);
            labelTextPaint.setAntiAlias(true);
            labelTextPaint.setTextSize(textSize);
            labelTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            labelTextPaint.setTextAlign(Align.CENTER);
        }

        public void draw(Canvas canvas, long time, int height, int width, boolean labelRight, boolean labelUp) {
            if (!adapter.isRangeDefined())
                return;
            final int offset = context.getTimeZoneSelector().getOffset(time, PTDate.NETWORK_OFFSET);
            final String label = Formats.formatTime(context.getTimeZoneSelector(), time, offset);
            final float y = adapter.timeToCoord(time, height);
            labelTextPaint.getTextBounds(label, 0, label.length(), bounds);
            bounds.inset(-timeLabelPaddingHorizontal, -timeLabelPaddingVertical);
            final int yText;
            if (labelUp) {
                yText = Math.round(y) - bounds.height();
            } else {
                yText = Math.round(y);
            }
            bounds.offsetTo(paddingHorizontalCram, yText);

            boundsF.set(bounds);
            if (labelRight) {
                canvas.drawLine(0, y, width, y, paint);
                boundsF.left = width - bounds.right;
                boundsF.right = width - bounds.left;
            } else {
                canvas.drawLine(bounds.right + paddingHorizontalCram, y, width, y, paint);
            }
            final float roundRadius = Math.min(timeLabelPaddingHorizontal, timeLabelPaddingVertical);
            canvas.drawRoundRect(boundsF, roundRadius, roundRadius, labelBackgroundPaint);
            canvas.drawText(label, boundsF.centerX(), boundsF.bottom - timeLabelPaddingVertical, labelTextPaint);
        }
    }
    private TimeLine currentTimeLine;
    private TimeLine referenceTimeLine;

    private final OeffiActivity context;
    private final int paddingHorizontal, paddingHorizontalCram;
    private final int timeLabelPaddingHorizontal, timeLabelPaddingVertical;

    private final TripsGalleryAdapter adapter;

    private final Handler handler = new Handler();

    public TripsGallery(final Context context) {
        this(context, null, 0);
    }

    public TripsGallery(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TripsGallery(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        this.context = (OeffiActivity) context;

        final Resources res = getResources();
        paddingHorizontal = res.getDimensionPixelSize(R.dimen.text_padding_horizontal);
        paddingHorizontalCram = res.getDimensionPixelSize(R.dimen.text_padding_horizontal_cram);
        timeLabelPaddingHorizontal = res.getDimensionPixelSize(R.dimen.text_padding_horizontal);
        timeLabelPaddingVertical = res.getDimensionPixelSize(R.dimen.text_padding_vertical);
        final float strokeWidth = res.getDimension(R.dimen.trips_overview_stroke_width_darkdefault);
        final float labelTextSize = res.getDimension(R.dimen.font_size_normal);
        final int colorSignificant = res.getColor(R.color.fg_significant_darkdefault);
        final int colorInsignificant = res.getColor(R.color.fg_insignificant_darkdefault);
        final int colorSignificantInverse = res.getColor(R.color.fg_significant_inverse_darkdefault);
        final int colorCurrentTime = res.getColor(R.color.bg_current_time_darkdefault);
        final int colorReferenceTime = res.getColor(R.color.bg_reference_time_darkdefault);

        gridPaint.setColor(colorInsignificant);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(strokeWidth);
        gridPaint.setPathEffect(new DashPathEffect(new float[] { paddingHorizontalCram, paddingHorizontal }, 0));
        gridPaint.setAntiAlias(false);

        gridLabelPaint.setColor(colorSignificant);
        gridLabelPaint.setAntiAlias(true);
        gridLabelPaint.setTextSize(labelTextSize);
        gridLabelPaint.setTextAlign(Align.CENTER);

        currentTimeLine = new TimeLine(colorCurrentTime, colorSignificantInverse, strokeWidth, labelTextSize);
        referenceTimeLine = new TimeLine(colorReferenceTime, colorSignificantInverse, strokeWidth, labelTextSize);

        setHorizontalFadingEdgeEnabled(false);

        adapter = new TripsGalleryAdapter(context);
        setAdapter(adapter);

        setOnHierarchyChangeListener(new OnHierarchyChangeListener() {
            public void onChildViewRemoved(final View parent, final View child) {
                handler.removeCallbacksAndMessages(null);
                handler.post(onChildViewChangedRunnable);
            }

            public void onChildViewAdded(final View parent, final View child) {
                handler.removeCallbacksAndMessages(null);
                handler.post(onChildViewChangedRunnable);
            }
        });
    }

    public void setRenderConfig(final TripsOverviewActivity.RenderConfig renderConfig) {
        this.renderConfig = renderConfig;
        adapter.setRenderConfig(renderConfig);
    }

    public void setTrips(
            final List<TripInfo> trips,
            final boolean canScrollLater, final boolean canScrollEarlier,
            final boolean showAccessibility, final boolean showBicycleCarriage) {
        adapter.setTrips(trips, canScrollLater, canScrollEarlier, showAccessibility, showBicycleCarriage);
    }

    public void setOnScrollListener(final OnScrollListener onScrollListener) {
        this.onScrollListener = onScrollListener;
    }

    private final Runnable onChildViewChangedRunnable = new Runnable() {
        public void run() {
            final long currentTime = System.currentTimeMillis();
            final int first = getFirstVisiblePosition();
            final int last = getLastVisiblePosition();

            // determine min/max time
            long minTime = Long.MAX_VALUE;
            long maxTime = 0;

            for (int index = first; index <= last; index++) {
                try {
                    final TripInfo tripInfo = adapter.getItem(index);
                    if (tripInfo != null) {
                        final Trip trip = tripInfo.trip;
                        final PTDate tripMinTime = trip.getMinTime();
                        if (tripMinTime != null && tripMinTime.getTime() < minTime)
                            minTime = tripMinTime.getTime();

                        final PTDate tripMaxTime = trip.getMaxTime();
                        if (tripMaxTime != null && tripMaxTime.getTime() > maxTime)
                            maxTime = tripMaxTime.getTime();
                    }
                } catch (IllegalStateException ise) {
                    log.error("cannot get adapter item at position {}, first={}, last={}", index, first, last, ise);
                    // ignore and continue
                }
            }

            // snap to current time
            if (minTime == 0 || (currentTime > minTime - DateUtils.MINUTE_IN_MILLIS * 30 && currentTime < minTime))
                minTime = currentTime;
            else if (maxTime == 0 || (currentTime < maxTime + DateUtils.MINUTE_IN_MILLIS * 30 && currentTime > maxTime))
                maxTime = currentTime;

            // padding
            final long timeDiff = LongMath.checkedSubtract(maxTime, minTime);
            long timePadding = timeDiff / 12;
            if (timeDiff < DateUtils.MINUTE_IN_MILLIS * 30) // zoom limit
                timePadding = (DateUtils.MINUTE_IN_MILLIS * 30 - timeDiff) / 2;
            if (timePadding < DateUtils.MINUTE_IN_MILLIS) // minimum padding
                timePadding = DateUtils.MINUTE_IN_MILLIS;
            minTime = LongMath.checkedSubtract(minTime, timePadding);
            maxTime = LongMath.checkedAdd(maxTime, timePadding);

            // animate
            final long currentMinTime = adapter.getMinTime();
            final long currentMaxTime = adapter.getMaxTime();

            if (currentMinTime != 0 || currentMaxTime != 0) {
                final long diffMin = minTime - currentMinTime;
                final long diffMax = maxTime - currentMaxTime;

                if (Math.abs(diffMin) > DateUtils.SECOND_IN_MILLIS * 10
                        || Math.abs(diffMax) > DateUtils.SECOND_IN_MILLIS * 10) {
                    minTime = currentMinTime + diffMin / 3;
                    maxTime = currentMaxTime + diffMax / 3;

                    handler.postDelayed(this, 25); // 40 fps
                }
            }

            adapter.setMinMaxTimes(minTime, maxTime);

            // refresh views
            invalidate();
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++)
                getChildAt(i).invalidate();

            // notify listener
            if (onScrollListener != null)
                onScrollListener.onScroll();
        }
    };

    private final Calendar gridPtr = new GregorianCalendar();
    private final Rect bounds = new Rect();
    private final RectF boundsF = new RectF();
    private final StringBuilder labelTime = new StringBuilder();
    private final Path path = new Path();

    @Override
    protected void onDraw(final Canvas canvas) {
        if (!adapter.isRangeDefined())
            return;

        final long now = System.currentTimeMillis();
        final int width = getWidth();
        final int height = getHeight();

        final long minTime = adapter.getMinTime();
        final long maxTime = adapter.getMaxTime();
        final long timeDiff = maxTime - minTime;

        // prepare grid
        gridPtr.setTimeInMillis(minTime);
        gridPtr.set(Calendar.MILLISECOND, 0);
        gridPtr.set(Calendar.SECOND, 0);
        gridPtr.set(Calendar.MINUTE, 0);

        final int gridValue, gridField;
        if (timeDiff < DateUtils.MINUTE_IN_MILLIS * 30) {
            // 5 minute grid
            gridValue = 5;
            gridField = Calendar.MINUTE;
        } else if (timeDiff < DateUtils.HOUR_IN_MILLIS) {
            // 10 minute grid
            gridValue = 10;
            gridField = Calendar.MINUTE;
        } else if (timeDiff < DateUtils.HOUR_IN_MILLIS * 3) {
            // half hour grid
            gridValue = 30;
            gridField = Calendar.MINUTE;
            gridPtr.set(Calendar.HOUR_OF_DAY, 0);
        } else if (timeDiff < DateUtils.HOUR_IN_MILLIS * 6) {
            // hour grid
            gridValue = 1;
            gridField = Calendar.HOUR_OF_DAY;
            gridPtr.set(Calendar.HOUR_OF_DAY, 0);
        } else if (timeDiff < DateUtils.HOUR_IN_MILLIS * 12) {
            // 2 hour grid
            gridValue = 2;
            gridField = Calendar.HOUR_OF_DAY;
            gridPtr.set(Calendar.HOUR_OF_DAY, 0);
        } else if (timeDiff < DateUtils.DAY_IN_MILLIS) {
            // 4 hour grid
            gridValue = 4;
            gridField = Calendar.HOUR_OF_DAY;
            gridPtr.set(Calendar.HOUR_OF_DAY, 0);
        } else {
            // 12 hour grid
            gridValue = 12;
            gridField = Calendar.HOUR_OF_DAY;
            gridPtr.set(Calendar.HOUR_OF_DAY, 0);
        }

        // draw grid
        long firstGrid = 0;
        boolean hasDateBorder = false;

        final TimeZoneSelector timeZoneSelector = ((OeffiActivity) context).getTimeZoneSelector();

        while (gridPtr.getTimeInMillis() < maxTime) {
            final long timeInMillis = gridPtr.getTimeInMillis();

            if (timeInMillis >= minTime) {
                // safe first grid line
                if (firstGrid == 0)
                    firstGrid = timeInMillis;

                final boolean isDateBorder = gridPtr.get(Calendar.HOUR_OF_DAY) == 0
                        && gridPtr.get(Calendar.MINUTE) == 0;
                final float y = adapter.timeToCoord(timeInMillis, height);

                final int offset = timeZoneSelector.getOffset(timeInMillis, PTDate.NETWORK_OFFSET);
                labelTime.setLength(0);
                labelTime.append(Formats.formatTime(context.getTimeZoneSelector(), timeInMillis, offset));
                if (isDateBorder) {
                    labelTime.append(", ").append(Formats.formatDate(context.getTimeZoneSelector(), now, timeInMillis, offset));
                    hasDateBorder = true;
                }

                gridLabelPaint.getTextBounds(labelTime.toString(), 0, labelTime.length(), bounds);
                bounds.offsetTo(paddingHorizontal, Math.round(y) - bounds.height());

                path.reset();
                path.moveTo(bounds.right + paddingHorizontal, y);
                path.lineTo(width, y);
                // can't use drawLine here because of
                // https://code.google.com/p/android/issues/detail?id=29944
                canvas.drawPath(path, gridPaint);
                canvas.drawText(labelTime, 0, labelTime.length(), bounds.centerX(), bounds.bottom, gridLabelPaint);
            }

            gridPtr.add(gridField, gridValue);
        }

        // retroactively add date to first grid line
        if (!hasDateBorder && firstGrid > 0) {
            final int firstGridOffset = timeZoneSelector.getOffset(firstGrid, PTDate.NETWORK_OFFSET);
            labelTime.setLength(0);
            labelTime.append(Formats.formatTime(context.getTimeZoneSelector(), firstGrid, firstGridOffset)).append(", ")
                    .append(Formats.formatDate(context.getTimeZoneSelector(), now, firstGrid, firstGridOffset));

            gridLabelPaint.getTextBounds(labelTime.toString(), 0, labelTime.length(), bounds);
            bounds.offsetTo(paddingHorizontal, Math.round(adapter.timeToCoord(firstGrid, height)) - bounds.height());

            canvas.drawText(labelTime, 0, labelTime.length(), bounds.centerX(), bounds.bottom, gridLabelPaint);
        }

        final boolean currentTimeUp;
        final TimeSpec referenceTime = renderConfig.referenceTime;
        if (referenceTime != null && (!(referenceTime instanceof TimeSpec.Relative) || ((TimeSpec.Relative) referenceTime).diffMs != 0)) {
            final boolean timeLineLabelRight = referenceTime.depArr == TimeSpec.DepArr.DEPART;
            final long time = referenceTime.timeInMillis();
            currentTimeUp = time > now;
            // draw reference time
            referenceTimeLine.draw(canvas, time, height, width, timeLineLabelRight, !currentTimeUp);
        } else {
            currentTimeUp = true;
        }
        // draw current time
        currentTimeLine.draw(canvas, now, height, width, true, currentTimeUp);
    }

    public interface OnScrollListener {
        void onScroll();
    }
}
