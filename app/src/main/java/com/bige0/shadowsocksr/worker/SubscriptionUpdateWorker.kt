package com.bige0.shadowsocksr.worker

import android.content.*
import androidx.work.*
import com.bige0.shadowsocksr.*
import com.bige0.shadowsocksr.R
import com.bige0.shadowsocksr.network.ssrsub.*
import com.bige0.shadowsocksr.utils.*
import java.util.concurrent.*

class SubscriptionUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params){
	override fun doWork(): Result
	{
		if (ShadowsocksApplication.app.settings.getInt(Constants.Key.ssrsub_autoupdate, 0) == 1)
		{
			val subs = ShadowsocksApplication.app.ssrSubManager.allSSRSubs
			SubUpdateHelper.instance()
				.updateSub(subs, object : SubUpdateCallback()
				{
					override fun onSuccess(subName: String)
					{
						VayLog.d(TAG, "onRunJob() update sub success!")
						ToastUtils.showShort(applicationContext.getString(R.string.sub_autoupdate_success, subName))
					}

					override fun onFailed()
					{
						VayLog.e(TAG, "onRunJob() update sub failed!")
					}
				})
			return Result.success()
		}
		return Result.retry()
	}

	companion object{
		const val TAG = "SSRSubUpdateWorker"

		fun schedule(appContext: Context):Unit{
			val constraints = Constraints.Builder()
				.setRequiresCharging(true)
				.build()
			val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(1, TimeUnit.DAYS)
				.setConstraints(constraints)
				.build()

			WorkManager.getInstance(appContext).cancelAllWorkByTag(TAG)
			WorkManager.getInstance(appContext).enqueue(request)
		}
	}
}

