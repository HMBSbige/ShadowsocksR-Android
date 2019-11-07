package com.bige0.shadowsocksr.utils

import com.bige0.shadowsocksr.R
import com.bige0.shadowsocksr.ShadowsocksApplication
import java.lang.System
import java.text.*

object TrafficMonitor
{
	// Bytes per second
	var txRate = 0L
	var rxRate = 0L

	// Bytes for the current session
	var txTotal = 0L
	var rxTotal = 0L

	// Bytes for the last query
	var txLast = 0L
	var rxLast = 0L
	var timestampLast = 0L
	var dirty = true

	private val units = arrayOf("KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB", "BB", "NB", "DB", "CB")
	private val numberFormat = DecimalFormat("@@@")

	fun formatTraffic(size: Long): String
	{
		var n = size.toDouble()
		var i = -1
		while (n >= 1000)
		{
			n /= 1024.0
			++i
		}
		return if (i < 0)
		{
			"$size ${ShadowsocksApplication.app.resources.getQuantityString(R.plurals.bytes, size.toInt())}"
		}
		else
		{
			"${numberFormat.format(n)} ${units[i]}"
		}
	}

	fun updateRate(): Boolean
	{
		val now = System.currentTimeMillis()
		val delta = now - timestampLast
		var updated = false
		if (delta != 0L)
		{
			if (dirty)
			{
				txRate = (txTotal - txLast) * 1000 / delta
				rxRate = (rxTotal - rxLast) * 1000 / delta
				txLast = txTotal
				rxLast = rxTotal
				dirty = false
				updated = true
			}
			else
			{
				if (txRate != 0L)
				{
					txRate = 0
					updated = true
				}
				if (rxRate != 0L)
				{
					rxRate = 0
					updated = true
				}
			}
			timestampLast = now
		}
		return updated
	}

	fun update(tx: Long, rx: Long)
	{
		if (txTotal != tx)
		{
			txTotal = tx
			dirty = true
		}

		if (rxTotal != rx)
		{
			rxTotal = rx
			dirty = true
		}
	}

	fun reset()
	{
		txRate = 0
		rxRate = 0
		txTotal = 0
		rxTotal = 0
		txLast = 0
		rxLast = 0
		dirty = true
	}
}
