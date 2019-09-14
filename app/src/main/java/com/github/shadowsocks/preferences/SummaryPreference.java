package com.github.shadowsocks.preferences;
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

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import java.util.Locale;

/**
 * Make your preference support %s in summary. Override getSummaryValue to customize what to put in.
 * @author Mygod
 */
public abstract class SummaryPreference extends Preference {

    public SummaryPreference(Context context) {
        super(context, null);
    }

    public SummaryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Returns the summary of this SummaryPreference. If the summary has a String formatting marker in it
     * (i.e. "%s" or "%1$s"), then the current entry value will be substituted in its place.
     *
     * @return the summary with appropriate string substitution
     */
    @Override
    public CharSequence getSummary() {
        return String.format(Locale.ENGLISH, super.getSummary().toString(), getSummaryValue());
    }

    /**
     * get summary value
     * @return  summary value
     */
    public abstract Object getSummaryValue();
}
