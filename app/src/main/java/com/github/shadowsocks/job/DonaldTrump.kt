package com.github.shadowsocks.job

import com.evernote.android.job.*
import com.github.shadowsocks.utils.*

@ExperimentalUnsignedTypes
class DonaldTrump : JobCreator
{

	override fun create(tag: String): Job?
	{
		val parts = tag.split(":")
			.dropLastWhile { it.isEmpty() }
			.toTypedArray()

		return when
		{
			AclSyncJob.TAG == parts[0] -> AclSyncJob(parts[1])
			SSRSubUpdateJob.TAG == parts[0] -> SSRSubUpdateJob()
			else ->
			{
				VayLog.w(TAG, "Unknown job tag: $tag")
				null
			}
		}
	}

	companion object
	{
		private const val TAG = "DonaldTrump"
	}
}
