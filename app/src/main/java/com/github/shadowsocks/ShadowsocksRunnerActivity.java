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

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;

import com.github.shadowsocks.utils.VayLog;

import androidx.annotation.Nullable;

import static com.github.shadowsocks.ShadowsocksApplication.app;

public class ShadowsocksRunnerActivity extends Activity {
    private static final String TAG = ShadowsocksRunnerActivity.class.getSimpleName();
    private static final int REQUEST_CONNECT = 1;

    private Handler handler = new Handler();

    private BroadcastReceiver receiver;

    private ServiceBoundContext mServiceBoundContext;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        mServiceBoundContext = new ServiceBoundContext(newBase) {
            @Override
            protected void onServiceConnected() {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (bgService != null) {
                            startBackgroundService();
                        }
                    }
                }, 1000);
            }
        };
    }

    private void startBackgroundService() {
        if (app.isNatEnabled()) {
            try {
                mServiceBoundContext.bgService.use(app.profileId());
                finish();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Intent intent = VpnService.prepare(ShadowsocksRunnerActivity.this);
            if (intent != null) {
                startActivityForResult(intent, REQUEST_CONNECT);
            } else {
                onActivityResult(REQUEST_CONNECT, Activity.RESULT_OK, null);
            }
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = km.inKeyguardRestrictedInputMode();
        if (locked) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                        mServiceBoundContext.attachService();
                    }
                }
            };
            registerReceiver(receiver, filter);
        } else {
            mServiceBoundContext.attachService();
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mServiceBoundContext.detachService();
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (mServiceBoundContext.bgService != null) {
                try {
                    mServiceBoundContext.bgService.use(app.profileId());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } else {
            VayLog.e(TAG, "Failed to start VpnService");
        }
        finish();
    }
}
