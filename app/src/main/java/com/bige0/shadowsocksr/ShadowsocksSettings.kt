package com.bige0.shadowsocksr

import android.app.*
import android.content.*
import android.net.*
import android.os.*
import android.preference.*
import android.text.*
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.bige0.shadowsocksr.database.*
import com.bige0.shadowsocksr.preferences.*
import com.bige0.shadowsocksr.utils.*
import java.io.*
import java.net.*
import java.util.*

//TODO:androidx
@Suppress("DEPRECATION")
class ShadowsocksSettings : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener
{
	companion object
	{
		private const val TAG = "ShadowsocksSettings"

		private val PROXY_PREFS = arrayOf(Constants.Key.group_name, Constants.Key.name, Constants.Key.host, Constants.Key.remotePort, Constants.Key.localPort, Constants.Key.password, Constants.Key.method, Constants.Key.protocol, Constants.Key.obfs, Constants.Key.obfs_param, Constants.Key.dns, Constants.Key.china_dns, Constants.Key.protocol_param)

		private val FEATURE_PREFS = arrayOf(Constants.Key.route, Constants.Key.proxyApps, Constants.Key.udpdns, Constants.Key.ipv6)
	}

	lateinit var currentProfile: Profile
	val isCurrentProfileInitialized: Boolean
		get()
		{
			return ::currentProfile.isInitialized
		}

	private lateinit var activity: Shadowsocks
	private lateinit var isProxyApps: SwitchPreference

	private fun updateDropDownPreference(pref: Preference, value: String)
	{
		(pref as DropDownPreference).setValue(value)
	}

	private fun updatePasswordEditTextPreference(pref: Preference, value: String)
	{
		pref.summary = value
		(pref as PasswordEditTextPreference).text = value
	}

	private fun updateNumberPickerPreference(pref: Preference, value: Int)
	{
		(pref as NumberPickerPreference).value = value
	}

	private fun updateSummaryEditTextPreference(pref: Preference, value: String)
	{
		pref.summary = value
		(pref as SummaryEditTextPreference).text = value
	}

	private fun updateSwitchPreference(pref: Preference, value: Boolean)
	{
		(pref as SwitchPreference).isChecked = value
	}

	private fun updatePreference(pref: Preference, name: String, profile: Profile)
	{
		when (name)
		{
			Constants.Key.group_name -> updateSummaryEditTextPreference(pref, profile.url_group)
			Constants.Key.name -> updateSummaryEditTextPreference(pref, profile.name)
			Constants.Key.remotePort -> updateNumberPickerPreference(pref, profile.remotePort)
			Constants.Key.localPort -> updateNumberPickerPreference(pref, profile.localPort)
			Constants.Key.password -> updatePasswordEditTextPreference(pref, profile.password)
			Constants.Key.method -> updateDropDownPreference(pref, profile.method)
			Constants.Key.protocol -> updateDropDownPreference(pref, profile.protocol)
			Constants.Key.protocol_param -> updateSummaryEditTextPreference(pref, profile.protocol_param)
			Constants.Key.obfs -> updateDropDownPreference(pref, profile.obfs)
			Constants.Key.obfs_param -> updateSummaryEditTextPreference(pref, profile.obfs_param)
			Constants.Key.route -> updateDropDownPreference(pref, profile.route)
			Constants.Key.proxyApps -> updateSwitchPreference(pref, profile.proxyApps)
			Constants.Key.udpdns -> updateSwitchPreference(pref, profile.udpdns)
			Constants.Key.dns -> updateSummaryEditTextPreference(pref, profile.dns)
			Constants.Key.china_dns -> updateSummaryEditTextPreference(pref, profile.china_dns)
			Constants.Key.ipv6 -> updateSwitchPreference(pref, profile.ipv6)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		addPreferencesFromResource(R.xml.pref_all)
		preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

		activity = getActivity() as Shadowsocks

		findPreference(Constants.Key.group_name).setOnPreferenceChangeListener { _, value ->
			currentProfile.url_group = value as String
			ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
		}
		findPreference(Constants.Key.name).setOnPreferenceChangeListener { _, value ->
			currentProfile.name = value as String
			ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
		}
		findPreference(Constants.Key.host).setOnPreferenceClickListener {
			val li = LayoutInflater.from(activity)
			val myView = li.inflate(R.layout.layout_edittext, null)
			val hostEditText = myView.findViewById<EditText>(R.id.editTextInput)
			hostEditText.setText(currentProfile.host)
			AlertDialog.Builder(activity)
				.setView(myView)
				.setTitle(getString(R.string.proxy))
				.setPositiveButton(android.R.string.ok) { _, _ ->
					currentProfile.host = hostEditText.text
						.toString()
					ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
				}
				.setNegativeButton(android.R.string.no) { _, _ -> setProfile(currentProfile) }
				.create()
				.show()
			true
		}
		findPreference(Constants.Key.remotePort).setOnPreferenceChangeListener { _, value ->
			currentProfile.remotePort = value as Int
			ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
		}
		findPreference(Constants.Key.localPort).setOnPreferenceChangeListener { _, value ->
			currentProfile.localPort = value as Int
			ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
		}
		findPreference(Constants.Key.password).setOnPreferenceChangeListener { _, value ->
			currentProfile.password = value as String
			ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
		}
		findPreference(Constants.Key.method).setOnPreferenceChangeListener { _, value ->
			currentProfile.method = value as String
			ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
		}
		findPreference(Constants.Key.protocol).setOnPreferenceChangeListener { _, value ->
			currentProfile.protocol = value as String
			ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
		}
		findPreference(Constants.Key.protocol_param).setOnPreferenceChangeListener { _, value ->
			currentProfile.protocol_param = value as String
			ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
		}
		findPreference(Constants.Key.obfs).setOnPreferenceChangeListener { _, value ->
			currentProfile.obfs = value as String
			ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
		}
		findPreference(Constants.Key.obfs_param).setOnPreferenceChangeListener { _, value ->
			currentProfile.obfs_param = value as String
			ShadowsocksApplication.app.profileManager.updateProfile(currentProfile)
		}

		findPreference(Constants.Key.route).setOnPreferenceChangeListener { _, value ->
			if ("self" == value)
			{
				val li = LayoutInflater.from(activity)
				val myView = li.inflate(R.layout.layout_edittext, null)
				val aclUrlEditText = myView.findViewById<EditText>(R.id.editTextInput)
				aclUrlEditText.setText(preferenceManager.sharedPreferences.getString(Constants.Key.aclurl, ""))
				AlertDialog.Builder(activity)
					.setView(myView)
					.setTitle(getString(R.string.acl_file))
					.setPositiveButton(android.R.string.ok) { _, _ ->
						if (TextUtils.isEmpty(aclUrlEditText.text.toString()))
						{
							setProfile(currentProfile)
						}
						else
						{
							preferenceManager.sharedPreferences
								.edit()
								.putString(Constants.Key.aclurl, aclUrlEditText.text.toString())
								.apply()
							downloadAcl(aclUrlEditText.text.toString())
							ShadowsocksApplication.app.profileManager.updateAllProfileByString(Constants.Key.route, value)
						}
					}
					.setNegativeButton(android.R.string.no) { _, _ -> setProfile(currentProfile) }
					.create()
					.show()
			}
			else
			{
				ShadowsocksApplication.app.profileManager.updateAllProfileByString(Constants.Key.route, value as String)
			}
			true
		}

		isProxyApps = findPreference(Constants.Key.proxyApps) as SwitchPreference
		isProxyApps.setOnPreferenceClickListener {
			startActivity(Intent(activity, AppManager::class.java))
			isProxyApps.isChecked = true
			false
		}

		isProxyApps.setOnPreferenceChangeListener { _, value -> ShadowsocksApplication.app.profileManager.updateAllProfileByBoolean("proxyApps", value as Boolean) }

		findPreference(Constants.Key.udpdns).setOnPreferenceChangeListener { _, value -> ShadowsocksApplication.app.profileManager.updateAllProfileByBoolean("udpdns", value as Boolean) }

		findPreference(Constants.Key.dns).setOnPreferenceChangeListener { _, value -> ShadowsocksApplication.app.profileManager.updateAllProfileByString(Constants.Key.dns, value as String) }

		findPreference(Constants.Key.china_dns).setOnPreferenceChangeListener { _, value -> ShadowsocksApplication.app.profileManager.updateAllProfileByString(Constants.Key.china_dns, value as String) }

		findPreference(Constants.Key.ipv6).setOnPreferenceChangeListener { _, value -> ShadowsocksApplication.app.profileManager.updateAllProfileByBoolean("ipv6", value as Boolean) }

		val switchPre = findPreference(Constants.Key.isAutoConnect) as SwitchPreference
		switchPre.setOnPreferenceChangeListener { _, value ->
			BootReceiver.enabled = value as Boolean
			true
		}

		if (preferenceManager.sharedPreferences.getBoolean(Constants.Key.isAutoConnect, false))
		{
			BootReceiver.enabled = true
			preferenceManager.sharedPreferences.edit()
				.remove(Constants.Key.isAutoConnect)
				.apply()
		}

		switchPre.isChecked = BootReceiver.enabled

		findPreference("recovery").setOnPreferenceClickListener {
			ShadowsocksApplication.app.track(TAG, "reset")
			activity.recovery()
			true
		}

		findPreference("ignore_battery_optimization").setOnPreferenceClickListener {
			ShadowsocksApplication.app.track(TAG, "ignore_battery_optimization")
			activity.ignoreBatteryOptimization()
			true
		}

		findPreference("aclupdate").setOnPreferenceClickListener {
			ShadowsocksApplication.app.track(TAG, "aclupdate")
			val url = preferenceManager.sharedPreferences
				.getString(Constants.Key.aclurl, "")
			if ("" == url)
			{
				AlertDialog.Builder(activity)
					.setTitle(getString(R.string.aclupdate))
					.setNegativeButton(getString(android.R.string.ok), null)
					.setMessage(R.string.aclupdate_url_notset)
					.create()
					.show()
			}
			else
			{
				downloadAcl(url)
			}
			true
		}

		if (!File(ShadowsocksApplication.app.applicationInfo.dataDir + '/'.toString() + "self.acl").exists() && "" != preferenceManager.sharedPreferences.getString(Constants.Key.aclurl, ""))
		{
			downloadAcl(preferenceManager.sharedPreferences.getString(Constants.Key.aclurl, ""))
		}

		findPreference("about").setOnPreferenceClickListener {
			ShadowsocksApplication.app.track(TAG, "about")
			val web = WebView(activity)
			web.isVerticalScrollBarEnabled = false
			web.loadUrl("file:///android_asset/pages/about.html")
			web.webViewClient = object : WebViewClient()
			{
				override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean
				{
					try
					{
						startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
					}
					catch (e: Exception)
					{
						// Ignore
					}

					return true
				}
			}

			AlertDialog.Builder(activity)
				.setTitle(String.format(Locale.ENGLISH, getString(R.string.about_title), BuildConfig.VERSION_NAME))
				.setNegativeButton(getString(android.R.string.ok), null)
				.setView(web)
				.create()
				.show()
			true
		}

		findPreference("logcat").setOnPreferenceClickListener {
			ShadowsocksApplication.app.track(TAG, "logcat")
			val li = LayoutInflater.from(activity)
			val myView = li.inflate(R.layout.layout_edittext, null)
			val etLogcat = myView.findViewById<EditText>(R.id.editTextInput)
			try
			{
				val logcat = Runtime.getRuntime()
					.exec("logcat -d")
				val br = BufferedReader(InputStreamReader(logcat.inputStream))
				var line: String? = br.readLine()
				while (line != null)
				{
					etLogcat.append(line)
					etLogcat.append("\n")
					line = br.readLine()
				}
				br.close()
			}
			catch (e: Exception)
			{
				// unknown failures, probably shouldn't retry
				e.printStackTrace()
			}

			AlertDialog.Builder(activity)
				.setView(myView)
				.setTitle("Logcat")
				.setNegativeButton(getString(android.R.string.ok), null)
				.create()
				.show()
			true
		}

		findPreference(Constants.Key.frontproxy).setOnPreferenceClickListener {
			val prefs = preferenceManager.sharedPreferences

			val view = View.inflate(activity, R.layout.layout_front_proxy, null)
			val swFrontproxyEnable = view.findViewById<Switch>(R.id.sw_frontproxy_enable)
			val spFrontproxyType = view.findViewById<Spinner>(R.id.sp_frontproxy_type)
			val etFrontproxyAddr = view.findViewById<EditText>(R.id.et_frontproxy_addr)
			val etFrontproxyPort = view.findViewById<EditText>(R.id.et_frontproxy_port)
			val etFrontproxyUsername = view.findViewById<EditText>(R.id.et_frontproxy_username)
			val etFrontproxyPassword = view.findViewById<EditText>(R.id.et_frontproxy_password)

			val stringArray = resources.getStringArray(R.array.frontproxy_type_entry)
				.toList()
			val indexOf = stringArray.indexOf(prefs.getString("frontproxy_type", "socks5"))
			spFrontproxyType.setSelection(indexOf)

			if (prefs.getInt("frontproxy_enable", 0) == 1)
			{
				swFrontproxyEnable.isChecked = true
			}

			etFrontproxyAddr.setText(prefs.getString("frontproxy_addr", ""))
			etFrontproxyPort.setText(prefs.getString("frontproxy_port", ""))
			etFrontproxyUsername.setText(prefs.getString("frontproxy_username", ""))
			etFrontproxyPassword.setText(prefs.getString("frontproxy_password", ""))

			swFrontproxyEnable.setOnCheckedChangeListener { _, isChecked ->
				val prefsEdit = prefs.edit()
				if (isChecked)
				{
					prefsEdit.putInt("frontproxy_enable", 1)
					if (!File(ShadowsocksApplication.app.applicationInfo.dataDir + "/proxychains.conf").exists())
					{
						val proxychainsConf = String.format(Locale.ENGLISH,
															Constants.ConfigUtils.PROXYCHAINS,
															prefs.getString("frontproxy_type", "socks5"),
															prefs.getString("frontproxy_addr", ""),
															prefs.getString("frontproxy_port", ""),
															prefs.getString("frontproxy_username", ""),
															prefs.getString("frontproxy_password", ""))
						Utils.printToFile(File(ShadowsocksApplication.app.applicationInfo.dataDir + "/proxychains.conf"), proxychainsConf, true)
					}
				}
				else
				{
					prefsEdit.putInt("frontproxy_enable", 0)
					if (File(ShadowsocksApplication.app.applicationInfo.dataDir + "/proxychains.conf").exists())
					{
						val deleteFlag = File(ShadowsocksApplication.app.applicationInfo.dataDir + "/proxychains.conf").delete()
						VayLog.d(TAG, "delete proxychains.conf = $deleteFlag")
					}
				}
				prefsEdit.apply()
			}

			AlertDialog.Builder(activity)
				.setTitle(getString(R.string.frontproxy_set))
				.setPositiveButton(android.R.string.ok) { _, _ ->
					val prefsEdit = prefs.edit()
					prefsEdit.putString("frontproxy_type", spFrontproxyType.selectedItem.toString())

					prefsEdit.putString("frontproxy_addr", etFrontproxyAddr.text.toString())
					prefsEdit.putString("frontproxy_port", etFrontproxyPort.text.toString())
					prefsEdit.putString("frontproxy_username", etFrontproxyUsername.text.toString())
					prefsEdit.putString("frontproxy_password", etFrontproxyPassword.text.toString())

					prefsEdit.apply()

					if (File(ShadowsocksApplication.app.applicationInfo.dataDir + "/proxychains.conf").exists())
					{
						val proxychainsConf = String.format(Locale.ENGLISH, Constants.ConfigUtils.PROXYCHAINS,
															prefs.getString("frontproxy_type", "socks5"), prefs.getString("frontproxy_addr", ""), prefs.getString("frontproxy_port", ""), prefs.getString("frontproxy_username", ""), prefs.getString("frontproxy_password", ""))
						Utils.printToFile(File(ShadowsocksApplication.app.applicationInfo.dataDir + "/proxychains.conf"), proxychainsConf, true)
					}
				}
				.setNegativeButton(android.R.string.no, null)
				.setView(view)
				.create()
				.show()
			true
		}
	}

	private fun downloadAcl(url: String?)
	{
		val progressDialog = ProgressDialog.show(activity,
												 getString(R.string.aclupdate),
												 getString(R.string.aclupdate_downloading),
												 false, false)

		ShadowsocksApplication.app.mThreadPool.execute {
			Looper.prepare()
			var conn: HttpURLConnection? = null
			var inputStream: InputStream? = null
			try
			{
				conn = URL(url).openConnection() as HttpURLConnection
				inputStream = conn.inputStream
				IOUtils.writeString(ShadowsocksApplication.app.applicationInfo.dataDir + '/'.toString() + "self.acl", IOUtils.readString(inputStream!!))

				progressDialog.dismiss()
				ToastUtils.showShort(R.string.aclupdate_successfully)
			}
			catch (e: IOException)
			{
				e.printStackTrace()
				progressDialog.dismiss()
				ToastUtils.showShort(R.string.aclupdate_failed)
			}
			catch (e: Exception)
			{
				// unknown failures, probably shouldn't retry
				e.printStackTrace()
				progressDialog.dismiss()
				ToastUtils.showShort(R.string.aclupdate_failed)
			}
			finally
			{
				IOUtils.close(inputStream)
				IOUtils.disconnect(conn)
			}
			Looper.loop()
		}
	}

	fun refreshProfile()
	{
		val profile = ShadowsocksApplication.app.currentProfile()
		if (profile != null)
		{
			currentProfile = profile
		}
		else
		{
			val first = ShadowsocksApplication.app.profileManager.firstProfile
			currentProfile = if (first != null)
			{
				ShadowsocksApplication.app.profileId(first.id)
				first
			}
			else
			{
				val defaultProfile = ShadowsocksApplication.app.profileManager.createDefault()
				ShadowsocksApplication.app.profileId(defaultProfile.id)
				defaultProfile
			}
		}

		isProxyApps.isChecked = currentProfile.proxyApps
	}

	override fun onDestroy()
	{
		super.onDestroy()
		ShadowsocksApplication.app.settings.unregisterOnSharedPreferenceChangeListener(this)
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String)
	{
	}

	fun setEnabled(enabled: Boolean)
	{
		val list = PROXY_PREFS + FEATURE_PREFS
		for (name in list)
		{
			val pref = findPreference(name)
			if (pref != null)
			{
				pref.isEnabled = enabled && (Constants.Key.proxyApps != name || Utils.isLollipopOrAbove)
			}
		}
	}

	fun setProfile(profile: Profile)
	{
		currentProfile = profile
		val list = PROXY_PREFS + FEATURE_PREFS
		for (name in list)
		{
			updatePreference(findPreference(name), name, profile)
		}
	}
}
