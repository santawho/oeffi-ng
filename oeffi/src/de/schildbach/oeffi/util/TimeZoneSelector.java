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

import java.util.Date;
import java.util.TimeZone;

import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Timestamp;

public class TimeZoneSelector {
    private boolean useTimezoneFromSystem;
    private boolean useTimeZoneFromNetwork;
    private TimeZone systemTimezone;
    private TimeZone networkTimezone;

    public TimeZoneSelector(final String preferredTimezonePreference, final NetworkId network) {
        useTimezoneFromSystem = false;
        useTimeZoneFromNetwork = false;
        if ("system".equals(preferredTimezonePreference))
            useTimezoneFromSystem = true;
        else if ("network".equals(preferredTimezonePreference))
            useTimeZoneFromNetwork = true;

        systemTimezone = TimeZone.getDefault();
        networkTimezone = NetworkProviderFactory.provider(network).getTimeZone();
    }

    public int getOffset(final Timestamp timestamp) {
        if (timestamp == null)
            return 0;

        return getOffset(timestamp.getTime(), timestamp.getOffset());
    }

    public int getOffset(final long time, final int offset) {
        if (useTimezoneFromSystem || Timestamp.isOffsetSystem(offset))
            return systemTimezone.getOffset(time);

        if (useTimeZoneFromNetwork || Timestamp.isOffsetNetwork(offset) || Timestamp.isOffsetUnknownLocationSpecific(offset))
            return networkTimezone.getOffset(time);

        return offset;
    }

    public int getOffset(final Date date, int offset) {
        if (date == null)
            return 0;

        return getOffset(date.getTime(), offset);
    }

    public Timestamp getDisplay(final Timestamp timestamp) {
        if (timestamp == null)
            return null;

        return Timestamp.fromDateAndOffset(timestamp.getDate(), getOffset(timestamp));
    }
}
