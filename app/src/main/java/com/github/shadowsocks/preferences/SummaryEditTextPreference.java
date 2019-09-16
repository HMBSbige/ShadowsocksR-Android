package com.github.shadowsocks.preferences;

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.util.AttributeSet;

public class SummaryEditTextPreference extends EditTextPreference {

    private final CharSequence mDefaultSummary;

    public SummaryEditTextPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextPreferenceStyle);
    }

    public SummaryEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDefaultSummary = getSummary();
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        setSummary(text);
    }

    @Override
    public void setSummary(CharSequence summary) {
        if (TextUtils.isEmpty(summary)) {
            super.setSummary(mDefaultSummary);
        } else {
            super.setSummary(summary);
        }
    }
}
