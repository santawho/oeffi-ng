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

import java.io.File;
import java.io.IOException;

public class FontUtil {
    private static final Typeface FALLBACK_TYPEFACE = Typeface.DEFAULT;
    private static Typeface appTypeFace = FALLBACK_TYPEFACE;

    public static Typeface getAppTypeFace() {
        return appTypeFace;
    }

    public static void setAppFontsFromPreferences() {
//        setAppFontsFromFiles(
//                "/storage/0000-0000/Android/obb/de.santawho.oeffi_ng.debug/AtkinsonHyperlegibleNext-VariableFont_wght.ttf",
//                "/storage/0000-0000/Android/obb/de.santawho.oeffi_ng.debug/AtkinsonHyperlegibleNext-Italic-VariableFont_wght.ttf");
    }

    public static void setAppFontsFromFiles(
            final String regularFontFilePath,
            final String italicFontFilePath) {
        appTypeFace = createTypeFaceFromFontFiles(regularFontFilePath, italicFontFilePath);
    }

    public static Typeface createTypeFaceFromFontFiles(
            final String regularFontFilePath,
            final String italicFontFilePath) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return FALLBACK_TYPEFACE;

        try {
            return new Typeface.CustomFallbackBuilder(
                    new FontFamily.Builder((new Font.Builder(
                            new File(regularFontFilePath))
                            .setSlant(FontStyle.FONT_SLANT_UPRIGHT)
                            .setWeight(FontStyle.FONT_WEIGHT_NORMAL)
                            .build()))
                    .addFont(new Font.Builder(
                            new File(italicFontFilePath))
                            .setSlant(FontStyle.FONT_SLANT_ITALIC)
                            .setWeight(FontStyle.FONT_WEIGHT_NORMAL)
                            .build())
                    .build())
                    .build();
        } catch (final IOException ioe) {
            return FALLBACK_TYPEFACE;
        }
    }
}
