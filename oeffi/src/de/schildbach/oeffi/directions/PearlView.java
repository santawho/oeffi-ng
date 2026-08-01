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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.pte.dto.Style;

public class PearlView extends View {
    private static final float SAFETY_MARGIN = 2.0f;

    private Type type = null;
    private boolean isHighlighted;
    private Style style = null;
    private float cellHeight;
    private float endMarkerRadius;
    private float highlightRadius;

    private final Style defaultStyle;
    private final float lineWidth;
    private final float halfLineWidth;
    private final float intermediateSizeRadius;
    private final float stopStrokeWidth;
    private final int colorBackground;
    private final int colorHighlight;

    private final Paint paint = new Paint();

    public enum Type {
        DEPARTURE,
        ARRIVAL,
        INTERMEDIATE_ARRIVAL,
        DEPARTURE_FOR_INTERMEDIATE_ARRIVAL,
        INTERMEDIATE_DEPARTURE,
        ARRIVAL_FOR_INTERMEDIATE_DEPARTURE,
        PASSING
    }

    public PearlView(final Context context) {
        this(context, null, 0);
    }

    public PearlView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PearlView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        final Resources res = getResources();

        defaultStyle = new Style(Color.GRAY, Color.WHITE);

        lineWidth = res.getDimensionPixelSize(R.dimen.pearl_line_width);
        halfLineWidth = lineWidth / 2.0f;
        intermediateSizeRadius = res.getDimensionPixelSize(R.dimen.pearl_intermediate_size) / 2.0f;
        stopStrokeWidth = res.getDisplayMetrics().density;

        colorBackground = ViewUtils.getAttrColor(context, R.attr.bg_level0);
        colorHighlight = res.getColor(R.color.fg_highlighted);

        paint.setAntiAlias(true);
    }

    public void setType(final Type type) {
        this.type = type;
    }

    public void setHighlighted(final boolean highlighted) {
        isHighlighted = highlighted;
    }

    public void setStyle(final Style style) {
        this.style = style != null ? style : defaultStyle;
    }

    public void setFontMetrics(final FontMetrics fontMetrics) {
        final float fontHeight = -fontMetrics.top + fontMetrics.bottom;
        cellHeight = fontHeight + SAFETY_MARGIN * 2;
        final float maxRadius = fontHeight / 2.0f;
        endMarkerRadius = maxRadius * getResources().getInteger(R.integer.pearl_endmarker_size_percent) / 100;
        highlightRadius = maxRadius * getResources().getInteger(R.integer.pearl_highlight_size_percent) / 100;
    }

    private void drawLine(final Canvas canvas, final float x, final float y1, final float y2) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(lineWidth);
        paint.setColor(style.backgroundColor);
        canvas.drawLine(x, y1, x, y2, paint);

        if (style.hasBorder()) {
            paint.setStrokeWidth(stopStrokeWidth);
            paint.setColor(style.borderColor);
            canvas.drawLine(x - halfLineWidth, y1, x - halfLineWidth, y2, paint);
            canvas.drawLine(x + halfLineWidth, y1, x + halfLineWidth, y2, paint);
        }
    }

    private void drawHighlight(final Canvas canvas, final float x, final float y) {

        if (isHighlighted) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(colorHighlight);
            canvas.drawCircle(x, y, highlightRadius, paint);
        }
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);

        final int height = getHeight();
        final float x = getWidth() / 2.0f;
        final float y = cellHeight / 2.0f;

        switch (type) {
            case DEPARTURE: {
                drawLine(canvas, x, y, height);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(style.backgroundColor);
                canvas.drawCircle(x, y, endMarkerRadius, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stopStrokeWidth);
                paint.setColor(style.hasBorder() ? style.borderColor : colorBackground);
                canvas.drawCircle(x, y, endMarkerRadius, paint);

                drawHighlight(canvas, x, y);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(style.foregroundColor);
                canvas.drawCircle(x, y, intermediateSizeRadius, paint);
                break;
            }
            case ARRIVAL: {
                drawLine(canvas, x, 0, y);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(style.backgroundColor);
                canvas.drawCircle(x, y, endMarkerRadius, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stopStrokeWidth);
                paint.setColor(style.hasBorder() ? style.borderColor : colorBackground);
                canvas.drawCircle(x, y, endMarkerRadius, paint);

                drawHighlight(canvas, x, y);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(style.foregroundColor);
                canvas.drawCircle(x, y, intermediateSizeRadius, paint);
                break;
            }
            case INTERMEDIATE_DEPARTURE:
            case INTERMEDIATE_ARRIVAL: {
                drawLine(canvas, x, 0, height);
                drawHighlight(canvas, x, y);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(style.foregroundColor);
                canvas.drawCircle(x, y, intermediateSizeRadius, paint);
                break;
            }
            default: {
                drawLine(canvas, x, 0, height);
                break;
            }
        }
    }

    @Override
    protected void onMeasure(final int wMeasureSpec, final int hMeasureSpec) {
        final int wMode = MeasureSpec.getMode(wMeasureSpec);
        final int wSize = MeasureSpec.getSize(wMeasureSpec);

        final int cellSize = (int) this.cellHeight;

        final int width;
        if (wMode == MeasureSpec.EXACTLY)
            width = wSize;
        else if (wMode == MeasureSpec.AT_MOST)
            width = Math.min(cellSize, wSize);
        else if (wMode == MeasureSpec.UNSPECIFIED)
            width = cellSize;
        else
            throw new IllegalArgumentException("mode: " + wMode);

        final int hMode = MeasureSpec.getMode(hMeasureSpec);
        final int hSize = MeasureSpec.getSize(hMeasureSpec);

        final int height;
        if (hMode == MeasureSpec.EXACTLY)
            height = hSize;
        else if (hMode == MeasureSpec.AT_MOST)
            height = Math.min(cellSize, hSize);
        else if (hMode == MeasureSpec.UNSPECIFIED)
            height = cellSize;
        else
            throw new IllegalArgumentException("mode: " + hMode);

        setMeasuredDimension(width, height);
    }
}
