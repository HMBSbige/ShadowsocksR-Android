package com.bige0.shadowsocksr.widget

import android.animation.*
import android.content.*
import android.util.*
import android.view.*
import androidx.coordinatorlayout.widget.*
import androidx.core.view.*
import androidx.interpolator.view.animation.*
import com.github.clans.fab.*
import com.google.android.material.snackbar.*
import kotlin.math.*

class FloatingActionMenuBehavior(context: Context, attrs: AttributeSet) : CoordinatorLayout.Behavior<FloatingActionMenu>(context, attrs)
{
	private var fabTranslationYAnimator: ValueAnimator? = null
	private var fabTranslationY: Float = 0F

	override fun layoutDependsOn(parent: CoordinatorLayout, child: FloatingActionMenu, dependency: View): Boolean
	{
		return dependency is Snackbar.SnackbarLayout
	}

	override fun onDependentViewChanged(parent: CoordinatorLayout, child: FloatingActionMenu, dependency: View): Boolean
	{
		var targetTransY = 0f
		val dependencies = parent.getDependencies(child)
		for (view in dependencies)
		{
			if (view is Snackbar.SnackbarLayout && parent.doViewsOverlap(child, view))
			{
				val value = view.getTranslationY() - view.getHeight()
				if (value <= targetTransY)
				{
					targetTransY = value
				}
			}
		}

		if (targetTransY > 0)
		{
			targetTransY = 0f
		}

		if (fabTranslationY != targetTransY)
		{
			val currentTransY = child.translationY
			if (fabTranslationYAnimator != null && fabTranslationYAnimator!!.isRunning)
			{
				fabTranslationYAnimator!!.cancel()
			}
			if (child.isShown && abs(currentTransY - targetTransY) > child.height * 0.667f)
			{
				if (fabTranslationYAnimator == null)
				{
					fabTranslationYAnimator = ValueAnimator()
					fabTranslationYAnimator!!.interpolator = FastOutSlowInInterpolator()
					fabTranslationYAnimator!!.addUpdateListener { animation -> child.translationY = animation.animatedValue as Float }
				}
				fabTranslationYAnimator!!.setFloatValues(currentTransY, targetTransY)
				fabTranslationYAnimator!!.start()
			}
			else
			{
				child.translationY = targetTransY
			}
			fabTranslationY = targetTransY
		}
		return false
	}

	override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout, child: FloatingActionMenu, directTargetChild: View, target: View, nestedScrollAxes: Int): Boolean
	{
		return true
	}

	override fun onNestedScroll(parent: CoordinatorLayout, child: FloatingActionMenu, target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int)
	{
		super.onNestedScroll(parent, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH)
		val dy = dyConsumed + dyUnconsumed
		if (child.isMenuButtonHidden)
		{
			if (dy < 0)
			{
				child.showMenuButton(true)
			}
		}
		else if (dy > 0)
		{
			child.hideMenuButton(true)
		}
	}
}
