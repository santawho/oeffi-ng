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
        return Base64.encodeToString(serialize(object), Base64.DEFAULT);
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

    public static Object deserialize(final String base64) {
        if (base64 == null) return null;
        return deserialize(Base64.decode(checkNotNull(base64), Base64.DEFAULT));
    }

    public static <T extends Serializable> T clone(T object) {
        if (object == null) return null;
        return (T) deserialize(serialize(object));
    }
}
