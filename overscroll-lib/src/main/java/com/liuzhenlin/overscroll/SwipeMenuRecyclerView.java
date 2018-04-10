package com.liuzhenlin.overscroll;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.liuzhenlin.overscroll.listener.OnOverFlyingListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static android.support.v4.widget.ViewDragHelper.INVALID_POINTER;

/**
 * Created on 2017/12/16. </br>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
public class SwipeMenuRecyclerView extends RecyclerView implements OverScrollBase,
        Animator.AnimatorListener, ValueAnimator.AnimatorUpdateListener {
    // @formatter:off
    private static final String TAG = "SwipeMenuRecyclerView";

    public static final int NO_ORIENTATION = -1;

    private int mPaddingLeft;
    private int mPaddingTop;
    private int mPaddingRight;
    private int mPaddingBottom;

    /** dp */
    protected final float mDp;
    protected final int mTouchSlop;

    private int mViewFlags;

    /** 标志列表正在滚动 */
    private static final int VIEW_FLAG_SCROLLING = 1;

    /** 标志itemView正在滚动 */
    private static final int VIEW_FLAG_ITEM_SCROLLING = 1 << 1;

    /** 标志itemView已完全滚开 */
    private static final int VIEW_FLAG_ITEM_FULL_SCROLLED = 1 << 2;

    /** 标志列表正在过度滚动 */
    private static final int VIEW_FLAG_OVERSCROLLING = 1 << 3;

    /** 标志手指正在拖拽着当前view，使其发生滚动 */
    private static final  int VIEW_FLAG_DRAGGED_OVERSCROLLING = 1 << 4;

    /**
     * 标志itemView可以滚动
     * @see #setItemScrollingEnabled(boolean)
     * */
    private static final int VIEW_FLAG_ITEM_SCROLLING_ENABLED_BY_USER = 1 << 5;

    /**
     * 标志itemView可以向右滑动且松手后有自动回弹的效果
     * @see #setItemSpringBackEnabled(boolean)
     */
    private static final int VIEW_FLAG_ITEM_SPRING_BACK_ENABLED = 1 << 6;

    /**
     * 标志用户设置了列表可以过度滚动
     * @see #setOverscrollEnabled(boolean)
     */
    private static final int VIEW_FLAG_OVERSCROLL_ENABLED_BY_USER = 1 << 7;

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

    /** 是否应该拦截touch事件 */
    private boolean mShouldInterceptTouchEvent;

    /** 是否消费了当前的touch事件 */
    private boolean mIsConsumed;

    /** 最近一次滚动的itemView */
    private SmoothScrollableLinearLayout mItemView;
    /** 上次滚动的itemView */
    private SmoothScrollableLinearLayout mLastItemView;

    private final Rect mItemTouchRect = new Rect();

    /** itemView向左滚动时的最大滚动距离 */
    private int mMaxLeftScrollX;
    /** itemView向右滚动时的最大滚动距离 */
    private int mMaxRightScrollX;

    private final OverFlyingDetector mOverflyingDetector;

    @OverscrollEdge
    private int mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;

    private ValueAnimator mOverscrollAnim;
    private final Interpolator mInterpolator = new DecelerateInterpolator();

    private int mAnimFlags;
    private static final int ANIM_FLAG_HEADER_ANIM_RUNNING = 1;
    private static final int ANIM_FLAG_FOOTER_ANIM_RUNNING = 1 << 1;
    // @formatter:on

    public boolean isScrolling() {
        return (mViewFlags & VIEW_FLAG_SCROLLING) != 0;
    }

    public boolean isItemScrolling() {
        return (mViewFlags & VIEW_FLAG_ITEM_SCROLLING) != 0;
    }

    public boolean isItemFullScrolled() {
        return (mViewFlags & VIEW_FLAG_ITEM_FULL_SCROLLED) != 0;
    }

    public boolean isOverscrolling() {
        return (mViewFlags & VIEW_FLAG_OVERSCROLLING) != 0;
    }

    public boolean isDraggedOverscrolling() {
        return (mViewFlags & VIEW_FLAG_DRAGGED_OVERSCROLLING) != 0;
    }

    public boolean isItemScrollingEnabled() {
        return getLayoutOrientation() == VERTICAL
                && (mViewFlags & VIEW_FLAG_ITEM_SCROLLING_ENABLED_BY_USER) != 0;
    }

    /**
     * only works on vertical layout
     */
    public void setItemScrollingEnabled(boolean enabled) {
        if (enabled)
            mViewFlags |= VIEW_FLAG_ITEM_SCROLLING_ENABLED_BY_USER;
        else
            mViewFlags &= ~VIEW_FLAG_ITEM_SCROLLING_ENABLED_BY_USER;
    }

    public boolean isItemSpringBackEnabled() {
        return (mViewFlags & VIEW_FLAG_ITEM_SPRING_BACK_ENABLED) != 0;
    }

    public void setItemSpringBackEnabled(boolean enabled) {
        if (enabled) {
            mViewFlags |= VIEW_FLAG_ITEM_SPRING_BACK_ENABLED;
            mMaxRightScrollX = (int) (10f * mDp + 0.5f);
        } else {
            mViewFlags &= ~VIEW_FLAG_ITEM_SPRING_BACK_ENABLED;
            mMaxRightScrollX = 0;
        }
    }

    public boolean isOverscrollEnabled() {
        return getChildCount() > 0 && (mViewFlags & VIEW_FLAG_OVERSCROLL_ENABLED_BY_USER) != 0;
    }

    public void setOverscrollEnabled(boolean enabled) {
        if (enabled) {
            mViewFlags |= VIEW_FLAG_OVERSCROLL_ENABLED_BY_USER;
            // 禁用拉到两端时发荧光的效果
            setOverScrollMode(OVER_SCROLL_NEVER);
            // 将滚动条设置在padding区域外并且覆盖在view上
            setScrollBarStyle(SCROLLBARS_OUTSIDE_OVERLAY);
        } else
            mViewFlags &= ~VIEW_FLAG_OVERSCROLL_ENABLED_BY_USER;
    }

    @OverscrollEdge
    public int getOverscrollEdge() {
        return mOverscrollEdge;
    }

    public SwipeMenuRecyclerView(Context context) {
        this(context, null);
    }

    public SwipeMenuRecyclerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeMenuRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        post(new Runnable() {
            @Override
            public void run() {
                mPaddingLeft = getPaddingLeft();
                mPaddingTop = getPaddingTop();
                mPaddingRight = getPaddingRight();
                mPaddingBottom = getPaddingBottom();
            }
        });
        mOverflyingDetector = new OverFlyingDetector();
        mDp = getContext().getResources().getDisplayMetrics().density;
        mTouchSlop = mOverflyingDetector.mTouchSlop;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SwipeMenuRecyclerView, defStyle, 0);
        setOverscrollEnabled(a.getBoolean(R.styleable
                .SwipeMenuRecyclerView_overscrollEnabled, true));
        setItemScrollingEnabled(a.getBoolean(R.styleable
                .SwipeMenuRecyclerView_itemScrollingEnabled, true));
        setItemSpringBackEnabled(a.getBoolean(R.styleable
                .SwipeMenuRecyclerView_itemSpringBackEnabled, true));
        a.recycle();
    }

    @Override
    public void setOverScrollMode(int overScrollMode) {
        if (isOverscrollEnabled() && getOverScrollMode() == View.OVER_SCROLL_NEVER) {
            return;
        }
        super.setOverScrollMode(overScrollMode);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        if (!isOverscrolling()) {
            mPaddingLeft = left;
            mPaddingTop = top;
            mPaddingRight = right;
            mPaddingBottom = bottom;
        }
        super.setPadding(left, top, right, bottom);
    }

    /**
     * Returns the current orientation of the layout.
     *
     * @return Current orientation,  either {@link #HORIZONTAL} or {@link #VERTICAL}
     * @see LinearLayoutManager#setOrientation(int)
     * @see StaggeredGridLayoutManager#setOrientation(int)
     */
    public int getLayoutOrientation() {
        if (getLayoutManager() instanceof LinearLayoutManager) {
            return (((LinearLayoutManager) getLayoutManager()).getOrientation());
        } else if (getLayoutManager() instanceof StaggeredGridLayoutManager) {
            return (((StaggeredGridLayoutManager) getLayoutManager()).getOrientation());
        }
        return NO_ORIENTATION;
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
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id "
                            + mActivePointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }
                mCurrX = (int) (ev.getX(pointerIndex) + 0.5f);
                mCurrY = (int) (ev.getY(pointerIndex) + 0.5f);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = INVALID_POINTER;
                break;
        }
        mIsConsumed = handleItemScrollingEvent(ev) || handleOverscroll(ev);
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

    private void markCurrXY(MotionEvent e) {
        switch (e.getAction() & MotionEvent.ACTION_MASK) {
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
        }
    }

    /**
     * 拦截touch事件
     *
     * @return 返回true时，ViewGroup的事件有效，执行onTouchEvent事件；
     * 返回false时，事件向下传递,onTouchEvent无效。
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        markCurrXY(e);
        return mShouldInterceptTouchEvent || super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (!isScrolling() && isTendToScrollCurrView()) {
                    mViewFlags |= VIEW_FLAG_SCROLLING;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mViewFlags &= ~VIEW_FLAG_SCROLLING;
                mShouldInterceptTouchEvent = false;
                break;
        }
        markCurrXY(e);
        return mIsConsumed || super.onTouchEvent(e);
    }

    protected boolean handleItemScrollingEvent(MotionEvent e) {
        if (!isItemScrollingEnabled()) {
            return false;
        }
        switch (e.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                for (int i = getChildCount() - 1; i >= 0; i--) {
                    View child = getChildAt(i);
                    if (!child.isShown()) continue;

                    child.getHitRect(mItemTouchRect);
                    if (!mItemTouchRect.contains(mDownX, mDownY)) continue;

                    if (!(child instanceof SmoothScrollableLinearLayout)) continue;

                    mItemView = (SmoothScrollableLinearLayout) child;
                    mMaxLeftScrollX = mItemView.getChildCount() <= 1 ? 0 :
                            mItemView.getChildAt(mItemView.getChildCount() - 1).getMeasuredWidth();
                    break;
                }

                // 如果存在itemView被滑开
                if (isItemFullScrolled()) {
                    if (mLastItemView == mItemView) {
                        // 点击的不是itemView滚开后显示的item（按钮）时,
                        // 才拦截touch事件，子view将无法收到touch事件
                        if (mDownX < mLastItemView.getWidth() - mLastItemView.getScrollX()) {
                            return mShouldInterceptTouchEvent = true;
                        }
                    } else {
                        // 之前滑开的和现在点击的不是同一个，之前的隐藏
                        releaseItemViewInternal(mLastItemView, 500);
                        return mShouldInterceptTouchEvent = true;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                // 如果在滑动itemView
                if (isItemScrolling()) {
                    // 不显示滚动条
                    setVerticalScrollBarEnabled(false);

                    int dx = mLastX - mCurrX; // 向左滑动为正
                    final int scrollX = mItemView.getScrollX(); // 向左滚动为正
                    // 向左滑动至最大左滑距离
                    if (dx + scrollX > mMaxLeftScrollX) {
                        dx = mMaxLeftScrollX - scrollX;
                        // 向右滑动至最大右滑距离
                    } else if (scrollX + dx < -mMaxRightScrollX) {
                        dx = -mMaxRightScrollX - scrollX;
                    }
                    mItemView.smoothScrollBy(-dx, 0, 0);

                    // 消费当前touch事件且不调用父类onTouchEvent(e)
                    // --> 列表无法上下滚动
                    return true;
                }

                if (isScrolling()) {
                    // 显示竖直滚动条
                    setVerticalScrollBarEnabled(true);
                    // 自动隐藏滑开的itemView
                    releaseItemView();
                } else {
                    if (shouldHandleItemScrollingEvent()) {
                        mViewFlags |= VIEW_FLAG_ITEM_SCROLLING;
                        mShouldInterceptTouchEvent = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                // 之前滑开的和现在点击的是同一个itemView，如果没有继续滑动，隐藏它
                if (mLastItemView == mItemView && !isItemScrolling()) {
                    releaseItemView();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (isItemScrolling()) {
                    final int scrollX = mItemView.getScrollX();
                    final int middle = mMaxLeftScrollX / 2;
                    // 左滑时 滚动距离 < 最大左滚距离的一半 -->自动隐藏
                    if (scrollX > 0 && scrollX < middle) {
                        releaseItemViewInternal(mItemView, (int) (250f
                                * (float) scrollX / (float) middle + 0.5f));

                        // 左滑时 滚动距离 >= 最大左滚距离的一半 -->自动滚开
                    } else if (scrollX >= middle && scrollX < mMaxLeftScrollX) {
                        mItemView.smoothScrollTo(mMaxLeftScrollX, 0, (int) (250f
                                * ((float) mMaxLeftScrollX - (float) scrollX)
                                / (float) middle + 0.5f));
                        mViewFlags |= VIEW_FLAG_ITEM_FULL_SCROLLED;

                        // 左滑时 完全滑开
                    } else if (scrollX == mMaxLeftScrollX) {
                        mViewFlags |= VIEW_FLAG_ITEM_FULL_SCROLLED;

                        // 右滑时
                    } else if (scrollX < 0) {
                        // 从右 mMaxRightScrollX 向左平滑滚动 2*mMaxRightScrollX 到
                        // 左maxRightScrollX位置
                        mItemView.smoothScrollTo(-mMaxRightScrollX, 0, 150);
                        mViewFlags |= VIEW_FLAG_ITEM_FULL_SCROLLED;

                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // 从左 mMaxRightScrollX 向右平滑滚动 mMaxRightScrollX 回到原点
                                releaseItemViewInternal(mItemView, 150);
                            }
                        }, 150);
                    }
                    mViewFlags &= ~VIEW_FLAG_ITEM_SCROLLING;
                    mLastItemView = mItemView;
                    return true;
                }
            case MotionEvent.ACTION_CANCEL:
                mViewFlags &= ~VIEW_FLAG_ITEM_SCROLLING;
                mLastItemView = mItemView;
                releaseItemView();
                break;
        }
        return false;
    }

    private boolean shouldHandleItemScrollingEvent() {
        final int absDX = Math.abs(mCurrX - mDownX);
        final int absDY = Math.abs(mCurrY - mDownY);
        return mItemView != null && !isOverscrolling()
                && mItemTouchRect.contains(mDownX, mDownY)
                && absDX > absDY && absDX >= mTouchSlop
                && mTotalAbsDeltaX > mTotalAbsDeltaY;
    }

    /**
     * 使当前滚开的itemView回到初始位置
     */
    public void releaseItemView() {
        releaseItemViewInternal(mItemView, 500);
    }

    private void releaseItemViewInternal(SmoothScrollableLinearLayout itemView, int duration) {
        if (itemView != null) {
            itemView.smoothScrollTo(0, 0, duration);
            mViewFlags &= ~VIEW_FLAG_ITEM_FULL_SCROLLED;
        }
    }

    @Override
    public boolean handleOverscroll(MotionEvent e) {
        if (!isOverscrollEnabled()) {
            return false;
        }
        mOverflyingDetector.onTouchEvent(e);

        // 列表发生上下滚动时才处理
        if (!isScrolling()) {
            return false;
        }
        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE:
                final int deltaY = computeOverscrollDeltaY();
                final int deltaX = computeOverscrollDeltaX();
                if (deltaX == 0 && deltaY == 0 && isDraggedOverscrolling()) {
                    return true;
                }
                switch (mOverscrollEdge) {
                    case OVERSCROLL_EDGE_UNSPECIFIED:
                        switch (getLayoutOrientation()) {
                            case VERTICAL:
                                final int dy = mCurrY - mDownY;
                                final boolean atTop = isAtTheStart();
                                final boolean atBottom = isAtTheEnd();
                                // itemView较少时，列表不能上下滚动 --> 不限制下拉和上拉
                                if (atTop && atBottom) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_TOP_OR_BOTTOM;
                                    // 固定列表的高度
                                    getLayoutParams().height = getHeight();
                                    // 下拉
                                } else if (atTop && dy > 0) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_TOP;
                                    // 上拉
                                } else if (atBottom && dy < 0) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_BOTTOM;
                                } else break;
                                mViewFlags |= VIEW_FLAG_OVERSCROLLING | VIEW_FLAG_DRAGGED_OVERSCROLLING;
                                break;
                            case HORIZONTAL:
                                final int dx = mCurrX - mDownX;
                                final boolean atFarLeft = isAtTheStart();
                                final boolean atFarRight = isAtTheEnd();
                                // itemView较少时，列表不能左右滚动 --> 不限制右拉和左拉
                                if (atFarLeft && atFarRight) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_LEFT_OR_RIGHT;
                                    // 固定列表的宽度
                                    getLayoutParams().width = getWidth();
                                    // 右拉
                                } else if (atFarLeft && dx > 0) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_LEFT;
                                    // 左拉
                                } else if (atFarRight && dx < 0) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_RIGHT;
                                } else break;
                                mViewFlags |= VIEW_FLAG_OVERSCROLLING | VIEW_FLAG_DRAGGED_OVERSCROLLING;
                                break;
                        }
                        break;

                    case OVERSCROLL_EDGE_TOP: {
                        if (!isAtTheStart()) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final int oldPt = getPaddingTop();
                        final int paddingTop = oldPt + deltaY < mPaddingTop ?
                                mPaddingTop : oldPt + deltaY;
                        setPadding(mPaddingLeft, paddingTop, mPaddingRight, mPaddingBottom);

                        if (paddingTop < oldPt) {
                            invalidateParentCachedTouchPos();
                            return true;
                        }
                    }
                    // Not consume this event when user scroll this view down,
                    // to enable nested scrolling.
                    break;

                    case OVERSCROLL_EDGE_BOTTOM: {
                        if (!isAtTheEnd()) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final int oldPb = getPaddingBottom();
                        final int paddingBottom = oldPb - deltaY < mPaddingBottom ?
                                mPaddingBottom : oldPb - deltaY;
                        setPadding(mPaddingLeft, mPaddingTop, mPaddingRight, paddingBottom);

                        if (paddingBottom < oldPb) {
                            invalidateParentCachedTouchPos();
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_TOP_OR_BOTTOM: {
                        final int oldPt = getPaddingTop();
                        final int paddingTop = oldPt + deltaY;
                        setPadding(mPaddingLeft, paddingTop, mPaddingRight, mPaddingBottom);

                        if (paddingTop > mPaddingTop && paddingTop < oldPt
                                || paddingTop < mPaddingTop && paddingTop > oldPt) {
                            invalidateParentCachedTouchPos();
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_LEFT: {
                        if (!isAtTheStart()) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final int oldPl = getPaddingLeft();
                        final int paddingLeft = oldPl + deltaX < mPaddingLeft ?
                                mPaddingLeft : oldPl + deltaX;
                        setPadding(paddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom);

                        if (paddingLeft < oldPl) {
                            invalidateParentCachedTouchPos();
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_RIGHT: {
                        if (!isAtTheEnd()) {
                            cancelDraggedOverscrolling();
                            break;
                        }

                        final int oldPr = getPaddingRight();
                        final int paddingRight = oldPr - deltaX < mPaddingRight ?
                                mPaddingRight : oldPr - deltaX;
                        setPadding(mPaddingLeft, mPaddingTop, paddingRight, mPaddingBottom);

                        if (paddingRight < oldPr) {
                            invalidateParentCachedTouchPos();
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_LEFT_OR_RIGHT: {
                        final int oldPl = getPaddingLeft();
                        final int paddingLeft = oldPl + deltaX;
                        setPadding(paddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom);

                        if (paddingLeft > mPaddingLeft && paddingLeft < oldPl
                                || paddingLeft < mPaddingLeft && paddingLeft > oldPl) {
                            invalidateParentCachedTouchPos();
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
                    cancelTouch(); // clear scroll state
                    mViewFlags &= ~VIEW_FLAG_DRAGGED_OVERSCROLLING;
                    return true;
                }
                break;
        }
        return false;
    }

    private void cancelDraggedOverscrolling() {
        mViewFlags &= ~(VIEW_FLAG_DRAGGED_OVERSCROLLING | VIEW_FLAG_OVERSCROLLING);
        mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
    }

    @Override
    public boolean isTendToScrollCurrView() {
        final int absDX = Math.abs(mCurrX - mDownX);
        final int absDY = Math.abs(mCurrY - mDownY);
        switch (getLayoutOrientation()) {
            case VERTICAL:
                return absDY > absDX && absDY >= mTouchSlop
                        || mTotalAbsDeltaY > mTotalAbsDeltaX && mTotalAbsDeltaY >= mTouchSlop;
            case HORIZONTAL:
                return absDX > absDY && absDX >= mTouchSlop
                        || mTotalAbsDeltaX > mTotalAbsDeltaY && mTotalAbsDeltaX >= mTouchSlop;
        }
        return false;
    }

    @Override
    public int computeOverscrollDeltaY() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_TOP:
            case OVERSCROLL_EDGE_BOTTOM:
            case OVERSCROLL_EDGE_TOP_OR_BOTTOM:
                final int deltaY = mCurrY - mLastY;// 向下滑动为正
                if (isPushingBack()) {
                    return deltaY;
                } else {
                    final double ratio = (double) (Math.abs(getPaddingTop() - mPaddingTop)
                            + getPaddingBottom() - mPaddingBottom)
                            / (double) (getHeight() - mPaddingTop - mPaddingBottom);
                    return (int) (1d / (2d + Math.tan(Math.PI / 2d * ratio)) * (double) deltaY);
                }
        }
        return 0;
    }

    @Override
    public int computeOverscrollDeltaX() {
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_LEFT:
            case OVERSCROLL_EDGE_RIGHT:
            case OVERSCROLL_EDGE_LEFT_OR_RIGHT:
                final int deltaX = mCurrX - mLastX;// 向右滑动为正
                if (isPushingBack()) {
                    return deltaX;
                } else {
                    final double ratio = (double) (Math.abs(getPaddingLeft() - mPaddingLeft)
                            + getPaddingRight() - mPaddingRight)
                            / (double) (getWidth() - mPaddingLeft - mPaddingRight);
                    return (int) (1d / (2d + Math.tan(Math.PI / 2d * ratio)) * (double) deltaX);
                }
        }
        return 0;
    }

    @Override
    public boolean isPushingBack() {
        final int deltaY = mCurrY - mLastY;// 向下滑动为正
        final int deltaX = mCurrX - mLastX;// 向右滑动为正
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_TOP:
                // 向下拉时手指向上滑动
                return deltaY < 0 && getPaddingTop() > mPaddingTop;
            case OVERSCROLL_EDGE_BOTTOM:
                // 向上拉时手指向下滑动
                return deltaY > 0 && getPaddingBottom() > mPaddingBottom;
            case OVERSCROLL_EDGE_TOP_OR_BOTTOM:
                // 向下拉时手指向上滑动
                return deltaY < 0 && getPaddingTop() > mPaddingTop ||
                        // 向上拉时手指向下滑动
                        deltaY > 0 && getPaddingTop() < mPaddingTop;
            case OVERSCROLL_EDGE_LEFT:
                // 向右拉时手指向左滑动
                return deltaX < 0 && getPaddingLeft() > mPaddingLeft;
            case OVERSCROLL_EDGE_RIGHT:
                // 向左拉时手指向右滑动
                return deltaX > 0 && getPaddingRight() > mPaddingRight;
            case OVERSCROLL_EDGE_LEFT_OR_RIGHT:
                // 向右拉时手指向左滑动
                return deltaX < 0 && getPaddingLeft() > mPaddingLeft ||
                        // 向左拉时手指向右滑动
                        deltaX > 0 && getPaddingLeft() < mPaddingLeft;
        }
        return false;
    }

    @Override
    public boolean isAtTheStart() {
        if (getLayoutManager().getItemCount() == 0) return true;

        try {
            switch (getLayoutOrientation()) {
                case VERTICAL:
                    return !ViewCompat.canScrollVertically(this, -1);
                case HORIZONTAL:
                    return !ViewCompat.canScrollHorizontally(this, -1);
            }
        } catch (NullPointerException e) {
            // It causes this exception on invoking #notifyDataSetChanged().
        }
        return false;
    }

    @Override
    public boolean isAtTheEnd() {
        final int lastItemPosition = getLayoutManager().getItemCount() - 1;
        if (lastItemPosition < 0) return true;

        try {
            switch (getLayoutOrientation()) {
                case VERTICAL:
                    return !ViewCompat.canScrollVertically(this, 1);
                case HORIZONTAL:
                    return !ViewCompat.canScrollHorizontally(this, 1);
            }
        } catch (NullPointerException e) {
            //
        }
        return false;
    }

    public void scrollToStart() {
        if (getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) getLayoutManager())
                    .scrollToPositionWithOffset(0, 0);
        } else if (getLayoutManager() instanceof StaggeredGridLayoutManager) {
            ((StaggeredGridLayoutManager) getLayoutManager())
                    .scrollToPositionWithOffset(0, 0);
        }
    }

    public void scrollToEnd() {
        final int position = getLayoutManager().getItemCount() - 1;
        if (position < 0) return;

        // 先粗略地滚到最后一个itemView的位置
        scrollToPosition(getLayoutManager().getItemCount() - 1);
        View lastChild = getChildAt(getChildCount() - 1);

        switch (getLayoutOrientation()) {
            case VERTICAL:
                final int dy = lastChild.getMeasuredHeight() -
                        (getHeight() - getPaddingBottom());
                final int offsetY = -(dy < 0 ? 0 : dy);
                if (getLayoutManager() instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) getLayoutManager())
                            .scrollToPositionWithOffset(position, offsetY);
                } else if (getLayoutManager() instanceof StaggeredGridLayoutManager) {
                    ((StaggeredGridLayoutManager) getLayoutManager())
                            .scrollToPositionWithOffset(position, offsetY);
                }
                break;
            case HORIZONTAL:
                // TODO:scroll this view to the far right.
                // Note that scrollToPositionWithOffset does not work.
                break;
        }
    }

    /**
     * @param from     current padding of top or left
     * @param to       the padding of top or left that the view will be set to.
     * @param duration the time this animation will last for.
     */
    @Override
    public void startHeaderOverscrollAnim(int from, int to, int duration) {
        if (from != to && hasAnimationFinished()) {
            mAnimFlags |= ANIM_FLAG_HEADER_ANIM_RUNNING;
            setupAnim(from, to, duration);
        }
    }

    /**
     * @param from     current padding of bottom or right
     * @param to       the padding of bottom or right that the view will be set to.
     * @param duration the time this animation will last for.
     */
    @Override
    public void startFooterOverscrollAnim(int from, int to, int duration) {
        if (from != to && hasAnimationFinished()) {
            mAnimFlags |= ANIM_FLAG_FOOTER_ANIM_RUNNING;
            setupAnim(from, to, duration);
        }
    }

    private void setupAnim(int from, int to, int duration) {
        mOverscrollAnim = ValueAnimator.ofInt(from, to);
        mOverscrollAnim.addListener(this);
        mOverscrollAnim.addUpdateListener(this);
        mOverscrollAnim.setInterpolator(mInterpolator);
        mOverscrollAnim.setDuration(duration).start();
    }

    private boolean hasAnimationFinished() {
        if ((mAnimFlags & (ANIM_FLAG_HEADER_ANIM_RUNNING | ANIM_FLAG_FOOTER_ANIM_RUNNING)) != 0) {
            Log.e(TAG, "can not start this over-scroll animation " +
                    "till the last animation has finished");
            return false;
        }
        return true;
    }

    @Override
    public void forceEndOverscrollAnim() {
        if ((mAnimFlags & (ANIM_FLAG_HEADER_ANIM_RUNNING | ANIM_FLAG_FOOTER_ANIM_RUNNING)) != 0) {
            mOverscrollAnim.end();
        }
    }

    @Override
    public void onAnimationStart(Animator animation) {
        mViewFlags |= VIEW_FLAG_OVERSCROLLING;
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        if ((mAnimFlags & ANIM_FLAG_HEADER_ANIM_RUNNING) != 0)
            mAnimFlags &= ~ANIM_FLAG_HEADER_ANIM_RUNNING;
        else if ((mAnimFlags & ANIM_FLAG_FOOTER_ANIM_RUNNING) != 0)
            mAnimFlags &= ~ANIM_FLAG_FOOTER_ANIM_RUNNING;
        smoothSpringBack();
    }

    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        final int padding = (int) animation.getAnimatedValue();
        if ((mAnimFlags & ANIM_FLAG_HEADER_ANIM_RUNNING) != 0) {
            switch (getLayoutOrientation()) {
                case VERTICAL:
                    final int pt = getPaddingTop();
                    setPadding(mPaddingLeft, padding, mPaddingRight, mPaddingBottom);
                    // 在顶部回弹时，使view正常显示
                    if (padding > pt) {
                        scrollToStart();
                    }
                    break;
                case HORIZONTAL:
                    final int pl = getPaddingLeft();
                    setPadding(padding, mPaddingTop, mPaddingRight, mPaddingBottom);
                    // 在最左部回弹时，使view正常显示
                    if (padding > pl) {
                        scrollToStart();
                    }
                    break;
            }
        } else if ((mAnimFlags & ANIM_FLAG_FOOTER_ANIM_RUNNING) != 0) {
            switch (getLayoutOrientation()) {
                case VERTICAL:
                    final int pb = getPaddingBottom();
                    setPadding(mPaddingLeft, mPaddingTop, mPaddingRight, padding);
                    // 在底部回弹时，使view正常显示
                    if (padding > pb) {
                        scrollToEnd();
                    }
                    break;
                case HORIZONTAL:
                    final int pr = getPaddingRight();
                    setPadding(mPaddingLeft, mPaddingTop, padding, mPaddingBottom);
                    // 在最右部回弹时，使view正常显示
                    if (padding > pr) {
                        scrollToEnd();
                    }
                    break;
            }
        }
    }

    @Override
    public void smoothSpringBack() {
        if (getPaddingTop() != mPaddingTop) {
            startHeaderOverscrollAnim(getPaddingTop(), mPaddingTop, DURATION_SPRING_BACK);
        } else if (getPaddingBottom() != mPaddingBottom) {
            startFooterOverscrollAnim(getPaddingBottom(), mPaddingBottom, DURATION_SPRING_BACK);
        } else if (getPaddingLeft() != mPaddingLeft) {
            startHeaderOverscrollAnim(getPaddingLeft(), mPaddingLeft, DURATION_SPRING_BACK);
        } else if (getPaddingRight() != mPaddingRight) {
            startFooterOverscrollAnim(getPaddingRight(), mPaddingRight, DURATION_SPRING_BACK);
        } else {
            mOverscrollAnim = null;
            mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
            mViewFlags &= ~VIEW_FLAG_OVERSCROLLING;
        }
    }

    protected class OverFlyingDetector extends OnOverFlyingListener {
        protected OverFlyingDetector() {
            super(getContext());
        }

        @Override
        protected void onTopOverFling(final int overHeight, final int duration) {
            mOverscrollEdge = OVERSCROLL_EDGE_TOP;
            startHeaderOverscrollAnim(getPaddingTop(), mPaddingTop + overHeight, duration);
        }

        @Override
        protected void onBottomOverFling(final int overHeight, final int duration) {
            mOverscrollEdge = OVERSCROLL_EDGE_BOTTOM;
            startFooterOverscrollAnim(getPaddingBottom(), mPaddingBottom + overHeight, duration);
        }

        @Override
        protected void onLeftOverFling(int overWidth, int duration) {
            mOverscrollEdge = OVERSCROLL_EDGE_LEFT;
            startHeaderOverscrollAnim(getPaddingLeft(), mPaddingLeft + overWidth, duration);
        }

        @Override
        protected void onRightOverFling(int overWidth, int duration) {
            mOverscrollEdge = OVERSCROLL_EDGE_RIGHT;
            startFooterOverscrollAnim(getPaddingRight(), mPaddingRight + overWidth, duration);
        }

        @Override
        protected boolean isAtTop() {
            return getLayoutOrientation() == VERTICAL && isAtTheStart();
        }

        @Override
        protected boolean isAtBottom() {
            return getLayoutOrientation() == VERTICAL && isAtTheEnd();
        }

        @Override
        protected boolean isAtFarLeft() {
            return getLayoutOrientation() == HORIZONTAL && isAtTheStart();
        }

        @Override
        protected boolean isAtFarRight() {
            return getLayoutOrientation() == HORIZONTAL && isAtTheEnd();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // reflection methods
    ///////////////////////////////////////////////////////////////////////////

    private Field mLastTouchXField;
    private Field mLastTouchYField;

    private Method mCancelTouchMethod;

    /**
     * Refresh the cached touch position {@link RecyclerView#mLastTouchX,RecyclerView#mLastTouchY}
     * of {@link RecyclerView} to ensure it will scroll up or down
     * within {@code Math.abs(mCurrY - mLastY)} px
     * or scroll left or right not more than {@code Math.abs(mCurrX - mLastX)} px
     * when it receives touch event again.
     */
    private void invalidateParentCachedTouchPos() {
        try {
            switch (getLayoutOrientation()) {
                case VERTICAL:
                    if (mLastTouchYField == null) {
                        mLastTouchYField = RecyclerView.class.getDeclaredField("mLastTouchY");
                        mLastTouchYField.setAccessible(true);
                    }
                    mLastTouchYField.set(this, mLastY);
                    break;
                case HORIZONTAL:
                    if (mLastTouchXField == null) {
                        mLastTouchXField = RecyclerView.class.getDeclaredField("mLastTouchX");
                        mLastTouchXField.setAccessible(true);
                    }
                    mLastTouchXField.set(this, mLastX);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Stop scroll and nested scroll while touching,and clear scroll state.
     *
     * @see RecyclerView#cancelTouch()
     * @see RecyclerView#onTouchEvent(MotionEvent) {@code case MotionEvent.ACTION_CANCEL: }
     */
    protected final void cancelTouch() {
        try {
            if (mCancelTouchMethod == null) {
                mCancelTouchMethod = RecyclerView.class.getDeclaredMethod("cancelTouch");
                mCancelTouchMethod.setAccessible(true);
            }
            mCancelTouchMethod.invoke(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}