package com.github.shadowsocks.utils;

import com.github.shadowsocks.R;
import com.github.shadowsocks.ShadowsocksApplication;

import java.text.DecimalFormat;

public class TrafficMonitor {
    // Bytes per second
    public static long txRate;
    public static long rxRate;

    // Bytes for the current session
    public static long txTotal;
    public static long rxTotal;

    // Bytes for the last query
    public static long txLast;
    public static long rxLast;
    public static long timestampLast;
    public static boolean dirty = true;

    private static String[] units = {"KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB", "BB", "NB", "DB", "CB"};
    private static DecimalFormat numberFormat = new DecimalFormat("@@@");

    public static String formatTraffic(long size) {
        double n = size;
        int i = -1;
        while (n >= 1000) {
            n /= 1024;
            i = i + 1;
        }
        if (i < 0) {
            return size + " " + ShadowsocksApplication.app.getResources().getQuantityString(R.plurals.bytes, (int) size);
        } else {
            return numberFormat.format(n) + ' ' + units[i];
        }
    }

    public static boolean updateRate() {
        long now = System.currentTimeMillis();
        long delta = now - timestampLast;
        boolean updated = false;
        if (delta != 0) {
            if (dirty) {
                txRate = (txTotal - txLast) * 1000 / delta;
                rxRate = (rxTotal - rxLast) * 1000 / delta;
                txLast = txTotal;
                rxLast = rxTotal;
                dirty = false;
                updated = true;
            } else {
                if (txRate != 0) {
                    txRate = 0;
                    updated = true;
                }
                if (rxRate != 0) {
                    rxRate = 0;
                    updated = true;
                }
            }
            timestampLast = now;
        }
        return updated;
    }

    public static void update(Long tx, Long rx) {
        if (txTotal != tx) {
            txTotal = tx;
            dirty = true;
        }

        if (rxTotal != rx) {
            rxTotal = rx;
            dirty = true;
        }
    }

    public static void reset() {
        txRate = 0;
        rxRate = 0;
        txTotal = 0;
        rxTotal = 0;
        txLast = 0;
        rxLast = 0;
        dirty = true;
    }
}
