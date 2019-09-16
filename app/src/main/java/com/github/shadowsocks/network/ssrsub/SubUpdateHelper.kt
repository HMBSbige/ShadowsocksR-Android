package com.github.shadowsocks.network.ssrsub


import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Base64

import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.database.SSRSub
import com.github.shadowsocks.network.request.RequestCallback
import com.github.shadowsocks.network.request.RequestHelper
import com.github.shadowsocks.utils.Parser
import com.github.shadowsocks.ShadowsocksApplication

import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory

/**
 * Created by vay on 2018/07/19
 */
class SubUpdateHelper private constructor() {
    private val mThreadPool: ScheduledThreadPoolExecutor

    private val mUIHandler: Handler

    init {
        // init thread pool
        mThreadPool = ScheduledThreadPoolExecutor(10, ThreadFactory { r ->
            val thread = Thread(r)
            thread.name = "sub_update_helper-thread"
            thread
        })

        // init ui handler
        mUIHandler = Handler(Looper.getMainLooper())
    }

    /**
     * update sub
     *
     * @see .updateSub
     */
    fun updateSub(subs: List<SSRSub>, callback: SubUpdateCallback) {
        updateSub(subs, 0, callback)
    }

    /**
     * update sub
     *
     * @param subs sub list
     */
    fun updateSub(subs: List<SSRSub>?, position: Int, callback: SubUpdateCallback) {
        mThreadPool.execute { updateSubTask(subs, position, callback) }
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
    private fun updateSubTask(subs: List<SSRSub>?, position: Int, callback: SubUpdateCallback) {
        if (subs == null || subs.isEmpty()) {
            callback.onFailed()
            callback.onFinished()
            return
        }

        if (position < subs.size) {
            val sub = subs[position]
            // start request
            RequestHelper.instance()!!.get(sub.url, object : RequestCallback() {

                override fun onSuccess(code: Int, response: String) {
                    mThreadPool.execute {
                        handleResponse(sub, response, object : SubUpdateCallback() {
                            override fun onFinished() {
                                updateSub(subs, position + 1, callback)
                            }
                        })
                    }
                }

                override fun onFailed(code: Int, msg: String) {
                    callback.onFailed()
                    callback.onFinished()
                }
            })
        } else {
            callback.onFinished()
        }
    }

    /**
     * handle response
     *
     * @param response response string
     */
    private fun handleResponse(sub: SSRSub, response: String, callback: SubUpdateCallback) {
        val deleteProfiles = ShadowsocksApplication.app.profileManager.getAllProfilesByGroup(sub.url_group)
        val responseString = String(Base64.decode(response, Base64.URL_SAFE))
        var profiles = Parser.findAll_ssr(responseString)
        if (profiles == null) {
            profiles = ArrayList()
        } else {
            Collections.shuffle(profiles)
        }

        for (profile in profiles) {
            val resultCode = ShadowsocksApplication.app.profileManager.createProfileSub(profile)
            if (resultCode != 0) {
                val tempList = ArrayList<Profile>()
                for (item in deleteProfiles!!) {
                    if (item.id != resultCode) {
                        tempList.add(item)
                    }
                }
                deleteProfiles.clear()
                deleteProfiles.addAll(tempList)
            }
        }

        for (profile in deleteProfiles!!) {
            if (profile.id != ShadowsocksApplication.app.profileId()) {
                ShadowsocksApplication.app.profileManager.delProfile(profile.id)
            }
        }

        // invoke callback
        mUIHandler.post {
            callback.onSuccess(sub.url_group)
            callback.onFinished()
        }
    }

    companion object {

        private var sInstance: SubUpdateHelper? = null

        /**
         * get instance
         */
        fun instance(): SubUpdateHelper {
            if (sInstance == null) {
                synchronized(SubUpdateHelper::class.java) {
                    if (sInstance == null) {
                        sInstance = SubUpdateHelper()
                    }
                }
            }
            return sInstance!!
        }

        /**
         * parse string to SSRSub object
         *
         * @param subUrl     ssr sub url
         * @param base64text base64 content
         * @return parse failed return null
         */
        fun parseSSRSub(subUrl: String, base64text: String): SSRSub? {
            val profilesSSR = Parser.findAll_ssr(String(Base64.decode(base64text, Base64.URL_SAFE)))
            if (profilesSSR != null && !profilesSSR.isEmpty()) {
                if (!TextUtils.isEmpty(profilesSSR[0].url_group)) {
                    val ssrsub = SSRSub()
                    ssrsub.url = subUrl
                    ssrsub.url_group = profilesSSR[0].url_group
                    return ssrsub
                }
            }
            return null
        }
    }
}
