package com.bige0.shadowsocksr

import android.content.*
import android.content.pm.*
import android.os.*
import androidx.core.content.*
import com.bige0.shadowsocksr.ShadowsocksApplication.Companion.app
import com.bige0.shadowsocksr.utils.*

class BootReceiver : BroadcastReceiver()
{
	override fun onReceive(context: Context, intent: Intent)
	{
		val doStart = when (intent.action)
		{
			Intent.ACTION_BOOT_COMPLETED -> !Utils.directBootSupported
			Intent.ACTION_LOCKED_BOOT_COMPLETED -> Utils.directBootSupported
			else -> Utils.directBootSupported || Build.VERSION.SDK_INT >= 24 && app.getSystemService<UserManager>()?.isUserUnlocked != false
		}
		if (doStart) Utils.startSsService(context)
	}

	companion object
	{
		private val componentName by lazy { ComponentName(app, BootReceiver::class.java) }
		var enabled: Boolean
			get() = app.packageManager.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
			set(value) = app.packageManager.setComponentEnabledSetting(componentName, if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
			else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
	}
}
