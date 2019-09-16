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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.RemoteException;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.Utils;

/**
 * @author Mygod
 */
@TargetApi(Build.VERSION_CODES.N)
public class ShadowsocksTileService extends TileService {

    private static final String TAG = ShadowsocksTileService.class.getSimpleName();
    private ServiceBoundContext mServiceBoundContext;

    private Icon iconIdle;
    private Icon iconBusy;
    private Icon iconConnected;

    private final IShadowsocksServiceCallback.Stub callback = new IShadowsocksServiceCallback.Stub() {
        @Override
        public void trafficUpdated(long txRate, long rxRate, long txTotal, long rxTotal) {
        }

        @Override
        public void stateChanged(int state, String profileName, String msg) throws RemoteException {
            Tile tile = getQsTile();
            if (tile != null) {
                switch (state) {
                    case Constants.State.STOPPED:
                        tile.setIcon(iconIdle);
                        tile.setLabel(getString(R.string.app_name));
                        tile.setState(Tile.STATE_INACTIVE);
                        break;
                    case Constants.State.CONNECTED:
                        tile.setIcon(iconConnected);
                        String label = profileName == null ? getString(R.string.app_name) : profileName;
                        tile.setLabel(label);
                        tile.setState(Tile.STATE_ACTIVE);
                        break;
                    default:
                        tile.setIcon(iconBusy);
                        tile.setLabel(getString(R.string.app_name));
                        tile.setState(Tile.STATE_UNAVAILABLE);
                        break;
                }
                tile.updateTile();
            }
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        iconIdle = Icon.createWithResource(this, R.drawable.ic_start_idle).setTint(0x80ffffff);
        iconBusy = Icon.createWithResource(this, R.drawable.ic_start_busy);
        iconConnected = Icon.createWithResource(this, R.drawable.ic_start_connected);

        mServiceBoundContext = new ServiceBoundContext(base) {
            @Override
            protected void onServiceConnected() {
                try {
                    callback.stateChanged(bgService.getState(), bgService.getProfileName(), null);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        mServiceBoundContext.attachService(callback);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        // just in case the user switches to NAT mode, also saves battery
        mServiceBoundContext.detachService();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(new Runnable() {
                @Override
                public void run() {
                    toggle();
                }
            });
        } else {
            toggle();
        }
    }

    private void toggle() {
        if (mServiceBoundContext.bgService != null) {
            try {
                int state = mServiceBoundContext.bgService.getState();
                switch (state) {
                    case Constants.State.STOPPED:
                        Utils.startSsService(this);
                        break;
                    case Constants.State.CONNECTED:
                        Utils.stopSsService(this);
                        break;
                    default:
                        // ignore
                        break;
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
