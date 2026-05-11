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

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SettingsUtil {
    public static final Logger log = LoggerFactory.getLogger(SettingsUtil.class);

    public static String RESTORE_FILENAME_PATTERN = "settings-restore.zip";
    private final Context context;

    public SettingsUtil(final Context context) {
        this.context = context;
    }

    private File getRestoreFile() {
        return new File(context.getCacheDir(), RESTORE_FILENAME_PATTERN);
    }

    public boolean backup(final OutputStream outputStream) {
        final byte[] buffer = new byte[4096];
        final AtomicBoolean anyError = new AtomicBoolean(false);
        try (final ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            final Path dataDir = context.getDataDir().toPath();
            for (final String subDirName : new String[]{
                    "shared_prefs",
                    "databases",
                    "files"
            }) {
                final Path subDir = dataDir.resolve(subDirName);
                try (final Stream<Path> stream = Files.walk(subDir)) {
                    stream.forEach(path -> {
                        log.info("backup: {}", path.toString());
                        final Path zipPath = dataDir.relativize(path);
                        try (final FileInputStream fis = new FileInputStream(path.toFile())) {
                            final ZipEntry zipEntry = new ZipEntry(zipPath.toString());
                            zipEntry.setTime(path.toFile().lastModified());
                            zos.putNextEntry(zipEntry);
                            copyStream(fis, zos, buffer);
                            zos.closeEntry();
                        } catch (final IOException ioe) {
                            log.error("backup {}", path, ioe);
                            anyError.set(true);
                        }
                    });
                } catch (final IOException ioe) {
                    log.error("backup {}", subDir, ioe);
                    anyError.set(true);
                }
            }
        } catch (final IOException ioe) {
            log.error("backup", ioe);
            anyError.set(true);
        }
        return !anyError.get();
    }

    public boolean prepareRestore(final InputStream inputStream) {
        final byte[] buffer = new byte[4096];
        try (final FileOutputStream fos = new FileOutputStream(getRestoreFile())) {
            copyStream(inputStream, fos, buffer);
        } catch (final IOException ioe) {
            log.error("prepareRestore", ioe);
            return false;
        }
        return true;
    }

    private void copyStream(final InputStream is, final OutputStream os, final byte[] buffer) throws IOException {
        int count;
        while ((count = is.read(buffer, 0, buffer.length)) > 0) {
            os.write(buffer, 0, count);
        }
    }

    public boolean restoreIfRequested() {
        final File restoreFile = getRestoreFile();
        if (!restoreFile.exists())
            return false;
        log.info("restoring from previously selected settings");
        final Path dataDir = context.getDataDir().toPath();
        final byte[] buffer = new byte[4096];
        final AtomicBoolean anyError = new AtomicBoolean(false);
        try (final FileInputStream fis = new FileInputStream(restoreFile)) {
            try (final ZipInputStream zis = new ZipInputStream(fis)) {
                for (;;) {
                    final ZipEntry zipEntry = zis.getNextEntry();
                    if (zipEntry == null)
                        break;
                    final String name = zipEntry.getName();
                    final long time = zipEntry.getTime();
                    final File file = dataDir.resolve(name).toFile();
                    file.getParentFile().mkdirs();
                    try (final FileOutputStream fos = new FileOutputStream(file)) {
                        copyStream(zis, fos, buffer);
                    } catch (final IOException ioe) {
                        log.error("restore {}", file, ioe);
                    }
                    file.setLastModified(time);
                }
            } catch (final IOException ioe) {
                log.error("restore", ioe);
                anyError.set(true);
            }
            return true;
        } catch (final IOException ioe) {
            log.error("restore", ioe);
            return false;
        } finally {
            restoreFile.delete();
        }
    }
}
