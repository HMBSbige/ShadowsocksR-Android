package com.bige0.shadowsocksr.preferences

import android.content.*
import android.preference.*
import android.util.*

class SummaryEditTextPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int = android.R.attr.editTextPreferenceStyle) : EditTextPreference(context, attrs, defStyleAttr)
{
	private val mDefaultSummary: CharSequence? = summary

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
			super.setSummary(summary)
		}
	}
}
