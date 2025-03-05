package de.schildbach.oeffi.directions.navigation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.util.ClockUtils;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.ResourceUri;
import de.schildbach.pte.dto.Trip;

public class NavigationNotification {
    private static final int SOUND_ALARM;
    private static final int SOUND_REMIND_NORMAL;
    private static final int SOUND_REMIND_IMPORTANT;
    private static final int SOUND_REMIND_NEXTLEG;

    private static final boolean DEVELOPMENT_TEST_PHASE = true;

    static {
        if (DEVELOPMENT_TEST_PHASE) {
            SOUND_ALARM = R.raw.nav_lowvolume_alarm;
            SOUND_REMIND_NORMAL = R.raw.nav_lowvolume_remind_down;
            SOUND_REMIND_IMPORTANT = R.raw.nav_lowvolume_remind_downup;
            SOUND_REMIND_NEXTLEG = R.raw.nav_lowvolume_remind_up;
        } else {
            SOUND_ALARM = R.raw.nav_alarm;
            SOUND_REMIND_NORMAL = R.raw.nav_remind_down;
            SOUND_REMIND_IMPORTANT = R.raw.nav_remind_downup;
            SOUND_REMIND_NEXTLEG = R.raw.nav_remind_up;
        }
    }
    private static final String CHANNEL_ID = "navigation";
    private static final String TAG_PREFIX = NavigationNotification.class.getName() + ":";

    private static final long KEEP_NOTIFICATION_FOR_MINUTES = 30;
    private static final long REMINDER_FIRST_MS = 6 * 60 * 1000;
    private static final long REMINDER_SECOND_MS = 2 * 60 * 1000;
    private static final String INTENT_EXTRA_REOPEN = NavigationNotification.class.getName() + ".reopen";
    private static final String EXTRA_INTENTDATA = NavigationNotification.class.getName() + ".intentdata";
    private static final String EXTRA_LASTNOTIFIED = NavigationNotification.class.getName() + ".lastnotified";
    public static final String ACTION_UPDATE_TRIGGER = NavigationNotification.class.getName() + ".updatetrigger";
    private static final long[] VIBRATION_PATTERN_REMIND = { 0, 1000, 500, 1500 };
    private static final long[] VIBRATION_PATTERN_ALARM = { 0, 1000, 500, 1500, 1000, 1000, 500, 1500 };

    private static final Logger log = LoggerFactory.getLogger(NavigationNotification.class);

    private static boolean notificationChannelCreated;

    private static void createNotificationChannel(final Context context) {
        if (notificationChannelCreated)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.navigation_notification_channel_name);
            String description = context.getString(R.string.navigation_notification_channel_description);
            final NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(description);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
//            channel.enableVibration(true);
            channel.setVibrationPattern(VIBRATION_PATTERN_REMIND);
            channel.setSound(ResourceUri.fromResource(context, SOUND_REMIND_NORMAL),
                    new AudioAttributes.Builder().setUsage(getAudioUsageForSound(SOUND_REMIND_NORMAL)).build());
            getNotificationManager(context).createNotificationChannel(channel);
        }
        notificationChannelCreated = true;
    }

    private static NotificationManager getNotificationManager(final Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static int getAudioStreamForSound(final int soundId) {
        return AudioManager.STREAM_NOTIFICATION;
    }

    private static int getAudioUsageForSound(final int soundId) {
        return AudioAttributes.USAGE_NOTIFICATION_EVENT;
    }

    private static final String[] REQUIRED_PERMISSION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? new String[] {
            Manifest.permission.POST_NOTIFICATIONS,
//            Manifest.permission.SCHEDULE_EXACT_ALARM,
//            Manifest.permission.USE_EXACT_ALARM,
    } : new String[]{};

    public static boolean requestPermissions(final Activity activity, final int requestCode) {
        boolean all = true;
        for (String permission : REQUIRED_PERMISSION) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED)
                all = false;
        }
        if (all)
            return true;
        ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSION, requestCode);
        return false;
    }

    public static long refreshAll(final Context context) {
        final StatusBarNotification[] activeNotifications = getNotificationManager(context).getActiveNotifications();
        long minRefreshAt = Long.MAX_VALUE;
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            if (!statusBarNotification.getTag().startsWith(TAG_PREFIX))
                continue;
            final Notification notification = statusBarNotification.getNotification();
            final Bundle extras = notification.extras;
            if (extras == null)
                continue;
            final TripDetailsActivity.IntentData intentData =
                    (TripDetailsActivity.IntentData) Objects.deserialize(extras.getByteArray(EXTRA_INTENTDATA));
            final TripRenderer.NotificationData notificationData =
                    (TripRenderer.NotificationData) Objects.deserialize(extras.getByteArray(EXTRA_LASTNOTIFIED));
            final NavigationNotification navigationNotification = new NavigationNotification(context, intentData, notificationData);
            final long refreshAt = navigationNotification.refresh();
            if (refreshAt > 0 && refreshAt < minRefreshAt)
                minRefreshAt = refreshAt;
        }
        return minRefreshAt;
    }

    private final Context context;
    private final String notificationTag;
    private final TripDetailsActivity.IntentData intentData;
    private TripRenderer.NotificationData lastNotified;

    public NavigationNotification(
            final Context context,
            final Intent intent) {
        this(context, new TripDetailsActivity.IntentData(intent), null);
    }

    public NavigationNotification(
            final Context context,
            final TripDetailsActivity.IntentData intentData,
            final TripRenderer.NotificationData lastNotified) {
        this.context = context;
        this.intentData = intentData;
        notificationTag = TAG_PREFIX + intentData.trip.getUniqueId();
        this.lastNotified = lastNotified != null ? lastNotified : getLastNotified();
    }

    private TripRenderer.NotificationData getLastNotified() {
        final StatusBarNotification[] activeNotifications = getNotificationManager(context).getActiveNotifications();
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            final String tag = statusBarNotification.getTag();
            if (tag == null || !tag.equals(notificationTag))
                continue;
            final Notification notification = statusBarNotification.getNotification();
            final Bundle extras = notification.extras;
            if (extras == null)
                return null;
            return (TripRenderer.NotificationData) Objects.deserialize(extras.getByteArray(EXTRA_LASTNOTIFIED));
        }
        return null;
    }

    private void soundBeep(int beepType) {
        final ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
        toneGenerator.startTone(beepType);
        new Handler(Looper.getMainLooper()).postDelayed(() -> toneGenerator.release(), 1000);
    }

    @SuppressLint("ScheduleExactAlarm")
    private boolean update(final Trip trip) {
        createNotificationChannel(context);
        final Date now = new Date();
        final long nowTime = now.getTime();
        final TripRenderer tripRenderer = new TripRenderer(trip, false, now);
        long nextRefreshTimeMs;
        if (tripRenderer.nextEventEarliestTime != null) {
            final long timeLeft = tripRenderer.nextEventEarliestTime.getTime() - nowTime;
            if (timeLeft < 240000) {
                // last 4 minutes and after, 30 secs refresh interval
                nextRefreshTimeMs = nowTime + 30000;
            } else if (timeLeft < 600000) {
                // last 10 minutes and after, 60 secs refresh interval
                nextRefreshTimeMs = nowTime + 60000;
            } else {
                // approaching, refresh after 25% of the remaining time
                nextRefreshTimeMs = nowTime + timeLeft / 4;
            }
        } else {
            final long timeOver = nowTime - trip.getLastArrivalTime().getTime();
            if (timeOver < 300000) {
                // max 5 minutes after the trip, 60 secs refresh interval
                nextRefreshTimeMs = nowTime + 60000;
            } else {
                // no refresh
                nextRefreshTimeMs = 0;
            }
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

        final TripRenderer.NotificationData newNotified = tripRenderer.notificationData;
        boolean timeChanged = false;
        boolean posChanged = false;
        int reminder = 0;
        final long nextEventTimeLeftTo10MinsBoundaryMs = nowTime
                + tripRenderer.nextEventTimeLeftMs - (tripRenderer.nextEventTimeLeftMs / 600000) * 600000;
        if (nextEventTimeLeftTo10MinsBoundaryMs < nextRefreshTimeMs)
            nextRefreshTimeMs = nextEventTimeLeftTo10MinsBoundaryMs;
        if (lastNotified == null || newNotified.legIndex != lastNotified.legIndex) {
            if (lastNotified == null)
                log.info("first notification !!");
            else
                log.info("switching leg from {} to {}", lastNotified.legIndex, newNotified.legIndex);
            if (lastNotified != null)
                reminder = SOUND_REMIND_NEXTLEG;
            lastNotified = new TripRenderer.NotificationData();
            lastNotified.legIndex = newNotified.legIndex;
            lastNotified.leftTimeReminded = Long.MAX_VALUE;
            lastNotified.eventTime = newNotified.plannedEventTime;
            lastNotified.position = newNotified.plannedPosition;
        }
        if (newNotified.position != null && !newNotified.position.equals(lastNotified.position)) {
            log.info("switching position from {} to {}", lastNotified.position, newNotified.position);
            posChanged = true;
        }
        if (newNotified.eventTime != null && lastNotified.eventTime != null) {
            final long diff = Math.abs(newNotified.eventTime.getTime() - lastNotified.eventTime.getTime());
            if (diff < 180000) {
                log.info("timediff = {} keeping last time", diff);
                newNotified.eventTime = lastNotified.eventTime;
            } else {
                log.info("timediff = {} accepting new time", diff);
                timeChanged = true;
            }
        }
        newNotified.leftTimeReminded = lastNotified.leftTimeReminded;
        final long nextEventTimeLeftMs = tripRenderer.nextEventTimeLeftMs;
        long nextReminderTimeMs = 0;
        if (tripRenderer.currentLeg != null) {
            if (nextEventTimeLeftMs < REMINDER_SECOND_MS + 20000) {
                log.info("next event {} < 2 mins : {}", nextEventTimeLeftMs, lastNotified.leftTimeReminded);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs;
                if (lastNotified.leftTimeReminded > REMINDER_SECOND_MS) {
                    log.info("reminding 2 mins = {}", nextReminderTimeMs);
                    reminder = SOUND_REMIND_IMPORTANT;
                    newNotified.leftTimeReminded = REMINDER_SECOND_MS;
                }
            } else if (nextEventTimeLeftMs < REMINDER_FIRST_MS + 20000) {
                log.info("next event {} < 6 mins : {}", nextEventTimeLeftMs, lastNotified.leftTimeReminded);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs - REMINDER_SECOND_MS;
                if (lastNotified.leftTimeReminded > REMINDER_FIRST_MS) {
                    log.info("reminding 6 mins = {}", nextReminderTimeMs);
                    reminder = SOUND_REMIND_NORMAL;
                    newNotified.leftTimeReminded = REMINDER_FIRST_MS;
                }
            } else {
                log.info("next event {} > 6 mins : {}", nextEventTimeLeftMs, lastNotified.leftTimeReminded);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs - REMINDER_FIRST_MS;
                if (lastNotified.leftTimeReminded <= REMINDER_SECOND_MS) {
                    log.info("resetting");
                    newNotified.leftTimeReminded = Long.MAX_VALUE;
                }
            }
            if (nextReminderTimeMs > 0 && nextReminderTimeMs < nextRefreshTimeMs + 20000)
                nextRefreshTimeMs = nextReminderTimeMs;
        }

        log.info("timeChanged={}, posChanged={} reminder={}", timeChanged, posChanged, reminder);

        final Bundle extras = new Bundle();
        extras.putByteArray(EXTRA_INTENTDATA, Objects.serialize(intentData));
        extras.putByteArray(EXTRA_LASTNOTIFIED, Objects.serialize(newNotified));

        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_MAX)
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
                .setCustomContentView(notificationLayout)
                // .setCustomBigContentView(notificationLayoutExpanded)
                .setContentIntent(getPendingActivityIntent(false, true))
                //.setDeleteIntent(getPendingActivityIntent(context, true))
                .setDeleteIntent(getPendingDeleteIntent(true))
                .setAutoCancel(false)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setWhen(nowTime)
                .setTimeoutAfter(duration)
                .setExtras(extras)
                .addAction(R.drawable.ic_clear_white_24dp, context.getString(R.string.navigation_stopnav_stop),
                        getPendingActivityIntent(true, false)) // getPendingDeleteIntent(context, false))
                .addAction(R.drawable.ic_navigation_white_24dp, context.getString(R.string.navigation_stopnav_showtrip),
                        getPendingActivityIntent(false, false));

        if (timeChanged || posChanged) {
            notificationBuilder.setSilent(true);
        } else if (reminder == SOUND_REMIND_NORMAL) {
            notificationBuilder
                    .setSilent(false)
                    .setVibrate(VIBRATION_PATTERN_REMIND)
                    .setSound(ResourceUri.fromResource(context, reminder), getAudioStreamForSound(reminder));
        } else {
            notificationBuilder.setSilent(true);
        }

        final Notification notification = notificationBuilder.build();
        getNotificationManager(context).notify(notificationTag, 0, notification);

        if (timeChanged || posChanged) {
            final Ringtone alarmTone = RingtoneManager.getRingtone(context, ResourceUri.fromResource(context, SOUND_ALARM));
            alarmTone.setAudioAttributes(new AudioAttributes.Builder().setUsage(getAudioUsageForSound(SOUND_ALARM)).build());
            alarmTone.play();
            ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(VIBRATION_PATTERN_ALARM, -1);
        } else if (reminder != 0 && reminder != SOUND_REMIND_NORMAL) {
            final Ringtone alarmTone = RingtoneManager.getRingtone(context, ResourceUri.fromResource(context, reminder));
            alarmTone.setAudioAttributes(new AudioAttributes.Builder().setUsage(getAudioUsageForSound(reminder)).build());
            alarmTone.play();
            ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(VIBRATION_PATTERN_REMIND, -1);
        }

        newNotified.refreshRequiredAt = nextRefreshTimeMs;
        lastNotified = newNotified;

        if (nextRefreshTimeMs > 0) {
            log.info("refreshing in {} secs at {} reminder at {}",
                    (nextRefreshTimeMs - nowTime) / 1000,
                    NavigationAlarmManager.LOG_TIME_FORMAT.format(nextRefreshTimeMs),
                    NavigationAlarmManager.LOG_TIME_FORMAT.format(nextReminderTimeMs));
        } else {
            log.info("stop refreshing");
        }

        return timeChanged || posChanged || reminder != 0;
    }

    public void updateFromForeground(final Trip newTrip) {
        update(newTrip);
        final long refreshAt = lastNotified.refreshRequiredAt;
        if (refreshAt > 0)
            NavigationAlarmManager.getInstance().start(refreshAt);
    }

    public void remove() {
        getNotificationManager(context).cancel(notificationTag, 0);
    }

    private PendingIntent getPendingActivityIntent(final boolean deleteRequest, final boolean showNextEvent) {
        final Intent intent = TripNavigatorActivity.buildStartIntent(
                context, intentData.network, intentData.trip, intentData.renderConfig,
                deleteRequest, showNextEvent);
        return PendingIntent.getActivity(context,
                (deleteRequest ? 1 : 0) + (showNextEvent ? 2 : 0),
                intent, PendingIntent.FLAG_IMMUTABLE);
    }

    public static class DeleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final NavigationNotification navigationNotification = new NavigationNotification(context, intent);
            if (intent.getBooleanExtra(INTENT_EXTRA_REOPEN, false)) {
                navigationNotification.update(navigationNotification.intentData.trip);
            } else {
                navigationNotification.remove();
            }
        }
    }

    private PendingIntent getPendingDeleteIntent(final boolean reopen) {
        final Intent intent = new Intent(context, DeleteReceiver.class);
        intent.setData(new Uri.Builder()
                .scheme("data")
                .authority(DeleteReceiver.class.getName())
                .path(intentData.network.name() + "/" + intentData.trip.getUniqueId())
                .build());
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_NETWORK, intentData.network);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_TRIP, intentData.trip);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_RENDERCONFIG, intentData.renderConfig);
        intent.putExtra(INTENT_EXTRA_REOPEN, reopen);
        return PendingIntent.getBroadcast(context, (reopen ? 1 : 0), intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private long refresh() {
        final long now = new Date().getTime();
        final long refreshRequiredAt = lastNotified.refreshRequiredAt;
        if (now < refreshRequiredAt)
            return refreshRequiredAt; // ignore multiple alarms in short time
        Trip newTrip;
        try {
            final Navigator navigator = new Navigator(intentData.network, intentData.trip);
            newTrip = navigator.refresh();
        } catch (IOException e) {
            newTrip = null;
        }
        if (newTrip != null) {
            final boolean alarmPlayed = update(newTrip);
            context.sendBroadcast(new Intent(ACTION_UPDATE_TRIGGER));
            if (!alarmPlayed)
                soundBeep(ToneGenerator.TONE_PROP_BEEP2);
        } else {
            soundBeep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD);
            update(intentData.trip);
        }
        return lastNotified.refreshRequiredAt;
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
        remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_positions, colorHighIfChangeover);
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

        String valueStr = tripRenderer.nextEventTimeLeftValue;
        if (valueStr == null) {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time, View.GONE);
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_finished, View.VISIBLE);
            remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_finished, colorHighlight);
            remoteViews.setTextColor(R.id.navigation_notification_next_event_finished, context.getColor(R.color.fg_significant));
        } else {
            final long minsLeft = tripRenderer.nextEventTimeLeftMs / 60000;
            if (minsLeft < 10) {
                if (TripRenderer.NO_TIME_LEFT_VALUE.equals(valueStr))
                    valueStr = context.getString(R.string.directions_trip_details_next_event_no_time_left);
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_value, View.VISIBLE);
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_chronometer, View.GONE);
                remoteViews.setTextViewText(R.id.navigation_notification_next_event_time_value, valueStr);
                remoteViews.setTextColor(R.id.navigation_notification_next_event_time_value,
                        context.getColor(tripRenderer.nextEventTimeLeftCritical ? R.color.fg_arrow : R.color.fg_significant));
            } else {
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_value, View.GONE);
                remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_chronometer, View.VISIBLE);
                final String format;
                if (minsLeft < 60)
                    format = "%1$.2s";
                else if (minsLeft < 600)
                    format = "%1$.4s";
                else
                    format = "%1$.5s";
                remoteViews.setChronometer(R.id.navigation_notification_next_event_time_chronometer,
                        ClockUtils.clockToElapsedTime(tripRenderer.nextEventEstimatedTime.getTime()),
                        format, true);
                remoteViews.setChronometerCountDown(R.id.navigation_notification_next_event_time_chronometer, true);
                remoteViews.setTextColor(R.id.navigation_notification_next_event_time_chronometer,
                        context.getColor(tripRenderer.nextEventTimeLeftCritical ? R.color.fg_arrow : R.color.fg_significant));
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

        remoteViews.setViewVisibility(R.id.navigation_notification_next_event_positions,
                tripRenderer.nextEventPositionsAvailable ? View.VISIBLE : View.GONE);

        if (tripRenderer.nextEventArrivalPosName != null) {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_position_from, View.VISIBLE);
            remoteViews.setTextViewText(R.id.navigation_notification_next_event_position_from, tripRenderer.nextEventArrivalPosName);
        } else {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_position_from, View.GONE);
        }

        if (tripRenderer.nextEventDeparturePosName != null) {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_position_to, View.VISIBLE);
            remoteViews.setTextViewText(R.id.navigation_notification_next_event_position_to, tripRenderer.nextEventDeparturePosName);
            remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_position_to,
                    context.getColor(tripRenderer.nextEventDeparturePosChanged ? R.color.bg_position_changed : R.color.bg_position));
        } else {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_position_to, View.GONE);
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
                                ? R.color.fg_arrow
                                : R.color.fg_significant));

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
    }

    private static void remoteViewsSetBackgroundColor(final RemoteViews remoteViews, int viewId, int color) {
        remoteViews.setInt(viewId, "setBackgroundColor", color);
    }
}
