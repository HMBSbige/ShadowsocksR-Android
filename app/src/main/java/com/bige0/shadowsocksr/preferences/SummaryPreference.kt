package com.bige0.shadowsocksr.preferences

import android.content.*
import android.preference.*
import android.util.*
import java.util.*

abstract class SummaryPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs)
{
	abstract fun getSummaryValue(): Any?

	override fun getSummary(): CharSequence
	{
		return String.format(Locale.ENGLISH, super.getSummary().toString(), getSummaryValue())
	}
}
