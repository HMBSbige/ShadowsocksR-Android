package com.github.shadowsocks;
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

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.text.TextUtils;

import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.ToastUtils;
import com.github.shadowsocks.utils.TrafficMonitor;
import com.github.shadowsocks.utils.TrafficMonitorThread;
import com.github.shadowsocks.utils.Utils;
import com.github.shadowsocks.utils.VayLog;
import com.google.android.gms.tagmanager.Container;
import com.google.android.gms.tagmanager.ContainerHolder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import okhttp3.Dns;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@SuppressLint("Registered")
public abstract class BaseService extends Service {

    public static final String protectPath = ShadowsocksApplication.app.getApplicationInfo().dataDir + "/protect_path";
    private static final String TAG = BaseService.class.getSimpleName();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private final RemoteCallbackList<IShadowsocksServiceCallback> callbacks;
    protected Profile profile;
    private int state = Constants.State.STOPPED;
    private Timer timer;
    private TrafficMonitorThread trafficMonitorThread;
    private int callbacksCount;
    private boolean closeReceiverRegistered;
    private BroadcastReceiver closeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ToastUtils.INSTANCE.showShort(R.string.stopping);
            stopRunner(true);
        }
    };
    public IShadowsocksService.Stub binder = new IShadowsocksService.Stub() {
        @Override
        public int getState() {
            return state;
        }

        @Override
        public String getProfileName() {
            if (profile == null) {
                return null;
            } else {
                return profile.getName();
            }
        }

        @Override
        public void unregisterCallback(IShadowsocksServiceCallback cb) {
            if (cb != null && callbacks.unregister(cb)) {
                callbacksCount -= 1;
                if (callbacksCount == 0 && timer != null) {
                    timer.cancel();
                    timer = null;
                }
            }
        }

        @Override
        public void registerCallback(IShadowsocksServiceCallback cb) {
            if (cb != null && callbacks.register(cb)) {
                callbacksCount += 1;
                if (callbacksCount != 0 && timer == null) {
                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            if (TrafficMonitor.updateRate()) {
                                updateTrafficRate();
                            }
                        }
                    };
                    timer = new Timer(true);
                    timer.schedule(task, 1000, 1000);
                }
                TrafficMonitor.updateRate();
                try {
                    cb.trafficUpdated(TrafficMonitor.txRate, TrafficMonitor.rxRate, TrafficMonitor.txTotal, TrafficMonitor.rxTotal);
                } catch (RemoteException e) {
                    VayLog.INSTANCE.e(TAG, "registerCallback", e);
                    ShadowsocksApplication.app.track(e);
                }
            }
        }

        @Override
        public synchronized void use(int profileId) {
            if (profileId < 0) {
                stopRunner(true);
            } else {
                Profile profile = ShadowsocksApplication.app.profileManager.getProfile(profileId);
                if (profile == null) {
                    stopRunner(true);
                } else {
                    switch (state) {
                        case Constants.State.STOPPED:
                            if (checkProfile(profile)) {
                                startRunner(profile);
                            }
                            break;
                        case Constants.State.CONNECTED:
                            if (profileId != BaseService.this.profile.getId() && checkProfile(profile)) {
                                stopRunner(false);
                                startRunner(profile);
                            }
                            break;
                        default:
                            VayLog.INSTANCE.w(TAG, "Illegal state when invoking use: " + state);
                            break;
                    }
                }
            }
        }

        @Override
        public void useSync(int profileId) {
            use(profileId);
        }
    };

    public BaseService() {
        callbacks = new RemoteCallbackList<>();
    }

    private boolean checkProfile(Profile profile) {
        if (TextUtils.isEmpty(profile.getHost()) || TextUtils.isEmpty(profile.getPassword())) {
            stopRunner(true, getString(R.string.proxy_empty));
            return false;
        } else {
            return true;
        }
    }

    public void connect() throws NameNotResolvedException, KcpcliParseException, NullConnectionException {
        if ("198.199.101.152".equals(profile.getHost())) {
            ContainerHolder holder = ShadowsocksApplication.app.containerHolder;
            Container container = holder.getContainer();
            String url = container.getString("proxy_url");
            String sig = Utils.INSTANCE.getSignature(this);

            OkHttpClient client = new OkHttpClient.Builder()
                    .dns(new Dns() {
                        @Override
                        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                            String ip = Utils.INSTANCE.resolve(hostname, false);
                            if (ip != null) {
                                List<InetAddress> list = new ArrayList<>();
                                list.add(InetAddress.getByName(ip));
                                return list;
                            } else {
                                return Dns.SYSTEM.lookup(hostname);
                            }
                        }
                    })
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            FormBody requestBody = new FormBody.Builder()
                    .add("sig", sig)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            try {
                Response resposne = client.newCall(request).execute();
                String list = resposne.body().string();

                List<String> proxies = Arrays.asList(list.split("|"));
                Collections.shuffle(proxies);
                String[] proxy = proxies.get(0).split(":");
                profile.setHost(proxy[0].trim());
                profile.setRemotePort(Integer.parseInt(proxy[1].trim()));
                profile.setPassword(proxy[2].trim());
                profile.setMethod(proxy[3].trim());
            } catch (Exception e) {
                VayLog.INSTANCE.e(TAG, "connect", e);
                ShadowsocksApplication.app.track(e);
                stopRunner(true, e.getMessage());
            }
        }
    }

    public void startRunner(Profile profile) {
        this.profile = profile;

        startService(new Intent(this, getClass()));
        TrafficMonitor.reset();
        trafficMonitorThread = new TrafficMonitorThread(getApplicationContext());
        trafficMonitorThread.start();

        if (!closeReceiverRegistered) {
            // register close receiver
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SHUTDOWN);
            filter.addAction(Constants.Action.CLOSE);
            registerReceiver(closeReceiver, filter);
            closeReceiverRegistered = true;
        }

        ShadowsocksApplication.app.track(TAG, "start");

        changeState(Constants.State.CONNECTING);

        if (profile.isMethodUnsafe()) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    ToastUtils.INSTANCE.showLong(R.string.method_unsafe);
                }
            });
        }

        // connect
        try {
            connect();
        } catch (NameNotResolvedException e) {
            stopRunner(true, getString(R.string.invalid_server));
        } catch (KcpcliParseException e) {
            stopRunner(true, getString(R.string.service_failed) + ": " + e.getCause().getMessage());
        } catch (NullConnectionException e) {
            stopRunner(true, getString(R.string.reboot_required));
        } catch (Throwable exc) {
            stopRunner(true, getString(R.string.service_failed) + ": " + exc.getMessage());
            exc.printStackTrace();
            ShadowsocksApplication.app.track(exc);
        }
    }

    public void stopRunner(boolean stopService) {
        stopRunner(stopService, null);
    }

    public void stopRunner(boolean stopService, String msg) {
        // clean up recevier
        if (closeReceiverRegistered) {
            unregisterReceiver(closeReceiver);
            closeReceiverRegistered = false;
        }

        // Make sure update total traffic when stopping the runner
        updateTrafficTotal(TrafficMonitor.txTotal, TrafficMonitor.rxTotal);

        TrafficMonitor.reset();
        if (trafficMonitorThread != null) {
            trafficMonitorThread.stopThread();
            trafficMonitorThread = null;
        }

        // change the state
        changeState(Constants.State.STOPPED, msg);

        // stop the service if nothing has bound to it
        if (stopService) {
            stopSelf();
        }

        // init profile
        profile = null;
    }

    private void updateTrafficTotal(long tx, long rx) {
        // avoid race conditions without locking
        Profile profile = this.profile;
        if (profile != null) {
            Profile p = ShadowsocksApplication.app.profileManager.getProfile(profile.getId());
            if (p != null) {
                // default profile may have host, etc. modified
                p.setTx(p.getTx() + tx);
                p.setRx(p.getRx() + rx);
                ShadowsocksApplication.app.profileManager.updateProfile(p);
            }
        }
    }

    public int getState() {
        return state;
    }

    private void updateTrafficRate() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (callbacksCount > 0) {
                    long txRate = TrafficMonitor.txRate;
                    long rxRate = TrafficMonitor.rxRate;
                    long txTotal = TrafficMonitor.txTotal;
                    long rxTotal = TrafficMonitor.rxTotal;
                    int n = callbacks.beginBroadcast();
                    for (int i = 0; i < n; i++) {
                        try {
                            callbacks.getBroadcastItem(i).trafficUpdated(txRate, rxRate, txTotal, rxTotal);
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    callbacks.finishBroadcast();
                }
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ShadowsocksApplication.app.refreshContainerHolder();
        ShadowsocksApplication.app.updateAssets();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Service of shadowsocks should always be started explicitly
        return Service.START_NOT_STICKY;
    }

    protected void changeState(final int s) {
        changeState(s, null);
    }

    protected void changeState(final int s, final String msg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != s || msg != null) {
                    if (callbacksCount > 0) {
                        int n = callbacks.beginBroadcast();
                        for (int i = 0; i < n; i++) {
                            try {
                                callbacks.getBroadcastItem(i).stateChanged(s, binder.getProfileName(), msg);
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                        callbacks.finishBroadcast();
                    }
                    state = s;
                }
            }
        });
    }

    public String getBlackList() {
        String defaultList = getString(R.string.black_list);
        try {
            Container container = ShadowsocksApplication.app.containerHolder.getContainer();
            String update = container.getString("black_list_lite");

            String list;
            if (update == null || update.isEmpty()) {
                list = defaultList;
            } else {
                list = update;
            }
            return "exclude = " + list + ";";
        } catch (Exception e) {
            return "exclude = " + defaultList + ";";
        }
    }

    public class NameNotResolvedException extends IOException {
    }

    public class KcpcliParseException extends Exception {
        public KcpcliParseException(Throwable cause) {
            super(cause);
        }
    }

    public class NullConnectionException extends NullPointerException {
    }
}
