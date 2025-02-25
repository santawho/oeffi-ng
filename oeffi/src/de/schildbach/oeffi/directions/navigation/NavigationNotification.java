package de.schildbach.oeffi.directions.navigation;

import static android.media.AudioAttributes.USAGE_ALARM;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
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
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Trip;

public class NavigationNotification {
    private static final String CHANNEL_ID = "navigation";
    private static final long KEEP_NOTIFICATION_FOR_MINUTES = 30;
    private static final long REMINDER_FIRST_MS = 6 * 60 * 1000;
    private static final long REMINDER_SECOND_MS = 2 * 60 * 1000;
    private static final String INTENT_EXTRA_REOPEN = NavigationNotification.class.getName() + ".reopen";
    private static final String EXTRA_LASTNOTIFIED = NavigationNotification.class.getName() + ".lastnotified";
    public static final String ACTION_UPDATE_TRIGGER = NavigationNotification.class.getName() + ".updatetrigger";
    private static final long[] VIBRATION_PATTERN = { 200, 200, 500, 200, 200 };
    private static Uri ALARM_SOUND_URI(final Context context) {
        return Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.nav_alarm);
    }

    private static final Logger log = LoggerFactory.getLogger(NavigationNotification.class);

    private static void createNotificationChannel(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.navigation_notification_channel_name);
            String description = context.getString(R.string.navigation_notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            final NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.setVibrationPattern(VIBRATION_PATTERN);
            channel.setSound(ALARM_SOUND_URI(context), new AudioAttributes.Builder().setUsage(USAGE_ALARM).build());
            getNotificationManager(context).createNotificationChannel(channel);
        }
    }

    private static NotificationManager getNotificationManager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static AlarmManager getAlarmManager(Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
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

    private final TripDetailsActivity.IntentData intentData;
    private final String notificationTag;

    public NavigationNotification(final Context context, final Trip trip, final Intent intent) {
        notificationTag = trip.getUniqueId();
        intentData = new TripDetailsActivity.IntentData(intent);
    }

    private Notification findActiveNotification(final Context context) {
        final StatusBarNotification[] activeNotifications = getNotificationManager(context).getActiveNotifications();
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            final String tag = statusBarNotification.getTag();
            if (tag == null || !tag.equals(notificationTag))
                continue;
            return statusBarNotification.getNotification();
        }
        return null;
    }

    private TripRenderer.NotificationData getLastNotified(final Context context) {
        final Notification activeNotification = findActiveNotification(context);
        if (activeNotification != null) {
            final Bundle extras = activeNotification.extras;
            if (extras != null) {
                return (TripRenderer.NotificationData) Objects.deserialize(extras.getByteArray(EXTRA_LASTNOTIFIED));
            }
        }
        return null;
    }

    private void soundBeep() {
        final ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2);
        new Handler(Looper.getMainLooper()).postDelayed(() -> toneGenerator.release(), 1000);
    }

    @SuppressLint("ScheduleExactAlarm")
    public void update(final Context context, final Trip trip, final boolean foreGround) {
        createNotificationChannel(context);
        final Date now = new Date();
        final long nowTime = now.getTime();
        final TripRenderer tripRenderer = new TripRenderer(trip, false, now);
        long nextRefreshTimeMs;
        Date timeoutAt;
        if (tripRenderer.nextEventEarliestTime != null) {
            final long timeLeft = tripRenderer.nextEventEarliestTime.getTime() - nowTime;
            if (timeLeft < 240000) {
                // last 4 minutes and after, 30 secs refresh interval
                nextRefreshTimeMs = nowTime + 30000;
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
//nextRefreshTime = nowTime + 30000;
        timeoutAt = new Date(trip.getLastArrivalTime().getTime() + KEEP_NOTIFICATION_FOR_MINUTES * 60000);
        final RemoteViews notificationLayout = new RemoteViews(context.getPackageName(), R.layout.navigation_notification);
        setupNotificationView(context, notificationLayout, tripRenderer, now);
        // final RemoteViews notificationLayoutExpanded = new RemoteViews(context.getPackageName(), R.layout.navigation_notification);
        // setupNotificationView(context, notificationLayoutExpanded, tripRenderer, now, newNotified);

        final TripRenderer.NotificationData newNotified = tripRenderer.notificationData;
        boolean changes = false;
        boolean reminder = false;
        long nextReminderTimeMs = 0;
        final long nextEventTimeLeftMs = tripRenderer.nextEventTimeLeftMs;
        final TripRenderer.NotificationData lastNotified = getLastNotified(context);
        if (lastNotified != null) {
            if (newNotified.legIndex != lastNotified.legIndex) {
                log.info("switching leg from {} to {}", lastNotified.legIndex, newNotified.legIndex);
                changes = true;
            }
            if (newNotified.position != null && !newNotified.position.equals(lastNotified.position)) {
                log.info("switching position from {} to {}", lastNotified.position, newNotified.position);
                changes = true;
            }
            if (lastNotified.eventTime != null) {
                final long diff = Math.abs(newNotified.eventTime.getTime() - lastNotified.eventTime.getTime());
                if (diff < 120000) {
                    log.info("timediff = {} keeping last time", diff);
                    newNotified.eventTime = lastNotified.eventTime;
                } else {
                    log.info("timediff = {} accepting new time", diff);
                    changes = true;
                }
            }
            newNotified.leftTimeReminded = lastNotified.leftTimeReminded;
            if (nextEventTimeLeftMs < REMINDER_SECOND_MS) {
                log.info("next event {} < 2 mins : {}", nextEventTimeLeftMs, lastNotified.leftTimeReminded);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs;
                if (lastNotified.leftTimeReminded > REMINDER_SECOND_MS) {
                    log.info("reminding 2 mins = {}", REMINDER_SECOND_MS);
                    reminder = true;
                    newNotified.leftTimeReminded = REMINDER_SECOND_MS;
                }
            } else if (nextEventTimeLeftMs < REMINDER_FIRST_MS) {
                log.info("next event {} < 6 mins : {}", nextEventTimeLeftMs, lastNotified.leftTimeReminded);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs - REMINDER_SECOND_MS;
                if (lastNotified.leftTimeReminded > REMINDER_FIRST_MS) {
                    log.info("reminding 6 mins = {}", REMINDER_FIRST_MS);
                    reminder = true;
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
        } else {
            log.info("first notification !!");
            if (nextEventTimeLeftMs < REMINDER_SECOND_MS) {
                log.info("next event {} < 2 mins", nextEventTimeLeftMs);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs;
                reminder = true;
                newNotified.leftTimeReminded = REMINDER_SECOND_MS;
            } else if (nextEventTimeLeftMs < REMINDER_FIRST_MS) {
                log.info("next event {} < 6 mins", nextEventTimeLeftMs);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs - REMINDER_SECOND_MS;
                reminder = true;
                newNotified.leftTimeReminded = REMINDER_FIRST_MS;
            }  else {
                log.info("next event {} > 6 mins", nextEventTimeLeftMs);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs - REMINDER_FIRST_MS;
                newNotified.leftTimeReminded = Long.MAX_VALUE;
            }
        }
        if (nextReminderTimeMs < nextRefreshTimeMs + 20000)
            nextRefreshTimeMs = nextReminderTimeMs;

        final boolean playAlarm = reminder || changes; // && !foreGround
        log.info("changes = {}, reminder = {}, playAlarm = {}", changes, reminder, playAlarm);
        if (!playAlarm)
            soundBeep();

        final Bundle extras = new Bundle();
        extras.putByteArray(EXTRA_LASTNOTIFIED, Objects.serialize(newNotified));

        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
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
                .setContentIntent(getPendingActivityIntent(context, false, true))
                //.setDeleteIntent(getPendingActivityIntent(context, true))
                .setDeleteIntent(getPendingDeleteIntent(context, true))
                .setAutoCancel(false)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setWhen(nowTime)
                .setSilent(!playAlarm)
                .setExtras(extras)
                .addAction(R.drawable.ic_clear_white_24dp, context.getString(R.string.navigation_stopnav_stop),
                        getPendingActivityIntent(context, true, false)) // getPendingDeleteIntent(context, false))
                .addAction(R.drawable.ic_navigation_white_24dp, context.getString(R.string.navigation_stopnav_showtrip),
                        getPendingActivityIntent(context, false, false));

        if (playAlarm) {
            notificationBuilder.setSound(ALARM_SOUND_URI(context), AudioManager.STREAM_ALARM);
            notificationBuilder.setVibrate(VIBRATION_PATTERN);
        }

        if (timeoutAt != null) {
            final long duration = timeoutAt.getTime() - nowTime;
            if (duration <= 1000) {
                remove(context);
                return;
            }
            notificationBuilder.setTimeoutAfter(duration);
        }

        final Notification notification = notificationBuilder.build();
        getNotificationManager(context).notify(notificationTag, intentData.trip.getUniqueId().hashCode(), notification);

        if (nextRefreshTimeMs > 0) {
            final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            log.info("refreshing in {} secs at {} reminder at {}",
                    (nextRefreshTimeMs - nowTime) / 1000,
                    timeFormat.format(nextRefreshTimeMs),
                    timeFormat.format(nextReminderTimeMs));
            final AlarmManager alarmManager = getAlarmManager(context);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRefreshTimeMs, getPendingRefreshIntent(context));
            }
        } else {
            log.info("stop refreshing");
        }
    }

    public void remove(final Context context) {
        getNotificationManager(context).cancel(notificationTag, intentData.trip.getUniqueId().hashCode());
        getAlarmManager(context).cancel(getPendingRefreshIntent(context));
    }

    private PendingIntent getPendingActivityIntent(
            final Context context,
            final boolean deleteRequest,
            final boolean showNextEvent) {
        final Intent intent = TripNavigatorActivity.buildStartIntent(
                context, intentData.network, intentData.trip, intentData.renderConfig,
                deleteRequest, showNextEvent);
        int requestCode = intentData.trip.getUniqueId().hashCode();
        if (deleteRequest)
            requestCode = requestCode + 1;
        if (showNextEvent)
            requestCode = requestCode + 2;
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    public static class DeleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final TripDetailsActivity.IntentData intentData = new TripDetailsActivity.IntentData(intent);
            final NavigationNotification navigationNotification = new NavigationNotification(context, intentData.trip, intent);
            if (intent.getBooleanExtra(INTENT_EXTRA_REOPEN, false)) {
                navigationNotification.update(context, intentData.trip, true);
            } else {
                navigationNotification.remove(context);
            }
        }
    }

    private PendingIntent getPendingDeleteIntent(final Context context, final boolean reopen) {
        final Intent intent = new Intent(context, NavigationNotification.DeleteReceiver.class);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_NETWORK, intentData.network);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_TRIP, intentData.trip);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_RENDERCONFIG, intentData.renderConfig);
        intent.putExtra(INTENT_EXTRA_REOPEN, reopen);
        return PendingIntent.getBroadcast(context,
                intentData.trip.getUniqueId().hashCode() + 4 + (reopen ? 1 : 0),
                intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private void refresh(final Context context, final NetworkId network, final Trip oldTrip) throws IOException {
        final Navigator navigator = new Navigator(network, oldTrip);
        final Trip newTrip = navigator.refresh();
        if (newTrip != null) {
            update(context, newTrip, false);
            context.sendBroadcast(new Intent(ACTION_UPDATE_TRIGGER));
        }
    }

    public static class RefreshReceiver extends BroadcastReceiver {
        private Runnable navigationRefreshRunnable;
        private HandlerThread backgroundThread;
        private Handler backgroundHandler;

        public RefreshReceiver() {
            backgroundThread = new HandlerThread("Navigation.queryTripsThread", Process.THREAD_PRIORITY_BACKGROUND);
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final TripDetailsActivity.IntentData intentData = new TripDetailsActivity.IntentData(intent);
            final NavigationNotification navigationNotification = new NavigationNotification(context, intentData.trip, intent);
            if (navigationRefreshRunnable != null)
                return;

            navigationRefreshRunnable = () -> {
                try {
                    navigationNotification.refresh(context, intentData.network, intentData.trip);
                } catch (IOException e) {
                    log.error("error when refreshing trip", e);
                } finally {
                    navigationRefreshRunnable = null;
                }
            };
            backgroundHandler.post(navigationRefreshRunnable);
        }
    }

    private PendingIntent getPendingRefreshIntent(final Context context) {
        final Intent intent = new Intent(context, NavigationNotification.RefreshReceiver.class);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_NETWORK, intentData.network);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_TRIP, intentData.trip);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_RENDERCONFIG, intentData.renderConfig);
        return PendingIntent.getBroadcast(context, intentData.trip.getUniqueId().hashCode() + 8, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private void setupNotificationView(
            final Context context, final RemoteViews remoteViews,
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
        if (TripRenderer.NO_TIME_LEFT_VALUE.equals(valueStr))
            valueStr = context.getString(R.string.directions_trip_details_next_event_no_time_left);
        remoteViews.setTextViewText(R.id.navigation_notification_next_event_time_value, valueStr);
        remoteViews.setTextColor(R.id.navigation_notification_next_event_time_value,
                context.getColor(tripRenderer.nextEventTimeLeftCritical ? R.color.fg_arrow : R.color.fg_significant));
        remoteViews.setTextViewText(R.id.navigation_notification_next_event_time_unit, tripRenderer.nextEventTimeLeftUnit);
        remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_hourglass,
                tripRenderer.nextEventTimeHourglassVisible ? View.VISIBLE : View.GONE);
        if (tripRenderer.nextEventTimeLeftExplainStr != null) {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_explain, View.VISIBLE);
            remoteViews.setTextViewText(R.id.navigation_notification_next_event_time_explain, tripRenderer.nextEventTimeLeftExplainStr);
        } else {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_time_explain, View.GONE);
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

            if (tripRenderer.nextEventTransferWalkAvailable) {
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

            if (tripRenderer.nextEventTransferWalkTimeValue != null) {
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
