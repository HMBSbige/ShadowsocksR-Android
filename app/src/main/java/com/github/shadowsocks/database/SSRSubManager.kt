package com.github.shadowsocks.database


import com.github.shadowsocks.ShadowsocksApplication
import com.github.shadowsocks.utils.VayLog

import java.util.ArrayList

class SSRSubManager(private val dbHelper: DBHelper)
{
	private val mSSRSubAddedListeners: MutableList<SSRSubAddedListener>?

	val firstSSRSub: SSRSub?
		get()
		{
			try
			{
				val result = dbHelper.ssrsubDao.query(dbHelper.ssrsubDao.queryBuilder().limit(1L).prepare())
				return if (result != null && !result.isEmpty())
				{
					result[0]
				}
				else
				{
					null
				}
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "getAllSSRSubs", e)
				ShadowsocksApplication.app.track(e)
				return null
			}

		}

	val allSSRSubs: List<SSRSub>?
		get()
		{
			try
			{
				return dbHelper.ssrsubDao.query(dbHelper.ssrsubDao.queryBuilder().prepare())
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "getAllSSRSubs", e)
				ShadowsocksApplication.app.track(e)
				return null
			}

		}

	init
	{
		mSSRSubAddedListeners = ArrayList(20)
	}

	fun createSSRSub(p: SSRSub?): SSRSub
	{
		val ssrsub: SSRSub
		if (p == null)
		{
			ssrsub = SSRSub()
		}
		else
		{
			ssrsub = p
		}
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

	fun updateSSRSub(ssrsub: SSRSub): Boolean
	{
		try
		{
			dbHelper.ssrsubDao.update(ssrsub)
			return true
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "updateSSRSub", e)
			ShadowsocksApplication.app.track(e)
			return false
		}

	}

	fun getSSRSub(id: Int): SSRSub?
	{
		try
		{
			return dbHelper.ssrsubDao.queryForId(id)
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "getSSRSub", e)
			ShadowsocksApplication.app.track(e)
			return null
		}

	}

	fun delSSRSub(id: Int): Boolean
	{
		try
		{
			dbHelper.ssrsubDao.deleteById(id)
			return true
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "delSSRSub", e)
			ShadowsocksApplication.app.track(e)
			return false
		}

	}

	fun createDefault(): SSRSub
	{
		val ssrSub = SSRSub()
		ssrSub.url = "https://raw.githubusercontent.com/HMBSbige/Text_Translation/master/ShadowsocksR/freenodeplain.txt"
		ssrSub.url_group = "ShadowsocksR"
		return createSSRSub(ssrSub)
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

		private val TAG = SSRSubManager::class.java.simpleName
	}
}
