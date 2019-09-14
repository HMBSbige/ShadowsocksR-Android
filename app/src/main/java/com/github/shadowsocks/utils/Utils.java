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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import com.github.shadowsocks.ShadowsocksRunnerService;

import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

import static com.github.shadowsocks.ShadowsocksApplication.app;

public class Utils {

    private static final String TAG = "Shadowsocks";

    /**
     * merge list
     *
     * @param lists list array
     * @param <T>   type class
     * @return merge failed return new ArrayList<>()
     */
    @SafeVarargs
    public static <T> List<T> mergeList(List<T>... lists) {
        List<T> result = new ArrayList<>();
        if (lists == null || lists.length == 0) {
            return result;
        }
        for (List<T> list : lists) {
            if (list == null || list.isEmpty()) {
                continue;
            }
            result.addAll(list);
        }
        return result;
    }

    /**
     * use string divider list value
     *
     * @param list    list
     * @param divider divider string
     * @return list is empty, return null.
     */
    public static String makeString(List<String> list, String divider) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            String item = list.get(i);
            if (i > 0) {
                sb.append(divider);
            }
            sb.append(item);
        }
        return sb.toString();
    }

    /**
     * use string divider list value
     *
     * @param list    list
     * @param divider divider string
     * @return list is empty, return null.
     */
    public static String makeString(HashSet<String> list, String divider) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        Iterator<String> iterator = list.iterator();
        for (int i = 0; iterator.hasNext(); i++) {
            String item = iterator.next();
            if (i > 0) {
                sb.append(divider);
            }
            sb.append(item);
        }
        return sb.toString();
    }

    public static List<String> getLinesByFile(File file) {
        List<String> list = new ArrayList<>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = null;
            while ((line = reader.readLine()) != null) {
                list.add(line);
            }
        } catch (Exception e) {
            // Ignore
        }
        return list;
    }

    public static boolean isLollipopOrAbove() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static String getSignature(Context context) {
        try {
            PackageInfo info = context
                    .getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            MessageDigest mdg = MessageDigest.getInstance("SHA-1");
            mdg.update(info.signatures[0].toByteArray());
            return new String(Base64.encode(mdg.digest(), 0));
        } catch (Exception e) {
            VayLog.e(TAG, "getSignature", e);
            app.track(e);
        }
        return null;
    }

    public static int dpToPx(Context context, int dp) {
        return Math.round(dp * (context.getResources().getDisplayMetrics().xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    /**
     * round or floor depending on whether you are using offsets(floor) or
     * widths(round)
     * <p>
     * Based on: http://stackoverflow.com/a/21026866/2245107
     */
    public static Toast positionToast(Toast toast, View view, Window window, int offsetX, int offsetY) {
        Rect rect = new Rect();
        window.getDecorView().getWindowVisibleDisplayFrame(rect);
        int[] viewLocation = new int[2];
        view.getLocationInWindow(viewLocation);
        DisplayMetrics metrics = new DisplayMetrics();
        window.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        View toastView = toast.getView();
        toastView.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.UNSPECIFIED));
        toast.setGravity(Gravity.LEFT | Gravity.TOP,
                viewLocation[0] - rect.left + (view.getWidth() - toast.getView().getMeasuredWidth()) / 2 + offsetX,
                viewLocation[1] - rect.top + view.getHeight() + offsetY);
        return toast;
    }

    public static void crossFade(Context context, final View from, View to) {
        int shortAnimTime = context.getResources().getInteger(android.R.integer.config_shortAnimTime);
        to.setAlpha(0);
        to.setVisibility(View.VISIBLE);
        to.animate().alpha(1).setDuration(shortAnimTime);
        from.animate().alpha(0).setDuration(shortAnimTime).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                from.setVisibility(View.GONE);
            }
        });
    }

    public static String readAllLines(File f) {
        Scanner scanner = null;
        try {
            scanner = new Scanner(f);
            scanner.useDelimiter("\\Z");
            return scanner.next();
        } catch (Exception e) {
            VayLog.e(TAG, "readAllLines", e);
            app.track(e);
            return null;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    /**
     * print to file
     *
     * @param file    file
     * @param content string content
     */
    public static void printToFile(File file, String content) {
        printToFile(file, content, false);
    }

    /**
     * println to file
     *
     * @param file    file
     * @param content string content
     */
    public static void printToFile(File file, String content, boolean isPrintln) {
        PrintWriter p = null;
        try {
            p = new PrintWriter(file);
            if (isPrintln) {
                p.println(content);
            } else {
                p.print(content);
            }
            p.flush();
        } catch (Exception e) {
            // Ignored
        } finally {
            if (p != null) {
                p.close();
            }
        }
    }

    /**
     * Crack a command line.
     * Based on: https://github.com/apache/ant/blob/588ce1f/src/main/org/apache/tools/ant/types/Commandline.java#L471
     *
     * @param toProcess the command line to process.
     * @return the command line broken into strings.
     * An empty or null toProcess parameter results in a zero sized ArrayBuffer.
     */
    public static ArrayList<String> translateCommandline(String toProcess) throws Exception {
        if (toProcess == null || toProcess.length() == 0) {
            return new ArrayList<>();
        }
        StringTokenizer tok = new StringTokenizer(toProcess, "\"' ", true);
        ArrayList<String> result = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        char quote = ' ';
        String last = " ";
        while (tok.hasMoreTokens()) {
            String nextTok = tok.nextToken();
            switch (quote) {
                case '\'':
                    if ("'".equals(nextTok)) {
                        quote = ' ';
                    } else {
                        current.append(nextTok);
                    }
                    break;
                case '"':
                    if ("\"".equals(nextTok)) {
                        quote = ' ';
                    } else {
                        current.append(nextTok);
                    }
                    break;
                default:
                    if ("'".equals(nextTok)) {
                        quote = '\'';
                    } else if ("\"".equals(nextTok)) {
                        quote = '"';
                    } else if (" ".equals(nextTok)) {
                        if (!" ".equals(last)) {
                            result.add(current.toString());
                            current.setLength(0);
                        }
                    } else {
                        current.append(nextTok);
                    }
                    break;
            }
            last = nextTok;
        }
        if (!TextUtils.isEmpty(current)) {
            result.add(current.toString());
        }

        if (quote == '\'' || quote == '"') {
            throw new Exception("Unbalanced quotes in " + toProcess);
        }

        return result;
    }

    public static String resolve(String host, int addrType) {
        try {
            Lookup lookup = new Lookup(host, addrType);
            SimpleResolver resolver = new SimpleResolver("114.114.114.114");
            resolver.setTimeout(5);
            lookup.setResolver(resolver);
            Record[] result = lookup.run();
            if (result == null || result.length == 0) {
                return null;
            }

            List<Record> records = new ArrayList<>(Arrays.asList(result));
            Collections.shuffle(records);
            for (Record r : records) {
                switch (addrType) {
                    case Type.A:
                        return ((ARecord) r).getAddress().getHostAddress();
                    case Type.AAAA:
                        return ((AAAARecord) r).getAddress().getHostAddress();
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            VayLog.e(TAG, "resolve", e);
            app.track(e);
        }
        return null;
    }

    public static String resolve(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.getHostAddress();
        } catch (Exception e) {
            VayLog.e(TAG, "resolve", e);
            app.track(e);
        }
        return null;
    }

    public static String resolve(String host, boolean enableIPv6) {
        String address = null;
        if (enableIPv6 && isIPv6Support()) {
            address = resolve(host, Type.AAAA);
            if (!TextUtils.isEmpty(address)) {
                return address;
            }
        }

        address = resolve(host, Type.A);
        if (!TextUtils.isEmpty(address)) {
            return address;
        }

        address = resolve(host);
        if (!TextUtils.isEmpty(address)) {
            return address;
        }

        return null;
    }

    private static Method isNumericMethod() throws NoSuchMethodException {
        return InetAddress.class.getMethod("isNumeric", String.class);
    }

    public static boolean isNumeric(String address) {
        try {
            return (boolean) isNumericMethod().invoke(null, address);
        } catch (Exception e) {
            VayLog.e(TAG, "isNumeric", e);
            app.track(e);
        }
        return false;
    }

    /**
     * If there exists a valid IPv6 interface
     */
    public static boolean isIPv6Support() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface intf = networkInterfaces.nextElement();

                Enumeration<InetAddress> addresses = intf.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet6Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        VayLog.d(TAG, "IPv6 address detected");
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            VayLog.e(TAG, "Failed to get interfaces' addresses.", e);
            app.track(e);
        }
        return false;
    }

    public static void startSsService(Context context) {
        Intent intent = new Intent(context, ShadowsocksRunnerService.class);
        context.startService(intent);
    }

    public static void stopSsService(Context context) {
        Intent intent = new Intent(Constants.Action.CLOSE);
        context.sendBroadcast(intent);
    }
}
