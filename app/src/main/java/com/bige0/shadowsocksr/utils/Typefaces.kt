package com.bige0.shadowsocksr.utils

import android.content.*
import android.graphics.*
import com.bige0.shadowsocksr.*
import java.util.*

object Typefaces
{
	private const val TAG = "Typefaces"
	private val cache = Hashtable<String, Typeface>()

	operator fun get(c: Context, assetPath: String): Typeface?
	{
		synchronized(cache) {
			if (!cache.containsKey(assetPath))
			{
				try
				{
					cache[assetPath] = Typeface.createFromAsset(c.assets, assetPath)
				}
				catch (e: Exception)
				{
					VayLog.e(TAG, """Could not get typeface '$assetPath' because ${e.message}""")
					ShadowsocksApplication.app.track(e)
					return null
				}
			}
			return cache[assetPath]
		}
	}
}