package com.bige0.shadowsocksr

import android.annotation.*
import android.app.*
import android.content.*
import android.content.ClipboardManager
import android.content.pm.*
import android.nfc.*
import android.os.*
import android.preference.*
import android.provider.*
import android.text.*
import android.text.style.*
import android.util.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.core.app.TaskStackBuilder
import androidx.recyclerview.widget.*
import com.bige0.shadowsocksr.aidl.*
import com.bige0.shadowsocksr.database.*
import com.bige0.shadowsocksr.network.ping.*
import com.bige0.shadowsocksr.network.request.*
import com.bige0.shadowsocksr.network.ssrsub.*
import com.bige0.shadowsocksr.utils.*
import com.bige0.shadowsocksr.widget.*
import com.github.clans.fab.*
import com.google.android.material.snackbar.*
import net.glxn.qrgen.android.*
import java.nio.charset.*
import java.util.*

class ProfileManagerActivity : AppCompatActivity(), View.OnClickListener, Toolbar.OnMenuItemClickListener, NfcAdapter.CreateNdefMessageCallback, ProfileManager.ProfileAddedListener, SSRSubManager.SSRSubAddedListener
{
	companion object
	{
		private const val MSG_FULL_TEST_FINISH = 1
		private const val requestQrCode = 1
	}

	private var selectedItem: ProfileViewHolder? = null
	private val handler = Handler()

	private lateinit var profilesAdapter: ProfilesAdapter
	private lateinit var ssrSubAdapter: SSRSubAdapter
	private lateinit var clipboard: ClipboardManager

	private lateinit var menu: FloatingActionMenu

	private lateinit var undoManager: UndoSnackBarManager<Profile>

	private var nfcAdapter: NfcAdapter? = null
	private var nfcShareItem: ByteArray? = null
	private var isNfcAvailable: Boolean = false
	private var isNfcBeamEnabled: Boolean = false

	private var testProgressDialog: ProgressDialog? = null
	private var isTesting: Boolean = false
	private var ssTestProcess: GuardedProcess? = null


	private var isSort = false

	private lateinit var mServiceBoundContext: ServiceBoundContext

	private val mProgressHandler = object : Handler(Looper.getMainLooper())
	{
		override fun handleMessage(msg: Message)
		{
			when (msg.what)
			{
				MSG_FULL_TEST_FINISH ->
				{
					if (testProgressDialog != null)
					{
						testProgressDialog!!.dismiss()
						testProgressDialog = null
					}
					finish()
					startActivity(Intent(intent))
				}
				else ->
				{
				}
			}
		}
	}

	private val currentProfilePosition: Int
		get()
		{
			var position = -1
			val profiles = profilesAdapter.profiles
			for (i in profiles.indices)
			{
				val profile = profiles[i]
				if (profile.id == ShadowsocksApplication.app.profileId())
				{
					position = i
				}
			}
			return position
		}

	override fun attachBaseContext(newBase: Context)
	{
		super.attachBaseContext(newBase)
		mServiceBoundContext = object : ServiceBoundContext(newBase)
		{}
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		profilesAdapter = ProfilesAdapter()
		ssrSubAdapter = SSRSubAdapter()

		clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

		when (intent.action)
		{
			Constants.Action.SCAN -> qrCodeScan()
			Constants.Action.SORT -> isSort = true
		}

		setContentView(R.layout.layout_profiles)

		initToolbar()
		initFab()
		initGroupSpinner()

		ShadowsocksApplication.app.profileManager.addProfileAddedListener(this)

		val profilesList = findViewById<RecyclerView>(R.id.profilesList)
		val layoutManager = LinearLayoutManager(this)
		profilesList.layoutManager = layoutManager
		profilesList.itemAnimator = DefaultItemAnimator()
		profilesList.adapter = profilesAdapter
		profilesList.postDelayed({
									 // scroll to position
									 val position = currentProfilePosition
									 profilesList.scrollToPosition(position)
								 }, 100)

		@Suppress("UNCHECKED_CAST")
		undoManager = UndoSnackBarManager(profilesList, { undo ->
			profilesAdapter.undo(undo as SparseArray<Profile>)
		}, { commit -> profilesAdapter.commit(commit as SparseArray<Profile>) })

		if (!isSort)
		{
			ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START or ItemTouchHelper.END)
							{
								override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int)
								{
									val index = viewHolder.adapterPosition
									profilesAdapter.remove(index)
									undoManager.remove(index, (viewHolder as ProfileViewHolder).item)
								}

								override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean
								{
									profilesAdapter.move(viewHolder.adapterPosition, target.adapterPosition)
									return true
								}

							}).attachToRecyclerView(profilesList)
		}

		mServiceBoundContext.attachService(object : IShadowsocksServiceCallback.Stub()
										   {

											   override fun stateChanged(state: Int, profileName: String, msg: String)
											   {
												   // Ignored
											   }

											   override fun trafficUpdated(txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long)
											   {
												   if (selectedItem != null)
												   {
													   selectedItem!!.updateText(txTotal, rxTotal)
												   }
											   }
										   })

		showProfileTipDialog()

		val intent = intent
		if (intent != null)
		{
			handleShareIntent(intent)
		}
	}

	private fun initGroupSpinner()
	{
		val groupSpinner = findViewById<Spinner>(R.id.group_choose_spinner)
		val groupNames = ShadowsocksApplication.app.profileManager.groupNames.toMutableList()
		groupNames.add(0, getString(R.string.allgroups))
		val groupAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, groupNames)
		groupSpinner.adapter = groupAdapter
		groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
		{
			override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long)
			{
				val str = parent.getItemAtPosition(position)
					.toString()
				profilesAdapter.onGroupChange(str)
			}

			override fun onNothingSelected(parent: AdapterView<*>)
			{
			}
		}
	}

	private fun initToolbar()
	{
		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		toolbar.setTitle(R.string.profiles)
		toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
		toolbar.setNavigationOnClickListener { v ->
			val intent = parentActivityIntent
			if (shouldUpRecreateTask(intent) || isTaskRoot)
			{
				TaskStackBuilder.create(this@ProfileManagerActivity)
					.addNextIntentWithParentStack(intent!!)
					.startActivities()
			}
			else
			{
				finish()
			}
		}
		toolbar.inflateMenu(R.menu.profile_manager_menu)
		toolbar.setOnMenuItemClickListener(this)
	}

	private fun showProfileTipDialog()
	{
		if (ShadowsocksApplication.app.settings.getBoolean(Constants.Key.profileTip, true))
		{
			ShadowsocksApplication.app.editor.putBoolean(Constants.Key.profileTip, false)
				.apply()
			AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
				.setTitle(R.string.profile_manager_dialog)
				.setMessage(R.string.profile_manager_dialog_content)
				.setPositiveButton(R.string.gotcha, null)
				.create()
				.show()
		}
	}

	@SuppressLint("RestrictedApi")
	fun initFab()
	{
		menu = findViewById(R.id.menu)
		menu.setClosedOnTouchOutside(true)
		val dm = AppCompatDrawableManager.get()
		val manualAddFAB = findViewById<FloatingActionButton>(R.id.fab_manual_add)
		manualAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_content_create))
		manualAddFAB.setOnClickListener(this)
		val qrCodeAddFAB = findViewById<FloatingActionButton>(R.id.fab_qrcode_add)
		qrCodeAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_image_camera_alt))
		qrCodeAddFAB.setOnClickListener(this)
		val nfcAddFAB = findViewById<FloatingActionButton>(R.id.fab_nfc_add)
		nfcAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_device_nfc))
		nfcAddFAB.setOnClickListener(this)
		val importAddFAB = findViewById<FloatingActionButton>(R.id.fab_import_add)
		importAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_content_paste))
		importAddFAB.setOnClickListener(this)
		val ssrSubAddFAB = findViewById<FloatingActionButton>(R.id.fab_ssr_sub)
		ssrSubAddFAB.setImageDrawable(dm.getDrawable(this, R.drawable.ic_rss))
		ssrSubAddFAB.setOnClickListener(this)
		menu.setOnMenuToggleListener { opened ->
			if (opened)
			{
				val visible = if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) View.VISIBLE else View.GONE
				qrCodeAddFAB.visibility = visible
			}
		}
	}

	override fun onResume()
	{
		super.onResume()
		updateNfcState()
	}

	override fun onNewIntent(intent: Intent)
	{
		super.onNewIntent(intent)
		handleShareIntent(intent)
	}

	private fun qrCodeScan()
	{
		try
		{
			val intent = Intent("com.google.zxing.client.android.SCAN")
			intent.putExtra("SCAN_MODE", "QR_CODE_MODE")

			startActivityForResult(intent, requestQrCode)
		}
		catch (e: Throwable)
		{
			if (::menu.isInitialized)
			{
				menu.toggle(false)
			}
			startActivity(Intent(this, ScannerActivity::class.java))
		}
	}

	override fun onClick(v: View)
	{
		when (v.id)
		{
			R.id.fab_manual_add ->
			{
				menu.toggle(true)
				val profile = ShadowsocksApplication.app.profileManager.createProfile()
				ShadowsocksApplication.app.profileManager.updateProfile(profile)
				ShadowsocksApplication.app.switchProfile(profile.id)
				finish()
			}
			R.id.fab_qrcode_add ->
			{
				menu.toggle(false)
				qrCodeScan()
			}
			R.id.fab_nfc_add -> nfcAdd()
			R.id.fab_import_add -> clipboardImportAdd()
			R.id.fab_ssr_sub ->
			{
				menu.toggle(true)
				ssrSubDialog()
			}
			else ->
			{
			}
		}
	}

	private fun nfcAdd()
	{
		menu.toggle(true)
		val dialog = AlertDialog.Builder(this@ProfileManagerActivity, R.style.Theme_Material_Dialog_Alert)
			.setCancelable(true)
			.setPositiveButton(R.string.gotcha, null)
			.setTitle(R.string.add_profile_nfc_hint_title)
			.create()
		if (!isNfcBeamEnabled)
		{
			dialog.setMessage(getString(R.string.share_message_nfc_disabled))
			dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.turn_on_nfc)) { _, _ -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
		}
		else
		{
			dialog.setMessage(getString(R.string.add_profile_nfc_hint))
		}
		dialog.show()
	}

	private fun clipboardImportAdd()
	{
		menu.toggle(true)
		if (clipboard.hasPrimaryClip())
		{
			val profilesNormal = Parser.findAllSs(clipboard.primaryClip!!.getItemAt(0).text)
			val profilesSsr = Parser.findAllSsr(clipboard.primaryClip!!.getItemAt(0).text)
			val profiles = ArrayList<Profile>()
			if (profilesNormal.isNotEmpty())
			{
				profiles.addAll(profilesNormal)
			}
			if (profilesSsr.isNotEmpty())
			{
				profiles.addAll(profilesSsr)
			}

			if (profiles.isNotEmpty())
			{
				showUrlAddProfileDialog(profiles)
				return
			}
		}
		ToastUtils.showShort(R.string.action_import_err)
	}

	private fun showUrlAddProfileDialog(profiles: List<Profile>)
	{
		val dialog = AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
			.setTitle(R.string.add_profile_dialog)
			.setPositiveButton(android.R.string.yes) { _, _ ->
				for (item in profiles)
				{
					ShadowsocksApplication.app.profileManager.createProfile(item)
				}
			}
			.setNeutralButton(R.string.dr) { _, _ ->
				for (item in profiles)
				{
					ShadowsocksApplication.app.profileManager.createProfileDr(item)
				}
			}
			.setNegativeButton(android.R.string.no) { _, _ -> finish() }
			.setMessage(Utils.makeString(profiles, "\n"))
			.create()
		dialog.show()
	}

	fun ssrSubDialog()
	{
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		val view = View.inflate(this, R.layout.layout_ssr_sub, null)
		val subAutoUpdateEnable = view.findViewById<Switch>(R.id.sw_ssr_sub_autoupdate_enable)

		// adding listener
		ShadowsocksApplication.app.ssrSubManager.addSSRSubAddedListener(this)

		val ssrSubsList = view.findViewById<RecyclerView>(R.id.ssrsubList)
		val layoutManager = LinearLayoutManager(this)
		ssrSubsList.layoutManager = layoutManager
		ssrSubsList.itemAnimator = DefaultItemAnimator()
		ssrSubsList.adapter = ssrSubAdapter
		ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START or ItemTouchHelper.END)
						{
							override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int)
							{
								delSubDialog(viewHolder)
							}

							override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean
							{
								return true
							}
						}).attachToRecyclerView(ssrSubsList)

		if (prefs.getInt(Constants.Key.ssrsub_autoupdate, 0) == 1)
		{
			subAutoUpdateEnable.isChecked = true
		}

		subAutoUpdateEnable.setOnCheckedChangeListener { _, isChecked ->
			val editor = prefs.edit()
			if (isChecked)
			{
				editor.putInt(Constants.Key.ssrsub_autoupdate, 1)
			}
			else
			{
				editor.putInt(Constants.Key.ssrsub_autoupdate, 0)
			}
			editor.apply()
		}

		AlertDialog.Builder(this)
			.setTitle(getString(R.string.add_profile_methods_ssr_sub))
			.setPositiveButton(R.string.ssrsub_ok) { _, _ -> confirmWithUpdateSub() }
			.setNegativeButton(android.R.string.no, null)
			.setNeutralButton(R.string.ssrsub_add) { _, _ -> showAddSSRSubDialog() }
			.setOnCancelListener {
				// remove listener
				ShadowsocksApplication.app.ssrSubManager.removeSSRSubAddedListener(this@ProfileManagerActivity)
			}
			.setView(view)
			.create()
			.show()
	}

	private fun delSubDialog(viewHolder: RecyclerView.ViewHolder)
	{
		val index = viewHolder.adapterPosition
		AlertDialog.Builder(this@ProfileManagerActivity)
			.setTitle(getString(R.string.ssrsub_remove_tip_title))
			.setPositiveButton(R.string.ssrsub_remove_tip_direct) { dialog, which ->
				ssrSubAdapter.remove(index)
				ShadowsocksApplication.app.ssrSubManager.delSSRSub((viewHolder as SSRSubViewHolder).item.id)
			}
			.setNegativeButton(android.R.string.no) { _, _ -> ssrSubAdapter.notifyDataSetChanged() }
			.setNeutralButton(R.string.ssrsub_remove_tip_delete) { _, _ ->
				val group = (viewHolder as SSRSubViewHolder).item.url_group
				val deleteProfiles = ShadowsocksApplication.app.profileManager.getAllProfilesByGroup(group)

				for (profile in deleteProfiles)
				{
					if (profile.id != ShadowsocksApplication.app.profileId())
					{
						ShadowsocksApplication.app.profileManager.delProfile(profile.id)
					}
				}

				val index1 = viewHolder.getAdapterPosition()
				ssrSubAdapter.remove(index1)
				ShadowsocksApplication.app.ssrSubManager.delSSRSub(viewHolder.item.id)

				finish()
				startActivity(Intent(intent))
			}
			.setMessage(getString(R.string.ssrsub_remove_tip))
			.setCancelable(false)
			.create()
			.show()
	}

	private fun confirmWithUpdateSub()
	{
		testProgressDialog = ProgressDialog.show(this@ProfileManagerActivity, getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true)

		// start update sub
		val subs = ShadowsocksApplication.app.ssrSubManager.allSSRSubs
		SubUpdateHelper.instance()
			.updateSub(subs, 0, object : SubUpdateCallback()
			{
				override fun onFailed()
				{
					ToastUtils.showShort(R.string.ssrsub_error)
				}

				override fun onFinished()
				{
					if (testProgressDialog != null)
					{
						testProgressDialog!!.dismiss()
					}

					finish()
					startActivity(Intent(intent))
				}
			})
	}

	private fun showAddSSRSubDialog()
	{
		val li = LayoutInflater.from(this)
		val myView = li.inflate(R.layout.layout_edittext, null)
		val cDialog = AlertDialog.Builder(this@ProfileManagerActivity)
		cDialog.setView(myView)
			.setTitle(getString(R.string.ssrsub_add))
			.setPositiveButton(android.R.string.ok) { _, _ ->
				val editText = myView.findViewById<EditText>(R.id.editTextInput)
				// add ssr sub by url
				val subUrl = editText.text.toString()
				if (!TextUtils.isEmpty(subUrl))
				{
					addSSRSubByUrl(subUrl.trim { it <= ' ' })
				}
			}
			.setNegativeButton(android.R.string.no) { _, _ -> ssrSubDialog() }
		val dialog = cDialog.create()
		dialog.show()
	}

	private fun addSSRSubByUrl(subUrl: String)
	{
		if (subUrl.isNotEmpty())
		{
			// show progress dialog
			testProgressDialog = ProgressDialog.show(this@ProfileManagerActivity, getString(R.string.ssrsub_progres), getString(R.string.ssrsub_progres_text), false, true)

			// request sub content
			RequestHelper.instance()!![subUrl, object : RequestCallback()
			{
				override fun onSuccess(code: Int, response: String)
				{
					val ssrSub = SubUpdateHelper.parseSSRSub(subUrl, response)
					ShadowsocksApplication.app.ssrSubManager.createSSRSub(ssrSub)
				}

				override fun onFailed(code: Int, msg: String)
				{
					ToastUtils.showShort(getString(R.string.ssrsub_error))
				}

				override fun onFinished()
				{
					testProgressDialog!!.dismiss()
					ssrSubDialog()
				}
			}]
		}
		else
		{
			ssrSubDialog()
		}
	}

	private fun updateNfcState()
	{
		isNfcAvailable = false
		isNfcBeamEnabled = false
		nfcAdapter = NfcAdapter.getDefaultAdapter(this)
		if (nfcAdapter != null)
		{
			isNfcAvailable = true
			if (nfcAdapter!!.isEnabled)
			{
				if (nfcAdapter!!.isNdefPushEnabled)
				{
					isNfcBeamEnabled = true
					nfcAdapter!!.setNdefPushMessageCallback(null, this@ProfileManagerActivity)
				}
			}
		}
	}

	private fun handleShareIntent(intent: Intent)
	{
		var sharedStr: String? = null
		val action = intent.action
		if (Intent.ACTION_VIEW == action)
		{
			sharedStr = intent.data!!.toString()
		}
		else if (NfcAdapter.ACTION_NDEF_DISCOVERED == action)
		{
			val rawMsg = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
			if (!rawMsg.isNullOrEmpty())
			{
				sharedStr = String((rawMsg[0] as NdefMessage).records[0].payload)
			}
		}

		if (sharedStr.isNullOrEmpty())
		{
			return
		}

		val profiles = Utils.mergeList(Parser.findAllSs(sharedStr), Parser.findAllSsr(sharedStr))

		if (profiles.isEmpty())
		{
			finish()
			return
		}
		val dialog = AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
			.setTitle(R.string.add_profile_dialog)
			.setPositiveButton(android.R.string.yes) { _, _ ->
				for (profile in profiles)
				{
					ShadowsocksApplication.app.profileManager.createProfile(profile)
				}
			}
			.setNeutralButton(R.string.dr) { _, _ ->
				for (profile in profiles)
				{
					ShadowsocksApplication.app.profileManager.createProfileDr(profile)
				}
			}
			.setNegativeButton(android.R.string.no) { _, _ -> finish() }
			.setMessage(Utils.makeString(profiles, "\n"))
			.create()
		dialog.show()
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
	{
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == requestQrCode)
		{
			when (resultCode)
			{
				Activity.RESULT_OK ->
				{
					val contents = data!!.getStringExtra("SCAN_RESULT")
					if (TextUtils.isEmpty(contents))
					{
						return
					}
					val profiles = Utils.mergeList(Parser.findAllSs(contents!!), Parser.findAllSsr(contents))
					if (profiles.isEmpty())
					{
						finish()
						return
					}
					val dialog = AlertDialog.Builder(this, R.style.Theme_Material_Dialog_Alert)
						.setTitle(R.string.add_profile_dialog)
						.setPositiveButton(android.R.string.yes) { _, _ ->
							for (profile in profiles)
							{
								ShadowsocksApplication.app.profileManager.createProfile(profile)
							}
						}
						.setNeutralButton(R.string.dr) { _, _ ->
							for (profile in profiles)
							{
								ShadowsocksApplication.app.profileManager.createProfileDr(profile)
							}
						}
						.setNegativeButton(android.R.string.no) { _, _ -> finish() }
						.setMessage(Utils.makeString(profiles, "\n"))
						.create()
					dialog.show()
				}
				Activity.RESULT_CANCELED ->
				{
					//handle cancel
				}
			}
		}
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
	}

	override fun onProfileAdded(profile: Profile)
	{
		profilesAdapter.add(profile)
	}

	override fun onSSRSubAdded(ssrSub: SSRSub)
	{
		ssrSubAdapter.add(ssrSub)
	}

	override fun onDestroy()
	{
		mServiceBoundContext.detachService()

		if (ssTestProcess != null)
		{
			ssTestProcess!!.destroy()
			ssTestProcess = null
		}

		undoManager.flush()
		ShadowsocksApplication.app.profileManager.removeProfileAddedListener(this)
		super.onDestroy()
	}

	override fun onBackPressed()
	{
		if (menu.isOpened)
		{
			menu.close(true)
		}
		else
		{
			super.onBackPressed()
		}
	}

	override fun createNdefMessage(nfcEvent: NfcEvent): NdefMessage
	{
		return NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_ABSOLUTE_URI, nfcShareItem, byteArrayOf(), nfcShareItem)))
	}

	override fun onMenuItemClick(item: MenuItem): Boolean
	{
		when (item.itemId)
		{
			R.id.action_export ->
			{
				val allProfiles = ShadowsocksApplication.app.profileManager.allProfiles
				if (allProfiles.isNotEmpty())
				{
					clipboard.setPrimaryClip(ClipData.newPlainText(null, Utils.makeString(allProfiles, "\n")))
					ToastUtils.showShort(R.string.action_export_msg)
				}
				else
				{
					ToastUtils.showShort(R.string.action_export_err)
				}
				return true
			}
			R.id.action_sort ->
			{
				finish()
				startActivity(Intent(Constants.Action.SORT))
				return true
			}
			R.id.action_full_test ->
			{
				pingAll()
				return true
			}
			else ->
			{
			}
		}
		return false
	}

	private fun pingAll()
	{
		// reject repeat operation
		if (isTesting)
		{
			return
		}

		isTesting = true
		testProgressDialog = ProgressDialog.show(this, getString(R.string.tips_testing), getString(R.string.tips_testing), false, true) {
			// TODO Auto-generated method stub
			// Do something...
			if (testProgressDialog != null)
			{
				testProgressDialog = null
			}

			isTesting = false

			finish()
			startActivity(Intent(intent))
		}

		// get profile list
		val profiles = profilesAdapter.profiles
		// start test
		PingHelper.instance()
			.pingAll(this, profiles, object : PingCallback()
			{
				override fun onSuccess(profile: Profile, elapsed: Long)
				{
					profile.elapsed = elapsed
					ShadowsocksApplication.app.profileManager.updateProfile(profile)

					// set progress message
					setProgressMessage("${profile.name}\n$resultMsg")
				}

				override fun onFailed(profile: Profile?)
				{
					if (profile != null)
					{
						profile.elapsed = -1
						ShadowsocksApplication.app.profileManager.updateProfile(profile)

						// set progress message
						setProgressMessage(resultMsg)
					}
				}

				private fun setProgressMessage(message: String)
				{
					testProgressDialog?.setMessage(message)
				}

				override fun onFinished(profile: Profile?)
				{
					mProgressHandler.sendEmptyMessageDelayed(MSG_FULL_TEST_FINISH, 2000)
					PingHelper.instance()
						.releaseTempActivity()
				}
			})
	}

	private inner class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnKeyListener
	{
		lateinit var item: Profile
		private val text: CheckedTextView = itemView.findViewById(android.R.id.text1)

		init
		{
			itemView.setOnClickListener(this)
			itemView.setOnKeyListener(this)

			initShareBtn()
			initPingBtn()
		}

		private fun initShareBtn()
		{
			val shareBtn = itemView.findViewById<ImageView>(R.id.share)
			shareBtn.setOnClickListener {
				val url = item.toString()
				if (isNfcBeamEnabled)
				{
					nfcAdapter!!.setNdefPushMessageCallback(this@ProfileManagerActivity, this@ProfileManagerActivity)
					nfcShareItem = url.toByteArray(Charset.forName("UTF-8"))
				}
				val image = ImageView(this@ProfileManagerActivity)
				image.layoutParams = LinearLayout.LayoutParams(-1, -1)
				val qrCode = (QRCode.from(url).withSize(Utils.dpToPx(this@ProfileManagerActivity, 250), Utils.dpToPx(this@ProfileManagerActivity, 250)) as QRCode).bitmap()
				image.setImageBitmap(qrCode)

				val dialog = AlertDialog.Builder(this@ProfileManagerActivity, R.style.Theme_Material_Dialog_Alert)
					.setCancelable(true)
					.setPositiveButton(R.string.close, null)
					.setNegativeButton(R.string.copy_url) { _, _ -> clipboard.setPrimaryClip(ClipData.newPlainText(null, url)) }
					.setView(image)
					.setTitle(R.string.share)
					.create()
				if (!isNfcAvailable)
				{
					dialog.setMessage(getString(R.string.share_message_without_nfc))
				}
				else if (!isNfcBeamEnabled)
				{
					dialog.setMessage(getString(R.string.share_message_nfc_disabled))
					dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.turn_on_nfc)) { _, _ -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
				}
				else
				{
					dialog.setMessage(getString(R.string.share_message))
					dialog.setOnDismissListener { nfcAdapter!!.setNdefPushMessageCallback(null, this@ProfileManagerActivity) }
				}
				dialog.show()
			}

			shareBtn.setOnLongClickListener {
				Utils.positionToast(Toast.makeText(this@ProfileManagerActivity, R.string.share, Toast.LENGTH_SHORT), shareBtn, window, 0, Utils.dpToPx(this@ProfileManagerActivity, 8))
					.show()
				true
			}
		}

		private fun initPingBtn()
		{
			val pingBtn = itemView.findViewById<ImageView>(R.id.ping_single)
			pingBtn.setOnClickListener {
				item.elapsed = 0
				val singleTestProgressDialog = ProgressDialog.show(this@ProfileManagerActivity, getString(R.string.tips_testing), getString(R.string.tips_testing), false, true)
				PingHelper.instance()
					.ping(this@ProfileManagerActivity, item, object : PingCallback()
					{
						override fun onSuccess(profile: Profile, elapsed: Long)
						{
							if (profile.elapsed == 0L)
							{
								profile.elapsed = elapsed
							}
							else if (profile.elapsed > elapsed)
							{
								profile.elapsed = elapsed
							}

							ShadowsocksApplication.app.profileManager.updateProfile(profile)
							updateText(profile.tx, profile.rx, elapsed)
						}

						override fun onFailed(profile: Profile?)
						{
						}

						override fun onFinished(profile: Profile?)
						{
							Snackbar.make(findViewById(android.R.id.content), resultMsg, Snackbar.LENGTH_LONG)
								.show()
							singleTestProgressDialog.dismiss()
							PingHelper.instance()
								.releaseTempActivity()
						}
					})
			}

			pingBtn.setOnLongClickListener {
				Utils.positionToast(Toast.makeText(this@ProfileManagerActivity, R.string.ping, Toast.LENGTH_SHORT), pingBtn, window, 0, Utils.dpToPx(this@ProfileManagerActivity, 8))
					.show()
				true
			}
		}

		fun updateText(txTotal: Long = 0, rxTotal: Long = 0, elapsedInput: Long = -1)
		{
			val builder = SpannableStringBuilder()
			val tx = item.tx + txTotal
			val rx = item.rx + rxTotal
			var elapsed = item.elapsed
			if (elapsedInput != -1L)
			{
				elapsed = elapsedInput
			}
			builder.append(item.name)
			if (tx != 0L || rx != 0L || elapsed != 0L || item.url_group !== "")
			{
				val start = builder.length
				builder.append(getString(R.string.stat_profiles, TrafficMonitor.formatTraffic(tx), TrafficMonitor.formatTraffic(rx), elapsed.toString(), item.url_group))
				builder.setSpan(TextAppearanceSpan(this@ProfileManagerActivity, android.R.style.TextAppearance_Small), start + 1, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			handler.post { text.text = builder }
		}

		fun bind(item: Profile)
		{
			this.item = item
			updateText()
			if (item.id == ShadowsocksApplication.app.profileId())
			{
				text.isChecked = true
				selectedItem = this
			}
			else
			{
				text.isChecked = false
				if (this == selectedItem)
				{
					selectedItem = null
				}
			}
		}

		override fun onClick(v: View)
		{
			ShadowsocksApplication.app.switchProfile(item.id)
			finish()
		}

		override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean
		{
			return if (event.action == KeyEvent.ACTION_DOWN)
			{
				when (keyCode)
				{
					KeyEvent.KEYCODE_DPAD_LEFT ->
					{
						val index = adapterPosition
						if (index >= 0)
						{
							profilesAdapter.remove(index)
							undoManager.remove(index, item)
							true
						}
						else
						{
							false
						}
					}
					else -> false
				}
			}
			else
			{
				false
			}
		}
	}

	private inner class ProfilesAdapter : RecyclerView.Adapter<ProfileViewHolder>()
	{
		private var list: MutableList<Profile>
		var profiles: MutableList<Profile>

		init
		{
			list = if (isSort)
			{
				ShadowsocksApplication.app.profileManager.allProfilesByElapsed.toMutableList()
			}
			else
			{
				ShadowsocksApplication.app.profileManager.allProfiles.toMutableList()
			}

			profiles = list
		}


		fun onGroupChange(groupName: String)
		{
			if (getString(R.string.allgroups) == groupName)
			{
				list = if (isSort)
				{
					ShadowsocksApplication.app.profileManager.allProfilesByElapsed.toMutableList()
				}
				else
				{
					ShadowsocksApplication.app.profileManager.allProfiles.toMutableList()
				}
			}
			else
			{
				list = if (isSort)
				{
					ShadowsocksApplication.app.profileManager.getAllProfilesByGroupOrderByElapse(groupName)
						.toMutableList()
				}
				else
				{
					ShadowsocksApplication.app.profileManager.getAllProfilesByGroup(groupName)
						.toMutableList()
				}
			}

			profiles = list

			notifyDataSetChanged()
		}

		override fun getItemCount(): Int
		{
			return profiles.size
		}

		override fun onBindViewHolder(vh: ProfileViewHolder, i: Int)
		{
			vh.bind(profiles[i])
		}

		override fun onCreateViewHolder(vg: ViewGroup, viewType: Int): ProfileViewHolder
		{
			val view = LayoutInflater.from(vg.context)
				.inflate(R.layout.layout_profiles_item, vg, false)
			return ProfileViewHolder(view)
		}

		fun add(item: Profile)
		{
			undoManager.flush()
			val pos = itemCount
			profiles.add(item)
			notifyItemInserted(pos)
		}

		fun move(from: Int, to: Int)
		{
			undoManager.flush()
			val step = if (from < to) 1 else -1
			val first = profiles[from]
			var previousOrder = profiles[from].userOrder
			var i = from
			while (true)
			{
				if (step > 0 && i >= to)
				{
					break
				}
				else if (step < 0 && i <= to)
				{
					break
				}

				val next = profiles[i + step]
				val order = next.userOrder
				next.userOrder = previousOrder
				previousOrder = order
				profiles[i] = next
				ShadowsocksApplication.app.profileManager.updateProfile(next)
				i += step
			}
			first.userOrder = previousOrder
			profiles[to] = first
			ShadowsocksApplication.app.profileManager.updateProfile(first)
			notifyItemMoved(from, to)
		}

		fun remove(pos: Int)
		{
			val remove = profiles.removeAt(pos)
			ShadowsocksApplication.app.profileManager.delProfile(remove.id)
			notifyItemRemoved(pos)
		}

		fun undo(actions: SparseArray<Profile>)
		{
			for (index in 0 until actions.size())
			{
				val item = actions.get(index)
				if (item != null)
				{
					profiles.add(index, item)
					notifyItemInserted(index)
				}
			}
		}

		fun commit(actions: SparseArray<Profile>)
		{
			for (index in 0 until actions.size())
			{
				val item = actions.get(index)
				if (item != null)
				{
					ShadowsocksApplication.app.profileManager.delProfile(item.id)
					if (item.id == ShadowsocksApplication.app.profileId())
					{
						ShadowsocksApplication.app.profileId(-1)
					}
				}
			}
		}
	}

	private inner class SSRSubViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnKeyListener, View.OnLongClickListener
	{
		lateinit var item: SSRSub
		private val text: TextView = itemView.findViewById(android.R.id.text2)
		private var showUrlFlag = true

		init
		{
			itemView.setOnClickListener(this)
			itemView.setOnLongClickListener(this)
		}

		fun updateText(isShowUrl: Boolean = false)
		{
			val builder = SpannableStringBuilder()
			builder.append(item.url_group)
			if (isShowUrl)
			{
				val start = builder.length
				builder.append("\n")
				builder.append(item.url)
				builder.setSpan(TextAppearanceSpan(this@ProfileManagerActivity, android.R.style.TextAppearance_Small), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
			handler.post { text.text = builder }
		}

		fun copyText()
		{
			val subUrl = item.url
			if (subUrl.isNotEmpty())
			{
				clipboard.setPrimaryClip(ClipData.newPlainText(null, subUrl))
				ToastUtils.showShort(R.string.action_export_msg)
			}
			else
			{
				ToastUtils.showShort(R.string.action_export_err)
			}
		}

		fun bind(item: SSRSub)
		{
			this.item = item
			updateText()
		}

		override fun onClick(v: View)
		{
			updateText(showUrlFlag)
			showUrlFlag = !showUrlFlag
		}

		override fun onLongClick(v: View): Boolean
		{
			copyText()
			return true
		}

		override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean
		{
			return true
		}
	}

	private inner class SSRSubAdapter : RecyclerView.Adapter<SSRSubViewHolder>()
	{
		private var profiles = ShadowsocksApplication.app.ssrSubManager.allSSRSubs.toMutableList()

		override fun getItemCount(): Int
		{
			return profiles.size
		}

		override fun onBindViewHolder(vh: SSRSubViewHolder, i: Int)
		{
			vh.bind(profiles[i])
		}

		override fun onCreateViewHolder(vg: ViewGroup, viewType: Int): SSRSubViewHolder
		{
			val view = LayoutInflater.from(vg.context)
				.inflate(R.layout.layout_ssr_sub_item, vg, false)
			return SSRSubViewHolder(view)
		}

		fun add(item: SSRSub)
		{
			undoManager.flush()
			val pos = itemCount
			profiles.add(item)
			notifyItemInserted(pos)
		}

		fun remove(pos: Int)
		{
			profiles.removeAt(pos)
			notifyItemRemoved(pos)
		}
	}
}
