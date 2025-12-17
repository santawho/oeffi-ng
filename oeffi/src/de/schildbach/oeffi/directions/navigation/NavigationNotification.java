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

package de.schildbach.oeffi.directions.navigation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.util.ClockUtils;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.ResourceUri;
import de.schildbach.oeffi.util.TimeZoneSelector;
import de.schildbach.pte.DbProvider;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Trip;

public class NavigationNotification {
    public static final String PREFS_KEY_NAVIGATION_SOUND_CHANNEL_RIDE = "navigation_sound_channel_ride";
    public static final String PREFS_KEY_NAVIGATION_SOUND_CHANNEL_TRANSFER = "navigation_sound_channel_transfer";
    public static final String PREFS_KEY_NAVIGATION_SOUND_CHANNEL_HEADSET = "navigation_sound_channel_headset";
    public static final String PREFS_KEY_NAVIGATION_USE_SOUND_CHANNEL_HEADSET = "navigation_use_sound_channel_headset";
    public static final String PREFS_KEY_NAVIGATION_SPEECH_OUTPUT = "navigation_speech_output";
    public static final String PREFS_KEY_NAVIGATION_REDUCED_SOUNDS = "navigation_reduced_sounds";
    public static final String PREFS_KEY_NAVIGATION_REFRESH_BEEP = "navigation_refresh_beep";
    public static final String PREFS_KEY_NOTIFICATIONS_ENABLED = "navigation_notifications_enabled";
    public static final String PREFS_KEY_NOTIFICATIONS_CHANGES_SHOW_WHEN = "navigation_notification_changes_show_when";
    public static final String PREFS_KEY_NOTIFICATIONS_CHANGES_REMOVE_WHEN = "navigation_notification_changes_remove_when";
    public static final String PREFS_KEY_NOTIFICATIONS_DIRECTIONS_SHOW_WHEN = "navigation_notification_directions_show_when";
    public static final String PREFS_KEY_NOTIFICATIONS_DIRECTIONS_REMOVE_WHEN = "navigation_notification_directions_remove_when";
    public static final String PREFS_KEY_NOTIFICATIONS_REMIND_2_MINUTES = "navigation_notifications_remind_2_minutes";
    public static final String PREFS_KEY_NOTIFICATIONS_REMIND_6_MINUTES = "navigation_notifications_remind_6_minutes";
    public static final String PREFS_KEY_NOTIFICATIONS_REMIND_2_MINUTES_PREVIEW = "navigation_notifications_remind_2_minutes_preview";
    public static final String PREFS_KEY_NOTIFICATIONS_REMIND_6_MINUTES_PREVIEW = "navigation_notifications_remind_6_minutes_preview";
    public static final String NOTIFICATION_GROUP_EVENTS = "events";
    public static final String NOTIFICATION_GROUP_GUIDES = "guides";

    private static final int SOUND_ALARM = R.raw.nav_alarm;
    private static final int SOUND_REMIND_VIA_NOTIFICATION = -1;
    private static final int SOUND_REMIND_NORMAL = R.raw.nav_remind_down;
    private static final int SOUND_REMIND_IMPORTANT = R.raw.nav_remind_downup;
    private static final int SOUND_REMIND_NEXTLEG = R.raw.nav_remind_up;
    private static String notificationTitleForChanges;
    private static String notificationTitleForDirections;

    private static int getActualSound(final int soundId) {
        int newId = soundId;
        if (prefs.getBoolean(PREFS_KEY_NAVIGATION_REDUCED_SOUNDS, false)) {
            if (soundId == SOUND_ALARM)
                newId = R.raw.nav_lowvolume_alarm;
            else if (soundId == SOUND_REMIND_NORMAL)
                newId = R.raw.nav_lowvolume_remind_down;
            else if (soundId == SOUND_REMIND_IMPORTANT)
                newId = R.raw.nav_lowvolume_remind_downup;
            else if (soundId == SOUND_REMIND_NEXTLEG)
                newId = R.raw.nav_lowvolume_remind_up;
        }
        return newId;
    }

    private static final String CHANNEL_ID_GUIDE = "navigation";
    private static final String CHANNEL_ID_CHANGES = "navigation-changes";
    private static final String CHANNEL_ID_DIRECTIONS = "navigation-directions";
    private static final String TAG_PREFIX_COMMON = NavigationNotification.class.getName() + ":";
    private static final String TAG_PREFIX_GUIDE = TAG_PREFIX_COMMON + "guide:";
    private static final String TAG_PREFIX_CHANGES = TAG_PREFIX_COMMON + "changes:";
    private static final String TAG_PREFIX_DIRECTIONS = TAG_PREFIX_COMMON + "directions:";

    private static final long KEEP_NOTIFICATION_FOR_MINUTES = 30;
    private static final long REMINDER_FIRST_MS = 6 * 60 * 1000;
    private static final long REMINDER_SECOND_MS = 2 * 60 * 1000;
    private static final int ACTION_REFRESH = 1;
    private static final int ACTION_DELETE = 2;
    private static final String INTENT_EXTRA_ACTION = NavigationNotification.class.getName() + ".action";
    private static final String EXTRA_INTENTDATA = NavigationNotification.class.getName() + ".intentdata";
    private static final String EXTRA_LASTNOTIFIED = NavigationNotification.class.getName() + ".lastnotified";
    private static final String EXTRA_CONFIGURATION = NavigationNotification.class.getName() + ".config";
    private static final String EXTRA_DATA = NavigationNotification.class.getName() + ".data";
    public static final String ACTION_UPDATE_TRIGGER = NavigationNotification.class.getName() + ".updatetrigger";
    private static final long[] VIBRATION_PATTERN_REMIND = { 0, 1000, 500, 1500 };
    private static final long[] VIBRATION_PATTERN_ALARM = { 0, 1000, 500, 1500, 1000, 1000, 500, 1500 };

    private static final Logger log = LoggerFactory.getLogger(NavigationNotification.class);

    private static boolean notificationChannelsCreated;

    private static SharedPreferences prefs;

    public static void createNotificationChannels(final Context context) {
        prefs = Application.getInstance().getSharedPreferences();

        TravelAlarmManager.createNotificationChannel(context);
        if (notificationChannelsCreated)
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createInstructionsChannel(context);
            createChangesChannel(context);
            createDirectionsChannel(context);
        }

        notificationTitleForChanges = context.getString(R.string.navigation_event_notify_changes_title);
        notificationTitleForDirections = context.getString(R.string.navigation_event_notify_directions_title);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON); // screen being turned on
        intentFilter.addAction(Intent.ACTION_USER_PRESENT); // screen beig unlocked
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                onDeviceWakingUp(context);
            }
        }, intentFilter);

        notificationChannelsCreated = true;
    }

    private static void createInstructionsChannel(final Context context) {
        final CharSequence name = context.getString(R.string.navigation_notification_channel_name);
        final String description = context.getString(R.string.navigation_notification_channel_description);
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID_GUIDE, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        // channel.enableVibration(true);
        channel.setVibrationPattern(VIBRATION_PATTERN_REMIND);
        if (SOUND_REMIND_VIA_NOTIFICATION >= 0) {
            final int usage = getAudioUsageForSound(SOUND_REMIND_VIA_NOTIFICATION, false);
            channel.setSound(ResourceUri.fromResource(context, SOUND_REMIND_VIA_NOTIFICATION),
                    new AudioAttributes.Builder().setUsage(usage).build());
        }
        getNotificationManager(context).createNotificationChannel(channel);
    }

    private static void createChangesChannel(final Context context) {
        final CharSequence name = context.getString(R.string.navigation_notification_channel_changes_name);
        final String description = context.getString(R.string.navigation_notification_channel_changes_description);
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID_CHANGES, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        // channel.setGroup(EVENT_NOTIFICATION_GROUP);
        getNotificationManager(context).createNotificationChannel(channel);
    }

    private static void createDirectionsChannel(final Context context) {
        final CharSequence name = context.getString(R.string.navigation_notification_channel_directions_name);
        final String description = context.getString(R.string.navigation_notification_channel_directions_description);
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID_DIRECTIONS, name, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        // channel.setGroup(EVENT_NOTIFICATION_GROUP);
        getNotificationManager(context).createNotificationChannel(channel);
    }

    private static NotificationManagerCompat getNotificationManager(final Context context) {
        // return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return NotificationManagerCompat.from(context);
    }

    private static int getAudioStreamForSound(final int soundId) {
        return AudioManager.STREAM_NOTIFICATION;
    }

    private static int getAudioUsageForSound(final int soundId, final boolean onRide) {
        int usage = AudioAttributes.USAGE_NOTIFICATION_EVENT;
        String prefKey = null;
        final boolean useHeadsetChannel;
        if (NavigationNotification.prefs.getBoolean(PREFS_KEY_NAVIGATION_USE_SOUND_CHANNEL_HEADSET, true)) {
            useHeadsetChannel = NotificationSoundManager.getInstance().isHeadsetConnected();
        } else {
            useHeadsetChannel = false;
        }
        if (useHeadsetChannel) {
            prefKey = PREFS_KEY_NAVIGATION_SOUND_CHANNEL_HEADSET;
        } else if (soundId == SOUND_REMIND_NORMAL
            || soundId == SOUND_REMIND_IMPORTANT
            || soundId == SOUND_ALARM) {
            prefKey = onRide
                    ? PREFS_KEY_NAVIGATION_SOUND_CHANNEL_RIDE
                    : PREFS_KEY_NAVIGATION_SOUND_CHANNEL_TRANSFER;
        } else if (soundId == SOUND_REMIND_NEXTLEG) {
            prefKey = PREFS_KEY_NAVIGATION_SOUND_CHANNEL_RIDE;
        }
        if (prefKey != null) {
            switch (prefKey) {
                case PREFS_KEY_NAVIGATION_SOUND_CHANNEL_HEADSET:
                    usage = AudioAttributes.USAGE_MEDIA;
                    break;
                case PREFS_KEY_NAVIGATION_SOUND_CHANNEL_RIDE:
                    usage = AudioAttributes.USAGE_NOTIFICATION_EVENT;
                    break;
                case PREFS_KEY_NAVIGATION_SOUND_CHANNEL_TRANSFER:
                    usage = AudioAttributes.USAGE_ALARM;
                    break;
            }
            final String usageName = NavigationNotification.prefs.getString(prefKey, null);
            if (usageName != null) {
                try {
                    usage = AudioAttributes.class.getField(usageName).getInt(null);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    log.error("bad sound usage: {}", usageName, e);
                }
            }
        }
        return usage;
    }

    private static final String[] REQUIRED_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? new String[] {
            Manifest.permission.POST_NOTIFICATIONS,
//            Manifest.permission.SCHEDULE_EXACT_ALARM,
//            Manifest.permission.USE_EXACT_ALARM,
    } : new String[]{};

    public static boolean requestPermissions(final Activity activity, final int requestCode) {
        boolean all = true;
        for (final String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED)
                all = false;
        }
        if (all)
            return true;
        ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, requestCode);
        return false;
    }

    public static long refreshAllGuides(final Context context) {
        final List<StatusBarNotification> activeNotifications =
                getNotificationManager(context).getActiveNotifications();
        long minRefreshAt = Long.MAX_VALUE;
        for (final StatusBarNotification statusBarNotification : activeNotifications) {
            if (!statusBarNotification.getTag().startsWith(TAG_PREFIX_GUIDE))
                continue;
            log.info("refresh notification with tag={} posttime={}, id={}, key={}",
                    statusBarNotification.getTag(),
                    statusBarNotification.getPostTime(),
                    statusBarNotification.getId(),
                    statusBarNotification.getKey());
            final Notification notification = statusBarNotification.getNotification();
            final Bundle extras = notification.extras;
            if (extras == null)
                continue;
            final TripDetailsActivity.IntentData intentData =
                    (TripDetailsActivity.IntentData) Objects.deserialize(extras.getByteArray(EXTRA_INTENTDATA));
            final TripRenderer.NotificationData notificationData =
                    (TripRenderer.NotificationData) Objects.deserialize(extras.getByteArray(EXTRA_LASTNOTIFIED));
            final Configuration configuration =
                    (Configuration) Objects.deserialize(extras.getByteArray(EXTRA_CONFIGURATION));
            final NavigationNotification navigationNotification = new NavigationNotification(
                    intentData, configuration, notificationData);
            final ExtraData extraData = (ExtraData) Objects.deserialize(extras.getByteArray(EXTRA_DATA));
            final long refreshAt = navigationNotification.refresh(extraData != null && extraData.refreshAllLegs);
            if (refreshAt > 0 && refreshAt < minRefreshAt)
                minRefreshAt = refreshAt;
        }
        return minRefreshAt;
    }

    public static void removeAllGuides(final Context context) {
        final NotificationManagerCompat notificationManager = getNotificationManager(context);
        final List<StatusBarNotification> activeNotifications =
                notificationManager.getActiveNotifications();
        for (final StatusBarNotification statusBarNotification : activeNotifications) {
            final String tag = statusBarNotification.getTag();
            if (!tag.startsWith(TAG_PREFIX_GUIDE))
                continue;
            final int id = statusBarNotification.getId();
            notificationManager.cancel(tag, id);
        }
    }

    public static void remove(final OeffiActivity context, final Intent intent) {
        NavigationAlarmManager.runOnHandlerThread(() -> {
            new NavigationNotification(intent).remove();
        });
    }

    private static final String S_ISSUE_NEVER = "never";
    private static final String S_ISSUE_WHEN_LOCKED = "when-locked";
    private static final String S_ISSUE_WHEN_OFF = "when-off";
    private static final String S_ISSUE_ALWAYS = "always";
    private static final int I_ISSUE_NEVER = 0;
    private static final int I_ISSUE_ALWAYS = -1;
    private static final int I_ISSUE_WHEN_LOCKED = -2;
    private static final int I_ISSUE_WHEN_OFF = -3;

    private static int getIssueConfig(final String prefKey) {
        final String value = prefs.getString(prefKey, S_ISSUE_NEVER);
        if (S_ISSUE_WHEN_LOCKED.equals(value))
            return I_ISSUE_WHEN_LOCKED;
        if (S_ISSUE_WHEN_OFF.equals(value))
            return I_ISSUE_WHEN_OFF;
        if (S_ISSUE_ALWAYS.equals(value))
            return I_ISSUE_ALWAYS;
        return I_ISSUE_NEVER;
    }

    private static final String S_REMOVE_NEVER = "never";
    private static final String S_REMOVE_WHEN_UNLOCKED = "when-unlocked";
    private static final String S_REMOVE_WHEN_ON = "when-on";
    private static final int I_REMOVE_NEVER = 0;
    private static final int I_REMOVE_WHEN_UNLOCKED = -1;
    private static final int I_REMOVE_WHEN_ON = -2;

    private static int getRemoveConfig(final String prefKey) {
        if (prefs == null)
            return I_REMOVE_NEVER;
        final String value = prefs.getString(prefKey, S_REMOVE_NEVER);
        if (Character.isDigit(value.charAt(0)))
            return Integer.parseInt(value) * 1000;
        if (S_REMOVE_WHEN_UNLOCKED.equals(value))
            return I_REMOVE_WHEN_UNLOCKED;
        if (S_REMOVE_WHEN_ON.equals(value))
            return I_REMOVE_WHEN_ON;
        return I_REMOVE_NEVER;
    }

    private static void onDeviceWakingUp(final Context context) {
        // the screen has just either been turned on or unlocked
        final boolean isScreenOn = ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isInteractive();
        final boolean isUnlocked = !((KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();
        log.info("device waking up, the screen now is {} {}", isScreenOn ? "on" : "off", isUnlocked ? "unlocked" : "locked");

        if (prefs.getBoolean(PREFS_KEY_NOTIFICATIONS_ENABLED, false))
            cleanupEventNotifications(context, isScreenOn, isUnlocked);
    }

    private static void cleanupEventNotifications(
            final Context context,
            final boolean isScreenOn,
            final boolean isUnlocked) {
        final int removeChangesWhen = getRemoveConfig(PREFS_KEY_NOTIFICATIONS_CHANGES_REMOVE_WHEN);
        final int removeDirectionsWhen = getRemoveConfig(PREFS_KEY_NOTIFICATIONS_DIRECTIONS_REMOVE_WHEN);

        final long now = System.currentTimeMillis();
        final NotificationManagerCompat notificationManager = getNotificationManager(context);
        final List<StatusBarNotification> activeNotifications =
                notificationManager.getActiveNotifications();
        for (final StatusBarNotification statusBarNotification : activeNotifications) {
            final String tag = statusBarNotification.getTag();
            final int removeWhen;

            if (tag.startsWith(TAG_PREFIX_CHANGES))
                removeWhen = removeChangesWhen;
            else if (tag.startsWith(TAG_PREFIX_DIRECTIONS))
                removeWhen = removeDirectionsWhen;
            else
                continue;

            if (removeWhen > 0) {
                if (now - statusBarNotification.getPostTime() < removeWhen)
                    continue;
            } else if (removeWhen == I_REMOVE_WHEN_UNLOCKED) {
                if (!isUnlocked)
                    continue;
            } else if (removeWhen == I_REMOVE_WHEN_ON) {
                if (!isScreenOn)
                    continue;
            } else {
                continue;
            }
            final int id = statusBarNotification.getId();
            notificationManager.cancel(tag, id);
        }
    }

    private static class EventNotificationData {
        final boolean isChangeEvent;
        final String title;
        final String message;

        EventNotificationData(
                final boolean isChangeEvent,
                final String title,
                final String message) {
            this.isChangeEvent = isChangeEvent;
            this.title = title;
            this.message = message;
        }

        static EventNotificationData changeEvent(final String message) {
            return new EventNotificationData(true, notificationTitleForChanges, message);
        }

        static EventNotificationData directionsEvent(final String message) {
            return new EventNotificationData(false, notificationTitleForDirections, message);
        }
    }

    private static int uniqueCounter;

    private void postEventNotifications(final List<EventNotificationData> dataList) {
        final boolean isScreenOff = !((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isInteractive();
        final boolean isLocked = ((KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();

        for (final EventNotificationData data : dataList) {
            final boolean isChangeEvent = data.isChangeEvent;
            final int showWhen = getIssueConfig(isChangeEvent
                    ? PREFS_KEY_NOTIFICATIONS_CHANGES_SHOW_WHEN
                    : PREFS_KEY_NOTIFICATIONS_DIRECTIONS_SHOW_WHEN);
            boolean doShow;
            if (showWhen == I_ISSUE_ALWAYS)
                doShow = true;
            else if (showWhen == I_ISSUE_WHEN_LOCKED)
                doShow = isLocked;
            else if (showWhen == I_ISSUE_WHEN_OFF)
                doShow = isScreenOff;
            else
                doShow = false;

            final int removeWhen = getRemoveConfig(isChangeEvent
                    ? PREFS_KEY_NOTIFICATIONS_CHANGES_REMOVE_WHEN
                    : PREFS_KEY_NOTIFICATIONS_DIRECTIONS_REMOVE_WHEN);

            if (removeWhen == I_REMOVE_WHEN_ON) {
                if (!isScreenOff)
                    doShow = false;
            } else if (removeWhen == I_REMOVE_WHEN_UNLOCKED) {
                if (!isLocked)
                    doShow = false;
            }

            log.info("{}posting a {} notification, the screen now is {} {}",
                    doShow ? "" : "not ",
                    isChangeEvent ? "change" : "direction",
                    isScreenOff ? "off" : "on",
                    isLocked ? "locked" : "unlocked");

            if (!doShow)
                continue;

            final long now = System.currentTimeMillis();
            final long uniqueId = (now / 1000) * 10 + uniqueCounter;
            if (++uniqueCounter >= 10)
                uniqueCounter = 0;
            final String tag = (isChangeEvent ? TAG_PREFIX_CHANGES : TAG_PREFIX_DIRECTIONS) + uniqueId;

            final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context,
                    isChangeEvent ? CHANNEL_ID_CHANGES : CHANNEL_ID_DIRECTIONS)
                    .setSmallIcon(R.drawable.ic_oeffi_directions_grey600_36dp)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setGroup(NOTIFICATION_GROUP_EVENTS)
                    .setContentTitle(data.title)
                    .setContentText(data.message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(data.message))
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    // .setAutoCancel(true)
                    .setTimeoutAfter(removeWhen > 0 ? removeWhen : 0)
                    .setSilent(true);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                getNotificationManager(context).notify(tag, 0, notificationBuilder.build());
            }
        }
    }

    public static final class Configuration implements Serializable {
        private static final long serialVersionUID = -3466636027523660100L;

        public boolean soundEnabled;
        public long beginningOfNavigation;
        public long[] travelAlarmExplicitMsForLegDeparture;
        public long[] travelAlarmIdForLegDeparture;
        public long[] travelAlarmExplicitMsForLegArrival;
        public long[] travelAlarmIdForLegArrival;

        public Configuration(final int numLegSlots) {
            travelAlarmExplicitMsForLegDeparture = new long[numLegSlots];
            travelAlarmIdForLegDeparture = new long[numLegSlots];
            travelAlarmExplicitMsForLegArrival = new long[numLegSlots];
            travelAlarmIdForLegArrival = new long[numLegSlots];
            for (int i = 0; i < numLegSlots; i += 1) {
                travelAlarmExplicitMsForLegDeparture[i] = -1;
                travelAlarmIdForLegDeparture[i] = 2L * i + 1;
                travelAlarmExplicitMsForLegArrival[i] = -1;
                travelAlarmIdForLegArrival[i] = 2L * i + 2;
            }
        }

        public void setTravelAlarmExplicitMsForLegDeparture(final int index, final long timeMs) {
            travelAlarmExplicitMsForLegDeparture[index] = timeMs;
            travelAlarmIdForLegDeparture[index] = System.currentTimeMillis();
        }

        public void setTravelAlarmExplicitMsForLegArrival(final int index, final long timeMs) {
            travelAlarmExplicitMsForLegArrival[index] = timeMs;
            travelAlarmIdForLegArrival[index] = System.currentTimeMillis();
        }
    }

    public static final class ExtraData implements Serializable {
        private static final long serialVersionUID = -2218877489048279370L;

        public boolean refreshAllLegs;
        public long[] currentTravelAlarmAtMsForLegDeparture;
        public long[] currentTravelAlarmAtMsForLegArrival;
        public EventLogEntry[] eventLogEntries;

        public ExtraData(final int numLegSlots) {
            currentTravelAlarmAtMsForLegDeparture = new long[numLegSlots];
            currentTravelAlarmAtMsForLegArrival = new long[numLegSlots];
            eventLogEntries = new EventLogEntry[0];
        }
    }

    public static final class EventLogEntry implements Serializable {
        private static final long serialVersionUID = -4350168363141237103L;

        public enum Type {
            MESSAGE,
            START_STOP,
            SERVICES_CANCELLED,
            PUBLIC_LEG_START,
            PUBLIC_LEG_END,
            PUBLIC_LEG_END_REMINDER,
            TRANSFER_LEG_START,
            TRANSFER_LEG_END,
            FINAL_TRANSFER,
            TRANSFER_END_REMINDER,
            DEPARTURE_DELAY_CHANGE,
            ARRIVAL_DELAY_CHANGE,
            DEPARTURE_POSITION_CHANGE,
            ARRIVAL_POSITION_CHANGE,
            TRANSFER_CRITICAL;
        }

        public final long timestamp;

        public final Type type;

        public final String message;

        public EventLogEntry(final long timestamp, final Type type, final String message) {
            this.timestamp = timestamp;
            this.type = type;
            this.message = message;
        }

        public EventLogEntry(final Type type, final String message) {
            this(System.currentTimeMillis(), type, message);
        }
    }

    private final Application context;
    private final boolean isEventNotificationsEnabled;
    private final boolean isDriverMode;
    private final TravelAlarmManager travelAlarmManager;
    private final String notificationTag;
    private final TripDetailsActivity.IntentData intentData;
    private TripRenderer.NotificationData lastNotified;
    private Configuration configuration;
    private final ExtraData extraData;

    public NavigationNotification(final Intent intent) {
        this(new TripDetailsActivity.IntentData(intent), null, null);
    }

    private NavigationNotification(
            final TripDetailsActivity.IntentData aIntentData,
            final Configuration configuration,
            final TripRenderer.NotificationData lastNotified) {
        this.context = Application.getInstance();
        this.isDriverMode = prefs.getBoolean(Constants.KEY_EXTRAS_DRIVERMODE_ENABLED, false);
        this.isEventNotificationsEnabled = prefs.getBoolean(PREFS_KEY_NOTIFICATIONS_ENABLED, false);
        this.travelAlarmManager = new TravelAlarmManager(context);
        final Trip trip = aIntentData.trip;
        final String uniqueId = trip.getUniqueId();
        final StringBuilder b = new StringBuilder();
        for (final Trip.Leg leg : trip.legs) {
            if (leg instanceof Trip.Public) {
                final Trip.Public publeg = (Trip.Public) leg;
                final JourneyRef journeyRef = publeg.journeyRef;
                if (journeyRef == null) {
                    b.append("null");
                } else if (journeyRef instanceof DbProvider.DbJourneyRef) {
                    final DbProvider.DbJourneyRef dbJourneyRef = (DbProvider.DbJourneyRef) journeyRef;
                    b.append(",j=");
                    b.append(dbJourneyRef.journeyId);
                    final Line line = dbJourneyRef.line;
                    b.append(",n=");
                    b.append(line.network);
                    b.append(",p=");
                    b.append(line.product);
                    b.append(",l=");
                    b.append(line.label);
                    b.append(",d=");
                    b.append(publeg.departureStop.location.id);
                } else {
                    b.append(journeyRef);
                }
            }
        }
        log.info("NOTIFICATION for TRIP: {}", b);
        notificationTag = TAG_PREFIX_GUIDE + uniqueId;
        final Bundle extras = getActiveNotificationExtras();
        if (extras == null) {
            this.intentData = aIntentData;
            final int numLegSlots = 1 + trip.legs.size();
            if (configuration != null) {
                this.configuration = configuration;
            } else {
                final Configuration conf = new Configuration(numLegSlots);
                conf.beginningOfNavigation = System.currentTimeMillis();
                this.configuration = conf;
            }
            this.extraData = new ExtraData(numLegSlots);
            this.lastNotified = lastNotified;
        } else {
            this.intentData = (TripDetailsActivity.IntentData) Objects.deserialize(extras.getByteArray(EXTRA_INTENTDATA));
            this.configuration = configuration != null ? configuration :
                    (Configuration) Objects.deserialize(extras.getByteArray(EXTRA_CONFIGURATION));
            this.extraData = (ExtraData) Objects.deserialize(extras.getByteArray(EXTRA_DATA));
            this.lastNotified = lastNotified != null ? lastNotified :
                    (TripRenderer.NotificationData) Objects.deserialize(extras.getByteArray(EXTRA_LASTNOTIFIED));
        }
    }

    public NetworkId getNetwork() {
        return intentData == null ? null : intentData.network;
    }

    public Trip getTrip() {
        return intentData == null ? null : intentData.trip;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public ExtraData getExtraData() {
        return extraData;
    }

    public static void updateFromForeground(
            final Context context, final Intent intent,
            final Configuration configuration,
            final Runnable doneListener) {
        updateFromForeground(context, intent, null, configuration, doneListener);
    }

    public static void updateFromForeground(
            final Context context, final Intent intent,
            final Trip trip, final Configuration configuration,
            final Runnable doneListener) {
        NavigationAlarmManager.runOnHandlerThread(() -> {
            new NavigationNotification(intent).internUpdateFromForeground(trip, configuration);
            if (doneListener != null)
                doneListener.run();
        });
    }

    private void internUpdateFromForeground(final Trip newTrip, final Configuration newConfiguration) {
        if (newConfiguration != null)
            this.configuration = newConfiguration;
        final Trip trip = newTrip != null ? newTrip : getTrip();
        update(trip);
        if (lastNotified != null) {
            final long refreshAt = lastNotified.refreshNotificationRequiredAt;
            if (refreshAt > 0) {
                NavigationAlarmManager.getInstance().start(refreshAt,
                        getPendingActivityIntent(false,
                                isDriverMode ? TripDetailsActivity.Page.ITINERARY : TripDetailsActivity.Page.NEXT_EVENT,
                                trip));
            }
        }
    }

    private Notification getActiveNotification() {
        log.info("looking for active notifications for tag={}", notificationTag);
        StatusBarNotification latestStatusBarNotification = null;
        final @NonNull List<StatusBarNotification> activeNotifications =
                getNotificationManager(context).getActiveNotifications();
        for (final StatusBarNotification statusBarNotification : activeNotifications) {
            final String tag = statusBarNotification.getTag();
            if (tag == null || !tag.equals(notificationTag)) {
                log.info("found other notification with tag={}", tag);
            } else {
                log.info("found matching notification with posttime={}, id={}, key={}",
                        statusBarNotification.getPostTime(),
                        statusBarNotification.getId(),
                        statusBarNotification.getKey());
                if (latestStatusBarNotification == null
                        || latestStatusBarNotification.getPostTime() < statusBarNotification.getPostTime()) {
                    latestStatusBarNotification = statusBarNotification;
                }
            }
        }
        if (latestStatusBarNotification == null)
            return null;
        return latestStatusBarNotification.getNotification();
    }

    private Bundle getActiveNotificationExtras() {
        final Notification notification = getActiveNotification();
        if (notification == null)
            return null;
        return notification.extras;
    }

//    static Long pppp;

    List<EventLogEntry> newEventLogEntries;
    List<String> newSpeakTexts;
    List<EventNotificationData> newEventNotifications;

    @SuppressLint("ScheduleExactAlarm")
    private boolean update(final Trip aTrip) {
        final TimeZoneSelector systemTimeZoneSelector = Application.getInstance().getSystemTimeZoneSelector();
        final Trip trip = aTrip != null ? aTrip : getTrip();
        final Date tripUpdatedAtDate = trip.updatedAt;
        log.info("updating with {} trip updated at {}", aTrip != null ? "new" : "old", NavigationAlarmManager.LOG_TIME_FORMAT.format(tripUpdatedAtDate));
        this.newEventLogEntries = new ArrayList<>();
        this.newSpeakTexts = new ArrayList<>();
        this.newEventNotifications = new ArrayList<>();
        // addEventOutputMessage(newEventLogEntries, "updating");
        final long tripUpdatedAt = tripUpdatedAtDate.getTime();
        final Date now = new Date();
        final long nowTime = now.getTime();
        final TripRenderer tripRenderer = new TripRenderer(null, trip, false, now);
        boolean refreshAllLegs = false;
        long nextRefreshTimeMs;
        String nextRefreshTimeReason;
        final long nextTripReloadTimeMs;
        final long travelAlarmAtMs;
        final boolean travelAlarmIsForDeparture;
        final boolean travelAlarmIsForJourneyStart;
        final int travelAlarmLegIndex;
        final String alarmTypeForLog;
        if (tripRenderer.nextEventEarliestTime != null) {
            if (tripRenderer.nextEventIsInitialIndividual) {
                travelAlarmIsForDeparture = true;
                travelAlarmIsForJourneyStart = true;
                final Trip.Leg firstLeg = trip.legs.isEmpty() ? null : trip.legs.get(0);
                final int walkMinutes;
                if (firstLeg instanceof Trip.Individual) {
                    walkMinutes = ((Trip.Individual) firstLeg).min;
                    travelAlarmLegIndex = 1;
                } else {
                    walkMinutes = 0;
                    travelAlarmLegIndex = 0;
                }
                travelAlarmAtMs = travelAlarmManager.getStartAlarm(
                        tripRenderer.nextEventEarliestTime.getTime(),
                        tripRenderer.nextEventEstimatedTime.getTime(),
                        configuration.travelAlarmExplicitMsForLegDeparture[travelAlarmLegIndex],
                        getNetwork(), trip.getFirstPublicLeg().departure.id, walkMinutes,
                        configuration.beginningOfNavigation, nowTime);
                alarmTypeForLog = "start";
            } else if (tripRenderer.nextEventTypeIsPublic) {
                travelAlarmLegIndex = tripRenderer.currentLeg.legIndex;
                travelAlarmIsForDeparture = false;
                travelAlarmIsForJourneyStart = false;
                travelAlarmAtMs = travelAlarmManager.getArrivalAlarm(
                        tripRenderer.nextEventEarliestTime.getTime(),
                        tripRenderer.nextEventEstimatedTime.getTime(),
                        configuration.travelAlarmExplicitMsForLegArrival[travelAlarmLegIndex],
                        tripRenderer.currentLeg.publicLeg.departureStop.getDepartureTime().getTime(), nowTime);
                alarmTypeForLog = "arrival";
            } else if (tripRenderer.currentLeg.transferTo != null) {
                travelAlarmLegIndex = tripRenderer.currentLeg.transferTo.legIndex;
                travelAlarmIsForDeparture = true;
                travelAlarmIsForJourneyStart = false;
                travelAlarmAtMs = travelAlarmManager.getDepartureAlarm(
                        tripRenderer.nextEventEarliestTime.getTime(),
                        tripRenderer.nextEventEstimatedTime.getTime(),
                        configuration.travelAlarmExplicitMsForLegDeparture[travelAlarmLegIndex],
                        tripRenderer.currentLeg.transferFrom.publicLeg.getArrivalTime().getTime(), nowTime);
                alarmTypeForLog = "departure";
            } else {
                travelAlarmAtMs = 0;
                alarmTypeForLog = "none";
                travelAlarmIsForDeparture = false;
                travelAlarmIsForJourneyStart = false;
                travelAlarmLegIndex = 0;
            }
            final long timeLeft = tripRenderer.nextEventEarliestTime.getTime() - nowTime;
            if (timeLeft < 240000) {
                // last 4 minutes and after, 30 secs refresh interval
                nextRefreshTimeReason = String.format("#1, timeLeft=%d", timeLeft);
                nextRefreshTimeMs = nowTime + 30000;
                nextTripReloadTimeMs = tripUpdatedAt + 60000;
            } else if (timeLeft < 600000) {
                // last 10 minutes and after, 60 secs refresh interval
                nextRefreshTimeReason = String.format("#2, timeLeft=%d", timeLeft);
                nextRefreshTimeMs = nowTime + 60000;
                nextTripReloadTimeMs = tripUpdatedAt + 120000;
            } else {
                final Date prevEventLatestTime = tripRenderer.prevEventLatestTime;
                final long prevEventLatestTimeValue = prevEventLatestTime != null ? prevEventLatestTime.getTime() : 0;
                final long timeOver = nowTime - prevEventLatestTimeValue;
                if (prevEventLatestTime != null && timeOver < 300000) {
                    // max 5 minutes after the beginning of the current action, 60 secs refresh interval
                    nextRefreshTimeReason = String.format("#3, timeLeft=%d, timeOver=%d, prevEventLatestTime=%s", timeLeft, timeOver, prevEventLatestTime);
                    nextRefreshTimeMs = nowTime + 60000;
                    nextTripReloadTimeMs = nextRefreshTimeMs;
                } else {
                    // approaching, refresh after 25% of the remaining time
                    nextRefreshTimeReason = String.format("#4, timeLeft=%d, timeOver=%d, prevEventLatestTime=%s", timeLeft, timeOver, prevEventLatestTime);
                    nextRefreshTimeMs = nowTime + ((timeLeft * 25) / 100);
                    nextTripReloadTimeMs = nextRefreshTimeMs;
                }
            }
        } else {
            final long lastArrivalTime = trip.getLastArrivalTime().getTime();
            final long timeOver = nowTime - lastArrivalTime;
            if (timeOver < 300000) {
                // max 5 minutes after the trip, 60 secs refresh interval
                nextRefreshTimeReason = String.format("#5, timeOver=%d, lastArrivalTime=%d", timeOver, lastArrivalTime);
                nextRefreshTimeMs = nowTime + 60000;
                nextTripReloadTimeMs = nextRefreshTimeMs;
            } else {
                // no refresh
                nextRefreshTimeReason = String.format("#6, timeOver=%d, lastArrivalTime=%d", timeOver, lastArrivalTime);
                nextRefreshTimeMs = 0;
                nextTripReloadTimeMs = 0;
            }
            travelAlarmAtMs = 0;
            alarmTypeForLog = "none";
            travelAlarmIsForDeparture = false;
            travelAlarmIsForJourneyStart = false;
            travelAlarmLegIndex = 0;
        }
//nextRefreshTimeMs = nowTime + 30000;
        final Date timeoutAt = new Date(trip.getLastArrivalTime().getTime() + KEEP_NOTIFICATION_FOR_MINUTES * 60000);
        final long duration = timeoutAt.getTime() - nowTime;
        if (duration <= 1000) {
            remove();
            return false;
        }
        final RemoteViews notificationLayout = new RemoteViews(context.getPackageName(), R.layout.navigation_notification);
        setupNotificationView(notificationLayout, tripRenderer, now);
        // final RemoteViews notificationLayoutExpanded = new RemoteViews(context.getPackageName(), R.layout.navigation_notification);
        // setupNotificationView(context, notificationLayoutExpanded, tripRenderer, now, newNotified);
        notificationLayout.setOnClickPendingIntent(R.id.navigation_notification_open_full,
                getPendingActivityIntent(false, null, trip));
        notificationLayout.setOnClickPendingIntent(R.id.navigation_notification_next_event,
                getPendingActionIntent(ACTION_REFRESH, trip));

        final TripRenderer.NotificationData newNotified = tripRenderer.notificationData;
        boolean timeChanged = false;
        boolean posChanged = false;
        boolean anyImportantIssues = false;
        int reminderSoundId = 0;
        final boolean onRide = tripRenderer.nextEventTypeIsPublic;
        final long nextEventTimeLeftMs = tripRenderer.nextEventTimeLeftMs;
        final long nextEventTimeLeftTo10MinsBoundaryMs = nowTime
                + nextEventTimeLeftMs - (nextEventTimeLeftMs / 600000) * 600000 + 2000;
        if (nextEventTimeLeftTo10MinsBoundaryMs < nextRefreshTimeMs) {
            nextRefreshTimeReason = String.format("#7, nextRefreshTimeMs=%d, nextEventTimeLeftTo10MinsBoundaryMs=%d", nextRefreshTimeMs, nextEventTimeLeftTo10MinsBoundaryMs);
            nextRefreshTimeMs = nextEventTimeLeftTo10MinsBoundaryMs;
        }
        if (lastNotified == null) {
            log.info("first notification !!");
            addEventOutputNavigationStarted();
        }
        if (newNotified.servicesCancelled) {
            addEventOutputServicesCancelled(lastNotified != null && lastNotified.servicesCancelled);
            anyImportantIssues = true;
        }
        if (lastNotified == null || newNotified.currentLegCIndex != lastNotified.currentLegCIndex) {
            if (lastNotified == null) {
                lastNotified = new TripRenderer.NotificationData();
            } else {
                log.info("switching leg from {} to {}", lastNotified.currentLegCIndex, newNotified.currentLegCIndex);
                if (newNotified.currentLegCIndex >= 0 && lastNotified.currentLegCIndex < 0)
                    addEventOutputNavigationRestarted();
                reminderSoundId = SOUND_REMIND_NEXTLEG;
            }
            lastNotified.leftTimeReminded = Long.MAX_VALUE;
            lastNotified.eventTime = newNotified.eventTime; // was .plannedEventTime but next announcement tells the change anyways
            if (newNotified.isTransfer) {
                // in transfer
                if (newNotified.publicArrivalLegIndex >= 0) {
                    addEventOutputPublicLegEnd((Trip.Public) trip.legs.get(newNotified.publicArrivalLegIndex));
                }
                if (newNotified.publicDepartureLegIndex >= 0) {
                    addEventOutputTransferStart(nextEventTimeLeftMs, (Trip.Public) trip.legs.get(newNotified.publicDepartureLegIndex));
                    lastNotified.departurePosition = newNotified.plannedDeparturePosition;
                } else {
                    addEventOutputFinalTransferStart(trip.to);
                }
            } else if (newNotified.currentLegCIndex >= 0) {
                // in public transport
                addEventOutputPublicLegStart(nextEventTimeLeftMs, (Trip.Public) trip.legs.get(newNotified.publicArrivalLegIndex));
                lastNotified.nextTransferCritical = false;
                lastNotified.departurePosition = newNotified.plannedDeparturePosition;
            } else {
                // finished
                if (newNotified.publicArrivalLegIndex >= 0) {
                    addEventOutputPublicLegEnd((Trip.Public) trip.legs.get(newNotified.publicArrivalLegIndex));
                }
                addEventOutputNavigationEnded();
            }
        }
        if (newNotified.departurePosition != null && !newNotified.departurePosition.equals(lastNotified.departurePosition)) {
            log.info("switching departure position from {} to {}", lastNotified.departurePosition, newNotified.departurePosition);
            if (newNotified.publicDepartureLegIndex >= 0) {
                addEventOutputDeparturePositionChange(
                        (Trip.Public) trip.legs.get(newNotified.publicDepartureLegIndex),
                        lastNotified.departurePosition);
            }
            posChanged = true;
        }
        if (newNotified.eventTime != null && lastNotified.eventTime != null) {
            final long leftSecs = nextEventTimeLeftMs / 1000;
            final long diffSecs = Math.abs(newNotified.eventTime.getTime() - lastNotified.eventTime.getTime()) / 1000;
            if (leftSecs < 1800) {
                timeChanged = diffSecs >= 180; // 3 mins during last 30 mins
            } else if (leftSecs < 3600) {
                timeChanged = diffSecs >= 300; // 5 mins during last 60 mins
            } else {
                timeChanged = diffSecs >= 600; // 10 mins when more than 1 hour
            }
            if (timeChanged) {
                log.info("time changed: leftSecs={}, diffSecs={}, accepting new time", leftSecs, diffSecs);
                if (newNotified.isTransfer) {
                    if (newNotified.publicDepartureLegIndex >= 0)
                        addEventOutputDepartureDelayChange((Trip.Public) trip.legs.get(newNotified.publicDepartureLegIndex));
                } else {
                    if (newNotified.publicArrivalLegIndex >= 0)
                        addEventOutputArrivalDelayChange((Trip.Public) trip.legs.get(newNotified.publicArrivalLegIndex));
                }
            } else {
                log.info("time not changed: leftSecs={}, diffSecs={}, keeping new time", leftSecs, diffSecs);
                newNotified.eventTime = lastNotified.eventTime;
            }
        }
        int numTransferCriticalChanged = 0;
        if (!newNotified.transfersCritical.equals(lastNotified.transfersCritical)) {
            final String lastTransfersCritical = lastNotified.transfersCritical;
            final String nextTransfersCritical = newNotified.transfersCritical;
            log.info("criticalTransfers switching from {} to {}", lastTransfersCritical, nextTransfersCritical);
            final int length = nextTransfersCritical.length();
            if (lastTransfersCritical == null || length != lastTransfersCritical.length()) {
                for (int i = 0; i < length; i += 1) {
                    final char next = nextTransfersCritical.charAt(i);
                    if (next != '-') {
                        numTransferCriticalChanged += 1;
                        break;
                    }
                }
            } else {
                for (int i = 0; i < length; i += 1) {
                    final char next = nextTransfersCritical.charAt(i);
                    if (next != '-' && next != lastTransfersCritical.charAt(i)) {
                        numTransferCriticalChanged += 1;
                        break;
                    }
                }
            }
        }
        boolean nextTransferCriticalChanged = false;
        if (newNotified.nextTransferCritical != lastNotified.nextTransferCritical) {
            log.info("transferCritical switching to {}", newNotified.nextTransferCritical);
            nextTransferCriticalChanged = newNotified.nextTransferCritical;
        }
        if (numTransferCriticalChanged > 0) {
            if (!nextTransferCriticalChanged || numTransferCriticalChanged > 1)
                addEventOutputAnyTransferCritical();
        }
        if (nextTransferCriticalChanged)
            addEventOutputNextTransferCritical();
        newNotified.playedTravelAlarmId = lastNotified.playedTravelAlarmId;
        if (travelAlarmAtMs > 0) {
            if (travelAlarmAtMs <= nowTime) {
                final long travelAlarmId = travelAlarmIsForDeparture
                        ? configuration.travelAlarmIdForLegDeparture[travelAlarmLegIndex]
                        : configuration.travelAlarmIdForLegArrival[travelAlarmLegIndex];
                if (lastNotified.playedTravelAlarmId == travelAlarmId) {
                    log.info("travel alarm for {} at {} was at {}, but already fired before", alarmTypeForLog,
                            tripRenderer.nextEventTargetName, Formats.formatTime(systemTimeZoneSelector, travelAlarmAtMs, PTDate.SYSTEM_OFFSET));
                } else {
                    log.info("travel alarm for {} at {} should have been at {}, now firing", alarmTypeForLog,
                            tripRenderer.nextEventTargetName, Formats.formatTime(systemTimeZoneSelector, travelAlarmAtMs, PTDate.SYSTEM_OFFSET));
                    travelAlarmManager.fireAlarm(context, intentData, tripRenderer, travelAlarmIsForDeparture, travelAlarmIsForJourneyStart);
                    newNotified.playedTravelAlarmId = travelAlarmId;
                }
            } else {
                log.info("travel alarm for {} at {} set to {}", alarmTypeForLog,
                        tripRenderer.nextEventTargetName, Formats.formatTime(systemTimeZoneSelector, travelAlarmAtMs, PTDate.SYSTEM_OFFSET));
                if (travelAlarmAtMs < nextRefreshTimeMs) {
                    nextRefreshTimeReason = String.format("#8, nextRefreshTimeMs=%d, travelAlarmAtMs=%d", nextRefreshTimeMs, travelAlarmAtMs);
                    nextRefreshTimeMs = travelAlarmAtMs;
                }
            }
        }
        newNotified.leftTimeReminded = lastNotified.leftTimeReminded;
        long nextReminderTimeMs = 0;
        if (tripRenderer.currentLeg != null) {
            if (nextEventTimeLeftMs < REMINDER_SECOND_MS + 20000) {
                log.info("next event {} < 2 mins : already reminded {}", nextEventTimeLeftMs, lastNotified.leftTimeReminded);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs;
                if (lastNotified.leftTimeReminded > REMINDER_SECOND_MS) {
                    log.info("reminding 2 mins = {}", nextReminderTimeMs);
                    addEventOutputReminder(nextEventTimeLeftMs, newNotified, trip);
                    reminderSoundId = SOUND_REMIND_IMPORTANT;
                    newNotified.leftTimeReminded = REMINDER_SECOND_MS;
                }
            } else if (nextEventTimeLeftMs < REMINDER_FIRST_MS + 20000) {
                log.info("next event {} < 6 mins : already reminded {}", nextEventTimeLeftMs, lastNotified.leftTimeReminded);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs - REMINDER_SECOND_MS;
                if (lastNotified.leftTimeReminded > REMINDER_FIRST_MS) {
                    log.info("reminding 6 mins = {}", nextReminderTimeMs);
                    addEventOutputReminder(nextEventTimeLeftMs, newNotified, trip);
                    reminderSoundId = SOUND_REMIND_NORMAL;
                    newNotified.leftTimeReminded = REMINDER_FIRST_MS;

                    // this is a good time to update the whole trip during the next minute
                    refreshAllLegs = true;
                }
            } else if (nextEventTimeLeftMs < REMINDER_FIRST_MS + 120000) {
                log.info("next event {} > 6 mins but < 6+2 : already reminded {}", nextEventTimeLeftMs, lastNotified.leftTimeReminded);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs - REMINDER_FIRST_MS;
                if (lastNotified.leftTimeReminded <= REMINDER_SECOND_MS) {
                    log.info("resetting");
                    newNotified.leftTimeReminded = Long.MAX_VALUE;
                }
            } else {
                log.info("next event {} > 6+2 mins : already reminded {}", nextEventTimeLeftMs, lastNotified.leftTimeReminded);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs - REMINDER_FIRST_MS;
                log.info("resetting");
                newNotified.leftTimeReminded = Long.MAX_VALUE;
            }
            if (nextReminderTimeMs > 0 && nextReminderTimeMs < nextRefreshTimeMs + 20000) {
                nextRefreshTimeReason = String.format("#9, nextRefreshTimeMs=%d, nextReminderTimeMs=%d", nextRefreshTimeMs, nextReminderTimeMs);
                nextRefreshTimeMs = nextReminderTimeMs;
            }
        }

        anyImportantIssues |= timeChanged || posChanged || nextTransferCriticalChanged || numTransferCriticalChanged > 0;
        log.info("timeChanged={}, posChanged={} nextTransferCriticalChanged={} numTransferCriticalChanged={} reminderSoundId={}",
                timeChanged, posChanged, nextTransferCriticalChanged, numTransferCriticalChanged, reminderSoundId);

        if (nextTripReloadTimeMs > 0 && nextTripReloadTimeMs < nextRefreshTimeMs) {
            nextRefreshTimeReason = String.format("#10, nextRefreshTimeMs=%d, nextTripReloadTimeMs=%d", nextRefreshTimeMs, nextTripReloadTimeMs);
            nextRefreshTimeMs = nextTripReloadTimeMs;
        }

        newNotified.refreshNotificationRequiredAt = nextRefreshTimeMs;
        newNotified.refreshTripRequiredAt = nextTripReloadTimeMs;

        if (nextRefreshTimeMs > 0) {
            log.info("refreshing in {} secs at {} (reason: {}), reminder at {}, trip reload at {}",
                    (nextRefreshTimeMs - nowTime) / 1000,
                    NavigationAlarmManager.LOG_TIME_FORMAT.format(nextRefreshTimeMs),
                    nextRefreshTimeReason,
                    NavigationAlarmManager.LOG_TIME_FORMAT.format(nextReminderTimeMs),
                    NavigationAlarmManager.LOG_TIME_FORMAT.format(nextTripReloadTimeMs));
        } else {
            log.info("stop refreshing");
        }

        final Bundle extras = new Bundle();
        extras.putByteArray(EXTRA_INTENTDATA, Objects.serialize(
                new TripDetailsActivity.IntentData(intentData.network, trip, intentData.renderConfig)));
        extras.putByteArray(EXTRA_LASTNOTIFIED, Objects.serialize(newNotified));
        extras.putByteArray(EXTRA_CONFIGURATION, Objects.serialize(configuration));

        final ExtraData newExtraData = extraData != null ? Objects.clone(extraData) : new ExtraData(1 + trip.legs.size());
        newExtraData.refreshAllLegs = refreshAllLegs;
        if (travelAlarmIsForDeparture)
            newExtraData.currentTravelAlarmAtMsForLegDeparture[travelAlarmLegIndex] = travelAlarmAtMs;
        else
            newExtraData.currentTravelAlarmAtMsForLegArrival[travelAlarmLegIndex] = travelAlarmAtMs;

        final int newEventLogEntriesSize = newEventLogEntries.size();
        if (newEventLogEntriesSize > 0) {
            int offset = newExtraData.eventLogEntries.length;
            final EventLogEntry[] entries = Arrays.copyOf(newExtraData.eventLogEntries, offset + newEventLogEntriesSize);
            for (final EventLogEntry logEntry : newEventLogEntries)
                entries[offset++] = logEntry;
            newExtraData.eventLogEntries = entries;
        }

        extras.putByteArray(EXTRA_DATA, Objects.serialize(newExtraData));

        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID_GUIDE)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setGroup(notificationTag)
//                .setSmallIcon(R.drawable.ic_oeffi_directions)
//                .setSmallIcon(R.drawable.ic_oeffi_directions,1)
                .setSmallIcon(R.drawable.ic_oeffi_directions_grey600_36dp)
//                .setSmallIcon(R.mipmap.ic_oeffi_directions_color_48dp)
//                .setSmallIcon(IconCompat.createWithResource(context, R.mipmap.ic_oeffi_directions_color_48dp))
//                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_oeffi_directions_color_48dp))
                .setColorized(true).setColor(context.getColor(R.color.bg_trip_details_public_now))
                .setSubText(context.getString(R.string.navigation_notification_subtext,
                        trip.getLastPublicLeg().arrivalStop.location.uniqueShortName()))
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setContent(notificationLayout)
                // .setCustomContentView(notificationLayout)
                // .setCustomBigContentView(notificationLayoutExpanded)
                // .setContentIntent(getPendingActivityIntent(false, true))
                .setContentIntent(getPendingActionIntent(ACTION_REFRESH, trip))
                //.setDeleteIntent(getPendingActivityIntent(context, true))
                .setDeleteIntent(getPendingActionIntent(ACTION_DELETE, trip))
                .setAutoCancel(false)
                .setOngoing(true)
                .setLocalOnly(true)
                .setUsesChronometer(true)
                .setWhen(nowTime)
                .setTimeoutAfter(duration)
                .setExtras(extras)
                .addAction(R.drawable.ic_clear_white_24dp, context.getString(R.string.navigation_opennav_shownextevent),
                        getPendingActivityIntent(false, TripDetailsActivity.Page.NEXT_EVENT, trip))
                .addAction(R.drawable.ic_navigation_white_24dp, context.getString(R.string.navigation_opennav_showtrip),
                        getPendingActivityIntent(false, TripDetailsActivity.Page.ITINERARY, trip))
                .addAction(R.drawable.ic_clear_white_24dp, context.getString(R.string.navigation_stopnav_stop),
                        getPendingActivityIntent(true, TripDetailsActivity.Page.ITINERARY, trip));

        if (anyImportantIssues) {
            notificationBuilder.setSilent(true);
        } else if (reminderSoundId == SOUND_REMIND_VIA_NOTIFICATION) {
            notificationBuilder
                    .setSilent(!configuration.soundEnabled)
                    .setVibrate(VIBRATION_PATTERN_REMIND)
                    .setSound(ResourceUri.fromResource(context, reminderSoundId), getAudioStreamForSound(reminderSoundId));
        } else {
            notificationBuilder.setSilent(true);
        }

        final Notification notification = notificationBuilder.build();
        log.info("set notification with tag={}", notificationTag);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            getNotificationManager(context).notify(notificationTag, 0, notification);
        }

        postEventNotifications(newEventNotifications);

        if (anyImportantIssues) {
            playAlarmSoundAndVibration(-1,
                    SOUND_ALARM,
                    VIBRATION_PATTERN_ALARM,
                    newSpeakTexts,
                    onRide);
        } else if ((reminderSoundId != 0 || !newSpeakTexts.isEmpty()) && reminderSoundId != SOUND_REMIND_VIA_NOTIFICATION) {
            playAlarmSoundAndVibration(-1,
                    reminderSoundId,
                    reminderSoundId == 0 ? null : VIBRATION_PATTERN_REMIND,
                    newSpeakTexts,
                    onRide);
        }

        lastNotified = newNotified;
        return anyImportantIssues || reminderSoundId != 0;
    }

    private void playAlarmSoundAndVibration(
            final int soundUsage,
            final int aSoundId,
            final long[] vibrationPattern,
            final List<String> speakTexts,
            final boolean onRide) {
        final int actualSoundId = configuration.soundEnabled ? getActualSound(aSoundId) : 0;
        final int actualUsage = soundUsage >= 0 ? soundUsage : getAudioUsageForSound(aSoundId, onRide);
        final NotificationSoundManager soundManager = NotificationSoundManager.getInstance();
        boolean doSpeech = false;
        if (configuration.soundEnabled) {
            final String when = NavigationNotification.prefs.getString(PREFS_KEY_NAVIGATION_SPEECH_OUTPUT, "never");
            if ("always".equals(when) || ("headphones".equals(when) && soundManager.isHeadsetConnected()))
                doSpeech = true;
        };
        soundManager.playAlarmSoundAndVibration(actualUsage, actualSoundId, vibrationPattern, doSpeech ? speakTexts : null);
    }

    public void remove() {
        getNotificationManager(context).cancel(notificationTag, 0);
    }

    private PendingIntent getPendingActivityIntent(
            final boolean deleteRequest, final TripDetailsActivity.Page setShowPage,
            final Trip trip) {
        final Intent intent = TripNavigatorActivity.buildStartIntent(
                context, intentData.network, trip, intentData.renderConfig,
                deleteRequest, setShowPage, null, false);
        return PendingIntent.getActivity(context,
                (deleteRequest ? 1 : 0) + (setShowPage == null ? 0 : (setShowPage.pageNum << 1)),
                intent, PendingIntent.FLAG_IMMUTABLE);
    }

    public static class ActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            NavigationAlarmManager.runOnHandlerThread(() -> {
                final NavigationNotification navigationNotification = new NavigationNotification(intent);
                switch (intent.getIntExtra(INTENT_EXTRA_ACTION, 0)) {
                    case ACTION_REFRESH:
                        navigationNotification.update(null);
                        break;
                    case ACTION_DELETE:
                        navigationNotification.remove();
                        break;
                    default:
                        break;
                }
            });
        }
    }

    private PendingIntent getPendingActionIntent(final int action, final Trip trip) {
        final Intent intent = new Intent(context, ActionReceiver.class);
        intent.setData(new Uri.Builder()
                .scheme("data")
                .authority(ActionReceiver.class.getName())
                .path(intentData.network.name() + "/" + trip.getUniqueId())
                .build());
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_NETWORK, intentData.network);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_TRIP, trip);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_RENDERCONFIG, intentData.renderConfig);
        intent.putExtra(INTENT_EXTRA_ACTION, action);
        return PendingIntent.getBroadcast(context, action, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private long refresh(final boolean refreshAllLegs) {
        log.info("refreshing notification");
        final Date now = new Date();
        final long nowTime = now.getTime();
        final long refreshRequiredAt = lastNotified.refreshNotificationRequiredAt;
        if (nowTime < refreshRequiredAt)
            return refreshRequiredAt; // ignore multiple alarms in short time
        Trip newTrip = null;
        if (lastNotified.refreshTripRequiredAt > 0 && nowTime >= lastNotified.refreshTripRequiredAt) {
            try {
                log.info("refreshing trip");
                final Navigator navigator = new Navigator(intentData.network, getTrip());
                newTrip = navigator.refresh(refreshAllLegs, now);
            } catch (IOException e) {
                log.error("error while refreshing trip", e);
            }
            if (newTrip != null) {
                final boolean alarmPlayed = update(newTrip);
                context.sendBroadcast(new Intent(ACTION_UPDATE_TRIGGER));
                if (!alarmPlayed && prefs.getBoolean(PREFS_KEY_NAVIGATION_REFRESH_BEEP, true)) {
                    playAlarmSoundAndVibration(AudioAttributes.USAGE_NOTIFICATION, R.raw.nav_refresh_beep, null, null, false);
                }
            } else {
                playAlarmSoundAndVibration(AudioAttributes.USAGE_NOTIFICATION, R.raw.nav_refresh_error, null, null, false);
                update(null);
            }
        } else {
            update(null);
            context.sendBroadcast(new Intent(ACTION_UPDATE_TRIGGER));
        }
        return lastNotified.refreshNotificationRequiredAt;
    }

    private void setupNotificationView(
            final RemoteViews remoteViews,
            final TripRenderer tripRenderer, final Date now) {
        final int colorHighlight = context.getColor(R.color.bg_trip_details_public_now);
        final int colorNormal = context.getColor(R.color.bg_level0);
        final int colorHighIfPublic = tripRenderer.nextEventTypeIsPublic ? colorHighlight : colorNormal;
        final int colorHighIfChangeover = tripRenderer.nextEventTypeIsPublic ? colorNormal : colorHighlight;
        remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_current_action, colorHighlight);
        remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_next_action, colorNormal);
        remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_time, colorHighlight);
        remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_target, colorHighlight);
        remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_positions_big, colorHighIfChangeover);
        remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_positions_small, colorHighIfChangeover);
        remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_departure, colorNormal);
        remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_connection, colorHighIfChangeover);
        remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_changeover, colorHighIfChangeover);

        if (tripRenderer.nextEventCurrentStringId > 0) {
            final String s = context.getString(tripRenderer.nextEventCurrentStringId);
            remoteViews.setTextViewText(R.id.navigation_notification_next_event_current_action, s);
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_current_action, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_current_action, View.GONE);
        }

        if (tripRenderer.nextEventNextStringId > 0) {
            final String s = context.getString(tripRenderer.nextEventNextStringId);
            remoteViews.setTextViewText(R.id.navigation_notification_next_event_next_action, s);
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_next_action, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_next_action, View.GONE);
        }

        final String chronoFormat = tripRenderer.nextEventTimeLeftChronometerFormat;
        String valueStr = tripRenderer.nextEventTimeLeftValue;
        if (valueStr == null) {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time, View.GONE);
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_finished, View.VISIBLE);
            remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_finished, colorHighlight);
            remoteViews.setTextColor(R.id.navigation_notification_next_event_finished, context.getColor(R.color.fg_significant));
        } else {
            if (chronoFormat != null) {
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_value, View.GONE);
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_chronometer, View.VISIBLE);
                remoteViews.setChronometer(R.id.navigation_notification_next_event_time_chronometer,
                        ClockUtils.clockToElapsedTime(tripRenderer.nextEventEstimatedTime.getTime()),
                        chronoFormat, true);
                remoteViews.setChronometerCountDown(R.id.navigation_notification_next_event_time_chronometer, true);
                remoteViews.setTextColor(R.id.navigation_notification_next_event_time_chronometer,
                        context.getColor(tripRenderer.nextEventTimeLeftCritical ? R.color.fg_trip_next_event_important : R.color.fg_trip_next_event_normal));
            } else {
                if (TripRenderer.NO_TIME_LEFT_VALUE.equals(valueStr))
                    valueStr = context.getString(R.string.navigation_next_event_no_time_left);
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_value, View.VISIBLE);
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_chronometer, View.GONE);
                remoteViews.setTextViewText(R.id.navigation_notification_next_event_time_value, valueStr);
                remoteViews.setTextColor(R.id.navigation_notification_next_event_time_value,
                        context.getColor(tripRenderer.nextEventTimeLeftCritical ? R.color.fg_trip_next_event_important : R.color.fg_trip_next_event_normal));
            }
            remoteViews.setTextViewText(R.id.navigation_notification_next_event_time_unit, tripRenderer.nextEventTimeLeftUnit);
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_hourglass,
                    tripRenderer.nextEventTimeHourglassVisible ? View.VISIBLE : View.GONE);
            if (tripRenderer.nextEventTimeLeftExplainStr != null) {
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_explain, View.VISIBLE);
                remoteViews.setTextViewText(R.id.navigation_notification_next_event_time_explain, tripRenderer.nextEventTimeLeftExplainStr);
            } else {
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_explain, View.GONE);
            }
        }

        if (tripRenderer.nextEventTargetName != null) {
            remoteViews.setTextViewText(R.id.navigation_notification_next_event_target, tripRenderer.nextEventTargetName);
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_target, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_target, View.GONE);
        }

        remoteViews.setViewVisibility(R.id.navigation_notification_next_event_positions_big, View.GONE);
        remoteViews.setViewVisibility(R.id.navigation_notification_next_event_positions_small, View.GONE);
        if (tripRenderer.nextEventPositionsAvailable) {
            final boolean isBigPositions = 7 <
                      (tripRenderer.nextEventArrivalPosName != null ? tripRenderer.nextEventArrivalPosName.length() : 0)
                    + (tripRenderer.nextEventDeparturePosName != null ? tripRenderer.nextEventDeparturePosName.length() : 0);

            remoteViews.setViewVisibility(isBigPositions
                    ? R.id.navigation_notification_next_event_positions_big
                    : R.id.navigation_notification_next_event_positions_small,
                    View.VISIBLE);

            final int id_navigation_notification_next_event_position_from = isBigPositions
                    ? R.id.navigation_notification_next_event_position_from_big
                    : R.id.navigation_notification_next_event_position_from_small;
            if (tripRenderer.nextEventArrivalPosName != null) {
                remoteViews.setViewVisibility(id_navigation_notification_next_event_position_from, View.VISIBLE);
                remoteViews.setTextViewText(id_navigation_notification_next_event_position_from, tripRenderer.nextEventArrivalPosName);
                remoteViewsSetBackgroundColor(remoteViews, id_navigation_notification_next_event_position_from,
                        context.getColor(tripRenderer.nextEventArrivalPosChanged ? R.color.bg_position_changed : R.color.bg_position));
            } else {
                remoteViews.setViewVisibility(id_navigation_notification_next_event_position_from, View.GONE);
            }

            final int id_navigation_notification_next_event_position_to = isBigPositions
                    ? R.id.navigation_notification_next_event_position_to_big
                    : R.id.navigation_notification_next_event_position_to_small;
            if (tripRenderer.nextEventDeparturePosName != null) {
                remoteViews.setViewVisibility(id_navigation_notification_next_event_position_to, View.VISIBLE);
                remoteViews.setTextViewText(id_navigation_notification_next_event_position_to, tripRenderer.nextEventDeparturePosName);
                remoteViewsSetBackgroundColor(remoteViews, id_navigation_notification_next_event_position_to,
                        context.getColor(tripRenderer.nextEventDeparturePosChanged ? R.color.bg_position_changed : R.color.bg_position));
            } else {
                remoteViews.setViewVisibility(id_navigation_notification_next_event_position_to, View.GONE);
            }

            final int id_navigation_notification_next_event_positions_walk_icon = isBigPositions
                    ? R.id.navigation_notification_next_event_positions_walk_icon_big
                    : R.id.navigation_notification_next_event_positions_walk_icon_small;
            final int id_navigation_notification_next_event_positions_from_walk_arrow = isBigPositions
                    ? R.id.navigation_notification_next_event_positions_from_walk_arrow_big
                    : R.id.navigation_notification_next_event_positions_from_walk_arrow_small;
            final int id_navigation_notification_next_event_positions_to_walk_arrow = isBigPositions
                    ? R.id.navigation_notification_next_event_positions_to_walk_arrow_big
                    : R.id.navigation_notification_next_event_positions_to_walk_arrow_small;
            final int iconId = tripRenderer.nextEventTransferIconId;
            if (tripRenderer.nextEventStopChange) {
                remoteViews.setImageViewResource(id_navigation_notification_next_event_positions_walk_icon,
                        iconId != 0 ? iconId : R.drawable.ic_directions_walk_grey600_24dp);
                remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_walk_icon, View.VISIBLE);
                remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_from_walk_arrow, View.VISIBLE);
                remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_to_walk_arrow, View.VISIBLE);
            } else if (iconId == 0) {
                remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_walk_icon, View.GONE);
                if (tripRenderer.nextEventDeparturePosName != null) {
                    remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_to_walk_arrow, View.VISIBLE);
                    remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_from_walk_arrow, View.GONE);
                } else {
                    remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_to_walk_arrow, View.GONE);
                    remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_from_walk_arrow,
                            tripRenderer.nextEventArrivalPosName != null ? View.VISIBLE : View.GONE);
                }
            } else {
                remoteViews.setImageViewResource(id_navigation_notification_next_event_positions_walk_icon, iconId);
                remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_walk_icon, View.VISIBLE);
                remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_to_walk_arrow,
                        tripRenderer.nextEventDeparturePosName != null ? View.VISIBLE : View.GONE);
                remoteViews.setViewVisibility(id_navigation_notification_next_event_positions_from_walk_arrow,
                        tripRenderer.nextEventArrivalPosName != null ? View.VISIBLE : View.GONE);
            }
        }

        if (tripRenderer.nextEventTransportLine == null) {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_connection, View.GONE);
        } else {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_connection, View.VISIBLE);

            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_connection_line, View.VISIBLE);
            remoteViews.setTextViewText(R.id.navigation_notification_next_event_connection_line, tripRenderer.nextEventTransportLine.label);
            if (tripRenderer.nextEventTransportLine.style != null) {
                remoteViews.setTextColor(R.id.navigation_notification_next_event_connection_line,
                        tripRenderer.nextEventTransportLine.style.foregroundColor);
                remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_connection_line,
                        tripRenderer.nextEventTransportLine.style.backgroundColor);
            }

            remoteViews.setTextViewText(R.id.navigation_notification_next_event_connection_to, tripRenderer.nextEventTransportDestinationName);
        }

        if (!tripRenderer.nextEventChangeOverAvailable) {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_changeover, View.GONE);
        } else {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_changeover, View.VISIBLE);

            if (tripRenderer.nextEventTransferAvailable) {
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_transfer, View.VISIBLE);
                remoteViews.setTextViewText(R.id.navigation_notification_next_event_transfer_value, tripRenderer.nextEventTransferLeftTimeValue);
                remoteViews.setTextColor(R.id.navigation_notification_next_event_transfer_value,
                        context.getColor(tripRenderer.nextEventTransferLeftTimeCritical
                                ? R.color.fg_trip_next_event_important
                                : R.color.fg_trip_next_event_normal));

                if (tripRenderer.nextEventTransferLeftTimeFromNowValue != null) {
                    remoteViews.setViewVisibility(R.id.navigation_notification_next_event_transfer_time, View.VISIBLE);
                    remoteViews.setTextViewText(R.id.navigation_notification_next_event_transfer_time_value, tripRenderer.nextEventTransferLeftTimeFromNowValue);
                } else {
                    remoteViews.setViewVisibility(R.id.navigation_notification_next_event_transfer_time, View.GONE);
                }

                if (tripRenderer.nextEventTransferExplain != null) {
                    remoteViews.setViewVisibility(R.id.navigation_notification_next_event_transfer_explain, View.VISIBLE);
                    remoteViews.setTextViewText(R.id.navigation_notification_next_event_transfer_explain, tripRenderer.nextEventTransferExplain);
                } else {
                    remoteViews.setViewVisibility(R.id.navigation_notification_next_event_transfer_explain, View.GONE);
                }
            } else {
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_transfer, View.GONE);
            }

            if (tripRenderer.nextEventTransferWalkAvailable) {
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_walk, View.VISIBLE);
                remoteViews.setTextViewText(R.id.navigation_notification_next_event_walk_value, tripRenderer.nextEventTransferWalkTimeValue);
                remoteViews.setImageViewResource(R.id.navigation_notification_next_event_walk_icon, tripRenderer.nextEventTransferIconId);
            } else {
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_walk, View.GONE);
            }
        }

        remoteViews.setTextViewText(R.id.navigation_notification_next_event_departure, tripRenderer.nextEventDepartureName);
        remoteViews.setViewVisibility(R.id.navigation_notification_next_event_departure,
                tripRenderer.nextEventDepartureName != null ? View.VISIBLE : View.GONE);

        remoteViews.setViewVisibility(R.id.navigation_notification_critical,
                tripRenderer.futureTransferCritical || tripRenderer.servicesCancelled
                        ? View.VISIBLE : View.GONE);
        remoteViews.setImageViewResource(R.id.navigation_notification_critical,
                tripRenderer.servicesCancelled
                    ? R.drawable.ic_no_transfer_black_24dp
                    : R.drawable.ic_warning_black_24px);
    }

    private static void remoteViewsSetBackgroundColor(
            final RemoteViews remoteViews,
            final int viewId, final int color) {
        remoteViews.setInt(viewId, "setBackgroundColor", color);
    }

    private TimeZoneSelector getNetworkTimeZoneSelector() {
        NetworkId network = getNetwork();
        if (network == null)
            network = context.prefsGetNetworkId();
        return context.getPreferredNetworkTimeZoneSelector(network);
    }

    private String platformForLogMessage(final Position prevPosition, final Position newPosition) {
        final String prevText = prevPosition == null ? "?" : prevPosition.toString();
        final String newText = newPosition == null ? "?" : newPosition.toString();
        if (prevText.equals(newText))
            return context.getString(R.string.navigation_event_log_position_unchanged_format, newText);
        return context.getString(R.string.navigation_event_log_position_changed_format, newText, prevText);
    }

    @SuppressLint("StringFormatMatches")
    private String platformForSpeakText(final boolean sayOn, final Position prevPosition, final Position newPosition) {
        final String prevText = prevPosition == null ? null : prevPosition.toString();
        final String newText = newPosition == null ? null : newPosition.toString();
        if (prevPosition == null) {
            if (newPosition == null)
                return "";
            return context.getString(
                    sayOn
                        ? R.string.navigation_event_speak_position_on_unchanged_format
                        : R.string.navigation_event_speak_position_to_unchanged_format,
                    newText);
        }
        if (newPosition == null || prevText.equals(newText)) {
            return context.getString(
                    sayOn
                        ? R.string.navigation_event_speak_position_on_unchanged_format
                        : R.string.navigation_event_speak_position_to_unchanged_format,
                    prevText);
        }
        return context.getString(
                sayOn
                    ? R.string.navigation_event_speak_position_on_changed_format
                    : R.string.navigation_event_speak_position_to_changed_format,
                newText, prevText);
    }

    private String platformForNotificationMessage(final Position prevPosition, final Position newPosition) {
        final String prevText = prevPosition == null ? "?" : prevPosition.toString();
        final String newText = newPosition == null ? "?" : newPosition.toString();
        if (prevText.equals(newText))
            return context.getString(R.string.navigation_event_notify_position_unchanged_format, newText);
        return context.getString(R.string.navigation_event_notify_position_changed_format, newText, prevText);
    }

    private String timesForSpeakText(final String plannedTime, final String estimatedTime, final long delayMillis) {
        if (plannedTime == null) {
            if (estimatedTime == null)
                return "";
            return context.getString(R.string.navigation_event_speak_times_nodelay_format, estimatedTime);
        }
        if (estimatedTime == null || plannedTime.equals(estimatedTime))
            return context.getString(R.string.navigation_event_speak_times_nodelay_format, plannedTime);
        return context.getString(R.string.navigation_event_speak_times_delayed_format,
                plannedTime, estimatedTime, Long.toString(delayMillis / 60000));
    }

    private String remainingTimeForSpeakTextAtEnd(final long remainingMillis) {
        if (remainingMillis < -50000)
            return context.getString(R.string.navigation_event_speak_time_left_end_negative_format,
                    Long.toString((-remainingMillis + 10000) / 60000));
        if (remainingMillis < 50000)
            return context.getString(R.string.navigation_event_speak_notime_left_end_format);
        return context.getString(R.string.navigation_event_speak_time_left_end_format,
                Long.toString((remainingMillis + 10000) / 60000));
    }

    private String remainingTimeForSpeakText(final long remainingMillis) {
        if (remainingMillis < 50000)
            return context.getString(R.string.navigation_event_speak_notime_left_front_format);
        return context.getString(R.string.navigation_event_speak_time_left_front_format,
                Long.toString((remainingMillis + 10000) / 60000));
    }

    private String remainingTimeForNotificationText(final long remainingMillis) {
        if (remainingMillis < 50000)
            return context.getString(R.string.navigation_event_notify_notime_left_front_format);
        return context.getString(R.string.navigation_event_notify_time_left_front_format,
                Long.toString((remainingMillis + 10000) / 60000));
    }

    private String remainingTimeForNotificationAtEnd(final long remainingMillis) {
        if (remainingMillis < -50000)
            return context.getString(R.string.navigation_event_notify_time_left_end_negative_format,
                    Long.toString((-remainingMillis + 10000) / 60000));
        if (remainingMillis < 50000)
            return context.getString(R.string.navigation_event_notify_notime_left_end_format);
        return context.getString(R.string.navigation_event_notify_time_left_end_format,
                Long.toString((remainingMillis + 10000) / 60000));
    }

    private void addEventLogMessage(final String text) {
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.MESSAGE, context.getString(
                R.string.navigation_event_log_entry_message,
                text)));
    }

    private void addEventOutputNavigationStarted() {
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.START_STOP, context.getString(
                R.string.navigation_event_log_entry_nav_start)));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_nav_start));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                    R.string.navigation_event_notify_nav_start)));
        }
    }

    private void addEventOutputNavigationRestarted() {
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.START_STOP, context.getString(
                R.string.navigation_event_log_entry_nav_restart)));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_nav_restart));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                    R.string.navigation_event_notify_nav_restart)));
        }
    }

    private void addEventOutputNavigationEnded() {
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.START_STOP, context.getString(
                R.string.navigation_event_log_entry_nav_end)));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_nav_end));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                    R.string.navigation_event_notify_nav_end)));
        }
    }

    private void addEventOutputServicesCancelled(final boolean alreadyNotified) {
        if (!alreadyNotified) {
            newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.SERVICES_CANCELLED, context.getString(
                    R.string.navigation_event_log_entry_services_cancelled)));
        }
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_services_cancelled));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                    R.string.navigation_event_notify_services_cancelled)));
        }
    }

    @SuppressLint("StringFormatInvalid")
    private void addEventOutputTransferStart(
            final long timeLeftMs,
            final Trip.Public publicLeg) {
        final TimeZoneSelector timeZoneSelector = getNetworkTimeZoneSelector();
        final Stop stop = publicLeg.departureStop;
        final PTDate predictedTime = stop.getDepartureTime(false);
        final PTDate plannedTime = stop.getDepartureTime(true);
        final Line line = publicLeg.line;
        final String locationName = Formats.fullLocationName(publicLeg.departure);
        final String plannedTimeString = Formats.formatTime(timeZoneSelector, plannedTime);
        final String predictedTimeString = Formats.formatTime(timeZoneSelector, predictedTime);
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.TRANSFER_LEG_START, context.getString(
                R.string.navigation_event_log_entry_transfer_start,
                line.label,
                locationName,
                platformForLogMessage(stop.plannedDeparturePosition, stop.getDeparturePosition()),
                plannedTimeString,
                predictedTimeString,
                formatTimeSpan(plannedTime, predictedTime))));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_transfer_start,
                makeSpeakableLineName(line, publicLeg.destination, stop.location),
                locationName,
                platformForSpeakText(true, stop.plannedDeparturePosition, stop.getDeparturePosition()),
                timesForSpeakText(plannedTimeString, predictedTimeString, predictedTime.getTime() - plannedTime.getTime()),
                remainingTimeForSpeakTextAtEnd(timeLeftMs)));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                    R.string.navigation_event_notify_transfer_start,
                    makeNotificationLineName(line, publicLeg.destination, stop.location),
                    locationName,
                    platformForNotificationMessage(stop.plannedDeparturePosition, stop.getDeparturePosition()),
                    plannedTimeString,
                    predictedTimeString,
                    formatTimeSpan(plannedTime, predictedTime),
                    remainingTimeForNotificationAtEnd(timeLeftMs))));
        }
    }

    private void addEventOutputNextTransferPreview(
            final Trip.Public departureLeg,
            final Trip.Public arrivalLeg,
            final boolean outputNotification) {
        final Location departureLocation = departureLeg.departure;
        final Location arrivalLocation = arrivalLeg == null ? null : arrivalLeg.arrival;
        final boolean sameLocation = departureLocation.equals(arrivalLocation);
        final Location originLocation = arrivalLeg == null ? null : arrivalLeg.departure;
        final Position departurePosition = departureLeg.getDeparturePosition();
        final String arrivalLocationName = Formats.fullLocationNameIfDifferentPlace(arrivalLocation, originLocation);
        final String departureLocationName = Formats.fullLocationNameIfDifferentPlace(departureLocation, arrivalLocation);
        if (sameLocation) {
            if (departurePosition != null) {
                newSpeakTexts.add(context.getString(
                        R.string.navigation_event_speak_transfer_preview_same_location,
                        arrivalLocationName,
                        departureLocationName,
                        platformForSpeakText(false, departureLeg.departureStop.plannedDeparturePosition, departurePosition)));
                if (isEventNotificationsEnabled && outputNotification) {
                    newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                            R.string.navigation_event_notify_transfer_preview_same_location,
                            arrivalLocationName,
                            departureLocationName,
                            platformForNotificationMessage(departureLeg.departureStop.plannedDeparturePosition, departurePosition))));
                }
            }
        } else {
            if (departurePosition != null) {
                newSpeakTexts.add(context.getString(
                        R.string.navigation_event_speak_transfer_preview_different_location,
                        arrivalLocationName,
                        departureLocationName,
                        platformForSpeakText(false, departureLeg.departureStop.plannedDeparturePosition, departurePosition)));
                if (isEventNotificationsEnabled && outputNotification) {
                    newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                            R.string.navigation_event_notify_transfer_preview_different_location,
                            arrivalLocationName,
                            departureLocationName,
                            platformForNotificationMessage(departureLeg.departureStop.plannedDeparturePosition, departurePosition))));
                }
            } else {
                newSpeakTexts.add(context.getString(
                        R.string.navigation_event_speak_transfer_preview_different_location_no_position,
                        arrivalLocationName,
                        departureLocationName));
                if (isEventNotificationsEnabled && outputNotification) {
                    newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                            R.string.navigation_event_notify_transfer_preview_different_location_no_position,
                            arrivalLocationName,
                            departureLocationName)));
                }
            }
        }
    }

    private void addEventOutputFinalTransferStart(
            final Location destination) {
        final String locationName = Formats.fullLocationName(destination);
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.FINAL_TRANSFER, context.getString(
                R.string.navigation_event_log_entry_final_transfer_start,
                locationName)));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_final_transfer_start,
                locationName));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                    R.string.navigation_event_notify_final_transfer_start,
                    locationName)));
        }
    }

    private void addEventOutputFinalTransferEndReminder(
            final long timeLeftMs,
            final Location destination) {
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.TRANSFER_END_REMINDER, context.getString(
                R.string.navigation_event_log_entry_final_transfer_end_reminder,
                formatTimeSpan(timeLeftMs),
                Formats.fullLocationName(destination))));
        // no spoken instruction
    }

    private void addEventOutputPublicLegStart(
            final long timeLeftMs,
            final Trip.Public publicLeg) {
        final TimeZoneSelector timeZoneSelector = getNetworkTimeZoneSelector();
        final Stop stop = publicLeg.arrivalStop;
        final PTDate predictedTime = stop.getArrivalTime(false);
        final PTDate plannedTime = stop.getArrivalTime(true);
        final Line line = publicLeg.line;
        final String locationName = Formats.fullLocationName(publicLeg.arrival);
        final String plannedTimeString = Formats.formatTime(timeZoneSelector, plannedTime);
        final String predictedTimeString = Formats.formatTime(timeZoneSelector, predictedTime);
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.PUBLIC_LEG_START, context.getString(
                R.string.navigation_event_log_entry_public_leg_start,
                line.label,
                locationName,
                platformForLogMessage(stop.plannedArrivalPosition, stop.getArrivalPosition()),
                plannedTimeString,
                predictedTimeString,
                formatTimeSpan(plannedTime, predictedTime))));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_public_leg_start,
                makeSpeakableLineName(line, publicLeg.destination, publicLeg.departureStop.location),
                locationName,
                platformForSpeakText(true, stop.plannedArrivalPosition, stop.getArrivalPosition()),
                timesForSpeakText(plannedTimeString, predictedTimeString, predictedTime.getTime() - plannedTime.getTime()),
                remainingTimeForSpeakTextAtEnd(timeLeftMs)));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                    R.string.navigation_event_notify_public_leg_start,
                    makeNotificationLineName(line, publicLeg.destination, publicLeg.departureStop.location),
                    locationName,
                    platformForNotificationMessage(stop.plannedArrivalPosition, stop.getArrivalPosition()),
                    plannedTimeString,
                    predictedTimeString,
                    formatTimeSpan(plannedTime, predictedTime),
                    remainingTimeForNotificationAtEnd(timeLeftMs))));
        }
    }

    private void addEventOutputPublicLegEnd(final Trip.Public publicLeg) {
        final String locationName = Formats.fullLocationName(publicLeg.arrival);
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.PUBLIC_LEG_END, context.getString(
                R.string.navigation_event_log_entry_public_leg_end,
                locationName)));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_public_leg_end,
                locationName));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                    R.string.navigation_event_notify_public_leg_end,
                    locationName)));
        }
    }

    private void addEventOutputReminder(
            final long nextEventTimeLeftMs,
            final TripRenderer.NotificationData newNotified,
            final Trip trip) {
        final int departureLegIndex = newNotified.publicDepartureLegIndex;
        final Trip.Public departureLeg = departureLegIndex < 0 ? null : (Trip.Public) trip.legs.get(departureLegIndex);
        if (newNotified.isTransfer) {
            if (departureLeg != null)
                addEventOutputTransferEndReminder(nextEventTimeLeftMs, departureLeg);
            else
                addEventOutputFinalTransferEndReminder(nextEventTimeLeftMs, trip.to);
        } else {
            final int arrivalLegIndex = newNotified.publicArrivalLegIndex;
            if (arrivalLegIndex >= 0) {
                addEventOutputPublicLegEndReminder(
                        nextEventTimeLeftMs,
                        (Trip.Public) trip.legs.get(arrivalLegIndex),
                        departureLeg);
            }
        }
    }

    private int getReminderNotificationEnabled(final long timeLeftMs) {
        if (!isEventNotificationsEnabled)
            return 0;
        final boolean is2Minutes = timeLeftMs < 4 * 60000;
        if (!prefs.getBoolean(is2Minutes
                        ? PREFS_KEY_NOTIFICATIONS_REMIND_2_MINUTES
                        : PREFS_KEY_NOTIFICATIONS_REMIND_6_MINUTES,
                false))
            return 0;
        return prefs.getBoolean(is2Minutes
                        ? PREFS_KEY_NOTIFICATIONS_REMIND_2_MINUTES_PREVIEW
                        : PREFS_KEY_NOTIFICATIONS_REMIND_6_MINUTES_PREVIEW,
                false)
                ? 3 : 1;
    }

    private void addEventOutputPublicLegEndReminder(
            final long timeLeftMs,
            final Trip.Public arrivalLeg,
            final Trip.Public departureLeg) {
        final TimeZoneSelector timeZoneSelector = getNetworkTimeZoneSelector();
        final Stop stop = arrivalLeg.arrivalStop;
        final PTDate predictedTime = stop.getArrivalTime(false);
        final PTDate plannedTime = stop.getArrivalTime(true);
        final String locationName = Formats.fullLocationName(arrivalLeg.arrival);
        final String plannedTimeString = Formats.formatTime(timeZoneSelector, plannedTime);
        final String predictedTimeString = Formats.formatTime(timeZoneSelector, predictedTime);
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.PUBLIC_LEG_END_REMINDER, context.getString(
                R.string.navigation_event_log_entry_public_leg_end_reminder,
                locationName,
                formatTimeSpan(timeLeftMs),
                platformForLogMessage(stop.plannedArrivalPosition, stop.getArrivalPosition()),
                predictedTimeString,
                formatTimeSpan(plannedTime, predictedTime))));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_public_leg_end_reminder,
                remainingTimeForSpeakText(timeLeftMs),
                locationName,
                platformForSpeakText(true, stop.plannedDeparturePosition, stop.getDeparturePosition()),
                timesForSpeakText(plannedTimeString, predictedTimeString, predictedTime.getTime() - plannedTime.getTime())));
        final int notificationEnabled = getReminderNotificationEnabled(timeLeftMs);
        if (notificationEnabled > 0) {
            newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                    R.string.navigation_event_notify_public_leg_end_reminder,
                    remainingTimeForNotificationText(timeLeftMs),
                    locationName,
                    platformForNotificationMessage(stop.plannedArrivalPosition, stop.getArrivalPosition()),
                    predictedTimeString,
                    formatTimeSpan(plannedTime, predictedTime))));
        }
        if (departureLeg != null)
            addEventOutputNextTransferPreview(departureLeg, arrivalLeg, notificationEnabled > 1);
    }

    private void addEventOutputTransferEndReminder(
            final long timeLeftMs,
            final Trip.Public toPublicLeg) {
        final TimeZoneSelector timeZoneSelector = getNetworkTimeZoneSelector();
        final Stop stop = toPublicLeg.departureStop;
        final PTDate predictedTime = stop.getDepartureTime(false);
        final PTDate plannedTime = stop.getDepartureTime(true);
        final Line line = toPublicLeg.line;
        final String plannedTimeString = Formats.formatTime(timeZoneSelector, plannedTime);
        final String predictedTimeString = Formats.formatTime(timeZoneSelector, predictedTime);
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.TRANSFER_END_REMINDER, context.getString(
                R.string.navigation_event_log_entry_transfer_end_reminder,
                formatTimeSpan(timeLeftMs),
                platformForLogMessage(stop.plannedDeparturePosition, stop.getDeparturePosition()),
                line.label,
                plannedTimeString,
                predictedTimeString,
                formatTimeSpan(plannedTime, predictedTime))));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_transfer_end_reminder,
                remainingTimeForSpeakText(timeLeftMs),
                makeSpeakableLineName(line, toPublicLeg.destination, stop.location),
                platformForSpeakText(true, stop.plannedDeparturePosition, stop.getDeparturePosition()),
                timesForSpeakText(plannedTimeString, predictedTimeString, predictedTime.getTime() - plannedTime.getTime())));
        final int notificationEnabled = getReminderNotificationEnabled(timeLeftMs);
        if (notificationEnabled > 0) {
            newEventNotifications.add(EventNotificationData.directionsEvent(context.getString(
                    R.string.navigation_event_notify_transfer_end_reminder,
                    remainingTimeForNotificationText(timeLeftMs),
                    makeNotificationLineName(line, toPublicLeg.destination, stop.location),
                    platformForNotificationMessage(stop.plannedDeparturePosition, stop.getDeparturePosition()),
                    predictedTimeString,
                    formatTimeSpan(plannedTime, predictedTime))));
        }
    }

    private void addEventOutputDepartureDelayChange(final Trip.Public publicLeg) {
        final TimeZoneSelector timeZoneSelector = getNetworkTimeZoneSelector();
        final Stop stop = publicLeg.departureStop;
        final Line line = publicLeg.line;
        final PTDate predictedTime = stop.getDepartureTime(false);
        final PTDate plannedTime = stop.getDepartureTime(true);
        final String plannedTimeString = Formats.formatTime(timeZoneSelector, plannedTime);
        final String predictedTimeString = Formats.formatTime(timeZoneSelector, predictedTime);
        final long timeLeftMs = predictedTime.getTime() - System.currentTimeMillis();
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.DEPARTURE_DELAY_CHANGE, context.getString(
                R.string.navigation_event_log_entry_departure_delay_change,
                line.label,
                Formats.fullLocationName(stop.location),
                formatTimeSpan(plannedTime, predictedTime),
                predictedTimeString)));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_departure_delay_change,
                timesForSpeakText(plannedTimeString, predictedTimeString, predictedTime.getTime() - plannedTime.getTime()),
                remainingTimeForSpeakTextAtEnd(timeLeftMs)));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.changeEvent(context.getString(
                    R.string.navigation_event_notify_departure_delay_change,
                    makeNotificationLineName(line, publicLeg.destination, stop.location),
                    Formats.fullLocationName(stop.location),
                    formatTimeSpan(plannedTime, predictedTime),
                    predictedTimeString,
                    remainingTimeForNotificationAtEnd(timeLeftMs))));
        }
    }

    private void addEventOutputArrivalDelayChange(
            final Trip.Public publicLeg) {
        final TimeZoneSelector timeZoneSelector = getNetworkTimeZoneSelector();
        final Stop stop = publicLeg.arrivalStop;
        final Line line = publicLeg.line;
        final PTDate predictedTime = stop.getArrivalTime(false);
        final PTDate plannedTime = stop.getArrivalTime(true);
        final String plannedTimeString = Formats.formatTime(timeZoneSelector, plannedTime);
        final String predictedTimeString = Formats.formatTime(timeZoneSelector, predictedTime);
        final long timeLeftMs = predictedTime.getTime() - System.currentTimeMillis();
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.ARRIVAL_DELAY_CHANGE, context.getString(
                R.string.navigation_event_log_entry_arrival_delay_change,
                line.label,
                Formats.fullLocationName(stop.location),
                formatTimeSpan(plannedTime, predictedTime),
                predictedTimeString)));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_arrival_delay_change,
                timesForSpeakText(plannedTimeString, predictedTimeString, predictedTime.getTime() - plannedTime.getTime()),
                remainingTimeForSpeakTextAtEnd(timeLeftMs)));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.changeEvent(context.getString(
                    R.string.navigation_event_notify_arrival_delay_change,
                    makeNotificationLineName(line, publicLeg.destination, publicLeg.departureStop.location),
                    Formats.fullLocationName(stop.location),
                    formatTimeSpan(plannedTime, predictedTime),
                    predictedTimeString,
                    remainingTimeForNotificationAtEnd(timeLeftMs))));
        }
    }

    private void addEventOutputDeparturePositionChange(
            final Trip.Public publicLeg,
            final Position prevPosition) {
        final TimeZoneSelector timeZoneSelector = getNetworkTimeZoneSelector();
        final Stop stop = publicLeg.departureStop;
        final Line line = publicLeg.line;
        final Position newPosition = stop.getDeparturePosition();
        final Position oldPosition = prevPosition != null ? prevPosition : stop.plannedDeparturePosition;
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.DEPARTURE_POSITION_CHANGE, context.getString(
                R.string.navigation_event_log_entry_departure_position_change,
                platformForLogMessage(oldPosition, newPosition),
                line.label,
                Formats.fullLocationName(stop.location),
                Formats.formatTime(timeZoneSelector, stop.getDepartureTime(true)))));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_departure_position_change,
                platformForSpeakText(true, oldPosition, newPosition)));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.changeEvent(context.getString(
                    R.string.navigation_event_notify_departure_position_change,
                    platformForNotificationMessage(oldPosition, newPosition),
                    makeNotificationLineName(line, publicLeg.destination, stop.location),
                    Formats.fullLocationName(stop.location),
                    Formats.formatTime(timeZoneSelector, stop.getDepartureTime(true)))));
        }
    }

    private void addEventOutputArrivalPositionChange(
            final Trip.Public publicLeg,
            final Position prevPosition) {
        final TimeZoneSelector timeZoneSelector = getNetworkTimeZoneSelector();
        final Stop stop = publicLeg.arrivalStop;
        final Position newPosition = stop.getArrivalPosition();
        final Position oldPosition = prevPosition != null ? prevPosition : stop.plannedArrivalPosition;
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.ARRIVAL_POSITION_CHANGE, context.getString(
                R.string.navigation_event_log_entry_arrival_position_change,
                platformForLogMessage(oldPosition, newPosition),
                publicLeg.line.label,
                Formats.fullLocationName(stop.location),
                Formats.formatTime(timeZoneSelector, stop.getArrivalTime(true)))));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_arrival_position_change,
                platformForSpeakText(true, oldPosition, newPosition)));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.changeEvent(context.getString(
                    R.string.navigation_event_notify_arrival_position_change,
                    platformForNotificationMessage(oldPosition, newPosition),
                    makeNotificationLineName(publicLeg.line, publicLeg.destination, publicLeg.departureStop.location),
                    Formats.fullLocationName(stop.location),
                    Formats.formatTime(timeZoneSelector, stop.getArrivalTime(true)))));
        }
    }

    private void addEventOutputNextTransferCritical() {
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.TRANSFER_CRITICAL, context.getString(
                R.string.navigation_event_log_entry_next_transfer_critical)));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_next_transfer_critical));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.changeEvent(context.getString(
                    R.string.navigation_event_notify_next_transfer_critical)));
        }
    }

    private void addEventOutputAnyTransferCritical() {
        newEventLogEntries.add(new EventLogEntry(EventLogEntry.Type.TRANSFER_CRITICAL, context.getString(
                R.string.navigation_event_log_entry_any_transfer_critical)));
        newSpeakTexts.add(context.getString(
                R.string.navigation_event_speak_any_transfer_critical));
        if (isEventNotificationsEnabled) {
            newEventNotifications.add(EventNotificationData.changeEvent(context.getString(
                    R.string.navigation_event_notify_any_transfer_critical)));
        }
    }

    private String formatTimeSpan(final Date earlier, final Date later) {
        return formatTimeSpan(later.getTime() - earlier.getTime(), "+");
    }

    private String formatTimeSpan(final long timeDiffMs) {
        return formatTimeSpan(timeDiffMs, "");
    }

    private String formatTimeSpan(final long timeDiffMs, final String positivePrefix) {
        long millis = timeDiffMs;
        final boolean isNegative = millis < 0;
        if (isNegative)
            millis = -millis;
        final long minutes = (millis / 1000 + 30) / 60;
        return (isNegative ? "-" : positivePrefix) + Long.toString(minutes) + " " + context.getString(R.string.time_minutes);
    }

    private static Map<Product, String> notificationProducts;

    private String makeNotificationLineName(final Line line, final Location destination, final Location refLocation) {
        if (notificationProducts == null) {
            notificationProducts = new HashMap<>();
            notificationProducts.put(Product.SUBWAY, context.getString(R.string.navigation_event_notify_product_subway));
            notificationProducts.put(Product.TRAM, context.getString(R.string.navigation_event_notify_product_tram));
            notificationProducts.put(Product.BUS, context.getString(R.string.navigation_event_notify_product_bus));
            notificationProducts.put(Product.FERRY, context.getString(R.string.navigation_event_notify_product_ferry));
            notificationProducts.put(Product.CABLECAR, context.getString(R.string.navigation_event_notify_product_cablecar));
            notificationProducts.put(Product.ON_DEMAND, context.getString(R.string.navigation_event_notify_product_ondemand));
            notificationProducts.put(Product.REPLACEMENT_SERVICE, context.getString(R.string.navigation_event_notify_product_replacement));
        }
        final String notificationDestination = Formats.fullLocationNameIfDifferentPlace(destination, refLocation);
        final String notificationProduct = notificationProducts.get(line.product);
        return (notificationProduct == null ? "" : notificationProduct + " ") + line.label + "->" + notificationDestination;
    }

    private static Map<Product, String> speakableProducts;

    private String makeSpeakableLineName(final Line line, final Location destination, final Location refLocation) {
        if (speakableProducts == null) {
            speakableProducts = new HashMap<>();
            speakableProducts.put(Product.SUBWAY, context.getString(R.string.navigation_event_speak_product_subway));
            speakableProducts.put(Product.TRAM, context.getString(R.string.navigation_event_speak_product_tram));
            speakableProducts.put(Product.BUS, context.getString(R.string.navigation_event_speak_product_bus));
            speakableProducts.put(Product.FERRY, context.getString(R.string.navigation_event_speak_product_ferry));
            speakableProducts.put(Product.CABLECAR, context.getString(R.string.navigation_event_speak_product_cablecar));
            speakableProducts.put(Product.ON_DEMAND, context.getString(R.string.navigation_event_speak_product_ondemand));
            speakableProducts.put(Product.REPLACEMENT_SERVICE, context.getString(R.string.navigation_event_speak_product_replacement));
        }
        final String lineName = line.label;
        if (lineName == null || lineName.length() <= 1)
            return lineName;
        final StringBuilder builder = new StringBuilder();
        char prevChar = lineName.charAt(0);
        builder.append(prevChar);
        for (int pos = 1; pos < lineName.length(); ++pos) {
            final char ch = lineName.charAt(pos);
            if (Character.isAlphabetic(ch)) {
                if (!Character.isSpaceChar(prevChar))
                    builder.append(Character.isAlphabetic(prevChar) ? '-' : ' ');
            } else {
                if (!Character.isSpaceChar(ch) && Character.isAlphabetic(prevChar))
                    builder.append(' ');
            }
            builder.append(ch);
            prevChar = ch;
        }
        final String speakableLineName = builder.toString();
        final String speakableDestination = Formats.fullLocationNameIfDifferentPlace(destination, refLocation);
        final String speakableProduct = speakableProducts.get(line.product);
        return context.getString(R.string.navigation_event_speak_linename,
                speakableProduct == null ? "" : speakableProduct + " ",
                speakableLineName,
                speakableDestination);
    }

}
