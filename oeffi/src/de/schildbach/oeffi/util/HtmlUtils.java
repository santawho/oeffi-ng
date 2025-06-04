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

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlUtils {
    public static String makeLinksClickableInHtml(String text) {
        // find all URLs with http
        text = makeLinksClickableInHtml(text,
                "(http[s]?)://(www\\.)?([\\S&&[^.@<]]+)(\\.[\\S&&[^@<]]+)",
                (s) -> "<a href=\"" + s + "\">" + s + "&#128279;</a>");

        // find all URLs without http
        text = makeLinksClickableInHtml(text,
                "(?<!http[s]?://)(www\\.+)([\\S&&[^.@<]]+)(\\.[\\S&&[^@<]]+)",
                (s) -> "<a href=\"https://" + s + "\">" + s + "&#128279;</a>");

        // find all phone numbers
        text = makeLinksClickableInHtml(text,
                "\\+?[0-9(][-0-9() ]{6,}[0-9)]",
                (s) -> "<a href=\"tel:" + s + "\">&#9742;&nbsp;" + s + "</a>");

        return text;
    }

    public static String makeLinksClickableInHtml(final String text, final String linkStyleRegex, final Function<String, String> makeLinkClickable) {
        // make all links not within <a href"...">...</a> clickable
        final Pattern pattern = Pattern.compile("<a href=\".*?\">.*?</a>");
        final Matcher matcher = pattern.matcher(text);
        final StringBuilder builder = new StringBuilder();
        int pos = 0;
        while (matcher.find(pos)) {
            final int start = matcher.start();
            final int end = matcher.end();
            builder.append(makeLinksClickableInString(text.substring(pos, start), linkStyleRegex, makeLinkClickable));
            builder.append(text.substring(start, end));
            pos = end;
        }
        builder.append(makeLinksClickableInString(text.substring(pos), linkStyleRegex, makeLinkClickable));
        return builder.toString();
    }

    public static String makeLinksClickableInString(final String text, final String linkStyleRegex, final Function<String, String> makeLinkClickable) {
        final Pattern pattern = Pattern.compile(linkStyleRegex);
        final Matcher matcher = pattern.matcher(text);
        final StringBuilder builder = new StringBuilder();
        int pos = 0;
        while (matcher.find(pos)) {
            final int start = matcher.start();
            final int end = matcher.end();
            builder.append(text.substring(pos, start));
            builder.append(makeLinkClickable.apply(text.substring(start, end)));
            pos = end;
        }
        builder.append(text.substring(pos));
        return builder.toString();
    }
}
