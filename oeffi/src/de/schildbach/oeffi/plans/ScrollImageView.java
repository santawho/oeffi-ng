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

package de.schildbach.oeffi.plans;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Scroller;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.stations.Station;
import de.schildbach.oeffi.util.UpGestureDetector;

import java.util.List;

public class ScrollImageView extends androidx.appcompat.widget.AppCompatImageView implements Runnable {
    private OnMoveListener onMoveListener;
    private StationsAware stationsAware;

    private final Scroller scroller;
    private final Scroller flinger;
    private final Zoomer zoomer = new Zoomer();
    private final GestureDetector gestureDetector;
    private final UpGestureDetector upGestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private final Handler handler = new Handler();
    private final int trackballSteps;

    private float scrollX, scrollY; // center of screen
    private float currentScale = 1f;
    private int minScrollX, minScrollY, maxScrollX, maxScrollY;
    private float minScale;

    private static final int SCALESTEP_MAX = 4;
    private static final float SCALESTEP_FACTOR = 1.5f;
    private static final float SCALE_MAX = (float) Math.pow(SCALESTEP_FACTOR, SCALESTEP_MAX);

    public static interface OnMoveListener {
        void onMove();
    }

    public ScrollImageView(final Context context) {
        this(context, null, 0);
    }

    public ScrollImageView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScrollImageView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        scroller = new Scroller(context, new DecelerateInterpolator());
        flinger = new Scroller(context);
        setScaleType(ScaleType.MATRIX);
        final GestureListener gestureListener = new GestureListener();
        gestureDetector = new GestureDetector(context, gestureListener);
        upGestureDetector = new UpGestureDetector(gestureListener);
        scaleGestureDetector = new ScaleGestureDetector(context, gestureListener);
        trackballSteps = (int) (128 * context.getResources().getDisplayMetrics().density);
    }

    public void setOnMoveListener(final OnMoveListener onMoveListener) {
        this.onMoveListener = onMoveListener;
    }

    public void setStationsAware(final StationsAware stationsAware) {
        this.stationsAware = stationsAware;
    }

    public void animateScaleStepIn(final float focusX, final float focusY) {
        animateScaleTo(currentScale * SCALESTEP_FACTOR, focusX, focusY);
    }

    public void animateScaleStepIn() {
        animateScaleStepIn(getWidth() / 2f, getHeight() / 2f);
    }

    public void animateScaleStepOut() {
        animateScaleTo(currentScale / SCALESTEP_FACTOR, getWidth() / 2f, getHeight() / 2f);
    }

    private void animateScaleTo(final float scale, final float focusX, final float focusY) {
        scroller.abortAnimation();
        flinger.abortAnimation();

        zoomer.zoom(currentScale, clamp(scale, minScale, SCALE_MAX), focusX, focusY);

        handler.removeCallbacksAndMessages(null);
        handler.post(this);
    }

    public void setScale(final float scale) {
        setScale(scale, scrollX, scrollY);
    }

    public void setScale(final float scale, final float focusX, final float focusY) {
        final float offsetX = focusX - scrollX;
        final float offsetY = focusY - scrollY;

        // determine focus on map
        final float[] center = new float[] { focusX, focusY };
        final Matrix inverse = new Matrix();
        getImageMatrix().invert(inverse);
        inverse.mapPoints(center);

        this.currentScale = scale;

        // determine focus on screen after scaling
        final Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        setImageMatrix(matrix);
        matrix.mapPoints(center);

        initScrollLimits();
        scrollX = clamp(center[0] - offsetX, minScrollX, maxScrollX);
        scrollY = clamp(center[1] - offsetY, minScrollY, maxScrollY);

        scroll(); // implicitly notifies listener
    }

    public boolean canZoomIn() {
        return currentScale < SCALE_MAX;
    }

    public boolean canZoomOut() {
        return currentScale > minScale;
    }

    public boolean isValidScrollX() {
        return scrollX >= minScrollX && scrollX <= maxScrollX;
    }

    public boolean isValidScrollY() {
        return scrollY >= minScrollY && scrollY <= maxScrollY;
    }

    @Override
    public void setImageDrawable(final Drawable drawable) {
        final boolean firstTime = getDrawable() == null;

        super.setImageDrawable(drawable);

        final boolean scrollLimitsChanged = initScrollLimits();
        final boolean scaleLimitsChanged = initScaleLimit();

        if (firstTime) {
            // start out centered
            scrollX = drawable.getIntrinsicWidth() / 2f;
            scrollY = drawable.getIntrinsicHeight() / 2f;
            scroll();
        }

        if (scrollLimitsChanged || scaleLimitsChanged)
            checkLimits();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        if (getDrawable() != null) {
            initScrollLimits();
            initScaleLimit();

            scroll();

            checkLimits();
        }
    }

    private boolean initScrollLimits() {
        final Drawable drawable = getDrawable();

        final float[] bitmapBounds = new float[] { drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight() };
        getImageMatrix().mapPoints(bitmapBounds);
        final int bitmapWidth = Math.round(bitmapBounds[0]);
        final int bitmapHeight = Math.round(bitmapBounds[1]);
        final int viewWidth = getWidth();
        final int viewHeight = getHeight();

        final float oldMinScrollX = minScrollX, oldMaxScrollX = maxScrollX;
        final float oldMinScrollY = minScrollY, oldMaxScrollY = maxScrollY;

        if (bitmapWidth > viewWidth) {
            minScrollX = viewWidth / 2;
            maxScrollX = bitmapWidth - viewWidth / 2;
        } else {
            maxScrollX = viewWidth / 2;
            minScrollX = bitmapWidth - viewWidth / 2;
        }

        if (bitmapHeight > viewHeight) {
            minScrollY = viewHeight / 2;
            maxScrollY = bitmapHeight - viewHeight / 2;
        } else {
            maxScrollY = viewHeight / 2;
            minScrollY = bitmapHeight - viewHeight / 2;
        }

        return minScrollX != oldMinScrollX || maxScrollX != oldMaxScrollX || minScrollY != oldMinScrollY
                || maxScrollY != oldMaxScrollY;
    }

    private boolean initScaleLimit() {
        final Drawable drawable = getDrawable();
        final float oldMinScale = minScale;
        minScale = Math.min(getWidth() / (float) drawable.getIntrinsicWidth(),
                getHeight() / (float) drawable.getIntrinsicHeight());
        return minScale != oldMinScale;
    }

    private void checkLimits() {
        final int dx = Math.round(clamp(scrollX, minScrollX, maxScrollX) - scrollX);
        final int dy = Math.round(clamp(scrollY, minScrollY, maxScrollY) - scrollY);

        if (dx != 0 || dy != 0) {
            scroller.startScroll(Math.round(scrollX), Math.round(scrollY), dx, dy);

            handler.removeCallbacksAndMessages(null);
            handler.post(ScrollImageView.this);
        }

        if (currentScale < minScale)
            animateScaleTo(minScale, getWidth() / 2f, getHeight() / 2f);
        else if (currentScale > SCALE_MAX)
            animateScaleTo(SCALE_MAX, getWidth() / 2f, getHeight() / 2f);
    }

    @Override
    public boolean onTrackballEvent(final MotionEvent event) {
        scrollX = clamp(scrollX - event.getX() * trackballSteps, minScrollX, maxScrollX);
        scrollY = clamp(scrollY - event.getY() * trackballSteps, minScrollY, maxScrollY);
        scroll();
        return true;
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
            scrollX = clamp(scrollX + trackballSteps, minScrollX, maxScrollX);
        else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
            scrollX = clamp(scrollX - trackballSteps, minScrollX, maxScrollX);
        else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
            scrollY = clamp(scrollY + trackballSteps, minScrollY, maxScrollY);
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP)
            scrollY = clamp(scrollY - trackballSteps, minScrollY, maxScrollY);
        else if (keyCode == KeyEvent.KEYCODE_ZOOM_IN)
            setScale(clamp(currentScale * SCALESTEP_FACTOR, minScale, SCALE_MAX));
        else if (keyCode == KeyEvent.KEYCODE_ZOOM_OUT)
            setScale(clamp(currentScale / SCALESTEP_FACTOR, minScale, SCALE_MAX));
        else
            return super.onKeyDown(keyCode, event);

        scroll();
        return true;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final boolean scaleGestureEventHandled = scaleGestureDetector.onTouchEvent(event);
        final boolean upEventHandled = upGestureDetector.onTouchEvent(event);
        final boolean gestureEventHandled = gestureDetector.onTouchEvent(event);

        return upEventHandled || scaleGestureEventHandled || gestureEventHandled || super.onTouchEvent(event);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener
            implements UpGestureDetector.OnUpGestureListener, ScaleGestureDetector.OnScaleGestureListener {
        private boolean scaleGestureStarted;

        @Override
        public boolean onDown(final MotionEvent e) {
            scroller.abortAnimation();
            flinger.abortAnimation();

            scaleGestureStarted = false;
            return true;
        }

        @Override
        public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX,
                final float velocityY) {
            if (!scaleGestureStarted) {
                flinger.fling(Math.round(scrollX), Math.round(scrollY), (int) -velocityX, (int) -velocityY,
                        Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);

                handler.removeCallbacksAndMessages(null);
                handler.post(ScrollImageView.this);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX,
                final float distanceY) {
            scrollX += distanceX;
            scrollY += distanceY;

            // overscroll resistance
            if (!isValidScrollX())
                scrollX -= distanceX / 2;
            if (!isValidScrollY())
                scrollY -= distanceY / 2;

            scroll();

            return true;
        }

        @Override
        public boolean onUp(final MotionEvent event) {
            if (!scaleGestureStarted) {
                checkLimits();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean onSingleTapConfirmed(final MotionEvent e) {
            if (stationsAware != null) {
                final List<Station> stations = stationsAware.getStations();
                final int[] coords = new int[2];

                Station tappedStation = null;
                double tappedDistance = 0;

                for (final Station station : stations) {
                    coords[0] = station.location.getLonAs1E6();
                    coords[1] = station.location.getLatAs1E6();
                    translateToViewCoordinates(coords);
                    final double distance = Math
                            .sqrt(Math.pow(e.getX() - coords[0], 2) + Math.pow(e.getY() - coords[1], 2));

                    if (tappedStation == null || distance < tappedDistance) {
                        tappedStation = station;
                        tappedDistance = distance;
                    }
                }

                if (tappedStation != null)
                    stationsAware.selectStation(tappedStation);
            }

            return true;
        }

        @Override
        public boolean onDoubleTapEvent(final MotionEvent e) {
            if (!scaleGestureStarted && e.getActionMasked() == MotionEvent.ACTION_UP) {
                animateScaleStepIn(e.getX(), e.getY());
                return true;
            } else {
                return false;
            }
        }

        public boolean onScaleBegin(final ScaleGestureDetector detector) {
            scaleGestureStarted = true;
            return true;
        }

        @Override
        public void onScaleEnd(final ScaleGestureDetector detector) {
            checkLimits();
        }

        @Override
        public boolean onScale(final ScaleGestureDetector detector) {
            final float factor = detector.getScaleFactor();
            final float scale = currentScale * factor;
            final float focusX = scrollX - getWidth() / 2f + detector.getFocusX();
            final float focusY = scrollY - getHeight() / 2f + detector.getFocusY();

            if (factor > 1) {
                if (scale <= SCALE_MAX)
                    setScale(scale, focusX, focusY);
                else
                    setScale(currentScale * (float) Math.pow(factor, 0.5), focusX, focusY); // overscale
            } else if (factor < 1) {
                if (scale >= minScale)
                    setScale(scale, focusX, focusY);
                else
                    setScale(currentScale * (float) Math.pow(factor, 0.5), focusX, focusY); // overscale
            }

            return true;
        }
    }

    public void run() {
        final boolean scrollerRunning = !scroller.isFinished() && scroller.computeScrollOffset();
        final boolean flingerRunning = !flinger.isFinished() && flinger.computeScrollOffset();

        if (scrollerRunning || flingerRunning) {
            if (isValidScrollX() && flingerRunning)
                scrollX = clamp(flinger.getCurrX(), minScrollX, maxScrollX);
            else if (scrollerRunning)
                scrollX = scroller.getCurrX();

            if (isValidScrollY() && flingerRunning)
                scrollY = clamp(flinger.getCurrY(), minScrollY, maxScrollY);
            else if (scrollerRunning)
                scrollY = scroller.getCurrY();

            scroll();
        }

        final boolean zoomerRunning = !zoomer.isFinished() && zoomer.computeZoomValue();

        if (zoomerRunning) {
            final float focusX = scrollX - getWidth() / 2f + zoomer.getFocusX();
            final float focusY = scrollY - getHeight() / 2f + zoomer.getFocusY();
            setScale(zoomer.currentValue(), focusX, focusY);
        }

        if (scrollerRunning || flingerRunning || zoomerRunning)
            handler.post(this);
    }

    private void scroll() {
        scrollTo(Math.round(scrollX - getWidth() / 2f), Math.round(scrollY - getHeight() / 2f));

        if (onMoveListener != null)
            onMoveListener.onMove();
    }

    public void translateToViewCoordinates(final int[] coords) {
        coords[0] = Math.round(coords[0] * currentScale - scrollX + getWidth() / 2f);
        coords[1] = Math.round(coords[1] * currentScale - scrollY + getHeight() / 2f);
    }

    public void animatePlanIntoView(final int x, final int y) {
        final float scrollTargetX = clamp(x * currentScale, minScrollX, maxScrollX);
        final float scrollTargetY = clamp(y * currentScale, minScrollY, maxScrollY);

        scroller.startScroll(Math.round(scrollX), Math.round(scrollY), Math.round(scrollTargetX - scrollX),
                Math.round(scrollTargetY - scrollY));
        flinger.abortAnimation();

        handler.removeCallbacksAndMessages(null);
        handler.post(this);
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(final float value, final float min, final float max) {
        return Math.max(min, Math.min(max, value));
    }
}
