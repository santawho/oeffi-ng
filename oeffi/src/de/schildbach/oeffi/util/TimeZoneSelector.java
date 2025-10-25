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

import java.util.Date;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.PTDate;

public class TimeZoneSelector {
    private static final String VARIABLE_TIMEZONE_ID = "?";

    public enum Source {
        LOCATION,
        NETWORK,
        SYSTEM,
        UTC,
    }

    public final Context context;
    private final Source timezoneSource;
    private final TimeZone utcTimezone = TimeZone.getTimeZone("UTC");
    private final TimeZone systemTimezone;
    private final TimeZone networkTimezone;

    public TimeZoneSelector(final Context context) {
        this.context = context;
        timezoneSource = Source.SYSTEM;
        systemTimezone = TimeZone.getDefault();
        networkTimezone = null;
    }

    public TimeZoneSelector(final Context context, final String preferredTimezonePreference, final NetworkId network) {
        this.context = context;

        if ("utc".equals(preferredTimezonePreference)) {
            timezoneSource = Source.UTC;
        } else if ("system".equals(preferredTimezonePreference)) {
            timezoneSource = Source.SYSTEM;
        } else if ("network".equals(preferredTimezonePreference)) {
            timezoneSource = Source.NETWORK;
        } else {
            timezoneSource = Source.LOCATION;
        }

        systemTimezone = TimeZone.getDefault();
        networkTimezone = NetworkProviderFactory.provider(network).getTimeZone();
    }

    public int getOffset(final PTDate timestamp) {
        if (timestamp == null)
            return 0;

        return getOffset(timestamp.getTime(), timestamp.getOffset());
    }

    public int getOffset(final long time, final int offset) {
        if (timezoneSource == Source.UTC)
            return 0;

        if (timezoneSource == Source.SYSTEM || PTDate.isOffsetSystem(offset))
            return systemTimezone.getOffset(time);

        if (timezoneSource == Source.NETWORK || PTDate.isOffsetNetwork(offset) || PTDate.isOffsetUnknownLocationSpecific(offset))
            return networkTimezone.getOffset(time);

        return offset;
    }

    public int getOffset(final Date date, int offset) {
        if (date == null)
            return 0;

        return getOffset(date.getTime(), offset);
    }

    public TimeZone getTimeZoneForOffset(final long time, final int offset) {
        return new SimpleTimeZone(getOffset(time, offset), VARIABLE_TIMEZONE_ID);
    }

    public TimeZone getInputTimeZone() {
        if (timezoneSource == Source.UTC)
            return utcTimezone;

        if (timezoneSource == Source.SYSTEM)
            return systemTimezone;

        return networkTimezone == null ? systemTimezone : networkTimezone;
    }

    public PTDate getDisplay(final PTDate timestamp) {
        if (timestamp == null)
            return null;

        return new PTDate(timestamp, getOffset(timestamp));
    }
}
