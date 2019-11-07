package com.bige0.shadowsocksr.utils

import android.util.Base64

object Base64
{
	fun decodeUrlSafe(str: String): String
	{
		val data = str.replace('/', '_')
			.replace('+', '-')
			.replace("=", "")
		val byte = Base64.decode(data, Base64.URL_SAFE or Base64.NO_PADDING)
		return byte.toString(Charsets.UTF_8)
	}

	fun encode(bytes: ByteArray): String
	{
		return Base64.encode(bytes, Base64.DEFAULT)
			.toString(Charsets.UTF_8)
	}
}
