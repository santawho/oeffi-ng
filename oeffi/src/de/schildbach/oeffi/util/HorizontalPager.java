package de.schildbach.oeffi.util;

// from: https://github.com/ysamlan/horizontalpager/blob/master/src/com/github/ysamlan/horizontalpager/HorizontalPager.java

/*
 * Modifications by Yoni Samlan; based on RealViewSwitcher, whose license is:
 *
 * Copyright (C) 2010 Marc Reichelt
 *
 * Work derived from Workspace.java of the Launcher application
 *  see http://android.git.kernel.org/?p=platform/packages/apps/Launcher.git
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.Scroller;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import de.schildbach.oeffi.R;

/**
 * A view group that allows users to switch between multiple screens (layouts) in the same way as
 * the Android home screen (Launcher application).
 * <p>
 * You can add and remove views using the normal methods {@link ViewGroup#addView(View)},
 * {@link ViewGroup#removeView(View)} etc. You may want to listen for updates by calling
 * {@link HorizontalPager#setOnScreenSwitchListener(OnScreenSwitchListener)} in order to perform
 * operations once a new screen has been selected.
 *
 * Modifications from original version (ysamlan): Animate argument in setCurrentScreen and duration
 * in snapToScreen; onInterceptTouchEvent handling to support nesting a vertical Scrollview inside
 * the RealViewSwitcher; allowing snapping to a view even during an ongoing scroll; snap to
 * next/prev view on 25% scroll change; density-independent swipe sensitivity; width-independent
 * pager animation durations on scrolling to properly handle large screens without excessively
 * long animations.
 *
 * Other modifications:
 * (aveyD) Handle orientation changes properly and fully snap to the right position.
 *
 * @author Marc Reichelt, <a href="http://www.marcreichelt.de/">http://www.marcreichelt.de/</a>
 * @version 0.1.0
 */
public final class HorizontalPager extends ViewGroup {
    /*
     * How long to animate between screens when programmatically setting with setCurrentScreen using
     * the animate parameter
     */
    private static final int ANIMATION_SCREEN_SET_DURATION_MILLIS = 500;
    // What fraction (1/x) of the screen the user must swipe to indicate a page change
    private static final int FRACTION_OF_SCREEN_WIDTH_FOR_SWIPE = 4;
    private static final int INVALID_SCREEN = -1;
    /*
     * Velocity of a swipe (in density-independent pixels per second) to force a swipe to the
     * next/previous screen. Adjusted into mDensityAdjustedSnapVelocity on init.
     */
    private static final int SNAP_VELOCITY_DIP_PER_SECOND = 600;
    // Argument to getVelocity for units to give pixels per second (1 = pixels per millisecond).
    private static final int VELOCITY_UNIT_PIXELS_PER_SECOND = 1000;

    private static final int TOUCH_STATE_REST = 0;
    private static final int TOUCH_STATE_HORIZONTAL_SCROLLING = 1;
    private static final int TOUCH_STATE_VERTICAL_SCROLLING = -1;
    private final List<View> visibleChildren = new ArrayList<>();
    private final List<View> dragExceptionViews = new ArrayList<>();

    private static final int PAGING_STYLE_SLIDING = 0;
    private static final int PAGING_STYLE_CARDDECK = 1;
    private int pagingStyle;

    private View mCurrentView;
    private int mCurrentScreen;
    private int mDensityAdjustedSnapVelocity;
    private boolean mFirstLayout = true;
    private float mLastMotionX;
    private float mLastMotionY;
    private OnScreenSwitchListener mOnScreenSwitchListener;
    private int mMaximumVelocity;
    private int mNextScreen = INVALID_SCREEN;
    private Scroller mScroller;
    private int mTouchSlop;
    private int mTouchState = TOUCH_STATE_REST;
    private VelocityTracker mVelocityTracker;
    private int mLastSeenLayoutWidth = -1;

    /**
     * Simple constructor to use when creating a view from code.
     *
     * @param context The Context the view is running in, through which it can
     *        access the current theme, resources, etc.
     */
    public HorizontalPager(final Context context) {
        this(context, null);
    }

    /**
     * Constructor that is called when inflating a view from XML. This is called
     * when a view is being constructed from an XML file, supplying attributes
     * that were specified in the XML file. This version uses a default style of
     * 0, so the only attribute values applied are those in the Context's Theme
     * and the given AttributeSet.
     *
     * <p>
     * The method onFinishInflate() will be called after all children have been
     * added.
     *
     * @param context The Context the view is running in, through which it can
     *        access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @see # View(Context, AttributeSet, int)
     */
    public HorizontalPager(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        construct(context, attrs, 0, 0);
    }

    public HorizontalPager(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public HorizontalPager(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
//        super(context, attrs, defStyleAttr);
        construct(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Sets up the scroller and touch/fling sensitivity parameters for the pager.
     */
    private void construct(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        final int[] overflowTextViewAttrs = R.styleable.HorizontalPager;
        final TypedArray a = context.getTheme().obtainStyledAttributes(attrs, overflowTextViewAttrs, defStyleAttr, defStyleRes);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            saveAttributeDataForStyleable(context, overflowTextViewAttrs, attrs, a, defStyleAttr, defStyleRes);
        final int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            final int attr = a.getIndex(i);

            if (attr == R.styleable.HorizontalPager_paging_style) {
                pagingStyle = a.getInt(attr, 0);
                break;
            }
        }
        a.recycle();

        mScroller = new Scroller(getContext());

        // Calculate the density-dependent snap velocity in pixels
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay()
                .getMetrics(displayMetrics);
        mDensityAdjustedSnapVelocity =
                (int) (displayMetrics.density * SNAP_VELOCITY_DIP_PER_SECOND);

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    public int getVisibleChildCount() {
        return visibleChildren.size();
    }

    private void updateVisibleChildren() {
        visibleChildren.clear();
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                visibleChildren.add(child);
            }
        }
    }

    public void addDragExceptionView(final View view) {
        dragExceptionViews.add(view);
    }

    public void removeDragExceptionView(final View view) {
        dragExceptionViews.remove(view);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateVisibleChildren();

        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("ViewSwitcher can only be used in EXACTLY mode.");
        }

        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("ViewSwitcher can only be used in EXACTLY mode.");
        }

        // The children are given the same width and height as the workspace
        final int count = getVisibleChildCount();
        for (int i = 0; i < count; i++) {
            visibleChildren.get(i).measure(widthMeasureSpec, heightMeasureSpec);
        }

        if (mFirstLayout) {
            if (mCurrentView != null)
                setCurrentView(mCurrentView, false);
            scrollTo(mCurrentScreen * width, 0);
            mFirstLayout = false;
        } else if (width != mLastSeenLayoutWidth) { // Width has changed
            /*
             * Recalculate the width and scroll to the right position to be sure we're in the right
             * place in the event that we had a rotation that didn't result in an activity restart
             * (code by aveyD). Without this you can end up between two pages after a rotation.
             */
            final Display display =
                    ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                            .getDefaultDisplay();
            final int displayWidth = display.getWidth();

            mNextScreen = Math.max(0, Math.min(getCurrentScreen(), getVisibleChildCount() - 1));
            final int newX = mNextScreen * displayWidth;
            final int delta = newX - getScrollX();

            mScroller.startScroll(getScrollX(), 0, delta, 0, 0);
        }

        mLastSeenLayoutWidth = width;
    }

    @Override
    protected void onLayout(
            final boolean changed,
            final int left, final int top, final int right, final int bottom) {
        // updateVisibleChildren();
        final int count = getVisibleChildCount();
        int childLeft = 0;
        for (int i = 0; i < count; i++) {
            final View child = visibleChildren.get(i);
            final int childWidth = child.getMeasuredWidth();
            final int childRight = childLeft + childWidth;
            child.layout(childLeft, 0, childRight, child.getMeasuredHeight());
            childLeft = childRight;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        /*
         * By Yoni Samlan: Modified onInterceptTouchEvent based on standard ScrollView's
         * onIntercept. The logic is designed to support a nested vertically scrolling view inside
         * this one; once a scroll registers for X-wise scrolling, handle it in this view and don't
         * let the children, but once a scroll registers for y-wise scrolling, let the children
         * handle it exclusively.
         */
        final int action = ev.getAction();
        boolean intercept = false;

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                /*
                 * If we're in a horizontal scroll event, take it (intercept further events). But if
                 * we're mid-vertical-scroll, don't even try; let the children deal with it. If we
                 * haven't found a scroll event yet, check for one.
                 */
                if (mTouchState == TOUCH_STATE_HORIZONTAL_SCROLLING) {
                    /*
                     * We've already started a horizontal scroll; set intercept to true so we can
                     * take the remainder of all touch events in onTouchEvent.
                     */
                    intercept = true;
                } else if (mTouchState == TOUCH_STATE_VERTICAL_SCROLLING) {
                    // Let children handle the events for the duration of the scroll event.
                    intercept = false;
                } else { // We haven't picked up a scroll event yet; check for one.

                    /*
                     * If we detected a horizontal scroll event, start stealing touch events (mark
                     * as scrolling). Otherwise, see if we had a vertical scroll event -- if so, let
                     * the children handle it and don't look to intercept again until the motion is
                     * done.
                     */

                    final float x = ev.getX();
                    final float y = ev.getY();

                    final int xDiff = (int) Math.abs(x - mLastMotionX);
                    if (xDiff > mTouchSlop) {
                        final View currentPageView = getCurrentView();
                        final Rect hitRect = new Rect();
                        View dragExceptionView = null;
                        for (final View view: dragExceptionViews) {
                            View v = view;
                            while (v != null) {
                                final int visibility = view.getVisibility();
                                if (visibility != View.VISIBLE)
                                    break;
                                if (!view.isEnabled())
                                    break;
                                final ViewParent parent = v.getParent();
                                if (!(parent instanceof View))
                                    break;
                                if (parent == currentPageView) {
                                    view.getHitRect(hitRect);
                                    if (hitRect.contains((int) x, (int) y))
                                        dragExceptionView = view;
                                    break;
                                }
                                v = (View) parent;
                            }
                            if (dragExceptionView != null)
                                break;
                        }
                        if (dragExceptionView == null) {
                            // Scroll if the user moved far enough along the X axis
                            mTouchState = TOUCH_STATE_HORIZONTAL_SCROLLING;
                            mLastMotionX = x;
                        }
                    }

                    final int yDiff = (int) Math.abs(y - mLastMotionY);
                    if (yDiff > mTouchSlop) {
                        mTouchState = TOUCH_STATE_VERTICAL_SCROLLING;
                    }
                }

                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // Release the drag.
                mTouchState = TOUCH_STATE_REST;
                break;
            case MotionEvent.ACTION_DOWN:
                /*
                 * No motion yet, but register the coordinates so we can check for intercept at the
                 * next MOVE event.
                 */
                mLastMotionY = ev.getY();
                mLastMotionX = ev.getX();
                mTouchState = TOUCH_STATE_REST;
                break;
            default:
                break;
        }

        return intercept;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();
        final float x = ev.getX();

        final int visibleChildCount = getVisibleChildCount();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                /*
                 * If being flinged and user touches, stop the fling. isFinished will be false if
                 * being flinged.
                 */
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                }

                // Remember where the motion event started
                mLastMotionX = x;
                mTouchState = TOUCH_STATE_REST;
                break;

            case MotionEvent.ACTION_MOVE:
                final int xDiff = (int) Math.abs(x - mLastMotionX);
                if (mTouchState == TOUCH_STATE_REST && xDiff > mTouchSlop) {
                    // Scroll if the user moved far enough along the X axis
                    mTouchState = TOUCH_STATE_HORIZONTAL_SCROLLING;
                }

                if (mTouchState == TOUCH_STATE_HORIZONTAL_SCROLLING) {
                    // Scroll to follow the motion event
                    if (visibleChildCount > 1) {
                        final int deltaX = (int) (mLastMotionX - x);
                        mLastMotionX = x;
                        final int scrollX = getScrollX();
                        if (deltaX < 0) {
                            final int availableToScroll =
                                    visibleChildren.get(0).getLeft() + scrollX;

                            if (availableToScroll > 0) {
                                final int scroll = Math.max(-availableToScroll, deltaX);
                                scrollBy(scroll, 0);
                            }
                        } else if (deltaX > 0) {
                            final int availableToScroll =
                                    visibleChildren.get(visibleChildCount - 1).getRight() - getWidth() - scrollX;

                            if (availableToScroll > 0) {
                                final int scroll = Math.min(availableToScroll, deltaX);
                                scrollBy(scroll, 0);
                            }
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mTouchState == TOUCH_STATE_HORIZONTAL_SCROLLING) {
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(VELOCITY_UNIT_PIXELS_PER_SECOND,
                            mMaximumVelocity);
                    final int velocityX = (int) velocityTracker.getXVelocity();

                    if (velocityX > mDensityAdjustedSnapVelocity && mCurrentScreen > 0) {
                        // Fling hard enough to move left
                        snapToScreen(mCurrentScreen - 1);
                    } else if (velocityX < -mDensityAdjustedSnapVelocity
                            && mCurrentScreen < visibleChildCount - 1) {
                        // Fling hard enough to move right
                        snapToScreen(mCurrentScreen + 1);
                    } else {
                        snapToDestination();
                    }

                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                    }
                }

                mTouchState = TOUCH_STATE_REST;
                break;

            case MotionEvent.ACTION_CANCEL:
                mTouchState = TOUCH_STATE_REST;
                break;

            default:
                break;
        }

        return true;
    }

    private void positionChildrenWhileScrolling(final int scrollX) {
        if (pagingStyle == PAGING_STYLE_CARDDECK) {
            final int currentScreen = getCurrentScreen();
            final int childCount = getVisibleChildCount();
            if (currentScreen < childCount) {
                final View child = visibleChildren.get(currentScreen);
                final int childWidth = child.getWidth();
                if (childWidth != 0) {
                    final int scrollOffset = scrollX - this.getWidth() * currentScreen;
                    child.setTranslationX(scrollOffset);
                    child.setAlpha(1f - Math.min(1f, ((float) Math.abs(scrollOffset) / childWidth) * 1.5f));
                }
            }
        }
    }

    @Override
    protected void onScrollChanged(final int left, final int top, final int oldLeft, final int oldTop) {
        positionChildrenWhileScrolling(left);
        super.onScrollChanged(left, top, oldLeft, oldTop);
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
            return;
        }

        if (mNextScreen != INVALID_SCREEN) {
            if (mNextScreen != mCurrentScreen) {
                // Notify observer about screen change
                if (mOnScreenSwitchListener != null) {
                    final int prevScreen = mCurrentScreen;
                    setCurrentScreen(mNextScreen);
                    mOnScreenSwitchListener.onScreenSwitched(prevScreen, mCurrentScreen);
                }
            }
            mNextScreen = INVALID_SCREEN;
        }
    }

    /**
     * Returns the index of the currently displayed screen.
     *
     * @return The index of the currently displayed screen.
     */
    public int getCurrentScreen() {
        return mCurrentScreen;
    }

    public View getCurrentView() {
        return visibleChildren.get(mCurrentScreen);
    }

    public void setCurrentView(final int id, final boolean animate) {
        updateVisibleChildren();
        final int childCount = getVisibleChildCount();
        for (int pos = 0; pos < childCount; ++pos) {
            final View child = visibleChildren.get(pos);
            if (child.getId() == id) {
                setCurrentScreen(pos, animate);
                break;
            }
        }
    }

    public void setCurrentView(final View view, final boolean animate) {
        updateVisibleChildren();
        final int childCount = getVisibleChildCount();
        for (int pos = 0; pos < childCount; ++pos) {
            final View child = visibleChildren.get(pos);
            if (child == view) {
                setCurrentScreen(pos, animate);
                break;
            }
        }
    }

    /**
     * Sets the current screen.
     *
     * @param newScreenWithinVisibleChildren The new screen.
     * @param animate True to smoothly scroll to the screen, false to snap instantly
     */
    public void setCurrentScreen(final int newScreenWithinVisibleChildren, final boolean animate) {
        updateVisibleChildren();
        if (newScreenWithinVisibleChildren == mCurrentScreen && getCurrentView() == mCurrentView)
            return;

        setCurrentScreen(newScreenWithinVisibleChildren);
        if (animate) {
            snapToScreen(mCurrentScreen, ANIMATION_SCREEN_SET_DURATION_MILLIS);
        } else {
            scrollTo(mCurrentScreen * getWidth(), 0);
        }
        invalidate();
    }

    private void setCurrentScreen(final int newScreenWithinVisibleChildren) {
        mCurrentScreen = Math.max(0, Math.min(newScreenWithinVisibleChildren, getVisibleChildCount() - 1));
        mCurrentView = getCurrentView();
        if (pagingStyle == PAGING_STYLE_CARDDECK) {
            final int childCount = getVisibleChildCount();
            for (int pos = 0; pos < childCount; ++pos) {
                final View child = visibleChildren.get(pos);
                child.setZ(pos == mCurrentScreen ? 0 : 1);
                child.setAlpha(1f);
            }
        }
    }

    /**
     * Sets the {@link OnScreenSwitchListener}.
     *
     * @param onScreenSwitchListener The listener for switch events.
     */
    public void setOnScreenSwitchListener(final OnScreenSwitchListener onScreenSwitchListener) {
        mOnScreenSwitchListener = onScreenSwitchListener;
    }

    /**
     * Snaps to the screen we think the user wants (the current screen for very small movements; the
     * next/prev screen for bigger movements).
     */
    private void snapToDestination() {
        final int screenWidth = getWidth();
        final int scrollX = getScrollX();
        int whichScreen = mCurrentScreen;
        final int deltaX = scrollX - (screenWidth * mCurrentScreen);

        // Check if they want to go to the prev. screen
        if ((deltaX < 0) && mCurrentScreen != 0
                && ((screenWidth / FRACTION_OF_SCREEN_WIDTH_FOR_SWIPE) < -deltaX)) {
            whichScreen--;
            // Check if they want to go to the next screen
        } else if ((deltaX > 0) && (mCurrentScreen + 1 != getVisibleChildCount())
                && ((screenWidth / FRACTION_OF_SCREEN_WIDTH_FOR_SWIPE) < deltaX)) {
            whichScreen++;
        }

        snapToScreen(whichScreen);
    }

    /**
     * Snap to a specific screen, animating automatically for a duration proportional to the
     * distance left to scroll.
     *
     * @param whichScreen Screen to snap to
     */
    private void snapToScreen(final int whichScreen) {
        snapToScreen(whichScreen, -1);
    }

    /**
     * Snaps to a specific screen, animating for a specific amount of time to get there.
     *
     * @param whichScreen Screen to snap to
     * @param duration -1 to automatically time it based on scroll distance; a positive number to
     *            make the scroll take an exact duration.
     */
    private void snapToScreen(final int whichScreen, final int duration) {
        /*
         * Modified by Yoni Samlan: Allow new snapping even during an ongoing scroll animation. This
         * is intended to make HorizontalPager work as expected when used in conjunction with a
         * RadioGroup used as "tabbed" controls. Also, make the animation take a percentage of our
         * normal animation time, depending how far they've already scrolled.
         */
        mNextScreen = Math.max(0, Math.min(whichScreen, getVisibleChildCount() - 1));
        final int newX = mNextScreen * getWidth();
        final int scrollX = getScrollX();
        final int delta = newX - scrollX;

        if (delta != 0) {
            if (duration < 0) {
                // E.g. if they've scrolled 80% of the way, only animation for 20% of the duration
                mScroller.startScroll(scrollX, 0, delta, 0,
                        (int) (Math.abs(delta) / (float) getWidth() * ANIMATION_SCREEN_SET_DURATION_MILLIS));
            } else {
                mScroller.startScroll(scrollX, 0, delta, 0, duration);
            }
        }

        invalidate();
    }

    /**
     * Listener for the event that the HorizontalPager switches to a new view.
     */
    public interface OnScreenSwitchListener {
        /**
         * Notifies listeners about the new screen. Runs after the animation completed.
         *
         * @param prevScreen The previous screen index.
         * @param newScreen The new screen index.
         */
        void onScreenSwitched(int prevScreen, int newScreen);
    }
}
