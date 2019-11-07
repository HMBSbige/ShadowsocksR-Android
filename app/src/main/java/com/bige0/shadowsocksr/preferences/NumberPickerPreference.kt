package com.bige0.shadowsocksr.preferences

import android.content.*
import android.content.res.*
import android.os.*
import android.util.*
import android.view.*
import android.widget.*
import com.bige0.shadowsocksr.*

class NumberPickerPreference(context: Context, attrs: AttributeSet) : SummaryDialogPreference(context, attrs)
{
	private val picker: NumberPicker = NumberPicker(context)

	var value: Int = 0
		set(i)
		{
			if (i == value)
			{
				return
			}
			picker.value = i
			field = picker.value
			persistInt(value)
			notifyChanged()
		}

	var min: Int
		get() = picker.minValue
		set(value)
		{
			picker.minValue = value
		}

	init
	{
		val a = context.obtainStyledAttributes(attrs, R.styleable.NumberPickerPreference)
		min = a.getInt(R.styleable.NumberPickerPreference_min, 0)
		setMax(a.getInt(R.styleable.NumberPickerPreference_max, Integer.MAX_VALUE - 1))
		a.recycle()
	}

	private fun setMax(value: Int)
	{
		picker.maxValue = value
	}

	override fun showDialog(state: Bundle?)
	{
		super.showDialog(state)
		val window = dialog.window
		window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
	}

	override fun onCreateDialogView(): View
	{
		val parent = picker.parent as ViewGroup?
		parent?.removeView(picker)
		return picker
	}

	override fun onDialogClosed(positiveResult: Boolean)
	{
		picker.clearFocus()
		super.onDialogClosed(positiveResult)

		if (positiveResult)
		{
			val value = picker.value
			if (callChangeListener(value))
			{
				this.value = value
				return
			}
		}
		picker.value = value
	}

	override fun onGetDefaultValue(a: TypedArray, index: Int): Any
	{
		return a.getInt(index, min)
	}

	override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any)
	{
		val defValue = defaultValue as Int
		value = if (restorePersistedValue) getPersistedInt(defValue) else defValue
	}

	override val summaryValue: Any?
		get() = value
}
