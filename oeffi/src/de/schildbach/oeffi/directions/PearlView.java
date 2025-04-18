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
import de.schildbach.oeffi.R;
import de.schildbach.pte.dto.Style;

public class PearlView extends View {
    private Type type = null;
    private Style style = null;
    private FontMetrics fontMetrics = null;

    private final Style defaultStyle;
    private final int lineWidth;
    private final int intermediateSize;
    private final float stopStrokeWidth;
    private final int colorBackground;

    private final Paint paint = new Paint();

    public enum Type {
        DEPARTURE, ARRIVAL, INTERMEDIATE_ARRIVAL, INTERMEDIATE_DEPARTURE, PASSING
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
        intermediateSize = res.getDimensionPixelSize(R.dimen.pearl_intermediate_size);
        stopStrokeWidth = res.getDisplayMetrics().density;

        colorBackground = res.getColor(R.color.bg_level0);

        paint.setAntiAlias(true);
    }

    public void setType(final Type type) {
        this.type = type;
    }

    public void setStyle(final Style style) {
        this.style = style != null ? style : defaultStyle;
    }

    public void setFontMetrics(final FontMetrics fontMetrics) {
        this.fontMetrics = fontMetrics;
    }

    private void drawLine(final Canvas canvas, final int x, final float y1, final float y2) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(lineWidth);
        paint.setColor(style.backgroundColor);
        canvas.drawLine(x, y1, x, y2, paint);

        if (style.hasBorder()) {
            paint.setStrokeWidth(stopStrokeWidth);
            paint.setColor(style.borderColor);
            canvas.drawLine(x - lineWidth / 2, y1, x - lineWidth / 2, y2, paint);
            canvas.drawLine(x + lineWidth / 2, y1, x + lineWidth / 2, y2, paint);
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        final int height = getHeight();
        final int x = getWidth() / 2;
        final float circleRadius = -fontMetrics.ascent / 2 * 0.9f;
        final float y = -fontMetrics.top - circleRadius + 2;

        if (type == Type.DEPARTURE) {
            drawLine(canvas, x, y, height);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(style.backgroundColor);
            canvas.drawCircle(x, y, circleRadius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stopStrokeWidth);
            paint.setColor(style.hasBorder() ? style.borderColor : colorBackground);
            canvas.drawCircle(x, y, circleRadius, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(style.foregroundColor);
            canvas.drawCircle(x, y, intermediateSize / 2, paint);
        } else if (type == Type.ARRIVAL) {
            drawLine(canvas, x, 0, y);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(style.backgroundColor);
            canvas.drawCircle(x, y, circleRadius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stopStrokeWidth);
            paint.setColor(style.hasBorder() ? style.borderColor : colorBackground);
            canvas.drawCircle(x, y, circleRadius, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(style.foregroundColor);
            canvas.drawCircle(x, y, intermediateSize / 2, paint);
        } else if (type == Type.PASSING) {
            drawLine(canvas, x, 0, height);
        } else if (type == Type.INTERMEDIATE_DEPARTURE) {
            drawLine(canvas, x, 0, height);
        } else if (type == Type.INTERMEDIATE_ARRIVAL) {
            drawLine(canvas, x, 0, height);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(style.foregroundColor);
            canvas.drawCircle(x, y, intermediateSize / 2, paint);
        }
    }

    private static final int SAFETY_MARGIN = 2;

    @Override
    protected void onMeasure(final int wMeasureSpec, final int hMeasureSpec) {
        final int wMode = MeasureSpec.getMode(wMeasureSpec);
        final int wSize = MeasureSpec.getSize(wMeasureSpec);

        final int circleSize = (int) -fontMetrics.ascent;

        final int optimalWidth = circleSize + SAFETY_MARGIN * 2;
        final int width;
        if (wMode == MeasureSpec.EXACTLY)
            width = wSize;
        else if (wMode == MeasureSpec.AT_MOST)
            width = Math.min(optimalWidth, wSize);
        else if (wMode == MeasureSpec.UNSPECIFIED)
            width = optimalWidth;
        else
            throw new IllegalArgumentException("mode: " + wMode);

        final int hMode = MeasureSpec.getMode(hMeasureSpec);
        final int hSize = MeasureSpec.getSize(hMeasureSpec);

        final int height;
        if (hMode == MeasureSpec.EXACTLY)
            height = hSize;
        else if (hMode == MeasureSpec.AT_MOST)
            height = Math.min(circleSize, hSize);
        else if (hMode == MeasureSpec.UNSPECIFIED)
            height = circleSize;
        else
            throw new IllegalArgumentException("mode: " + hMode);

        setMeasuredDimension(width, height);
    }
}
