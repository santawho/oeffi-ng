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

import static com.google.common.base.Preconditions.checkNotNull;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Objects {
    public static byte[] serialize(final Serializable object) {
        if (object == null) return null;
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(object);
            final byte[] bytes = bos.toByteArray();
            oos.close();
            return bytes;
        } catch (final IOException x) {
            throw new RuntimeException(x);
        }
    }

    public static String serializeToString(final Serializable object) {
        if (object == null) return null;
        return Base64.encodeToString(serialize(object), Base64.NO_WRAP);
    }

    public static String serializeAndCompressToString(final Serializable object) throws IOException {
        if (object == null) return null;
        return compressToString(serialize(object));
    }

    public static String compressToString(final byte[] bytes) throws IOException {
        if (bytes == null) return null;
        final Deflater deflater = new Deflater();
        deflater.setInput(bytes);
        deflater.finish();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int compressedSize = deflater.deflate(buffer);
            os.write(buffer, 0, compressedSize);
        }
        os.close();
        final byte[] compressed = os.toByteArray();
        return Base64.encodeToString(compressed, Base64.NO_WRAP);
    }

    public static Object deserialize(final byte[] bytes) {
        if (bytes == null) return null;
        try {
            final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
            final Object obj = ois.readObject();
            ois.close();
            return obj;
        } catch (final ClassNotFoundException | IOException x) {
            throw new RuntimeException(x);
        }
    }

    public static Object deserializeFromString(final String base64) {
        if (base64 == null) return null;
        return deserialize(Base64.decode(checkNotNull(base64), Base64.DEFAULT));
    }

    public static byte[] uncompressFromString(final String base64) throws Exception {
        if (base64 == null) return null;
        final byte[] compressed = Base64.decode(checkNotNull(base64), Base64.DEFAULT);
        final Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int decompressedSize = inflater.inflate(buffer);
            os.write(buffer, 0, decompressedSize);
        }
        os.close();
        return os.toByteArray();
    }

    public static Object deserializeFromCompressedString(final String base64) throws Exception {
        return deserialize(uncompressFromString(base64));
    }

    public static <T extends Serializable> T clone(T object) {
        if (object == null) return null;
        return (T) deserialize(serialize(object));
    }
}
