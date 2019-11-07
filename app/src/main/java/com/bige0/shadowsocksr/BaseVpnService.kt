package com.bige0.shadowsocksr

import android.app.*
import android.content.*
import android.net.*
import android.os.*
import com.bige0.shadowsocksr.aidl.*
import com.bige0.shadowsocksr.database.*
import com.bige0.shadowsocksr.utils.*
import okhttp3.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.*

abstract class BaseVpnService : VpnService()
{
	private val callbacks: RemoteCallbackList<IShadowsocksServiceCallback> = RemoteCallbackList()
	protected var profile: Profile? = null
	var currentState = Constants.State.STOPPED
		private set
	private var timer: Timer? = null
	private var trafficMonitorThread: TrafficMonitorThread? = null
	private var callbacksCount: Int = 0
	private val handler = Handler(Looper.getMainLooper())
	private var closeReceiverRegistered: Boolean = false
	private val closeReceiver = object : BroadcastReceiver()
	{
		override fun onReceive(context: Context, intent: Intent)
		{
			ToastUtils.showShort(R.string.stopping)
			stopRunner(true)
		}
	}
	var binder: IShadowsocksService.Stub = object : IShadowsocksService.Stub()
	{
		override fun getState(): Int
		{
			return currentState
		}

		override fun getProfileName(): String?
		{
			return if (profile == null)
			{
				null
			}
			else
			{
				profile!!.name
			}
		}

		override fun unregisterCallback(cb: IShadowsocksServiceCallback?)
		{
			if (cb != null && callbacks.unregister(cb))
			{
				callbacksCount -= 1
				if (callbacksCount == 0 && timer != null)
				{
					timer!!.cancel()
					timer = null
				}
			}
		}

		override fun registerCallback(cb: IShadowsocksServiceCallback?)
		{
			if (cb != null && callbacks.register(cb))
			{
				callbacksCount += 1
				if (callbacksCount != 0 && timer == null)
				{
					val task = object : TimerTask()
					{
						override fun run()
						{
							if (TrafficMonitor.updateRate())
							{
								updateTrafficRate()
							}
						}
					}
					timer = Timer(true)
					timer!!.schedule(task, 1000, 1000)
				}
				TrafficMonitor.updateRate()
				try
				{
					cb.trafficUpdated(TrafficMonitor.txRate, TrafficMonitor.rxRate, TrafficMonitor.txTotal, TrafficMonitor.rxTotal)
				}
				catch (e: RemoteException)
				{
					VayLog.e(TAG, "registerCallback", e)
					ShadowsocksApplication.app.track(e)
				}

			}
		}

		@Synchronized
		override fun use(profileId: Int)
		{
			if (profileId < 0)
			{
				stopRunner(true)
			}
			else
			{
				val profile = ShadowsocksApplication.app.profileManager.getProfile(profileId)
				if (profile == null)
				{
					stopRunner(true)
				}
				else
				{
					when (currentState)
					{
						Constants.State.STOPPED -> if (checkProfile(profile))
						{
							startRunner(profile)
						}
						Constants.State.CONNECTED -> if (profileId != this@BaseVpnService.profile!!.id && checkProfile(profile))
						{
							stopRunner(false)
							startRunner(profile)
						}
						else -> VayLog.w(TAG, "Illegal state when invoking use: $currentState")
					}
				}
			}
		}

		override fun useSync(profileId: Int)
		{
			use(profileId)
		}
	}

	val blackList: String
		get()
		{
			val defaultList = getString(R.string.black_list)
			try
			{
				val container = ShadowsocksApplication.app.containerHolder.container
				val update = container.getString("black_list_lite")

				val list: String
				list = if (update.isNullOrEmpty())
				{
					defaultList
				}
				else
				{
					update
				}
				return "exclude = $list;"
			}
			catch (e: Exception)
			{
				return "exclude = $defaultList;"
			}

		}

	private fun checkProfile(profile: Profile): Boolean
	{
		return if (profile.host.isEmpty() || profile.password.isEmpty())
		{
			stopRunner(true, getString(R.string.proxy_empty))
			false
		}
		else
		{
			true
		}
	}

	open fun connect()
	{
		if (profile == null) return
		if (Constants.DefaultHostName == profile!!.host)
		{
			val holder = ShadowsocksApplication.app.containerHolder
			val container = holder.container
			val url = container.getString("proxy_url")
			val sig = Utils.getSignature(this)

			val client = OkHttpClient.Builder()
				.dns(object : Dns
					 {
						 override fun lookup(hostname: String): List<InetAddress>
						 {
							 val ip = Utils.resolve(hostname, false)
							 return if (ip != null)
							 {
								 listOf(InetAddress.getByName(ip))
							 }
							 else
							 {
								 Dns.SYSTEM.lookup(hostname)
							 }
						 }
					 })
				.connectTimeout(10, TimeUnit.SECONDS)
				.writeTimeout(10, TimeUnit.SECONDS)
				.readTimeout(30, TimeUnit.SECONDS)
				.build()

			val requestBody = FormBody.Builder()
				.add("sig", sig ?: "")
				.build()

			val request = Request.Builder()
				.url(url)
				.post(requestBody)
				.build()

			try
			{
				val response = client.newCall(request)
					.execute()
				val list = response.body!!.string()

				val proxies = list.split("|")
				proxies.shuffled()
				val proxy = proxies[0].split(":")
				profile!!.host = proxy[0].trim()
				profile!!.remotePort = Integer.parseInt(proxy[1].trim())
				profile!!.password = proxy[2].trim()
				profile!!.method = proxy[3].trim()
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "connect", e)
				ShadowsocksApplication.app.track(e)
				stopRunner(true, e.message)
			}

		}
	}

	open fun startRunner(profile: Profile)
	{
		this.profile = profile

		startService(Intent(this, javaClass))
		TrafficMonitor.reset()
		trafficMonitorThread = TrafficMonitorThread(applicationContext)
		trafficMonitorThread!!.start()

		if (!closeReceiverRegistered)
		{
			// register close receiver
			val filter = IntentFilter()
			filter.addAction(Intent.ACTION_SHUTDOWN)
			filter.addAction(Constants.Action.CLOSE)
			registerReceiver(closeReceiver, filter)
			closeReceiverRegistered = true
		}

		ShadowsocksApplication.app.track(TAG, "start")

		changeState(Constants.State.CONNECTING)

		if (profile.isMethodUnsafe)
		{
			handler.post { ToastUtils.showLong(R.string.method_unsafe) }
		}

		// connect
		try
		{
			connect()
		}
		catch (e: NameNotResolvedException)
		{
			stopRunner(true, getString(R.string.invalid_server))
		}
		catch (e: NullConnectionException)
		{
			stopRunner(true, getString(R.string.reboot_required))
		}
		catch (exc: Throwable)
		{
			stopRunner(true, "${getString(R.string.service_failed)}: ${exc.message}")
			exc.printStackTrace()
			ShadowsocksApplication.app.track(exc)
		}
	}

	open fun stopRunner(stopService: Boolean)
	{
		stopRunner(stopService, null)
	}

	open fun stopRunner(stopService: Boolean, msg: String?)
	{
		// clean up recevier
		if (closeReceiverRegistered)
		{
			unregisterReceiver(closeReceiver)
			closeReceiverRegistered = false
		}

		// Make sure update total traffic when stopping the runner
		updateTrafficTotal(TrafficMonitor.txTotal, TrafficMonitor.rxTotal)

		TrafficMonitor.reset()
		if (trafficMonitorThread != null)
		{
			trafficMonitorThread!!.stopThread()
			trafficMonitorThread = null
		}

		// change the state
		changeState(Constants.State.STOPPED, msg)

		// stop the service if nothing has bound to it
		if (stopService)
		{
			stopSelf()
		}

		// init profile
		profile = null
	}

	private fun updateTrafficTotal(tx: Long, rx: Long)
	{
		// avoid race conditions without locking
		val profile = profile
		if (profile != null)
		{
			val p = ShadowsocksApplication.app.profileManager.getProfile(profile.id)
			if (p != null)
			{
				// default profile may have host, etc. modified
				p.tx = p.tx + tx
				p.rx = p.rx + rx
				ShadowsocksApplication.app.profileManager.updateProfile(p)
			}
		}
	}

	private fun updateTrafficRate()
	{
		handler.post {
			if (callbacksCount > 0)
			{
				val txRate = TrafficMonitor.txRate
				val rxRate = TrafficMonitor.rxRate
				val txTotal = TrafficMonitor.txTotal
				val rxTotal = TrafficMonitor.rxTotal
				val n = callbacks.beginBroadcast()
				for (i in 0 until n)
				{
					try
					{
						callbacks.getBroadcastItem(i)
							.trafficUpdated(txRate, rxRate, txTotal, rxTotal)
					}
					catch (e: Exception)
					{
						// Ignore
					}

				}
				callbacks.finishBroadcast()
			}
		}
	}

	override fun onCreate()
	{
		super.onCreate()
		ShadowsocksApplication.app.refreshContainerHolder()
		ShadowsocksApplication.app.updateAssets()
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int
	{
		// Service of shadowsocks should always be started explicitly
		return Service.START_NOT_STICKY
	}

	protected fun changeState(s: Int, msg: String? = null)
	{
		val handler = Handler(Looper.getMainLooper())
		handler.post {
			if (currentState != s || msg != null)
			{
				if (callbacksCount > 0)
				{
					val n = callbacks.beginBroadcast()
					for (i in 0 until n)
					{
						try
						{
							callbacks.getBroadcastItem(i)
								.stateChanged(s, binder.profileName, msg)
						}
						catch (e: Exception)
						{
							// Ignore
						}

					}
					callbacks.finishBroadcast()
				}
				currentState = s
			}
		}
	}

	inner class NameNotResolvedException : IOException()

	class NullConnectionException : NullPointerException()

	companion object
	{
		val protectPath = "${ShadowsocksApplication.app.applicationInfo.dataDir}/protect_path"
		private const val TAG = "BaseVpnService"
	}
}
