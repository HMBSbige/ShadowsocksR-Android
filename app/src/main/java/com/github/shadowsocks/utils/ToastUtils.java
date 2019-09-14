package com.github.shadowsocks.utils;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by vay on 2018/03/07.
 */
public class ToastUtils {

    private static Toast mToast;
    private static Context sContext;

    public static void init(Context context) {
        sContext = context;
    }

    public static void showLong(String str){
        cancelToast();
        mToast = Toast.makeText(getContext(), str, Toast.LENGTH_LONG);
        mToast.show();
    }

    public static void showLong(int strId){
        cancelToast();
        mToast = Toast.makeText(getContext(), strId, Toast.LENGTH_LONG);
        mToast.show();
    }

    public static void showShort(String str){
        cancelToast();
        mToast = Toast.makeText(getContext(), str, Toast.LENGTH_SHORT);
        mToast.show();
    }

    public static void showShort(int strId){
        cancelToast();
        mToast = Toast.makeText(getContext(), strId, Toast.LENGTH_SHORT);
        mToast.show();
    }

    private static void cancelToast(){
        if(mToast != null){
            mToast.cancel();
        }
    }

    /**
     * get context
     *
     * @return
     */
    private static Context getContext() {
        return sContext;
    }
}