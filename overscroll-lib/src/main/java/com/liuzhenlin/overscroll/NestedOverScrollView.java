package com.liuzhenlin.overscroll;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.util.ArraySet;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListener;
import android.support.v4.view.ViewPropertyAnimatorUpdateListener;
import android.support.v4.widget.NestedScrollView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.liuzhenlin.overscroll.listener.OverFlyingDetector;

import java.lang.reflect.Field;
import java.util.Set;

import static android.support.v4.widget.ViewDragHelper.INVALID_POINTER;

/**
 * Created on 2017/12/18. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
public class NestedOverScrollView extends NestedScrollView implements OverScrollView,
        ViewPropertyAnimatorListener, ViewPropertyAnimatorUpdateListener,
        OverFlyingDetector.OnOverFlyingListener {
    // @formatter:off
    private static final String TAG = "NestedOverScrollView";
    private static final boolean DEBUG = false;

    private View mInnerView;

    protected final int mTouchSlop;

    private int mActivePointerId = INVALID_POINTER;

    private final float[] mTouchX = new float[2];
    private final float[] mTouchY = new float[2];

    private VelocityTracker mVelocityTracker;

    /**
     * 当前View是否可以过度滚动
     * @see #setOverScrollEnabled(boolean)
     */
    private boolean mIsOverScrollEnabled;

    @OverScrollEdge
    private int mOverScrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;

    @OverScrollState
    private int mOverScrollState = OVERSCROLL_STATE_IDLE;

    private float mOverScrollDist;

    private final OverFlyingDetector mOverflyingDetector;

    private static final int DURATION_SPRING_BACK = 250;

    private final Interpolator mInterpolator = new DecelerateInterpolator();

    private boolean mIsAnimRunning;
    // @formatter:on

    public boolean isOverScrollEnabled() {
        return mIsOverScrollEnabled;
    }

    public void setOverScrollEnabled(boolean enabled) {
        if (enabled)
            // 禁用拉到两端时发荧光的效果
            setOverScrollMode(OVER_SCROLL_NEVER);
        else
            setOverScrollMode(OVER_SCROLL_ALWAYS);
        mIsOverScrollEnabled = enabled;
    }

    public boolean isOverScrolling() {
        return mOverScrollState != OVERSCROLL_STATE_IDLE;
    }

    @OverScrollEdge
    public int getOverScrollEdge() {
        return mOverScrollEdge;
    }

    @OverScrollState
    public int getOverScrollState() {
        return mOverScrollState;
    }

    public float getOverScrollDistance() {
        return mOverScrollDist;
    }

    public NestedOverScrollView(Context context) {
        this(context, null);
    }

    public NestedOverScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NestedOverScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NestedOverScrollView, defStyleAttr, 0);
        setOverScrollEnabled(a.getBoolean(R.styleable
                .NestedOverScrollView_overscrollEnabled, true));
        a.recycle();

        mOverflyingDetector = new OverFlyingDetector();
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        if (DEBUG)
            addOnOverScrollListener(new OnOverScrollListener() {
                @Override
                public void onOverScrollStart(OverScrollView view, int edge) {
                    Log.d(TAG, "onOverScrollStart   edge=" + edge);
                }

                @Override
                public void onOverScrollEnd(OverScrollView view, int edge) {
                    Log.d(TAG, "onOverScrollEnd   edge=" + edge);
                }

                @Override
                public void onOverScrollDistanceChange(OverScrollView view, float distance) {
                    Log.d(TAG, "onOverScrollDistanceChange   distance=" + distance);
                }

                @Override
                public void onOverScrollStateChange(OverScrollView view, int state) {
                    Log.d(TAG, "onOverScrollStateChange   state=" + state);
                }
            });
    }

    @Override
    public void setOverScrollMode(int overScrollMode) {
        if (mIsOverScrollEnabled && getOverScrollMode() == OVER_SCROLL_NEVER)
            return;
        super.setOverScrollMode(overScrollMode);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() == 1)
            mInnerView = getChildAt(0);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        mInnerView = child;
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        mInnerView = null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                final int actionIndex = ev.getActionIndex();
                mActivePointerId = ev.getPointerId(actionIndex);
                markCurrTouchPoint(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                final int index = ev.findPointerIndex(mActivePointerId);
                if (index < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id "
                            + mActivePointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }
                markCurrTouchPoint(ev);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = INVALID_POINTER;
                break;
        }
        if (mIsOverScrollEnabled && mInnerView != null)
            mOverflyingDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up.
            // Choose a new active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            markCurrTouchPoint(ev);
        }
    }

    private void markCurrTouchPoint(MotionEvent ev) {
        final int actionIndex = ev.findPointerIndex(mActivePointerId);
        System.arraycopy(mTouchX, 1, mTouchX, 0, mTouchX.length - 1);
        mTouchX[mTouchX.length - 1] = ev.getX(actionIndex);
        System.arraycopy(mTouchY, 1, mTouchY, 0, mTouchY.length - 1);
        mTouchY[mTouchY.length - 1] = ev.getY(actionIndex);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return handleOverScroll(ev) || super.onTouchEvent(ev);
    }

    private void initVelocityTracker() {
        if (mVelocityTracker == null)
            mVelocityTracker = VelocityTracker.obtain();
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @SuppressLint("SwitchIntDef")
    @Override
    public boolean handleOverScroll(MotionEvent ev) {
        if (!(mIsOverScrollEnabled && mInnerView != null))
            return false;

        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                switch (mOverScrollState) {
                    case OVERSCROLL_STATE_IDLE:
                        final float dy = mTouchY[mTouchY.length - 1] - mTouchY[mTouchY.length - 2];
                        final boolean atTop = isAtTop();
                        final boolean atBottom = isAtBottom();
                        // 当前布局不能上下滚动时 --> 不限制下拉和上拉
                        if (atTop && atBottom)
                            mOverScrollEdge = OVERSCROLL_EDGE_TOP_OR_BOTTOM;
                            // 下拉
                        else if (atTop && dy > 0)
                            mOverScrollEdge = OVERSCROLL_EDGE_TOP;
                            // 上拉
                        else if (atBottom && dy < 0)
                            mOverScrollEdge = OVERSCROLL_EDGE_BOTTOM;
                        else break;
                        deliverOverScrollStartEventIfNeeded(mOverScrollEdge);
                        deliverOverScrollStateChangeIfNeeded(OVERSCROLL_STATE_TOUCH_SCROLL);
                        return true;
                    case OVERSCROLL_STATE_TOUCH_SCROLL:
                        initVelocityTracker();
                        mVelocityTracker.addMovement(ev);

                        final float deltaY = computeOverScrollDeltaY();
                        if (deltaY == 0f) return true;
                        switch (mOverScrollEdge) {
                            case OVERSCROLL_EDGE_TOP: {
                                final float transY = mInnerView.getTranslationY();
                                float newTransY = transY + deltaY;
                                if (newTransY < 0f) newTransY = 0f;
                                // 移动布局
                                mInnerView.setTranslationY(newTransY);
                                deliverOverScrollDistanceChangeIfNeeded();

                                if (newTransY < transY) {
                                    invalidateParentCachedTouchY();
                                    if (newTransY == 0f)
                                        endOverScroll();
                                    return true;
                                }
                                // Not consume this event when user scroll this view down,
                                // to enable nested scrolling.
                                break;
                            }
                            case OVERSCROLL_EDGE_BOTTOM: {
                                final float transY = mInnerView.getTranslationY();
                                float newTransY = transY + deltaY;
                                if (newTransY > 0f) newTransY = 0f;
                                mInnerView.setTranslationY(newTransY);
                                deliverOverScrollDistanceChangeIfNeeded();

                                if (newTransY > transY) {
                                    invalidateParentCachedTouchY();
                                    if (newTransY == 0f)
                                        endOverScroll();
                                    return true;
                                }
                                break;
                            }
                            case OVERSCROLL_EDGE_TOP_OR_BOTTOM: {
                                final float transY = mInnerView.getTranslationY();
                                final float newTransY = transY + deltaY;
                                mInnerView.setTranslationY(newTransY);
                                deliverOverScrollDistanceChangeIfNeeded();

                                if (newTransY > 0f && newTransY < transY
                                        || newTransY < 0f && newTransY > transY) {
                                    invalidateParentCachedTouchY();
                                    return true;
                                }
                                break;
                            }
                        }
                        break;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mVelocityTracker == null) break;
                mVelocityTracker.computeCurrentVelocity(1000);
                final float velocityY = mVelocityTracker.getYVelocity(mActivePointerId);
                recycleVelocityTracker();

                if (Math.abs(velocityY) >= mOverflyingDetector.getOverFlyingMinimumVelocity())
                    break;
            case MotionEvent.ACTION_CANCEL:
                if (mOverScrollState == OVERSCROLL_STATE_TOUCH_SCROLL)
                    smoothSpringBack();
                recycleVelocityTracker();
                break;
        }
        return false;
    }

    private void endOverScroll() {
        if (!mIsAnimRunning) {
            deliverOverScrollEndEventIfNeeded(mOverScrollEdge);
            deliverOverScrollStateChangeIfNeeded(OVERSCROLL_STATE_IDLE);
            mOverScrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
        }
    }

    private float computeOverScrollDeltaY() {
        if (mOverScrollState != OVERSCROLL_STATE_TOUCH_SCROLL)
            return 0f;
        final float deltaY = mTouchY[mTouchY.length - 1] - mTouchY[mTouchY.length - 2];
        final float transY = mInnerView.getTranslationY();
        // 向下拉时手指向上滑动           // 向上拉时手指向下滑动
        if (transY > 0f && deltaY < 0f || transY < 0f && deltaY > 0f)
            return deltaY;
        else {
            MarginLayoutParams mlp = (MarginLayoutParams) mInnerView.getLayoutParams();
            final float ratio = Math.abs(mInnerView.getTranslationY()) /
                    ((getHeight() - getPaddingTop() - getPaddingBottom() - mlp.topMargin - mlp.bottomMargin) * 0.95f);
            return (float) (1d / (2d + Math.tan(Math.PI / 2d * ratio)) * deltaY);
        }
    }

    public void smoothSpringBack() {
        if (mInnerView.getTranslationY() != 0f)
            animateOverScroll(0f, DURATION_SPRING_BACK);
        else
            endOverScroll();
    }

    public void animateOverScroll(float toTransY, int duration) {
        final float transY = mInnerView.getTranslationY();
        final float dy = toTransY - transY;
        if (dy == 0) return;
        mOverScrollEdge = dy > 0 ? OVERSCROLL_EDGE_TOP : OVERSCROLL_EDGE_BOTTOM;
        ViewCompat.animate(mInnerView).translationY(toTransY)
                .setDuration(duration)
                .setInterpolator(mInterpolator)
                .setListener(this).setUpdateListener(this).start();
    }

    @Override
    public void onAnimationStart(View view) {
        mIsAnimRunning = true;
        deliverOverScrollStartEventIfNeeded(mOverScrollEdge);
        deliverOverScrollStateChangeIfNeeded(OVERSCROLL_STATE_AUTO_SCROLL);
    }

    @Override
    public void onAnimationUpdate(View view) {
        deliverOverScrollDistanceChangeIfNeeded();
    }

    @Override
    public void onAnimationEnd(View view) {
        mIsAnimRunning = false;
        smoothSpringBack();
    }

    @Override
    public void onAnimationCancel(View view) {
    }

    public boolean isAtTop() {
        // noinspection deprecation
        return getScrollY() == 0 || !ViewCompat.canScrollVertically(this, -1);
    }

    public boolean isAtBottom() {
        MarginLayoutParams mlp = (MarginLayoutParams) mInnerView.getLayoutParams();
        final int scrollRange = mInnerView.getMeasuredHeight()
                - (getHeight() - getPaddingTop() - getPaddingBottom() - mlp.topMargin - mlp.bottomMargin);
        final int scrollY = getScrollY();
        // noinspection deprecation
        return scrollY == scrollRange || !ViewCompat.canScrollVertically(this, 1);
    }

    @Override
    public void onTopEdgeOverFling(float overHeight, int duration) {
        animateOverScroll(overHeight, duration);
    }

    @Override
    public void onBottomEdgeOverFling(float overHeight, int duration) {
        animateOverScroll(-overHeight, duration);
    }

    @Override
    public void onStartEdgeOverFling(float overWidth, int duration) {
    }

    @Override
    public void onEndEdgeOverFling(float overWidth, int duration) {
    }

    protected class OverFlyingDetector extends com.liuzhenlin.overscroll.listener.OverFlyingDetector {

        public OverFlyingDetector() {
            super(NestedOverScrollView.this, NestedOverScrollView.this);
        }

        @Override
        protected boolean isViewAtTop() {
            return isAtTop();
        }

        @Override
        protected boolean isViewAtBottom() {
            return isAtBottom();
        }

        @Override
        protected boolean isViewAtStart() {
            return false;
        }

        @Override
        protected boolean isViewAtEnd() {
            return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // OverScroll Listener
    ///////////////////////////////////////////////////////////////////////////

    private void deliverOverScrollStartEventIfNeeded(int edge) {
        if (!isOverScrolling() && mOnOverScrollListeners != null)
            for (OnOverScrollListener listener : mOnOverScrollListeners)
                listener.onOverScrollStart(this, edge);
    }

    private void deliverOverScrollStateChangeIfNeeded(int state) {
        if (mOverScrollState != state) {
            mOverScrollState = state;
            if (mOnOverScrollListeners != null)
                for (OnOverScrollListener listener : mOnOverScrollListeners)
                    listener.onOverScrollStateChange(this, mOverScrollState);
        }
    }

    private void deliverOverScrollDistanceChangeIfNeeded() {
        final float transY = mInnerView.getTranslationY();
        final float dist = mOverScrollEdge == OVERSCROLL_EDGE_START_OR_END ? transY : Math.abs(transY);
        if (mOverScrollDist != dist) {
            mOverScrollDist = dist;
            if (mOnOverScrollListeners != null)
                for (OnOverScrollListener listener : mOnOverScrollListeners)
                    listener.onOverScrollDistanceChange(this, mOverScrollDist);
        }
    }

    private void deliverOverScrollEndEventIfNeeded(int edge) {
        if (isOverScrolling() && mOnOverScrollListeners != null)
            for (OnOverScrollListener listener : mOnOverScrollListeners)
                listener.onOverScrollEnd(this, edge);
    }

    private Set<OnOverScrollListener> mOnOverScrollListeners;

    public void addOnOverScrollListener(OnOverScrollListener listener) {
        if (mOnOverScrollListeners == null)
            mOnOverScrollListeners = new ArraySet<>();
        mOnOverScrollListeners.add(listener);
    }

    public void removeOnOverScrollListener(OnOverScrollListener listener) {
        if (mOnOverScrollListeners != null)
            mOnOverScrollListeners.add(listener);
    }

    public void clearOnOverScrollListeners() {
        if (mOnOverScrollListeners != null)
            mOnOverScrollListeners.clear();
    }

    ///////////////////////////////////////////////////////////////////////////
    // reflection methods
    ///////////////////////////////////////////////////////////////////////////

    private Field mLastMotionYField;

    /**
     * Refresh the cached touch Y {@link NestedScrollView#mLastMotionY}
     * of {@link NestedScrollView} to ensure it will scroll up or down
     * within {@code Math.abs(mTouchY[mTouchY.length-1] - mTouchY[mTouchY.length-2])} px
     * when it receives touch event again.
     */
    private void invalidateParentCachedTouchY() {
        try {
            if (mLastMotionYField == null) {
                mLastMotionYField = NestedScrollView.class.getDeclaredField("mLastMotionY");
                mLastMotionYField.setAccessible(true);
            }
            mLastMotionYField.set(this, (int) (mTouchY[mTouchY.length - 2] + 0.5f));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}