package com.bige0.shadowsocksr

import android.app.*
import android.content.*
import android.content.pm.*
import android.os.*
import androidx.core.content.*
import androidx.core.content.pm.*
import androidx.core.graphics.drawable.*
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
						}
						Constants.State.CONNECTED -> Utils.stopSsService(this)
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

		when (intent.action)
		{
			Intent.ACTION_CREATE_SHORTCUT ->
			{
				setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, ShortcutInfoCompat.Builder(this, "toggle").setIntent(Intent(this, QuickToggleShortcut::class.java).setAction(Intent.ACTION_MAIN)).setIcon(IconCompat.createWithResource(this, R.drawable.ic_qu_shadowsocks_launcher)).setShortLabel(getString(R.string.quick_toggle)).build()))
				finish()
			}
			else ->
			{
				mServiceBoundContext.attachService()
				if (Build.VERSION.SDK_INT >= 25)
				{
					getSystemService<ShortcutManager>()!!.reportShortcutUsed("toggle")
				}
			}
		}
	}

	override fun onDestroy()
	{
		mServiceBoundContext.detachService()
		super.onDestroy()
	}
}
