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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import com.google.common.base.Preconditions;
import de.schildbach.oeffi.R;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Style;
import de.schildbach.pte.dto.Style.Shape;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.Trip.Individual;
import de.schildbach.pte.dto.Trip.Leg;
import de.schildbach.pte.dto.Trip.Public;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TripsGalleryAdapter extends BaseAdapter {
    private List<Trip> trips = Collections.emptyList();
    private TripsOverviewActivity.RenderConfig renderConfig;
    private boolean canScrollLater = true, canScrollEarlier = true;
    private long minTime = 0, maxTime = 0;

    private final Context context;
    private final boolean darkMode;

    private static final int VIEW_TYPE_TRIP = 0;
    private static final int VIEW_TYPE_CANNOT_SCROLL_EARLIER = 1;
    private static final int VIEW_TYPE_CANNOT_SCROLL_LATER = 2;

    final int positionPaddingHorizontal;
    final int positionPaddingVertical;
    private final Paint publicFillPaint = new Paint();
    private final Paint publicStrokePaint = new Paint();
    private final Paint publicLabelPaint = new Paint();
    private final Paint individualFillPaint = new Paint();
    private final Paint individualLabelPaint = new Paint();
    private final Paint individualTimePaint = new Paint();
    private final Paint individualTimeDiffPaint = new Paint();
    private final Paint publicTimePaint = new Paint();
    private final Paint publicTimeDiffPaint = new Paint();
    private final Paint positionPaint = new Paint();
    private final Paint positionPaintBackground = new Paint();
    private final Paint feederStrokePaint = new Paint();
    private final Paint cannotScrollPaint = new Paint();
    private final int colorSignificantInverse;
    private final int colorDelayed;

    private static final float ROUNDED_CORNER_RADIUS = 8f;
    private static final float CIRCLE_CORNER_RADIUS = 16f;
    private final int tripWidth;

    public TripsGalleryAdapter(final Context context) {
        this.context = context;
        final Resources res = context.getResources();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            this.darkMode =
                    (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        else
            this.darkMode = true;

        final float strokeWidth = res.getDimension(R.dimen.trips_overview_entry_box_stroke_width);
        final int colorSignificant = res.getColor(R.color.fg_significant_darkdefault);
        final int colorLessSignificant = res.getColor(R.color.fg_less_significant_darkdefault);
        final int colorIndividual = res.getColor(R.color.bg_individual_darkdefault);
        colorSignificantInverse = res.getColor(R.color.fg_significant_inverse_darkdefault);
        colorDelayed = res.getColor(R.color.bg_delayed_darkdefault);
        positionPaddingHorizontal = res.getDimensionPixelSize(R.dimen.text_padding_horizontal);
        positionPaddingVertical = res.getDimensionPixelSize(R.dimen.text_padding_vertical);

        tripWidth = res.getDimensionPixelSize(R.dimen.trips_overview_entry_width);

        publicFillPaint.setStyle(Paint.Style.FILL);

        publicStrokePaint.setStyle(Paint.Style.STROKE);
        publicStrokePaint.setColor(Color.WHITE);
        publicStrokePaint.setStrokeWidth(strokeWidth);
        publicStrokePaint.setAntiAlias(true);

        publicLabelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        publicLabelPaint.setTextAlign(Align.CENTER);
        publicLabelPaint.setAntiAlias(true);

        individualFillPaint.setStyle(Paint.Style.FILL);
        individualFillPaint.setColor(colorIndividual);

        individualLabelPaint.setColor(Color.GRAY);
        individualLabelPaint.setTypeface(Typeface.DEFAULT);
        individualLabelPaint.setTextSize(res.getDimension(R.dimen.font_size_xlarge));
        individualLabelPaint.setTextAlign(Align.CENTER);

        individualTimePaint.setColor(colorLessSignificant);
        individualTimePaint.setTypeface(Typeface.DEFAULT);
        individualTimePaint.setTextSize(res.getDimension(R.dimen.font_size_normal));
        individualTimePaint.setAntiAlias(true);
        individualTimePaint.setTextAlign(Align.CENTER);

        individualTimeDiffPaint.setColor(colorLessSignificant);
        individualTimeDiffPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        individualTimeDiffPaint.setTextSize(res.getDimension(R.dimen.font_size_normal));
        individualTimeDiffPaint.setAntiAlias(true);
        individualTimeDiffPaint.setTextAlign(Align.CENTER);

        publicTimePaint.setColor(colorSignificant);
        publicTimePaint.setTypeface(Typeface.DEFAULT_BOLD);
        publicTimePaint.setTextSize(res.getDimension(R.dimen.font_size_normal));
        publicTimePaint.setAntiAlias(true);
        publicTimePaint.setTextAlign(Align.CENTER);

        publicTimeDiffPaint.setColor(colorSignificant);
        publicTimeDiffPaint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.ITALIC));
        publicTimeDiffPaint.setTextSize(res.getDimension(R.dimen.font_size_normal));
        publicTimeDiffPaint.setAntiAlias(true);
        publicTimeDiffPaint.setTextAlign(Align.CENTER);

        positionPaintBackground.setColor(colorSignificant);
        positionPaint.setColor(colorSignificantInverse);
        positionPaint.setTypeface(Typeface.DEFAULT_BOLD);
        positionPaint.setTextSize(res.getDimension(R.dimen.font_size_large));
        positionPaint.setAntiAlias(true);
        positionPaint.setTextAlign(Align.CENTER);

        feederStrokePaint.setStyle(Paint.Style.STROKE);
        feederStrokePaint.setColor(res.getColor(R.color.bg_highlighted_darkdefault));
        feederStrokePaint.setStrokeWidth(strokeWidth * 2);
        feederStrokePaint.setAntiAlias(true);

        cannotScrollPaint.setStyle(Paint.Style.FILL);
    }

    public void setRenderConfig(TripsOverviewActivity.RenderConfig renderConfig) {
        this.renderConfig = renderConfig;
    }

    public void setTrips(final List<Trip> trips, final boolean canScrollLater, final boolean canScrollEarlier) {
        this.trips = trips;
        this.canScrollLater = canScrollLater;
        this.canScrollEarlier = canScrollEarlier;

        notifyDataSetChanged();
    }

    public void setMinMaxTimes(final long minTime, final long maxTime) {
        Preconditions.checkArgument(minTime > 0);
        Preconditions.checkArgument(maxTime > minTime);

        if (minTime != this.minTime || maxTime != this.maxTime) {
            this.minTime = minTime;
            this.maxTime = maxTime;
        }
    }

    public long getMinTime() {
        return minTime;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public float timeToCoord(final long time, final int height) {
        Preconditions.checkArgument(time > 0);
        Preconditions.checkArgument(height > 0);

        final long timeDiff = maxTime - minTime;
        if (timeDiff == 0)
            return minTime;

        return (time - minTime) * height / (float) timeDiff;
    }

    public View getView(final int position, View view, final ViewGroup parent) {
        final int type = getItemViewType(position);

        if (type == VIEW_TYPE_TRIP) {
            if (view == null)
                view = new TripView(context);

            ((TripView) view).setTrip(getItem(position));

            return view;
        } else if (type == VIEW_TYPE_CANNOT_SCROLL_EARLIER) {
            return view != null ? view : new CannotScrollView(context, false);
        } else if (type == VIEW_TYPE_CANNOT_SCROLL_LATER) {
            return view != null ? view : new CannotScrollView(context, true);
        } else {
            throw new IllegalStateException();
        }
    }

    public int getCount() {
        int count = trips.size();

        if (!canScrollEarlier)
            count++;

        if (!canScrollLater)
            count++;

        return count;
    }

    public Trip getItem(int position) {
        if (!canScrollEarlier) {
            if (position == 0)
                return null;

            position--;
        }

        if (position < trips.size())
            return trips.get(position);

        position -= trips.size();

        if (!canScrollLater) {
            if (position == 0)
                return null;

            position--;
        }

        throw new IllegalStateException();
    }

    public long getItemId(final int position) {
        // FIXME small chance of possible collisions
        final int type = getItemViewType(position);
        if (type == VIEW_TYPE_TRIP)
            return getItem(position).hashCode();
        else
            return type;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        if (!canScrollEarlier) {
            if (position == 0)
                return VIEW_TYPE_CANNOT_SCROLL_EARLIER;

            position--;
        }

        if (position < trips.size())
            return VIEW_TYPE_TRIP;

        position -= trips.size();

        if (!canScrollLater) {
            if (position == 0)
                return VIEW_TYPE_CANNOT_SCROLL_LATER;

            position--;
        }

        return Adapter.IGNORE_ITEM_VIEW_TYPE;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(final int position) {
        return getItemViewType(position) == VIEW_TYPE_TRIP;
    }

    private class TripView extends View {
        private Trip trip;
        private final Resources res = getResources();
        private final java.text.DateFormat timeFormat;

        private final float density = res.getDisplayMetrics().density;
        private final float publicBoxFraction = res.getFraction(R.fraction.trips_overview_entry_public_box_fraction, 1,
                1);
        private final float individualBoxFraction = res
                .getFraction(R.fraction.trips_overview_entry_individual_box_fraction, 1, 1);
        private final Drawable walkIcon = res.getDrawable(R.drawable.ic_directions_walk_grey600_24dp);
        private final Drawable bikeIcon = res.getDrawable(R.drawable.ic_directions_bike_grey600_24dp);
        private final Drawable carIcon = res.getDrawable(R.drawable.ic_local_taxi_grey600_24dp);
        private final Drawable warningIcon = res.getDrawable(R.drawable.ic_warning_amber_24dp);

        private final int[] gradientColors = new int[2];
        private final float[] GRADIENT_POSITIONS = new float[] { 0.5f, 0.5f };

        private TripView(final Context context) {
            super(context);

            timeFormat = DateFormat.getTimeFormat(context);

            final TypedArray ta = context.obtainStyledAttributes(new int[] { android.R.attr.selectableItemBackground });
            setBackgroundDrawable(ta.getDrawable(0));
            ta.recycle();
        }

        public void setTrip(final Trip trip) {
            this.trip = trip;
        }

        private final RectF legBox = new RectF(), legBoxRotated = new RectF();
        private final Rect bounds = new Rect();
        private final Matrix matrix = new Matrix();

        @Override
        protected void onDraw(final Canvas canvas) {
            super.onDraw(canvas);

            long now = System.currentTimeMillis();

            final int width = getWidth();
            final int centerX = width / 2;
            final int height = getHeight();

            final List<Leg> legs = trip.legs;

            if (legs != null) {
                if (!trip.isTravelable()) {
                    final int warningWidth = warningIcon.getIntrinsicWidth();
                    final int warningLeft = centerX - warningWidth / 2;
                    final int warningPaddingTop = (int) (4 * density);
                    warningIcon.setBounds(warningLeft, warningPaddingTop, warningLeft + warningWidth,
                            warningPaddingTop + warningIcon.getIntrinsicHeight());
                    warningIcon.draw(canvas);
                }

                // iterate delayed public legs first and draw ghosts of planned times
                for (final Leg leg : legs) {
                    if (leg instanceof Public) {
                        final Public publicLeg = (Public) leg;
                        publicFillPaint.setShader(null);
                        publicFillPaint.setColor(colorDelayed);
                        final Line line = publicLeg.line;
                        final Style style = line.style;
                        final float radius;
                        if (style != null) {
                            if (style.shape == Shape.RECT)
                                radius = 0;
                            else if (style.shape == Shape.CIRCLE)
                                radius = CIRCLE_CORNER_RADIUS;
                            else
                                radius = ROUNDED_CORNER_RADIUS;
                        } else {
                            radius = ROUNDED_CORNER_RADIUS;
                        }

                        final Long departureDelay = publicLeg.departureStop.getDepartureDelay();
                        final Long arrivalDelay = publicLeg.arrivalStop.getArrivalDelay();
                        final boolean isDelayed = (departureDelay != null
                                && departureDelay / DateUtils.MINUTE_IN_MILLIS != 0)
                                || (arrivalDelay != null && arrivalDelay / DateUtils.MINUTE_IN_MILLIS != 0);

                        final Date plannedDepartureTime = publicLeg.departureStop.plannedDepartureTime;
                        final Date plannedArrivalTime = publicLeg.arrivalStop.plannedArrivalTime;

                        if (isDelayed && plannedDepartureTime != null && plannedArrivalTime != null) {
                            final long tPlannedDeparture = plannedDepartureTime.getTime();
                            final float yPlannedDeparture = timeToCoord(tPlannedDeparture, height);
                            final long tPlannedArrival = plannedArrivalTime.getTime();
                            final float yPlannedArrival = timeToCoord(tPlannedArrival, height);

                            // line box
                            legBox.set(0, yPlannedDeparture, width * publicBoxFraction, yPlannedArrival);
                            canvas.drawRoundRect(legBox, radius, radius, publicFillPaint);
                        }
                    }
                }

                // then iterate all individual legs
                final int nLegs = legs.size();
                for (int iLeg = 0; iLeg < nLegs; iLeg++) {
                    final Leg leg = legs.get(iLeg);
                    if (leg instanceof Individual) {
                        final Individual individualLeg = (Individual) leg;
                        final long tDeparture = individualLeg.departureTime.getTime();
                        final float yDeparture = timeToCoord(tDeparture, height);
                        final long tArrival = individualLeg.arrivalTime.getTime();
                        final float yArrival = timeToCoord(tArrival, height);

                        // box
                        final float left = width * (1f - individualBoxFraction) / 2f;
                        legBox.set(left, yDeparture, left + width * individualBoxFraction, yArrival);
                        canvas.drawRect(legBox, individualFillPaint);

                        // symbol
                        final Drawable symbol;
                        if (individualLeg.type == Individual.Type.WALK)
                            symbol = walkIcon;
                        else if (individualLeg.type == Individual.Type.BIKE)
                            symbol = bikeIcon;
                        else if (individualLeg.type == Individual.Type.CAR
                                || individualLeg.type == Individual.Type.TRANSFER)
                            symbol = carIcon;
                        else
                            throw new IllegalStateException("unknown type: " + individualLeg.type);
                        final int symbolWidth = symbol.getIntrinsicWidth();
                        final int symbolHeight = symbol.getIntrinsicHeight();
                        if (legBox.height() >= symbolHeight) {
                            final int symbolLeft = (int) (legBox.centerX() - (float) symbolWidth / 2);
                            final int symbolTop = (int) (legBox.centerY() - (float) symbolHeight / 2);
                            symbol.setBounds(symbolLeft, symbolTop, symbolLeft + symbolWidth, symbolTop + symbolHeight);
                            symbol.draw(canvas);
                        }
                    }
                }

                // then draw arr/dep times
                Date startTime = null;
                boolean startCancelled = false;
                Paint startPaint = null;
                int startYabs = 0;
                Position departurePosition = null;
                final Public firstPublicLeg = trip.getFirstPublicLeg();
                final Date publicDepartureTime;
                if (firstPublicLeg != null) {
                    final Stop publicDepartureStop = firstPublicLeg.departureStop;
                    final boolean publicDepartureCancelled = publicDepartureStop.departureCancelled;
                    publicDepartureTime = publicDepartureStop.getDepartureTime();
                    if (publicDepartureTime != null) {
                        startTime = publicDepartureTime;
                        startCancelled = publicDepartureCancelled;
                        startPaint = publicTimeDiffPaint;
                        departurePosition = publicDepartureStop.getDeparturePosition();
                        startYabs = (int) timeToCoord(publicDepartureTime.getTime(), height);
                        if (departurePosition != null && !startCancelled) {
                            startYabs = drawPosition(canvas, centerX, startYabs, height, true, departurePosition.name);
                        }
                        startYabs = drawTime(canvas, centerX, startYabs, height, true, publicTimePaint, publicDepartureCancelled,
                                publicDepartureTime, null);
                    }
                } else {
                    publicDepartureTime = null;
                }

                final Date individualDepartureTime = trip.getFirstDepartureTime();
                if (individualDepartureTime != null) {
                    final int individualYabs = drawTime(canvas, centerX, startYabs, height,
                            true, individualTimePaint, false, individualDepartureTime,
                            publicDepartureTime);
                    if (individualYabs != Integer.MIN_VALUE) {
                        startTime = individualDepartureTime;
                        startPaint = individualTimeDiffPaint;
                        startYabs = individualYabs;
                    }
                }

                TimeSpec referenceTime = renderConfig.referenceTime;

                if (startTime != null) {
                    final long baseTime;
                    if (referenceTime != null && referenceTime.depArr == TimeSpec.DepArr.DEPART) {
                        final long refTime = referenceTime.timeInMillis();
                        baseTime = Math.max(now, refTime);
                    } else {
                        baseTime = now;
                    }
                    long diff = startTime.getTime() - baseTime;
                    if (diff > 0) {
                        startYabs = drawRemaining(canvas, centerX, startYabs, height, true, startPaint, startCancelled, diff);
                    }
                }

                Date endTime = null;
                boolean endCancelled = false;
                Paint endPaint = null;
                int endYabs = 0;
                final Public lastPublicLeg = trip.getLastPublicLeg();
                final Date publicArrivalTime;
                if (lastPublicLeg != null) {
                    final Stop publicArrivalStop = lastPublicLeg.arrivalStop;
                    final boolean publicArrivalCancelled = publicArrivalStop.arrivalCancelled;
                    publicArrivalTime = trip.getLastPublicLegArrivalTime();
                    if (publicArrivalTime != null) {
                        endTime = publicArrivalTime;
                        endCancelled = publicArrivalCancelled;
                        endPaint = publicTimeDiffPaint;
                        endYabs = (int) timeToCoord(publicArrivalTime.getTime(), height);
                        endYabs = drawTime(canvas, centerX, endYabs, height, false, publicTimePaint, publicArrivalCancelled,
                                publicArrivalTime, null);
                    }
                } else {
                    publicArrivalTime = null;
                }

                final Date individualArrivalTime = trip.getLastArrivalTime();
                if (individualArrivalTime != null) {
                    final int individualYabs = drawTime(canvas, centerX, endYabs, height,
                            false, individualTimePaint, false, individualArrivalTime,
                            publicArrivalTime);
                    if (individualYabs != Integer.MIN_VALUE) {
                        endTime = individualArrivalTime;
                        endPaint = individualTimeDiffPaint;
                        endYabs = individualYabs;
                    }
                }

                if (endTime != null && referenceTime != null && referenceTime.depArr == TimeSpec.DepArr.ARRIVE) {
                    long refTime = referenceTime.timeInMillis();
                    long diff = refTime - endTime.getTime();
                    drawRemaining(canvas, centerX, endYabs, height, false, endPaint, endCancelled, diff);
                }

                // last, iterate all public legs
                for (final Leg leg : legs) {
                    if (leg instanceof Public) {
                        final Public publicLeg = (Public) leg;
                        final boolean isFeeder = renderConfig.feederJourneyRef != null && renderConfig.feederJourneyRef.equals(publicLeg.journeyRef);
                        final boolean isConnection = renderConfig.connectionJourneyRef != null && renderConfig.connectionJourneyRef.equals(publicLeg.journeyRef);
                        final Line line = publicLeg.line;
                        final Style style = line.style;
                        final float radius;
                        final int fillColor, fillColor2;
                        final int labelColor;
                        if (style != null) {
                            if (style.shape == Shape.RECT)
                                radius = 0;
                            else if (style.shape == Shape.CIRCLE)
                                radius = CIRCLE_CORNER_RADIUS;
                            else
                                radius = ROUNDED_CORNER_RADIUS;
                            fillColor = style.backgroundColor;
                            fillColor2 = style.backgroundColor2;
                            labelColor = style.foregroundColor;
                        } else {
                            radius = ROUNDED_CORNER_RADIUS;
                            fillColor = Color.GRAY;
                            fillColor2 = 0;
                            labelColor = Color.WHITE;
                        }

                        final long tDeparture = publicLeg.departureStop.getDepartureTime().getTime();
                        final float yDeparture = timeToCoord(tDeparture, height);
                        final long tArrival = publicLeg.arrivalStop.getArrivalTime().getTime();
                        final float yArrival = timeToCoord(tArrival, height);

                        // line box
                        final float margin = width * (1f - publicBoxFraction) / 2f;
                        legBox.set(margin, yDeparture, margin + width * publicBoxFraction, yArrival);
                        if (fillColor2 == 0) {
                            publicFillPaint.setColor(fillColor);
                            publicFillPaint.setShader(null);
                        } else {
                            matrix.reset();
                            matrix.postRotate(90, legBox.centerX(), legBox.centerY());
                            matrix.mapRect(legBoxRotated, legBox);
                            gradientColors[0] = fillColor;
                            gradientColors[1] = fillColor2;
                            publicFillPaint.setShader(new LinearGradient(legBoxRotated.left, legBoxRotated.top,
                                    legBoxRotated.right, legBoxRotated.bottom, gradientColors, GRADIENT_POSITIONS,
                                    Shader.TileMode.CLAMP));
                        }
                        canvas.drawRoundRect(legBox, radius, radius, publicFillPaint);
                        if (isFeeder || isConnection) {
                            canvas.drawRoundRect(legBox, radius, radius, feederStrokePaint);
                        } else if (style != null && style.hasBorder()) {
                            publicStrokePaint.setColor(style.borderColor);
                            canvas.drawRoundRect(legBox, radius, radius, publicStrokePaint);
                        } else if (darkMode && Style.perceivedBrightness(fillColor) < 0.15f ||
                                !darkMode && Style.perceivedBrightness(fillColor) > 0.85f) {
                            publicStrokePaint.setColor(colorSignificantInverse);
                            canvas.drawRoundRect(legBox, radius, radius, publicStrokePaint);
                        }

                        // line label
                        final String[] lineLabels = splitLineLabel(line.label != null ? line.label : "?");
                        publicLabelPaint.setColor(labelColor);
                        publicLabelPaint.setShadowLayer(
                                publicLabelPaint.getColor() != Color.BLACK && publicLabelPaint.getColor() != Color.RED
                                        ? 2f : 0f,
                                0, 0, publicLabelPaint.getColor() != Color.BLACK ? Color.BLACK : Color.WHITE);
                        publicLabelPaint.setTextSize(24f * density);
                        final FontMetrics mLine = publicLabelPaint.getFontMetrics();
                        final float hLine = mLine.descent + (-mLine.ascent);
                        float scale = hLine / legBox.height();
                        final float lineSpacing = scale < 0.6f ? 4 * density : 1;
                        if (scale < 1f)
                            scale = 1f;
                        publicLabelPaint.setTextSize(24f / scale * density);
                        publicLabelPaint.setTextScaleX(scale);

                        // draw really centered on line box
                        if (scale < 4f) {
                            publicLabelPaint.getTextBounds(lineLabels[0], 0, lineLabels[0].length(), bounds);
                            if (lineLabels.length == 1) {
                                final int halfHeight = -bounds.centerY();
                                canvas.drawText(lineLabels[0], legBox.centerX(), legBox.centerY() + halfHeight,
                                        publicLabelPaint);
                            } else {
                                canvas.drawText(lineLabels[0], legBox.centerX(), legBox.centerY() - lineSpacing / 2,
                                        publicLabelPaint);
                                canvas.drawText(lineLabels[1], legBox.centerX(),
                                        legBox.centerY() + bounds.height() + lineSpacing / 2, publicLabelPaint);
                            }
                        }
                    }
                }
            }
        }

        private int drawTime(final Canvas canvas, final int centerX, final int aY, final int height, final boolean above,
                final Paint paint, final boolean strikeThru, final Date time, final @Nullable Date timeKeepOut) {
            final FontMetrics metrics = paint.getFontMetrics();

            final long t = time.getTime();
            final float y;
            final String str = timeFormat.format(t);
            final float fontHeight = (-metrics.ascent + metrics.descent); // + 4 * density;

            if (strikeThru)
                paint.setFlags(paint.getFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            else
                paint.setFlags(paint.getFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);

            if (timeKeepOut != null) {
                final long tKeepOut = timeKeepOut.getTime();
                final float yKeepOut = timeToCoord(tKeepOut, height);
                if (t == tKeepOut)
                    return Integer.MIN_VALUE; // don't draw anything

                if (above)
                    y = Math.min(aY, yKeepOut - fontHeight);
                else
                    y = Math.max(aY, yKeepOut + fontHeight);
            } else {
                y = aY;
            }

            if (above) {
                canvas.drawText(str, centerX, y - metrics.descent, paint);
                return (int) (y - fontHeight);
            } else {
                canvas.drawText(str, centerX, y  - metrics.ascent, paint);
                return (int) (y + fontHeight);
            }
        }

        private int drawRemaining(final Canvas canvas, final int centerX, final int y, final int height, final boolean above,
                              final Paint paint, final boolean strikeThru, final long timeDiff) {
            final FontMetrics metrics = paint.getFontMetrics();

            final String str = ((timeDiff >= 0) ? "+" : "") + Long.toString(timeDiff / 60000);

            if (strikeThru)
                paint.setFlags(paint.getFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            else
                paint.setFlags(paint.getFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);

            final float fontHeight = (-metrics.ascent + metrics.descent); // + 4 * density;
            if (above) {
                canvas.drawText(str, centerX, y - metrics.descent, paint);
                return (int) (y - fontHeight);
            } else {
                canvas.drawText(str, centerX, y - metrics.ascent, paint);
                return (int) (y + fontHeight);
            }
        }

        private int drawPosition(final Canvas canvas, final int centerX, final int y, final int height, final boolean above,
                                 final String name) {
            final FontMetrics metrics = positionPaint.getFontMetrics();

            positionPaint.setFlags(positionPaint.getFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            final float fontHeight = (-metrics.ascent + metrics.descent); // + 4 * density;
            positionPaint.getTextBounds(name, 0, name.length(), bounds);
            bounds.inset(-positionPaddingHorizontal, -positionPaddingVertical);
            if (above) {
                bounds.offsetTo(centerX - bounds.width() / 2, (int) (y - fontHeight));
                canvas.drawRect(bounds, positionPaintBackground);
                canvas.drawText(name, centerX, y - metrics.descent, positionPaint);
                return (int) (y - fontHeight);
            } else {
                bounds.offsetTo(centerX - bounds.width() / 2, (int) (y - fontHeight));
                canvas.drawRect(bounds, positionPaintBackground);
                canvas.drawText(name, centerX, y + metrics.ascent, positionPaint);
                return (int) (y + fontHeight);
            }
        }

        private final Pattern P_SPLIT_LINE_LABEL_1 = Pattern.compile("([^\\s]+)\\s+([^\\s]+)");
        private final Pattern P_SPLIT_LINE_LABEL_2 = Pattern.compile("([a-zA-Z]+)(\\d+)");

        private String[] splitLineLabel(final String label) {
            if (label.length() <= 4)
                return new String[] { label };

            final Matcher m1 = P_SPLIT_LINE_LABEL_1.matcher(label);
            if (m1.matches())
                return new String[] { m1.group(1), m1.group(2) };

            final Matcher m2 = P_SPLIT_LINE_LABEL_2.matcher(label);
            if (m2.matches())
                return new String[] { m2.group(1), m2.group(2) };

            if (label.length() <= 5)
                return new String[] { label };

            final int splitIndex = (int) Math.ceil((double) label.length() / 2);
            return new String[] { label.substring(0, splitIndex), label.substring(splitIndex) };
        }

        @Override
        protected void onMeasure(final int wMeasureSpec, final int hMeasureSpec) {
            final int wMode = MeasureSpec.getMode(wMeasureSpec);
            final int wSize = MeasureSpec.getSize(wMeasureSpec);

            final int width;
            if (wMode == MeasureSpec.EXACTLY)
                width = wSize;
            else if (wMode == MeasureSpec.AT_MOST)
                width = Math.min(tripWidth, wSize);
            else
                width = tripWidth;

            final int height = MeasureSpec.getSize(hMeasureSpec);

            setMeasuredDimension(width, height);
        }
    }

    private class CannotScrollView extends View {
        private final boolean later;

        private final int COLOR = Color.parseColor("#80303030");

        private CannotScrollView(final Context context, final boolean later) {
            super(context);

            this.later = later;
        }

        @Override
        protected void onMeasure(final int wMeasureSpec, final int hMeasureSpec) {
            final int wMode = MeasureSpec.getMode(wMeasureSpec);
            final int wSize = MeasureSpec.getSize(wMeasureSpec);

            final int width;
            if (wMode == MeasureSpec.EXACTLY)
                width = wSize;
            else if (wMode == MeasureSpec.AT_MOST)
                width = Math.min(tripWidth * 2, wSize);
            else
                width = tripWidth * 2;

            final int height = MeasureSpec.getSize(hMeasureSpec);

            setMeasuredDimension(width, height);
        }

        private final RectF box = new RectF();

        @Override
        protected void onDraw(final Canvas canvas) {
            super.onDraw(canvas);

            final int width = getWidth();
            final int height = getHeight();

            final float left, right;
            final LinearGradient gradient;

            if (later) {
                left = width * 0.1f;
                right = width;
                gradient = new LinearGradient(left, 0, right, 0, COLOR, Color.TRANSPARENT, TileMode.CLAMP);
            } else {
                left = 0;
                right = width * 0.9f;
                gradient = new LinearGradient(left, 0, right, 0, Color.TRANSPARENT, COLOR, TileMode.CLAMP);
            }

            box.set(left, 0, right, height);
            cannotScrollPaint.setShader(gradient);
            canvas.drawRect(box, cannotScrollPaint);
        }
    }
}
