package com.github.shadowsocks.widget;
/*
 * Shadowsocks - A shadowsocks client for Android
 * Copyright (C) 2014 <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.github.clans.fab.FloatingActionMenu;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

/**
 * Behavior for com.github.clans.fab.FloatingActionMenu that is aware of Snackbars and scrolling.
 *
 * @author Mygod
 */
public class FloatingActionMenuBehavior extends CoordinatorLayout.Behavior<FloatingActionMenu> {

    private ValueAnimator fabTranslationYAnimator;
    private float fabTranslationY;

    public FloatingActionMenuBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, FloatingActionMenu child, View dependency) {
        return dependency instanceof Snackbar.SnackbarLayout;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, final FloatingActionMenu child, View dependency) {
        float targetTransY = 0;
        List<View> dependencies = parent.getDependencies(child);
        for (View view : dependencies) {
            if (view instanceof Snackbar.SnackbarLayout && parent.doViewsOverlap(child, view)) {
                float value = view.getTranslationY() - view.getHeight();
                if (value <= targetTransY) {
                    targetTransY = value;
                }
            }
        }

        if (targetTransY > 0) {
            targetTransY = 0;
        }

        if (fabTranslationY != targetTransY) {
            float currentTransY = child.getTranslationY();
            if (fabTranslationYAnimator != null && fabTranslationYAnimator.isRunning()) {
                fabTranslationYAnimator.cancel();
            }
            if (child.isShown() && Math.abs(currentTransY - targetTransY) > child.getHeight() * 0.667F) {
                if (fabTranslationYAnimator == null) {
                    fabTranslationYAnimator = new ValueAnimator();
                    fabTranslationYAnimator.setInterpolator(new FastOutSlowInInterpolator());
                    fabTranslationYAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            child.setTranslationY((float) animation.getAnimatedValue());
                        }
                    });
                }
                fabTranslationYAnimator.setFloatValues(currentTransY, targetTransY);
                fabTranslationYAnimator.start();
            } else {
                child.setTranslationY(targetTransY);
            }
            fabTranslationY = targetTransY;
        }
        return false;
    }

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout, FloatingActionMenu child, View directTargetChild, View target, int nestedScrollAxes) {
        return true;
    }

    @Override
    public void onNestedScroll(CoordinatorLayout parent, FloatingActionMenu child, View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        super.onNestedScroll(parent, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
        int dy = dyConsumed + dyUnconsumed;
        if (child.isMenuButtonHidden()) {
            if (dy < 0) {
                child.showMenuButton(true);
            }
        } else if (dy > 0) {
            child.hideMenuButton(true);
        }
    }
}
