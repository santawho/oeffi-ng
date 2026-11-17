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

import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontStyle;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.schildbach.oeffi.Application;

public class FontManager {
    private static final Logger log = LoggerFactory.getLogger(FontManager.class);

    public static FontManager instance;

    public static FontManager getInstance() {
        return instance;
    }

    public static Typeface appTypeFace;

    public static void init(final Application application) {
        instance = new FontManager(new File(application.getObbDir(), "appfont"));
        instance.setCustomFontsFromFontDir();
        if (application.getPreferences().getBoolean("user_interface_font_enabled", false))
            appTypeFace = instance.getTypeFace();
        if (appTypeFace == null)
            appTypeFace = Typeface.DEFAULT;
    }

    private static final String REGULAR_FONT_NAME = "regular.ttf";
    private static final String BOLD_FONT_NAME = "bold.ttf";
    private static final String ITALIC_FONT_NAME = "italic.ttf";
    private static final String BOLD_ITALIC_FONT_NAME = "bolditalic.ttf";

    private final File fontDir;
    private Typeface typeFace;

    public FontManager(final File fontDir) {
        this.fontDir = fontDir;
    }

    public Typeface getTypeFace() {
        return typeFace;
    }

    public boolean isCustomFontInstalled() {
        return typeFace != null;
    }

    public void setCustomFontsFromFontDir() {
        setCustomFontsFromDirectory(fontDir);
    }

    public void setCustomFontsFromDirectory(final File dir) {
        setCustomFontsFromFiles(
                new File(dir, REGULAR_FONT_NAME),
                new File(dir, BOLD_FONT_NAME),
                new File(dir, ITALIC_FONT_NAME),
                new File(dir, BOLD_ITALIC_FONT_NAME));
    }

    public void setCustomFontsFromFiles(
            final File regularFontFile,
            final File boldFontFile,
            final File italicFontFile,
            final File boldItalicFontFile) {
        typeFace = createTypeFaceFromFontFiles(
                regularFontFile,
                boldFontFile,
                italicFontFile,
                boldItalicFontFile);
    }

    public static Typeface createTypeFaceFromFontFiles(
            final File regularFontFile,
            final File boldFontFile,
            final File italicFontFile,
            final File boldItalicFontFile) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return null;

        try {
            if (regularFontFile == null || !regularFontFile.isFile())
                return null;

            final FontFamily.Builder familyBuilder = new FontFamily.Builder(
                    new Font.Builder(regularFontFile)
                            .setSlant(FontStyle.FONT_SLANT_UPRIGHT)
                            .setWeight(FontStyle.FONT_WEIGHT_NORMAL)
                            .build());
            addOptionalFontFile(familyBuilder, boldFontFile, FontStyle.FONT_SLANT_UPRIGHT, FontStyle.FONT_WEIGHT_BOLD);
            addOptionalFontFile(familyBuilder, italicFontFile, FontStyle.FONT_SLANT_ITALIC, FontStyle.FONT_WEIGHT_NORMAL);
            addOptionalFontFile(familyBuilder, boldItalicFontFile, FontStyle.FONT_SLANT_ITALIC, FontStyle.FONT_WEIGHT_BOLD);
            return new Typeface.CustomFallbackBuilder(familyBuilder.build()).build();
        } catch (final IOException ioe) {
            log.error("cannot create typeface from font files", ioe);
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static void addOptionalFontFile(
            final FontFamily.Builder familyBuilder,
            final File file,
            final int slant,
            final int weight) throws IOException {
        if (file == null)
            return;
        if (!file.isFile())
            return;
        familyBuilder.addFont(new Font.Builder(file)
                .setSlant(slant)
                .setWeight(weight)
                .build());
    }

    // unpack a ZIP file structure like thos from Google Fonts
    public boolean installCustomFontFromZip(final InputStream zipFileInputStream) throws IOException {
        removeCustomFont();
        fontDir.mkdirs();
        try (final ZipInputStream zis = new ZipInputStream(zipFileInputStream)) {
            for (;;) {
                final ZipEntry zipEntry = zis.getNextEntry();
                if (zipEntry == null)
                    break;
                final String name = zipEntry.getName();
                final File destFile;
                if (name.endsWith("-Regular.ttf")) {
                    // the static regular font
                    destFile = new File(fontDir, REGULAR_FONT_NAME);
                } else if (name.endsWith("-Bold.ttf")) {
                    // the static bold font
                    destFile = new File(fontDir, BOLD_FONT_NAME);
                } else if (name.endsWith("-Italic.ttf")) {
                    // the static italic font
                    destFile = new File(fontDir, ITALIC_FONT_NAME);
                } else if (name.endsWith("-BoldItalic.ttf")) {
                    // the static bold italic font
                    destFile = new File(fontDir, BOLD_ITALIC_FONT_NAME);
                } else if (name.endsWith("-Italic-VariableFont_wght.ttf")) {
                    // the static bold italic font
                    destFile = new File(fontDir, BOLD_ITALIC_FONT_NAME);
                    if (destFile.exists())
                        continue; // already provided as static font
                } else if (name.endsWith("-VariableFont_wght.ttf")) {
                    // the static bold italic font
                    destFile = new File(fontDir, REGULAR_FONT_NAME);
                    if (destFile.exists())
                        continue; // already provided as static font
                } else {
                    continue;
                }
                try (final FileOutputStream fos = new FileOutputStream(destFile)) {
                    IOUtils.copyStream(zis, fos);
                } catch (final IOException ioe) {
                    log.error("cannot unpack font zip", ioe);
                    return false;
                }
            }
        }

        setCustomFontsFromFontDir();
        final boolean installed = isCustomFontInstalled();
        if (!installed)
            log.error("newly installed fonts not applicable");
        return installed;
    }

    public void removeCustomFont() {
        typeFace = null;
        IOUtils.deleteDir(fontDir, true);
    }
}
