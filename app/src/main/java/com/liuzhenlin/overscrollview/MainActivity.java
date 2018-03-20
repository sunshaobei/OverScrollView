package com.liuzhenlin.overscrollview;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button_swipe_menu_recycler).setOnClickListener(this);
        findViewById(R.id.button_overscroll).setOnClickListener(this);
        findViewById(R.id.button_horizontal_overscroll).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent it = new Intent();
        switch (v.getId()) {
            case R.id.button_swipe_menu_recycler:
                it.setClass(this, SwipeMenuRecyclerActivity.class);
                break;
            case R.id.button_overscroll:
                it.setClassName(this, OverScrollActivity.class.getName());
                break;
            case R.id.button_horizontal_overscroll:
                it.setClassName(getPackageName(), HorizontalOverScrollActivity.class.getName());
                break;
        }
        startActivity(it);
    }
}
