package com.bige0.shadowsocksr

import android.annotation.*
import android.content.*
import android.graphics.drawable.*
import android.os.*
import android.service.quicksettings.*
import com.bige0.shadowsocksr.aidl.*
import com.bige0.shadowsocksr.utils.*

@TargetApi(Build.VERSION_CODES.N)
class ShadowsocksTileService : TileService()
{
	private lateinit var mServiceBoundContext: ServiceBoundContext

	private lateinit var iconIdle: Icon
	private lateinit var iconBusy: Icon
	private lateinit var iconConnected: Icon

	private val callback = object : IShadowsocksServiceCallback.Stub()
	{
		override fun trafficUpdated(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long)
		{
		}

		override fun stateChanged(state: Int, profileName: String?, msg: String?)
		{
			val tile = qsTile
			if (tile != null)
			{
				when (state)
				{
					Constants.State.STOPPED ->
					{
						tile.icon = iconIdle
						tile.label = getString(R.string.app_name)
						tile.state = Tile.STATE_INACTIVE
					}
					Constants.State.CONNECTED ->
					{
						tile.icon = iconConnected
						val label = profileName ?: getString(R.string.app_name)
						tile.label = label
						tile.state = Tile.STATE_ACTIVE
					}
					else ->
					{
						tile.icon = iconBusy
						tile.label = getString(R.string.app_name)
						tile.state = Tile.STATE_UNAVAILABLE
					}
				}
				tile.updateTile()
			}
		}
	}

	override fun attachBaseContext(base: Context)
	{
		super.attachBaseContext(base)

		iconIdle = Icon.createWithResource(this, R.drawable.ic_start_idle)
			.setTint(-0x7f000001)
		iconBusy = Icon.createWithResource(this, R.drawable.ic_start_busy)
		iconConnected = Icon.createWithResource(this, R.drawable.ic_start_connected)

		mServiceBoundContext = object : ServiceBoundContext(base)
		{
			override fun onServiceConnected()
			{
				try
				{
					if (bgService != null)
					{
						callback.stateChanged(bgService!!.state, bgService!!.profileName, null)
					}
				}
				catch (e: RemoteException)
				{
					e.printStackTrace()
				}
			}
		}
	}

	override fun onStartListening()
	{
		super.onStartListening()
		mServiceBoundContext.attachService(callback)
	}

	override fun onStopListening()
	{
		super.onStopListening()
		// just in case the user switches to NAT mode, also saves battery
		mServiceBoundContext.detachService()
	}

	override fun onClick()
	{
		super.onClick()
		if (isLocked)
		{
			unlockAndRun { toggle() }
		}
		else
		{
			toggle()
		}
	}

	private fun toggle()
	{
		if (mServiceBoundContext.bgService != null)
		{
			try
			{
				when (mServiceBoundContext.bgService!!.state)
				{
					Constants.State.STOPPED -> Utils.startSsService(this)
					Constants.State.CONNECTED -> Utils.stopSsService(this)
					else ->
					{
					} // ignore
				}
			}
			catch (e: RemoteException)
			{
				e.printStackTrace()
			}
		}
	}
}
