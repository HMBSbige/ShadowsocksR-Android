package com.bige0.shadowsocksr

import android.annotation.*
import android.app.*
import android.app.backup.*
import android.content.*
import android.content.res.*
import android.net.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.*
import androidx.appcompat.widget.Toolbar
import androidx.core.content.*
import androidx.core.content.pm.*
import com.bige0.shadowsocksr.aidl.*
import com.bige0.shadowsocksr.database.*
import com.bige0.shadowsocksr.job.*
import com.bige0.shadowsocksr.network.request.*
import com.bige0.shadowsocksr.shortcuts.*
import com.bige0.shadowsocksr.utils.*
import com.github.jorgecastilloprz.*
import com.google.android.material.floatingactionbutton.*
import com.google.android.material.snackbar.*
import java.util.*

//TODO:androidx
@Suppress("DEPRECATION")
class Shadowsocks : AppCompatActivity()
{
	companion object
	{
		private const val TAG = "Shadowsocks"
		private const val REQUEST_CONNECT = 1
	}

	private val callback by lazy {
		object : IShadowsocksServiceCallback.Stub()
		{
			override fun stateChanged(s: Int, profileName: String?, m: String?)
			{
				handler.post {
					when (s)
					{
						Constants.State.CONNECTING ->
						{
							fab.backgroundTintList = greyTint
							fab.setImageResource(R.drawable.ic_start_busy)
							fab.isEnabled = false
							fabProgressCircle.show()
							preferences.setEnabled(false)
							stat.visibility = View.GONE
						}
						Constants.State.CONNECTED ->
						{
							fab.backgroundTintList = greenTint
							if (state == Constants.State.CONNECTING)
							{
								fabProgressCircle.beginFinalAnimation()
							}
							else
							{
								fabProgressCircle.postDelayed({ hideCircle() }, 1000)
							}
							fab.isEnabled = true
							changeSwitch(true)
							preferences.setEnabled(false)
							stat.visibility = View.VISIBLE
							connectionTestText.visibility = View.VISIBLE
							connectionTestText.text = getString(R.string.connection_test_pending)
						}
						Constants.State.STOPPED ->
						{
							fab.backgroundTintList = greyTint
							fabProgressCircle.postDelayed({ hideCircle() }, 1000)
							fab.isEnabled = true
							changeSwitch(false)
							if (!m.isNullOrEmpty())
							{
								val snackBar = Snackbar.make(findViewById(android.R.id.content),
															 String.format(Locale.ENGLISH, getString(R.string.vpn_error), m), Snackbar.LENGTH_LONG)
								snackBar.show()
								VayLog.e(TAG, "Error to start VPN service: $m")
							}
							preferences.setEnabled(true)
							stat.visibility = View.GONE
						}
						Constants.State.STOPPING ->
						{
							fab.backgroundTintList = greyTint
							fab.setImageResource(R.drawable.ic_start_busy)
							fab.isEnabled = false
							if (state == Constants.State.CONNECTED)
							{
								// ignore for stopped
								fabProgressCircle.show()
							}
							preferences.setEnabled(false)
							stat.visibility = View.GONE
						}
						else ->
						{
						}
					}
					state = s
				}
			}

			override fun trafficUpdated(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long)
			{
				handler.post { updateTraffic(txRate, rxRate, txTotal, rxTotal) }
			}
		}
	}

	var handler = Handler()
	private var serviceStarted: Boolean = false
	private var state = Constants.State.STOPPED
	private var isTestConnect: Boolean = false

	private lateinit var fab: FloatingActionButton
	private lateinit var fabProgressCircle: FABProgressCircle
	private lateinit var mServiceBoundContext: ServiceBoundContext
	private lateinit var stat: View
	private lateinit var connectionTestText: TextView
	private lateinit var txText: TextView
	private lateinit var rxText: TextView
	private lateinit var txRateText: TextView
	private lateinit var rxRateText: TextView
	private lateinit var preferences: ShadowsocksSettings

	private var progressDialog: ProgressDialog? = null
	private var greyTint: ColorStateList? = null
	private var greenTint: ColorStateList? = null

	override fun attachBaseContext(newBase: Context)
	{
		super.attachBaseContext(newBase)

		mServiceBoundContext = object : ServiceBoundContext(newBase)
		{
			override fun onServiceConnected()
			{
				// Update the UI
				if (::fab.isInitialized)
				{
					fab.isEnabled = true
				}

				updateState()
			}

			override fun onServiceDisconnected()
			{
				if (::fab.isInitialized)
				{
					fab.isEnabled = false
				}
			}

			override fun binderDied()
			{
				detachService()
				ShadowsocksApplication.app.crashRecovery()
				attachService()
			}
		}
	}

	fun updateTraffic(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long)
	{
		txText.text = TrafficMonitor.formatTraffic(txTotal)
		rxText.text = TrafficMonitor.formatTraffic(rxTotal)
		txRateText.text = "${TrafficMonitor.formatTraffic(txRate)}/s"
		rxRateText.text = "${TrafficMonitor.formatTraffic(rxRate)}/s"
	}

	private fun attachService()
	{
		mServiceBoundContext.attachService(callback)
	}

	private fun changeSwitch(checked: Boolean)
	{
		serviceStarted = checked
		val resId = if (checked) R.drawable.ic_start_connected else R.drawable.ic_start_idle
		fab.setImageResource(resId)
		if (fab.isEnabled)
		{
			fab.isEnabled = false
			handler.postDelayed({ fab.isEnabled = true }, 1000)
		}
	}

	private fun Int.showProgress(): Handler
	{
		clearDialog()
		progressDialog = ProgressDialog.show(this@Shadowsocks, "", getString(this), true, false)
		return object : Handler(Looper.getMainLooper())
		{
			override fun handleMessage(msg: Message)
			{
				clearDialog()
			}
		}
	}

	private fun cancelStart()
	{
		clearDialog()
		changeSwitch(false)
	}

	private fun prepareStartService()
	{
		val intent = VpnService.prepare(mServiceBoundContext)
		if (intent != null)
		{
			startActivityForResult(intent, REQUEST_CONNECT)
		}
		else
		{
			handler.post { onActivityResult(REQUEST_CONNECT, Activity.RESULT_OK, null) }
		}
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.layout_main)
		// Initialize Toolbar
		initToolbar()

		greyTint = ContextCompat.getColorStateList(this, R.color.material_blue_grey_700)
		greenTint = ContextCompat.getColorStateList(this, R.color.material_green_700)
		preferences = fragmentManager.findFragmentById(android.R.id.content) as ShadowsocksSettings

		stat = findViewById(R.id.stat)
		connectionTestText = findViewById(R.id.connection_test)
		txText = findViewById(R.id.tx)
		txRateText = findViewById(R.id.txRate)
		rxText = findViewById(R.id.rx)
		rxRateText = findViewById(R.id.rxRate)
		stat.setOnClickListener { testConnect() }

		fab = findViewById(R.id.fab)
		fabProgressCircle = findViewById(R.id.fabProgressCircle)
		fab.setOnClickListener {
			when
			{
				serviceStarted -> serviceStop()
				mServiceBoundContext.bgService != null -> prepareStartService()
				else -> changeSwitch(false)
			}
		}
		fab.setOnLongClickListener {
			val strId = if (serviceStarted) R.string.stop else R.string.connect
			Utils.positionToast(
				Toast.makeText(this@Shadowsocks, strId, Toast.LENGTH_SHORT),
				fab,
				window,
				0,
				Utils.dpToPx(this@Shadowsocks, 8))
				.show()
			true
		}

		updateTraffic(0, 0, 0, 0)

		SSRSubUpdateJob.schedule()

		updateDynamicShortcuts()

		// attach service
		attachService()
	}

	/**
	 * init toolbar
	 */
	private fun initToolbar()
	{
		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		// non-translatable logo
		toolbar.title = "shadowsocks R"
		toolbar.setTitleTextAppearance(toolbar.context, R.style.Toolbar_Logo)
		try
		{
			val field = Toolbar::class.java.getDeclaredField("mTitleTextView")
			field.isAccessible = true
			val title = field.get(toolbar) as TextView
			title.isFocusable = true
			title.gravity = 0x10
			title.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
			title.setOnClickListener { startActivity(Intent(this@Shadowsocks, ProfileManagerActivity::class.java)) }
			val typedArray = obtainStyledAttributes(intArrayOf(R.attr.selectableItemBackgroundBorderless))
			title.setBackgroundResource(typedArray.getResourceId(0, 0))
			typedArray.recycle()
			val tf = Typefaces[this, "fonts/Iceland.ttf"]
			if (tf != null)
			{
				title.typeface = tf
			}
			title.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down, 0)
		}
		catch (e: Exception)
		{
			e.printStackTrace()
		}

	}

	/**
	 * test connect
	 */
	private fun testConnect()
	{
		// reject repeat invoke
		if (isTestConnect)
		{
			return
		}

		// change flag
		isTestConnect = true

		// display state
		connectionTestText.setText(R.string.connection_test_testing)

		// start test connect
		val instance = RequestHelper.instance()
		val requestCallback = object : RequestCallback()
		{

			override fun isRequestOk(code: Int): Boolean
			{
				return code == 204 || code == 200
			}

			override fun onSuccess(code: Int, response: String)
			{
				val elapsed = System.currentTimeMillis() - start
				val result = getString(R.string.connection_test_available, elapsed)
				connectionTestText.text = result
			}

			override fun onFailed(code: Int, msg: String)
			{
				val exceptionMsg = getString(R.string.connection_test_error_status_code, code)
				val result = getString(R.string.connection_test_error, exceptionMsg)
				connectionTestText.setText(R.string.connection_test_fail)
				Snackbar.make(findViewById(android.R.id.content), result, Snackbar.LENGTH_LONG)
					.show()
			}

			override fun onFinished()
			{
				isTestConnect = false
			}
		}
		requestCallback.start = System.currentTimeMillis()
		instance!!["https://www.google.com/generate_204", requestCallback]
	}

	private fun hideCircle()
	{
		if (::fabProgressCircle.isInitialized)
		{
			fabProgressCircle.hide()
		}
	}

	private fun updateState(resetConnectionTest: Boolean = true)
	{
		if (mServiceBoundContext.bgService != null)
		{
			try
			{
				when (mServiceBoundContext.bgService!!.state)
				{
					Constants.State.CONNECTING, Constants.State.STOPPING ->
					{
						fab.backgroundTintList = greyTint
						serviceStarted = false
						fab.setImageResource(R.drawable.ic_start_busy)
						preferences.setEnabled(false)
						fabProgressCircle.show()
						stat.visibility = View.GONE
					}
					Constants.State.CONNECTED ->
					{
						fab.backgroundTintList = greenTint
						serviceStarted = true
						fab.setImageResource(R.drawable.ic_start_connected)
						preferences.setEnabled(false)
						fabProgressCircle.postDelayed({ hideCircle() }, 100)
						stat.visibility = View.VISIBLE
						if (resetConnectionTest)
						{
							connectionTestText.visibility = View.VISIBLE
							connectionTestText.text = getString(R.string.connection_test_pending)
						}
					}
					else ->
					{
						fab.backgroundTintList = greyTint
						serviceStarted = false
						fab.setImageResource(R.drawable.ic_start_idle)
						preferences.setEnabled(true)
						fabProgressCircle.postDelayed({ hideCircle() }, 100)
						stat.visibility = View.GONE
					}
				}
			}
			catch (e: RemoteException)
			{
				e.printStackTrace()
			}

		}
	}

	private fun updateCurrentProfile(): Boolean
	{
		// Check if current profile changed
		if (!preferences.isCurrentProfileInitialized || ShadowsocksApplication.app.profileId() != preferences.currentProfile.id)
		{
			// updated
			var profile = ShadowsocksApplication.app.currentProfile()
			if (profile == null)
			{
				// removed
				val first = ShadowsocksApplication.app.profileManager.firstProfile
				val id: Int
				id = first?.id ?: ShadowsocksApplication.app.profileManager.createDefault().id

				profile = ShadowsocksApplication.app.switchProfile(id)
			}

			updatePreferenceScreen(profile)

			if (serviceStarted)
			{
				serviceLoad()
			}
			return true
		}
		preferences.refreshProfile()
		return false
	}

	private fun updateDynamicShortcuts()
	{
		val disconnectedShortcut = makeToggleShortcut(context = this, isConnected = false)
		ShortcutManagerCompat.pushDynamicShortcut(this, disconnectedShortcut)
	}

	override fun onResume()
	{
		super.onResume()
		ShadowsocksApplication.app.refreshContainerHolder()
		updateState(updateCurrentProfile())
	}

	private fun updatePreferenceScreen(profile: Profile)
	{
		preferences.setProfile(profile)
	}

	override fun onStart()
	{
		super.onStart()
		mServiceBoundContext.registerCallback()
	}

	override fun onStop()
	{
		super.onStop()

		mServiceBoundContext.unregisterCallback()
		clearDialog()
	}

	override fun onDestroy()
	{
		super.onDestroy()
		mServiceBoundContext.detachService()
		BackupManager(this).dataChanged()
		handler.removeCallbacksAndMessages(null)
	}

	fun recovery()
	{
		if (serviceStarted)
		{
			serviceStop()
		}
		val h = R.string.recovering.showProgress()
		ShadowsocksApplication.app.copyAssets()
		h.sendEmptyMessage(0)
	}

	@SuppressLint("BatteryLife")
	@TargetApi(Build.VERSION_CODES.M)
	fun ignoreBatteryOptimization(): Boolean
	{
		var exception: Boolean
		try
		{
			val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
			val packageName = packageName
			var hasIgnored = true
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
			{
				hasIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
			}
			if (!hasIgnored)
			{
				val intent = Intent()
				intent.action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
				intent.data = Uri.parse("package:$packageName")
				startActivity(intent)
			}
			exception = false
		}
		catch (e: Throwable)
		{
			exception = true
		}

		if (exception)
		{
			try
			{
				val intent = Intent(Intent.ACTION_MAIN)
				intent.addCategory(Intent.CATEGORY_LAUNCHER)

				val cn = ComponentName(
					"com.android.settings",
					"com.android.com.settings.Settings@HighPowerApplicationsActivity"
				)

				intent.component = cn
				startActivity(intent)
				exception = false
			}
			catch (e: Throwable)
			{
				exception = true
			}

		}
		return exception
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
	{
		super.onActivityResult(requestCode, resultCode, data)
		if (resultCode == Activity.RESULT_OK)
		{
			serviceLoad()
		}
		else
		{
			cancelStart()
			VayLog.e(TAG, "Failed to start VpnService")
		}
	}

	private fun serviceStop()
	{
		if (mServiceBoundContext.bgService != null)
		{
			try
			{
				mServiceBoundContext.bgService!!.use(-1)
			}
			catch (e: RemoteException)
			{
				e.printStackTrace()
			}

		}
	}

	/**
	 * Called when connect button is clicked.
	 */
	private fun serviceLoad()
	{
		try
		{
			mServiceBoundContext.bgService!!.use(ShadowsocksApplication.app.profileId())
		}
		catch (e: RemoteException)
		{
			e.printStackTrace()
		}

		changeSwitch(false)
	}

	private fun clearDialog()
	{
		if (progressDialog != null && progressDialog!!.isShowing)
		{
			if (!isDestroyed)
			{
				progressDialog!!.dismiss()
			}
			progressDialog = null
		}
	}
}