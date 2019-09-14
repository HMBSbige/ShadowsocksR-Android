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
import com.github.shadowsocks.utils.IOUtils;
import com.github.shadowsocks.utils.VayLog;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

import static com.github.shadowsocks.ShadowsocksApplication.app;

/**
 * @author Mygod
 */
public class AclSyncJob extends Job {

    public static final String TAG = AclSyncJob.class.getSimpleName();

    private final String route;

    public AclSyncJob(String route) {
        this.route = route;
    }

    public static int schedule(String route) {
        return new JobRequest.Builder(AclSyncJob.TAG + ':' + route)
                .setExecutionWindow(1, TimeUnit.DAYS.toMillis(28))
                .setRequirementsEnforced(true)
                .setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .setUpdateCurrent(true)
                .build().schedule();
    }

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        String filename = route + ".acl";
        InputStream is = null;
        try {
            if ("self".equals(route)) {
                // noinspection JavaAccessorMethodCalledAsEmptyParen
                is = new URL("https://raw.githubusercontent.com/shadowsocksr/shadowsocksr-android/nokcp/src/main/assets/acl/" + filename).openConnection().getInputStream();
                IOUtils.writeString(app.getApplicationInfo().dataDir + '/' + filename, IOUtils.readString(is));
            }
            return Result.SUCCESS;
        } catch (IOException e) {
            VayLog.e(TAG, "onRunJob", e);
            app.track(e);
            return Result.RESCHEDULE;
        } catch (Exception e) {
            // unknown failures, probably shouldn't retry
            VayLog.e(TAG, "onRunJob", e);
            app.track(e);
            return Result.FAILURE;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                VayLog.e(TAG, "onRunJob", e);
                app.track(e);
            }
        }
    }
}
