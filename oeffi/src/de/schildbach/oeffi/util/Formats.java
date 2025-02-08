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

package de.schildbach.oeffi.util;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;

import java.util.Calendar;
import java.util.GregorianCalendar;

public final class Formats {
    public static String formatDate(final Context context, final long time) {
        return DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY
                | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
    }

    public static String formatDate(final Context context, final long now, final long time, final boolean abbreviate,
            final String todayString) {
        // today
        if (DateUtils.isToday(time))
            return todayString;

        final Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(time);
        if (time > now) {
            // tomorrow
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            if (DateUtils.isToday(calendar.getTimeInMillis()))
                return context.getString(abbreviate ? R.string.time_tomorrow_abbrev : R.string.time_tomorrow);

            // next several days
            if (time - now < DateUtils.DAY_IN_MILLIS * 6) {
                int flags = DateUtils.FORMAT_SHOW_WEEKDAY;
                if (abbreviate)
                    flags |= DateUtils.FORMAT_ABBREV_WEEKDAY;
                return DateUtils.formatDateTime(context, time, flags);
            }
        } else {
            // yesterday
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            if (DateUtils.isToday(calendar.getTimeInMillis()))
                return context.getString(abbreviate ? R.string.time_yesterday_abbrev : R.string.time_yesterday);
        }

        // default
        return formatDate(context, time);
    }

    public static String formatDate(final Context context, final long now, final long time) {
        return formatDate(context, now, time, false, context.getString(R.string.time_today));
    }

    public static String formatTime(final Context context, final long time) {
        return DateFormat.getTimeFormat(context).format(time);
    }

    public static String formatTimeDiff(final Context context, final long from, final long to) {
        return formatTimeDiff(context, to - from);
    }

    public static String formatTimeDiff(final Context context, final long diff) {
        final long rel = Math.round(((float) diff) / DateUtils.MINUTE_IN_MILLIS);
        if (rel >= 60)
            return context.getString(R.string.time_hours, rel / 60, rel % 60);
        else if (rel > 0)
            return context.getString(R.string.time_in, rel);
        else if (rel == 0)
            return context.getString(R.string.time_now);
        else
            return context.getString(R.string.time_ago, -rel);
    }

    private static final String METER_SUFFIX = Constants.CHAR_HAIR_SPACE + "m";
    private static final String KILOMETER_SUFFIX = Constants.CHAR_HAIR_SPACE + "km";

    public static String formatDistance(final float meters) {
        final int metersInt = (int) meters;
        if (metersInt < 1000)
            return String.valueOf(metersInt) + METER_SUFFIX;
        else if (metersInt < 1000 * 100)
            return String.valueOf(metersInt / 1000) + '.' + String.valueOf((metersInt % 1000) / 100) + KILOMETER_SUFFIX;
        else
            return String.valueOf(metersInt / 1000) + KILOMETER_SUFFIX;
    }

    public static String makeBreakableStationName(final String originalName) {
        if (originalName == null) return null;
        // "\u200B" is a breakable whitespace with zero width
        return originalName
                .replaceAll("([-,.)])", "$1\u200B")
                .replaceAll("([(])", "\u200B$1");
    }
}
