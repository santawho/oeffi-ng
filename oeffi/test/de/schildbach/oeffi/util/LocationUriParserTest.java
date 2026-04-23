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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import org.junit.Assert;
import org.junit.Test;

public class LocationUriParserTest {
    @Test
    public void contacts() throws Exception {
        final Location[] results = LocationUriParser.parseLocations("geo:0,0?q=Karl-Marx-Allee+84,+Berlin");

        assertEquals(1, results.length);
        final Location location = results[0];

        assertEquals(LocationType.ANY, location.type);
        assertFalse(location.hasCoord());
        assertEquals("Karl-Marx-Allee 84, Berlin", location.name);
    }

    @Test
    public void contactsMultiline() throws Exception {
        final Location[] resultsNewline = LocationUriParser.parseLocations("geo:0,0?q=Karl-Marx-Allee+84\nBerlin");

        assertEquals(1, resultsNewline.length);
        final Location locationNewline = resultsNewline[0];

        assertEquals(LocationType.ANY, locationNewline.type);
        assertFalse(locationNewline.hasCoord());
        assertEquals("Karl-Marx-Allee 84, Berlin", locationNewline.name);

        final Location[] resultsEncodedNewline = LocationUriParser
                .parseLocations("geo:0,0?q=Karl-Marx-Allee+84%0aBerlin");

        assertEquals(1, resultsEncodedNewline.length);
        final Location locationEncodedNewline = resultsEncodedNewline[0];

        assertEquals(LocationType.ANY, locationEncodedNewline.type);
        assertFalse(locationEncodedNewline.hasCoord());
        assertEquals("Karl-Marx-Allee 84, Berlin", locationEncodedNewline.name);

        final Location[] resultsComma = LocationUriParser.parseLocations("geo:0,0?q=Karl-Marx-Allee+84,%0aBerlin");

        assertEquals(1, resultsComma.length);
        final Location locationComma = resultsComma[0];

        assertEquals(LocationType.ANY, locationComma.type);
        assertFalse(locationComma.hasCoord());
        assertEquals("Karl-Marx-Allee 84, Berlin", locationComma.name);
    }

    @Test
    public void oldCalendar() throws Exception {
        final Location[] results = LocationUriParser.parseLocations("geo:0,0?q=Prinzenstraße 85, Berlin");

        assertEquals(1, results.length);
        final Location location = results[0];

        assertEquals(LocationType.ANY, location.type);
        assertFalse(location.hasCoord());
        assertEquals("Prinzenstraße 85, Berlin", location.name);
    }

    @Test
    public void geoVariant() throws Exception {
        final Location[] results = LocationUriParser.parseLocations("geo:52.1333313,11.60000038?z=6");

        assertEquals(1, results.length);
        final Location location = results[0];

        assertEquals(LocationType.COORD, location.type);
        assertEquals(52133331, location.getLatAs1E6());
        assertEquals(11600000, location.getLonAs1E6());
        assertNull(location.name);
    }

    @Test(expected = RuntimeException.class)
    public void exceptionBecauseOfScheme() throws Exception {
        LocationUriParser.parseLocations("foo:bar");
    }
}
