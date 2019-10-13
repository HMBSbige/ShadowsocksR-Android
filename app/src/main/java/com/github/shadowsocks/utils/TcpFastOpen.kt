package com.github.shadowsocks.utils

import eu.chainfire.libsuperuser.*
import java.io.*

object TcpFastOpen
{
	private val p = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)")

	const val path = "/proc/sys/net/ipv4/tcp_fastopen"

	/**
	 * Is kernel version >= 3.7.1.
	 */
	fun supported(): Boolean
	{
		val m = p.find(System.getProperty("os.version")!!)
		if (m != null)
		{
			val kernel = Integer.parseInt(m.groups[1]!!.value)
			return when
			{
				kernel < 3 -> false
				kernel > 3 -> true
				else ->
				{
					val major = Integer.parseInt(m.groups[2]!!.value)
					when
					{
						major < 7 -> false
						major > 7 -> true
						else -> Integer.parseInt(m.groups[3]!!.value) >= 1
					}
				}
			}
		}
		return false
	}

	fun sendEnabled(): Boolean
	{
		val file = File(path)
		return file.canRead() && Integer.parseInt(Utils.readAllLines(file)!!) and 1 > 0
	}

	fun enabled(value: Boolean): String?
	{
		if (sendEnabled() != value)
		{
			val suAvailable = Shell.SU.available()
			if (suAvailable)
			{
				val valueFlag = if (value) 3 else 0
				val cmds = arrayOf("if echo $valueFlag > /proc/sys/net/ipv4/tcp_fastopen; then", "  echo Success.", "else", "  echo Failed.")

				val res = Shell.run("su", cmds, null, true)
				if (res != null && res.isNotEmpty())
				{
					return Utils.makeString(res, "\n")
				}
			}
		}

		return null
	}
}
