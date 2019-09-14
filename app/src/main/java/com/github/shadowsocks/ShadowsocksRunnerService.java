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

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;

import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.utils.VayLog;

import androidx.annotation.Nullable;

import static com.github.shadowsocks.ShadowsocksApplication.app;

public class ShadowsocksRunnerService extends ServiceBoundService {

    private static final String TAG = ShadowsocksRunnerService.class.getSimpleName();

    private Handler handler = new Handler();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        detachService();
        attachService(mCallback);
        return Service.START_STICKY;
    }

    @Override
    protected void onServiceConnected() {
        if (bgService != null) {
            if (app.isNatEnabled()) {
                startBackgroundService();
            } else if (VpnService.prepare(ShadowsocksRunnerService.this) == null) {
                startBackgroundService();
            } else {
                handler.postDelayed(mStopSelfRunnable, 10000);
            }
        }
    }

    @Override
    protected void onServiceDisconnected() {
        super.onServiceDisconnected();
    }

    private void startBackgroundService() {
        try {
            bgService.use(app.profileId());
        } catch (RemoteException e) {
            VayLog.e(TAG, "startBackgroundService", e);
            app.track(e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        detachService();
    }

    /**
     * callback
     */
    private IShadowsocksServiceCallback.Stub mCallback = new IShadowsocksServiceCallback.Stub() {
        @Override
        public void stateChanged(int state, String profileName, String msg) throws RemoteException {
        }

        @Override
        public void trafficUpdated(long txRate, long rxRate, long txTotal, long rxTotal) throws RemoteException {
        }
    };

    /**
     * stop self runnable
     */
    private Runnable mStopSelfRunnable = new Runnable() {
        @Override
        public void run() {
            stopSelf();
        }
    };
}
