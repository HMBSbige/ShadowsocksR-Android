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
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatSpinner;

import com.github.shadowsocks.R;

public class DropDownPreference extends SummaryPreference {

    private Context mContext;
    private ArrayAdapter<String> mAdapter;
    private AppCompatSpinner mSpinner;
    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private int mSelectedIndex = -1;

    public DropDownPreference(Context context) {
        this(context, null);
    }

    public DropDownPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mAdapter = new ArrayAdapter<>(mContext, android.R.layout.simple_spinner_dropdown_item);
        mSpinner = new AppCompatSpinner(mContext);

        mSpinner.setVisibility(View.INVISIBLE);
        mSpinner.setAdapter(mAdapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = getValue(position);
                if (position != mSelectedIndex && callChangeListener(value)) {
                    setValue(position, value);
                }
            }
        });

        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // TODO: not working with scrolling
                // mSpinner.setDropDownVerticalOffset(Utils.dpToPx(getContext, -48 * mSelectedIndex).toInt)
                mSpinner.performClick();
                return true;
            }
        });

        TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.DropDownPreference);
        setEntries(a.getTextArray(R.styleable.DropDownPreference_android_entries));
        setEntryValues(a.getTextArray(R.styleable.DropDownPreference_android_entryValues));
        a.recycle();
    }

    @Override
    public Object getSummaryValue() {
        return getValue();
    }

    /**
     * Sets the human-readable entries to be shown in the list. This will be shown in subsequent dialogs.
     * <p>
     * Each entry must have a corresponding index in [[setEntryValues(CharSequence[])]].
     *
     * @param entries The entries.
     * @see [[setEntryValues]]
     */
    public void setEntries(CharSequence[] entries) {
        mEntries = entries;
        mAdapter.clear();
        if (entries != null) {
            for (CharSequence entry : entries) {
                mAdapter.add(entry.toString());
            }
        }
    }

    /**
     * @param entriesResId The entries array as a resource.
     * @see #setEntries
     */
    public void setEntries(@ArrayRes int entriesResId) {
        setEntries(mContext.getResources().getTextArray(entriesResId));
    }

    /**
     * The list of entries to be shown in the list in subsequent dialogs.
     *
     * @return The list as an array.
     */
    public CharSequence[] getEntries() {
        return mEntries;
    }

    /**
     * The array to find the value to save for a preference when an entry from
     * entries is selected. If a user clicks on the second item in entries, the
     * second item in this array will be saved to the preference.
     *
     * @param entryValues The array to be used as values to save for the preference.
     */
    public void setEntryValues(CharSequence[] entryValues) {
        mEntryValues = entryValues;
    }

    /**
     * @param entryValuesResId The entry values array as a resource.
     */
    public void setEntryValues(@ArrayRes int entryValuesResId) {
        setEntryValues(mContext.getResources().getTextArray(entryValuesResId));
    }

    /**
     * Returns the array of values to be saved for the preference.
     *
     * @return The array of values.
     */
    public CharSequence[] getEntryValues() {
        return mEntryValues;
    }

    public String getValue(int index) {
        if (mEntryValues == null) {
            return null;
        } else {
            return mEntryValues[index].toString();
        }
    }

    /**
     * Sets the value of the key. This should be one of the entries in [[getEntryValues]].
     *
     * @param value The value to set for the key.
     */
    public void setValue(String value) {
        setValue(findIndexOfValue(value), value);
    }

    /**
     * Sets the value to the given index from the entry values.
     *
     * @param index The index of the value to set.
     */
    public void setValueIndex(int index) {
        setValue(index, getValue(index));
    }

    public void setValue(int index, String value) {
        persistString(value);
        mSelectedIndex = index;
        mSpinner.setSelection(index);
        notifyChanged();
    }

    /**
     * Returns the value of the key. This should be one of the entries in [[getEntryValues]].
     *
     * @return The value of the key.
     */
    public String getValue() {
        if (mEntryValues == null || mSelectedIndex < 0) {
            return null;
        } else {
            return mEntryValues[mSelectedIndex].toString();
        }
    }

    /**
     * Returns the entry corresponding to the current value.
     *
     * @return The entry corresponding to the current value, or null.
     */
    public CharSequence getEntry() {
        int index = getValueIndex();
        if (index >= 0 && mEntries != null) {
            return mEntries[index];
        } else {
            return null;
        }
    }

    /**
     * Returns the index of the given value (in the entry values array).
     *
     * @param value The value whose index should be returned.
     * @return The index of the value, or -1 if not found.
     */
    public int findIndexOfValue(String value) {
        if (value != null && mEntryValues != null) {
            int i = mEntryValues.length - 1;
            while (i >= 0) {
                if (mEntryValues[i].equals(value)) {
                    return i;
                }
                i -= 1;
            }
        }
        return -1;
    }

    public int getValueIndex() {
        return mSelectedIndex;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        String value = restorePersistedValue ? getPersistedString(getValue()) : defaultValue.toString();
        setValue(value);
    }

    public void setDropDownWidth(int dimenResId) {
        mSpinner.setDropDownWidth(mContext.getResources().getDimensionPixelSize(dimenResId));
    }

    @Override
    protected void onBindView(@NonNull View view) {
        super.onBindView(view);
        ViewGroup parent = (ViewGroup) mSpinner.getParent();

        if (view == parent) {
            return;
        }

        if (parent != null) {
            parent.removeView(mSpinner);
        }

        ((ViewGroup) view).addView(mSpinner, 0);
        ViewGroup.LayoutParams lp = mSpinner.getLayoutParams();
        lp.width = 0;
        mSpinner.setLayoutParams(lp);
    }
}
