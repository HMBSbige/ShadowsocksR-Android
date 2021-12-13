package com.bige0.shadowsocksr.worker

import android.content.*
import androidx.work.*
import androidx.work.multiprocess.*
import com.bige0.shadowsocksr.*
import com.bige0.shadowsocksr.utils.*
import java.io.*
import java.net.*
import java.util.concurrent.*

class AclSyncWorker(context: Context, params: WorkerParameters)
	: Worker(context, params){
	override fun doWork(): Result
	{
		val route = inputData.getString("route")
		val filename = "$route.acl"
		var inputStream: InputStream? = null
		try
		{
			if ("self" != route)
			{
			inputStream = URL("https://raw.githubusercontent.com/HMBSbige/Text_Translation/master/ShadowsocksR/$filename").openConnection()
				.getInputStream()
			IOUtils.writeString(ShadowsocksApplication.app.applicationInfo.dataDir + '/'.toString() + filename, IOUtils.readString(inputStream!!))

				//TODO:may use china_ip_list.txt & gfwlist.txt to construct acl files as they are updated more frequently in repo
			}
			return Result.success()
		}
		catch (e: IOException)
		{
			VayLog.e(TAG, "onRunJob", e)
			ShadowsocksApplication.app.track(e)
			return Result.retry()
		}
		catch (e: Exception)
		{
			// unknown failures, probably shouldn't retry
			VayLog.e(TAG, "onRunJob", e)
			ShadowsocksApplication.app.track(e)
			return Result.failure()
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
		const val TAG = "AclSyncWorker"

		fun schedule(route: String, appContext: Context) : Unit{
			val data = Data.Builder().putString("route", route).build()
			val constraints = Constraints.Builder()
				.setRequiredNetworkType(NetworkType.UNMETERED)
				.setRequiresCharging(true)
				.build()

			//Update Acl rules per month, with a 5 day flexInterval for system to schedule
			val request = PeriodicWorkRequestBuilder<AclSyncWorker>(30, TimeUnit.DAYS,
																	5, TimeUnit.DAYS)
				.setInputData(data)
				.addTag("${TAG}:$route")
				.setConstraints(constraints)
				.build()

			//Cancel pending updates with same route
			RemoteWorkManager.getInstance(appContext).cancelAllWorkByTag("${TAG}:$route")

			RemoteWorkManager.getInstance(appContext).enqueue(request)
		}
	}
}
