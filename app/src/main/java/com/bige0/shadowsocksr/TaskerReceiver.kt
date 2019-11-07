package com.bige0.shadowsocksr

import android.content.*
import com.bige0.shadowsocksr.utils.*

class TaskerReceiver : BroadcastReceiver()
{
	override fun onReceive(context: Context, intent: Intent)
	{
		val settings = TaskerSettings.fromIntent(intent)
		val profile = ShadowsocksApplication.app.profileManager.getProfile(settings.profileId)

		if (profile != null)
		{
			ShadowsocksApplication.app.switchProfile(settings.profileId)
		}

		if (settings.switchOn)
		{
			Utils.startSsService(context)
		}
		else
		{
			Utils.stopSsService(context)
		}
	}
}
