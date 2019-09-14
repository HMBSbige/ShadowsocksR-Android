package com.github.shadowsocks.database;
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

import com.github.shadowsocks.utils.VayLog;

import java.util.ArrayList;
import java.util.List;

import static com.github.shadowsocks.ShadowsocksApplication.app;

public class SSRSubManager {

    private static final String TAG = SSRSubManager.class.getSimpleName();
    private final DBHelper dbHelper;
    private List<SSRSubAddedListener> mSSRSubAddedListeners;

    public SSRSubManager(DBHelper helper) {
        this.dbHelper = helper;
        mSSRSubAddedListeners = new ArrayList<>(20);
    }

    public SSRSub createSSRSub(SSRSub p) {
        SSRSub ssrsub;
        if (p == null) {
            ssrsub = new SSRSub();
        } else {
            ssrsub = p;
        }
        ssrsub.id = 0;

        try {
            dbHelper.ssrsubDao.createOrUpdate(ssrsub);
            invokeSSRSubAdded(ssrsub);
        } catch (Exception e) {
            VayLog.e(TAG, "addSSRSub", e);
            app.track(e);
        }
        return ssrsub;
    }

    public boolean updateSSRSub(SSRSub ssrsub) {
        try {
            dbHelper.ssrsubDao.update(ssrsub);
            return true;
        } catch (Exception e) {
            VayLog.e(TAG, "updateSSRSub", e);
            app.track(e);
            return false;
        }
    }

    public SSRSub getSSRSub(int id) {
        try {
            return dbHelper.ssrsubDao.queryForId(id);
        } catch (Exception e) {
            VayLog.e(TAG, "getSSRSub", e);
            app.track(e);
            return null;
        }
    }

    public boolean delSSRSub(int id) {
        try {
            dbHelper.ssrsubDao.deleteById(id);
            return true;
        } catch (Exception e) {
            VayLog.e(TAG, "delSSRSub", e);
            app.track(e);
            return false;
        }
    }

    public SSRSub getFirstSSRSub() {
        try {
            List<SSRSub> result = dbHelper.ssrsubDao.query(dbHelper.ssrsubDao.queryBuilder().limit(1L).prepare());
            if (result != null && !result.isEmpty()) {
                return result.get(0);
            } else {
                return null;
            }
        } catch (Exception e) {
            VayLog.e(TAG, "getAllSSRSubs", e);
            app.track(e);
            return null;
        }
    }

    public List<SSRSub> getAllSSRSubs() {
        try {
            return dbHelper.ssrsubDao.query(dbHelper.ssrsubDao.queryBuilder().prepare());
        } catch (Exception e) {
            VayLog.e(TAG, "getAllSSRSubs", e);
            app.track(e);
            return null;
        }
    }

    public SSRSub createDefault() {
        SSRSub ssrSub = new SSRSub();
        ssrSub.url = "https://raw.githubusercontent.com/breakwa11/breakwa11.github.io/master/free/freenodeplain.txt";
        ssrSub.url_group = "FreeSSR-public";
        return createSSRSub(ssrSub);
    }

    /**
     * add ssr sub added listener
     *
     * @param l callback
     */
    public void addSSRSubAddedListener(SSRSubAddedListener l) {
        if (mSSRSubAddedListeners == null) {
            return;
        }

        // adding listener
        if (!mSSRSubAddedListeners.contains(l)) {
            mSSRSubAddedListeners.add(l);
        }
    }

    /**
     * remove ssr sub added listener
     *
     * @param l callback
     */
    public void removeSSRSubAddedListener(SSRSubAddedListener l) {
        if (mSSRSubAddedListeners == null || mSSRSubAddedListeners.isEmpty()) {
            return;
        }

        // remove listener
        if (mSSRSubAddedListeners.contains(l)) {
            mSSRSubAddedListeners.remove(l);
        }
    }

    /**
     * invoke ssr sub added listener
     *
     * @param ssrSub ssr sub param
     */
    private void invokeSSRSubAdded(SSRSub ssrSub) {
        if (mSSRSubAddedListeners == null || mSSRSubAddedListeners.isEmpty()) {
            return;
        }

        // iteration invoke listener
        for (SSRSubAddedListener l : mSSRSubAddedListeners) {
            if (l != null) {
                l.onSSRSubAdded(ssrSub);
            }
        }
    }

    public interface SSRSubAddedListener {

        /**
         * ssr sub added
         *
         * @param ssrSub ssr sub object
         */
        void onSSRSubAdded(SSRSub ssrSub);
    }
}
