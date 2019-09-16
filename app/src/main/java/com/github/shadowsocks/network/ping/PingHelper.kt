package com.github.shadowsocks.network.ping


import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log

import com.github.shadowsocks.GuardedProcess
import com.github.shadowsocks.R
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.network.request.RequestCallback
import com.github.shadowsocks.network.request.RequestHelper
import com.github.shadowsocks.utils.Constants
import com.github.shadowsocks.utils.TcpFastOpen
import com.github.shadowsocks.utils.Utils
import com.github.shadowsocks.utils.VayLog
import com.j256.ormlite.stmt.query.In

import java.io.File
import java.util.ArrayList
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory

import okhttp3.Request
import okhttp3.ResponseBody
import kotlin.math.log
import java.io.IOException
import java.net.*


/**
 * Created by vay on 2018/07/18
 */
class PingHelper
/**
 * private construction
 */
private constructor() {
    private val mThreadPool: ScheduledThreadPoolExecutor
    private var ssTestProcess: GuardedProcess? = null

    private var mTempActivity: Activity? = null

    private val applicationInfo: ApplicationInfo?
        get() = if (mTempActivity == null) {
            null
        } else mTempActivity!!.applicationInfo

    init {
        // create thread pool
        mThreadPool = ScheduledThreadPoolExecutor(10, ThreadFactory { r ->
            val thread = Thread(r)
            thread.name = "ping_helper-thread"
            thread
        })
    }

    /**
     * ping profile
     *
     * @param aty      activity object
     * @param profile  profile object
     * @param callback ping callback object
     */
    fun ping(aty: Activity, profile: Profile, callback: PingCallback) {
        mThreadPool.execute { pingByProfile(aty, profile, callback, 1) }
    }


    /**
     * ping profile
     *
     * @param aty      activity object
     * @param profile  profile object
     * @param callback ping callback object
     */
    fun tcp_ping(aty: Activity, profile: Profile, callback: PingCallback) {
        mThreadPool.execute { pingByProfile(aty, profile, callback, 2) }
    }

    /**
     * ping all profile
     *
     * @see .pingAll
     */
    fun pingAll(aty: Activity, profiles: List<Profile>, callback: PingCallback, pingtype: Int) {
        pingAll(aty, profiles, 0, callback, pingtype)
    }

    /**
     * ping all profile
     *
     * @param profiles profile list
     * @param position list start index
     * @param callback ping callback
     */
    fun pingAll(aty: Activity, profiles: List<Profile>?, position: Int, callback: PingCallback, pingtype: Int) {
        mThreadPool.execute { pingAllByProfiles(aty, profiles, position, callback, pingtype) }
    }

    /**
     * release temp activity
     */
    fun releaseTempActivity() {
        if (mTempActivity != null) {
            mTempActivity = null
        }
    }

    //===================================================================================================//
    //========================================= private method =========================================//
    //=================================================================================================//

    /**
     * pint all by profiles
     *
     * @param profiles profile list
     * @param position list start index
     * @param callback ping callback
     */
    private fun pingAllByProfiles(aty: Activity, profiles: List<Profile>?, position: Int, callback: PingCallback, pingtype: Int) {
        if (profiles == null || profiles.isEmpty()) {
            callback.resultMsg = "test all failed, profile list is empty"
            callback.onFailed(null)
            callback.onFinished(null)
            return
        }

        if (position < profiles.size) {
            val profile = profiles[position]
            pingByProfile(aty, profile, object : PingCallback() {
                override fun onSuccess(profile: Profile, elapsed: Long) {
                    callback.resultMsg = resultMsg
                    Log.i("d", resultMsg)
                    callback.onSuccess(profile, elapsed)
                }

                override fun onFailed(profile: Profile?) {
                    callback.resultMsg = resultMsg
                    callback.onFailed(profile)
                }

                override fun onFinished(profile: Profile?) {
                    // test next profile
                    pingAll(aty, profiles, position + 1, callback, pingtype)
                }
            }, pingtype)
        } else {
            // test finished
            callback.onFinished(null)
        }
    }

    var mUIHandler = Handler(Looper.getMainLooper())


    /**
     * pint by profile
     *
     * @param profile  profile
     * @param callback ping callback
     */
    private fun pingByProfile(aty: Activity, profile: Profile, callback: PingCallback, pingtype: Int) {
        Log.i("profile testing: ", profile.name)
        mTempActivity = aty
        // Resolve the server address
        var host: String = profile.host
        if (!Utils.isNumeric(host)) {
            val addr = Utils.resolve(host, profile.ipv6)
            if (!TextUtils.isEmpty(addr)) {
                host = addr
            } else {
                val result = getString(R.string.connection_test_error, "can't resolve")
                callback.resultMsg = result
                callback.onFailed(profile)
                callback.onFinished(profile)
                return
            }
        }
        if (pingtype==1) {
            val conf = String.format(Locale.ENGLISH,
                    Constants.ConfigUtils.SHADOWSOCKS,
                    host,
                    profile.remotePort,
                    profile.localPort + 2,
                    Constants.ConfigUtils.EscapedJson(profile.password),
                    profile.method,
                    600,
                    profile.protocol,
                    profile.obfs,
                    Constants.ConfigUtils.EscapedJson(profile.obfs_param),
                    Constants.ConfigUtils.EscapedJson(profile.protocol_param))

            Utils.printToFile(File(applicationInfo!!.dataDir + "/libssr-local.so-test.conf"), conf, true)

            val cmd = arrayOf(applicationInfo!!.nativeLibraryDir + "/libssr-local.so", "-t", "600", "-L", "www.google.com:80", "-c", applicationInfo!!.dataDir + "/libssr-local.so-test.conf")

            val cmds = ArrayList(Arrays.asList(*cmd))

            if (TcpFastOpen.sendEnabled()) {
                cmds.add("--fast-open")
            }

            if (ssTestProcess != null) {
                ssTestProcess!!.destroy()
                ssTestProcess = null
            }

            try {
                ssTestProcess = GuardedProcess(cmds).start()
            } catch (e: InterruptedException) {
                val result = getString(R.string.connection_test_error, "GuardedProcess start exception")
                callback.resultMsg = result
                callback.onFailed(profile)
                callback.onFinished(profile)
                return
            }


            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 5 * 1000 && isPortAvailable(profile.localPort + 2)) {
                try {
                    Thread.sleep(50)
                } catch (e: InterruptedException) {
                    // Ignored
                }

            }
            //val proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", profile.localPort + 2))

            // Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
            val request = Request.Builder()
                    .url("http://127.0.0.1:" + (profile.localPort + 2) + "/generate_204")
                    .removeHeader("Host")
                    .addHeader("Host", "www.google.com")
                    .build()


            val rinstance = RequestHelper.instance()
            val rcallback = object : RequestCallback() {
                override fun isRequestOk(code: Int): Boolean {
                    return code == 204 || code == 200 ||code==404
                }
                override fun onSuccess(code: Int, response: String) {
                    // update profile
                    val elapsed = System.currentTimeMillis() - this.start

                    val result = getString(R.string.connection_test_available, elapsed)
                    callback.resultMsg = result
                    callback.onSuccess(profile, elapsed)
                }
                override fun onFailed(code: Int, msg: String) {
                    val result: String
                    if (code != 404) {
                        result = getString(R.string.connection_test_error_status_code, code)
                    } else {
                        result = getString(R.string.connection_test_error, msg)
                    }
                    callback.resultMsg = result
                    callback.onFailed(profile)
                }
                override fun onFinished() {
                    callback.onFinished(profile)
                    if (ssTestProcess != null) {
                        ssTestProcess!!.destroy()
                        ssTestProcess = null
                    }
                }
            }
            // flag start request time
            rcallback.start = System.currentTimeMillis()

            // request
            rinstance!!.request(request, rcallback)
        } else {
            mThreadPool.execute {
                val elapsed = tcping(host, profile.remotePort)
                // run to main thread
                mUIHandler.post {
                    try {
                        if (elapsed==0.toLong()) {
                            callback.resultMsg = getString(R.string.connection_test_error, "port can't reach")
                            callback.onFailed(profile)

                        } else {
                            callback.resultMsg = getString(R.string.connection_test_available, elapsed)
                            callback.onSuccess(profile, elapsed)
                        }
                        callback.onFinished(profile)
                    } catch (e: Exception) {
                        callback.onFailed(profile)
                    }
                }
            }
        }

    }

    private fun isPortAvailable(port: Int): Boolean {
        // Assume no connection is possible.
        var result = true

        try {
            Socket("127.0.0.1", port).close()
            result = false
        } catch (e: Exception) {
            VayLog.e(TAG, "isPortAvailable", e)
        }

        return result
    }

    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return if (mTempActivity == null) {
            ""
        } else mTempActivity!!.getString(resId, *formatArgs)
    }

    companion object {

        private val TAG = PingHelper::class.java.simpleName

        private var sInstance: PingHelper? = null

        /**
         * get instance
         */
        fun instance(): PingHelper {
            if (sInstance == null) {
                synchronized(PingHelper::class.java) {
                    if (sInstance == null) {
                        sInstance = PingHelper()
                    }
                }
            }
            return sInstance!!
        }
    }

    fun tcping(target: String, port: Int): Long {
        try {
            var mindelay: Long = 5000
            for (i in 1..2){
                val start = System.currentTimeMillis()
                var sock = Socket()
                val socketAddress = InetSocketAddress(target, port);
                sock.connect(socketAddress, 1000)
                val delay = System.currentTimeMillis()-start
                sock.close()
                if (delay<mindelay) {
                    mindelay =delay
                }
            }
            return mindelay
        } catch (e: UnknownHostException) {
            return 0
        } catch (e: InterruptedException) {
            return 0
        } catch (e: IOException) {
            return 0
        }

    }
}
