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

package de.schildbach.oeffi;

import okhttp3.HttpUrl;

public class URLs {
    private static HttpUrl oeffiBaseUrl;
    private static HttpUrl plansBaseUrl;
    private static HttpUrl messagesBaseUrl;

    public static HttpUrl getOeffiBaseUrl() {
        if (oeffiBaseUrl == null) {
            final String urlString = Application.getInstance().getString(R.string.oeffi_base_default_url);
            if (!urlString.isEmpty())
                oeffiBaseUrl = HttpUrl.parse(urlString);
            else
                oeffiBaseUrl = Constants.OEFFI_BASE_DEFAULT_URL;
        }
        return oeffiBaseUrl;
    }

    public static HttpUrl getPlansBaseUrl() {
        if (plansBaseUrl == null) {
            final String urlString = Application.getInstance().getString(R.string.plans_base_default_url);
            if (!urlString.isEmpty())
                plansBaseUrl = HttpUrl.parse(urlString);
            else
                plansBaseUrl = getOeffiBaseUrl().newBuilder().addPathSegment(Constants.PLANS_PATH).build();
        }
        return plansBaseUrl;
    }

    public static HttpUrl getMessagesBaseUrl() {
        if (messagesBaseUrl == null) {
            final String urlString = Application.getInstance().getString(R.string.messages_base_default_url);
            if (!urlString.isEmpty())
                messagesBaseUrl = HttpUrl.parse(urlString);
            else
                messagesBaseUrl = getOeffiBaseUrl().newBuilder().addPathSegment(Constants.MESSAGES_PATH).build();
        }
        return messagesBaseUrl;
    }
}
