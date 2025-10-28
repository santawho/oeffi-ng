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

import android.graphics.Color;
import android.text.format.DateUtils;
import androidx.activity.SystemBarStyle;
import okhttp3.HttpUrl;

import java.util.Locale;

public class Constants {
    public static final HttpUrl OEFFI_BASE_DEFAULT_URL = HttpUrl.parse("https://oeffi.schildbach.de/");
    public static final String PLANS_PATH = "plans";
    public static final HttpUrl PLANS_BASE_URL = OEFFI_BASE_DEFAULT_URL.newBuilder().addPathSegment(PLANS_PATH).build();
    public static final String MESSAGES_PATH = "messages";
    public static final HttpUrl MESSAGES_BASE_URL = OEFFI_BASE_DEFAULT_URL.newBuilder().addPathSegment(MESSAGES_PATH).build();
    public static final String PLANS_DIR = "plans";
    public static final String PLAN_INDEX_FILENAME = "plans-index.txt";
    public static final String PLAN_STATIONS_FILENAME = "plans-stations.txt";

    public static final long LOCATION_UPDATE_FREQ_MS = 10 * DateUtils.SECOND_IN_MILLIS;
    public static final int LOCATION_UPDATE_DISTANCE = 3;
    public static final long LOCATION_FOREGROUND_UPDATE_TIMEOUT_MS = 1 * DateUtils.MINUTE_IN_MILLIS;
    public static final long LOCATION_BACKGROUND_UPDATE_TIMEOUT_MS = 5 * DateUtils.MINUTE_IN_MILLIS;
    public static final long STALE_UPDATE_MS = 2 * DateUtils.MINUTE_IN_MILLIS;
    public static final int MAX_NUMBER_OF_STOPS = 150;
    public static final int MAX_HISTORY_ENTRIES = 50;
    public static final float BEARING_ACCURACY_THRESHOLD = 0.5f;
    public static final double MAP_MIN_ZOOM_LEVEL = 3.0;
    public static final double MAP_MAX_ZOOM_LEVEL = 18.0;
    public static final double INITIAL_MAP_ZOOM_LEVEL_NETWORK = 12.0;
    public static final double INITIAL_MAP_ZOOM_LEVEL = 17.0;
    public static final int MAX_TRIES_ON_IO_PROBLEM = 2;

    public static final Locale DEFAULT_LOCALE = Locale.GERMAN;

    public static final String PREFS_KEY_PREFERRED_TIMEZONE = "common_preferred_timezone";
    public static final String PREFS_KEY_NETWORK_PROVIDER = "network_provider";
    public static final String PREFS_KEY_LAST_NETWORK_PROVIDERS = "last_network_providers";
    public static final String PREFS_KEY_PRODUCT_FILTER = "product_filter";
    public static final String PREFS_KEY_OPTIMIZE_TRIP = "optimize_trip";
    public static final String PREFS_KEY_WALK_SPEED = "walk_speed";
    public static final String PREFS_KEY_MIN_TRANSFER_TIME = "min_transfer_time";
    public static final String PREFS_KEY_ACCESSIBILITY = "accessibility";
    public static final String PREFS_KEY_BICYCLE_TRAVEL = "bicycle_travel";
    public static final String PREFS_KEY_LAST_VERSION = "last_version";
    public static final String PREFS_KEY_SHOW_INFO = "show_hints";
    public static final String PREFS_KEY_USER_INTERFACE_MAINMENU_SHAREAPP_ENABLED = "user_interface_mainmenu_shareapp_enabled";
    public static final String PREFS_KEY_USER_INTERFACE_DEVELOPER_OPTIONS_SHOW_EXTRA_INFOS_ENABLED = "user_interface_developer_options_show_extra_infos_enabled";
    public static final String PREFS_KEY_LAST_INFO_AT = "last_hint_at";
    public static final String PREFS_KEY_NAVIGATION_SOUND_CHANNEL_RIDE = "navigation_sound_channel_ride";
    public static final String PREFS_KEY_NAVIGATION_SOUND_CHANNEL_TRANSFER = "navigation_sound_channel_transfer";
    public static final String PREFS_KEY_NAVIGATION_SOUND_CHANNEL_HEADSET = "navigation_sound_channel_headset";
    public static final String PREFS_KEY_NAVIGATION_USE_SOUND_CHANNEL_HEADSET = "navigation_use_sound_channel_headset";
    public static final String PREFS_KEY_NAVIGATION_SPEECH_OUTPUT = "navigation_speech_output";
    public static final String PREFS_KEY_NAVIGATION_REDUCED_SOUNDS = "navigation_reduced_sounds";
    public static final String PREFS_KEY_NAVIGATION_REFRESH_BEEP = "navigation_refresh_beep";

    public static final char CHAR_THIN_SPACE = '\u2009';
    public static final char CHAR_HAIR_SPACE = '\u200a';
    public static final char CHAR_RIGHTWARDS_ARROW = '\u279d';
    public static final char CHAR_LEFT_RIGHT_ARROW = '\u21c4';
    public static final String DESTINATION_ARROW_PREFIX = Character.toString(Constants.CHAR_RIGHTWARDS_ARROW)
            + Constants.CHAR_THIN_SPACE;
    public static final String DESTINATION_ARROW_INVISIBLE_PREFIX = "     ";

    public static final SystemBarStyle STATUS_BAR_STYLE = SystemBarStyle.dark(Color.TRANSPARENT);
}
