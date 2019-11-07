package com.bige0.shadowsocksr

import android.annotation.*
import android.app.*
import android.content.*
import android.content.res.*
import android.os.*
import android.preference.*
import androidx.appcompat.app.*
import com.bige0.shadowsocksr.database.*
import com.bige0.shadowsocksr.job.*
import com.bige0.shadowsocksr.utils.*
import com.evernote.android.job.*
import com.google.android.gms.analytics.*
import com.google.android.gms.common.api.*
import com.google.android.gms.tagmanager.*
import com.j256.ormlite.logger.*
import java.io.*
import java.util.*
import java.util.concurrent.*

class ShadowsocksApplication : Application()
{
	companion object
	{
		const val SIG_FUNC = "getSignature"
		private const val TAG = "ShadowsocksApplication"
		private val EXECUTABLES = arrayOf(Constants.Executable.PDNSD, Constants.Executable.PROXYCHAINS4, Constants.Executable.SS_LOCAL, Constants.Executable.TUN2SOCKS)

		private val SIMPLIFIED_CHINESE: Locale
		private val TRADITIONAL_CHINESE: Locale
		lateinit var app: ShadowsocksApplication

		init
		{
			if (Build.VERSION.SDK_INT >= 21)
			{
				SIMPLIFIED_CHINESE = Locale.forLanguageTag("zh-Hans-CN")
				TRADITIONAL_CHINESE = Locale.forLanguageTag("zh-Hant-TW")
			}
			else
			{
				SIMPLIFIED_CHINESE = Locale.SIMPLIFIED_CHINESE
				TRADITIONAL_CHINESE = Locale.TRADITIONAL_CHINESE
			}
		}
	}

	lateinit var containerHolder: ContainerHolder
	lateinit var settings: SharedPreferences
	lateinit var editor: SharedPreferences.Editor
	lateinit var profileManager: ProfileManager
	lateinit var ssrSubManager: SSRSubManager
	lateinit var mThreadPool: ScheduledExecutorService
	private lateinit var tracker: Tracker

	private fun initVariable()
	{
		tracker = GoogleAnalytics.getInstance(this)
			.newTracker(R.xml.tracker)
		settings = PreferenceManager.getDefaultSharedPreferences(this)
		editor = settings.edit()

		profileManager = ProfileManager(DBHelper(this))
		ssrSubManager = SSRSubManager(DBHelper(this))

		mThreadPool = ScheduledThreadPoolExecutor(10, ThreadFactory { r ->
			val thread = Thread(r)
			thread.name = "shadowsocksr-thread"
			thread
		})
	}

	fun track(category: String, action: String)
	{
		val builders = HitBuilders.EventBuilder()
			.setAction(action)
			.setCategory(category)
			.setLabel(BuildConfig.VERSION_NAME)
			.build()
		tracker.send(builders)
	}

	fun track(t: Throwable)
	{
		val builders = HitBuilders.ExceptionBuilder()
			.setDescription(StandardExceptionParser(this, null).getDescription(Thread.currentThread().name, t))
			.setFatal(false)
			.build()
		tracker.send(builders)
	}

	fun profileId(): Int
	{
		return settings.getInt(Constants.Key.id, -1)
	}

	fun profileId(i: Int)
	{
		editor.putInt(Constants.Key.id, i)
			.apply()
	}

	fun currentProfile(): Profile?
	{
		return profileManager.getProfile(profileId())
	}

	fun switchProfile(id: Int): Profile
	{
		profileId(id)
		val profile = profileManager.getProfile(id)
		return profile ?: profileManager.createProfile()
	}

	@SuppressLint("NewApi")
	private fun checkChineseLocale(locale: Locale): Locale?
	{
		if ("zh" == locale.language)
		{
			val country = locale.country
			if ("CN" == country || "TW" == country)
			{
				return null
			}
			val script = locale.script
			if ("Hans" == script)
			{
				return SIMPLIFIED_CHINESE
			}
			else if ("Hant" == script)
			{
				return TRADITIONAL_CHINESE
			}
			VayLog.w(TAG, String.format("Unknown zh locale script: %s. Falling back to trying countries...", script))
			return if ("SG" == country)
			{
				SIMPLIFIED_CHINESE
			}
			else if ("HK" == country || "MO" == country)
			{
				TRADITIONAL_CHINESE
			}
			else
			{
				VayLog.w(TAG, String.format("Unknown zh locale: %s. Falling back to zh-Hans-CN...", locale.toLanguageTag()))
				SIMPLIFIED_CHINESE
			}
		}
		return null
	}

	private fun checkChineseLocale(config: Configuration)
	{
		if (Build.VERSION.SDK_INT >= 24)
		{
			val localeList = config.locales
			val newList = arrayOfNulls<Locale>(localeList.size())
			var changed = false
			for (i in 0 until localeList.size())
			{
				val locale = localeList.get(i)
				val newLocale = checkChineseLocale(locale)
				if (newLocale == null)
				{
					newList[i] = locale
				}
				else
				{
					newList[i] = newLocale
					changed = true
				}
			}
			if (changed)
			{
				val newConfig = Configuration(config)
				newConfig.setLocales(LocaleList(*newList))
				resources.updateConfiguration(newConfig, resources.displayMetrics)
			}
		}
		else
		{
			val newLocale = checkChineseLocale(config.locale)
			if (newLocale != null)
			{
				val newConfig = Configuration(config)
				newConfig.locale = newLocale
				resources.updateConfiguration(newConfig, resources.displayMetrics)
			}
		}
	}

	override fun onConfigurationChanged(newConfig: Configuration)
	{
		super.onConfigurationChanged(newConfig)
		checkChineseLocale(newConfig)
	}

	override fun onCreate()
	{
		super.onCreate()
		app = this
		initVariable()

		if (!BuildConfig.DEBUG)
		{
			System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR")
		}
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
		checkChineseLocale(resources.configuration)
		val tm = TagManager.getInstance(this)
		val pending = tm.loadContainerPreferNonDefault("GTM-NT8WS8", R.raw.gtm_default_container)
		val callback = ResultCallback<ContainerHolder> { holder ->
			if (!holder.status.isSuccess)
			{
				return@ResultCallback
			}
			containerHolder = holder
			val container = holder.container
			container.registerFunctionCallMacroCallback(SIG_FUNC) { functionName, _ ->
				if (SIG_FUNC == functionName)
				{
					Utils.getSignature(applicationContext)
				}
				else
				{
					null
				}
			}
		}
		pending.setResultCallback(callback, 2, TimeUnit.SECONDS)
		JobManager.create(this)
			.addJobCreator(DonaldTrump())
	}

	fun refreshContainerHolder()
	{
		if (::containerHolder.isInitialized)
		{
			containerHolder.refresh()
		}
	}

	private fun copyAssets(path: String)
	{
		val assetManager = assets
		var files: Array<String>? = null
		try
		{
			files = assetManager.list(path)
		}
		catch (e: Exception)
		{
			VayLog.e(TAG, e.message.toString())
			app.track(e)
		}

		if (files != null)
		{
			for (file in files)
			{
				var inputStream: InputStream? = null
				var fos: FileOutputStream? = null
				try
				{
					inputStream = if (path.isNotEmpty())
					{
						assetManager.open(path + File.separator + file)
					}
					else
					{
						assetManager.open(file)
					}
					fos = FileOutputStream("${applicationInfo.dataDir}/$file")
					IOUtils.copy(inputStream, fos)
				}
				catch (e: IOException)
				{
					VayLog.e(TAG, "copyAssets", e)
				}
				finally
				{
					try
					{
						inputStream?.close()
					}
					catch (e: Exception)
					{
						VayLog.e(TAG, "copyAssets", e)
					}
					try
					{
						fos?.close()
					}
					catch (e: Exception)
					{
						VayLog.e(TAG, "copyAssets", e)
					}
				}
			}
		}
	}

	fun crashRecovery()
	{
		for (exe in EXECUTABLES)
		{
			ProcessBuilder("sh", "killall", exe).start()
			ProcessBuilder("sh", "rm", "-f", "${applicationInfo.dataDir}${File.separator}${exe}-vpn.conf").start()
		}
	}

	fun copyAssets()
	{
		// ensure executables are killed before writing to them
		crashRecovery()
		copyAssets(Build.SUPPORTED_ABIS[0])
		copyAssets("acl")

		for (exe in EXECUTABLES)
		{
			ProcessBuilder("sh", "chmod", "755", "${applicationInfo.nativeLibraryDir}${File.separator}${exe}").start()
		}

		// save current version code
		editor.putInt(Constants.Key.currentVersionCode, BuildConfig.VERSION_CODE)
			.apply()
	}

	fun updateAssets()
	{
		if (settings.getInt(Constants.Key.currentVersionCode, -1) != BuildConfig.VERSION_CODE)
		{
			copyAssets()
		}
	}
}
