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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class IOUtils {
    public static byte[] readAllBytes(final InputStream is) throws IOException {
        try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            final byte[] data = new byte[4096];
            int nr;
            while ((nr = is.read(data, 0, data.length)) != -1)
                buffer.write(data, 0, nr);
            return buffer.toByteArray();
        }
    }

    public static String readAllText(final InputStream is) throws IOException {
        return new String(readAllBytes(is), StandardCharsets.UTF_8);
    }

    public static void copyStream(final InputStream is, final OutputStream os) throws IOException {
        final byte[] buffer = new byte[4096];
        for (;;) {
            final int len = is.read(buffer, 0, buffer.length);
            if (len <= 0)
                break;
            os.write(buffer, 0, len);
        }
    }

    public static void deleteDir(final File dir, final boolean deleteSelf) {
        if (dir == null)
            return;
        if (!dir.isDirectory())
            return;
        final File[] files = dir.listFiles();
        if (files == null)
            return;
        for (final File file : files) {
            if (file.isFile())
                file.delete();
            else if (file.isDirectory())
                deleteDir(file, true);
        }
        if (deleteSelf)
            dir.delete();
    }
}
