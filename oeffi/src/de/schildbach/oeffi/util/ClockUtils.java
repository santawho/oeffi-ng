package de.schildbach.oeffi.util;

import android.os.SystemClock;

public class ClockUtils {
    public static long elapsedTimeToClock(final long elapsed) {
        final long nowClock = System.currentTimeMillis();
        final long nowElapsed = SystemClock.elapsedRealtime();
        return elapsed - nowElapsed + nowClock;
    }

    public static long clockToElapsedTime(final long clock) {
        final long nowClock = System.currentTimeMillis();
        final long nowElapsed = SystemClock.elapsedRealtime();
        return clock - nowClock + nowElapsed;
    }

    public static long elapsedTimePlus(final long plusMillis) {
        final long nowElapsed = SystemClock.elapsedRealtime();
        return nowElapsed + plusMillis;
    }
}
