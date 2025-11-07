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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class KeyWordMatcher {
    private static final boolean USE_OR_MATCHING = false; // false uses "AND" matching

    public static class SearchableItem {
        private SearchableItem() {}

        private final List<String> keyWords = new ArrayList<>();

        public void addIndexableText(final String text) {
            addKeyWordsFromText(text, keyWords);
        }

        public void addIndexableString(final String string) {
            addStringAsKeyword(string, keyWords);
        }
    }

    public static class Query {
        public Match matchTo(final SearchableItem item) {
            return match(item, this);
        }

        private Query(final String query) {
            this.queryStrings = new ArrayList<>();
            addKeyWordsFromText(query, queryStrings);
        }

        private final List<String> queryStrings;
    }

    public static class Match {
        public final boolean matches;
        public final int score;

        private Match(final boolean matches, final int score) {
            this.matches = matches;
            this.score = score;
        }
    }

    public static SearchableItem createSearchableItem() {
        return new SearchableItem();
    }

    public static Query createQuery(final String filter) {
        return new Query(filter);
    }

    private static final Pattern patternNoWordChars = Pattern.compile("[^\\sa-z0-9]+");
    private static final Pattern patternSpaces = Pattern.compile("\\s+");

    private static void addKeyWordsFromText(final String text, final List<String> keyWords) {
        if (text == null || text.isEmpty())
            return;
        final String lowerCaseAscii = text
                .toLowerCase()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        final String wordsBySpaces = patternNoWordChars.matcher(lowerCaseAscii).replaceAll(" ");
        addWordArrayToList(patternSpaces.split(wordsBySpaces), keyWords);
    }

    private static void addStringAsKeyword(final String string, final List<String> keyWords) {
        if (string == null || string.isEmpty())
            return;
        keyWords.add(string.toLowerCase());
    }

    private static void addWordArrayToList(final String[] words, final List<String> keyWords) {
        for (final String word : words)
            keyWords.add(word);
    }

    private static Match match(final SearchableItem item, final Query query) {
        final List<String> queryStrings = query.queryStrings;
        if (queryStrings.isEmpty())
            return new Match(true, 1);

        boolean matches;
        int score = 0;

        if (USE_OR_MATCHING) {
            matches = false;
            for (final String itemKeyWord : item.keyWords) {
                final int itemKeyWordLength = itemKeyWord.length();
                boolean itemMatches = false;
                for (final String queryString : queryStrings) {
                    final int queryStringLength = queryString.length();
                    if (itemKeyWord.contains(queryString)) {
                        itemMatches = true;
                        score += queryStringLength;
                        if (queryStringLength == itemKeyWordLength)
                            score += 10;
                    }
                }
                matches |= itemMatches;
            }
        } else {
            matches = true;
            for (final String queryString : queryStrings) {
                final int queryStringLength = queryString.length();
                boolean queryMatches = false;
                for (final String itemKeyWord : item.keyWords) {
                    final int itemKeyWordLength = itemKeyWord.length();
                    if (itemKeyWord.contains(queryString)) {
                        queryMatches = true;
                        score += queryStringLength;
                        if (queryStringLength == itemKeyWordLength)
                            score += 10;
                    }
                }
                matches &= queryMatches;
            }
        }

        return new Match(matches, score);
    }
}
