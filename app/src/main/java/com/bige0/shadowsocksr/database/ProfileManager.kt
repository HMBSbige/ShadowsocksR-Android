package com.bige0.shadowsocksr.database

import com.bige0.shadowsocksr.*
import com.bige0.shadowsocksr.utils.*
import java.sql.*
import java.util.*

class ProfileManager(private val dbHelper: DBHelper)
{

	private val mProfileAddedListeners: MutableList<ProfileAddedListener>?

	/**
	 * get first profile
	 *
	 * @return get failed return null.
	 */
	// list is empty, return null;
	val firstProfile: Profile?
		get()
		{
			return try
			{
				val result = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().limit(1L).prepare())
				if (result != null && result.isNotEmpty())
				{
					result[0]
				}
				else null
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "getFirstProfile", e)
				ShadowsocksApplication.app.track(e)
				null
			}

		}

	val allProfiles: List<Profile>
		get()
		{
			return try
			{
				dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("userOrder", true).prepare())
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "getAllProfiles", e)
				ShadowsocksApplication.app.track(e)
				emptyList()
			}
		}

	/**
	 * get all profiles by elapsed
	 *
	 * @return get failed return null.
	 */
	// merge list
	val allProfilesByElapsed: List<Profile>
		get()
		{
			return try
			{
				val notlist = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("elapsed", true).where().not().eq("elapsed", 0).prepare())
				val eqList = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("elapsed", true).where().eq("elapsed", 0).prepare())
				val result = ArrayList<Profile>()
				result.addAll(notlist)
				result.addAll(eqList)
				result
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "getAllProfilesByElapsed", e)
				ShadowsocksApplication.app.track(e)
				emptyList()
			}
		}

	val groupNames: List<String>
		get()
		{
			try
			{
				val groupdistinktprofile = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().selectColumns("url_group").distinct().prepare())
				val groupnames = ArrayList<String>()
				for (profile in groupdistinktprofile)
				{
					groupnames.add(profile.url_group)
				}
				return groupnames
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "getAllProfilesByGroup", e)
				ShadowsocksApplication.app.track(e)
				return emptyList()
			}
		}

	init
	{
		mProfileAddedListeners = ArrayList(20)
	}

	/**
	 * create profile
	 */
	fun createProfile(p: Profile? = null): Profile
	{
		val profile: Profile = p ?: Profile()
		profile.id = 0
		val oldProfile = ShadowsocksApplication.app.currentProfile()
		if (oldProfile != null)
		{
			// Copy Feature Settings from old profile
			profile.route = oldProfile.route
			profile.ipv6 = oldProfile.ipv6
			profile.proxyApps = oldProfile.proxyApps
			profile.bypass = oldProfile.bypass
			profile.individual = oldProfile.individual
			profile.udpdns = oldProfile.udpdns
		}
		try
		{
			val last = dbHelper.profileDao.queryRaw(dbHelper.profileDao.queryBuilder().selectRaw("MAX(userOrder)").prepareStatementString())
				.firstResult
			// set user order
			if (last != null && last.size == 1 && last[0] != null)
			{
				profile.userOrder = (Integer.parseInt(last[0]) + 1).toLong()
			}
			// create or update
			dbHelper.profileDao.createOrUpdate(profile)
			invokeProfileAdded(profile)
		}
		catch (e: SQLException)
		{
			VayLog.e(TAG, "createProfile", e)
			ShadowsocksApplication.app.track(e)
		}

		return profile
	}

	/**
	 * create profile dr
	 *
	 * @param p profile
	 */
	fun createProfileDr(p: Profile?): Profile
	{
		val profile: Profile = p ?: Profile()
		profile.id = 0
		val oldProfile = ShadowsocksApplication.app.currentProfile()
		if (oldProfile != null)
		{
			// Copy Feature Settings from old profile
			profile.route = oldProfile.route
			profile.ipv6 = oldProfile.ipv6
			profile.proxyApps = oldProfile.proxyApps
			profile.bypass = oldProfile.bypass
			profile.individual = oldProfile.individual
			profile.udpdns = oldProfile.udpdns
			profile.dns = oldProfile.dns
			profile.china_dns = oldProfile.china_dns
		}

		try
		{
			val last = dbHelper.profileDao.queryRaw(dbHelper.profileDao.queryBuilder().selectRaw("MAX(userOrder)").prepareStatementString())
				.firstResult
			if (last != null && last.size == 1 && last[0] != null)
			{
				profile.userOrder = (Integer.parseInt(last[0]) + 1).toLong()
			}

			val lastExist = dbHelper.profileDao.queryBuilder()
				.where()
				.eq("name", profile.name)
				.and()
				.eq("host", profile.host)
				.and()
				.eq("remotePort", profile.remotePort)
				.and()
				.eq("password", profile.password)
				.and()
				.eq("protocol", profile.protocol)
				.and()
				.eq("protocol_param", profile.protocol_param)
				.and()
				.eq("obfs", profile.obfs)
				.and()
				.eq("obfs_param", profile.obfs_param)
				.and()
				.eq("url_group", profile.url_group)
				.and()
				.eq("method", profile.method)
				.queryForFirst()
			if (lastExist == null)
			{
				dbHelper.profileDao.createOrUpdate(profile)
				invokeProfileAdded(profile)
			}
		}
		catch (e: SQLException)
		{
			VayLog.e(TAG, "createProfileDr", e)
			ShadowsocksApplication.app.track(e)
		}

		return profile
	}

	/**
	 * create profile sub
	 *
	 * @return create failed return 0, create success return id.
	 */
	fun createProfileSub(p: Profile?): Int
	{
		val profile: Profile = p ?: Profile()
		profile.id = 0
		val oldProfile = ShadowsocksApplication.app.currentProfile()
		if (oldProfile != null)
		{
			// Copy Feature Settings from old profile
			profile.route = oldProfile.route
			profile.ipv6 = oldProfile.ipv6
			profile.proxyApps = oldProfile.proxyApps
			profile.bypass = oldProfile.bypass
			profile.individual = oldProfile.individual
			profile.udpdns = oldProfile.udpdns
			profile.dns = oldProfile.dns
			profile.china_dns = oldProfile.china_dns
		}

		try
		{
			val last = dbHelper.profileDao.queryRaw(dbHelper.profileDao.queryBuilder().selectRaw("MAX(userOrder)").prepareStatementString())
				.firstResult
			if (last != null && last.size == 1 && last[0] != null)
			{
				profile.userOrder = (Integer.parseInt(last[0]) + 1).toLong()
			}

			val lastExist = dbHelper.profileDao.queryBuilder()
				.where()
				.eq("name", profile.name)
				.and()
				.eq("host", profile.host)
				.and()
				.eq("remotePort", profile.remotePort)
				.and()
				.eq("password", profile.password)
				.and()
				.eq("protocol", profile.protocol)
				.and()
				.eq("protocol_param", profile.protocol_param)
				.and()
				.eq("obfs", profile.obfs)
				.and()
				.eq("obfs_param", profile.obfs_param)
				.and()
				.eq("url_group", profile.url_group)
				.and()
				.eq("method", profile.method)
				.queryForFirst()
			return if (lastExist == null)
			{
				dbHelper.profileDao.createOrUpdate(profile)
				0
			}
			else
			{
				lastExist.id
			}
		}
		catch (e: SQLException)
		{
			VayLog.e(TAG, "createProfileSub", e)
			ShadowsocksApplication.app.track(e)
			return 0
		}

	}

	/**
	 * update profile
	 */
	fun updateProfile(profile: Profile): Boolean
	{
		return try
		{
			dbHelper.profileDao.update(profile)
			true
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "updateProfile", e)
			ShadowsocksApplication.app.track(e)
			false
		}

	}

	/**
	 * update all profile by string
	 *
	 * @param key   profile key
	 * @param value profile value
	 * @return update failed return false.
	 */
	fun updateAllProfileByString(key: String, value: String): Boolean
	{
		return try
		{
			dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET $key = '$value';")
			true
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "updateAllProfileByString", e)
			ShadowsocksApplication.app.track(e)
			false
		}

	}

	/**
	 * update all profile by boolean
	 *
	 * @param key   profile key
	 * @param value profile value
	 * @return update failed return false.
	 */
	fun updateAllProfileByBoolean(key: String, value: Boolean): Boolean
	{
		try
		{
			if (value)
			{
				dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET $key = '1';")
			}
			else
			{
				dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET $key = '0';")
			}
			return true
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "updateAllProfileByBoolean", e)
			ShadowsocksApplication.app.track(e)
			return false
		}

	}

	/**
	 * get profile by id
	 *
	 * @param id profile id
	 */
	fun getProfile(id: Int): Profile?
	{
		return try
		{
			dbHelper.profileDao.queryForId(id)
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "getProfile", e)
			ShadowsocksApplication.app.track(e)
			null
		}
	}

	/**
	 * del profile by id
	 *
	 * @param id profile id
	 * @return del failed return false.
	 */
	fun delProfile(id: Int): Boolean
	{
		return try
		{
			dbHelper.profileDao.deleteById(id)
			true
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "delProfile", e)
			ShadowsocksApplication.app.track(e)
			false
		}
	}

	/**
	 * get all profiles by group
	 *
	 * @param group group name
	 * @return get failed return null.
	 */
	fun getAllProfilesByGroup(group: String): List<Profile>
	{
		return try
		{
			dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("userOrder", true).where().like("url_group", "$group%").prepare())
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "getAllProfilesByGroup", e)
			ShadowsocksApplication.app.track(e)
			emptyList()
		}
	}

	fun getAllProfilesByGroupOrderByElapse(groupname: String): List<Profile>
	{
		try
		{
			val notlist = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("elapsed", true).where().eq("url_group", groupname).and().not().eq("elapsed", 0).prepare())
			val eqList = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("elapsed", true).where().eq("url_group", groupname).and().eq("elapsed", 0).prepare())

			// merge list
			val result = ArrayList<Profile>()
			result.addAll(notlist)
			result.addAll(eqList)
			return result
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "getAllProfilesByElapsed", e)
			ShadowsocksApplication.app.track(e)
			return emptyList()
		}
	}


	/**
	 * create default profile
	 */
	fun createDefault(): Profile
	{
		val profile = Profile()
		profile.name = "ShadowsocksR"
		profile.host = Constants.DefaultHostName
		profile.remotePort = 8388
		profile.password = ""
		profile.protocol = "origin"
		profile.obfs = "plain"
		profile.method = "aes-256-cfb"
		profile.url_group = "Default Group"
		return createProfile(profile)
	}

	/**
	 * add profile added listener
	 *
	 * @param l listener callback
	 */
	fun addProfileAddedListener(l: ProfileAddedListener)
	{
		if (mProfileAddedListeners == null)
		{
			return
		}

		// adding listener
		if (!mProfileAddedListeners.contains(l))
		{
			mProfileAddedListeners.add(l)
		}
	}

	/**
	 * remove profile added listener
	 *
	 * @param l listener callback
	 */
	fun removeProfileAddedListener(l: ProfileAddedListener)
	{
		if (mProfileAddedListeners == null || mProfileAddedListeners.isEmpty())
		{
			return
		}

		// remove listener
		mProfileAddedListeners.remove(l)
	}

	/**
	 * invoke profile added listener
	 *
	 * @param profile profile param
	 */
	private fun invokeProfileAdded(profile: Profile)
	{
		if (mProfileAddedListeners == null || mProfileAddedListeners.isEmpty())
		{
			return
		}

		// iteration invoke listener
		for (l in mProfileAddedListeners)
		{
			l.onProfileAdded(profile)
		}
	}

	/**
	 * pro file added listener
	 */
	interface ProfileAddedListener
	{
		/**
		 * profile added
		 *
		 * @param profile profile object
		 */
		fun onProfileAdded(profile: Profile)
	}

	companion object
	{
		private const val TAG = "ProfileManager"
	}
}