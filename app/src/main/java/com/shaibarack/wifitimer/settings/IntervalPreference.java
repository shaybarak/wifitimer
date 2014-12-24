package com.shaibarack.wifitimer.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

import com.shaibarack.wifitimer.R;

/**
 * Preference for choosing a time interval in minutes and seconds.
 */
public class IntervalPreference extends DialogPreference {

    private NumberPicker mMinutesPicker;
    private int mMinutes;
    private NumberPicker mSecondsPicker;
    private int mSeconds;

    public IntervalPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public IntervalPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            mSeconds = getPersistedInt(0);
        } else {
            mSeconds = Integer.parseInt(defaultValue.toString());
        }
        // Convert to wall time
        mMinutes = mSeconds / 60;
        mSeconds = mSeconds % 60;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mMinutesPicker = (NumberPicker) view.findViewById(R.id.minutes);
        mMinutesPicker.setMinValue(0);
        mMinutesPicker.setMaxValue(99);
        mMinutesPicker.setValue(mMinutes);
        mMinutesPicker.setFormatter(new TwoDigitFormatter());

        mSecondsPicker = (NumberPicker) view.findViewById(R.id.seconds);
        mSecondsPicker.setMinValue(0);
        mSecondsPicker.setMaxValue(59);
        mSecondsPicker.setValue(mSeconds);
        mSecondsPicker.setFormatter(new TwoDigitFormatter());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            mMinutes = mMinutesPicker.getValue();
            mSeconds = mSecondsPicker.getValue();
            persistInt(mMinutes * 60 + mSeconds);
            super.setSummary(getSummary());
        }
    }

    @Override
    public CharSequence getSummary() {
        return String.format("%02d:%02d", mMinutes, mSeconds);
    }

    private static class TwoDigitFormatter implements NumberPicker.Formatter {
        @Override
        public String format(int i) {
            return String.format("%02d", i);
        }
    }
}
