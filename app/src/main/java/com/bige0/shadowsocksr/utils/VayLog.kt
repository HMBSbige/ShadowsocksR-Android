package com.bige0.shadowsocksr.utils

import android.util.*
import com.bige0.shadowsocksr.*

object VayLog
{
	private const val DEFAULT_TAG = "VayLog"

	private val LOGGABLE = BuildConfig.DEBUG

	fun d(str: String)
	{
		d(DEFAULT_TAG, str)
	}

	fun d(tag: String, str: String)
	{
		if (LOGGABLE)
		{
			Log.d(tag, str + "")
		}
	}

	fun w(str: String)
	{
		w(DEFAULT_TAG, str)
	}

	fun w(tag: String, str: String)
	{
		if (LOGGABLE)
		{
			Log.w(tag, str + "")
		}
	}

	fun e(str: String)
	{
		e(DEFAULT_TAG, str)
	}

	fun e(tag: String, str: String)
	{
		e(tag, str, null)
	}

	fun e(tag: String, msg: String, e: Throwable?)
	{
		if (LOGGABLE)
		{
			Log.e(tag, msg + "", e)
		}
	}

	fun i(str: String)
	{
		i(DEFAULT_TAG, str + "")
	}

	fun i(tag: String, str: String)
	{
		if (LOGGABLE)
		{
			Log.i(tag, str + "")
		}
	}

	fun v(str: String)
	{
		v(DEFAULT_TAG, str + "")
	}

	fun v(tag: String, str: String)
	{
		if (LOGGABLE)
		{
			Log.v(tag, str + "")
		}
	}
}
