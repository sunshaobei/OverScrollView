package com.liuzhenlin.overscroll.listener;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * Created on 2017/12/19. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
public abstract class OnOverFlyingListener implements GestureDetector.OnGestureListener {
    // @formatter:off
    private final OverFlyingHandler mHandler;

    private final GestureDetector mGestureDetector;

    public final int mTouchSlop;

    /** 是否从最顶部开始fling */
    private boolean mIsFromTop;
    /** 是否从最底部开始fling */
    private boolean mIsFromBottom;
    /** 是否从最左部开始fling */
    private boolean mIsFromFarLeft;
    /** 是否从最右部开始fling */
    private boolean mIsFromFarRight;

    /** 满足过度滚动的手势最低速度(默认3000 px/s ) */
    private static final int OVER_FLYING_MIN_V = 3000;

    /** fling时水平方向的速度 px/s */
    private float mVelocityX;
    /** fling时竖直方向的速度 px/s */
    private float mVelocityY;
    /** 手指放下到抬起时的水平位移改变量，向右滑为正 */
    private int mDeltaX;
    /** 手指放下到抬起时的的竖直位移改变量，向下滑为正 */
    private int mDeltaY;

    // 针对View的延时策略
    /** 开始计算 */
    private static final int MSG_START_COMPUTE_FLYING = 0;
    /** 继续计算 */
    private static final int MSG_CONTINUE_COMPUTE_FLYING = 1;
    /** 停止计算 */
    private static final int MSG_STOP_COMPUTE_OVERFLYING = 2;

    /** 最大计算次数 */
    private static final int MAX_COMPUTE_TIMES = 100;  // 10ms计算一次,总共计算100次

    /** 当前计算次数 */
    private int mCurrComputeTimes = 0;

    /** 过度滚动到最大距离所花的时间 ms */
    private static final int DURATION_OVERFLYING = 120;
    // @formatter:on

    public OnOverFlyingListener(Context context) {
        mHandler = new OverFlyingHandler();
        mGestureDetector = new GestureDetector(context, this, mHandler);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public final boolean onTouchEvent(MotionEvent ev) {
        return mGestureDetector.onTouchEvent(ev);
    }

    @Override
    public boolean onDown(MotionEvent ev) {
        mVelocityX = mVelocityY = 0;
        mIsFromTop = isAtTop();
        mIsFromBottom = isAtBottom();
        mIsFromFarLeft = isAtFarLeft();
        mIsFromFarRight = isAtFarRight();
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent downEvent, MotionEvent currentEvent, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    /**
     * fling到两端时才触发OverFling，获取速度并采用演示策略估算View是否滚动到边界
     * 1.监听fling动作 2.获取手指滑动速度（存在滑动但非fling的状态）
     */
    @Override
    public boolean onFling(MotionEvent downEvent, MotionEvent upEvent, float velocityX, float velocityY) {
        mDeltaX = (int) (upEvent.getX() - downEvent.getX());
        mDeltaY = (int) (upEvent.getY() - downEvent.getY());
        final int absDX = Math.abs(mDeltaX);
        final int absDY = Math.abs(mDeltaY);
        Log.d("OnOverFlyingListener", velocityX + "   " + velocityY);

        // 上下flying
        if (absDY > absDX && absDY >= mTouchSlop && Math.abs(velocityY) >= OVER_FLYING_MIN_V) {
            // 控件在顶部滚动且向下fling或在底部滚动且向上fling
            if (mIsFromTop && mDeltaY > 0 || mIsFromBottom && mDeltaY < 0) {
                return false;
            }
            mVelocityY = velocityY;
            mHandler.sendEmptyMessage(MSG_START_COMPUTE_FLYING);

            // 左右flying
        } else if (absDX > absDY && absDX >= mTouchSlop && Math.abs(velocityX) >= OVER_FLYING_MIN_V) {
            // 控件在最左部滚动且向右fling或最右部滚动且向左fling
            if (mIsFromFarLeft && mDeltaX > 0 || mIsFromFarRight && mDeltaX < 0) {
                return false;
            }
            mVelocityX = velocityX;
            mHandler.sendEmptyMessage(MSG_START_COMPUTE_FLYING);
        }
        return false;
    }

    // @formatter:off
    protected abstract void onTopOverFling(int overHeight, int duration);
    protected abstract void onBottomOverFling(int overHeight, int duration);
    protected abstract void onLeftOverFling(int overWidth, int duration);
    protected abstract void onRightOverFling(int overWidth, int duration);

    protected abstract boolean isAtTop();
    protected abstract boolean isAtBottom();
    protected abstract boolean isAtFarLeft();
    protected abstract boolean isAtFarRight();
    // @formatter:on

    private class OverFlyingHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_COMPUTE_FLYING:
                    // 先停止正在进行的计算
                    mHandler.removeCallbacksAndMessages(null);
                    mCurrComputeTimes = -1; //这里没有break,写作-1方便计数
                case MSG_CONTINUE_COMPUTE_FLYING:
                    mCurrComputeTimes++;

                    // 顶部发生过度滚动
                    if (mDeltaY > 0 && isAtTop()) {
                        onTopOverFling((int) Math.abs(mVelocityY / 100f), DURATION_OVERFLYING);
                        mCurrComputeTimes = MAX_COMPUTE_TIMES;

                        // 底部发生过度滚动
                    } else if (mDeltaY < 0 && isAtBottom()) {
                        onBottomOverFling((int) Math.abs(mVelocityY / 100f), DURATION_OVERFLYING);
                        mCurrComputeTimes = MAX_COMPUTE_TIMES;

                        // 左部发生过度滚动
                    } else if (mDeltaX > 0 && isAtFarLeft()) {
                        onLeftOverFling((int) Math.abs(mVelocityX / 100f), DURATION_OVERFLYING);
                        mCurrComputeTimes = MAX_COMPUTE_TIMES;

                        // 右部发生过度滚动
                    } else if (mDeltaX <= 0 && isAtFarRight()) {
                        onRightOverFling((int) Math.abs(mVelocityX / 100f), DURATION_OVERFLYING);
                        mCurrComputeTimes = MAX_COMPUTE_TIMES;
                    }

                    // 计算未超时，继续发送消息并循环计算
                    if (mCurrComputeTimes < MAX_COMPUTE_TIMES)
                        sendEmptyMessageDelayed(MSG_CONTINUE_COMPUTE_FLYING, 10);
                    break;
                case MSG_STOP_COMPUTE_OVERFLYING:
                    mHandler.removeCallbacksAndMessages(null);
                    break;
            }
        }
    }
}
