package com.liuzhenlin.overscroll;

import android.support.annotation.IntDef;
import android.view.MotionEvent;

/**
 * Created on 2017/12/23. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
public interface OverScrollBase {
    /**
     * 回弹时间
     */
    int DURATION_SPRING_BACK = 250; // ms

    int OVERSCROLL_EDGE_UNSPECIFIED = 0;
    int OVERSCROLL_EDGE_TOP = 1;
    int OVERSCROLL_EDGE_BOTTOM = 2;
    int OVERSCROLL_EDGE_TOP_OR_BOTTOM = 3;
    int OVERSCROLL_EDGE_LEFT = 4;
    int OVERSCROLL_EDGE_RIGHT = 5;
    int OVERSCROLL_EDGE_LEFT_OR_RIGHT = 6;

    @IntDef({
            OVERSCROLL_EDGE_UNSPECIFIED,
            OVERSCROLL_EDGE_TOP, OVERSCROLL_EDGE_BOTTOM, OVERSCROLL_EDGE_TOP_OR_BOTTOM,
            OVERSCROLL_EDGE_LEFT, OVERSCROLL_EDGE_RIGHT, OVERSCROLL_EDGE_LEFT_OR_RIGHT
    })
    @interface OverscrollEdge {
    }

    boolean handleOverscroll(MotionEvent e);

    boolean isTendToScrollCurrView();

    int computeOverscrollDeltaY();

    int computeOverscrollDeltaX();

    /**
     * 是否向下拉时手指向上滑动或向上拉时手指向下滑动 或
     * 向右拉时手指向左滑动或向左拉时手指向右滑动
     */
    boolean isPushingBack();

    boolean isAtTheStart();

    boolean isAtTheEnd();

    void startHeaderOverscrollAnim(int from, int to, int duration);

    void startFooterOverscrollAnim(int from, int to, int duration);

    /**
     * 回弹至初始位置
     */
    void smoothSpringBack();
}
