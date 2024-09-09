package com.bige0.shadowsocksr.shortcuts

import android.content.*
import androidx.core.content.pm.*
import androidx.core.graphics.drawable.*
import com.bige0.shadowsocksr.*

fun makeToggleShortcut(context: Context, isConnected: Boolean): ShortcutInfoCompat
{
	val icon = when (isConnected)
	{
		true -> R.drawable.ic_qu_shadowsocks_launcher
		false -> R.drawable.ic_qu_shadowsocks_launcher_disabled
	}

	return ShortcutInfoCompat.Builder(context, "toggle")
		.setIntent(Intent(context, QuickToggleShortcut::class.java).setAction(Intent.ACTION_MAIN))
		.setIcon(IconCompat.createWithResource(context, icon))
		.setShortLabel(context.getString(if (isConnected) R.string.proxy_is_on else R.string.proxy_is_off))
		.build()
}