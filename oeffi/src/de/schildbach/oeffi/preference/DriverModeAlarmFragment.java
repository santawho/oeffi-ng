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
import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;

public class DriverModeAlarmFragment extends PreferenceFragment {

    public static final String KEY_ENABLED = "extras_drivermode_alarm_%s_enabled";
    public static final String KEY_DURATION = "extras_drivermode_alarm_%s_duration";
    public static final String KEY_LEAD_TIME = "extras_drivermode_alarm_%s_lead_time";
    public static final String KEY_VALUE = "%s_value";
    public static final String KEY_SHORT_BREAK = "short_break";
    public static final String KEY_LONG_BREAK = "long_break";
    public static final String KEY_SECOND_SHIFT = "second_shift";
    public static final String KEY_NEXT_DAY = "next_day";

    private static final String[] GROUPS = new String[] {
            KEY_SHORT_BREAK,
            KEY_LONG_BREAK,
            KEY_SECOND_SHIFT,
            KEY_NEXT_DAY,
    };

    public static class BreakDef {
        public final String group;
        public long minimumDurationMillis;
        public long leadTimeMillis;

        BreakDef(final String group) {
            this.group = group;
        }
    }

    @Override
    public void onCreatePreferences(@androidx.annotation.Nullable final Bundle savedInstanceState, @androidx.annotation.Nullable final String rootKey) {
        addPreferencesFromResource(R.xml.preference_drivermode_alarm);
        setupDynamicSummaries();
    }

    private void setupDynamicSummaries() {
        for (final String group : GROUPS) {
            setupDynamicSummary(makeKey(KEY_DURATION, group), R.string.extras_drivermode_alarm_duration_summary);
            setupDynamicSummary(makeKey(KEY_LEAD_TIME, group), R.string.extras_drivermode_alarm_lead_time_summary);
        }
    }

    private static String makeKey(final String keyPattern, final String group) {
        return String.format(keyPattern, group);
    }

    private static String makeValueKey(final String key) {
        return String.format(KEY_VALUE, key);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, @Nullable final String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);

        if (key != null) {
            boolean haveChanges = false;
            final List<BreakDef> defs = getBreakDefinitions(true);
            for (int index = 0; index < GROUPS.length; index++) {
                final String group = GROUPS[index];
                if (key.equals(makeKey(KEY_DURATION, group))) {
                    haveChanges |= !isStandardFormat(key);
                    haveChanges |= onDurationChanged(index, defs);
                    break;
                }
                if (key.equals(makeKey(KEY_LEAD_TIME, group))) {
                    haveChanges |= !isStandardFormat(key);
                    haveChanges |= onLeadTimeChanged(index, defs);
                    break;
                }
            }
            if (haveChanges) {
                final SharedPreferences.Editor edit = prefs.edit();
                for (final BreakDef def : defs) {
                    final String group = def.group;
                    edit.putString(makeKey(KEY_DURATION, group), timeString(def.minimumDurationMillis));
                    edit.putString(makeKey(KEY_LEAD_TIME, group), timeString(def.leadTimeMillis));
                }
                edit.apply();
                setupDynamicSummaries();
            }
        }
    }

    private boolean isStandardFormat(final String key) {
        final String value = prefs.getString(key, null);
        final String standardValue = timeString(getValueInMillis(prefs, key));
        return standardValue.equals(value);
    }

    private boolean onDurationChanged(final int index, final List<BreakDef> defs) {
        boolean haveChanges = false;
        BreakDef def;
        long duration;
        // fix larger durations
        def = defs.get(index);
        duration = def.minimumDurationMillis;
        for (int pos = index + 1; pos < defs.size(); pos += 1) {
            def = defs.get(pos);
            if (def.minimumDurationMillis <= duration) {
                duration = duration + 60000L;
                def.minimumDurationMillis = duration;
                haveChanges = true;
            }
        }
        // fix smaller durations
        def = defs.get(index);
        duration = def.minimumDurationMillis;
        for (int pos = index - 1; pos >= 0; pos -= 1) {
            def = defs.get(pos);
            if (def.minimumDurationMillis >= duration) {
                haveChanges = true;
                duration = duration - 60000L;
                def.minimumDurationMillis = duration;
                final long leadTimeMillis = def.leadTimeMillis;
                if (leadTimeMillis >= duration)
                    def.leadTimeMillis = leadTimeMillis - 60000L;
            }
        }
        return haveChanges;
    }

    private boolean onLeadTimeChanged(final int index, final List<BreakDef> defs) {
        boolean haveChanges = false;
        // accept the new lead time and adapt its duration if necessary
        final BreakDef def = defs.get(index);
        final long leadTimeMillis = def.leadTimeMillis;
        if (leadTimeMillis >= def.minimumDurationMillis) {
            haveChanges = true;
            def.minimumDurationMillis = leadTimeMillis + 60000L;
            onDurationChanged(index, defs);
        }
        return haveChanges;
    }

    public static List<BreakDef> getBreakDefinitions() {
        return getBreakDefinitions(false);
    }

    private static List<BreakDef> getBreakDefinitions(final boolean all) {
        final SharedPreferences prefs = Application.getInstance().getSharedPreferences();
        final List<BreakDef> defs = new ArrayList<>();
        for (final String group : GROUPS) {
            if (all || prefs.getBoolean(makeKey(KEY_ENABLED, group), false)) {
                final BreakDef def = new BreakDef(group);
                def.minimumDurationMillis = getDurationInMillis(prefs, group);
                def.leadTimeMillis = getLeadTimeInMillis(prefs, group);
                defs.add(def);
            }
        }
        return defs;
    }

    private static long getDurationInMillis(
            final SharedPreferences prefs,
            final String group) {
        return getValueInMillis(prefs, KEY_DURATION, group);
    }

    private static long getLeadTimeInMillis(
            final SharedPreferences prefs,
            final String group) {
        return getValueInMillis(prefs, KEY_LEAD_TIME, group);
    }

    private static String timeString(final long millis) {
        final long minutes = millis / 60000L;
        return String.format("%d:%02d", minutes / 60L, minutes % 60L);
    }

    private static long getValueInMillis(
            final SharedPreferences prefs,
            final String keyPattern, final String group) {
        return getValueInMillis(prefs, makeKey(keyPattern, group));
    }

    private static long getValueInMillis(final SharedPreferences prefs, final String key) {
        final String valueString = prefs.getString(key, makeValueKey(key));
        final String[] split = valueString.split("[:.]");
        final String sMinutes, sHours;
        final int length = split.length;
        if (length == 1) {
            sHours = "0";
            sMinutes = split[0];
        } else {
            sHours = split[length - 2];
            sMinutes = split[length - 1];
        }
        final int hours = parseInt(sHours);
        final int minutes = parseInt(sMinutes);
        return (hours * 60L + minutes) * 60000L;
    }

    private static int parseInt(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException nfe) {
            return 0;
        }
    }
}
