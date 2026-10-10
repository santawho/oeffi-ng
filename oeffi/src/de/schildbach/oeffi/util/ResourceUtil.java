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

import java.util.Locale;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.pte.dto.Product;

public class ResourceUtil {
    public static String getProductName(final Product product) {
        final Application application = Application.getInstance();
        final int productResId = application.getResources().getIdentifier(
                "product_" + Character.toLowerCase((product == null ? Product.UNKNOWN : product).code),
                "string", application.getPackageName());
        if (productResId == 0)
            return null;
        return application.getString(productResId);
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
}
