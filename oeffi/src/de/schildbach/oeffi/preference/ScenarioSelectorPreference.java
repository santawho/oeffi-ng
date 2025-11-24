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

import java.util.concurrent.atomic.AtomicBoolean;


public class ScenarioSelectorPreference extends ListPreference {

    public ScenarioSelectorPreference(final Context context) {
        super(context);
        construct();
    }

    public ScenarioSelectorPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        construct();
    }

    public ScenarioSelectorPreference(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        construct();
    }

    public ScenarioSelectorPreference(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        construct();
    }

    private void construct() {
        setDefaultValue("justsomething"); // forces call of onSetInitialValue()
    }

    private boolean isAllSetLike(final String scenarioValue) {
        final SharedPreferences preferences = getSharedPreferences();
        final AtomicBoolean allFullfilled = new AtomicBoolean(true);
        handleScenarioValue(scenarioValue, new ScenarioValuesHandler() {
            @Override
            public void stringValue(final String name, final String value) {
                final String currentSetting = preferences.getString(name, null);
                if (currentSetting == null || !currentSetting.equals(value))
                    allFullfilled.set(false);
            }

            @Override
            public void intValue(final String name, final int value) {
                final int currentSetting = preferences.getInt(name, Integer.MIN_VALUE);
                if (currentSetting == Integer.MIN_VALUE || currentSetting != value)
                    allFullfilled.set(false);
            }

            @Override
            public void booleanValue(final String name, final boolean value) {
                if (!preferences.contains(name) || preferences.getBoolean(name, false) != value)
                    allFullfilled.set(false);
            }
        });
        return allFullfilled.get();
    }



    @Override
    protected void onSetInitialValue(final boolean restoreValue, final Object defaultValue) {
//        if (restoreValue) {
//            final String currentValue = getPersistedString(getValue());
//            if (isAllSetLike(currentValue))
//                super.setValue(currentValue);
//            // else something changed by user, fall through to check all options
//        }
        // check all options in provided order, take the first that matches
        final CharSequence[] entryValues = getEntryValues();
        for (final CharSequence entryValue : entryValues) {
            final String value = entryValue.toString();
            if (value.isEmpty() && !restoreValue) {
                // the always matching empty pattern on first startup
                // then take the first option, which must match all defaults
                super.setValue((String) entryValues[0]);
                break;
            }
            if (isAllSetLike(value)) {
                super.setValue(value);
                break;
            }
        }
        // else no entry value is like current settings, then left unset, nothing selected
    }

    @Override
    public void setValue(final String value) {
        super.setValue(value);
        setPreferences(value);
        ((PreferenceActivity) getContext()).onBackPressed();
    }

    private void setPreferences(final String scenarioValue) {
        final SharedPreferences.Editor editor = getEditor();
        handleScenarioValue(scenarioValue, new ScenarioValuesHandler() {
            @Override
            public void stringValue(final String name, final String value) {
                editor.putString(name, value);
            }

            @Override
            public void intValue(final String name, final int value) {
                editor.putInt(name, value);
            }

            @Override
            public void booleanValue(final String name, final boolean value) {
                editor.putBoolean(name, value);
            }
        });
        editor.apply();
    }

    private void handleScenarioValue(
            final String scenarioValue,
            final ScenarioValuesHandler handler) {
        if (scenarioValue == null)
            return;
        final String[] assignments = scenarioValue.split(";");
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
                    handler.intValue(name, Integer.parseInt(value));
                    break;
                case 'B':
                    handler.booleanValue(name, value.equalsIgnoreCase("true"));
                    break;
                case 'S':
                    handler.stringValue(name, value);
                    break;
                default:
                    break;
            }
        }
    }

    private interface ScenarioValuesHandler {
        void stringValue(final String name, final String value);
        void intValue(final String name, final int value);
        void booleanValue(final String name, final boolean value);
    }
}
