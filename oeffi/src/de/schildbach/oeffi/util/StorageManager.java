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
import java.io.InputStream;
import java.io.OutputStream;

import de.schildbach.oeffi.Application;

public class StorageManager {
    private static final Logger log = LoggerFactory.getLogger(StorageManager.class);
    public final String KEY_STORAGE_EXTERNAL_OBB_ENABLED = "storage_external_obb_enabled";
    public final String KEY_STORAGE_EXTERNAL_CACHE_ENABLED = "storage_external_cache_enabled";

    private final Storage obbStorage;
    private final Storage cacheStorage;

    public StorageManager(final Application application, final Context rootContext) {
        cacheStorage = new Storage(application,
                KEY_STORAGE_EXTERNAL_CACHE_ENABLED,
                rootContext.getCacheDir(), rootContext.getExternalCacheDirs());
        obbStorage = new Storage(application,
                KEY_STORAGE_EXTERNAL_OBB_ENABLED,
                rootContext.getObbDir(), rootContext.getObbDirs());
    }

    public File getObbDir() {
        return obbStorage.getActiveDir();
    }

    public File getCacheDir() {
        return cacheStorage.getActiveDir();
    }

    private static class Storage {
        private final File activeDir;

        public File getActiveDir() {
            return activeDir;
        }

        public Storage(
                final Application application,
                final String prefKeyExternalEnabled,
                final File internalDir,
                final File[] externalDirs) {
            final boolean externalEnabled = application.getPreferences().getBoolean(prefKeyExternalEnabled, false);
            if (externalEnabled && externalDirs != null && externalDirs.length >= 2 && externalDirs[1] != null) {
                activeDir = externalDirs[1];
            } else if (externalEnabled && externalDirs != null && externalDirs.length >= 1 && externalDirs[0] != null) {
                activeDir = externalDirs[0];
            } else {
                activeDir = internalDir;
            }
            boolean success = true;
            success &= migrateDir(internalDir, activeDir);
            if (externalDirs != null) {
                for (final File externalDir : externalDirs)
                    success &= migrateDir(externalDir, activeDir);
            }
            if (!success) {
                log.warn("some migrations failed");
            }
        }
    }

    public static boolean migrateDir(final File fromDir, final File toDir) {
        if (fromDir == null || fromDir.equals(toDir) || !fromDir.exists())
            return true;
        final File[] files = fromDir.listFiles();
        boolean success = true;
        if (files != null) {
            for (final File file : files) {
                success &= migrateFile(file, fromDir, toDir);
            }
        }
        return success;
    }

    public static boolean migrateFile(final File srcFile, final File fromDir, final File toDir) {
        final String filePath = srcFile.getAbsolutePath();
        final String fromPath = fromDir.getAbsolutePath();
        if (!filePath.startsWith(fromPath))
            return false;
        final String fileRelPath = filePath.substring(fromPath.length());
        final File destFile = new File(toDir, fileRelPath);
        final String fileName = destFile.getName();
        final String destPath = destFile.getPath();
        final File destDir = new File(destPath.substring(0, destPath.length() - fileName.length()));

        if (srcFile.isDirectory()) {
            if (!migrateDir(srcFile, destFile))
                return false;
        } else if (srcFile.isFile()) {
            log.debug("migrate {} to {}", srcFile, destFile);
            try {
                if (!destDir.exists() && !destDir.mkdirs()) {
                    log.error("failed to mkdirs {}", destDir);
                    return false;
                }
            } catch (final Exception e) {
                log.error("cannot mkdirs {}", destDir, e);
                return false;
            }
            try (final InputStream is = new FileInputStream(srcFile);
                 final OutputStream os = new FileOutputStream(destFile)) {
                IOUtils.copyAll(is, os);
            } catch (final Exception e) {
                log.error("cannot copy {} to {}", srcFile, destFile, e);
                return false;
            }
        }

        try {
            if (!srcFile.delete()) {
                log.error("failed to delete {}", srcFile);
                return false;
            }
        } catch (final Exception e) {
            log.error("cannot delete {}", srcFile, e);
            return false;
        }

        return true;
    }
}
