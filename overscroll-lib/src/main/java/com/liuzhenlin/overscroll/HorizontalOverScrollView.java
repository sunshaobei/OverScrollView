package com.liuzhenlin.overscroll;

import android.animation.Animator;
import android.animation.ValueAnimator;
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
public class HorizontalOverScrollView extends HorizontalScrollView implements OverScrollBase {
    // @formatter:off
    private static final String TAG = "HorizontalOverScrollView";

    protected View mInnerView;

    // 子View相对于当前view的位置偏移量
    protected int mChildLeft;
    protected int mChildTop;
    protected int mChildRight;
    protected int mChildBottom;

    protected final int mTouchSlop;

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

    /** 当前view是否正在滚动 */
    private boolean mIsScrolling;

    private final OverFlyingDetector mOverflyingDetector;

    @OverscrollEdge
    protected int mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;

    /**
     * 是否可以过度滚动
     * @see #setOverscrollEnabled(boolean)
     */
    private boolean mOverscrollEnabled;// default true

    /** 手指是否正在拖拽着当前view，使其发生过度滚动 */
    private boolean mIsDraggedOverscrolling;
    // @formatter:on

    public boolean isScrolling() {
        return mIsScrolling;
    }

    public boolean isOverscrollEnabled() {
        return mOverscrollEnabled && mInnerView != null;
    }

    public void setOverscrollEnabled(boolean overscrollEnabled) {
        mOverscrollEnabled = overscrollEnabled;
        if (mOverscrollEnabled) {
            // 禁用拉到两端时发荧光的效果
            setOverScrollMode(OVER_SCROLL_NEVER);
        }
    }

    public boolean isOverscrolling() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_LEFT:
            case OVERSCROLL_EDGE_RIGHT:
            case OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT:
                return mOverscrollEnabled;
        }
        return false;
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
        if (mOverscrollEnabled && getOverScrollMode() == View.OVER_SCROLL_NEVER) {
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

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (!mIsScrolling) {
                    mIsScrolling = isTendToScrollCurrView();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsScrolling = false;
                break;
        }
        markCurrXY(ev);
        return mIsConsumed || super.onTouchEvent(ev);
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
    public boolean handleOverscroll(MotionEvent e) {
        if (!isOverscrollEnabled()) {
            return false;
        }
        mOverflyingDetector.onTouchEvent(e);

        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                resolveInnerViewOffsets();
                break;
            case MotionEvent.ACTION_MOVE:
                final int deltaX = computeOverscrollDeltaX();
                if (deltaX == 0 && mIsDraggedOverscrolling) {
                    return true;
                }
                switch (mOverscrollEdge) {
                    case OVERSCROLL_EDGE_UNSPECIFIED:
                        if (!mIsScrolling) {
                            return false;
                        }
                        final int dx = mCurrX - mDownX;
                        final boolean atLeft = isAtLeft();
                        final boolean atRight = isAtRight();
                        // 当前布局不能左右滚动时 --> 不限制右拉和左拉
                        if (atLeft && atRight) {
                            mOverscrollEdge = OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT;
                            // 右拉
                        } else if (atLeft && dx > 0) {
                            mOverscrollEdge = OVERSCROLL_EDGE_LEFT;
                            // 左拉
                        } else if (atRight && dx < 0) {
                            mOverscrollEdge = OVERSCROLL_EDGE_RIGHT;
                        } else break;
                        mIsDraggedOverscrolling = true;
                        break;

                    case OVERSCROLL_EDGE_LEFT: {
                        if (!isAtLeft()) {
                            if (isAtRight()) {
                                mOverscrollEdge = OVERSCROLL_EDGE_RIGHT;
                            }
                            break;
                        }

                        final int old = mInnerView.getLeft();
                        final int childLeft = old + deltaX < mChildLeft ? mChildLeft : old + deltaX;
                        // 移动布局
                        mInnerView.layout(childLeft, mChildTop,
                                childLeft + mInnerView.getMeasuredWidth(), mChildBottom);

                        invalidateParentCachedTouchX();
                        if (childLeft == mChildLeft) {
                            break;
                        }
                    }
                    return true;

                    case OVERSCROLL_EDGE_RIGHT: {
                        if (!isAtRight()) {
                            if (isAtLeft()) {
                                mOverscrollEdge = OVERSCROLL_EDGE_LEFT;
                            }
                            break;
                        }

                        final int old = mInnerView.getRight();
                        final int childRight = old + deltaX > mChildRight ?
                                mChildRight : old + deltaX;
                        mInnerView.layout(childRight - mInnerView.getMeasuredWidth(),
                                mChildTop, childRight, mChildBottom);

                        invalidateParentCachedTouchX();
                        if (childRight == mChildRight) {
                            break;
                        }
                    }
                    return true;

                    case OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT:
                        mInnerView.layout(mInnerView.getLeft() + deltaX, mChildTop,
                                mInnerView.getRight() + deltaX, mChildBottom);
                        return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsDraggedOverscrolling) {
                    smoothSpringBack();
                    endDrag();// clear scroll state
                    mIsDraggedOverscrolling = false;
                    return true;
                }
                break;
        }
        return false;
    }

    protected void resolveInnerViewOffsets() {
        if (mInnerView != null) {
            if (mInnerView.getLayoutParams() instanceof MarginLayoutParams) {
                MarginLayoutParams mlp = (MarginLayoutParams) mInnerView.getLayoutParams();
                mChildLeft = mlp.leftMargin;
                mChildTop = mlp.topMargin;
            }
            mChildRight = mChildLeft + mInnerView.getMeasuredWidth();
            mChildBottom = mChildTop + mInnerView.getMeasuredHeight();
        }
    }

    @Override
    public boolean isTendToScrollCurrView() {
        // 不在触摸移动
        if (mLastX == mDownX || mLastX == mCurrX) {
            return false;
        }
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
            case OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT:
                final int deltaX = mCurrX - mLastX;
                if (isPushingBack()) {
                    return deltaX;
                } else {
                    final double ratio = (double) (Math.abs(mInnerView.getMeasuredWidth()
                            - mInnerView.getRight())) / (double) getWidth();
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
            case OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT:
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
        final int offset = mInnerView.getMeasuredWidth() - getWidth();
        final int scrollX = getScrollX();
        return offset == scrollX || !canScrollHorizontally(1);
    }

    /**
     * @param from     {@link #mInnerView#getLeft()} 当前子view的左部位置偏移量
     * @param to       最终子view的左部位置偏移量
     * @param duration 动画持续时间
     */
    @Override
    public void startHeaderOverscrollAnim(int from, int to, int duration) {
        startSmoothOverscrollAnim(from, to, duration);
    }

    /**
     * @param from     {@link #mInnerView#getRight()} 当前子view的右部位置偏移量
     * @param to       最终子view的右部位置偏移量
     * @param duration 动画持续时间
     */
    @Override
    public void startFooterOverscrollAnim(int from, int to, int duration) {
        startSmoothOverscrollAnim(from - mInnerView.getMeasuredWidth(),
                to - mInnerView.getMeasuredWidth(), duration);
    }

    /**
     * @deprecated use {@link #startSmoothOverscrollAnim(int, int, int)} instead.
     */
    @Deprecated
    protected void startOverscrollAnim(int fromLeft, int toLeft, int duration) {
        if (fromLeft == toLeft || !animNeedStated()) {
            return;
        }
        mOverscrollAnim = new TranslateAnimation(fromLeft, toLeft, 0, 0);
        mOverscrollAnim.setDuration(duration);
        mOverscrollAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                animation.setInterpolator(mInterpolator);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mOverscrollAnim = null;
                smoothSpringBack();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        // 开启回弹动画
        mInnerView.startAnimation(mOverscrollAnim);
        mInnerView.layout(toLeft, mChildTop, toLeft + mInnerView.getMeasuredWidth(), mChildBottom);
    }

    private TranslateAnimation mOverscrollAnim;
    private ValueAnimator mSmoothOverscrollAnim;
    protected final Interpolator mInterpolator = new DecelerateInterpolator();

    protected void startSmoothOverscrollAnim(int fromLeft, final int toLeft, int duration) {
        if (fromLeft == toLeft || !animNeedStated()) {
            return;
        }
        mSmoothOverscrollAnim = ValueAnimator.ofInt(fromLeft, toLeft);
        mSmoothOverscrollAnim.setDuration(duration);
        mSmoothOverscrollAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final int left = (int) animation.getAnimatedValue();
                mInnerView.layout(left, mChildTop,
                        left + mInnerView.getMeasuredWidth(), mChildBottom);
            }
        });
        mSmoothOverscrollAnim.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                animation.setInterpolator(mInterpolator);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mSmoothOverscrollAnim = null;
                smoothSpringBack();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        mSmoothOverscrollAnim.start();
    }

    private boolean animNeedStated() {
        if (mOverscrollAnim != null || mSmoothOverscrollAnim != null) {
            Log.e(TAG, "can not start this over-scroll animation " +
                    "till the last animation has finished");
            return false;
        }
        return true;
    }

    @Override
    public void smoothSpringBack() {
        if (mInnerView.getLeft() != mChildLeft) {
            startHeaderOverscrollAnim(mInnerView.getLeft(), mChildLeft, DURATION_SPRING_BACK);
        } else if (mInnerView.getRight() != mChildRight) {
            startFooterOverscrollAnim(mInnerView.getRight(), mChildRight, DURATION_SPRING_BACK);
        } else {
            mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
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