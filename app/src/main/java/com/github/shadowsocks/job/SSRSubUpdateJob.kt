package com.github.shadowsocks.job

import com.evernote.android.job.*
import com.github.shadowsocks.*
import com.github.shadowsocks.R
import com.github.shadowsocks.network.ssrsub.*
import com.github.shadowsocks.utils.*
import java.util.concurrent.*

class SSRSubUpdateJob : Job()
{
	override fun onRunJob(params: Params): Result
	{
		if (ShadowsocksApplication.app.settings.getInt(Constants.Key.ssrsub_autoupdate, 0) == 1)
		{
			val subs = ShadowsocksApplication.app.ssrsubManager.allSSRSubs
			SubUpdateHelper.instance()
				.updateSub(subs!!, object : SubUpdateCallback()
				{
					override fun onSuccess(subName: String)
					{
						VayLog.d(TAG, "onRunJob() update sub success!")
						ToastUtils.showShort(context.getString(R.string.sub_autoupdate_success, subName))
					}

					override fun onFailed()
					{
						VayLog.e(TAG, "onRunJob() update sub failed!")
					}
				})
			return Result.SUCCESS
		}
		else
		{
			return Result.RESCHEDULE
		}
	}

	companion object
	{
		const val TAG = "SSRSubUpdateJob"

		fun schedule(): Int
		{
			return JobRequest.Builder(TAG)
				.setPeriodic(TimeUnit.HOURS.toMillis(1))
				.setRequirementsEnforced(true)
				.setRequiresCharging(false)
				.setUpdateCurrent(true)
				.build()
				.schedule()
		}
	}
}
