package com.liuzhenlin.overscroll;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.util.ArraySet;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListener;
import android.support.v4.view.ViewPropertyAnimatorUpdateListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.HorizontalScrollView;

import com.liuzhenlin.overscroll.listener.OverFlyingDetector;

import java.lang.reflect.Field;
import java.util.Set;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.support.v4.widget.ViewDragHelper.INVALID_POINTER;

/**
 * Created on 2017/12/23. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
@SuppressLint("LongLogTag")
public class HorizontalOverScrollView extends HorizontalScrollView implements OverScrollView,
        ViewPropertyAnimatorListener, ViewPropertyAnimatorUpdateListener,
        OverFlyingDetector.OnOverFlyingListener {
    // @formatter:off
    private static final String TAG = "HorizontalOverScrollView";
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

    public HorizontalOverScrollView(Context context) {
        this(context, null);
    }

    public HorizontalOverScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HorizontalOverScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.HorizontalOverScrollView, defStyleAttr, 0);
        setOverScrollEnabled(a.getBoolean(R.styleable
                .HorizontalOverScrollView_overscrollEnabled, true));
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

    protected final boolean isLayoutRtl() {
        return SDK_INT >= JELLY_BEAN_MR1 && (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
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
                        final float dx = isLayoutRtl() ? -(mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2]) :
                                mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2]; // 往水平结束端拉为正
                        final boolean atStart = isAtStart();
                        final boolean atEnd = isAtEnd();
                        // 当前布局不能左右滚动时 --> 不限制左右拉
                        if (atStart && atEnd)
                            mOverScrollEdge = OVERSCROLL_EDGE_START_OR_END;
                            // 往水平结束端拉
                        else if (atStart && dx > 0f)
                            mOverScrollEdge = OVERSCROLL_EDGE_START;
                            // 往水平开始端拉
                        else if (atEnd && dx < 0f)
                            mOverScrollEdge = OVERSCROLL_EDGE_END;
                        else break;
                        deliverOverScrollStartEventIfNeeded(mOverScrollEdge);
                        deliverOverScrollStateChangeIfNeeded(OVERSCROLL_STATE_TOUCH_SCROLL);
                        return true;
                    case OVERSCROLL_STATE_TOUCH_SCROLL:
                        initVelocityTracker();
                        mVelocityTracker.addMovement(ev);

                        final float deltaX = computeOverScrollDeltaX();
                        if (deltaX == 0f) return true;
                        switch (mOverScrollEdge) {
                            case OVERSCROLL_EDGE_START: {
                                final float transX = mInnerView.getTranslationX();
                                final boolean rtl = isLayoutRtl();
                                float newTransX = transX + deltaX;
                                if (!rtl && newTransX < 0f)
                                    newTransX = 0f;
                                else if (rtl && newTransX > 0f)
                                    newTransX = 0f;
                                // 移动布局
                                mInnerView.setTranslationX(newTransX);
                                deliverOverScrollDistanceChangeIfNeeded();

                                invalidateParentCachedTouchX();
                                if (newTransX == 0f) {
                                    endOverScroll();
                                    break;
                                }
                                return true;
                            }
                            case OVERSCROLL_EDGE_END: {
                                final float transX = mInnerView.getTranslationX();
                                final boolean rtl = isLayoutRtl();
                                float newTransX = transX + deltaX;
                                if (!rtl && newTransX > 0f)
                                    newTransX = 0f;
                                else if (rtl && newTransX < 0f)
                                    newTransX = 0f;
                                mInnerView.setTranslationX(newTransX);
                                deliverOverScrollDistanceChangeIfNeeded();

                                invalidateParentCachedTouchX();
                                if (newTransX == 0f) {
                                    endOverScroll();
                                    break;
                                }
                                return true;
                            }
                            case OVERSCROLL_EDGE_START_OR_END:
                                mInnerView.setTranslationX(mInnerView.getTranslationX() + deltaX);
                                deliverOverScrollDistanceChangeIfNeeded();
                                return true;
                        }
                        break;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mVelocityTracker == null) break;
                mVelocityTracker.computeCurrentVelocity(1000);
                final float velocityX = mVelocityTracker.getXVelocity(mActivePointerId);
                recycleVelocityTracker();

                if (Math.abs(velocityX) >= mOverflyingDetector.getOverFlyingMinimumVelocity())
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

    private float computeOverScrollDeltaX() {
        if (mOverScrollState != OVERSCROLL_STATE_TOUCH_SCROLL)
            return 0f;
        final float deltaX = mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2];
        final float transX = mInnerView.getTranslationX();
        // 向右拉时手指向左滑             // 向左拉时手指向右滑
        if (transX > 0f && deltaX < 0f || transX < 0f && deltaX > 0f)
            return deltaX;
        else {
            MarginLayoutParams mlp = (MarginLayoutParams) mInnerView.getLayoutParams();
            final float ratio = Math.abs(mInnerView.getTranslationX()) /
                    ((getWidth() - getPaddingLeft() - getPaddingRight() - mlp.leftMargin - mlp.rightMargin) * 0.95f);
            return (float) (1d / (2d + Math.tan(Math.PI / 2d * ratio)) * deltaX);
        }
    }

    public void smoothSpringBack() {
        final float transX = mInnerView.getTranslationX();
        if (transX != 0f)
            animateOverScroll(0f, DURATION_SPRING_BACK);
        else
            endOverScroll();
    }

    public void animateOverScroll(float toTransX, int duration) {
        final boolean rtl = isLayoutRtl();
        final float transX = mInnerView.getTranslationX();
        final float dx = toTransX - transX;
        if (dx == 0) return;
        if (rtl)
            mOverScrollEdge = dx < 0 ? OVERSCROLL_EDGE_START : OVERSCROLL_EDGE_END;
        else
            mOverScrollEdge = dx > 0 ? OVERSCROLL_EDGE_START : OVERSCROLL_EDGE_END;
        ViewCompat.animate(mInnerView).translationX(toTransX)
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

    public boolean isAtStart() {
        // noinspection deprecation
        return !ViewCompat.canScrollHorizontally(this, isLayoutRtl() ? 1 : -1);
    }

    public boolean isAtEnd() {
        // noinspection deprecation
        return !ViewCompat.canScrollHorizontally(this, isLayoutRtl() ? -1 : 1);
    }

    @Override
    public void onStartEdgeOverFling(float overWidth, int duration) {
        animateOverScroll(isLayoutRtl() ? -overWidth : overWidth, duration);
    }

    @Override
    public void onEndEdgeOverFling(float overWidth, int duration) {
        animateOverScroll(isLayoutRtl() ? overWidth : -overWidth, duration);
    }

    @Override
    public void onTopEdgeOverFling(float overHeight, int duration) {
    }

    @Override
    public void onBottomEdgeOverFling(float overHeight, int duration) {
    }

    protected class OverFlyingDetector extends com.liuzhenlin.overscroll.listener.OverFlyingDetector {
        public OverFlyingDetector() {
            super(HorizontalOverScrollView.this, HorizontalOverScrollView.this);
        }

        @Override
        protected boolean isViewAtTop() {
            return false;
        }

        @Override
        protected boolean isViewAtBottom() {
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
        final float transX = mInnerView.getTranslationX();
        final float dist = mOverScrollEdge == OVERSCROLL_EDGE_START_OR_END ? transX : Math.abs(transX);
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

    private Field mLastMotionXField;

    /**
     * Refresh the cached touch X {@link HorizontalScrollView#mLastMotionX}
     * of {@link HorizontalScrollView} to ensure it will scroll left or right
     * within {@code Math.abs(mTouchX[mTouchX.length-1] - mTouchX[mTouchX.length-2])} px
     * when it receives touch event again.
     */
    private void invalidateParentCachedTouchX() {
        try {
            if (mLastMotionXField == null) {
                // noinspection JavaReflectionMemberAccess
                mLastMotionXField = HorizontalScrollView.class.getDeclaredField("mLastMotionX");
                mLastMotionXField.setAccessible(true);
            }
            mLastMotionXField.set(this, (int) (mTouchX[mTouchX.length - 2] + 0.5f));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}