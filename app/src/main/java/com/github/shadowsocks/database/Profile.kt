package com.github.shadowsocks.database

/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2013 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

import android.util.Base64

import com.j256.ormlite.field.DataType
import com.j256.ormlite.field.DatabaseField

import java.util.Date
import java.util.Locale

class Profile
{

	@DatabaseField
	val date: Date = java.util.Date()
	@DatabaseField(generatedId = true)
	var id = 0
	@DatabaseField
	var name = "ShadowsocksR"
	@DatabaseField
	var host = "1.1.1.1"
	@DatabaseField
	var localPort = 1080
	@DatabaseField
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
	var route = "all"
	@DatabaseField
	var proxyApps = false
	@DatabaseField
	var bypass = false
	@DatabaseField
	var udpdns = false
	@DatabaseField
	var url_group = "ShadowsocksR"
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
	var tcpdelay: Long = 0
	@DatabaseField
	var userOrder: Long = 0

	/**
	 * is method unsafe
	 */
	val isMethodUnsafe: Boolean
		get() = "table".equals(method, ignoreCase = true) || "rc4".equals(method, ignoreCase = true)

	override fun toString(): String
	{
		val result = Base64.encodeToString(String.format(Locale.ENGLISH, "%s:%d:%s:%s:%s:%s/?obfsparam=%s&protoparam=%s&remarks=%s&group=%s", host, remotePort, protocol, method, obfs, Base64.encodeToString(String.format(Locale.ENGLISH, "%s", password).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP), Base64.encodeToString(String.format(Locale.ENGLISH, "%s", obfs_param).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP), Base64.encodeToString(String.format(Locale.ENGLISH, "%s", protocol_param).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP), Base64.encodeToString(String.format(Locale.ENGLISH, "%s", name).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP), Base64.encodeToString(String.format(Locale.ENGLISH, "%s", url_group).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP)).toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE or Base64.NO_WRAP)
		return "ssr://$result"
	}
}
