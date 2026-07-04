package de.schildbach.oeffi.util;

import org.junit.Test;

public class ColorHashTest {
    private void print(final String stationName) {
        print(false, stationName);
        print(true, stationName);
    }

    private void print(final boolean dark, final String stationName) {
        final ColorHash colorHash;
        final String label;
        if (dark) {
            colorHash = ColorHash.COLORHASH_DARK_MODE;
            label = "dark";
        } else {
            colorHash = ColorHash.COLORHASH_LIGHT_MODE;
            label = "light";
        }
        print(label, colorHash, stationName);
    }

    private void print(final String label, final ColorHash colorHash, final String stationName) {
        final String color = colorHash.toHexString(stationName);
        System.out.printf("\"%s\": %s = %s%n", stationName, label, color);
    }

    @Test
    public void t1() {
        print("Bonn, Ippendorf Ippendorfer Allee");
    }
}
