package com.bige0.shadowsocksr.utils

import android.content.*
import android.os.*
import com.bige0.shadowsocksr.*

class TaskerSettings(bundle: Bundle)
{
	var switchOn = false
	var profileId = 0

	init
	{
		switchOn = bundle.getBoolean(KEY_SWITCH_ON, true)
		profileId = bundle.getInt(KEY_PROFILE_ID, -1)
	}

	fun toIntent(context: Context): Intent
	{
		val bundle = Bundle()
		if (!switchOn)
		{
			bundle.putBoolean(KEY_SWITCH_ON, false)
		}

		if (profileId >= 0)
		{
			bundle.putInt(KEY_PROFILE_ID, profileId)
		}

		val p = ShadowsocksApplication.app.profileManager.getProfile(profileId)
		val value = if (p != null)
		{
			val strId = if (switchOn) R.string.start_service else R.string.stop_service
			context.getString(strId, p.name)
		}
		else
		{
			val strId = if (switchOn) R.string.start_service_default else R.string.stop
			context.getString(strId)
		}
		val intent = Intent()
		intent.putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_BUNDLE, bundle)
		intent.putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_STRING_BLURB, value)
		return intent
	}

	companion object
	{
		private const val KEY_SWITCH_ON = "switch_on"
		private const val KEY_PROFILE_ID = "profile_id"

		fun fromIntent(intent: Intent): TaskerSettings
		{
			val bundle = if (intent.hasExtra(com.twofortyfouram.locale.api.Intent.EXTRA_BUNDLE))
			{
				intent.getBundleExtra(com.twofortyfouram.locale.api.Intent.EXTRA_BUNDLE)
			}
			else
			{
				Bundle.EMPTY
			}
			return TaskerSettings(bundle!!)
		}
	}
}
