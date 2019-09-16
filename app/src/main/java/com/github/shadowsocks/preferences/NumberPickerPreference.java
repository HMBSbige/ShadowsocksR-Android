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
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.NumberPicker;

import com.github.shadowsocks.R;

public class NumberPickerPreference extends SummaryDialogPreference {

    private NumberPicker picker;
    private int value;

    public NumberPickerPreference(Context context) {
        this(context, null);
    }

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        picker = new NumberPicker(context);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumberPickerPreference);
        setMin(a.getInt(R.styleable.NumberPickerPreference_min, 0));
        setMax(a.getInt(R.styleable.NumberPickerPreference_max, Integer.MAX_VALUE - 1));
        a.recycle();
    }

    public int getValue() {
        return this.value;
    }

    public void setValue(int i) {
        if (i == getValue()) {
            return;
        }

        picker.setValue(i);
        value = picker.getValue();
        persistInt(value);
        notifyChanged();
    }

    public int getMin() {
        if (picker == null) {
            return 0;
        }

        return picker.getMinValue();
    }

    public void setMin(int value) {
        if (picker == null) {
            return;
        }

        picker.setMinValue(value);
    }

    public int getMax() {
        if (picker == null) {
            return 0;
        }

        return picker.getMaxValue();
    }

    public void setMax(int value) {
        if (picker == null) {
            return;
        }

        picker.setMaxValue(value);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        Window window = getDialog().getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    @Override
    protected View onCreateDialogView() {
        ViewGroup parent = (ViewGroup) picker.getParent();
        if (parent != null) {
            parent.removeView(picker);
        }
        return picker;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        picker.clearFocus();
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            int value = picker.getValue();
            if (callChangeListener(value)) {
                setValue(value);
                return;
            }
        }
        picker.setValue(value);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, getMin());
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        int defValue = (int) defaultValue;
        int value = restorePersistedValue ? getPersistedInt(defValue) : defValue;
        setValue(value);
    }

    @Override
    public Object getSummaryValue() {
        return getValue();
    }
}
