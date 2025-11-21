/*
 * Copyright the original author or authors.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.oeffi.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.ListPreference;
import android.util.AttributeSet;


public class ScenarioSelectorPreference extends ListPreference {

    public ScenarioSelectorPreference(final Context context) {
        super(context);
    }

    public ScenarioSelectorPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public ScenarioSelectorPreference(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ScenarioSelectorPreference(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onSetInitialValue(final boolean restoreValue, final Object defaultValue) {
        // do not set the value on start up
    }

    @Override
    public void setValue(final String value) {
        super.setValue(value);
        setPreferences(value);
        ((PreferenceActivity) getContext()).onBackPressed();
    }

    private void setPreferences(final String valueString) {
        final SharedPreferences.Editor editor = getEditor();
        final String[] assignments = valueString.split(";");
        for (final String assignment : assignments) {
            final String[] nameAndTypeValue = assignment.split("=", 2);
            if (nameAndTypeValue.length < 2)
                continue;
            final String name = nameAndTypeValue[0];
            final String typeAndValue = nameAndTypeValue[1];
            final char type = typeAndValue.charAt(0);
            final String value = typeAndValue.substring(1);
            switch (type) {
                case 'I':
                    editor.putInt(name, Integer.parseInt(value));
                    break;
                case 'B':
                    editor.putBoolean(name, value.equalsIgnoreCase("true"));
                    break;
                case 'S':
                    editor.putString(name, value);
                    break;
                default:
                    break;
            }
        }
        editor.apply();
    }
}
