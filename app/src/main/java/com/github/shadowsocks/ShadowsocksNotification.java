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

import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.os.RemoteException;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.TrafficMonitor;
import com.github.shadowsocks.utils.Utils;

import java.util.List;
import java.util.Locale;

import static com.github.shadowsocks.ShadowsocksApplication.app;

/**
 * @author Mygod
 */
public class ShadowsocksNotification {

    private Service service;
    private String profileName;
    private boolean visible;
    private PowerManager pm;

    private KeyguardManager keyGuard;
    private NotificationManager nm;

    private IShadowsocksServiceCallback.Stub callback = new IShadowsocksServiceCallback.Stub() {

        @Override
        public void stateChanged(int state, String profileName, String msg) throws RemoteException {
            // Ignore
        }

        @Override
        public void trafficUpdated(long txRate, long rxRate, long txTotal, long rxTotal) throws RemoteException {
            String txr = TrafficMonitor.formatTraffic(txRate);
            String rxr = TrafficMonitor.formatTraffic(rxRate);
            builder.setContentText(String.format(Locale.ENGLISH, service.getString(R.string.traffic_summary), txr, rxr));

            style.bigText(String.format(Locale.ENGLISH,
                    service.getString(R.string.stat_summary),
                    txr,
                    rxr,
                    TrafficMonitor.formatTraffic(txTotal),
                    TrafficMonitor.formatTraffic(rxTotal)));
            show();
        }
    };

    private boolean callbackRegistered;

    private NotificationCompat.Builder builder;

    private NotificationCompat.BigTextStyle style;
    private boolean isVisible = true;

    /**
     * loca receiver
     */
    private BroadcastReceiver lockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update(intent.getAction());
        }
    };


    public ShadowsocksNotification(Service service, String profileName) {
        this(service, profileName, false);
    }

    public ShadowsocksNotification(Service service, String profileName, boolean visible) {
        this.service = service;
        this.profileName = profileName;
        this.visible = visible;

        keyGuard = (KeyguardManager) service.getSystemService(Context.KEYGUARD_SERVICE);
        nm = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);

        // init notification builder
        initNotificationBuilder();
        style = new NotificationCompat.BigTextStyle(builder);

        // init with update action
        initWithUpdateAction();

        // register lock receiver
        registerLockReceiver(service, visible);
    }

    private void update(String action) {
        update(action, false);
    }

    private void update(String action, boolean forceShow) {
        if (forceShow || getServiceState() == Constants.State.CONNECTED) {
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                setVisible(visible && !Utils.isLollipopOrAbove(), forceShow);
                // unregister callback to save battery
                unregisterCallback();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                setVisible(visible && Utils.isLollipopOrAbove() && !keyGuard.inKeyguardRestrictedInputMode(), forceShow);
                try {
                    registerServiceCallback(callback);
                } catch (RemoteException e) {
                    // Ignored
                }
                callbackRegistered = true;
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                setVisible(true, forceShow);
            }
        }
    }

    public void destroy() {
        if (lockReceiver != null) {
            service.unregisterReceiver(lockReceiver);
            lockReceiver = null;
        }
        unregisterCallback();
        service.stopForeground(true);
        nm.cancel(1);
    }

    public void setVisible(boolean visible) {
        setVisible(visible, false);
    }

    public void setVisible(boolean visible, boolean forceShow) {
        if (isVisible != visible) {
            isVisible = visible;
            int priority = visible ? NotificationCompat.PRIORITY_LOW : NotificationCompat.PRIORITY_MIN;
            builder.setPriority(priority);
            show();
        } else if (forceShow) {
            show();
        }
    }

    public void show() {
        service.startForeground(1, builder.build());
    }

    private void unregisterCallback() {
        if (callbackRegistered) {
            try {
                unregisterServiceCallback(callback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            callbackRegistered = false;
        }
    }

    private void registerLockReceiver(Service service, boolean visible) {
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        if (visible && Utils.isLollipopOrAbove()) {
            screenFilter.addAction(Intent.ACTION_USER_PRESENT);
        }
        service.registerReceiver(lockReceiver, screenFilter);
    }

    private void initWithUpdateAction() {
        String action;
        if (Build.VERSION.SDK_INT < 20) {
            action = pm.isScreenOn() ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF;
        } else {
            action = pm.isInteractive() ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF;
        }
        // upate
        update(action, true);
    }

    private void initNotificationBuilder() {
        String channelId = "net_speed";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "NetSpeed", NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(channel);
        }
        builder = new NotificationCompat.Builder(service, channelId)
                .setWhen(0)
                .setColor(ContextCompat.getColor(service, R.color.material_accent_500))
                .setTicker(service.getString(R.string.forward_success))
                .setContentTitle(profileName)
                .setContentIntent(PendingIntent.getActivity(service, 0, new Intent(service, Shadowsocks.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0))
                .setSmallIcon(R.drawable.ic_stat_shadowsocks);
        builder.addAction(R.drawable.ic_navigation_close,
                service.getString(R.string.stop),
                PendingIntent.getBroadcast(service, 0, new Intent(Constants.Action.CLOSE), 0));

        List<Profile> profiles = app.profileManager.getAllProfiles();
        if (profiles != null && !profiles.isEmpty()) {
            builder.addAction(R.drawable.ic_action_settings, service.getString(R.string.quick_switch),
                    PendingIntent.getActivity(service, 0, new Intent(Constants.Action.QUICK_SWITCH), 0));
        }
    }

    private int getServiceState() {
        int state = 0;
        if (service instanceof BaseVpnService) {
            state = ((BaseVpnService) service).getState();
        } else if (service instanceof BaseService) {
            state = ((BaseService) service).getState();
        }
        return state;
    }

    private void registerServiceCallback(IShadowsocksServiceCallback callback) throws RemoteException {
        IShadowsocksService.Stub binder = null;
        if (service instanceof BaseVpnService) {
            BaseVpnService vpnService = (BaseVpnService) service;
            binder = vpnService.binder;
        } else if (service instanceof BaseService) {
            BaseService baseService = (BaseService) service;
            binder = baseService.binder;
        }
        if (binder != null) {
            binder.registerCallback(callback);
        }
    }

    private void unregisterServiceCallback(IShadowsocksServiceCallback callback) throws RemoteException {
        IShadowsocksService.Stub binder = null;
        if (service instanceof BaseVpnService) {
            BaseVpnService vpnService = (BaseVpnService) service;
            binder = vpnService.binder;
        } else if (service instanceof BaseService) {
            BaseService baseService = (BaseService) service;
            binder = baseService.binder;
        }
        if (binder != null) {
            binder.unregisterCallback(callback);
        }
    }
}
