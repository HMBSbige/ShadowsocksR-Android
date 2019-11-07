package com.bige0.shadowsocksr

import android.content.pm.*
import android.content.res.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.*
import androidx.appcompat.widget.Toolbar
import androidx.core.content.*
import androidx.recyclerview.widget.*
import com.bige0.shadowsocksr.database.*
import com.bige0.shadowsocksr.utils.*
import java.util.*

class ShadowsocksQuickSwitchActivity : AppCompatActivity()
{
	private lateinit var profilesAdapter: ProfilesAdapter

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.layout_quick_switch)
		profilesAdapter = ProfilesAdapter()

		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		toolbar.setTitle(R.string.quick_switch)

		val profilesList = findViewById<RecyclerView>(R.id.profilesList)
		val lm = LinearLayoutManager(this)
		profilesList.layoutManager = lm
		profilesList.itemAnimator = DefaultItemAnimator()
		profilesList.adapter = profilesAdapter
		if (ShadowsocksApplication.app.profileId() >= 0)
		{
			var position = 0
			val profiles = profilesAdapter.profiles
			for (i in profiles!!.indices)
			{
				val profile = profiles[i]
				if (profile.id == ShadowsocksApplication.app.profileId())
				{
					position = i + 1
					break
				}
			}
			lm.scrollToPosition(position)
		}

		if (Build.VERSION.SDK_INT >= 25)
		{
			getSystemService<ShortcutManager>()!!.reportShortcutUsed("switch")
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

		fun bind(item: Profile)
		{
			this.item = item
			text.text = item.name
			text.isChecked = item.id == ShadowsocksApplication.app.profileId()
		}

		override fun onClick(v: View)
		{
			ShadowsocksApplication.app.switchProfile(item!!.id)
			Utils.startSsService(this@ShadowsocksQuickSwitchActivity)
			finish()
		}
	}

	private inner class ProfilesAdapter : RecyclerView.Adapter<ProfileViewHolder>()
	{
		private val name: String
		var profiles: List<Profile>? = null

		init
		{
			val profiles = ShadowsocksApplication.app.profileManager.allProfiles
			if (profiles.isEmpty())
			{
				this.profiles = ArrayList()
			}
			else
			{
				this.profiles = profiles
			}

			val version = if (Build.VERSION.SDK_INT >= 21) "material" else "holo"
			name = "select_dialog_singlechoice_$version"
		}

		override fun onCreateViewHolder(vg: ViewGroup, viewType: Int): ProfileViewHolder
		{
			val view = LayoutInflater.from(vg.context)
				.inflate(Resources.getSystem().getIdentifier(name, "layout", "android"), vg, false)
			return ProfileViewHolder(view)
		}

		override fun onBindViewHolder(vh: ProfileViewHolder, i: Int)
		{
			vh.bind(profiles!![i])
		}

		override fun getItemCount(): Int
		{
			return profiles!!.size
		}
	}
}
