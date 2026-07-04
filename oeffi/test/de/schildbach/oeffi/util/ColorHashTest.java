package de.schildbach.oeffi.util;

import org.junit.Test;

public class ColorHashTest {
    private void print(final String stationName) {
        print(false, stationName);
        print(true, stationName);
    }

    private void print(final boolean dark, final String stationName) {
        final ColorHash colorHash = ColorHash.getExtendedColorHash(dark);
        final String label = dark ? "dark" : "light";
        print(dark, label, colorHash, stationName);
    }

    private void print(final boolean dark, final String label, final ColorHash colorHash, final String stationName) {
        final ColorHash.HSL hsl = colorHash.toHSL(stationName);
        final ColorHash.RGB rgb = hsl.toRGB();
        final String color = rgb.toHex();
        System.out.printf("\"%s\": %s = %s h=%.0f s=%.2f l=%.2f \033[%s;38;2;%d;%d;%dmXXXXXXXXX\033[39;49m \n",
                stationName, label, color,
                hsl.hue, hsl.saturation, hsl.lightness,
                dark ? "40" : "49",
                rgb.red, rgb.green, rgb.blue);
    }

    public void runTests(final boolean dark) {
        for (char n='A'; n<='Z'; ++n)
            print(dark, "" + n);
    }

    @Test
    public void testLightMode() {
        runTests(false);
    }

    @Test
    public void testDarkMode() {
        runTests(true);
    }
}
