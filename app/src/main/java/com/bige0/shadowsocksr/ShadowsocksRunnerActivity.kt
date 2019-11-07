package com.bige0.shadowsocksr

import android.app.*
import android.content.*
import android.net.*
import android.os.*
import com.bige0.shadowsocksr.utils.*

class ShadowsocksRunnerActivity : Activity()
{
	companion object
	{
		private const val TAG = "ShadowsocksRunnerActivity"
		private const val REQUEST_CONNECT = 1
	}

	private val handler = Handler()

	private var receiver: BroadcastReceiver? = null

	private lateinit var mServiceBoundContext: ServiceBoundContext

	override fun attachBaseContext(newBase: Context)
	{
		super.attachBaseContext(newBase)
		mServiceBoundContext = object : ServiceBoundContext(newBase)
		{
			override fun onServiceConnected()
			{
				handler.postDelayed({
										if (bgService != null)
										{
											startBackgroundService()
										}
									}, 1000)
			}
		}
	}

	private fun startBackgroundService()
	{
		val intent = VpnService.prepare(this@ShadowsocksRunnerActivity)
		if (intent != null)
		{
			startActivityForResult(intent, REQUEST_CONNECT)
		}
		else
		{
			onActivityResult(REQUEST_CONNECT, RESULT_OK, null)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
		if (km.isKeyguardLocked)
		{
			val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
			receiver = object : BroadcastReceiver()
			{
				override fun onReceive(context: Context, intent: Intent)
				{
					when (intent.action)
					{
						Intent.ACTION_USER_PRESENT -> mServiceBoundContext.attachService()
					}
				}
			}
			registerReceiver(receiver, filter)
		}
		else
		{
			mServiceBoundContext.attachService()
		}
		finish()
	}

	override fun onDestroy()
	{
		super.onDestroy()
		mServiceBoundContext.detachService()
		if (receiver != null)
		{
			unregisterReceiver(receiver)
			receiver = null
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
	{
		super.onActivityResult(requestCode, resultCode, data)
		if (resultCode == RESULT_OK)
		{
			if (mServiceBoundContext.bgService != null)
			{
				try
				{
					mServiceBoundContext.bgService!!.use(ShadowsocksApplication.app.profileId())
				}
				catch (e: RemoteException)
				{
					e.printStackTrace()
				}
			}
		}
		else
		{
			VayLog.e(TAG, "Failed to start VpnService")
		}
		finish()
	}
}
