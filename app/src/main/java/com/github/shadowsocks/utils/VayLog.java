package com.github.shadowsocks.utils;
/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2014 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

import android.util.Log;

import com.github.shadowsocks.BuildConfig;

/**
 * Created by vay on 2018/03/05.
 */
public class VayLog {

    private final static String DEFAULT_TAG = "VayLog";

    private static boolean LOGGABLE = BuildConfig.DEBUG;

    public static void d(String str) {
        d(DEFAULT_TAG, str);
    }

    public static void d(String tag, String str) {
        if (LOGGABLE) {
            Log.d(tag, str + "");
        }
    }

    public static void w(String str) {
        w(DEFAULT_TAG, str);
    }

    public static void w(String tag, String str) {
        if (LOGGABLE) {
            Log.w(tag, str + "");
        }
    }

    public static void e(String str) {
        e(DEFAULT_TAG, str);
    }

    public static void e(String tag, String str) {
        e(tag, str, null);
    }

    public static void e(String tag, String msg, Throwable e) {
        if (LOGGABLE) {
            Log.e(tag, msg + "", e);
        }
    }

    public static void i(String str) {
        i(DEFAULT_TAG, str + "");
    }

    public static void i(String tag, String str) {
        if (LOGGABLE) {
            Log.i(tag, str + "");
        }
    }

    public static void v(String str) {
        v(DEFAULT_TAG, str + "");
    }

    public static void v(String tag, String str) {
        if (LOGGABLE) {
            Log.v(tag, str + "");
        }
    }
}
