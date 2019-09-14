package com.github.shadowsocks.network.ssrsub;
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

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.SSRSub;
import com.github.shadowsocks.network.request.RequestCallback;
import com.github.shadowsocks.network.request.RequestHelper;
import com.github.shadowsocks.utils.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import androidx.annotation.NonNull;

import static com.github.shadowsocks.ShadowsocksApplication.app;

/**
 * Created by vay on 2018/07/19
 */
public class SubUpdateHelper {

    private static SubUpdateHelper sInstance;
    private final ScheduledThreadPoolExecutor mThreadPool;

    private Handler mUIHandler;

    /**
     * get instance
     */
    public static SubUpdateHelper instance() {
        if (sInstance == null) {
            synchronized (SubUpdateHelper.class) {
                if (sInstance == null) {
                    sInstance = new SubUpdateHelper();
                }
            }
        }
        return sInstance;
    }

    /**
     * parse string to SSRSub object
     *
     * @param subUrl     ssr sub url
     * @param base64text base64 content
     * @return parse failed return null
     */
    public static SSRSub parseSSRSub(String subUrl, String base64text) {
        List<Profile> profilesSSR = Parser.findAll_ssr(new String(Base64.decode(base64text, Base64.URL_SAFE)));
        if (profilesSSR != null && !profilesSSR.isEmpty()) {
            if (!TextUtils.isEmpty(profilesSSR.get(0).url_group)) {
                SSRSub ssrsub = new SSRSub();
                ssrsub.url = subUrl;
                ssrsub.url_group = profilesSSR.get(0).url_group;
                return ssrsub;
            }
        }
        return null;
    }

    private SubUpdateHelper() {
        // init thread pool
        mThreadPool = new ScheduledThreadPoolExecutor(10, new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("sub_update_helper-thread");
                return thread;
            }
        });

        // init ui handler
        mUIHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * update sub
     *
     * @see #updateSub(List, int, SubUpdateCallback)
     */
    public void updateSub(List<SSRSub> subs, SubUpdateCallback callback) {
        updateSub(subs, 0, callback);
    }

    /**
     * update sub
     *
     * @param subs sub list
     */
    public void updateSub(final List<SSRSub> subs, final int position, final SubUpdateCallback callback) {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                updateSubTask(subs, position, callback);
            }
        });
    }

    //===================================================================================================//
    //========================================= private method =========================================//
    //=================================================================================================//

    /**
     * update sub task
     *
     * @param subs     sub list
     * @param position list start index
     * @param callback request callback
     */
    private void updateSubTask(final List<SSRSub> subs, final int position, final SubUpdateCallback callback) {
        if (subs == null || subs.isEmpty()) {
            callback.onFailed();
            callback.onFinished();
            return;
        }

        if (position < subs.size()) {
            final SSRSub sub = subs.get(position);
            // start request
            RequestHelper.instance().get(sub.url, new RequestCallback() {

                @Override
                public void onSuccess(int code, final String response) {
                    mThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            handleResponse(sub, response, new SubUpdateCallback() {
                                @Override
                                public void onFinished() {
                                    updateSub(subs, position + 1, callback);
                                }
                            });
                        }
                    });
                }

                @Override
                public void onFailed(int code, String msg) {
                    callback.onFailed();
                    callback.onFinished();
                }
            });
        } else {
            callback.onFinished();
        }
    }

    /**
     * handle response
     *
     * @param response response string
     */
    private void handleResponse(SSRSub sub, String response, final SubUpdateCallback callback) {
        final List<Profile> deleteProfiles = app.profileManager.getAllProfilesByGroup(sub.url_group);
        String responseString = new String(Base64.decode(response, Base64.URL_SAFE));
        List<Profile> profiles = Parser.findAll_ssr(responseString);
        if (profiles == null) {
            profiles = new ArrayList<>();
        } else {
            Collections.shuffle(profiles);
        }

        for (Profile profile : profiles) {
            int resultCode = app.profileManager.createProfileSub(profile);
            if (resultCode != 0) {
                List<Profile> tempList = new ArrayList<>();
                for (Profile item : deleteProfiles) {
                    if (item.id != resultCode) {
                        tempList.add(item);
                    }
                }
                deleteProfiles.clear();
                deleteProfiles.addAll(tempList);
            }
        }

        for (Profile profile : deleteProfiles) {
            if (profile.id != app.profileId()) {
                app.profileManager.delProfile(profile.id);
            }
        }

        // invoke callback
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess();
                callback.onFinished();
            }
        });
    }
}
