# OverscrollView

## SwipeMenuRecyclerView — Support for Horizontally Sliding ItemView and the Over-scroll of List
### Horizontal Scrolling of ItemView
It supports all the vertical layouts of RecyclerView and the common problem existing in other alike libraries <br>
that the horizontal scrolling of items clashes with the vertical scrolling of the list has been well solved. <br>
Right sliding item brings a rebound effect.

![preview one](https://raw.githubusercontent.com/ApksHolder/OverscrollView/master/preview1.gif)

**Usages:**
1. The layout file of itemView needs just to follow the below.
```xml
<?xml version="1.0" encoding="utf-8"?>
<com.liuzhenlin.overscroll.SmoothScrollableLinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">

    <RelativeLayout
        android:layout_width="match_parent"
        android:layout_height="65dp"
        android:background="@drawable/default_selector_recycler_item"
        android:clickable="true">
        <!-- child views -->

        <ImageView
            android:id="@+id/image_ssll_rl"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_centerVertical="true"
            android:src="@mipmap/ic_launcher" />

        <TextView
            android:id="@+id/text_ssll_rl"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_centerVertical="true"
            android:layout_margin="5dp"
            android:layout_toRightOf="@id/image_ssll_rl"
            android:autoLink="web"
            android:linksClickable="true"
            android:text="文本"
            android:textSize="16sp" />
    </RelativeLayout>

    <LinearLayout
        android:layout_width="150dp"
        android:layout_height="match_parent"
        android:orientation="horizontal">

        <Button
            android:id="@+id/button_top"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@color/orange"
            android:gravity="center"
            android:text="置顶"
            android:textColor="@android:color/white"
            android:textSize="16sp" />

        <Button
            android:id="@+id/button_delete"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="@color/red"
            android:gravity="center"
            android:text="删除"
            android:textColor="@android:color/white"
            android:textSize="16sp" />
        <!-- and can place more buttons here... -->

    </LinearLayout>
</com.liuzhenlin.overscroll.SmoothScrollableLinearLayout>
```
Note that SmoothScrollableLinearLayout is used to realize the smooth scrolling effect of the itemView. <br>
Below is its two chief methods.
```Java
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
```
2. You can disable this functionality in your xml layout files or class files (By default, it's enabled in vertical layouts).
```xml  
app:item_scrolling_enabled="false"
```
```Java
mSwipeMenuRecyclerView.setItemScrollingEnabled(false);
```

### Over-scroll of the List
The over-scroll primely supports the vertical layouts of RecyclerView, but to horizontal layouts, there's still a problem on the far right scrolling, to which I have no solution. If you have one, please come up with your idea directly.

![preview two](https://raw.githubusercontent.com/ApksHolder/OverscrollView/master/preview2.gif)

**You can disable this functionality in your class files or xml layout files to use the default fluorescence effect.**
```Java
mSwipeMenuRecyclerView.setOverscrollEnabled(false);  
```
```xml
app:overscroll_enabled="false"
```

## OverScrollView
In order to let other layouts and views (such as LinearLayout, RelativeLayout, ImageView, TextView, etc.) <br>
achieve over-scroll effect, so there is an OverScrollView, the effect is similar to the above.

![preview three](https://raw.githubusercontent.com/ApksHolder/OverscrollView/master/preview3.gif)

**Usages:** 
Just according to the usages of NestedScrollView.

## HorizontalOverScrollView
To satisfy the need of over-scroll in the horizontal direction of general widgets.

![preview four](https://raw.githubusercontent.com/ApksHolder/OverscrollView/master/preview4.gif)

**Usages:** 
Similar to HorizontalScrollView

**`Note that OverScrollView and HorizontalOverScrollView can also disable over-scroll functionality (the same as SwipeMenuRecyclerView). OverScrollView can also be used with HorizontalOverScrollView to achieve four directions over-scroll and rebound.`**

## OverScrollBase Interface
For views with scrollbars like ListView and GridView, OverScrollView and HorizontalOverScrollView may not be suitable. <br> 
In this case, you need to create a subclass inherited from the corresponding widget and implementing the methods of this interface, and then do some logic processing on the touch events of the derived widget.
```Java
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
```

## Download
Download via jitpack:

To get a Git project into your build:

Step 1. Add the JitPack repository in your root build.gradle at the end of repositories:
```gradle
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
Step 2. Add the dependency
```gradle
	dependencies {
	        compile 'com.github.freeze-frame:OverscrollView:v1.1'
	}
```

## Pull Requests
I will gladly accept pull requests for bug fixes and feature enhancements but please do them in the developers branch.

## License
Copyright 2017-2018 Liu Zhenlin

Licensed under the Apache License, Version 2.0 (the "License"); <br>
you may not use this file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
