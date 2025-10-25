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
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.PTDate;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public final class Formats {
    public static String formatDate(final TimeZoneSelector timeZoneSelector, final PTDate timestamp) {
        return formatDate(timeZoneSelector, timestamp.getTime(), timestamp.getOffset());
    }

    public static String formatDate(final TimeZoneSelector timeZoneSelector, final Date date, final int offset) {
        return formatDate(timeZoneSelector, date.getTime(), offset);
    }

    public static String formatDate(final TimeZoneSelector timeZoneSelector, final long time, final int offset) {
        // return DateUtils.formatDateTime(context, time,
        //         DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY
        //                 | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        return getDateFormat(timeZoneSelector, time, offset).format(time);
    }

    public static java.text.DateFormat getDateFormat(final TimeZoneSelector timeZoneSelector, final long time, final int offset) {
        final java.text.DateFormat dateFormat = DateFormat.getDateFormat(timeZoneSelector.context);
        dateFormat.setTimeZone(timeZoneSelector.getTimeZoneForOffset(time, offset));
        return dateFormat;
    }

    public static String formatDate(
            final TimeZoneSelector timeZoneSelector, final long now, final Date date, final int offset,
            final boolean abbreviate, final String todayString) {
        return formatDate(timeZoneSelector, now, date.getTime(), offset, abbreviate, todayString);
    }


    public static String formatDate(
            final TimeZoneSelector timeZoneSelector, final long now, final PTDate timestamp,
            final boolean abbreviate, final String todayString) {
        return formatDate(timeZoneSelector, now, timestamp.getTime(), timestamp.getOffset(), abbreviate, todayString);
    }

    public static String formatDate(
            final TimeZoneSelector timeZoneSelector, final long now, final long time, final int offset,
            final boolean abbreviate, final String todayString) {
        // today
        if (DateUtils.isToday(time))
            return todayString;

        final Calendar calendar = new GregorianCalendar(timeZoneSelector.getTimeZoneForOffset(time, offset));
        calendar.setTimeInMillis(time);
        if (time > now) {
            // tomorrow
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            if (DateUtils.isToday(calendar.getTimeInMillis()))
                return timeZoneSelector.context.getString(abbreviate ? R.string.time_tomorrow_abbrev : R.string.time_tomorrow);

            // next several days
            if (time - now < DateUtils.DAY_IN_MILLIS * 6) {
                int flags = DateUtils.FORMAT_SHOW_WEEKDAY;
                if (abbreviate)
                    flags |= DateUtils.FORMAT_ABBREV_WEEKDAY;
                return DateUtils.formatDateTime(timeZoneSelector.context, time, flags);
            }
        } else {
            // yesterday
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            if (DateUtils.isToday(calendar.getTimeInMillis()))
                return timeZoneSelector.context.getString(abbreviate ? R.string.time_yesterday_abbrev : R.string.time_yesterday);
        }

        // default
        return formatDate(timeZoneSelector, time, offset);
    }

    public static String formatDate(final TimeZoneSelector timeZoneSelector, final long now, final PTDate timestamp) {
        return formatDate(timeZoneSelector, now, timestamp, timestamp.getOffset());
    }

    public static String formatDate(final TimeZoneSelector timeZoneSelector, final long now, final Date date, final int offset) {
        return formatDate(timeZoneSelector, now, date.getTime(), offset);
    }

    public static String formatDate(final TimeZoneSelector timeZoneSelector, final long now, final long time, final int offset) {
        return formatDate(timeZoneSelector, now, time, offset, false, timeZoneSelector.context.getString(R.string.time_today));
    }

    public static String formatTime(final TimeZoneSelector timeZoneSelector, final Date date, final int offset) {
        return formatTime(timeZoneSelector, date.getTime(), offset);
    }

    public static String formatTime(final TimeZoneSelector timeZoneSelector, final PTDate timestamp) {
        return formatTime(timeZoneSelector, timestamp.getTime(), timestamp.getOffset());
    }

    public static String formatTime(final TimeZoneSelector timeZoneSelector, final long time, final int offset) {
        return getTimeFormat(timeZoneSelector, time, offset).format(time);
    }

    public static java.text.DateFormat getTimeFormat(final TimeZoneSelector timeZoneSelector, final long time, final int offset) {
        final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(timeZoneSelector.context);
        timeFormat.setTimeZone(timeZoneSelector.getTimeZoneForOffset(time, offset));
        return timeFormat;
    }

    public static String formatTime(final TimeZoneSelector timeZoneSelector, final long now, final long time, final int offset) {
        final String timeString = formatTime(timeZoneSelector, time, offset);

        final long diff = time - now;
        if (diff < -8 * 3600000 || diff > 8 * 3600000)
            return formatDate(timeZoneSelector, now, time, offset) + " " + timeString;

        return timeString;
    }

    public static String formatTimeDiff(final Context context, final Date from, final Date to) {
        return formatTimeDiff(context, from, to, true);
    }

    public static String formatTimeDiff(final Context context, final long from, final long to) {
        return formatTimeDiff(context, from, to, true);
    }

    public static String formatTimeDiff(final Context context, final Date from, final Date to, final boolean refIsNow) {
        if (to == null || from == null)
            return "?";
        return formatTimeDiff(context, to.getTime(), from.getTime(), refIsNow);
    }

    public static String formatTimeDiff(final Context context, final long from, final long to, final boolean refIsNow) {
        return formatTimeDiff(context, to - from, refIsNow);
    }

    public static String formatTimeDiff(final Context context, final long diff) {
        return formatTimeDiff(context, diff, true);
    }

    public static String formatTimeDiff(final Context context, final long diff, final boolean refIsNow) {
        final long rel = Math.round(((float) diff) / DateUtils.MINUTE_IN_MILLIS);
        if (refIsNow) {
            if (rel >= 60)
                return context.getString(R.string.time_hours, rel / 60, rel % 60);
            else if (rel > 0)
                return context.getString(R.string.time_in, rel);
            else if (rel == 0)
                return context.getString(R.string.time_now);
            else
                return context.getString(R.string.time_ago, -rel);
        } else {
            if (rel >= 0)
                return context.getString(R.string.time_after, rel);
            else
                return context.getString(R.string.time_before, -rel);
        }
    }

    public static String formatTimeSpan(final long millis) {
        final long mins = millis / DateUtils.MINUTE_IN_MILLIS;
        return String.format("%d:%02d", mins / 60, mins % 60);
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

    public static String fullLocationNameIfDifferentPlace(final Location location, final Location refLocation) {
        return fullLocationNameIfDifferentPlace(location, refLocation, false);
    }

    public static String fullLocationNameIfDifferentPlace(final Location location, final Location refLocation, boolean placeLast) {
        return location == null ? null
                : (refLocation == null || refLocation.place == null || location.place == null || location.name == null)
                ? location.uniqueShortName()
                : location.place.equals(refLocation.place)
                ? location.name
                : fullLocationName(location, placeLast);
    }

    public static String fullLocationName(final Location location) {
        return fullLocationName(location, false);
    }

    public static String fullLocationName(final Location location, boolean placeLast) {
        return location == null ? null
                : location.place == null || location.name == null
                ? location.uniqueShortName()
                : placeLast
                ? location.name + ", " + location.place
                : location.place + ", " + location.name;
    }

    public static String formatPosition(final Context context, final Position position, final Line line) {
        // position: use german translation "Gleis" only for trains, otherwise use "Steig"
        return context.getString(
                line.isTrain() ? R.string.position_platform_train : R.string.position_platform,
                position);
    }
}
