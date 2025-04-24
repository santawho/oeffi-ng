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

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocationUriParser {
    public static final Location[] parseLocations(final String encodedUriString) {
        URI uri;
        try {
            uri = new URI(encodedUriString);
        } catch (final URISyntaxException x) {
            try {
                // work around uri encoding bug in Google Calendar
                uri = new URI(encodedUriString.replace(' ', '+').replaceAll("\n", "%0a"));
            } catch (final URISyntaxException x2) {
                throw new RuntimeException(x2);
            }
        }

        final String scheme = uri.getScheme();

        if ("geo".equals(scheme)) {
            final Location location = parseGeo(uri.getSchemeSpecificPart().replaceAll(",?\n+", ",+"));

            return new Location[] { location };
        }

        throw new IllegalArgumentException("cannot parse: '" + encodedUriString + "'");
    }

    private static final Pattern P_URI_GEO = Pattern.compile("(-?\\d*\\.?\\d+),(-?\\d*\\.?\\d+)", Pattern.DOTALL);

    private static Location parseGeo(final String query) throws NumberFormatException {
        final int sepIndex = query.indexOf('?');
        final String coordStr;
        final String q;
        if (sepIndex != -1) {
            coordStr = query.substring(0, sepIndex);
            final String params = query.substring(sepIndex + 1);
            q = getQueryParameter(params, "q");
        } else {
            coordStr = query;
            q = null;
        }

        final Matcher m = P_URI_GEO.matcher(coordStr);
        final Point coord;
        if (m.matches()) {
            final Point c = Point.fromDouble(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
            if (c.getLatAs1E6() != 0 || c.getLonAs1E6() != 0)
                coord = c;
            else
                coord = null;
        } else {
            coord = null;
        }

        if (coord != null) {
            if (q == null)
                return Location.coord(coord);
            return new Location(LocationType.ADDRESS, null, coord, null, q);
        }

        if (q != null)
            return parseAddrParam(q, null);

        throw new IllegalArgumentException("cannot parse: '" + query + "'");
    }

    private static String getQueryParameter(final String query, final String name) {
        final int nameIndex = findQueryParameterName(query, name);
        if (nameIndex < 0)
            return null;

        final int startIndex = nameIndex + name.length() + 1;
        final int endIndex = query.indexOf('&', startIndex);

        final String param;
        if (endIndex >= 0)
            param = query.substring(startIndex, endIndex);
        else
            param = query.substring(startIndex);

        return normalizeDecodeParam(param);
    }

    private static int findQueryParameterName(final String query, final String name) {
        if (query.startsWith(name + '='))
            return 0;

        final int index = query.indexOf('&' + name + '=');
        if (index >= 0)
            return index + 1;

        return -1;
    }

    private static final Pattern P_URI_LOCATION = Pattern
            .compile("(?:([^@]*)@)?(-?\\d*\\.\\d+),(-?\\d*\\.\\d+)(?:\\(([^\\)]*)\\))?", Pattern.DOTALL);

    private static Location parseAddrParam(final String param, final String title) throws NumberFormatException {
        final Matcher m = P_URI_LOCATION.matcher(param);

        if (!m.matches())
            return null;

        final Point point = Point.fromDouble(Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)));

        if (title != null)
            return new Location(LocationType.ADDRESS, null, point, null, title);
        else if (m.group(1) != null)
            return new Location(LocationType.ADDRESS, null, point, null, m.group(1).length() > 0 ? m.group(1) : null);
        else if (m.group(4) != null)
            return new Location(LocationType.ADDRESS, null, point, null, m.group(4));
        else
            return Location.coord(point);
    }

    private static String normalizeDecodeParam(final String raw) {
        if (raw == null)
            return null;

        final String trimmed = raw.trim();
        if (raw.length() == 0)
            return null;

        return URLDecoder.decode(trimmed);
    }
}
