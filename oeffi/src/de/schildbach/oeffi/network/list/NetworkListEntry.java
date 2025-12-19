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

package de.schildbach.oeffi.network.list;

import de.schildbach.pte.NetworkId;

public interface NetworkListEntry {
    class Network implements NetworkListEntry {
        public final NetworkId id;
        public final NetworkId.State state;
        public final String group;
        public final String coverage;

        public Network(final NetworkId id, final NetworkId.State state, final String group, final String coverage) {
            this.id = id;
            this.state = state;
            this.group = group;
            this.coverage = coverage;
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof Network))
                return false;
            return this.id == ((Network) other).id;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    class Separator implements NetworkListEntry {
        public final String label;

        public Separator(final String label) {
            this.label = label;
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof Separator))
                return false;
            return this.label.equals(((Separator) other).label);
        }

        @Override
        public int hashCode() {
            return label.hashCode();
        }
    }
}
