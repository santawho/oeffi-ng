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

package de.schildbach.oeffi.network;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.schildbach.oeffi.Application;
import de.schildbach.pte.provider.locationsearch.LocationSearchApiProvider;
import de.schildbach.pte.provider.locationsearch.LocationSearchProvider;
import de.schildbach.pte.provider.locationsearch.LocationSearchProviderId;
import de.schildbach.pte.provider.locationsearch.NominatimLocationSearchProvider;

public class LocationSearchProviderFactory {
    private static Map<LocationSearchProviderId, LocationSearchProvider> providerCache = new HashMap<>();

    public static synchronized LocationSearchProvider provider(final LocationSearchProviderId locationSearchProviderId) {
        final LocationSearchProvider cachedProvider = providerCache.get(locationSearchProviderId);
        if (cachedProvider != null)
            return cachedProvider;

        final LocationSearchApiProvider provider = forId(locationSearchProviderId);
        provider.setUserAgent(Application.getUserAgent());
        provider.setUserInterfaceLanguage(Locale.getDefault().getLanguage());
        provider.setMessagesAsSimpleHtml(true);
        providerCache.put(locationSearchProviderId, provider);
        return provider;
    }

    private static LocationSearchApiProvider forId(final LocationSearchProviderId locationSearchProviderId) {
        if (locationSearchProviderId.equals(LocationSearchProviderId.Nominamtim))
            return new NominatimLocationSearchProvider();
        else
            throw new IllegalArgumentException(locationSearchProviderId.name());
    }
}
