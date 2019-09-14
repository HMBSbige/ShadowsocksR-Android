package com.github.shadowsocks.job;
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

import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;
import com.github.shadowsocks.database.SSRSub;
import com.github.shadowsocks.network.ssrsub.SubUpdateCallback;
import com.github.shadowsocks.network.ssrsub.SubUpdateHelper;
import com.github.shadowsocks.utils.Constants;
import com.github.shadowsocks.utils.VayLog;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

import static com.github.shadowsocks.ShadowsocksApplication.app;

/**
 * @author Mygod
 */
public class SSRSubUpdateJob extends Job {

    public static final String TAG = SSRSubUpdateJob.class.getSimpleName();

    public static int schedule() {
        return new JobRequest.Builder(SSRSubUpdateJob.TAG)
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .setRequirementsEnforced(true)
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .setRequiresCharging(false)
                .setUpdateCurrent(true)
                .build().schedule();
    }

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        if (app.settings.getInt(Constants.Key.ssrsub_autoupdate, 0) == 1) {
            List<SSRSub> subs = app.ssrsubManager.getAllSSRSubs();
            SubUpdateHelper.instance().updateSub(subs, 0, new SubUpdateCallback() {
                @Override
                public void onSuccess() {
                    VayLog.d(TAG, "onRunJob() update sub success!");
                }

                @Override
                public void onFailed() {
                    VayLog.e(TAG, "onRunJob() update sub failed!");
                }
            });
            return Result.SUCCESS;
        } else {
            return Result.RESCHEDULE;
        }
    }
}
