package com.bige0.shadowsocksr

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.*
import androidx.core.content.*
import com.bige0.shadowsocksr.aidl.*
import com.bige0.shadowsocksr.utils.*
import java.util.*

class ShadowsocksNotification constructor(private val service: Service, private val profileName: String, private val visible: Boolean = false)
{
	private val callback by lazy {
		object : IShadowsocksServiceCallback.Stub()
		{
			override fun stateChanged(state: Int, profileName: String, msg: String)
			{
			}

			override fun trafficUpdated(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long)
			{
				val txr = TrafficMonitor.formatTraffic(txRate)
				val rxr = TrafficMonitor.formatTraffic(rxRate)
				builder.setContentText(String.format(Locale.ENGLISH, service.getString(R.string.traffic_summary), txr, rxr))

				style.bigText(String.format(Locale.ENGLISH,
											service.getString(R.string.stat_summary),
											txr,
											rxr,
											TrafficMonitor.formatTraffic(txTotal),
											TrafficMonitor.formatTraffic(rxTotal)))
				show()
			}
		}
	}

	private val pm: PowerManager = service.getSystemService(Context.POWER_SERVICE) as PowerManager
	private val keyGuard: KeyguardManager = service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
	private val nm: NotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
	private var callbackRegistered: Boolean = false
	private lateinit var builder: NotificationCompat.Builder
	private val style: NotificationCompat.BigTextStyle
	private var isVisible = true

	private var lockReceiver: BroadcastReceiver? = object : BroadcastReceiver()
	{
		override fun onReceive(context: Context, intent: Intent)
		{
			update(intent.action)
		}
	}

	private val serviceState: Int
		get()
		{
			var state = 0
			if (service is BaseVpnService)
			{
				state = service.currentState
			}
			else if (service is BaseService)
			{
				state = service.currentState
			}
			return state
		}

	init
	{
		// init notification builder
		initNotificationBuilder()
		style = NotificationCompat.BigTextStyle(builder)

		// init with update action
		initWithUpdateAction()

		// register lock receiver
		registerLockReceiver(service, visible)
	}

	private fun update(action: String?, forceShow: Boolean = false)
	{
		if (forceShow || serviceState == Constants.State.CONNECTED)
		{
			when (action)
			{
				Intent.ACTION_SCREEN_OFF ->
				{
					setVisible(visible && !Utils.isLollipopOrAbove, forceShow)
					// unregister callback to save battery
					unregisterCallback()
				}
				Intent.ACTION_SCREEN_ON ->
				{
					setVisible(visible && Utils.isLollipopOrAbove && !keyGuard.isKeyguardLocked, forceShow)
					try
					{
						registerServiceCallback(callback)
					}
					catch (e: RemoteException)
					{
						// Ignored
					}
					callbackRegistered = true
				}
				Intent.ACTION_USER_PRESENT -> setVisible(true, forceShow)
			}
		}
	}

	fun destroy()
	{
		if (lockReceiver != null)
		{
			service.unregisterReceiver(lockReceiver)
			lockReceiver = null
		}
		unregisterCallback()
		service.stopForeground(true)
		nm.cancel(1)
	}

	private fun setVisible(visible: Boolean, forceShow: Boolean)
	{
		if (isVisible != visible)
		{
			isVisible = visible
			builder.priority = if (visible) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN
			show()
		}
		else if (forceShow)
		{
			show()
		}
	}

	fun show()
	{
		service.startForeground(1, builder.build())
	}

	private fun unregisterCallback()
	{
		if (callbackRegistered)
		{
			try
			{
				unregisterServiceCallback(callback)
			}
			catch (e: RemoteException)
			{
				e.printStackTrace()
			}

			callbackRegistered = false
		}
	}

	private fun registerLockReceiver(service: Service, visible: Boolean)
	{
		val screenFilter = IntentFilter()
		screenFilter.addAction(Intent.ACTION_SCREEN_ON)
		screenFilter.addAction(Intent.ACTION_SCREEN_OFF)
		if (visible && Utils.isLollipopOrAbove)
		{
			screenFilter.addAction(Intent.ACTION_USER_PRESENT)
		}
		service.registerReceiver(lockReceiver, screenFilter)
	}

	private fun initWithUpdateAction()
	{
		val action = if (pm.isInteractive) Intent.ACTION_SCREEN_ON else Intent.ACTION_SCREEN_OFF
		update(action, true)
	}

	private fun initNotificationBuilder()
	{
		val channelId = "net_speed"
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			val channel = NotificationChannel(channelId, "NetSpeed", NotificationManager.IMPORTANCE_MIN)
			channel.setSound(null, null)
			nm.createNotificationChannel(channel)
		}
		builder = NotificationCompat.Builder(service, channelId)
			.setWhen(0)
			.setColor(ContextCompat.getColor(service, R.color.material_accent_500))
			.setTicker(service.getString(R.string.forward_success))
			.setContentTitle(profileName)
			.setContentIntent(PendingIntent.getActivity(service, 0, Intent(service, Shadowsocks::class.java)
				.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0))
			.setSmallIcon(R.drawable.ic_stat_shadowsocks)
		builder.addAction(R.drawable.ic_navigation_close,
						  service.getString(R.string.stop),
						  PendingIntent.getBroadcast(service, 0, Intent(Constants.Action.CLOSE), 0))

		val profiles = ShadowsocksApplication.app.profileManager.allProfiles
		if (profiles.isNotEmpty())
		{
			builder.addAction(R.drawable.ic_action_settings, service.getString(R.string.quick_switch),
							  PendingIntent.getActivity(service, 0, Intent(Constants.Action.QUICK_SWITCH), 0))
		}
	}

	private fun registerServiceCallback(callback: IShadowsocksServiceCallback)
	{
		var binder: IShadowsocksService.Stub? = null
		if (service is BaseVpnService)
		{
			binder = service.binder
		}
		else if (service is BaseService)
		{
			binder = service.binder
		}
		binder?.registerCallback(callback)
	}

	private fun unregisterServiceCallback(callback: IShadowsocksServiceCallback)
	{
		var binder: IShadowsocksService.Stub? = null
		if (service is BaseVpnService)
		{
			binder = service.binder
		}
		else if (service is BaseService)
		{
			binder = service.binder
		}
		binder?.unregisterCallback(callback)
	}
}
