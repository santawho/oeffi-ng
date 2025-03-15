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

package de.schildbach.oeffi.directions;

import android.text.format.DateUtils;

import java.io.Serializable;
import java.util.Locale;

public abstract class TimeSpec implements Serializable {
    public enum DepArr {
        DEPART, ARRIVE
    }

    public final DepArr depArr;

    private TimeSpec(final DepArr depArr) {
        this.depArr = depArr;
    }

    public abstract long timeInMillis();

    @Override
    public String toString() {
        return depArr.name().toLowerCase(Locale.ENGLISH);
    }

    public static final class Absolute extends TimeSpec {
        private static final long serialVersionUID = -1384741768418053020L;

        public final long timeMs;

        public Absolute(final DepArr depArr, final long timeMs) {
            super(depArr);
            this.timeMs = timeMs;
        }

        @Override
        public long timeInMillis() {
            return timeMs;
        }

        @Override
        public String toString() {
            return String.format(Locale.ENGLISH, "%s at %tF %<tT", super.toString(), timeMs);
        }
    }

    public static final class Relative extends TimeSpec {
        private static final long serialVersionUID = 8082677158593413075L;

        public final long diffMs;

        public Relative(final long diffMs) {
            this(DepArr.DEPART, diffMs);
        }

        public Relative(final DepArr depArr, final long diffMs) {
            super(depArr);
            this.diffMs = diffMs;
        }

        @Override
        public long timeInMillis() {
            return System.currentTimeMillis() + diffMs;
        }

        @Override
        public String toString() {
            return String.format(Locale.ENGLISH, "%s in %d min", super.toString(), diffMs / DateUtils.MINUTE_IN_MILLIS);
        }
    }
}
