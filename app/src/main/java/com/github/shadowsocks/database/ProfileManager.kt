package com.github.shadowsocks.database


import com.github.shadowsocks.ShadowsocksApplication
import com.github.shadowsocks.utils.VayLog

import java.sql.SQLException
import java.util.ArrayList

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
			try
			{
				val result = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().limit(1L).prepare())
				return if (result != null && !result.isEmpty())
				{
					result[0]
				}
				else null
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "getFirstProfile", e)
				ShadowsocksApplication.app.track(e)
				return null
			}

		}

	/**
	 * get all profiles
	 *
	 * @return get failed return null.
	 */
	val allProfiles: List<Profile>?
		get()
		{
			try
			{
				return dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("url_group", true).orderBy("name", true).prepare())
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "getAllProfiles", e)
				ShadowsocksApplication.app.track(e)
				return null
			}

		}

	/**
	 * get all profiles by elapsed
	 *
	 * @return get failed return null.
	 */
	// merge list
	val allProfilesByElapsed: List<Profile>?
		get()
		{
			try
			{
				val notlist = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("elapsed", true).where().not().eq("elapsed", 0).prepare())
				val eqList = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("elapsed", true).where().eq("elapsed", 0).prepare())
				val result = ArrayList<Profile>()
				result.addAll(notlist)
				result.addAll(eqList)
				return result
			}
			catch (e: Exception)
			{
				VayLog.e(TAG, "getAllProfilesByElapsed", e)
				ShadowsocksApplication.app.track(e)
				return null
			}

		}

	val groupNames: List<String>?
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
				return null
			}

		}

	init
	{
		this.mProfileAddedListeners = ArrayList(20)
	}

	/**
	 * create profile
	 */
	@JvmOverloads
	fun createProfile(p: Profile? = null): Profile
	{
		val profile: Profile
		if (p == null)
		{
			profile = Profile()
		}
		else
		{
			profile = p
		}
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
		val profile: Profile
		if (p == null)
		{
			profile = Profile()
		}
		else
		{
			profile = p
		}
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

			val last_exist = dbHelper.profileDao.queryBuilder()
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
			if (last_exist == null)
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
		val profile: Profile
		if (p == null)
		{
			profile = Profile()
		}
		else
		{
			profile = p
		}
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

			val last_exist = dbHelper.profileDao.queryBuilder()
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
			if (last_exist == null)
			{
				dbHelper.profileDao.createOrUpdate(profile)
				return 0
			}
			else
			{
				return last_exist.id
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
		try
		{
			dbHelper.profileDao.update(profile)
			return true
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "updateProfile", e)
			ShadowsocksApplication.app.track(e)
			return false
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
		try
		{
			dbHelper.profileDao.executeRawNoArgs("UPDATE `profile` SET $key = '$value';")
			return true
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "updateAllProfileByString", e)
			ShadowsocksApplication.app.track(e)
			return false
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
		try
		{
			return dbHelper.profileDao.queryForId(id)
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "getProfile", e)
			ShadowsocksApplication.app.track(e)
			return null
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
		try
		{
			dbHelper.profileDao.deleteById(id)
			return true
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "delProfile", e)
			ShadowsocksApplication.app.track(e)
			return false
		}

	}

	/**
	 * get all profiles by group
	 *
	 * @param group group name
	 * @return get failed return null.
	 */
	fun getAllProfilesByGroup(group: String): MutableList<Profile>?
	{
		try
		{
			return dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("name", true).where().like("url_group", "$group%").prepare())
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, "getAllProfilesByGroup", e)
			ShadowsocksApplication.app.track(e)
			return null
		}

	}

	fun getAllProfilesByGroupOrderbyElapse(groupname: String): List<Profile>?
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
			return null
		}

	}


	/**
	 * create default profile
	 */
	fun createDefault(): Profile
	{
		val profile = Profile()
		profile.name = "ShadowsocksR"
		profile.host = "1.1.1.1"
		profile.remotePort = 80
		profile.password = "androidssr"
		profile.protocol = "auth_chain_a"
		profile.obfs = "http_simple"
		profile.method = "none"
		profile.url_group = "ShadowsocksR"
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

		private val TAG = ProfileManager::class.java.simpleName
	}


}
/**
 * create profile
 */
