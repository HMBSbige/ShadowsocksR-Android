package com.bige0.shadowsocksr.job

import com.bige0.shadowsocksr.*
import com.bige0.shadowsocksr.utils.*
import com.evernote.android.job.*
import java.io.*
import java.net.*
import java.util.concurrent.*

class AclSyncJob(private val route: String) : Job()
{
	override fun onRunJob(params: Params): Result
	{
		val filename = "$route.acl"
		var inputStream: InputStream? = null
		try
		{
			if ("self" == route)
			{

				inputStream = URL("https://raw.githubusercontent.com/HMBSbige/Text_Translation/master/ShadowsocksR/$filename").openConnection()
					.getInputStream()
				IOUtils.writeString(ShadowsocksApplication.app.applicationInfo.dataDir + '/'.toString() + filename, IOUtils.readString(inputStream!!))
			}
			return Result.SUCCESS
		}
		catch (e: IOException)
		{
			VayLog.e(TAG, "onRunJob", e)
			ShadowsocksApplication.app.track(e)
			return Result.RESCHEDULE
		}
		catch (e: Exception)
		{
			// unknown failures, probably shouldn't retry
			VayLog.e(TAG, "onRunJob", e)
			ShadowsocksApplication.app.track(e)
			return Result.FAILURE
		}
		finally
		{
			try
			{
				inputStream?.close()
			}
			catch (e: IOException)
			{
				VayLog.e(TAG, "onRunJob", e)
				ShadowsocksApplication.app.track(e)
			}

		}
	}

	companion object
	{
		const val TAG = "AclSyncJob"

		fun schedule(route: String): Int
		{
			return JobRequest.Builder("$TAG:$route")
				.setExecutionWindow(1, TimeUnit.DAYS.toMillis(28))
				.setRequirementsEnforced(true)
				.setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
				.setRequiresCharging(true)
				.setUpdateCurrent(true)
				.build()
				.schedule()
		}
	}
}
