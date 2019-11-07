package com.bige0.shadowsocksr

import android.content.*
import android.os.*
import com.bige0.shadowsocksr.aidl.*
import com.bige0.shadowsocksr.utils.*

open class ServiceBoundContext(base: Context) : ContextWrapper(base), IBinder.DeathRecipient
{
	companion object
	{
		private const val TAG = "ServiceBoundContext"
	}

	var bgService: IShadowsocksService? = null
	private var binder: IBinder? = null
	private var callback: IShadowsocksServiceCallback? = null
	private var connection: ShadowsocksServiceConnection? = null
	private var callbackRegistered: Boolean = false

	/**
	 * register callback
	 */
	fun registerCallback()
	{
		if (bgService != null && callback != null && !callbackRegistered)
		{
			try
			{
				bgService!!.registerCallback(callback)
				callbackRegistered = true
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "registerCallback", e)
			}

		}
	}

	/**
	 * unregister callback
	 */
	fun unregisterCallback()
	{
		if (bgService != null && callback != null && callbackRegistered)
		{
			try
			{
				bgService!!.unregisterCallback(callback)
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "unregisterCallback", e)
			}

			callbackRegistered = false
		}
	}

	protected open fun onServiceConnected()
	{
	}

	protected open fun onServiceDisconnected()
	{
	}

	override fun binderDied()
	{
	}

	fun attachService(callback: IShadowsocksServiceCallback.Stub? = null)
	{
		this.callback = callback
		if (bgService == null)
		{
			val clazz: Class<*>
			clazz = ShadowsocksVpnService::class.java

			val intent = Intent(this, clazz)
			intent.action = Constants.Action.SERVICE

			connection = ShadowsocksServiceConnection()
			bindService(intent, connection!!, Context.BIND_AUTO_CREATE)
		}
	}

	/**
	 * detach service
	 */
	fun detachService()
	{
		unregisterCallback()
		callback = null
		if (connection != null)
		{
			try
			{
				unbindService(connection!!)
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "detachService", e)
			}

			connection = null
		}

		if (binder != null)
		{
			binder!!.unlinkToDeath(this, 0)
			binder = null
		}

		bgService = null
	}

	inner class ShadowsocksServiceConnection : ServiceConnection
	{
		override fun onServiceConnected(name: ComponentName, service: IBinder)
		{
			try
			{
				binder = service
				service.linkToDeath(this@ServiceBoundContext, 0)
				bgService = IShadowsocksService.Stub.asInterface(service)
				registerCallback()
				this@ServiceBoundContext.onServiceConnected()
			}
			catch (e: RemoteException)
			{
				VayLog.e(TAG, "onServiceConnected", e)
			}

		}

		override fun onServiceDisconnected(name: ComponentName)
		{
			unregisterCallback()
			this@ServiceBoundContext.onServiceDisconnected()
			bgService = null
			binder = null
		}
	}
}
