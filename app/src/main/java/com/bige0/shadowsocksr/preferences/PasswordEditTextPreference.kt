package com.bige0.shadowsocksr.preferences

import android.content.*
import android.preference.*
import android.util.*

class PasswordEditTextPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet, defStyle: Int = android.R.attr.editTextPreferenceStyle) : EditTextPreference(context, attrs, defStyle)
{
	private val mDefaultSummary: CharSequence = summary

	override fun setText(text: String)
	{
		super.setText(text)
		summary = text
	}

	override fun setSummary(summary: CharSequence)
	{
		if (summary.isEmpty())
		{
			super.setSummary(mDefaultSummary)
		}
		else
		{
			super.setSummary("*".repeat(summary.length))
		}
	}
}
