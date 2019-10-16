package com.github.shadowsocks.utils

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
}
