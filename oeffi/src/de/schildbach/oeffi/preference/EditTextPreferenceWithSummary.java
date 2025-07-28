package de.schildbach.oeffi.preference;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

/*
 * NOT USED ANY MORE, REPLACED BY PreferenceFragment.setupDynamicSummary()
 */
public class EditTextPreferenceWithSummary extends EditTextPreference {
    public EditTextPreferenceWithSummary(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() {
        String text = super.getText();
        return String.format(super.getSummary().toString(), text == null ? "" : text);
    }
}
