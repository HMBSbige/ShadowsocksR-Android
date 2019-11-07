package com.bige0.shadowsocksr.database

import com.bige0.shadowsocksr.*
import com.bige0.shadowsocksr.utils.*
import java.util.*

class SSRSubManager(private val dbHelper: DBHelper)
{
	private val mSSRSubAddedListeners: MutableList<SSRSubAddedListener>?

	val allSSRSubs: List<SSRSub>
		get()
		{
			return try
			{
				dbHelper.ssrsubDao.query(dbHelper.ssrsubDao.queryBuilder().prepare())
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "getAllSSRSubs", e)
				ShadowsocksApplication.app.track(e)
				emptyList()
			}
		}

	init
	{
		mSSRSubAddedListeners = ArrayList(20)
	}

	fun createSSRSub(p: SSRSub?): SSRSub
	{
		val ssrsub: SSRSub = p ?: SSRSub()
		ssrsub.id = 0

		try
		{
			dbHelper.ssrsubDao.createOrUpdate(ssrsub)
			invokeSSRSubAdded(ssrsub)
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "addSSRSub", e)
			ShadowsocksApplication.app.track(e)
		}

		return ssrsub
	}

	fun delSSRSub(id: Int): Boolean
	{
		return try
		{
			dbHelper.ssrsubDao.deleteById(id)
			true
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "delSSRSub", e)
			ShadowsocksApplication.app.track(e)
			false
		}
	}

	/**
	 * add ssr sub added listener
	 *
	 * @param l callback
	 */
	fun addSSRSubAddedListener(l: SSRSubAddedListener)
	{
		if (mSSRSubAddedListeners == null)
		{
			return
		}

		// adding listener
		if (!mSSRSubAddedListeners.contains(l))
		{
			mSSRSubAddedListeners.add(l)
		}
	}

	/**
	 * remove ssr sub added listener
	 *
	 * @param l callback
	 */
	fun removeSSRSubAddedListener(l: SSRSubAddedListener)
	{
		if (mSSRSubAddedListeners == null || mSSRSubAddedListeners.isEmpty())
		{
			return
		}

		// remove listener
		mSSRSubAddedListeners.remove(l)
	}

	/**
	 * invoke ssr sub added listener
	 *
	 * @param ssrSub ssr sub param
	 */
	private fun invokeSSRSubAdded(ssrSub: SSRSub)
	{
		if (mSSRSubAddedListeners == null || mSSRSubAddedListeners.isEmpty())
		{
			return
		}

		// iteration invoke listener
		for (l in mSSRSubAddedListeners)
		{
			l.onSSRSubAdded(ssrSub)
		}
	}

	interface SSRSubAddedListener
	{
		/**
		 * ssr sub added
		 *
		 * @param ssrSub ssr sub object
		 */
		fun onSSRSubAdded(ssrSub: SSRSub)
	}

	companion object
	{
		private const val TAG = "SSRSubManager"
	}
}
