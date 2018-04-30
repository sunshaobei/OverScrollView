package com.liuzhenlin.overscrollview.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.widget.Toast;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created on 2017/12/25. <br/>
 * Copyright (c) 2017 刘振林.All rights reserved.
 *
 * @author 刘振林
 */
public class ToastUtil {
    private static Toast sToast;

    @SuppressLint("ShowToast")
    public static void showToast(@NonNull Context context, String text, @ToastDuration int duration) {
        if (sToast == null) {
            sToast = Toast.makeText(context.getApplicationContext(), text, duration);
        } else {
            sToast.setText(text);
            sToast.setDuration(duration);
        }
        sToast.show();
    }

    public static void showToast(@NonNull Context context, @StringRes int resId, @ToastDuration int duration) {
        showToast(context, context.getString(resId), duration);
    }

    @IntDef({Toast.LENGTH_SHORT, Toast.LENGTH_LONG})
    @Retention(RetentionPolicy.SOURCE)
    private @interface ToastDuration {
    }
}
