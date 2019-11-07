package com.bige0.shadowsocksr

import android.app.*
import android.content.*
import android.os.*
import com.bige0.shadowsocksr.aidl.*
import com.bige0.shadowsocksr.utils.*

abstract class ServiceBoundService : Service(), IBinder.DeathRecipient
{
	companion object
	{
		private const val TAG = "ServiceBoundService"
	}

	protected var bgService: IShadowsocksService? = null
	private var binder: IBinder? = null
	private var callback: IShadowsocksServiceCallback? = null
	private var connection: ShadowsocksServiceConnection? = null
	private var callbackRegistered: Boolean = false

	/**
	 * register callback
	 */
	private fun registerCallback()
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
	protected fun unregisterCallback()
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
			val intent = Intent(this, ShadowsocksVpnService::class.java)
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
				service.linkToDeath(this@ServiceBoundService, 0)
				bgService = IShadowsocksService.Stub.asInterface(service)
				registerCallback()
				this@ServiceBoundService.onServiceConnected()
			}
			catch (e: RemoteException)
			{
				VayLog.e(TAG, "onServiceConnected", e)
			}

		}

		override fun onServiceDisconnected(name: ComponentName)
		{
			unregisterCallback()
			this@ServiceBoundService.onServiceDisconnected()
			bgService = null
			binder = null
		}
	}
}
