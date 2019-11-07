package com.bige0.shadowsocksr.database

import android.content.*
import android.database.sqlite.*
import com.bige0.shadowsocksr.*
import com.bige0.shadowsocksr.utils.*
import com.j256.ormlite.android.apptools.*
import com.j256.ormlite.dao.*
import com.j256.ormlite.support.*
import com.j256.ormlite.table.*
import java.sql.*

class DBHelper(context: Context) : OrmLiteSqliteOpenHelper(context, PROFILE, null, VERSION)
{
	internal lateinit var profileDao: Dao<Profile, Int>
	internal lateinit var ssrsubDao: Dao<SSRSub, Int>

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
		private const val VERSION = 1
	}
}
