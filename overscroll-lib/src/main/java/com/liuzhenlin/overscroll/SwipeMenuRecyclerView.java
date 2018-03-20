package com.liuzhenlin.overscroll;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.liuzhenlin.overscroll.listener.OnOverFlyingListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.support.v4.widget.ViewDragHelper.INVALID_POINTER;

/**
 * Created on 2017/12/16. </br>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
@SuppressWarnings({"DeprecatedIsStillUsed", "deprecation"})
public class SwipeMenuRecyclerView extends RecyclerView implements OverScrollBase {
    // @formatter:off
    private static final String TAG = "SwipeMenuRecyclerView";

    protected static final int NO_ORIENTATION = -1;

    protected static final int UNRESOLVED_PADDING = Integer.MIN_VALUE;
    private int mPaddingLeft = UNRESOLVED_PADDING;
    private int mPaddingTop = UNRESOLVED_PADDING;
    private int mPaddingRight = UNRESOLVED_PADDING;
    private int mPaddingBottom = UNRESOLVED_PADDING;

    /** dp */
    protected final float mDensity;

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

    /**
     * 是否应该拦截touch事件
     */
    private boolean mShouldInterceptTouchEvent;

    /** 当前view是否正在滚动 */
    private boolean mIsScrolling;

    /** 最近一次滚动的itemView */
    private SmoothScrollableLinearLayout mItemView;
    /** 上次滚动的itemView */
    private SmoothScrollableLinearLayout mLastItemView;

    private final Rect mItemTouchRect = new Rect();

    /** itemView向左滚动时的最大滚动距离 */
    private int mMaxLeftScrollX;
    /** itemView向右滚动时的最大滚动距离 */
    private int mMaxRightScrollX;

    /** itemView是否可以滚动 */
    private boolean mItemScrollingEnabled;// default true

    /**
     * itemView是否可以向右滑动且松手后有自动回弹的效果
     * @see #setItemSpringBackEnabled(boolean)
     */
    private boolean mItemSpringBackEnabled;// default true

    /** itemView是否正在滚动 */
    private boolean mIsItemScrolling;

    /** itemView是否已完全滚开 */
    private boolean mIsItemFullScrolled;

    private final OverFlyingDetector mOverflyingDetector;

    @OverscrollEdge
    protected int mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;

    /**
     * 是否可以过度滚动
     * @see #setOverscrollEnabled(boolean)
     */
    private boolean mOverscrollEnabled;// default true

    /** 手指是否正在拖拽着当前view，使其发生滚动 */
    private boolean mIsDraggedOverscrolling;
    // @formatter:on

    public boolean isScrolling() {
        return mIsScrolling;
    }

    public boolean isItemScrollingEnabled() {
        return mItemScrollingEnabled && getLayoutOrientation() == VERTICAL;
    }

    /**
     * only works on vertical layout
     */
    public void setItemScrollingEnabled(boolean itemScrollingEnabled) {
        mItemScrollingEnabled = itemScrollingEnabled;
    }

    public boolean isItemSpringBackEnabled() {
        return mItemSpringBackEnabled;
    }

    public void setItemSpringBackEnabled(boolean itemSpringBackEnabled) {
        mItemSpringBackEnabled = itemSpringBackEnabled;
        mMaxRightScrollX = mItemSpringBackEnabled ? (int) (10f * mDensity + 0.5f) : 0;
    }

    public boolean isItemScrolling() {
        return mIsItemScrolling;
    }

    public boolean isItemFullScrolled() {
        return mIsItemFullScrolled;
    }

    public boolean isOverscrollEnabled() {
        return mOverscrollEnabled && getChildCount() > 0;
    }

    public void setOverscrollEnabled(boolean overscrollEnabled) {
        mOverscrollEnabled = overscrollEnabled;
        if (mOverscrollEnabled) {
            // 禁用拉到两端时发荧光的效果
            setOverScrollMode(OVER_SCROLL_NEVER);
            // 将滚动条设置在padding区域外并且覆盖在view上
            setScrollBarStyle(SCROLLBARS_OUTSIDE_OVERLAY);
        }
    }

    public boolean isOverscrolling() {
        return mOverscrollEnabled && mOverscrollEdge != OVERSCROLL_EDGE_UNSPECIFIED;
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
        mDensity = getContext().getResources().getDisplayMetrics().density;
        mTouchSlop = mOverflyingDetector.mTouchSlop;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SwipeMenuRecyclerView, defStyle, 0);
        setOverscrollEnabled(a.getBoolean(R.styleable
                .SwipeMenuRecyclerView_overscrollEnabled, true));
        mItemScrollingEnabled = a.getBoolean(R.styleable
                .SwipeMenuRecyclerView_itemScrollingEnabled, true);
        setItemSpringBackEnabled(a.getBoolean(R.styleable
                .SwipeMenuRecyclerView_itemSpringBackEnabled, true));
        mIsUsingAdapterWrapper = a.getBoolean(R.styleable
                .SwipeMenuRecyclerView_useHeaderAndFooterWrapper, false);
        a.recycle();
    }

    @Override
    public void setOverScrollMode(int overScrollMode) {
        if (mOverscrollEnabled && getOverScrollMode() == View.OVER_SCROLL_NEVER) {
            return;
        }
        super.setOverScrollMode(overScrollMode);
    }

    /**
     * If you want to set padding for this view,you should invoke this method
     * instead of invoking {@link #setPadding(int, int, int, int)} directly.
     */
    public void setSettledPadding(int left, int top, int right, int bottom) {
        mPaddingLeft = left;
        mPaddingTop = top;
        mPaddingRight = right;
        mPaddingBottom = bottom;
        setPadding(left, top, right, bottom);
    }

    public int getSettledPaddingLeft() {
        return mPaddingLeft == UNRESOLVED_PADDING ? getPaddingLeft() : mPaddingLeft;
    }

    public int getSettledPaddingTop() {
        return mPaddingTop == UNRESOLVED_PADDING ? getPaddingTop() : mPaddingTop;
    }

    public int getSettledPaddingRight() {
        return mPaddingRight == UNRESOLVED_PADDING ? getPaddingRight() : mPaddingRight;
    }

    public int getSettledPaddingBottom() {
        return mPaddingBottom == UNRESOLVED_PADDING ? getPaddingBottom() : mPaddingBottom;
    }

    @RequiresApi(api = JELLY_BEAN_MR1)
    public void setSettledPaddingRelative(int start, int top, int end, int bottom) {
        switch (getLayoutDirection()) {
            case LAYOUT_DIRECTION_RTL:
                mPaddingLeft = end;
                mPaddingRight = start;
                break;
            case LAYOUT_DIRECTION_LTR:
            default:
                mPaddingLeft = start;
                mPaddingRight = end;
                break;
        }
        mPaddingTop = top;
        mPaddingBottom = bottom;
        setPaddingRelative(start, top, end, bottom);
    }

    @RequiresApi(api = JELLY_BEAN_MR1)
    public int getSettledPaddingStart() {
        return (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ?
                getSettledPaddingRight() : getSettledPaddingLeft();
    }

    @RequiresApi(api = JELLY_BEAN_MR1)
    public int getSettledPaddingEnd() {
        return (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ?
                getSettledPaddingLeft() : getSettledPaddingRight();
    }

    /**
     * @deprecated use {@link #setSettledPadding(int, int, int, int)} instead.
     */
    @Deprecated
    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
    }

    /**
     * @deprecated use {@link #getSettledPaddingLeft()} instead.
     */
    @Deprecated
    @Override
    public int getPaddingLeft() {
        return super.getPaddingLeft();
    }

    /**
     * @deprecated use {@link #getSettledPaddingTop()} instead.
     */
    @Deprecated
    @Override
    public int getPaddingTop() {
        return super.getPaddingTop();
    }

    /**
     * @deprecated use {@link #getSettledPaddingRight()} instead.
     */
    @Deprecated
    @Override
    public int getPaddingRight() {
        return super.getPaddingRight();
    }

    /**
     * @deprecated use {@link #getSettledPaddingBottom()} instead.
     */
    @Deprecated
    @Override
    public int getPaddingBottom() {
        return super.getPaddingBottom();
    }

    /**
     * @deprecated use {@link #setSettledPaddingRelative(int, int, int, int)} instead.
     */
    @RequiresApi(api = JELLY_BEAN_MR1)
    @Deprecated
    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        setSettledPaddingRelative(start, top, end, bottom);
    }

    /**
     * @deprecated use {@link #getSettledPaddingStart()} instead.
     */
    @RequiresApi(api = JELLY_BEAN_MR1)
    @Deprecated
    @Override
    public int getPaddingStart() {
        return getSettledPaddingStart();
    }

    /**
     * @deprecated use {@link #getSettledPaddingEnd()} instead.
     */
    @RequiresApi(api = JELLY_BEAN_MR1)
    @Deprecated
    @Override
    public int getPaddingEnd() {
        return getSettledPaddingEnd();
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
                if (!mIsScrolling) {
                    mIsScrolling = isTendToScrollCurrView();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsScrolling = false;
                mShouldInterceptTouchEvent = false;
                break;
        }
        markCurrXY(e);
        return mIsConsumed || super.onTouchEvent(e);
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
                if (mIsItemFullScrolled) {
                    if (mLastItemView == mItemView) {
                        // 点击的不是itemView滚开后显示的item（按钮）时,
                        // 才拦截touch事件，子view将无法收到touch事件
                        if (mDownX < mLastItemView.getWidth() - mLastItemView.getScrollX()) {
                            mShouldInterceptTouchEvent = true;
                        }
                    } else {
                        // 之前滑开的和现在点击的不是同一个，之前的隐藏
                        releaseItemViewInternal(mLastItemView, 500);
                        mShouldInterceptTouchEvent = true;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                // 如果在滑动itemView
                if (mIsItemScrolling) {
                    // 不显示滚动条
                    setVerticalScrollBarEnabled(false);

                    int dx = mLastX - mCurrX; // 向左滑动为正
                    final int scrollX = mItemView.getScrollX(); // 向左滚动为正
                    // 向左滑动
                    if (dx > 0) {
                        if (scrollX > mMaxLeftScrollX) {
                            dx = 0;
                        } else if (dx + scrollX > mMaxLeftScrollX) {
                            dx = mMaxLeftScrollX - scrollX;
                        }
                        // 向右滑动
                    } else if (dx < 0) {
                        if (scrollX < -mMaxRightScrollX) {
                            dx = 0;
                        } else if (scrollX + dx < -mMaxRightScrollX) {
                            dx = -mMaxRightScrollX - scrollX;
                        }
                    }
                    mItemView.smoothScrollBy(-dx, 0, 0);

                    // 消费当前touch事件且不调用父类onTouchEvent(e)
                    // --> 列表无法上下滚动
                    return true;
                }

                if (mIsScrolling) {
                    // 显示竖直滚动条
                    setVerticalScrollBarEnabled(true);
                    // 自动隐藏滑开的itemView
                    releaseItemView();
                } else {
                    if (shouldHandleItemScrollingEvent()) {
                        mIsItemScrolling = true;
                        mShouldInterceptTouchEvent = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                // 之前滑开的和现在点击的是同一个itemView，如果没有继续滑动，隐藏它
                if (mLastItemView == mItemView && !mIsItemScrolling) {
                    releaseItemView();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsItemScrolling) {
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
                        mIsItemFullScrolled = true;

                        // 左滑时 完全滑开
                    } else if (scrollX == mMaxLeftScrollX) {
                        mIsItemFullScrolled = true;

                        // 右滑时
                    } else if (scrollX < 0) {
                        // 从右 mMaxRightScrollX 向左平滑滚动 2*mMaxRightScrollX 到
                        // 左maxRightScrollX位置
                        mItemView.smoothScrollTo(-mMaxRightScrollX, 0, 150);
                        mIsItemFullScrolled = true;

                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // 从左 mMaxRightScrollX 向右平滑滚动 mMaxRightScrollX 回到原点
                                releaseItemViewInternal(mItemView, 150);
                            }
                        }, 150);
                    }
                    mIsItemScrolling = false;
                    mLastItemView = mItemView;
                    return true;
                }
            case MotionEvent.ACTION_CANCEL:
                mIsItemScrolling = false;
                mLastItemView = mItemView;
                releaseItemView();
                break;
        }
        return false;
    }

    protected boolean shouldHandleItemScrollingEvent() {
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
        if (itemView == null) {
            return;
        }
        itemView.smoothScrollTo(0, 0, duration);
        mIsItemFullScrolled = false;
    }

    @Override
    public boolean handleOverscroll(MotionEvent e) {
        if (!mOverscrollEnabled) {
            return false;
        }
        mOverflyingDetector.onTouchEvent(e);

        // 列表发生上下滚动时才处理
        if (!mIsScrolling) {
            return false;
        }
        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE:
                final int deltaY = computeOverscrollDeltaY();
                final int deltaX = computeOverscrollDeltaX();
                if (deltaX == 0 && deltaY == 0 && mIsDraggedOverscrolling) {
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
                                    mOverscrollEdge = OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM;
                                    // 固定列表的高度
                                    getLayoutParams().height = getHeight();
                                    // 下拉
                                } else if (atTop && dy > 0) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_TOP;
                                    // 上拉
                                } else if (atBottom && dy < 0) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_BOTTOM;
                                } else break;
                                mIsDraggedOverscrolling = true;
                                break;
                            case HORIZONTAL:
                                final int dx = mCurrX - mDownX;
                                final boolean atFarLeft = isAtTheStart();
                                final boolean atFarRight = isAtTheEnd();
                                // itemView较少时，列表不能左右滚动 --> 不限制右拉和左拉
                                if (atFarLeft && atFarRight) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT;
                                    // 固定列表的宽度
                                    getLayoutParams().width = getWidth();
                                    // 右拉
                                } else if (atFarLeft && dx > 0) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_LEFT;
                                    // 左拉
                                } else if (atFarRight && dx < 0) {
                                    mOverscrollEdge = OVERSCROLL_EDGE_RIGHT;
                                } else break;
                                mIsDraggedOverscrolling = true;
                                break;
                        }
                        break;

                    case OVERSCROLL_EDGE_TOP: {
                        if (!isAtTheStart()) {
                            if (isAtTheEnd()) {
                                mOverscrollEdge = OVERSCROLL_EDGE_BOTTOM;
                            }
                            break;
                        }

                        final int old = getPaddingTop();
                        final int paddingTop = old + deltaY < mPaddingTop ?
                                mPaddingTop : old + deltaY;
                        setPadding(mPaddingLeft, paddingTop, mPaddingRight, mPaddingBottom);

                        if (getPaddingTop() < old) {
                            invalidateParentCachedTouchPos();
                            if (paddingTop == mPaddingTop) {
                                break;
                            }
                            return true;
                        }
                    }
                    // Not consume this event when user scroll this view down,
                    // to enable nested scrolling.
                    break;

                    case OVERSCROLL_EDGE_BOTTOM: {
                        if (!isAtTheEnd()) {
                            if (isAtTheStart()) {
                                mOverscrollEdge = OVERSCROLL_EDGE_TOP;
                            }
                            break;
                        }

                        final int old = getPaddingBottom();
                        final int paddingBottom = old - deltaY < mPaddingBottom ?
                                mPaddingBottom : old - deltaY;
                        setPadding(mPaddingLeft, mPaddingTop, mPaddingRight, paddingBottom);

                        if (getPaddingBottom() < old) {
                            invalidateParentCachedTouchPos();
                            if (paddingBottom == mPaddingBottom) {
                                break;
                            }
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM: {
                        final int old = getPaddingTop();
                        final int paddingTop = old + deltaY;
                        setPadding(mPaddingLeft, paddingTop, mPaddingRight, mPaddingBottom);

                        if (paddingTop > mPaddingTop && paddingTop < old
                                || paddingTop < mPaddingTop && paddingTop > old) {
                            invalidateParentCachedTouchPos();
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_LEFT: {
                        if (!isAtTheStart()) {
                            if (isAtTheEnd()) {
                                mOverscrollEdge = OVERSCROLL_EDGE_RIGHT;
                            }
                            break;
                        }

                        final int old = getPaddingLeft();
                        final int paddingLeft = old + deltaX < mPaddingLeft ?
                                mPaddingLeft : old + deltaX;
                        setPadding(paddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom);

                        if (getPaddingLeft() < old) {
                            invalidateParentCachedTouchPos();
                            if (paddingLeft == mPaddingLeft) {
                                break;
                            }
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_RIGHT: {
                        if (!isAtTheEnd()) {
                            if (isAtTheStart()) {
                                mOverscrollEdge = OVERSCROLL_EDGE_LEFT;
                            }
                            break;
                        }

                        final int old = getPaddingRight();
                        final int paddingRight = old - deltaX < mPaddingRight ?
                                mPaddingRight : old - deltaX;
                        setPadding(mPaddingLeft, mPaddingTop, paddingRight, mPaddingBottom);

                        if (getPaddingRight() < old) {
                            invalidateParentCachedTouchPos();
                            if (paddingRight == mPaddingRight) {
                                break;
                            }
                            return true;
                        }
                    }
                    break;

                    case OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT: {
                        final int old = getPaddingLeft();
                        final int paddingLeft = old + deltaX;
                        setPadding(paddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom);

                        if (paddingLeft > mPaddingLeft && paddingLeft < old
                                || paddingLeft < mPaddingLeft && paddingLeft > old) {
                            invalidateParentCachedTouchPos();
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
                    cancelTouch(); // clear scroll state
                    mIsDraggedOverscrolling = false;
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean isTendToScrollCurrView() {
        final int absDX = Math.abs(mCurrX - mDownX);
        final int absDY = Math.abs(mCurrY - mDownY);
        switch (getLayoutOrientation()) {
            case VERTICAL:
                // 不在触摸移动
                if (mLastY == mDownY || mLastY == mCurrY) {
                    return false;
                }
                return absDY > absDX && absDY >= mTouchSlop
                        || mTotalAbsDeltaY > mTotalAbsDeltaX && mTotalAbsDeltaY >= mTouchSlop;
            case HORIZONTAL:
                // 不在触摸移动
                if (mLastX == mDownX || mLastX == mCurrX) {
                    return false;
                }
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
            case OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM:
                final int deltaY = mCurrY - mLastY;// 向下滑动为正
                if (isPushingBack()) {
                    return deltaY;
                } else {
                    final double ratio = (double) (Math.abs(getPaddingTop() - mPaddingTop)
                            + getPaddingBottom() - mPaddingBottom) / (double) getHeight();
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
            case OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT:
                final int deltaX = mCurrX - mLastX;// 向右滑动为正
                if (isPushingBack()) {
                    return deltaX;
                } else {
                    final double ratio = (double) (Math.abs(getPaddingLeft() - mPaddingLeft)
                            + getPaddingRight() - mPaddingRight) / (double) getWidth();
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
            case OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM:
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
            case OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT:
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

        boolean overscroll = false;
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_TOP:
                overscroll = getPaddingTop() > mPaddingTop;
                break;
            case OVERSCROLL_EDGE_LEFT:
                overscroll = getPaddingLeft() > mPaddingLeft;
                break;
            case OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM:
            case OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT:
                return true;
        }
        if (overscroll) {
            return true;
        }

        try {
            if (getLayoutManager() instanceof LinearLayoutManager) {
                return ((LinearLayoutManager) getLayoutManager())
                        .findFirstCompletelyVisibleItemPosition() == 0;
            } else if (getLayoutManager() instanceof StaggeredGridLayoutManager) {
                return ((StaggeredGridLayoutManager) getLayoutManager())
                        .findFirstCompletelyVisibleItemPositions(null)[0] == 0;
            }
            switch (getLayoutOrientation()) {
                case VERTICAL:
                    return !canScrollVertically(-1);
                case HORIZONTAL:
                    return !canScrollHorizontally(-1);
            }
        } catch (NullPointerException e) {
            //
        }
        return false;
    }

    @Override
    public boolean isAtTheEnd() {
        final int lastItemPosition = getLayoutManager().getItemCount() - 1;
        if (lastItemPosition < 0) return true;

        boolean overscroll = false;
        switch (mOverscrollEdge) {
            case OVERSCROLL_EDGE_BOTTOM:
                overscroll = getPaddingBottom() > mPaddingBottom;
                break;
            case OVERSCROLL_EDGE_RIGHT:
                overscroll = getPaddingRight() > mPaddingRight;
                break;
            case OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM:
            case OVERSCROLL_EDGE_LEFT | OVERSCROLL_EDGE_RIGHT:
                return true;
        }
        if (overscroll) {
            return true;
        }

        try {
            if (getLayoutManager() instanceof LinearLayoutManager) {
                return ((LinearLayoutManager) getLayoutManager())
                        .findLastCompletelyVisibleItemPosition() == lastItemPosition;
            } else if (getLayoutManager() instanceof StaggeredGridLayoutManager) {
                final int[] positions = ((StaggeredGridLayoutManager) getLayoutManager()).findLastCompletelyVisibleItemPositions(null);
                for (int position : positions) {
                    if (position == lastItemPosition) return true;
                }
            }
            switch (getLayoutOrientation()) {
                case VERTICAL:
                    return !canScrollVertically(1);
                case HORIZONTAL:
                    return !canScrollHorizontally(1);
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

    private ValueAnimator mHeaderOverscrollAnim;
    private ValueAnimator mFooterOverscrollAnim;

    private ValueAnimator.AnimatorUpdateListener mAnimUpdateListener;
    private Animator.AnimatorListener mAnimListener;

    /**
     * @param from     current padding of top or left
     * @param to       the padding of top or left that the view will be set to.
     * @param duration the time this animation will last for.
     */
    @Override
    public void startHeaderOverscrollAnim(int from, int to, int duration) {
        if (from == to) {
            return;
        }
        if (mHeaderOverscrollAnim != null) {
            Log.e(TAG, "can not start this header over-scroll animation " +
                    "till the last animation has finished");
            return;
        }
        mHeaderOverscrollAnim = ValueAnimator.ofInt(from, to);
        mHeaderOverscrollAnim.setDuration(duration);
        initAnimListeners();
        mHeaderOverscrollAnim.addListener(mAnimListener);
        mHeaderOverscrollAnim.start();
    }

    /**
     * @param from     current padding of bottom or right
     * @param to       the padding of bottom or right that the view will be set to.
     * @param duration the time this animation will last for.
     */
    @Override
    public void startFooterOverscrollAnim(int from, int to, int duration) {
        if (from == to) {
            return;
        }
        if (mFooterOverscrollAnim != null) {
            Log.e(TAG, "can not start this footer over-scroll animation " +
                    "till the last animation has finished");
            return;
        }
        mFooterOverscrollAnim = ValueAnimator.ofInt(from, to);
        mFooterOverscrollAnim.setDuration(duration);
        initAnimListeners();
        mFooterOverscrollAnim.addListener(mAnimListener);
        mFooterOverscrollAnim.start();
    }

    private void initAnimListeners() {
        if (mAnimUpdateListener == null) {
            mAnimUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    final int padding = (int) animation.getAnimatedValue();
                    if (animation == mHeaderOverscrollAnim) {
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
                    } else if (animation == mFooterOverscrollAnim) {
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
            };
        }
        if (mAnimListener == null) {
            mAnimListener = new Animator.AnimatorListener() {
                final Interpolator interpolator = new DecelerateInterpolator();

                @Override
                public void onAnimationStart(Animator animation) {
                    ((ValueAnimator) animation).addUpdateListener(mAnimUpdateListener);
                    animation.setInterpolator(interpolator);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation == mHeaderOverscrollAnim) {
                        mHeaderOverscrollAnim = null;
                    } else if (animation == mFooterOverscrollAnim) {
                        mFooterOverscrollAnim = null;
                    }
                    smoothSpringBack();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            };
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
            mOverscrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
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
            return isAtTheStart() && getLayoutOrientation() == VERTICAL;
        }

        @Override
        protected boolean isAtBottom() {
            return isAtTheEnd() && getLayoutOrientation() == VERTICAL;
        }

        @Override
        protected boolean isAtFarLeft() {
            return isAtTheStart() && getLayoutOrientation() == HORIZONTAL;
        }

        @Override
        protected boolean isAtFarRight() {
            return isAtTheEnd() && getLayoutOrientation() == HORIZONTAL;
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

    ///////////////////////////////////////////////////////////////////////////
    // Adapter(wrapper of headerView and footerView) and ViewHolder
    ///////////////////////////////////////////////////////////////////////////

    private HeaderAndFooterWrapper mWrapper;

    protected boolean mIsUsingAdapterWrapper;// default false

    protected HeaderAndFooterWrapper getAdapterWrapper() {
        return mWrapper;
    }

    public boolean isUsingAdapterWrapper() {
        return mWrapper != null;
    }

    /**
     * Must be invoked before per invoking of {@link #setAdapter(Adapter)},
     * or else it will not be valid unless you invoke that method later.
     */
    public void setUsingAdapterWrapper(boolean usingAdapterWrapper) {
        mIsUsingAdapterWrapper = usingAdapterWrapper;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        mWrapper = null;
        if (mIsUsingAdapterWrapper) {
            mWrapper = new HeaderAndFooterWrapper(adapter);
        }
        super.setAdapter(mWrapper == null ? adapter : mWrapper);
    }

    @Override
    public Adapter getAdapter() {
        if (isUsingAdapterWrapper()) {
            return mWrapper.mInnerAdapter;
        }
        return super.getAdapter();
    }

    public void addHeaderView(View view) {
        if (isUsingAdapterWrapper()) {
            mWrapper.addHeaderView(view);
        }
    }

    public void addFooterView(View view) {
        if (isUsingAdapterWrapper()) {
            mWrapper.addFooterView(view);
        }
    }

    public <V extends View> V getHeaderView(int position) {
        if (isUsingAdapterWrapper()) {
            return mWrapper.getHeaderView(position);
        }
        return null;
    }

    public <V extends View> V getFooterView(int position) {
        if (isUsingAdapterWrapper()) {
            return mWrapper.getFooterView(position);
        }
        return null;
    }

    public int getHeaderCount() {
        if (isUsingAdapterWrapper()) {
            return mWrapper.getHeaderCount();
        }
        return 0;
    }

    public int getFooterCount() {
        if (isUsingAdapterWrapper()) {
            return mWrapper.getFooterCount();
        }
        return 0;
    }

    protected static class HeaderAndFooterWrapper extends Adapter {
        protected static final int BASE_ITEM_TYPE_HEADER = 1000_0000;
        protected static final int BASE_ITEM_TYPE_FOOTER = 2000_0000;

        protected final SparseArray<View> mHeaderViews = new SparseArray<>();
        protected final SparseArray<View> mFooterViews = new SparseArray<>();

        protected final Adapter mInnerAdapter;

        protected HeaderAndFooterWrapper(Adapter adapter) {
            mInnerAdapter = adapter;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (mHeaderViews.get(viewType) != null) {
                return new ViewHolder(mHeaderViews.get(viewType));
            } else if (mFooterViews.get(viewType) != null) {
                return new ViewHolder(mFooterViews.get(viewType));
            }
            return mInnerAdapter.onCreateViewHolder(parent, viewType);
        }

        @Override
        public int getItemViewType(int position) {
            if (isHeaderViewPos(position)) {
                return mHeaderViews.keyAt(position);
            } else if (isFooterViewPos(position)) {
                return mFooterViews.keyAt(position - getHeaderCount() - getInitialItemCount());
            }
            return mInnerAdapter.getItemViewType(position - getHeaderCount());
        }

        @Override
        public int getItemCount() {
            return getHeaderCount() + getFooterCount() + getInitialItemCount();
        }

        protected int getInitialItemCount() {
            return mInnerAdapter.getItemCount();
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (isHeaderViewPos(position)) {
                return;
            }
            if (isFooterViewPos(position)) {
                return;
            }
            mInnerAdapter.onBindViewHolder(holder, position - getHeaderCount());
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            mInnerAdapter.onAttachedToRecyclerView(recyclerView);

            LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof GridLayoutManager) {
                final GridLayoutManager gridLayoutManager = (GridLayoutManager) layoutManager;
                final GridLayoutManager.SpanSizeLookup spanSizeLookup = gridLayoutManager.getSpanSizeLookup();
                gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        final int viewType = getItemViewType(position);
                        if (mHeaderViews.get(viewType) != null) {
                            return gridLayoutManager.getSpanCount();
                        } else if (mFooterViews.get(viewType) != null) {
                            return gridLayoutManager.getSpanCount();
                        }
                        if (spanSizeLookup != null)
                            return spanSizeLookup.getSpanSize(position);
                        return 1;
                    }
                });
                gridLayoutManager.setSpanCount(gridLayoutManager.getSpanCount());
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            mInnerAdapter.onViewAttachedToWindow(holder);

            final int position = holder.getLayoutPosition();
            if (isHeaderViewPos(position) || isFooterViewPos(position)) {
                ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
                if (lp != null && lp instanceof StaggeredGridLayoutManager.LayoutParams) {
                    StaggeredGridLayoutManager.LayoutParams p = (StaggeredGridLayoutManager.LayoutParams) lp;
                    p.setFullSpan(true);
                }
            }
        }

        protected boolean isHeaderViewPos(int position) {
            return position < getHeaderCount();
        }

        protected boolean isFooterViewPos(int position) {
            return position >= getHeaderCount() + getInitialItemCount();
        }

        public void addHeaderView(View view) {
            mHeaderViews.put(BASE_ITEM_TYPE_HEADER + mHeaderViews.size(), view);
        }

        public void addFooterView(View view) {
            mFooterViews.put(BASE_ITEM_TYPE_FOOTER + mFooterViews.size(), view);
        }

        public <V extends View> V getHeaderView(int position) {
            return (V) mHeaderViews.valueAt(position);
        }

        public <V extends View> V getFooterView(int position) {
            return (V) mFooterViews.valueAt(position - getHeaderCount());
        }

        public int getHeaderCount() {
            return mHeaderViews.size();
        }

        public int getFooterCount() {
            return mFooterViews.size();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final SparseArray<View> mViews = new SparseArray<>();

        public ViewHolder(View itemView) {
            super(itemView);
            mViews.put(itemView.getId(), itemView);
        }

        /**
         * 通过view的id获取控件
         */
        public <V extends View> V findViewById(@IdRes int id) {
            V view = (V) mViews.get(id);
            if (view == null) {
                view = itemView.findViewById(id);
                mViews.put(id, view);
            }
            return view;
        }
    }
}