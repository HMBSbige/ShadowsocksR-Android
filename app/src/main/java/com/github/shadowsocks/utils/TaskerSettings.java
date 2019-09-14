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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.github.shadowsocks.R;
import com.github.shadowsocks.database.Profile;

import static com.github.shadowsocks.ShadowsocksApplication.app;

public class TaskerSettings {

    private static final String KEY_SWITCH_ON = "switch_on";
    private static final String KEY_PROFILE_ID = "profile_id";

    public boolean switchOn;
    public int profileId;

    public TaskerSettings(Bundle bundle) {
        switchOn = bundle.getBoolean(KEY_SWITCH_ON, true);
        profileId = bundle.getInt(KEY_PROFILE_ID, -1);
    }

    public static TaskerSettings fromIntent(Intent intent) {
        Bundle bundle;
        if (intent.hasExtra(com.twofortyfouram.locale.api.Intent.EXTRA_BUNDLE)) {
            bundle = intent.getBundleExtra(com.twofortyfouram.locale.api.Intent.EXTRA_BUNDLE);
        } else {
            bundle = Bundle.EMPTY;
        }
        return new TaskerSettings(bundle);
    }

    public Intent toIntent(Context context) {
        Bundle bundle = new Bundle();
        if (!switchOn) {
            bundle.putBoolean(KEY_SWITCH_ON, false);
        }

        if (profileId >= 0) {
            bundle.putInt(KEY_PROFILE_ID, profileId);
        }
        String value;
        Profile p = app.profileManager.getProfile(profileId);
        if (p != null) {
            int strId = switchOn ? R.string.start_service : R.string.stop_service;
            value = context.getString(strId, p.name);
        } else {
            int strId = switchOn ? R.string.start_service_default : R.string.stop;
            value = context.getString(strId);
        }
        Intent intent = new Intent();
        intent.putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_BUNDLE, bundle);
        intent.putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_STRING_BLURB, value);
        return intent;
    }
}
