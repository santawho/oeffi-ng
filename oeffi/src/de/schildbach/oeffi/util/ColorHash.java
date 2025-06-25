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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// compare to https://github.com/unhappychoice/color-hash.kt

//   Color color = new ColorHash(
//           Arrays.asList(0.35, 0.5, 0.65), // lightness list
//           Arrays.asList(0.35, 0.5, 0.65), // saturation list
//           0, // minHue
//           360 // maxHue
//   ).toColor(
//           "some string", // string which you want to use as hash
//   ); // returns Android Color class

public class ColorHash {
    private static final Logger log = LoggerFactory.getLogger(ColorHash.class);

    public interface StringHasher {
        long stringToHash(String string);
    }

    public static class RGB {
        private int red;
        private int green;
        private int blue;

        public RGB(int red, int green, int blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public String toHex() {
            List<Integer> colors = new ArrayList<>();
            colors.add(red);
            colors.add(green);
            colors.add(blue);
            Collections.reverse(colors);

            StringBuilder hexString = new StringBuilder("#");
            for (int color : colors) {
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
        private final double hue;
        private final double saturation;
        private final double lightness;

        public HSL(double hue, double saturation, double lightness) {
            this.hue = hue;
            this.saturation = saturation;
            this.lightness = lightness;
        }

        public RGB toRGB() {
            final double h = hue / 360.0;

            final double q;
            if (lightness < 0.5) {
                q = lightness * (1.0 + saturation);
            } else {
                q = lightness + saturation - lightness * saturation;
            }

            final double p = 2.0 * lightness - q;

            final List<Double> rgbs = new ArrayList<>();
            for (double color : new double[]{h + 1.0 / 3.0, h, h - 1.0 / 3.0}) {
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
                rgbs.add(Math.max(0.0, Math.round(c * 255)));
            }

            final RGB rgb = new RGB(rgbs.get(0).intValue(), rgbs.get(1).intValue(), rgbs.get(2).intValue());
//            log.debug("H={} S={} L={}  -->  R={} G={} B={}", hue, saturation, lightness, rgb.red, rgb.green, rgb.blue);
            return rgb;
        }

        public int toColor() {
            float[] array = new float[]{(float) hue, (float) saturation, (float) lightness};
            return Color.HSVToColor(array);
        }
    }

    private final List<Double> lightness;
    private final List<Double> saturation;
    private final int minHue;
    private final int maxHue;
    private final StringHasher stringHasher;

    public static final long SEED = 131L;
    public static final long SEED2 = 137L;
    public static final long MAX_SAFE_LONG = 9007199254740991L / SEED2; // 65745979961613L;

    public ColorHash() {
        this(
                Arrays.asList(0.35, 0.5, 0.65),
                Arrays.asList(0.35, 0.5, 0.65),
                0, 360,
                ColorHash::javaHash
        );
    }

    public ColorHash(
            final List<Double> lightness,
            final List<Double> saturation,
            final int minHue,
            final int maxHue) {
        this(lightness, saturation, minHue, maxHue, ColorHash::javaHash);
    }

    public ColorHash(
            final List<Double> lightness,
            final List<Double> saturation,
            final int minHue,
            final int maxHue,
            StringHasher stringHasher) {
        this.lightness = lightness;
        this.saturation = saturation;
        this.minHue = minHue;
        this.maxHue = maxHue;
        this.stringHasher = stringHasher;
    }

    public HSL toHSL(final String string) {
        long hash = stringHasher.stringToHash(string) & Long.MAX_VALUE;

        final long hueVal = hash % 997;
        double hue = (((double) hueVal / 997.0) * (double)(maxHue - minHue) + (double)minHue) % 360.0;
        hash = hash / 997;

        double sat = saturation.get((int) (hash % saturation.size()));
//        sat = 0.95;

        hash = hash / saturation.size();

        double light = lightness.get((int) (hash % lightness.size()));
//        light = 0.9;

//        log.debug("\"{}\": H={} S={} L={}", string, hue, sat, light);
        return new HSL(hue, sat, light);
    }

    public RGB toRGB(final String string) {
        return toHSL(string).toRGB();
    }

    public int toARGB(final String string) {
        return toRGB(string).toARGB();
//        return toHSL(string).toColor();
    }

    public String toHexString(final String string) {
        return toRGB(string).toHex();
    }

    public Color toColor(final String string) {
        final Color color = toRGB(string).toColor();
//        log.debug("\"{}\": R={} G={} B={}, A={}", string, color.red(), color.green(), color.blue(), color.alpha());
        return color;
    }

    public static long javaHash(final String string) {
        return string.hashCode();
    }

    public static long bkdrHash(final String string) {
        long acc = 0L;
        for (char value : (string + 'x').toCharArray()) {
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
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static long md5Hash(final String string) {
        md5.reset();
        md5.update(string.getBytes());
        final byte[] bytes = md5.digest();
        long hash =
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

