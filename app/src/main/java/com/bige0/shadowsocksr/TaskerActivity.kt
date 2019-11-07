package com.bige0.shadowsocksr

import android.app.*
import android.content.res.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.*
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.*
import com.bige0.shadowsocksr.ShadowsocksApplication.Companion.app
import com.bige0.shadowsocksr.database.*
import com.bige0.shadowsocksr.utils.*

class TaskerActivity : AppCompatActivity()
{
	private lateinit var taskerOption: TaskerSettings
	private lateinit var mSwitch: Switch
	private lateinit var profilesAdapter: ProfilesAdapter

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.layout_tasker)

		profilesAdapter = ProfilesAdapter()

		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		toolbar.setTitle(R.string.app_name)
		toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
		toolbar.setNavigationOnClickListener { finish() }

		taskerOption = TaskerSettings.fromIntent(intent)
		mSwitch = findViewById(R.id.serviceSwitch)
		mSwitch.isChecked = taskerOption.switchOn
		val profilesList = findViewById<RecyclerView>(R.id.profilesList)
		val lm = LinearLayoutManager(this)
		profilesList.layoutManager = lm
		profilesList.itemAnimator = DefaultItemAnimator()
		profilesList.adapter = profilesAdapter

		if (taskerOption.profileId >= 0)
		{
			var position = 0
			val profiles = profilesAdapter.getCurrentProfiles()
			for ((i, profile) in profiles.withIndex())
			{
				if (profile.id == taskerOption.profileId)
				{
					position = i + 1
					break
				}
			}
			lm.scrollToPosition(position)
		}
	}

	private inner class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener
	{
		private var item: Profile? = null
		private val text: CheckedTextView

		init
		{
			val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
			view.setBackgroundResource(typedArray.getResourceId(0, 0))
			typedArray.recycle()

			text = itemView.findViewById(android.R.id.text1)
			itemView.setOnClickListener(this)
		}

		fun bindDefault()
		{
			item = null
			text.setText(R.string.profile_default)
			text.isChecked = taskerOption.profileId < 0
		}

		fun bind(item: Profile)
		{
			this.item = item
			text.text = item.name
			text.isChecked = taskerOption.profileId == item.id
		}

		override fun onClick(v: View)
		{
			taskerOption.switchOn = mSwitch.isChecked
			taskerOption.profileId = if (item == null) -1 else item!!.id
			setResult(Activity.RESULT_OK, taskerOption.toIntent(this@TaskerActivity))
			finish()
		}
	}

	private inner class ProfilesAdapter : RecyclerView.Adapter<ProfileViewHolder>()
	{
		private val name: String

		init
		{
			val version = if (Build.VERSION.SDK_INT >= 21) "material" else "holo"
			name = "select_dialog_singlechoice_$version"
		}

		fun getCurrentProfiles(): List<Profile>
		{
			return app.profileManager.allProfiles
		}

		override fun getItemCount(): Int
		{
			return 1 + getCurrentProfiles().size
		}

		override fun onBindViewHolder(vh: ProfileViewHolder, i: Int)
		{
			if (i == 0)
			{
				vh.bindDefault()
			}
			else
			{
				vh.bind(getCurrentProfiles()[i - 1])
			}
		}

		override fun onCreateViewHolder(vg: ViewGroup, i: Int): ProfileViewHolder
		{
			val view = LayoutInflater.from(vg.context)
				.inflate(Resources.getSystem().getIdentifier(name, "layout", "android"), vg, false)
			return ProfileViewHolder(view)
		}
	}
}
