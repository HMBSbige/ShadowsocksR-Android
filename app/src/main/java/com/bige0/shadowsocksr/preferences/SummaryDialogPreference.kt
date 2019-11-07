package com.bige0.shadowsocksr.preferences

import android.content.*
import android.preference.*
import android.util.*
import java.util.*

abstract class SummaryDialogPreference internal constructor(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs)
{
	abstract val summaryValue: Any?

	override fun getSummary(): CharSequence
	{
		return String.format(Locale.ENGLISH, super.getSummary().toString(), summaryValue)
	}
}
