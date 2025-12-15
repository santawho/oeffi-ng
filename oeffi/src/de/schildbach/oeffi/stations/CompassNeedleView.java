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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.View;

import static de.schildbach.pte.util.Preconditions.checkArgument;

public final class CompassNeedleView extends View {
    public interface Callback {
        Float getDeviceBearing();

        boolean isFaceDown();
    }

    private Float stationBearing = null;
    private int displayRotation = 0;
    private Callback callback = null;

    private int width;
    private int height;
    private static final Path path = new Path();
    private final Paint arrowPaint = new Paint();
    private static final Paint circlePaint = new Paint();

    static {
        path.moveTo(25f, 0f);
        path.lineTo(10f, 45f);
        path.lineTo(25f, 40f);
        path.lineTo(40f, 45f);
        path.close();

        circlePaint.setColor(Color.parseColor("#aa8888"));
        circlePaint.setAntiAlias(true);
    }

    public CompassNeedleView(final Context context) {
        this(context, null, 0);
    }

    public CompassNeedleView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CompassNeedleView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        arrowPaint.setColor(Color.parseColor("#aa0000"));
    }

    public void setArrowColor(final int color) {
        arrowPaint.setColor(color);
        invalidate();
    }

    public void setStationBearing(final Float stationBearing) {
        this.stationBearing = stationBearing;
        invalidate();
    }

    public void setDisplayRotation(final int displayRotation) {
        checkArgument(displayRotation >= Surface.ROTATION_0 && displayRotation <= Surface.ROTATION_270);
        this.displayRotation = displayRotation;
        invalidate();
    }

    public void setCallback(final Callback callback) {
        this.callback = callback;
        invalidate();
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        final Float deviceBearing;
        final boolean faceDown;
        if (callback != null) {
            deviceBearing = callback.getDeviceBearing();
            faceDown = callback.isFaceDown();
        } else {
            deviceBearing = null;
            faceDown = false;
        }

        canvas.scale(width / 50f, height / 50f);
        if (deviceBearing != null && stationBearing != null) {
            final float heading = stationBearing - deviceBearing;
            final float degrees = (faceDown ? -heading : heading) - displayRotation * 90;
            canvas.rotate(degrees, 25f, 25f);
            canvas.drawPath(path, arrowPaint);
        } else {
            canvas.drawCircle(25f, 25f, 10f, circlePaint);
        }
    }

    @Override
    protected void onMeasure(final int wMeasureSpec, final int hMeasureSpec) {
        final int wMode = MeasureSpec.getMode(wMeasureSpec);
        final int wSize = MeasureSpec.getSize(wMeasureSpec);

        if (wMode == MeasureSpec.EXACTLY)
            width = wSize;
        else if (wMode == MeasureSpec.AT_MOST)
            width = Math.min(width, wSize);

        final int hMode = MeasureSpec.getMode(hMeasureSpec);
        final int hSize = MeasureSpec.getSize(hMeasureSpec);

        if (hMode == MeasureSpec.EXACTLY)
            height = hSize;
        else if (hMode == MeasureSpec.AT_MOST)
            height = Math.min(height, hSize);

        setMeasuredDimension(width, height);
    }
}
