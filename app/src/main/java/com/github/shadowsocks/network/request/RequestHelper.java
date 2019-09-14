package com.github.shadowsocks.network.request;
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

import java.io.IOException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * network request utils
 *
 * Created by vay on 2018/07/18
 */
public class RequestHelper {

    private static RequestHelper sInstance;
    private static final OkHttpClient.Builder mDefaultBuilder;

    static {
        // init default builder
        mDefaultBuilder = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS);
    }

    private OkHttpClient.Builder mClientBuilder;
    private OkHttpClient mClient;
    private final ScheduledThreadPoolExecutor mThreadPool;
    private final Handler mUIHandler;
    private final RequestCallback mDefaultRequestCallback;

    /**
     * init
     *
     * @see #init(OkHttpClient.Builder)
     */
    public static void init() {
        init(null);
    }

    /**
     * init
     *
     * @param builder client builder object
     */
    public static void init(OkHttpClient.Builder builder) {
        if (sInstance == null) {
            synchronized (RequestHelper.class) {
                if (sInstance == null) {
                    sInstance = new RequestHelper(builder);
                }
            }
        }
    }

    /**
     * get instance
     */
    public static RequestHelper instance() {
        init();
        return sInstance;
    }

    /**
     * construction method
     */
    private RequestHelper(OkHttpClient.Builder builder) {
        // create thread pool
        mThreadPool = new ScheduledThreadPoolExecutor(10, new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("request_helper-thread");
                return thread;
            }
        });

        // init handler
        mUIHandler = new Handler(Looper.getMainLooper());

        // create default request callback
        mDefaultRequestCallback = new RequestCallback();

        // set default builder
        setClientBuilder(builder);
    }

    /**
     * network request by get
     *
     * @param url      request url
     * @param callback request callback
     */
    public void get(String url, RequestCallback callback) {
        Request request = createRequest(url, callback);
        if (request == null) {
            return;
        }

        // request
        request(request, callback);
    }

    /**
     * network request by post
     *
     * @param url      request url
     * @param body     request body
     * @param callback request callback
     */
    public void post(String url, RequestBody body, RequestCallback callback) {
        Request request = createRequest(url, body, callback);
        if (request == null) {
            return;
        }

        // request
        request(request, callback);
    }

    /**
     * network request
     *
     * @param request  request object
     * @param callback request callback
     */
    public void request(Request request, RequestCallback callback) {
        // no allow callback is null
        if (callback == null) {
            callback = mDefaultRequestCallback;
        }

        // start request task
        startRequestTask(request, callback);
    }

    /**
     * request by thread
     *
     * @param request request object
     * @return response object
     * @throws IOException request exception
     */
    public Response requestByThread(Request request) throws IOException {
        return mClient.newCall(request).execute();
    }

    //===================================================================================================//
    //========================================= private method =========================================//
    //=================================================================================================//

    /**
     * create request object
     */
    private Request createRequest(String url, RequestCallback callback) {
        return createRequest(url, null, callback);
    }

    /**
     * create request object
     *
     * @param url  request url
     * @param body request body
     * @return create failed return null.
     */
    private Request createRequest(String url, RequestBody body, RequestCallback callback) {
        // format url string
        url = formatUrl(url);
        try {
            Request.Builder builder = new Request.Builder();
            builder.url(url);
            // body not null,  set body
            if (body != null) {
                builder.post(body);
            }
            return builder.build();
        } catch (Exception e) {
            callback.onFailed(404, e.getMessage());
            callback.onFinished();
            return null;
        }
    }

    /**
     * format url string
     *
     * @param url request url
     * @return url
     */
    private String formatUrl(String url) {
        url = url.replace(" ", "");
        url = url.replace("\n", "");
        url = url.replace("\r", "");
        return url;
    }

    /**
     * set client builder
     *
     * @param builder client builder object
     */
    private void setClientBuilder(OkHttpClient.Builder builder) {
        if (builder != null) {
            mClientBuilder = builder;
        } else {
            mClientBuilder = mDefaultBuilder;
        }
        mClient = mClientBuilder.build();
    }

    /**
     * open request task
     *
     * @param request  okhttp request object
     * @param callback request callback
     */
    private void startRequestTask(final Request request, final RequestCallback callback) {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                ResponseBody body = null;
                try {
                    Response response = requestByThread(request);
                    int code = response.code();
                    body = response.body();
                    String result = null;
                    if (body != null) {
                        result = body.string();
                    }
                    invokeRequestCallback(callback, code, result);
                } catch (Exception e) {
                    invokeRequestCallback(callback, 404, e.getMessage());
                } finally {
                    // close
                    if (body != null) {
                        body.close();
                    }

                    // invoke finished
                    invokeFinished(callback);
                }
            }
        });
    }

    /**
     * invoke request callback
     *
     * @param callback request callback
     * @param code     response code
     * @param response body
     */
    private void invokeRequestCallback(final RequestCallback callback, final int code, final String response) {
        if (callback == null) {
            return;
        }
        // run to main thread
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (callback.isRequestOk(code)) {
                        String result = "";
                        if (!TextUtils.isEmpty(response)) {
                            result = response;
                        }
                        // invoke success callback
                        callback.onSuccess(code, result);
                    } else {
                        String result = "null";
                        if (!TextUtils.isEmpty(response)) {
                            result = response;
                        }
                        callback.onFailed(code, result);
                    }
                } catch (Exception e) {
                    callback.onFailed(404, e.getMessage());
                }
            }
        });
    }

    /**
     * invoke finished
     *
     * @param callback request callback
     */
    private void invokeFinished(final RequestCallback callback) {
        if (callback == null) {
            return;
        }
        // run to main thread
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onFinished();
            }
        });
    }
}
