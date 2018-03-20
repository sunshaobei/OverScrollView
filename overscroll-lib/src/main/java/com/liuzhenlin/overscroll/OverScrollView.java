package com.liuzhenlin.overscroll;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.widget.NestedScrollView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.TranslateAnimation;

import com.liuzhenlin.overscroll.listener.OnOverFlyingListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static android.support.v4.widget.ViewDragHelper.INVALID_POINTER;

/**
 * Created on 2017/12/18. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
public class OverScrollView extends NestedScrollView implements OverScrollBase {
    // @formatter:off
    private static final String TAG = "OverScrollView";

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
            case OVERSCROLL_EDGE_TOP:
            case OVERSCROLL_EDGE_BOTTOM:
            case OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM:
                return mOverscrollEnabled;
        }
        return false;
    }

    public OverScrollView(Context context) {
        this(context, null);
    }

    public OverScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.OverScrollView, defStyleAttr, 0);
        setOverscrollEnabled(a.getBoolean(R.styleable
                .OverScrollView_overscrollEnabled, true));
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
                final int deltaY = computeOverscrollDeltaY();
                if (deltaY == 0 && mIsDraggedOverscrolling) {
                    return true;
                }
                switch (mOverscrollEdge) {
                    case OVERSCROLL_EDGE_UNSPECIFIED:
                        if (!mIsScrolling) {
                            return false;
                        }
                        final int dy = mCurrY - mDownY;
                        final boolean atTop = isAtTop();
                        final boolean atBottom = isAtBottom();
                        // 当前布局不能上下滚动时 --> 不限制下拉和上拉
                        if (atTop && atBottom) {
                            mOverscrollEdge = OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM;
                            // 下拉
                        } else if (atTop && dy > 0) {
                            mOverscrollEdge = OVERSCROLL_EDGE_TOP;
                            // 上拉
                        } else if (atBottom && dy < 0) {
                            mOverscrollEdge = OVERSCROLL_EDGE_BOTTOM;
                        } else break;
                        mIsDraggedOverscrolling = true;
                        break;

                    case OVERSCROLL_EDGE_TOP: {
                        if (!isAtTop()) {
                            if (isAtBottom()) {
                                mOverscrollEdge = OVERSCROLL_EDGE_BOTTOM;
                            }
                            break;
                        }

                        final int old = mInnerView.getTop();
                        final int childTop = old + deltaY < mChildTop ? mChildTop : old + deltaY;
                        // 移动布局
                        mInnerView.layout(mChildLeft, childTop, mChildRight,
                                childTop + mInnerView.getMeasuredHeight());

                        if (childTop < old) {
                            invalidateParentCachedTouchY();
                            if (childTop == mChildTop) {
                                break;
                            }
                            return true;
                        }
                    }
                    // Not consume this event when user scroll this view down,
                    // to enable nested scrolling.
                    break;

                    case OVERSCROLL_EDGE_BOTTOM: {
                        if (!isAtBottom()) {
                            if (isAtTop()) {
                                mOverscrollEdge = OVERSCROLL_EDGE_TOP;
                            }
                            break;
                        }

                        final int old = mInnerView.getBottom();
                        final int childBottom = old + deltaY > mChildBottom ?
                                mChildBottom : old + deltaY;
                        mInnerView.layout(mChildLeft, childBottom - mInnerView.getMeasuredHeight(),
                                mChildRight, childBottom);

                        if (childBottom > old) {
                            invalidateParentCachedTouchY();
                            if (childBottom == mChildBottom) {
                                break;
                            }
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM: {
                        final int old = mInnerView.getTop();
                        final int childTop = old + deltaY;
                        mInnerView.layout(mChildLeft, childTop,
                                mChildRight, mInnerView.getBottom() + deltaY);

                        if (childTop > mChildTop && childTop < old
                                || childTop < mChildTop && childTop > old) {
                            invalidateParentCachedTouchY();
                            return true;
                        }
                    }
                    break;
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
        if (mLastY == mDownY || mLastY == mCurrY) {
            return false;
        }
        final int absDX = Math.abs(mCurrX - mDownX);
        final int absDY = Math.abs(mCurrY - mDownY);
        return absDY > absDX && absDY >= mTouchSlop
                || mTotalAbsDeltaY > mTotalAbsDeltaX && mTotalAbsDeltaY >= mTouchSlop;
    }

    @Override
    public int computeOverscrollDeltaY() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_TOP:
            case OVERSCROLL_EDGE_BOTTOM:
            case OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM:
                final int deltaY = mCurrY - mLastY;
                if (isPushingBack()) {
                    return deltaY;
                } else {
                    final double ratio = (double) (Math.abs(mInnerView.getMeasuredHeight()
                            - mInnerView.getBottom())) / (double) getHeight();
                    return (int) (1d / (2d + Math.tan(Math.PI / 2d * ratio)) * (double) deltaY);
                }
        }
        return 0;
    }

    @Override
    public int computeOverscrollDeltaX() {
        return 0;
    }

    /**
     * 是否向下拉时手指向上滑动或向上拉时手指向下滑动
     */
    @Override
    public boolean isPushingBack() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_TOP:
            case OVERSCROLL_EDGE_BOTTOM:
            case OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM:
                final int deltaY = mCurrY - mLastY;// 向下滑动为正
                // 向下拉时手指向上滑动
                return (mInnerView.getTop() > mChildTop && deltaY < 0 ||
                        // 向上拉时手指向下滑动
                        mInnerView.getTop() < mChildTop && deltaY > 0);
        }
        return false;
    }

    @Override
    public boolean isAtTheStart() {
        return isAtTop();
    }

    @Override
    public boolean isAtTheEnd() {
        return isAtBottom();
    }

    public boolean isAtTop() {
        return getScrollY() == 0 || !canScrollVertically(-1);
    }

    public boolean isAtBottom() {
        final int offset = mInnerView.getMeasuredHeight() - getHeight();
        final int scrollY = getScrollY();
        return offset == scrollY || !canScrollVertically(1);
    }

    /**
     * @param from     {@link #mInnerView#getTop()} 当前子view的顶部位置偏移量
     * @param to       最终子view的顶部位置偏移量
     * @param duration 动画持续时间
     */
    @Override
    public void startHeaderOverscrollAnim(int from, int to, int duration) {
        startSmoothOverscrollAnim(from, to, duration);
    }

    /**
     * @param from     {@link #mInnerView#getBottom()} 当前子view的底部位置偏移量
     * @param to       最终子view的底部位置偏移量
     * @param duration 动画持续时间
     */
    @Override
    public void startFooterOverscrollAnim(int from, int to, int duration) {
        startSmoothOverscrollAnim(from - mInnerView.getMeasuredHeight(),
                to - mInnerView.getMeasuredHeight(), duration);
    }

    /**
     * @deprecated use {@link #startSmoothOverscrollAnim(int, int, int)} instead.
     */
    @Deprecated
    protected void startOverscrollAnim(int fromTop, int toTop, int duration) {
        if (fromTop == toTop || !animNeedStated()) {
            return;
        }
        mOverscrollAnim = new TranslateAnimation(0, 0, fromTop, toTop);
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
        mInnerView.layout(mChildLeft, toTop, mChildRight, toTop + mInnerView.getMeasuredHeight());
    }

    private TranslateAnimation mOverscrollAnim;
    private ValueAnimator mSmoothOverscrollAnim;
    protected final Interpolator mInterpolator = new DecelerateInterpolator();

    protected void startSmoothOverscrollAnim(int fromTop, final int toTop, int duration) {
        if (fromTop == toTop || !animNeedStated()) {
            return;
        }
        mSmoothOverscrollAnim = ValueAnimator.ofInt(fromTop, toTop);
        mSmoothOverscrollAnim.setDuration(duration);
        mSmoothOverscrollAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final int top = (int) animation.getAnimatedValue();
                mInnerView.layout(mChildLeft, top, mChildRight,
                        top + mInnerView.getMeasuredHeight());
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
        if (mInnerView.getTop() != mChildTop) {
            startHeaderOverscrollAnim(mInnerView.getTop(), mChildTop, DURATION_SPRING_BACK);
        } else if (mInnerView.getBottom() != mChildBottom) {
            startFooterOverscrollAnim(mInnerView.getBottom(), mChildBottom, DURATION_SPRING_BACK);
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
            mOverscrollEdge = OVERSCROLL_EDGE_TOP;
            startHeaderOverscrollAnim(mInnerView.getTop(), mChildTop + overHeight, duration);
        }

        @Override
        protected void onBottomOverFling(int overHeight, int duration) {
            mOverscrollEdge = OVERSCROLL_EDGE_BOTTOM;
            startFooterOverscrollAnim(mInnerView.getBottom(), mChildBottom - overHeight, duration);
        }

        @Override
        protected void onLeftOverFling(int overWidth, int duration) {

        }

        @Override
        protected void onRightOverFling(int overWidth, int duration) {

        }

        @Override
        protected boolean isAtTop() {
            return isAtTheStart();
        }

        @Override
        protected boolean isAtBottom() {
            return isAtTheEnd();
        }

        @Override
        protected boolean isAtFarLeft() {
            return false;
        }

        @Override
        protected boolean isAtFarRight() {
            return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // reflection methods
    ///////////////////////////////////////////////////////////////////////////

    private Field mLastMotionYField;

    private Method mEndDragMethod;

    /**
     * Refresh the cached touch Y {@link NestedScrollView#mLastMotionY}
     * of {@link NestedScrollView} to ensure it will scroll up or down
     * within {@code Math.abs(mCurrY - mLastY)} px
     * when it receives touch event again.
     */
    private void invalidateParentCachedTouchY() {
        try {
            if (mLastMotionYField == null) {
                mLastMotionYField = NestedScrollView.class.getDeclaredField("mLastMotionY");
                mLastMotionYField.setAccessible(true);
            }
            mLastMotionYField.set(this, mLastY);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @see NestedScrollView#endDrag
     */
    protected final void endDrag() {
        try {
            if (mEndDragMethod == null) {
                mEndDragMethod = NestedScrollView.class.getDeclaredMethod("endDrag");
                mEndDragMethod.setAccessible(true);
            }
            mEndDragMethod.invoke(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}