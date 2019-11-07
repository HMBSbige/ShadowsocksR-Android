package com.bige0.shadowsocksr.network.ping

import android.app.*
import android.content.pm.*
import com.bige0.shadowsocksr.*
import com.bige0.shadowsocksr.database.*
import com.bige0.shadowsocksr.network.request.*
import com.bige0.shadowsocksr.utils.*
import okhttp3.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.*

class PingHelper
private constructor()
{
	private val mThreadPool: ScheduledThreadPoolExecutor
	private var ssTestProcess: GuardedProcess? = null

	private var mTempActivity: Activity? = null

	private val applicationInfo: ApplicationInfo?
		get() = if (mTempActivity == null)
		{
			null
		}
		else mTempActivity!!.applicationInfo

	init
	{
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
	fun ping(aty: Activity, profile: Profile, callback: PingCallback)
	{
		mThreadPool.execute { pingByProfile(aty, profile, callback) }
	}

	/**
	 * ping all profile
	 *
	 * @see .pingAll
	 */
	fun pingAll(aty: Activity, profiles: List<Profile>, callback: PingCallback)
	{
		pingAll(aty, profiles, 0, callback)
	}

	/**
	 * ping all profile
	 *
	 * @param profiles profile list
	 * @param position list start index
	 * @param callback ping callback
	 */
	fun pingAll(aty: Activity, profiles: List<Profile>?, position: Int, callback: PingCallback)
	{
		mThreadPool.execute { pingAllByProfiles(aty, profiles, position, callback) }
	}

	/**
	 * release temp activity
	 */
	fun releaseTempActivity()
	{
		if (mTempActivity != null)
		{
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
	private fun pingAllByProfiles(aty: Activity, profiles: List<Profile>?, position: Int, callback: PingCallback)
	{
		if (profiles.isNullOrEmpty())
		{
			callback.resultMsg = "test all failed, profile list is empty"
			callback.onFailed(null)
			callback.onFinished(null)
			return
		}

		if (position < profiles.size)
		{
			val profile = profiles[position]
			pingByProfile(aty, profile, object : PingCallback()
			{
				override fun onSuccess(profile: Profile, elapsed: Long)
				{
					callback.resultMsg = resultMsg
					callback.onSuccess(profile, elapsed)
				}

				override fun onFailed(profile: Profile?)
				{
					callback.resultMsg = resultMsg
					callback.onFailed(profile)
				}

				override fun onFinished(profile: Profile?)
				{
					// test next profile
					pingAll(aty, profiles, position + 1, callback)
				}
			})
		}
		else
		{
			// test finished
			callback.onFinished(null)
		}
	}

	/**
	 * ping by profile
	 *
	 * @param profile  profile
	 * @param callback ping callback
	 */
	private fun pingByProfile(aty: Activity, profile: Profile, callback: PingCallback)
	{
		mTempActivity = aty
		// Resolve the server address
		var host: String = profile.host
		if (!Utils.isNumeric(host))
		{
			val addr = Utils.resolve(host, profile.ipv6)

			if (!addr.isNullOrBlank())
			{
				host = addr
			}
			else
			{
				val result = getString(R.string.connection_test_error, "can't resolve")
				callback.resultMsg = result
				callback.onFailed(profile)
				callback.onFinished(profile)
				return
			}
		}

		val conf = String.format(Locale.ENGLISH, Constants.ConfigUtils.SHADOWSOCKS, host, profile.remotePort, profile.localPort + 2, Constants.ConfigUtils.escapedJson(profile.password), profile.method, 600, profile.protocol, profile.obfs, Constants.ConfigUtils.escapedJson(profile.obfs_param), Constants.ConfigUtils.escapedJson(profile.protocol_param))

		Utils.printToFile(File(applicationInfo!!.dataDir + "/libssr-local.so-test.conf"), conf, true)

		val cmd = arrayOf(applicationInfo!!.nativeLibraryDir + "/libssr-local.so", "-t", "600", "-L", "www.google.com:80", "-c", applicationInfo!!.dataDir + "/libssr-local.so-test.conf")

		val cmds = cmd.toMutableList()

		if (ssTestProcess != null)
		{
			ssTestProcess!!.destroy()
			ssTestProcess = null
		}

		try
		{
			ssTestProcess = GuardedProcess(cmds)
				.start()
		}
		catch (e: InterruptedException)
		{
			val result = getString(R.string.connection_test_error, "GuardedProcess start exception")
			callback.resultMsg = result
			callback.onFailed(profile)
			callback.onFinished(profile)
			return
		}

		val start = System.currentTimeMillis()
		while (System.currentTimeMillis() - start < 5 * 1000 && isPortAvailable(profile.localPort + 2))
		{
			try
			{
				Thread.sleep(50)
			}
			catch (e: InterruptedException)
			{
				VayLog.e(TAG, "pingByProfile", e)
			}

		}
		//val proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", profile.localPort + 2))

		// Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640

		val request = Request.Builder()
			.url("http://127.0.0.1:" + (profile.localPort + 2) + "/generate_204")
			.removeHeader("Host")
			.addHeader("Host", "www.google.com")
			.build()

		// request
		val rInstance = RequestHelper.instance()
		val rCallback = object : RequestCallback()
		{
			override fun isRequestOk(code: Int): Boolean
			{
				return code == 204 || code == 200
			}

			override fun onSuccess(code: Int, response: String)
			{
				// update profile
				val elapsed = System.currentTimeMillis() - this.start
				val result = getString(R.string.connection_test_available, elapsed)
				callback.resultMsg = result
				callback.onSuccess(profile, elapsed)
			}

			override fun onFailed(code: Int, msg: String)
			{
				val result: String = if (code != 404)
				{
					getString(R.string.connection_test_error_status_code, code)
				}
				else
				{
					getString(R.string.connection_test_error, msg)
				}
				callback.resultMsg = result
				callback.onFailed(profile)
			}

			override fun onFinished()
			{
				callback.onFinished(profile)
				//Snackbar.make(findViewById(android.R.id.content), result, Snackbar.LENGTH_LONG).show();
				if (ssTestProcess != null)
				{
					ssTestProcess!!.destroy()
					ssTestProcess = null
				}
			}
		}

		// flag start request time
		rCallback.start = System.currentTimeMillis()

		// request
		rInstance!!.request(request, rCallback)

		// Based on: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java#640
	}

	private fun isPortAvailable(port: Int): Boolean
	{
		// Assume no connection is possible.
		var result = true

		try
		{
			Socket("127.0.0.1", port).close()
			result = false
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "isPortAvailable", e)
		}

		return result
	}

	private fun getString(resId: Int, vararg formatArgs: Any): String
	{
		return if (mTempActivity == null)
		{
			""
		}
		else mTempActivity!!.getString(resId, *formatArgs)
	}

	companion object
	{
		private const val TAG = "PingHelper"

		private var sInstance: PingHelper? = null

		/**
		 * get instance
		 */
		fun instance(): PingHelper
		{
			if (sInstance == null)
			{
				synchronized(PingHelper::class.java) {
					if (sInstance == null)
					{
						sInstance = PingHelper()
					}
				}
			}
			return sInstance!!
		}
	}
}
