package de.schildbach.oeffi.util;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;

public class LocationUtils {
    public static Location locationFromCoord(final Point coord) {
        return new Location(LocationType.COORD, null, coord);
    }
}
