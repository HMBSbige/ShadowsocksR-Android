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
import com.evernote.android.job.JobCreator;
import com.github.shadowsocks.utils.VayLog;

/**
 * “I create jobs all day long.
 * - Donald Trump, 2015
 * <p>
 * Source: http://www.cnn.com/2015/09/24/politics/donald-trump-marco-rubio-foreign-policy/
 *
 * @author !Mygod
 */
public class DonaldTrump implements JobCreator {

    private static final String TAG = DonaldTrump.class.getSimpleName();

    @Override
    public Job create(String tag) {
        String[] parts = tag.split(":");

        if (AclSyncJob.TAG.equals(parts[0])) {
            return new AclSyncJob(parts[1]);
        } else if (SSRSubUpdateJob.TAG.equals(parts[0])) {
            return new SSRSubUpdateJob();
        } else {
            VayLog.w(TAG, "Unknown job tag: " + tag);
            return null;
        }
    }
}
