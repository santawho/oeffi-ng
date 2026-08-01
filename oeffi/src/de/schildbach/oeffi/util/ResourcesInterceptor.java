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

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.content.res.loader.ResourcesLoader;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import de.schildbach.oeffi.R;

public class ResourcesInterceptor extends Resources {
    public static Context getContext(final Context baseContext) {
        return new ContextWrapper(baseContext, null);
    }

    public static Context getContext(final Context baseContext, final Resources resources) {
        return new ContextWrapper(baseContext, resources);
    }

    public static void setConfiguration(final Properties properties) {
        mapper.setConfiguration(properties);
    }

    public static class ContextWrapper extends android.content.ContextWrapper {
        private Resources resources;

        private ContextWrapper(final Context baseContext, final Resources explicitResources) {
            super(baseContext);
            if (explicitResources != null)
                this.resources = new ResourcesInterceptor(explicitResources);
            if (mapper == null)
                mapper = new ResourcesMapper(explicitResources);
        }

        @Override
        public Resources getResources() {
            if (resources == null)
                resources = new ResourcesInterceptor(getBaseContext().getResources());
            return resources;
        }
    }

    private static class ResourcesMapper {
        private final Resources resources;

        private Properties configuredValues;

        public ResourcesMapper(final Resources resources) {
            requireNonNull(resources);
            this.resources = resources;
        }

        public void setConfiguration(final Properties properties) {
            configuredValues = properties;
            overloadedString.clear();
            overloadedInteger.clear();
            overloadedBoolean.clear();
            overloadedColor.clear();
        }

        private Properties getAllResourcesAsProperties() throws IllegalAccessException {
            return ResourcesInterceptor.getAllResourcesAsProperties(resources);
        }

        private String getConfiguredValue(final int resId) {
            try {
                if (configuredValues == null)
                    return null;
                final String entryName = resources.getResourceEntryName(resId);
                if (entryName == null)
                    return null;
                return configuredValues.getProperty(entryName);
            } catch (final NotFoundException nfe) {
                return null;
            }
        }

        private static class MapValue<T> {
            private final T value;
            MapValue(final T value) {
                this.value = value;
            }
        }

        private Map<Integer, MapValue<String>> overloadedString = new HashMap<>();

        public String getString(final int id) {
            final MapValue<String> mapValue = overloadedString.get(id);
            if (mapValue != null)
                return mapValue.value;
            final String configuredValue = getConfiguredValue(id);
            if (configuredValue == null) {
                overloadedColor.put(id, null);
                return null;
            }
            overloadedString.put(id, new MapValue<>(configuredValue));
            return configuredValue;
        }

        private Map<Integer, MapValue<Boolean>> overloadedBoolean = new HashMap<>();

        public Boolean getBoolean(final int id) {
            final MapValue<Boolean> mapValue = overloadedBoolean.get(id);
            if (mapValue != null)
                return mapValue.value;
            final String configuredValue = getConfiguredValue(id);
            if (configuredValue == null) {
                overloadedBoolean.put(id, null);
                return null;
            }
            final boolean configuredBoolean = Boolean.parseBoolean(configuredValue);
            overloadedBoolean.put(id, new MapValue<>(configuredBoolean));
            return configuredBoolean;
        }

        private Map<Integer, MapValue<Integer>> overloadedInteger = new HashMap<>();

        public Integer getInteger(final int id) {
            final MapValue<Integer> mapValue = overloadedInteger.get(id);
            if (mapValue != null)
                return mapValue.value;
            final String configuredValue = getConfiguredValue(id);
            if (configuredValue == null) {
                overloadedInteger.put(id, null);
                return null;
            }
            int configuredInteger;
            try {
                configuredInteger = Integer.parseInt(configuredValue);
            } catch (final NumberFormatException nfe) {
                configuredInteger = 0;
            }
            overloadedInteger.put(id, new MapValue<>(configuredInteger));
            return configuredInteger;
        }

        private Map<Integer, MapValue<Integer>> overloadedColor = new HashMap<>();

        public Integer getColor(final int id) {
            final MapValue<Integer> mapValue = overloadedColor.get(id);
            if (mapValue != null)
                return mapValue.value;
            final String configuredValue = getConfiguredValue(id);
            if (configuredValue == null) {
                overloadedColor.put(id, null);
                return null;
            }
            final int configuredColor = Color.parseColor(configuredValue);
            overloadedColor.put(id, new MapValue<>(configuredColor));
            return configuredColor;
        }
    }

    static ResourcesMapper mapper;

    final Resources baseResources;

    public ResourcesInterceptor(
            final Resources baseResources) {
        super(
                baseResources.getAssets(),
                baseResources.getDisplayMetrics(),
                baseResources.getConfiguration());
        this.baseResources = baseResources;
    }

    @Override
    public void addLoaders(@NonNull final ResourcesLoader... loaders) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return;
        baseResources.addLoaders(loaders);
    }

    @NonNull
    @Override
    public XmlResourceParser getAnimation(final int id) throws NotFoundException {
        return baseResources.getAnimation(id);
    }

    public static int getAttributeSetSourceResId(@Nullable final AttributeSet set) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return 0;
        return Resources.getAttributeSetSourceResId(set);
    }

    @Override
    public boolean getBoolean(final int id) throws NotFoundException {
        final Boolean bool = mapper.getBoolean(id);
        if (bool != null)
            return bool;
        return baseResources.getBoolean(id);
    }

    @Override
    public int getColor(final int id, @Nullable final Theme theme) throws NotFoundException {
        final Integer color = mapper.getColor(id);
        if (color != null)
            return color;
        return baseResources.getColor(id, theme);
    }

    @Deprecated
    @NonNull
    @Override
    public ColorStateList getColorStateList(final int id) throws NotFoundException {
        return baseResources.getColorStateList(id);
    }

    @NonNull
    @Override
    public ColorStateList getColorStateList(final int id, @Nullable final Theme theme) throws NotFoundException {
        return baseResources.getColorStateList(id, theme);
    }

    @Override
    public Configuration getConfiguration() {
        return baseResources.getConfiguration();
    }

    @Override
    public float getDimension(final int id) throws NotFoundException {
        return baseResources.getDimension(id);
    }

    @Override
    public int getDimensionPixelOffset(final int id) throws NotFoundException {
        return baseResources.getDimensionPixelOffset(id);
    }

    @Override
    public int getDimensionPixelSize(final int id) throws NotFoundException {
        return baseResources.getDimensionPixelSize(id);
    }

    @Override
    public DisplayMetrics getDisplayMetrics() {
        return baseResources.getDisplayMetrics();
    }

    @Deprecated
    @Override
    public Drawable getDrawable(final int id) throws NotFoundException {
        return baseResources.getDrawable(id);
    }

    @Override
    public Drawable getDrawable(final int id, @Nullable final Theme theme) throws NotFoundException {
        return baseResources.getDrawable(id, theme);
    }

    @Deprecated
    @Nullable
    @Override
    public Drawable getDrawableForDensity(final int id, final int density) throws NotFoundException {
        return baseResources.getDrawableForDensity(id, density);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(final int id, final int density, @Nullable final Theme theme) {
        return baseResources.getDrawableForDensity(id, density, theme);
    }

    @Override
    public float getFloat(final int id) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return 0;
        return baseResources.getFloat(id);
    }

    @NonNull
    @Override
    public Typeface getFont(final int id) throws NotFoundException {
        return baseResources.getFont(id);
    }

    @Override
    public float getFraction(final int id, final int base, final int pbase) {
        return baseResources.getFraction(id, base, pbase);
    }

    @Override
    public int getIdentifier(final String name, final String defType, final String defPackage) {
        return baseResources.getIdentifier(name, defType, defPackage);
    }

    @NonNull
    @Override
    public int[] getIntArray(final int id) throws NotFoundException {
        return baseResources.getIntArray(id);
    }

    @Override
    public int getInteger(final int id) throws NotFoundException {
        final Integer integer = mapper.getInteger(id);
        if (integer != null)
            return integer;
        return baseResources.getInteger(id);
    }

    @NonNull
    @Override
    public XmlResourceParser getLayout(final int id) throws NotFoundException {
        return baseResources.getLayout(id);
    }

    @Deprecated
    @Override
    public Movie getMovie(final int id) throws NotFoundException {
        return baseResources.getMovie(id);
    }

    @NonNull
    @Override
    public String getQuantityString(final int id, final int quantity) throws NotFoundException {
        return baseResources.getQuantityString(id, quantity);
    }

    @NonNull
    @Override
    public String getQuantityString(final int id, final int quantity, final Object... formatArgs) throws NotFoundException {
        return baseResources.getQuantityString(id, quantity, formatArgs);
    }

    @NonNull
    @Override
    public CharSequence getQuantityText(final int id, final int quantity) throws NotFoundException {
        return baseResources.getQuantityText(id, quantity);
    }

    @Override
    public String getResourceEntryName(final int resid) throws NotFoundException {
        return baseResources.getResourceEntryName(resid);
    }

    @Override
    public String getResourceName(final int resid) throws NotFoundException {
        return baseResources.getResourceName(resid);
    }

    @Override
    public String getResourcePackageName(final int resid) throws NotFoundException {
        return baseResources.getResourcePackageName(resid);
    }

    @Override
    public String getResourceTypeName(final int resid) throws NotFoundException {
        return baseResources.getResourceTypeName(resid);
    }

    @NonNull
    @Override
    public String getString(final int id) throws NotFoundException {
        final String string = mapper.getString(id);
        if (string != null)
            return string;
        return baseResources.getString(id);
    }

    @NonNull
    @Override
    public String[] getStringArray(final int id) throws NotFoundException {
        return baseResources.getStringArray(id);
    }

    public static Resources getSystem() {
        return Resources.getSystem();
    }

    @NonNull
    @Override
    public CharSequence getText(final int id) throws NotFoundException {
        return baseResources.getText(id);
    }

    @Override
    public CharSequence getText(final int id, final CharSequence def) {
        return baseResources.getText(id, def);
    }

    @NonNull
    @Override
    public CharSequence[] getTextArray(final int id) throws NotFoundException {
        return baseResources.getTextArray(id);
    }

    @Override
    public void getValue(final int id, final TypedValue outValue, final boolean resolveRefs) throws NotFoundException {
        baseResources.getValue(id, outValue, resolveRefs);
    }

    @Override
    public void getValue(final String name, final TypedValue outValue, final boolean resolveRefs) throws NotFoundException {
        baseResources.getValue(name, outValue, resolveRefs);
    }

    @Override
    public void getValueForDensity(final int id, final int density, final TypedValue outValue, final boolean resolveRefs) throws NotFoundException {
        baseResources.getValueForDensity(id, density, outValue, resolveRefs);
    }

    @NonNull
    @Override
    public XmlResourceParser getXml(final int id) throws NotFoundException {
        return baseResources.getXml(id);
    }

    @Override
    public TypedArray obtainAttributes(final AttributeSet set, final int[] attrs) {
        return baseResources.obtainAttributes(set, attrs);
    }

    @NonNull
    @Override
    public TypedArray obtainTypedArray(final int id) throws NotFoundException {
        return baseResources.obtainTypedArray(id);
    }

    @NonNull
    @Override
    public InputStream openRawResource(final int id) throws NotFoundException {
        return baseResources.openRawResource(id);
    }

    @NonNull
    @Override
    public InputStream openRawResource(final int id, final TypedValue value) throws NotFoundException {
        return baseResources.openRawResource(id, value);
    }

    @Override
    public AssetFileDescriptor openRawResourceFd(final int id) throws NotFoundException {
        return baseResources.openRawResourceFd(id);
    }

    @Override
    public void parseBundleExtra(final String tagName, final AttributeSet attrs, final Bundle outBundle) throws XmlPullParserException {
        baseResources.parseBundleExtra(tagName, attrs, outBundle);
    }

    @Override
    public void parseBundleExtras(final XmlResourceParser parser, final Bundle outBundle) throws IOException, XmlPullParserException {
        baseResources.parseBundleExtras(parser, outBundle);
    }

    public static void registerResourcePaths(@NonNull final String uniqueId, @NonNull final ApplicationInfo appInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM)
            return;
        Resources.registerResourcePaths(uniqueId, appInfo);
    }

    @Override
    public void removeLoaders(@NonNull final ResourcesLoader... loaders) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return;
        baseResources.removeLoaders(loaders);
    }

    @Deprecated
    @Override
    public void updateConfiguration(final Configuration config, final DisplayMetrics metrics) {
        baseResources.updateConfiguration(config, metrics);
    }

    public static Properties getAllResourcesAsProperties() {
        try {
            return mapper.getAllResourcesAsProperties();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private interface ResourceAsStringResolver {
        String getAsString(Resources resources, int id);
    }

    private static Properties getAllResourcesAsProperties(final Resources resources) throws IllegalAccessException {
        final Properties properties = new Properties() {
            @Serial
            private static final long serialVersionUID = 1458631718343670066L;

            final List<String> keys = new ArrayList<>();

            @Override
            public synchronized Object put(final Object key, final Object value) {
                keys.add(key.toString());
                return super.put(key, value);
            }

            @Override
            public Set<Entry<Object, Object>> entrySet() {
                final Set<Entry<Object, Object>> set = new LinkedHashSet<>();
                for (final String key : keys) {
                    final Object value = get(key);
                    set.add(new Entry<>() {
                        @Override
                        public Object getKey() {
                            return key;
                        }

                        @Override
                        public Object getValue() {
                            return value;
                        }

                        @Override
                        public Object setValue(final Object value) {
                            return null;
                        }
                    });
                }
                return Collections.synchronizedSet(set);
            }
        };
        addAllResourcesToProperties(properties, resources, R.string.class, (r, id) -> r.getString(id));
        addAllResourcesToProperties(properties, resources, R.integer.class, (r, id) -> Integer.toString(r.getInteger(id)));
        addAllResourcesToProperties(properties, resources, R.bool.class, (r, id) -> Boolean.toString(r.getBoolean(id)));
        addAllResourcesToProperties(properties, resources, R.color.class, (r, id) -> String.format("#%08x", r.getColor(id)));
        return properties;
    }

    private static void addAllResourcesToProperties(
            final Properties properties, final Resources resources,
            final Class<?> resourcesClass, final ResourceAsStringResolver resolver)
            throws IllegalAccessException {
        final int[] stringIds = getAllIdsForResourcesClass(resourcesClass);
        for (final int id : stringIds) {
            final String name = resources.getResourceEntryName(id);
            final String value = resolver.getAsString(resources, id);
            properties.put(name, value);
        }
    }

    private static int[] getAllIdsForResourcesClass(final Class<?> resourcesClass) throws IllegalAccessException {
        final Field[] declaredFields = resourcesClass.getDeclaredFields();
        final int size = declaredFields.length;
        final int[] ids = new int[size];
        for (int index = 0; index < declaredFields.length; index++) {
            final Field field = declaredFields[index];
            ids[index] = field.getInt(null);
        }
        return ids;
    }
}
