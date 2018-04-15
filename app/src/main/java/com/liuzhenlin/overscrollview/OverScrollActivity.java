package com.liuzhenlin.overscrollview;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.Nullable;

import me.slideback.activity.SlideBackActivity;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static me.slideback.utils.SystemBarUtil.setTranslucentNavigation;
import static me.slideback.utils.SystemBarUtil.setTranslucentStatus;
import static me.slideback.utils.SystemBarUtil.setTransparentStatus;

/**
 * Created on 2017/12/25. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
public class OverScrollActivity extends SlideBackActivity {

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overscroll);
        if (SDK_INT >= KITKAT) {
            if (SDK_INT >= LOLLIPOP) {
                setTransparentStatus(this, true);
            } else {
                setTranslucentStatus(this, true);
            }
            setTranslucentNavigation(this, true);
        }
    }
}
