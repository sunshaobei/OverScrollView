package com.liuzhenlin.overscroll;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.ViewCompat;
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
public class OverScrollView extends NestedScrollView
        implements OverScrollBase, Animation.AnimationListener {
    // @formatter:off
    private static final String TAG = "OverScrollView";

    private View mInnerView;

    // 子View相对于当前view的位置
    private int mChildTop;
    private int mChildBottom;

    private int mChildTopMargins;
    private int mChildBottomMargins;

    protected final int mTouchSlop;

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
                final int deltaY = computeOverscrollDeltaY();
                if (deltaY == 0 && isDraggedOverscrolling()) {
                    return true;
                }
                switch (mOverscrollEdge) {
                    case OVERSCROLL_EDGE_UNSPECIFIED: {
                        if (!isScrolling()) {
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
                        mViewFlags |= VIEW_FLAG_OVERSCROLLING | VIEW_FLAG_DRAGGED_OVERSCROLLING;
                    }
                    break;

                    case OVERSCROLL_EDGE_TOP: {
                        if (!isAtTop()) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final int oldTop = mInnerView.getTop();
                        final int dy = oldTop + deltaY < mChildTop ? 0 : deltaY;
                        // 移动布局
                        mInnerView.offsetTopAndBottom(dy);

                        if (mInnerView.getTop() < oldTop) {
                            invalidateParentCachedTouchY();
                            return true;
                        }
                    }
                    // Not consume this event when user scroll this view down,
                    // to enable nested scrolling.
                    break;

                    case OVERSCROLL_EDGE_BOTTOM: {
                        if (!isAtBottom()) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final int oldBottom = mInnerView.getBottom();
                        final int dy = oldBottom + deltaY > mChildBottom ? 0 : deltaY;
                        mInnerView.offsetTopAndBottom(dy);

                        if (mInnerView.getBottom() > oldBottom) {
                            invalidateParentCachedTouchY();
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM: {
                        final int oldTop = mInnerView.getTop();
                        final int childTop = oldTop + deltaY;
                        mInnerView.offsetTopAndBottom(deltaY);

                        if (childTop > mChildTop && childTop < oldTop
                                || childTop < mChildTop && childTop > oldTop) {
                            invalidateParentCachedTouchY();
                            return true;
                        }
                    }
                    break;
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

    private void resolveInnerViewBounds() {
        if (mInnerView != null) {
            mChildTopMargins = getPaddingTop();
            mChildBottomMargins = getPaddingBottom();
            if (mInnerView.getLayoutParams() instanceof MarginLayoutParams) {
                MarginLayoutParams mlp = (MarginLayoutParams) mInnerView.getLayoutParams();
                mChildTopMargins += mlp.topMargin;
                mChildBottomMargins += mlp.bottomMargin;
            }
            mChildTop = mChildTopMargins;
            mChildBottom = mChildTop + mInnerView.getMeasuredHeight();
        }
    }

    private void cancelDraggedOverscrolling() {
        mViewFlags &= ~(VIEW_FLAG_DRAGGED_OVERSCROLLING | VIEW_FLAG_OVERSCROLLING);
        mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
    }

    @Override
    public boolean isTendToScrollCurrView() {
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
                    final double ratio = (double) Math.abs(mInnerView.getTop() - mChildTop)
                            / (double) (getHeight() - mChildTopMargins - mChildBottomMargins);
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
        return getScrollY() == 0 || !ViewCompat.canScrollVertically(this, -1);
    }

    public boolean isAtBottom() {
        final int scrollRange = mInnerView.getMeasuredHeight()
                - (getHeight() - mChildTopMargins - mChildBottomMargins);
        final int scrollY = getScrollY();
        return scrollY == scrollRange || !ViewCompat.canScrollVertically(this, 1);
    }

    /**
     * @param from     {@link #mInnerView#getTop()} 当前子view的顶部位置偏移量
     * @param to       最终子view的顶部位置偏移量
     * @param duration 动画持续时间
     */
    @Override
    public void startHeaderOverscrollAnim(int from, int to, int duration) {
        startOverscrollAnim(from, to, duration);
    }

    /**
     * @param from     {@link #mInnerView#getBottom()} 当前子view的底部位置偏移量
     * @param to       最终子view的底部位置偏移量
     * @param duration 动画持续时间
     */
    @Override
    public void startFooterOverscrollAnim(int from, int to, int duration) {
        startOverscrollAnim(from - mInnerView.getMeasuredHeight(),
                to - mInnerView.getMeasuredHeight(), duration);
    }

    private void startOverscrollAnim(int fromTop, int toTop, int duration) {
        if (fromTop != toTop && hasAnimationFinished()) {
            mOverscrollAnim = new TranslateAnimation(0, 0,
                    fromTop - toTop, 0);
            mOverscrollAnim.setDuration(duration);
            mOverscrollAnim.setInterpolator(mInterpolator);
            mOverscrollAnim.setAnimationListener(this);
            // 开启回弹动画
            mInnerView.startAnimation(mOverscrollAnim);
            mInnerView.offsetTopAndBottom(toTop - fromTop);
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
    public void forceEndOverscrollAnim() {
        if (mOverscrollAnim != null) {
            mOverscrollAnim.cancel();
        }
    }

    @Override
    public void onAnimationStart(Animation animation) {
        mViewFlags |= VIEW_FLAG_OVERSCROLLING;
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
        if (mInnerView.getTop() != mChildTop) {
            startHeaderOverscrollAnim(mInnerView.getTop(), mChildTop, DURATION_SPRING_BACK);
        } else if (mInnerView.getBottom() != mChildBottom) {
            startFooterOverscrollAnim(mInnerView.getBottom(), mChildBottom, DURATION_SPRING_BACK);
        } else {
            mInnerView.clearAnimation();
            mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
            mViewFlags &= ~(VIEW_FLAG_DRAGGED_OVERSCROLLING | VIEW_FLAG_OVERSCROLLING);
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