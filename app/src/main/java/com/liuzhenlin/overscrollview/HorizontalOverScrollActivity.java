package com.liuzhenlin.overscrollview;

import android.os.Bundle;
import android.support.annotation.Nullable;

import me.slideback.activity.SlideBackActivity;

/**
 * Created on 2017/12/25. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
public class HorizontalOverScrollActivity extends SlideBackActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_horizontal_overscroll);
    }
}
