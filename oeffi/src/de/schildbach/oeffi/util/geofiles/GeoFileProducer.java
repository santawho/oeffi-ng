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

package de.schildbach.oeffi.util.geofiles;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import de.schildbach.oeffi.Application;
import de.schildbach.pte.dto.Trip;

public abstract class GeoFileProducer {
    protected Application application;

    public void setApplication(final Application application) {
        this.application = application;
    }

    public abstract String getFilenameExtension();

    public void writeTrip(final Trip trip, final File file) throws IOException {
        final FileOutputStream fos = new FileOutputStream(file);
        if (trip != null)
            writeTrip(trip, fos);
        fos.close();
    }

    protected abstract void writeTrip(final Trip trip, final OutputStream outputStream) throws IOException;
}
