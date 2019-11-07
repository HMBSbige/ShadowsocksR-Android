package com.bige0.shadowsocksr.widget

import android.util.*
import android.view.*
import com.bige0.shadowsocksr.*
import com.google.android.material.snackbar.*

class UndoSnackBarManager<T>(private val view: View, private val undo: (Any) -> Unit, private val commit: (Any) -> Unit)
{
	private val recycleBin: SparseArray<T> = SparseArray()
	private var last: Snackbar? = null

	private val removedCallback = object : Snackbar.Callback()
	{
		override fun onDismissed(transientBottomBar: Snackbar?, event: Int)
		{
			if (event == DISMISS_EVENT_SWIPE || event == DISMISS_EVENT_MANUAL || event == DISMISS_EVENT_TIMEOUT)
			{
				commit(recycleBin)
				recycleBin.clear()
			}
			last = null
		}
	}

	fun remove(index: Int, item: T)
	{
		recycleBin.append(index, item)
		val count = recycleBin.size()
		last = Snackbar.make(view, view.resources.getQuantityString(R.plurals.removed, count, count), Snackbar.LENGTH_LONG)
			.addCallback(removedCallback)
			.setAction(R.string.undo) {
				undo(recycleBin)
				recycleBin.clear()
			}
		last!!.show()
	}

	fun flush()
	{
		if (last != null)
		{
			last!!.dismiss()
		}
	}
}
