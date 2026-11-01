package de.schildbach.oeffi.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

public class Preferences {
    final Context context;
    final SharedPreferences prefs;
    final Resources resources;

    public Preferences(final Context context, final SharedPreferences sharedPreferences) {
        this.context = context;
        this.prefs = sharedPreferences;
        this.resources = context.getResources();
    }

    public SharedPreferences getSharedPreferences() {
        return prefs;
    }

    public String getString(final String key) {
        return getString(key, null);
    }

    public String getString(final String key, final String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    public String getStringR(final String key, final int defaultValueResId) {
        return prefs.getString(key, resources.getString(defaultValueResId));
    }

    public Integer getInteger(final String key) {
        final String text = getString(key, null);
        if (text == null)
            return null;
        return parseInt(text, 0);
    }

    public int getInteger(final String key, final int defaultValue, final int parseErrorValue) {
        final String text = getString(key, null);
        if (text == null)
            return defaultValue;
        return parseInt(text, parseErrorValue);
    }

    public int getIntegerRI(final String key, final int defaultValueResId, final int parseErrorValue) {
        final String text = getString(key, null);
        if (text == null)
            return resources.getInteger(defaultValueResId);
        return parseInt(text, parseErrorValue);
    }

    public int getIntegerRS(final String key, final int defaultValueResId, final int parseErrorValue) {
        final String text = getString(key, resources.getString(defaultValueResId));
        return parseInt(text, parseErrorValue);
    }

    public Float getFloat(final String key) {
        final String text = getString(key, null);
        if (text == null)
            return null;
        return parseFloat(text, null);
    }

    public float getFloat(final String key, final float defaultValue, final Float parseErrorValue) {
        final String text = getString(key, null);
        if (text == null)
            return defaultValue;
        return parseFloat(text, parseErrorValue);
    }

    public float getFloatR(final String key, final int defaultValueResId, final Float parseErrorValue) {
        final String text = getString(key, resources.getString(defaultValueResId));
        return parseFloat(text, parseErrorValue);
    }

    public Long getMillis(final String key, final long factor, final Long defaultValue, final Long parseErrorValue) {
        final String text = getString(key, null);
        if (text == null)
            return defaultValue;
        final Float value = parseFloat(text, null);
        if (value == null)
            return parseErrorValue;
        return (long) (value * factor);
    }

    public Long getMillisR(final String key, final long factor, final int defaultValueResId, final Long parseErrorValue) {
        final String text = getString(key, resources.getString(defaultValueResId));
        final Float value = parseFloat(text, null);
        if (value == null)
            return parseErrorValue;
        return (long) (value * factor);
    }

    private static int parseInt(final String text, final int defaultValue) {
        if (text == null)
            return defaultValue;
        try {
            return Integer.parseInt(text);
        } catch (final NumberFormatException nfe) {
            return defaultValue;
        }
    }

    private static Float parseFloat(final String text, final Float defaultValue) {
        if (text == null)
            return defaultValue;
        try {
            return Float.parseFloat(text);
        } catch (final NumberFormatException nfe) {
            return defaultValue;
        }
    }
}
