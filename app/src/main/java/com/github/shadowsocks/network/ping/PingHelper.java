package com.github.shadowsocks.network.ping;
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

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;

import com.github.shadowsocks.GuardedProcess;
import com.github.shadowsocks.R;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.network.request.RequestCallback;
import com.github.shadowsocks.network.request.RequestHelper;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.github.shadowsocks.utils.Utils;
import com.github.shadowsocks.utils.VayLog;

import java.io.File;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import androidx.annotation.NonNull;
import okhttp3.Request;

/**
 * Created by vay on 2018/07/18
 */
public class PingHelper {

    private static final String TAG = PingHelper.class.getSimpleName();

    private static PingHelper sInstance;
    private final ScheduledThreadPoolExecutor mThreadPool;
    private GuardedProcess ssTestProcess;

    private Activity mTempActivity;

    /**
     * get instance
     */
    public static PingHelper instance() {
        if (sInstance == null) {
            synchronized (PingHelper.class) {
                if (sInstance == null) {
                    sInstance = new PingHelper();
                }
            }
        }
        return sInstance;
    }

    /**
     * private construction
     */
    private PingHelper() {
        // create thread pool
        mThreadPool = new ScheduledThreadPoolExecutor(10, new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("ping_helper-thread");
                return thread;
            }
        });
    }

    /**
     * ping profile
     *
     * @param aty      activity object
     * @param profile  profile object
     * @param callback ping callback object
     */
    public void ping(final Activity aty, final Profile profile, final PingCallback callback) {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                pingByProfile(aty, profile, callback);
            }
        });
    }

    /**
     * ping all profile
     *
     * @see #pingAll(Activity, List, int, PingCallback)
     */
    public void pingAll(Activity aty, List<Profile> profiles, PingCallback callback) {
        pingAll(aty, profiles, 0, callback);
    }

    /**
     * ping all profile
     *
     * @param profiles profile list
     * @param position list start index
     * @param callback ping callback
     */
    public void pingAll(final Activity aty, final List<Profile> profiles, final int position, final PingCallback callback) {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                pingAllByProfiles(aty, profiles, position, callback);
            }
        });
    }

    /**
     * release temp activity
     */
    public void releaseTempActivity() {
        if (mTempActivity != null) {
            mTempActivity = null;
        }
    }

    //===================================================================================================//
    //========================================= private method =========================================//
    //=================================================================================================//

    /**
     * pint all by profiles
     *
     * @param profiles profile list
     * @param position list start index
     * @param callback ping callback
     */
    private void pingAllByProfiles(final Activity aty, final List<Profile> profiles, final int position, final PingCallback callback) {
        if (profiles == null || profiles.isEmpty()) {
            callback.setResultMsg("test all failed, profile list is empty");
            callback.onFailed(null);
            callback.onFinished(null);
            return;
        }

        if (position < profiles.size()) {
            Profile profile = profiles.get(position);
            pingByProfile(aty, profile, new PingCallback() {
                @Override
                public void onSuccess(Profile profile, long elapsed) {
                    callback.setResultMsg(getResultMsg());
                    callback.onSuccess(profile, elapsed);
                }

                @Override
                public void onFailed(Profile profile) {
                    callback.setResultMsg(getResultMsg());
                    callback.onFailed(profile);
                }

                @Override
                public void onFinished(Profile profile) {
                    // test next profile
                    pingAll(aty, profiles, position + 1, callback);
                }
            });
        } else {
            // test finished
            callback.onFinished(null);
        }
    }

    /**
     * pint by profile
     *
     * @param profile  profile
     * @param callback ping callback
     */
    private void pingByProfile(Activity aty, final Profile profile, final PingCallback callback) {
        mTempActivity = aty;
        // Resolve the server address
        String host = profile.host;
        if (!Utils.isNumeric(host)) {
            String addr = Utils.resolve(host, true);
            if (!TextUtils.isEmpty(addr)) {
                host = addr;
            } else {
                String result = getString(R.string.connection_test_error, "can't resolve");
                callback.setResultMsg(result);
                callback.onFailed(profile);
                callback.onFinished(profile);
                return;
            }
        }

        String conf = String.format(Locale.ENGLISH,
                Constants.ConfigUtils.SHADOWSOCKS,
                host,
                profile.remotePort,
                profile.localPort + 2,
                Constants.ConfigUtils.EscapedJson(profile.password),
                profile.method,
                600,
                profile.protocol,
                profile.obfs,
                Constants.ConfigUtils.EscapedJson(profile.obfs_param),
                Constants.ConfigUtils.EscapedJson(profile.protocol_param));

        Utils.printToFile(new File(getApplicationInfo().dataDir + "/ss-local-test.conf"), conf, true);

        String[] cmd = {getApplicationInfo().dataDir + "/ss-local",
                "-t", "600",
                "-L", "www.google.com:80",
                "-c", getApplicationInfo().dataDir + "/ss-local-test.conf"};

        List<String> cmds = new ArrayList<>(Arrays.asList(cmd));

        if (TcpFastOpen.sendEnabled()) {
            cmds.add("--fast-open");
        }

        if (ssTestProcess != null) {
            ssTestProcess.destroy();
            ssTestProcess = null;
        }

        try {
            ssTestProcess = new GuardedProcess(cmds).start();
        } catch (InterruptedException e) {
            String result = getString(R.string.connection_test_error, "GuardedProcess start exception");
            callback.setResultMsg(result);
            callback.onFailed(profile);
            callback.onFinished(profile);
            return;
        }

        long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < 5 * 1000 && isPortAvailable(profile.localPort + 2)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignored
            }
        }
        //val proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", profile.localPort + 2))

        // Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640

        final Request request = new Request.Builder()
                .url("http://127.0.0.1:" + (profile.localPort + 2) + "/generate_204")
                .removeHeader("Host")
                .addHeader("Host", "www.google.com")
                .build();

        // flag start request time
        final long startRequestTime = System.currentTimeMillis();

        // request
        RequestHelper.instance().request(request, new RequestCallback() {

            @Override
            public boolean isRequestOk(int code) {
                return code == 204 || code == 200;
            }

            @Override
            public void onSuccess(int code, String response) {
                // update profile
                long elapsed = System.currentTimeMillis() - startRequestTime;
                String result = getString(R.string.connection_test_available, elapsed);
                callback.setResultMsg(result);
                callback.onSuccess(profile, elapsed);
            }

            @Override
            public void onFailed(int code, String msg) {
                String result;
                if (code != 404) {
                    result = getString(R.string.connection_test_error_status_code, code);
                } else {
                    result = getString(R.string.connection_test_error, msg);
                }
                callback.setResultMsg(result);
                callback.onFailed(profile);
            }

            @Override
            public void onFinished() {
                callback.onFinished(profile);
                //Snackbar.make(findViewById(android.R.id.content), result, Snackbar.LENGTH_LONG).show();
                if (ssTestProcess != null) {
                    ssTestProcess.destroy();
                    ssTestProcess = null;
                }
            }
        });
        // Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
    }

    private boolean isPortAvailable(int port) {
        // Assume no connection is possible.
        boolean result = true;

        try {
            new Socket("127.0.0.1", port).close();
            result = false;
        } catch (Exception e) {
            VayLog.e(TAG, "isPortAvailable", e);
        }

        return result;
    }

    private String getString(int resId, Object... formatArgs) {
        if (mTempActivity == null) {
            return "";
        }
        return mTempActivity.getString(resId, formatArgs);
    }

    private ApplicationInfo getApplicationInfo() {
        if (mTempActivity == null) {
            return null;
        }
        return mTempActivity.getApplicationInfo();
    }
}
