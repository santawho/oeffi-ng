package de.schildbach.oeffi.preference;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

public class EditTextPreferenceWithSummary extends EditTextPreference {
    public EditTextPreferenceWithSummary(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        notifyChanged();
    }

    @Override
    public CharSequence getSummary() {
        String text = super.getText();
        String summary = super.getSummary().toString();
        return String.format(summary, text == null ? "" : text);
    }
}
