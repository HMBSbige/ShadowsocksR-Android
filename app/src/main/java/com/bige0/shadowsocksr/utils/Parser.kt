package com.bige0.shadowsocksr.utils

import com.bige0.shadowsocksr.*
import com.bige0.shadowsocksr.database.*
import java.net.*
import java.util.*

object Parser
{
	const val TAG = "Parser"

	private const val pattern_ss_regex = "(?i)ss://([A-Za-z0-9+-/=_]+)(#(.+))?"
	private const val decodedPattern_ss_regex = "(?i)^((.+?)(-auth)??:(.*)@(.+?):(\\d+?))$"

	private const val pattern_ssr_regex = "(?i)ssr://([A-Za-z0-9+-/=_]+)"
	private const val decodedPattern_ssr_regex = "(?i)^((.+):(\\d+?):(.*):(.+):(.*):([^/]+))"
	private const val decodedPattern_ssr_obfsparam_regex = "(?i)[?&]obfsparam=([A-Za-z0-9+-/=_]*)"
	private const val decodedPattern_ssr_remarks_regex = "(?i)[?&]remarks=([A-Za-z0-9+-/=_]*)"
	private const val decodedPattern_ssr_protocolparam_regex = "(?i)[?&]protoparam=([A-Za-z0-9+-/=_]*)"
	private const val decodedPattern_ssr_groupparam_regex = "(?i)[?&]group=([A-Za-z0-9+-/=_]*)"

	fun findAllSs(data: CharSequence): List<Profile>
	{
		val pattern = Regex(pattern_ss_regex)
		val decodedPattern = Regex(decodedPattern_ss_regex)

		val input: CharSequence = if (data.isNotEmpty()) data else ""
		val list = ArrayList<Profile>()
		try
		{
			pattern.findAll(input)
				.forEach {
					val ss = decodedPattern.matchEntire(Base64.decodeUrlSafe(it.groupValues[1]))
					if (ss != null)
					{
						val profile = Profile()
						val port = ss.groupValues[6].toUShortOrNull()
						if (port != null)
						{
							profile.remotePort = port.toInt()
						}
						else
						{
							return@forEach
						}
						profile.method = ss.groupValues[2].toLowerCase(Locale.ENGLISH)
						if (ss.groups[3] != null)
						{
							profile.protocol = "verify_sha1"
						}
						profile.password = ss.groupValues[4]
						profile.name = ss.groupValues[5]
						profile.host = profile.name
						if (it.groups[2] != null)
						{
							profile.name = URLDecoder.decode(it.groupValues[3], "utf-8")
						}
						list.add(profile)
					}
				}
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "findAllSs", e)
			ShadowsocksApplication.app.track(e)
		}
		finally
		{
			return list
		}

	}

	fun findAllSsr(data: CharSequence): MutableList<Profile>
	{
		val input = if (data.isNotEmpty()) data else ""
		val list = ArrayList<Profile>()
		try
		{
			Regex(pattern_ssr_regex).findAll(input)
				.forEach {
					val uri = Base64.decodeUrlSafe(it.groupValues[1])
					val ss = Regex(decodedPattern_ssr_regex).find(uri)
					if (ss != null)
					{
						val profile = Profile()
						val port = ss.groupValues[3].toUShortOrNull()
						if (port != null)
						{
							profile.remotePort = port.toInt()
						}
						else
						{
							return@forEach
						}
						profile.host = ss.groupValues[2].toLowerCase(Locale.ENGLISH)
						profile.protocol = ss.groupValues[4].toLowerCase(Locale.ENGLISH)
						profile.method = ss.groupValues[5].toLowerCase(Locale.ENGLISH)
						profile.obfs = ss.groupValues[6].toLowerCase(Locale.ENGLISH)
						profile.password = Base64.decodeUrlSafe(ss.groupValues[7])

						if (profile.obfs == "tls1.2_ticket_fastauth")
						{
							profile.obfs = "tls1.2_ticket_auth"
						}
						var param = Regex(decodedPattern_ssr_obfsparam_regex).find(uri)
						if (param != null)
						{
							profile.obfs_param = Base64.decodeUrlSafe(param.groupValues[1])
						}

						param = Regex(decodedPattern_ssr_protocolparam_regex).find(uri)
						if (param != null)
						{
							profile.protocol_param = Base64.decodeUrlSafe(param.groupValues[1])
						}

						param = Regex(decodedPattern_ssr_remarks_regex).find(uri)
						if (param != null)
						{
							profile.name = Base64.decodeUrlSafe(param.groupValues[1])
						}
						else
						{
							profile.name = profile.host
						}

						param = Regex(decodedPattern_ssr_groupparam_regex).find(uri)
						if (param != null)
						{
							profile.url_group = Base64.decodeUrlSafe(param.groupValues[1])
						}

						list.add(profile)
					}
				}
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "findAllSsr", e)
			ShadowsocksApplication.app.track(e)
		}
		finally
		{
			return list
		}
	}
}
