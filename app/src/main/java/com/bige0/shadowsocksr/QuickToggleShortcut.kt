package com.bige0.shadowsocksr

import android.app.*
import android.content.*
import android.content.pm.*
import android.os.*
import androidx.core.content.*
import androidx.core.content.pm.*
import com.bige0.shadowsocksr.shortcuts.*
import com.bige0.shadowsocksr.utils.*

class QuickToggleShortcut : Activity()
{
	private lateinit var mServiceBoundContext: ServiceBoundContext

	override fun attachBaseContext(newBase: Context)
	{
		super.attachBaseContext(newBase)
		mServiceBoundContext = object : ServiceBoundContext(newBase)
		{
			override fun onServiceConnected()
			{
				try
				{
					when (bgService?.state)
					{
						Constants.State.STOPPED ->
						{
							ToastUtils.showShort(R.string.loading)
							Utils.startSsService(this)
							val connectedShortcut = makeToggleShortcut(context = this, isConnected = true)
							ShortcutManagerCompat.pushDynamicShortcut(this, connectedShortcut)
						}

						Constants.State.CONNECTED ->
						{
							Utils.stopSsService(this)
							val disconnectedShortcut = makeToggleShortcut(context = this, isConnected = false)
							ShortcutManagerCompat.pushDynamicShortcut(this, disconnectedShortcut)
						}

						else ->
						{
						}
					} // ignore
				}
				catch (e: RemoteException)
				{
					e.printStackTrace()
				}

				finish()
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)

		if (intent.action == Intent.ACTION_MAIN)
		{
			mServiceBoundContext.attachService()
			if (Build.VERSION.SDK_INT >= 25)
			{
				getSystemService<ShortcutManager>()!!.reportShortcutUsed("toggle")
			}
		}
	}

	override fun onDestroy()
	{
		mServiceBoundContext.detachService()
		super.onDestroy()
	}
}
