package com.bige0.shadowsocksr.database

import android.util.Base64
import com.bige0.shadowsocksr.utils.*
import com.j256.ormlite.field.*
import java.util.*

class Profile
{
	@DatabaseField
	val date: Date = Date()
	@DatabaseField(generatedId = true)
	var id = 0
	@DatabaseField
	var name = "ShadowsocksR"
	@DatabaseField
	var host = Constants.DefaultHostName
	@DatabaseField
	var localPort = 1080
	@DatabaseField
	//TODO:UShort
	var remotePort = 8388
	@DatabaseField
	var password = ""
	@DatabaseField
	var protocol = "origin"
	@DatabaseField
	var protocol_param = ""
	@DatabaseField
	var obfs = "plain"
	@DatabaseField
	var obfs_param = ""
	@DatabaseField
	var method = "aes-256-cfb"
	@DatabaseField
	var route = "bypass-lan-china"
	@DatabaseField
	var proxyApps = false
	@DatabaseField
	var bypass = false
	@DatabaseField
	var udpdns = false
	@DatabaseField
	var url_group = "Default Group"
	@DatabaseField
	var dns = "8.8.8.8:53"
	@DatabaseField
	var china_dns = "114.114.114.114:53,223.5.5.5:53"
	@DatabaseField
	var ipv6 = false
	@DatabaseField(dataType = DataType.LONG_STRING)
	var individual = ""
	@DatabaseField
	var tx: Long = 0
	@DatabaseField
	var rx: Long = 0
	@DatabaseField
	var elapsed: Long = 0
	@DatabaseField
	var userOrder: Long = 0

	/**
	 * is method unsafe
	 */
	val isMethodUnsafe: Boolean
		get() = "table".equals(method, ignoreCase = true) || "rc4".equals(method, ignoreCase = true)

	override fun toString(): String
	{
		//TODO
		val result = Base64.encodeToString(String.format(Locale.ENGLISH, "%s:%d:%s:%s:%s:%s/?obfsparam=%s&protoparam=%s&remarks=%s&group=%s", host, remotePort, protocol, method, obfs, Base64.encodeToString(String.format(Locale.ENGLISH, "%s", password).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP), Base64.encodeToString(String.format(Locale.ENGLISH, "%s", obfs_param).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP), Base64.encodeToString(String.format(Locale.ENGLISH, "%s", protocol_param).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP), Base64.encodeToString(String.format(Locale.ENGLISH, "%s", name).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP), Base64.encodeToString(String.format(Locale.ENGLISH, "%s", url_group).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP)).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP)
		return "ssr://$result"
	}
}
