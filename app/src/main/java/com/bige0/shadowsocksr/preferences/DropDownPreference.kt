package com.bige0.shadowsocksr.preferences

import android.content.*
import android.content.res.*
import android.util.*
import android.view.*
import android.widget.*
import androidx.appcompat.widget.*
import com.bige0.shadowsocksr.*

class DropDownPreference(mContext: Context, attrs: AttributeSet) : SummaryPreference(mContext, attrs)
{
	private val mAdapter: ArrayAdapter<String> = ArrayAdapter(mContext, android.R.layout.simple_spinner_dropdown_item)
	private val mSpinner: AppCompatSpinner = AppCompatSpinner(mContext)

	private var mEntries: Array<CharSequence>? = null
	private var mEntryValues: Array<CharSequence>? = null
	private var mSelectedIndex = -1

	init
	{
		mSpinner.visibility = View.INVISIBLE
		mSpinner.adapter = mAdapter
		mSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener
		{
			override fun onNothingSelected(parent: AdapterView<*>)
			{
			}

			override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long)
			{
				val value = getValue(position)
				if (position != mSelectedIndex && callChangeListener(value))
				{
					setValue(position, value)
				}
			}
		}

		setOnPreferenceClickListener {
			// TODO: not working with scrolling
			// mSpinner.setDropDownVerticalOffset(Utils.dpToPx(getContext, -48 * mSelectedIndex).toInt)
			mSpinner.performClick()
			true
		}

		val a = mContext.obtainStyledAttributes(attrs, R.styleable.DropDownPreference)
		setEntries(a.getTextArray(R.styleable.DropDownPreference_android_entries))
		setEntryValues(a.getTextArray(R.styleable.DropDownPreference_android_entryValues))
		a.recycle()
	}

	override fun getSummaryValue(): Any?
	{
		return getEntry()
	}

	private fun setEntries(entries: Array<CharSequence>?)
	{
		mEntries = entries
		mAdapter.clear()
		if (entries != null)
		{
			for (entry in entries)
			{
				mAdapter.add(entry.toString())
			}
		}
	}

	private fun setEntryValues(entryValues: Array<CharSequence>?)
	{
		mEntryValues = entryValues
	}

	private fun getValue(index: Int): String?
	{
		return if (mEntryValues == null)
		{
			null
		}
		else
		{
			mEntryValues!![index].toString()
		}
	}

	private fun setValue(index: Int, value: String?)
	{
		persistString(value)
		mSelectedIndex = index
		mSpinner.setSelection(index)
		notifyChanged()
	}

	fun getValue(): String?
	{
		return if (mEntryValues == null || mSelectedIndex < 0)
		{
			null
		}
		else
		{
			mEntryValues!![mSelectedIndex].toString()
		}
	}

	fun setValue(value: String?)
	{
		setValue(findIndexOfValue(value), value)
	}

	private fun getEntry(): CharSequence?
	{
		val index = mSelectedIndex
		return if (index >= 0 && mEntries != null)
		{
			mEntries!![index]
		}
		else
		{
			null
		}
	}

	private fun findIndexOfValue(value: String?): Int
	{
		if (value != null && mEntryValues != null)
		{
			var i = mEntryValues!!.size - 1
			while (i >= 0)
			{
				if (mEntryValues!![i] == value)
				{
					return i
				}
				i -= 1
			}
		}
		return -1
	}

	override fun onGetDefaultValue(a: TypedArray, index: Int): Any?
	{
		return a.getString(index)
	}

	override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any)
	{
		val value = if (restorePersistedValue) getPersistedString(getValue()) else defaultValue.toString()
		setValue(value)
	}

	override fun onBindView(view: View)
	{
		super.onBindView(view)
		val parent = mSpinner.parent as ViewGroup?

		if (view === parent)
		{
			return
		}

		parent?.removeView(mSpinner)

		(view as ViewGroup).addView(mSpinner, 0)
		val lp = mSpinner.layoutParams
		lp.width = 0
		mSpinner.layoutParams = lp
	}
}
