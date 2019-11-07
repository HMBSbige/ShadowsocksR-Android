package com.bige0.shadowsocksr

import android.content.*
import android.net.*
import android.os.*
import com.bige0.shadowsocksr.aidl.*
import com.bige0.shadowsocksr.utils.*

class ShadowsocksRunnerService : ServiceBoundService()
{
	companion object
	{
		private const val TAG = "ShadowsocksRunnerService"
	}

	private val handler = Handler()

	private val mCallback = object : IShadowsocksServiceCallback.Stub()
	{
		override fun stateChanged(state: Int, profileName: String, msg: String)
		{
		}

		override fun trafficUpdated(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long)
		{
		}
	}

	private val mStopSelfRunnable = Runnable { stopSelf() }

	override fun onBind(intent: Intent): IBinder?
	{
		return null
	}

	override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int
	{
		detachService()
		attachService(mCallback)
		return START_STICKY
	}

	override fun onServiceConnected()
	{
		if (bgService != null)
		{
			if (VpnService.prepare(this@ShadowsocksRunnerService) == null)
			{
				startBackgroundService()
			}
			else
			{
				handler.postDelayed(mStopSelfRunnable, 10000)
			}
		}
	}

	private fun startBackgroundService()
	{
		try
		{
			bgService!!.use(ShadowsocksApplication.app.profileId())
		}
		catch (e: RemoteException)
		{
			VayLog.e(TAG, "startBackgroundService", e)
			ShadowsocksApplication.app.track(e)
		}
	}

	override fun onDestroy()
	{
		super.onDestroy()
		detachService()
	}
}
