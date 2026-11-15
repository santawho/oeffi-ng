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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.appcompat.content.res.AppCompatResources;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.pte.dto.Product;

public class ResourceUtil {
    private static final Map<Product, Integer> PRODUCT_LABELS = new HashMap<>();
    private static final Map<Product, Integer> PRODUCT_NAMES = new HashMap<>();
    static {
        PRODUCT_LABELS.put(Product.UNKNOWN, R.string.product_label_unknown);
        PRODUCT_LABELS.put(Product.HIGH_SPEED_TRAIN, R.string.product_label_highspeed_train);
        PRODUCT_LABELS.put(Product.REGIONAL_TRAIN, R.string.product_label_regional_train);
        PRODUCT_LABELS.put(Product.SUBURBAN_TRAIN, R.string.product_label_suburban_train);
        PRODUCT_LABELS.put(Product.SUBWAY, R.string.product_label_subway);
        PRODUCT_LABELS.put(Product.TRAM, R.string.product_label_tram);
        PRODUCT_LABELS.put(Product.BUS, R.string.product_label_bus);
        PRODUCT_LABELS.put(Product.COACH, R.string.product_label_coach);
        PRODUCT_LABELS.put(Product.ON_DEMAND, R.string.product_label_ondemand);
        PRODUCT_LABELS.put(Product.FERRY, R.string.product_label_ferry);
        PRODUCT_LABELS.put(Product.CABLECAR, R.string.product_label_cablecar);
        PRODUCT_LABELS.put(Product.REPLACEMENT_SERVICE, R.string.product_label_replacement);

        PRODUCT_NAMES.put(Product.UNKNOWN, R.string.product_name_unknown);
        PRODUCT_NAMES.put(Product.HIGH_SPEED_TRAIN, R.string.product_name_highspeed_train);
        PRODUCT_NAMES.put(Product.REGIONAL_TRAIN, R.string.product_name_regional_train);
        PRODUCT_NAMES.put(Product.SUBURBAN_TRAIN, R.string.product_name_suburban_train);
        PRODUCT_NAMES.put(Product.SUBWAY, R.string.product_name_subway);
        PRODUCT_NAMES.put(Product.TRAM, R.string.product_name_tram);
        PRODUCT_NAMES.put(Product.BUS, R.string.product_name_bus);
        PRODUCT_NAMES.put(Product.COACH, R.string.product_name_coach);
        PRODUCT_NAMES.put(Product.ON_DEMAND, R.string.product_name_ondemand);
        PRODUCT_NAMES.put(Product.FERRY, R.string.product_name_ferry);
        PRODUCT_NAMES.put(Product.CABLECAR, R.string.product_name_cablecar);
        PRODUCT_NAMES.put(Product.REPLACEMENT_SERVICE, R.string.product_name_replacement);
    }
    public static String getProductLabel(final @Nonnull Product product) {
        final Integer resId = PRODUCT_LABELS.get(product);
        return Application.getInstance().getString(resId != null ? resId : R.string.product_label_unknown);
    }

    public static String getProductName(final @Nonnull Product product) {
        final Integer resId = PRODUCT_NAMES.get(product);
        return Application.getInstance().getString(resId != null ? resId : R.string.product_name_unknown);
    }

    public static String[] getStringArray(final int resId, final Object... formatArgs) {
        final Resources resources = Application.getInstance().getResources();
        final Locale locale = resources.getConfiguration().getLocales().get(0);
        final String[] strings = resources.getStringArray(resId);
        for (int pos = 0; pos < strings.length; pos++) {
            final String string = strings[pos];
            strings[pos] = String.format(locale, string, formatArgs);
        }
        return strings;
    }

    public static Context getLanguageContext(final Context context, final String languageCode) {
        if (languageCode == null)
            return context;
        final Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(languageCode));
        final Context newContext = context.createConfigurationContext(configuration);
        final String newLanguageCode = newContext.getString(R.string.locale);
        return languageCode.equals(newLanguageCode) ? newContext : context;
    }

    public static Drawable getDrawableInTextColor(final Context context, final int iconResId) {
        return getColoredDrawable(context, iconResId, context.getColor(R.color.fg_significant));
    }

    public static Drawable getColoredDrawable(final Context context, final int iconResId, final int color) {
        final Drawable drawable = AppCompatResources.getDrawable(context, iconResId);
        if (drawable == null)
            return null;
        final Drawable mutable = drawable.mutate();
        mutable.setTint(color);
        return mutable;
    }
}
