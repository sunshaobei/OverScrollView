package com.liuzhenlin.overscroll;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.IntDef;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.EdgeEffect;
import android.widget.HorizontalScrollView;

import com.liuzhenlin.overscroll.listener.OnOverFlyingListener;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
import static android.support.v4.widget.ViewDragHelper.INVALID_POINTER;

/**
 * Created on 2017/12/23. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
@SuppressLint("LongLogTag")
public class HorizontalOverScrollView extends HorizontalScrollView
        implements ViewPropertyAnimatorListener {
    // @formatter:off
    private static final String TAG = "HorizontalOverScrollView";

    private View mInnerView;

    private int mViewFlags;

    /** 标志当前View正在滚动 */
    private static final int VIEW_FLAG_SCROLLING = 1;

    /** 标志当前View正在过度滚动 */
    private static final int VIEW_FLAG_OVERSCROLLING = 1 << 1;

    /** 标志手指正在拖拽着当前view，使其发生滚动 */
    private static final  int VIEW_FLAG_DRAGGED_OVERSCROLLING = 1 << 2;

    /**
     * 标志用户设置了当前View可以过度滚动
     * @see #setOverscrollEnabled(boolean)
     */
    private static final int VIEW_FLAG_OVERSCROLL_ENABLED_BY_USER = 1 << 3;

    protected final int mTouchSlop;

    private int mActivePointerId = INVALID_POINTER;

    /** 按下时点的横坐标 */
    private float mDownX;
    /** 按下时点的纵坐标 */
    private float mDownY;

    /** 当前点的横坐标 */
    private float mCurrX;
    /** 当前点的纵坐标 */
    private float mCurrY;

    /** 前一个点的横坐标 */
    private float mLastX;
    /** 前一个点的纵坐标 */
    private float mLastY;

    /** 移动时水平方向总共移动的像素点 */
    private float mTotalAbsDeltaX;
    /** 移动时竖直方向总共移动的像素点 */
    private float mTotalAbsDeltaY;

    /** 是否消费了当前的touch事件 */
    private boolean mIsConsumed;

    @OverscrollEdge
    private int mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;

    public static final int OVERSCROLL_EDGE_UNSPECIFIED = 0;
    public static final int OVERSCROLL_EDGE_LEFT = 1;
    public static final int OVERSCROLL_EDGE_RIGHT = 2;
    public static final int OVERSCROLL_EDGE_LEFT_OR_RIGHT = 3;
    @IntDef({
            OVERSCROLL_EDGE_UNSPECIFIED,
            OVERSCROLL_EDGE_LEFT, OVERSCROLL_EDGE_RIGHT, OVERSCROLL_EDGE_LEFT_OR_RIGHT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OverscrollEdge {
    }

    /** 回弹时间 */
    private static final int DURATION_SPRING_BACK = 250; // ms

    private final OverFlyingDetector mOverflyingDetector;

    private final Interpolator mInterpolator = new DecelerateInterpolator();
    // @formatter:on

    public boolean isScrolling() {
        return (mViewFlags & VIEW_FLAG_SCROLLING) != 0;
    }

    public boolean isOverscrolling() {
        return (mViewFlags & VIEW_FLAG_OVERSCROLLING) != 0;
    }

    public boolean isDraggedOverscrolling() {
        return (mViewFlags & VIEW_FLAG_DRAGGED_OVERSCROLLING) != 0;
    }

    public boolean isOverscrollEnabled() {
        return getChildCount() > 0 && (mViewFlags & VIEW_FLAG_OVERSCROLL_ENABLED_BY_USER) != 0;
    }

    public void setOverscrollEnabled(boolean enabled) {
        if (enabled) {
            mViewFlags |= VIEW_FLAG_OVERSCROLL_ENABLED_BY_USER;
            // 禁用拉到两端时发荧光的效果
            setOverScrollMode(OVER_SCROLL_NEVER);
        } else
            mViewFlags &= ~VIEW_FLAG_OVERSCROLL_ENABLED_BY_USER;
    }

    @OverscrollEdge
    public int getOverscrollEdge() {
        return mOverscrollEdge;
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
        setOverscrollEnabled(a.getBoolean(R.styleable
                .HorizontalOverScrollView_overscrollEnabled, true));
        a.recycle();

        mOverflyingDetector = new OverFlyingDetector();
        mTouchSlop = mOverflyingDetector.mTouchSlop;
    }

    @Override
    public void setOverScrollMode(int overScrollMode) {
        if (isOverscrollEnabled() && getOverScrollMode() == View.OVER_SCROLL_NEVER) {
            return;
        }
        super.setOverScrollMode(overScrollMode);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() == 1) {
            mInnerView = getChildAt(0);
        }
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
                mCurrX = mDownX = ev.getX(actionIndex);
                mCurrY = mDownY = ev.getY(actionIndex);
                break;
            case MotionEvent.ACTION_MOVE:
                final int index = ev.findPointerIndex(mActivePointerId);
                if (index < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id "
                            + mActivePointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }
                mCurrX = ev.getX(index);
                mCurrY = ev.getY(index);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = INVALID_POINTER;
                break;
        }
        mIsConsumed = handleOverscroll(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastX = mCurrX = ev.getX(newPointerIndex);
            mLastY = mCurrY = ev.getY(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    private void markCurrXY(MotionEvent ev) {
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mTotalAbsDeltaX = mTotalAbsDeltaY = 0f;
            case MotionEvent.ACTION_POINTER_DOWN:
                mLastX = mCurrX;
                mLastY = mCurrY;
                break;
            case MotionEvent.ACTION_MOVE:
                mTotalAbsDeltaX += Math.abs(mCurrX - mLastX);
                mTotalAbsDeltaY += Math.abs(mCurrY - mLastY);
                mLastX = mCurrX;
                mLastY = mCurrY;
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (!isScrolling() && isTendToScrollHorizontally()) {
                    mViewFlags |= VIEW_FLAG_SCROLLING;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mViewFlags &= ~VIEW_FLAG_SCROLLING;
                break;
        }
        markCurrXY(ev);
        return mIsConsumed || super.onTouchEvent(ev);
    }

    public boolean isTendToScrollHorizontally() {
        final float absDX = Math.abs(mCurrX - mDownX);
        final float absDY = Math.abs(mCurrY - mDownY);
        return absDX > absDY && absDX >= mTouchSlop
                || mTotalAbsDeltaX > mTotalAbsDeltaY && mTotalAbsDeltaX >= mTouchSlop;
    }

    protected boolean handleOverscroll(MotionEvent e) {
        if (!isOverscrollEnabled()) {
            return false;
        }
        mOverflyingDetector.onTouchEvent(e);

        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE:
                final float deltaX = computeOverscrollDeltaX();
                if (deltaX == 0f && isDraggedOverscrolling()) {
                    return true;
                }

                switch (mOverscrollEdge) {
                    case OVERSCROLL_EDGE_UNSPECIFIED: {
                        if (!isScrolling()) {
                            return false;
                        }
                        final float dx = mCurrX - mDownX;
                        final boolean atLeft = isAtLeft();
                        final boolean atRight = isAtRight();
                        // 当前布局不能左右滚动时 --> 不限制右拉和左拉
                        if (atLeft && atRight) {
                            mOverscrollEdge = OVERSCROLL_EDGE_LEFT_OR_RIGHT;
                            // 右拉
                        } else if (atLeft && dx > 0f) {
                            mOverscrollEdge = OVERSCROLL_EDGE_LEFT;
                            // 左拉
                        } else if (atRight && dx < 0f) {
                            mOverscrollEdge = OVERSCROLL_EDGE_RIGHT;
                        } else
                            return false;
                        mViewFlags |= VIEW_FLAG_OVERSCROLLING | VIEW_FLAG_DRAGGED_OVERSCROLLING;
                        return true;
                    }

                    case OVERSCROLL_EDGE_LEFT: {
                        final float transX = mInnerView.getTranslationX();
                        if (transX + deltaX < 0f) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final float newTransX = transX + (transX + deltaX < 0f ? 0f : deltaX);
                        // 移动布局
                        mInnerView.setTranslationX(newTransX);

                        invalidateParentCachedTouchX();
                    }
                    return true;

                    case OVERSCROLL_EDGE_RIGHT: {
                        final float transX = mInnerView.getTranslationX();
                        if (transX + deltaX > 0f) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final float newTransX = transX + (transX + deltaX > 0f ? 0f : deltaX);
                        mInnerView.setTranslationX(newTransX);

                        invalidateParentCachedTouchX();
                    }
                    return true;

                    case OVERSCROLL_EDGE_LEFT_OR_RIGHT:
                        mInnerView.setTranslationX(mInnerView.getTranslationX() + deltaX);
                        return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDraggedOverscrolling()) {
                    smoothSpringBack();
                    endDrag();// clear scroll state
                    mViewFlags &= ~VIEW_FLAG_DRAGGED_OVERSCROLLING;
                    return true;
                }
                break;
        }
        return false;
    }

    private float computeOverscrollDeltaX() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_LEFT:
            case OVERSCROLL_EDGE_RIGHT:
            case OVERSCROLL_EDGE_LEFT_OR_RIGHT:
                final float deltaX = mCurrX - mLastX;
                if (isPushingBack()) {
                    return deltaX;
                } else {
                    MarginLayoutParams mlp = (MarginLayoutParams) mInnerView.getLayoutParams();
                    final float ratio = Math.abs(mInnerView.getTranslationX()) /
                            ((getWidth() - getPaddingLeft() - getPaddingRight() - mlp.leftMargin - mlp.rightMargin) * 0.95f);
                    return (float) (1d / (2d + Math.tan(Math.PI / 2d * ratio)) * deltaX);
                }
        }
        return 0f;
    }

    /**
     * 是否向右拉时手指向左滑动或向左拉时手指向右滑动
     */
    private boolean isPushingBack() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_LEFT:
            case OVERSCROLL_EDGE_RIGHT:
            case OVERSCROLL_EDGE_LEFT_OR_RIGHT:
                final float deltaX = mCurrX - mLastX;// 向右滑动为正
                final float transX = mInnerView.getTranslationX();
                // 向右拉时手指向左滑动
                return (transX > 0f && deltaX < 0f ||
                        // 向左拉时手指向右滑动
                        transX < 0f && deltaX > 0f);
        }
        return false;
    }

    private void cancelDraggedOverscrolling() {
        mViewFlags &= ~(VIEW_FLAG_DRAGGED_OVERSCROLLING | VIEW_FLAG_OVERSCROLLING);
        mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
    }

    public boolean isAtLeft() {
        return getScrollX() == 0 || !ViewCompat.canScrollHorizontally(this, -1);
    }

    public boolean isAtRight() {
        MarginLayoutParams mlp = (MarginLayoutParams) mInnerView.getLayoutParams();
        final int scrollRange = mInnerView.getMeasuredWidth()
                - (getWidth() - getPaddingLeft() - getPaddingRight() - mlp.leftMargin - mlp.rightMargin);
        final int scrollX = getScrollX();
        return scrollX == scrollRange || !ViewCompat.canScrollHorizontally(this, 1);
    }

    public void smoothSpringBack() {
        if (mInnerView.getTranslationX() != 0f) {
            startOverscrollAnim(0f, DURATION_SPRING_BACK);
        } else {
            mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
            mViewFlags &= ~VIEW_FLAG_OVERSCROLLING;
        }
    }

    private void startOverscrollAnim(float toTransX, int duration) {
        if (mInnerView.getTranslationX() != toTransX) {
            ViewCompat.animate(mInnerView).translationX(toTransX)
                    .setDuration(duration)
                    .setInterpolator(mInterpolator)
                    .setListener(this).start();
        }
    }

    @Override
    public void onAnimationStart(View view) {
        mViewFlags |= VIEW_FLAG_OVERSCROLLING;
    }

    @Override
    public void onAnimationEnd(View view) {
        smoothSpringBack();
    }

    @Override
    public void onAnimationCancel(View view) {
        mInnerView.setTranslationX(0f);
    }

    public void forceEndOverscrollAnim() {
        ViewCompat.animate(mInnerView).cancel();
    }

    protected class OverFlyingDetector extends OnOverFlyingListener {
        protected OverFlyingDetector() {
            super(getContext());
        }

        @Override
        protected void onTopOverFling(int overHeight, int duration) {
        }

        @Override
        protected void onBottomOverFling(int overHeight, int duration) {
        }

        @Override
        protected void onLeftOverFling(int overWidth, int duration) {
            mOverscrollEdge = OVERSCROLL_EDGE_LEFT;
            startOverscrollAnim(overWidth, duration);
        }

        @Override
        protected void onRightOverFling(int overWidth, int duration) {
            mOverscrollEdge = OVERSCROLL_EDGE_RIGHT;
            startOverscrollAnim(-overWidth, duration);
        }

        @Override
        protected boolean isAtTop() {
            return false;
        }

        @Override
        protected boolean isAtBottom() {
            return false;
        }

        @Override
        protected boolean isAtFarLeft() {
            return isAtLeft();
        }

        @Override
        protected boolean isAtFarRight() {
            return isAtRight();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // reflection methods
    ///////////////////////////////////////////////////////////////////////////

    private Field mLastMotionXField;

    private Method mRecycleVelocityTrackerMethod;
    private Field mIsBeingDraggedField;
    private Field mActivePointerIdField;
    private EdgeEffect mEdgeGlowLeft;
    private EdgeEffect mEdgeGlowRight;

    /**
     * Refresh the cached touch X {@link HorizontalScrollView#mLastMotionX}
     * of {@link HorizontalScrollView} to ensure it will scroll left or right
     * within {@code Math.abs(mCurrX - mLastX)} px
     * when it receives touch event again.
     */
    private void invalidateParentCachedTouchX() {
        try {
            if (mLastMotionXField == null) {
                mLastMotionXField = HorizontalScrollView.class.getDeclaredField("mLastMotionX");
                mLastMotionXField.setAccessible(true);
            }
            mLastMotionXField.set(this, (int) (mLastX + 0.5f));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * clear scroll state of {@link HorizontalScrollView} and stop dragging.
     *
     * @see HorizontalScrollView#onTouchEvent(MotionEvent) {@code case MotionEvent.ACTION_CANCEL: }
     */
    protected final void endDrag() {
        try {
            if (mRecycleVelocityTrackerMethod == null) {
                Class<HorizontalScrollView> horizontalScrollView = HorizontalScrollView.class;
                mRecycleVelocityTrackerMethod = horizontalScrollView.getDeclaredMethod("recycleVelocityTracker");
                mRecycleVelocityTrackerMethod.setAccessible(true);

                mIsBeingDraggedField = horizontalScrollView.getDeclaredField("mIsBeingDragged");
                mIsBeingDraggedField.setAccessible(true);

                mActivePointerIdField = horizontalScrollView.getDeclaredField("mActivePointerId");
                mActivePointerIdField.setAccessible(true);
            }
            mRecycleVelocityTrackerMethod.invoke(this);
            mIsBeingDraggedField.set(this, false);
            mActivePointerIdField.set(this, INVALID_POINTER);
            if (SDK_INT >= ICE_CREAM_SANDWICH && getOverScrollMode() != OVER_SCROLL_NEVER) {
                if (mEdgeGlowLeft == null) {
                    Class<HorizontalScrollView> horizontalScrollView = HorizontalScrollView.class;
                    Field edgeGlowLeft = horizontalScrollView.getDeclaredField("mEdgeGlowLeft");
                    edgeGlowLeft.setAccessible(true);
                    mEdgeGlowLeft = (EdgeEffect) edgeGlowLeft.get(this);

                    Field edgeGlowRight = horizontalScrollView.getDeclaredField("mEdgeGlowRight");
                    edgeGlowRight.setAccessible(true);
                    mEdgeGlowRight = (EdgeEffect) edgeGlowRight.get(this);
                }
                mEdgeGlowLeft.onRelease();
                mEdgeGlowRight.onRelease();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}