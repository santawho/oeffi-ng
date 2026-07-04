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

import android.graphics.Color;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

// compare to https://github.com/unhappychoice/color-hash.kt

//   Color color = new ColorHash(
//           Arrays.asList(0.35, 0.5, 0.65), // lightness list
//           Arrays.asList(0.35, 0.5, 0.65), // saturation list
//           0,  // minHue, hue is degress anti-clockwise
//           360 // maxHue, may be less than minHue to exclude a color range
//   ).toColor(
//           "some string", // string which you want to use as hash
//   ); // returns Android Color class

public class ColorHash {
    public static final ColorHash COLORHASH_GENERAL = new ColorHash(
            new double[]{ // available hue values
                    0.0, // red
                    20.0, 32.0, 45.0,
                    //except: 60.0, // yellow
                    75.0, 95.0,
                    120.0, // green
                    160.0, 175.0, 185.0, 200.0, 215.0,
                    240.0, // blue
                    265.0, 280.0, 300.0, 320.0},
            new double[]{0.30, 0.45, 0.60, 0.70},
            new double[]{0.20, 0.30, 0.40, 0.50});

    public static final double[] SATURATION_VALUES = {0.30, 0.45, 0.60, 0.70};
    public static final double[] LIGHTNESS_VALUES_LIGHT_MODE = {0.20, 0.30, 0.40, 0.50};
    public static final double[] LIGHTNESS_VALUES_DARK_MODE = {0.60, 0.70, 0.80, 0.90};

    public static final ColorHash COLORHASH_STANDARD_LIGHT_MODE = new ColorHash(
            new double[]{ // available hue values
                    0.0, // red
                    30.0,
                    //except: 60.0, // yellow
                    75.0,
                    120.0, // green
                    160.0,
                    180.0, 200.0,
                    240.0, // blue
                    280.0, 310.0},
            SATURATION_VALUES,
            LIGHTNESS_VALUES_LIGHT_MODE);
    public static final ColorHash COLORHASH_STANDARD_DARK_MODE = new ColorHash(
            new double[]{ // available hue values
                    0.0, // red
                    30.0,
                    60.0, // yellow
                    75.0,
                    120.0, // green
                    160.0,
                    180.0, 200.0,
                    //except: 240.0, // blue
                    280.0, 310.0},
            SATURATION_VALUES,
            LIGHTNESS_VALUES_DARK_MODE);
    public static ColorHash getStandardColorHash(final boolean darkMode) {
        return darkMode ? COLORHASH_STANDARD_DARK_MODE : COLORHASH_STANDARD_LIGHT_MODE;
    }

    public static final ColorHash COLORHASH_EXTENDED_LIGHT_MODE = new ColorHash(
            new double[]{ // available hue values
                    0.0, // red
                    20.0, 32.0, 45.0,
                    //except: 60.0, // yellow
                    75.0, 95.0,
                    120.0, // green
                    160.0, 175.0, 185.0, 200.0, 215.0,
                    240.0, // blue
                    265.0, 280.0, 300.0, 320.0},
            SATURATION_VALUES,
            LIGHTNESS_VALUES_LIGHT_MODE);
    public static final ColorHash COLORHASH_EXTENDED_DARK_MODE = new ColorHash(
            new double[]{ // available hue values
                    0.0, // red
                    20.0, 32.0, 45.0,
                    60.0, // yellow
                    75.0, 95.0,
                    120.0, // green
                    160.0, 175.0, 185.0, 200.0, 215.0,
                    //except: 240.0, // blue
                    265.0, 280.0, 300.0, 320.0},
            SATURATION_VALUES,
            LIGHTNESS_VALUES_DARK_MODE);
    public static ColorHash getExtendedColorHash(final boolean darkMode) {
        return darkMode ? COLORHASH_EXTENDED_DARK_MODE : COLORHASH_EXTENDED_LIGHT_MODE;
    }

    public interface StringHasher {
        long stringToHash(String string);
    }

    public static class RGB {
        public final int red;
        public final int green;
        public final int blue;

        public RGB(final int red, final int green, final int blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public String toHex() {
            final List<Integer> colors = new ArrayList<>();
            colors.add(red);
            colors.add(green);
            colors.add(blue);

            final StringBuilder hexString = new StringBuilder("#");
            for (final int color : colors) {
                if (color < 16) {
                    hexString.append("0").append(Integer.toHexString(color));
                } else {
                    hexString.append(Integer.toHexString(color));
                }
            }
            return hexString.toString();
        }

        public Color toColor() {
            return Color.valueOf(red / 256f, green / 256f, blue / 256f);
        }

        public int toARGB() {
            return 0xFF000000 | (red << 16) | (green << 8) | blue;
        }
    }

    public static class HSL {
        public final double hue;
        public final double saturation;
        public final double lightness;

        public HSL(final double hue, final double saturation, final double lightness) {
            this.hue = hue;
            this.saturation = saturation;
            this.lightness = lightness;
        }

        public RGB toRGB() {
            final double h = hue / 360.0;

            final double q = (lightness < 0.5)
                    ? lightness * (1.0 + saturation)
                    : lightness + saturation - lightness * saturation;

            final double p = 2.0 * lightness - q;

            return new RGB(
                    channel(p, q, h + 1.0 / 3.0),
                    channel(p, q, h),
                    channel(p, q, h - 1.0 / 3.0));
        }

        private int channel(final double p, final double q, final double color) {
            final double co;
            if (color < 0) {
                co = color + 1;
            } else if (color > 1) {
                co = color - 1;
            } else {
                co = color;
            }

            final double c;
            if (co < 1.0 / 6.0) {
                c = p + (q - p) * 6.0 * co;
            } else if (co < 0.5) {
                c = q;
            } else if (co < 2.0 / 3.0) {
                c = p + (q - p) * 6.0 * (2.0 / 3.0 - co);
            } else {
                c = p;
            }
            final double ch = Math.max(0.0, Math.min(Math.floor(c * 256.0), 255.0));
            return (int) ch;
        }

        public int toColor() {
            final float[] array = new float[]{(float) hue, (float) saturation, (float) lightness};
            return Color.HSVToColor(array);
        }
    }

    private final double[] hueValues;
    private final double[] saturationValues;
    private final double[] lightnessValues;
    private final StringHasher stringHasher;

    public static final long SEED = 131L;
    public static final long SEED2 = 137L;
    public static final long MAX_SAFE_LONG = 9007199254740991L / SEED2; // 65745979961613L;

    public ColorHash() {
        this(
                new double[]{0.0, 120.0, 240.0},
                new double[]{0.35, 0.5, 0.65},
                new double[]{0.35, 0.5, 0.65});
    }

    public ColorHash(
            final double[] hueValues,
            final double[] saturationValues,
            final double[] lightnessValues) {
        this(hueValues, saturationValues, lightnessValues, ColorHash::md5Hash);
    }

    public ColorHash(
            final double[] hueValues,
            final double[] saturationValues,
            final double[] lightnessValues,
            final StringHasher stringHasher) {
        this.hueValues = hueValues;
        this.saturationValues = saturationValues;
        this.lightnessValues = lightnessValues;
        this.stringHasher = stringHasher;
    }

    public HSL toHSL(final String string) {
        long hash = stringHasher.stringToHash(string) & Long.MAX_VALUE;

        final long numHueValues = hueValues.length;
        final int hueIndex = (int)(hash % numHueValues);
        hash = hash / numHueValues;
        final long numSatValues = saturationValues.length;
        final int satIndex = (int)(hash % numSatValues);
        hash = hash / numSatValues;
        final long numLightnessValues = lightnessValues.length;
        final int lightIndex = (int) (hash % numLightnessValues);

        final double hue = hueValues[hueIndex];
        final double sat = saturationValues[satIndex];
        final double light = lightnessValues[lightIndex];
        return new HSL(hue, sat, light);
    }

    public RGB toRGB(final String string) {
        return toHSL(string).toRGB();
    }

    public int toARGB(final String string) {
        return toRGB(string).toARGB();
    }

    public String toHexString(final String string) {
        return toRGB(string).toHex();
    }

    public Color toColor(final String string) {
        return toRGB(string).toColor();
    }

    public static long javaHash(final String string) {
        return string.hashCode();
    }

    public static long bkdrHash(final String string) {
        long acc = 0L;
        for (final char value : (string + 'x').toCharArray()) {
            if (acc > MAX_SAFE_LONG) {
                acc /= SEED2;
            }
            acc = acc * SEED + (long) value;
        }
        return acc;
    }

    private static final MessageDigest md5;
    static {
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static long md5Hash(final String string) {
        md5.reset();
        md5.update(string.getBytes());
        final byte[] bytes = md5.digest();
        final long hash =
                ((bytes[1] & 0xFFL) << 56) |
                ((bytes[2] & 0xFFL) << 48) |
                ((bytes[4] & 0xFFL) << 40) |
                ((bytes[6] & 0xFFL) << 32) |
                ((bytes[7] & 0xFFL) << 24) |
                ((bytes[9] & 0xFFL) << 16) |
                ((bytes[11] & 0xFFL) << 8) |
                ((bytes[13] & 0xFFL) << 0) ;
        return hash;
    }
}

