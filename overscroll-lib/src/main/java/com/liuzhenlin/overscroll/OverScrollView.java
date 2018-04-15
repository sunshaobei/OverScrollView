package com.liuzhenlin.overscroll;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.IntDef;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListener;
import android.support.v4.widget.NestedScrollView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.liuzhenlin.overscroll.listener.OnOverFlyingListener;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static android.support.v4.widget.ViewDragHelper.INVALID_POINTER;

/**
 * Created on 2017/12/18. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
public class OverScrollView extends NestedScrollView implements ViewPropertyAnimatorListener {
    // @formatter:off
    private static final String TAG = "OverScrollView";

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
    public static final int OVERSCROLL_EDGE_TOP = 1;
    public static final int OVERSCROLL_EDGE_BOTTOM = 2;
    public static final int OVERSCROLL_EDGE_TOP_OR_BOTTOM = 3;
    @IntDef({
            OVERSCROLL_EDGE_UNSPECIFIED,
            OVERSCROLL_EDGE_TOP, OVERSCROLL_EDGE_BOTTOM, OVERSCROLL_EDGE_TOP_OR_BOTTOM
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
                if (!isScrolling() && isTendToScrollVertically()) {
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

    public boolean isTendToScrollVertically() {
        final float absDX = Math.abs(mCurrX - mDownX);
        final float absDY = Math.abs(mCurrY - mDownY);
        return absDY > absDX && absDY >= mTouchSlop
                || mTotalAbsDeltaY > mTotalAbsDeltaX && mTotalAbsDeltaY >= mTouchSlop;
    }

    protected boolean handleOverscroll(MotionEvent e) {
        if (!isOverscrollEnabled()) {
            return false;
        }
        mOverflyingDetector.onTouchEvent(e);

        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE:
                final float deltaY = computeOverscrollDeltaY();
                if (deltaY == 0f && isDraggedOverscrolling()) {
                    return true;
                }

                switch (mOverscrollEdge) {
                    case OVERSCROLL_EDGE_UNSPECIFIED: {
                        if (!isScrolling()) {
                            return false;
                        }
                        final float dy = mCurrY - mDownY;
                        final boolean atTop = isAtTop();
                        final boolean atBottom = isAtBottom();
                        // 当前布局不能上下滚动时 --> 不限制下拉和上拉
                        if (atTop && atBottom) {
                            mOverscrollEdge = OVERSCROLL_EDGE_TOP_OR_BOTTOM;
                            // 下拉
                        } else if (atTop && dy > 0f) {
                            mOverscrollEdge = OVERSCROLL_EDGE_TOP;
                            // 上拉
                        } else if (atBottom && dy < 0f) {
                            mOverscrollEdge = OVERSCROLL_EDGE_BOTTOM;
                        } else break;
                        mViewFlags |= VIEW_FLAG_OVERSCROLLING | VIEW_FLAG_DRAGGED_OVERSCROLLING;
                    }
                    break;

                    case OVERSCROLL_EDGE_TOP: {
                        final float transY = mInnerView.getTranslationY();
                        if (transY + deltaY < 0f) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        // 移动布局
                        final float newTransY = transY + (transY + deltaY < 0f ? 0f : deltaY);
                        mInnerView.setTranslationY(newTransY);

                        if (newTransY < transY) {
                            invalidateParentCachedTouchY();
                            return true;
                        }
                    }
                    // Not consume this event when user scroll this view down,
                    // to enable nested scrolling.
                    break;

                    case OVERSCROLL_EDGE_BOTTOM: {
                        final float transY = mInnerView.getTranslationY();
                        if (transY + deltaY > 0f) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final float newTransY = transY + (transY + deltaY > 0f ? 0f : deltaY);
                        mInnerView.setTranslationY(newTransY);

                        if (newTransY >= transY) {
                            invalidateParentCachedTouchY();
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_TOP_OR_BOTTOM: {
                        final float transY = mInnerView.getTranslationY();
                        final float newTransY = transY + deltaY;
                        mInnerView.setTranslationY(newTransY);

                        if (newTransY > 0f && newTransY < transY
                                || newTransY < 0f && newTransY > transY) {
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

    private float computeOverscrollDeltaY() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_TOP:
            case OVERSCROLL_EDGE_BOTTOM:
            case OVERSCROLL_EDGE_TOP_OR_BOTTOM:
                final float deltaY = mCurrY - mLastY;
                if (isPushingBack()) {
                    return deltaY;
                } else {
                    MarginLayoutParams mlp = (MarginLayoutParams) mInnerView.getLayoutParams();
                    final float ratio = Math.abs(mInnerView.getTranslationY()) /
                            ((getHeight() - getPaddingTop() - getPaddingBottom() - mlp.topMargin - mlp.bottomMargin) * 0.95f);
                    return (float) (1d / (2d + Math.tan(Math.PI / 2d * ratio)) * deltaY);
                }
        }
        return 0f;
    }

    /**
     * 是否向下拉时手指向上滑动或向上拉时手指向下滑动
     */
    private boolean isPushingBack() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_TOP:
            case OVERSCROLL_EDGE_BOTTOM:
            case OVERSCROLL_EDGE_TOP_OR_BOTTOM:
                final float deltaY = mCurrY - mLastY;// 向下滑动为正
                final float transY = mInnerView.getTranslationY();
                // 向下拉时手指向上滑动
                return (transY > 0f && deltaY < 0f ||
                        // 向上拉时手指向下滑动
                        transY < 0f && deltaY > 0f);
        }
        return false;
    }

    private void cancelDraggedOverscrolling() {
        mViewFlags &= ~(VIEW_FLAG_DRAGGED_OVERSCROLLING | VIEW_FLAG_OVERSCROLLING);
        mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
    }

    public boolean isAtTop() {
        return getScrollY() == 0 || !ViewCompat.canScrollVertically(this, -1);
    }

    public boolean isAtBottom() {
        MarginLayoutParams mlp = (MarginLayoutParams) mInnerView.getLayoutParams();
        final int scrollRange = mInnerView.getMeasuredHeight()
                - (getHeight() - getPaddingTop() - getPaddingBottom() - mlp.topMargin - mlp.bottomMargin);
        final int scrollY = getScrollY();
        return scrollY == scrollRange || !ViewCompat.canScrollVertically(this, 1);
    }

    public void smoothSpringBack() {
        if (mInnerView.getTranslationY() != 0f) {
            startOverscrollAnim(0f, DURATION_SPRING_BACK);
        } else {
            mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
            mViewFlags &= ~VIEW_FLAG_OVERSCROLLING;
        }
    }

    public void startOverscrollAnim(float toTransY, int duration) {
        if (mInnerView.getTranslationY() != toTransY) {
            ViewCompat.animate(mInnerView).translationY(toTransY)
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
        mInnerView.setTranslationY(0f);
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
            mOverscrollEdge = OVERSCROLL_EDGE_TOP;
            startOverscrollAnim(overHeight, duration);
        }

        @Override
        protected void onBottomOverFling(int overHeight, int duration) {
            mOverscrollEdge = OVERSCROLL_EDGE_BOTTOM;
            startOverscrollAnim(-overHeight, duration);
        }

        @Override
        protected void onLeftOverFling(int overWidth, int duration) {
        }

        @Override
        protected void onRightOverFling(int overWidth, int duration) {
        }

        @Override
        protected boolean isAtTop() {
            return OverScrollView.this.isAtTop();
        }

        @Override
        protected boolean isAtBottom() {
            return OverScrollView.this.isAtBottom();
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
            mLastMotionYField.set(this, (int) (mLastY + 0.5f));
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