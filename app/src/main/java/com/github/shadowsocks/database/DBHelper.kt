package com.github.shadowsocks.database

import android.content.*
import android.content.pm.*
import android.database.sqlite.*
import android.text.*
import com.github.shadowsocks.*
import com.github.shadowsocks.utils.*
import com.j256.ormlite.android.apptools.*
import com.j256.ormlite.dao.*
import com.j256.ormlite.support.*
import com.j256.ormlite.table.*
import java.sql.*
import java.util.*

class DBHelper(private val context: Context) : OrmLiteSqliteOpenHelper(context, PROFILE, null, VERSION)
{
	internal lateinit var profileDao: Dao<Profile, Int>
	internal lateinit var ssrsubDao: Dao<SSRSub, Int>
	private var apps: List<ApplicationInfo>? = null

	init
	{
		try
		{
			profileDao = getDao(Profile::class.java)
		}
		catch (e: SQLException)
		{
			VayLog.e(TAG, "", e)
		}

		try
		{
			ssrsubDao = getDao(SSRSub::class.java)
		}
		catch (e: SQLException)
		{
			VayLog.e(TAG, "", e)
		}

	}

	/**
	 * is all digits
	 */
	private fun isAllDigits(x: String): Boolean
	{
		if (!TextUtils.isEmpty(x))
		{
			for (ch in x.toCharArray())
			{
				val digit = Character.isDigit(ch)
				if (!digit)
				{
					return false
				}
			}
			return true
		}
		return false
	}

	/**
	 * update proxied apps
	 */
	@Synchronized
	private fun updateProxiedApps(context: Context, old: String): String
	{
		if (apps == null)
		{
			apps = context.packageManager.getInstalledApplications(0)
		}

		val uidSet = ArrayList<Int>()
		val split = old.split("|")
			.dropLastWhile { it.isEmpty() }
			.toTypedArray()
		for (item in split)
		{
			if (isAllDigits(item))
			{
				// add to uid list
				uidSet.add(Integer.parseInt(item))
			}
		}
		val sb = StringBuilder()
		for (i in apps!!.indices)
		{
			val ai = apps!![i]
			if (uidSet.contains(ai.uid))
			{
				if (i > 0)
				{
					// adding separator
					sb.append("\n")
				}
				sb.append(ai.packageName)
			}
		}
		return sb.toString()
	}

	override fun onCreate(database: SQLiteDatabase, connectionSource: ConnectionSource?)
	{
		try
		{
			TableUtils.createTable(connectionSource!!, Profile::class.java)
			TableUtils.createTable(connectionSource, SSRSub::class.java)
		}
		catch (e: SQLException)
		{
			VayLog.e(TAG, "onCreate", e)
		}

	}

	override fun onUpgrade(database: SQLiteDatabase, connectionSource: ConnectionSource, oldVersion: Int, newVersion: Int)
	{
		if (oldVersion != newVersion)
		{
			try
			{
				if (oldVersion < 7)
				{
					profileDao.executeRawNoArgs("DROP TABLE IF EXISTS 'profile';")
					onCreate(database, connectionSource)
					return
				}
				if (oldVersion < 8)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN udpdns SMALLINT;")
				}
				if (oldVersion < 9)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN route VARCHAR DEFAULT 'all';")
				}
				else if (oldVersion < 19)
				{
					profileDao.executeRawNoArgs("UPDATE `profile` SET route = 'all' WHERE route IS NULL;")
				}
				if (oldVersion < 10)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN auth SMALLINT;")
				}
				if (oldVersion < 11)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN ipv6 SMALLINT;")
				}
				if (oldVersion < 12)
				{
					profileDao.executeRawNoArgs("BEGIN TRANSACTION;")
					profileDao.executeRawNoArgs("ALTER TABLE `profile` RENAME TO `tmp`;")
					TableUtils.createTable(connectionSource, Profile::class.java)
					profileDao.executeRawNoArgs("INSERT INTO `profile`(id, name, host, localPort, remotePort, password, method, route, proxyApps, bypass," + " udpdns, auth, ipv6, individual) " + "SELECT id, name, host, localPort, remotePort, password, method, route, 1 - global, bypass, udpdns, auth," + " ipv6, individual FROM `tmp`;")
					profileDao.executeRawNoArgs("DROP TABLE `tmp`;")
					profileDao.executeRawNoArgs("COMMIT;")
				}
				else if (oldVersion < 13)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN tx LONG;")
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN rx LONG;")
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN date VARCHAR;")
				}

				if (oldVersion < 15)
				{
					if (oldVersion >= 12)
					{
						profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN userOrder LONG;")
					}
					var i = 0
					for (profile in profileDao.queryForAll())
					{
						if (oldVersion < 14)
						{
							profile.individual = updateProxiedApps(context, profile.individual)
						}
						profile.userOrder = i.toLong()
						profileDao.update(profile)
						i += 1
					}
				}


				if (oldVersion < 16)
				{
					profileDao.executeRawNoArgs("UPDATE `profile` SET route = 'bypass-lan-china' WHERE route = 'bypass-china'")
				}

				if (oldVersion < 19)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN dns VARCHAR DEFAULT '8.8.8.8:53';")
				}

				if (oldVersion < 20)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN china_dns VARCHAR DEFAULT '114.114.114.114:53,223.5.5.5:53';")
				}

				if (oldVersion < 21)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN protocol_param VARCHAR DEFAULT '';")
				}

				if (oldVersion < 22)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN elapsed LONG DEFAULT 0;")
				}

				if (oldVersion < 23)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN tcpdelay LONG DEFAULT 0;")
				}

				if (oldVersion < 24)
				{
					profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN url_group VARCHAR DEFAULT '';")
				}

				if (oldVersion < 25)
				{
					TableUtils.createTable(connectionSource, SSRSub::class.java)
				}

			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "onUpgrade", e)
				ShadowsocksApplication.app.track(e)

				try
				{
					profileDao.executeRawNoArgs("DROP TABLE IF EXISTS 'profile';")
				}
				catch (e1: SQLException)
				{
					VayLog.e(TAG, "onUpgrade", e)
				}

				onCreate(database, connectionSource)
			}

		}
	}

	companion object
	{
		const val PROFILE = "profile.db"
		private const val TAG = "DBHelper"
		private const val VERSION = 25
	}
}
