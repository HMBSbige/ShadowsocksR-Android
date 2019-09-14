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
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;

import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.ToastUtils;
import com.github.shadowsocks.utils.Utils;

import androidx.annotation.Nullable;

/**
 * @author Mygod
 */
public class QuickToggleShortcut extends Activity {

    private ServiceBoundContext mServiceBoundContext;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        mServiceBoundContext = new ServiceBoundContext(newBase) {
            @Override
            protected void onServiceConnected() {
                try {
                    int state = bgService.getState();
                    switch (state) {
                        case Constants.State.STOPPED:
                            ToastUtils.showShort(R.string.loading);
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
                finish();
            }
        };
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String action = getIntent().getAction();

        if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            setResult(Activity.RESULT_OK, new Intent()
                    .putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(this, QuickToggleShortcut.class))
                    .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.quick_toggle))
                    .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                            Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher)));
            finish();
        } else {
            mServiceBoundContext.attachService();
            if (Build.VERSION.SDK_INT >= 25) {
                ShortcutManager service = getSystemService(ShortcutManager.class);
                if (service != null) {
                    service.reportShortcutUsed("toggle");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        mServiceBoundContext.detachService();
        super.onDestroy();
    }
}
