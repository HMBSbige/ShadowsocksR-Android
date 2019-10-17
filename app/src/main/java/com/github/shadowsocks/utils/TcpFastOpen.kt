package com.github.shadowsocks.utils

import java.io.*

object TcpFastOpen
{
	private const val path = "/proc/sys/net/ipv4/tcp_fastopen"

	/**
	 * Is kernel version >= 3.7.1.
	 */
	val supported by lazy {
		if (File(path).canRead()) return@lazy true
		val match = """^(\d+)\.(\d+)\.(\d+)""".toRegex()
			.find(System.getProperty("os.version") ?: "")
		if (match == null) false
		else when (match.groupValues[1].toInt())
		{
			in Int.MIN_VALUE..2 -> false
			3 -> when (match.groupValues[2].toInt())
			{
				in Int.MIN_VALUE..6 -> false
				7 -> match.groupValues[3].toInt() >= 1
				else -> true
			}
			else -> true
		}
	}

	val sendEnabled: Boolean
		get()
		{
			val file = File(path)
			// File.readText doesn't work since this special file will return length 0
			// on Android containers like Chrome OS, this file does not exist so we simply judge by the kernel version
			return if (file.canRead()) file.bufferedReader().use { it.readText() }.trim().toInt() and 1 > 0 else supported
		}

	fun enabled(value: Boolean): String
	{
		val valueFlag = if (value) 3 else 0
		return try
		{
			ProcessBuilder("su", "-c", "echo $valueFlag > $path").redirectErrorStream(true)
				.start()
				.inputStream.bufferedReader()
				.readText()
		}
		catch (e: IOException)
		{
			e.readableMessage
		}
	}
}
