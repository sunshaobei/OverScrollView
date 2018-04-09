package com.liuzhenlin.overscroll;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.TranslateAnimation;
import android.widget.EdgeEffect;
import android.widget.HorizontalScrollView;

import com.liuzhenlin.overscroll.listener.OnOverFlyingListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static android.support.v4.widget.ViewDragHelper.INVALID_POINTER;

/**
 * Created on 2017/12/23. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
@SuppressLint("LongLogTag")
public class HorizontalOverScrollView extends HorizontalScrollView
        implements OverScrollBase, Animation.AnimationListener {
    // @formatter:off
    private static final String TAG = "HorizontalOverScrollView";

    private View mInnerView;

    // 子View相对于当前view的位置
    private int mChildLeft;
    private int mChildRight;

    private int mChildLeftMargins;
    private int mChildRightMargins;

    protected final int mTouchSlop;

    private int mViewFlags;

    /** 标志当前View正在滚动 */
    private static final int FLAG_SCROLLING = 1;

    /** 标志当前View正在过度滚动 */
    private static final int FLAG_OVERSCROLLING = 1 << 1;

    /** 标志手指正在拖拽着当前view，使其发生滚动 */
    private static final  int FLAG_DRAGGED_OVERSCROLLING = 1 << 2;

    /**
     * 标志用户设置了当前View可以过度滚动
     * @see #setOverscrollEnabled(boolean)
     */
    private static final int FLAG_OVERSCROLL_ENABLED_BY_USER = 1 << 3;

    private int mActivePointerId = INVALID_POINTER;

    /** 按下时点的横坐标 */
    private int mDownX;
    /** 按下时点的纵坐标 */
    private int mDownY;

    /** 当前点的横坐标 */
    private int mCurrX;
    /** 当前点的纵坐标 */
    private int mCurrY;

    /** 前一个点的横坐标 */
    private int mLastX;
    /** 前一个点的纵坐标 */
    private int mLastY;

    /** 移动时水平方向总共移动的像素点 */
    private int mTotalAbsDeltaX;
    /** 移动时竖直方向总共移动的像素点 */
    private int mTotalAbsDeltaY;

    /** 是否消费了当前的touch事件 */
    private boolean mIsConsumed;

    private final OverFlyingDetector mOverflyingDetector;

    @OverscrollEdge
    private int mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;

    private TranslateAnimation mOverscrollAnim;
    private final Interpolator mInterpolator = new DecelerateInterpolator();
    // @formatter:on

    public boolean isScrolling() {
        return (mViewFlags & FLAG_SCROLLING) != 0;
    }

    public boolean isOverscrolling() {
        return (mViewFlags & FLAG_OVERSCROLLING) != 0;
    }

    public boolean isDraggedOverscrolling() {
        return (mViewFlags & FLAG_DRAGGED_OVERSCROLLING) != 0;
    }

    public boolean isOverscrollEnabled() {
        return getChildCount() > 0 && (mViewFlags & FLAG_OVERSCROLL_ENABLED_BY_USER) != 0;
    }

    public void setOverscrollEnabled(boolean enabled) {
        if (enabled) {
            mViewFlags |= FLAG_OVERSCROLL_ENABLED_BY_USER;
            // 禁用拉到两端时发荧光的效果
            setOverScrollMode(OVER_SCROLL_NEVER);
        } else
            mViewFlags &= ~FLAG_OVERSCROLL_ENABLED_BY_USER;
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
                mCurrX = mDownX = (int) (ev.getX(actionIndex) + 0.5f);
                mCurrY = mDownY = (int) (ev.getY(actionIndex) + 0.5f);
                break;
            case MotionEvent.ACTION_MOVE:
                final int index = ev.findPointerIndex(mActivePointerId);
                if (index < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id "
                            + mActivePointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }
                mCurrX = (int) (ev.getX(index) + 0.5f);
                mCurrY = (int) (ev.getY(index) + 0.5f);
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
            mLastX = mCurrX = (int) (ev.getX(newPointerIndex) + 0.5f);
            mLastY = mCurrY = (int) (ev.getY(newPointerIndex) + 0.5f);
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    private void markCurrXY(MotionEvent ev) {
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mTotalAbsDeltaX = mTotalAbsDeltaY = 0;
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
                if (!isScrolling() && isTendToScrollCurrView()) {
                    mViewFlags |= FLAG_SCROLLING;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mViewFlags &= ~FLAG_SCROLLING;
                break;
        }
        markCurrXY(ev);
        return mIsConsumed || super.onTouchEvent(ev);
    }

    @Override
    public boolean handleOverscroll(MotionEvent e) {
        if (!isOverscrollEnabled()) {
            return false;
        }
        mOverflyingDetector.onTouchEvent(e);

        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                resolveInnerViewBounds();
                break;
            case MotionEvent.ACTION_MOVE:
                final int deltaX = computeOverscrollDeltaX();
                if (deltaX == 0 && isDraggedOverscrolling()) {
                    return true;
                }
                switch (mOverscrollEdge) {
                    case OVERSCROLL_EDGE_UNSPECIFIED: {
                        if (!isScrolling()) {
                            return false;
                        }
                        final int dx = mCurrX - mDownX;
                        final boolean atLeft = isAtLeft();
                        final boolean atRight = isAtRight();
                        // 当前布局不能左右滚动时 --> 不限制右拉和左拉
                        if (atLeft && atRight) {
                            mOverscrollEdge = OVERSCROLL_EDGE_LEFT_OR_RIGHT;
                            // 右拉
                        } else if (atLeft && dx > 0) {
                            mOverscrollEdge = OVERSCROLL_EDGE_LEFT;
                            // 左拉
                        } else if (atRight && dx < 0) {
                            mOverscrollEdge = OVERSCROLL_EDGE_RIGHT;
                        } else
                            return false;
                        mViewFlags |= FLAG_OVERSCROLLING | FLAG_DRAGGED_OVERSCROLLING;
                        return true;
                    }

                    case OVERSCROLL_EDGE_LEFT: {
                        if (!isAtLeft()) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final int dx = mInnerView.getLeft() + deltaX < mChildLeft ? 0 : deltaX;
                        // 移动布局
                        mInnerView.offsetLeftAndRight(dx);

                        invalidateParentCachedTouchX();
                    }
                    return true;

                    case OVERSCROLL_EDGE_RIGHT: {
                        if (!isAtRight()) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final int dx = mInnerView.getRight() + deltaX > mChildRight ? 0 : deltaX;
                        mInnerView.offsetLeftAndRight(dx);

                        invalidateParentCachedTouchX();
                    }
                    return true;

                    case OVERSCROLL_EDGE_LEFT_OR_RIGHT:
                        mInnerView.offsetLeftAndRight(deltaX);
                        return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDraggedOverscrolling()) {
                    smoothSpringBack();
                    endDrag();// clear scroll state
                    mViewFlags &= ~FLAG_DRAGGED_OVERSCROLLING;
                    return true;
                }
                break;
        }
        return false;
    }

    private void resolveInnerViewBounds() {
        if (mInnerView != null) {
            mChildLeftMargins = getPaddingLeft();
            mChildRightMargins = getPaddingRight();
            if (mInnerView.getLayoutParams() instanceof MarginLayoutParams) {
                MarginLayoutParams mlp = (MarginLayoutParams) mInnerView.getLayoutParams();
                mChildLeftMargins += mlp.leftMargin;
                mChildRightMargins += mlp.rightMargin;
            }
            mChildLeft = mChildLeftMargins;
            mChildRight = mChildLeft + mInnerView.getMeasuredWidth();
        }
    }

    private void cancelDraggedOverscrolling() {
        mViewFlags &= ~(FLAG_DRAGGED_OVERSCROLLING | FLAG_OVERSCROLLING);
        mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
    }

    @Override
    public boolean isTendToScrollCurrView() {
        final int absDX = Math.abs(mCurrX - mDownX);
        final int absDY = Math.abs(mCurrY - mDownY);
        return absDX > absDY && absDX >= mTouchSlop
                || mTotalAbsDeltaX > mTotalAbsDeltaY && mTotalAbsDeltaX >= mTouchSlop;
    }

    @Override
    public int computeOverscrollDeltaY() {
        return 0;
    }

    @Override
    public int computeOverscrollDeltaX() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_LEFT:
            case OVERSCROLL_EDGE_RIGHT:
            case OVERSCROLL_EDGE_LEFT_OR_RIGHT:
                final int deltaX = mCurrX - mLastX;
                if (isPushingBack()) {
                    return deltaX;
                } else {
                    final double ratio = (double) Math.abs(mInnerView.getLeft() - mChildLeft)
                            / (double) (getWidth() - mChildLeftMargins - mChildRightMargins);
                    return (int) (1d / (2d + Math.tan(Math.PI / 2d * ratio)) * (double) deltaX);
                }
        }
        return 0;
    }

    /**
     * 是否向右拉时手指向左滑动或向左拉时手指向右滑动
     */
    @Override
    public boolean isPushingBack() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_LEFT:
            case OVERSCROLL_EDGE_RIGHT:
            case OVERSCROLL_EDGE_LEFT_OR_RIGHT:
                final int deltaX = mCurrX - mLastX;// 向右滑动为正
                // 向右拉时手指向左滑动
                return (mInnerView.getLeft() > mChildLeft && deltaX < 0 ||
                        // 向左拉时手指向右滑动
                        mInnerView.getLeft() < mChildLeft && deltaX > 0);
        }
        return false;
    }

    @Override
    public boolean isAtTheStart() {
        return isAtLeft();
    }

    @Override
    public boolean isAtTheEnd() {
        return isAtRight();
    }

    public boolean isAtLeft() {
        return getScrollX() == 0 || !canScrollHorizontally(-1);
    }

    public boolean isAtRight() {
        final int scrollRange = mInnerView.getMeasuredWidth()
                - (getWidth() - mChildLeftMargins - mChildRightMargins);
        final int scrollX = getScrollX();
        return scrollX == scrollRange || !canScrollHorizontally(1);
    }

    /**
     * @param from     {@link #mInnerView#getLeft()} 当前子view的左部位置偏移量
     * @param to       最终子view的左部位置偏移量
     * @param duration 动画持续时间
     */
    @Override
    public void startHeaderOverscrollAnim(int from, int to, int duration) {
        startOverscrollAnim(from, to, duration);
    }

    /**
     * @param from     {@link #mInnerView#getRight()} 当前子view的右部位置偏移量
     * @param to       最终子view的右部位置偏移量
     * @param duration 动画持续时间
     */
    @Override
    public void startFooterOverscrollAnim(int from, int to, int duration) {
        startOverscrollAnim(from - mInnerView.getMeasuredWidth(),
                to - mInnerView.getMeasuredWidth(), duration);
    }

    private void startOverscrollAnim(int fromLeft, int toLeft, int duration) {
        if (fromLeft != toLeft && hasAnimationFinished()) {
            mViewFlags |= FLAG_OVERSCROLLING;
            mOverscrollAnim = new TranslateAnimation(fromLeft - toLeft, 0, 0, 0);
            mOverscrollAnim.setDuration(duration);
            mOverscrollAnim.setInterpolator(mInterpolator);
            mOverscrollAnim.setAnimationListener(this);
            // 开启回弹动画
            mInnerView.startAnimation(mOverscrollAnim);
            mInnerView.offsetLeftAndRight(toLeft - fromLeft);
        }
    }

    private boolean hasAnimationFinished() {
        if (mOverscrollAnim != null) {
            Log.e(TAG, "can not start this over-scroll animation " +
                    "till the last animation has finished");
            return false;
        }
        return true;
    }

    @Override
    public void onAnimationStart(Animation animation) {
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        mOverscrollAnim = null;
        smoothSpringBack();
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
    }

    @Override
    public void smoothSpringBack() {
        if (mInnerView.getLeft() != mChildLeft) {
            startHeaderOverscrollAnim(mInnerView.getLeft(), mChildLeft, DURATION_SPRING_BACK);
        } else if (mInnerView.getRight() != mChildRight) {
            startFooterOverscrollAnim(mInnerView.getRight(), mChildRight, DURATION_SPRING_BACK);
        } else {
            mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
            mViewFlags &= ~(FLAG_DRAGGED_OVERSCROLLING | FLAG_OVERSCROLLING);
        }
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
            startHeaderOverscrollAnim(mChildLeft, mChildLeft + overWidth, duration);
        }

        @Override
        protected void onRightOverFling(int overWidth, int duration) {
            mOverscrollEdge = OVERSCROLL_EDGE_RIGHT;
            startFooterOverscrollAnim(mChildRight, mChildRight - overWidth, duration);
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
            return isAtTheStart();
        }

        @Override
        protected boolean isAtFarRight() {
            return isAtTheEnd();
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
            mLastMotionXField.set(this, mLastX);
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
            if (getOverScrollMode() != OVER_SCROLL_NEVER) {
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