package com.liuzhenlin.overscroll;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.AttributeSet;
import android.widget.OverScroller;

/**
 * Created on 2017/10/21. </br>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */

public class SmoothScrollableLinearLayout extends LinearLayoutCompat {
    private final OverScroller mOverScroller;

    public SmoothScrollableLinearLayout(Context context) {
        this(context, null);
    }

    public SmoothScrollableLinearLayout(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SmoothScrollableLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mOverScroller = new OverScroller(context);
    }

    /**
     * Smoothly scroll this view to a position relative to its old position.
     *
     * @param deltaX   The amount of pixels to scroll by horizontally.
     *                 Positive numbers will scroll the view to the right.
     * @param deltaY   The amount of pixels to scroll by vertically.
     *                 Positive numbers will scroll the view up.
     * @param duration duration of the scroll in milliseconds.
     */
    public void smoothScrollBy(int deltaX, int deltaY, int duration) {
        if (deltaX != 0 || deltaY != 0) {
            mOverScroller.startScroll(getScrollX(), getScrollY(), -deltaX, deltaY, duration);
            invalidate();
        }
    }

    /**
     * Smoothly scroll this view to a position.
     *
     * @param desX     The x position to scroll to in pixels.
     * @param desY     The y position to scroll to in pixels.
     * @param duration duration of the scroll in milliseconds.
     */
    public void smoothScrollTo(int desX, int desY, int duration) {
        final int scrollX = getScrollX();
        final int scrollY = getScrollY();

        final boolean finished = mOverScroller.isFinished();
        if (finished && (-scrollX != desX || scrollY != desY) ||
                !finished && (mOverScroller.getFinalX() != desX || mOverScroller.getFinalY() != desY)) {

            final int deltaX = scrollX - (desX > 0 ? desX : -desX);
            final int deltaY = desY - scrollY;
            smoothScrollBy(deltaX, deltaY, duration);
        }
    }

    @Override
    public void computeScroll() {
        // 重写computeScroll()方法，并在其内部完成平滑滚动的逻辑
        if (mOverScroller.computeScrollOffset()) {
            scrollTo(mOverScroller.getCurrX(), mOverScroller.getCurrY());
            invalidate();
        }
    }
}