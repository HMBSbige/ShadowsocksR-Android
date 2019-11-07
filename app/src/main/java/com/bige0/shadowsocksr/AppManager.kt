package com.bige0.shadowsocksr

import android.*
import android.app.*
import android.content.*
import android.content.pm.*
import android.graphics.drawable.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.*
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.*
import com.bige0.shadowsocksr.database.*
import com.bige0.shadowsocksr.utils.*
import java.util.*
import java.util.concurrent.atomic.*

class AppManager : AppCompatActivity(), Toolbar.OnMenuItemClickListener
{
	private var receiverRegistered: Boolean = false
	private var cachedApps = mutableListOf<ProxiedApp>()

	private var proxiedApps = HashSet<String>()
	private lateinit var toolbar: Toolbar
	private lateinit var bypassSwitch: Switch
	private lateinit var appListView: RecyclerView
	private lateinit var loadingView: View
	private var appsLoading: AtomicBoolean = AtomicBoolean()
	private var handler: Handler? = null
	private val profile: Profile? = ShadowsocksApplication.app.currentProfile()

	private fun initProxiedApps(str: String? = profile?.individual)
	{
		if (str.isNullOrEmpty())
		{
			return
		}
		val split = str.lines()
		proxiedApps = HashSet(split)
	}

	override fun onDestroy()
	{
		super.onDestroy()

		if (handler != null)
		{
			handler!!.removeCallbacksAndMessages(null)
			handler = null
		}
	}

	override fun onMenuItemClick(item: MenuItem): Boolean
	{
		val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
		val proxiedAppString: String
		when (item.itemId)
		{
			R.id.action_apply_all ->
			{
				val profiles = ShadowsocksApplication.app.profileManager.allProfiles
				if (profiles.isNotEmpty() && profile != null)
				{
					proxiedAppString = profile.individual
					val inProxiedApp = profile.proxyApps
					val inBypass = profile.bypass
					for (p in profiles)
					{
						p.individual = proxiedAppString
						p.bypass = inBypass
						p.proxyApps = inProxiedApp
						ShadowsocksApplication.app.profileManager.updateProfile(p)
					}
					ToastUtils.showShort(R.string.action_apply_all)
				}
				else
				{
					ToastUtils.showShort(R.string.action_export_err)
				}
				return true
			}
			R.id.action_export ->
			{
				if (profile == null || clipboard == null)
				{
					ToastUtils.showShort(R.string.action_export_err)
					return false
				}
				val bypass = profile.bypass
				proxiedAppString = profile.individual
				val clip = ClipData.newPlainText(Constants.Key.individual, "$bypass\n$proxiedAppString")
				clipboard.setPrimaryClip(clip)
				ToastUtils.showShort(R.string.action_export_msg)
				return true
			}
			R.id.action_import ->
			{
				if (clipboard != null && clipboard.hasPrimaryClip() && profile != null)
				{
					val proxiedAppSequence = clipboard.primaryClip!!.getItemAt(0)
						.text
					if (proxiedAppSequence != null)
					{
						proxiedAppString = proxiedAppSequence.toString()
						if (proxiedAppString.isNotEmpty())
						{
							//TODO
							val i = proxiedAppString.indexOf('\n')
							try
							{
								val enabled: String
								val apps: String
								if (i < 0)
								{
									enabled = proxiedAppString
									apps = ""
								}
								else
								{
									enabled = proxiedAppString.substring(0, i)
									apps = proxiedAppString.substring(i + 1)
								}

								bypassSwitch.isChecked = enabled.toBoolean()
								profile.individual = apps
								ShadowsocksApplication.app.profileManager.updateProfile(profile)
								ToastUtils.showShort(R.string.action_import_msg)
								appListView.visibility = View.GONE
								loadingView.visibility = View.VISIBLE
								initProxiedApps(apps)
								reloadApps()
								return true
							}
							catch (e: Exception)
							{
								//ignore
							}
						}
					}
				}
				ToastUtils.showShort(R.string.action_import_err)
				return false
			}
			else ->
			{
			}
		}
		return false
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		if (profile == null)
		{
			finish()
			return
		}

		handler = Handler()

		setContentView(R.layout.layout_apps)
		toolbar = findViewById(R.id.toolbar)
		toolbar.setTitle(R.string.proxied_apps)
		toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material)
		toolbar.setNavigationOnClickListener {
			val intent = parentActivityIntent
			if (shouldUpRecreateTask(intent) || isTaskRoot)
			{
				TaskStackBuilder.create(this@AppManager)
					.addNextIntentWithParentStack(intent)
					.startActivities()
			}
			else
			{
				finish()
			}
		}
		toolbar.inflateMenu(R.menu.app_manager_menu)
		toolbar.setOnMenuItemClickListener(this)

		if (!profile.proxyApps)
		{
			profile.proxyApps = true
			ShadowsocksApplication.app.profileManager.updateProfile(profile)
		}

		(findViewById<View>(R.id.onSwitch) as Switch).setOnCheckedChangeListener { _, checked ->
			profile.proxyApps = checked
			ShadowsocksApplication.app.profileManager.updateProfile(profile)
			finish()
		}

		bypassSwitch = findViewById(R.id.bypassSwitch)
		bypassSwitch.isChecked = profile.bypass
		bypassSwitch.setOnCheckedChangeListener { _, isChecked ->
			profile.bypass = isChecked
			ShadowsocksApplication.app.profileManager.updateProfile(profile)
		}
		initProxiedApps()
		loadingView = findViewById(R.id.loading)
		appListView = findViewById(R.id.applistview)
		appListView.layoutManager = LinearLayoutManager(this)
		appListView.itemAnimator = DefaultItemAnimator()

		Thread { loadApps() }.start()
	}


	private fun reloadApps()
	{
		if (!appsLoading.compareAndSet(true, false))
		{
			loadApps()
		}
	}

	private fun loadApps()
	{
		if (!appsLoading.compareAndSet(false, true))
		{
			return
		}
		var tempAdapter: AppsAdapter
		do
		{
			appsLoading.set(true)
			tempAdapter = AppsAdapter()
		} while (!appsLoading.compareAndSet(true, false))

		handler?.post {
			appListView.adapter = tempAdapter
			Utils.crossFade(this@AppManager, loadingView, appListView)
		}
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean
	{
		return if (keyCode == KeyEvent.KEYCODE_MENU)
		{
			if (toolbar.isOverflowMenuShowing)
			{
				toolbar.hideOverflowMenu()
			}
			else
			{
				toolbar.showOverflowMenu()
			}
		}
		else super.onKeyUp(keyCode, event)
	}

	private fun getApps(pm: PackageManager): MutableList<ProxiedApp>
	{
		if (!receiverRegistered)
		{
			val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
			filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
			filter.addDataScheme("package")
			ShadowsocksApplication.app.registerReceiver(object : BroadcastReceiver()
														{
															override fun onReceive(context: Context, intent: Intent)
															{
																if (Intent.ACTION_PACKAGE_REMOVED == intent.action || !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
																{
																	synchronized(ProxiedApp::class) {
																		reloadApps()
																	}
																}
															}
														}, filter)
			receiverRegistered = true
		}

		synchronized(cachedApps) {
			if (cachedApps.isEmpty())
			{
				val packagesInfo = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
				for (p in packagesInfo)
				{
					if (p.requestedPermissions != null)
					{
						val requestPermissions = listOf(*p.requestedPermissions)
						if (requestPermissions.contains(Manifest.permission.INTERNET))
						{
							val app = ProxiedApp(pm.getApplicationLabel(p.applicationInfo).toString(), p.packageName, p.applicationInfo.loadIcon(pm))
							cachedApps.add(app)
						}
					}
				}
			}
		}
		return cachedApps
	}

	inner class ProxiedApp(var name: String, var packageName: String, var icon: Drawable)

	private inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener
	{
		private val icon: ImageView = itemView.findViewById(R.id.itemicon)
		private val check: Switch = itemView.findViewById(R.id.itemcheck)
		private lateinit var item: ProxiedApp

		init
		{
			itemView.setOnClickListener(this)
		}

		private fun proxied(): Boolean
		{
			return proxiedApps.contains(item.packageName)
		}

		fun bind(app: ProxiedApp)
		{
			item = app
			icon.setImageDrawable(app.icon)
			check.text = app.name
			check.isChecked = proxied()
		}

		override fun onClick(v: View)
		{
			if (proxied())
			{
				proxiedApps.remove(item.packageName)
				check.isChecked = false
			}
			else
			{
				proxiedApps.add(item.packageName)
				check.isChecked = true
			}
			if (!appsLoading.get() && profile != null)
			{
				profile.individual = Utils.makeString(proxiedApps, "\n")
				ShadowsocksApplication.app.profileManager.updateProfile(profile)
			}
		}
	}

	private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>()
	{
		private val apps: List<ProxiedApp>

		init
		{
			val apps = getApps(packageManager)
			apps.sortWith(Comparator { a, b ->
				val aProxied = proxiedApps.contains(a.packageName)
				if (aProxied xor proxiedApps.contains(b.packageName))
				{
					if (aProxied) -1 else 1
				}
				else
				{
					val result = a.name.compareTo(b.name, ignoreCase = true) < 0
					if (result) 1 else -1
				}
			})
			this.apps = apps
		}

		override fun getItemCount(): Int
		{
			return apps.size
		}

		override fun onBindViewHolder(vh: AppViewHolder, i: Int)
		{
			vh.bind(apps[i])
		}

		override fun onCreateViewHolder(vg: ViewGroup, viewType: Int): AppViewHolder
		{
			val view = LayoutInflater.from(vg.context)
				.inflate(R.layout.layout_apps_item, vg, false)
			return AppViewHolder(view)
		}
	}
}
