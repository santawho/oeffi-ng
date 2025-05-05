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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.util.ClockUtils;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.ResourceUri;
import de.schildbach.pte.DbWebProvider;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Trip;

public class NavigationNotification {
    private static final int SOUND_ALARM = R.raw.nav_alarm;
    private static final int SOUND_REMIND_VIA_NOTIFICATION = -1;
    private static final int SOUND_REMIND_NORMAL = R.raw.nav_remind_down;
    private static final int SOUND_REMIND_IMPORTANT = R.raw.nav_remind_downup;
    private static final int SOUND_REMIND_NEXTLEG = R.raw.nav_remind_up;

    private static int getActualSound(final int soundId) {
        int newId = soundId;
        if (prefs.getBoolean(Constants.PREFS_KEY_NAVIGATION_REDUCED_SOUNDS, false)) {
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

    private static final String CHANNEL_ID = "navigation";
    private static final String TAG_PREFIX = NavigationNotification.class.getName() + ":";

    private static final long KEEP_NOTIFICATION_FOR_MINUTES = 30;
    private static final long REMINDER_FIRST_MS = 6 * 60 * 1000;
    private static final long REMINDER_SECOND_MS = 2 * 60 * 1000;
    private static final int ACTION_REFRESH = 1;
    private static final int ACTION_DELETE = 2;
    private static final String INTENT_EXTRA_ACTION = NavigationNotification.class.getName() + ".action";
    private static final String EXTRA_INTENTDATA = NavigationNotification.class.getName() + ".intentdata";
    private static final String EXTRA_LASTNOTIFIED = NavigationNotification.class.getName() + ".lastnotified";
    private static final String EXTRA_CONFIGURATION = NavigationNotification.class.getName() + ".config";
    public static final String ACTION_UPDATE_TRIGGER = NavigationNotification.class.getName() + ".updatetrigger";
    private static final long[] VIBRATION_PATTERN_REMIND = { 0, 1000, 500, 1500 };
    private static final long[] VIBRATION_PATTERN_ALARM = { 0, 1000, 500, 1500, 1000, 1000, 500, 1500 };

    private static final Logger log = LoggerFactory.getLogger(NavigationNotification.class);

    private static boolean notificationChannelCreated;

    private static SharedPreferences prefs;

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
            if (SOUND_REMIND_VIA_NOTIFICATION >= 0) {
                final int usage = getAudioUsageForSound(SOUND_REMIND_VIA_NOTIFICATION, false);
                channel.setSound(ResourceUri.fromResource(context, SOUND_REMIND_VIA_NOTIFICATION),
                        new AudioAttributes.Builder().setUsage(usage).build());
            }
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

    private static int getAudioUsageForSound(final int soundId, boolean onRide) {
        int usage = AudioAttributes.USAGE_NOTIFICATION_EVENT;
        String prefKey = null;
        if (soundId == SOUND_REMIND_NORMAL
            || soundId == SOUND_REMIND_IMPORTANT
            || soundId == SOUND_ALARM) {
            prefKey = onRide
                    ? Constants.PREFS_KEY_NAVIGATION_SOUND_CHANNEL_RIDE
                    : Constants.PREFS_KEY_NAVIGATION_SOUND_CHANNEL_TRANSFER;
        } else if (soundId == SOUND_REMIND_NEXTLEG) {
            prefKey = Constants.PREFS_KEY_NAVIGATION_SOUND_CHANNEL_RIDE;
        }
        if (prefKey != null) {
            if (Constants.PREFS_KEY_NAVIGATION_SOUND_CHANNEL_RIDE.equals(prefKey))
                usage = AudioAttributes.USAGE_NOTIFICATION_EVENT;
            else if (Constants.PREFS_KEY_NAVIGATION_SOUND_CHANNEL_TRANSFER.equals(prefKey))
                usage = AudioAttributes.USAGE_ALARM;
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
                    context, intentData, configuration, notificationData);
            final long refreshAt = navigationNotification.refresh();
            if (refreshAt > 0 && refreshAt < minRefreshAt)
                minRefreshAt = refreshAt;
        }
        return minRefreshAt;
    }

    public static void remove(final Context context, final Intent intent) {
        NavigationAlarmManager.runOnHandlerThread(() -> {
            new NavigationNotification(context, intent).remove();
        });
    }

    public static final class Configuration implements Serializable {
        private static final long serialVersionUID = -3466636027523660100L;

        boolean soundEnabled;
    }

    private final Context context;
    private final String notificationTag;
    private final TripDetailsActivity.IntentData intentData;
    private TripRenderer.NotificationData lastNotified;
    private Configuration configuration;

    public NavigationNotification(
            final Context context,
            final Intent intent) {
        this(context, new TripDetailsActivity.IntentData(intent), null, null);
    }

    private NavigationNotification(
            final Context context,
            final TripDetailsActivity.IntentData intentData,
            final Configuration configuration,
            final TripRenderer.NotificationData lastNotified) {
        this.context = context;
        final Trip trip = intentData.trip;
        final String uniqueId = trip.getUniqueId();
        final StringBuilder b = new StringBuilder();
        for (Trip.Leg leg : trip.legs) {
            if (leg instanceof Trip.Public) {
                final Trip.Public publeg = (Trip.Public) leg;
                final DbWebProvider.DbWebJourneyRef journeyRef = (DbWebProvider.DbWebJourneyRef) publeg.journeyRef;
                b.append(",j=");
                b.append(journeyRef.journeyId);
                final Line line = journeyRef.line;
                b.append(",n=");
                b.append(line.network);
                b.append(",p=");
                b.append(line.product);
                b.append(",l=");
                b.append(line.label);
                b.append(",d=");
                b.append(publeg.departureStop.location.id);
            }
        }
        log.info("NOTIFICATION for TRIP: {}", b.toString());
        notificationTag = TAG_PREFIX + uniqueId;
        final Bundle extras = getActiveNotificationExtras();
        if (extras == null) {
            this.intentData = intentData;
            this.configuration = configuration != null ? configuration : new Configuration();
            this.lastNotified = lastNotified;
        } else {
            this.intentData = (TripDetailsActivity.IntentData) Objects.deserialize(extras.getByteArray(EXTRA_INTENTDATA));
            this.configuration = configuration != null ? configuration :
                    (Configuration) Objects.deserialize(extras.getByteArray(EXTRA_CONFIGURATION));
            this.lastNotified = lastNotified != null ? lastNotified :
                    (TripRenderer.NotificationData) Objects.deserialize(extras.getByteArray(EXTRA_LASTNOTIFIED));
        }
        if (NavigationNotification.prefs == null)
            NavigationNotification.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void updateFromForeground(
            final Context context, final Intent intent,
            final Trip trip, final Configuration configuration) {
        NavigationAlarmManager.runOnHandlerThread(() -> {
            new NavigationNotification(context, intent).updateFromForeground(trip, configuration);
        });
    }

    public void updateFromForeground(final Trip newTrip, final Configuration configuration) {
        if (configuration != null)
            this.configuration = configuration;
        update(newTrip);
        if (lastNotified != null) {
            final long refreshAt = lastNotified.refreshNotificationRequiredAt;
            if (refreshAt > 0)
                NavigationAlarmManager.getInstance().start(refreshAt);
        }
    }

    private Notification getActiveNotification() {
        log.info("looking for active notifications for tag={}", notificationTag);
        StatusBarNotification latestStatusBarNotification = null;
        final StatusBarNotification[] activeNotifications = getNotificationManager(context).getActiveNotifications();
        for (StatusBarNotification statusBarNotification : activeNotifications) {
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

    @SuppressLint("ScheduleExactAlarm")
    private boolean update(final Trip aTrip) {
        final Trip trip = aTrip != null ? aTrip : intentData.trip;
        log.info("updating with {} trip loaded at {}", aTrip != null ? "new" : "old", NavigationAlarmManager.LOG_TIME_FORMAT.format(trip.loadedAt));
        final long tripLoadedAt = trip.loadedAt.getTime();
        createNotificationChannel(context);
        final Date now = new Date();
        final long nowTime = now.getTime();
        final TripRenderer tripRenderer = new TripRenderer(null, trip, false, now);
        long nextRefreshTimeMs;
        long nextTripReloadTimeMs;
        if (tripRenderer.nextEventEarliestTime != null) {
            final long timeLeft = tripRenderer.nextEventEarliestTime.getTime() - nowTime;
            if (timeLeft < 240000) {
                // last 4 minutes and after, 30 secs refresh interval
                nextRefreshTimeMs = nowTime + 30000;
                nextTripReloadTimeMs = tripLoadedAt + 60000;
            } else if (timeLeft < 600000) {
                // last 10 minutes and after, 60 secs refresh interval
                nextRefreshTimeMs = nowTime + 60000;
                nextTripReloadTimeMs = tripLoadedAt + 120000;
            } else {
                // approaching, refresh after 25% of the remaining time
                nextRefreshTimeMs = nowTime + timeLeft / 4;
                nextTripReloadTimeMs = nextRefreshTimeMs;
            }
        } else {
            final long timeOver = nowTime - trip.getLastArrivalTime().getTime();
            if (timeOver < 300000) {
                // max 5 minutes after the trip, 60 secs refresh interval
                nextRefreshTimeMs = nowTime + 60000;
                nextTripReloadTimeMs = nextRefreshTimeMs;
            } else {
                // no refresh
                nextRefreshTimeMs = 0;
                nextTripReloadTimeMs = 0;
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
        notificationLayout.setOnClickPendingIntent(R.id.navigation_notification_open_full,
                getPendingActivityIntent(false, true, trip));
        notificationLayout.setOnClickPendingIntent(R.id.navigation_notification_next_event,
                getPendingActionIntent(ACTION_REFRESH, trip));

        final TripRenderer.NotificationData newNotified = tripRenderer.notificationData;
        boolean timeChanged = false;
        boolean posChanged = false;
        boolean nextTransferCriticalChanged = false;
        boolean anyTransferCriticalChanged = false;
        int reminderSoundId = 0;
        boolean onRide = tripRenderer.nextEventTypeIsPublic;
        final long nextEventTimeLeftMs = tripRenderer.nextEventTimeLeftMs;
        final long nextEventTimeLeftTo10MinsBoundaryMs = nowTime
                + nextEventTimeLeftMs - (nextEventTimeLeftMs / 600000) * 600000 + 2000;
        if (nextEventTimeLeftTo10MinsBoundaryMs < nextRefreshTimeMs)
            nextRefreshTimeMs = nextEventTimeLeftTo10MinsBoundaryMs;
        if (lastNotified == null || newNotified.currentLegIndex != lastNotified.currentLegIndex) {
            if (lastNotified == null) {
                log.info("first notification !!");
                lastNotified = new TripRenderer.NotificationData();
                lastNotified.currentLegIndex = -1;
                lastNotified.publicDepartureLegIndex = -1;
                lastNotified.publicArrivalLegIndex = -1;
            } else {
                log.info("switching leg from {} to {}", lastNotified.currentLegIndex, newNotified.currentLegIndex);
                reminderSoundId = SOUND_REMIND_NEXTLEG;
            }
            lastNotified.leftTimeReminded = Long.MAX_VALUE;
            lastNotified.eventTime = newNotified.plannedEventTime;
            if (newNotified.publicDepartureLegIndex != lastNotified.publicDepartureLegIndex)
                lastNotified.departurePosition = newNotified.plannedDeparturePosition;
            if (newNotified.publicArrivalLegIndex != lastNotified.publicArrivalLegIndex)
                lastNotified.nextTransferCritical = false;
        }
        if (newNotified.departurePosition != null && !newNotified.departurePosition.equals(lastNotified.departurePosition)) {
            log.info("switching position from {} to {}", lastNotified.departurePosition, newNotified.departurePosition);
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
            } else {
                log.info("time not changed: leftSecs={}, diffSecs={}, keeping new time", leftSecs, diffSecs);
                newNotified.eventTime = lastNotified.eventTime;
            }
        }
        if (newNotified.nextTransferCritical != lastNotified.nextTransferCritical) {
            log.info("transferCritical switching to {}", newNotified.nextTransferCritical);
            nextTransferCriticalChanged = newNotified.nextTransferCritical;
        }
        if (!newNotified.transfersCritical.equals(lastNotified.transfersCritical)) {
            final String lastTransfersCritical = lastNotified.transfersCritical;
            final String nextTransfersCritical = newNotified.transfersCritical;
            log.info("criticalTransfers switching from {} to {}", lastTransfersCritical, nextTransfersCritical);
            final int length = nextTransfersCritical.length();
            if (lastTransfersCritical == null || length != lastTransfersCritical.length()) {
                for (int i = 0; i < length; i += 1) {
                    final char next = nextTransfersCritical.charAt(i);
                    if (next != '-') {
                        anyTransferCriticalChanged = true;
                        break;
                    }
                }
            } else {
                for (int i = 0; i < length; i += 1) {
                    final char next = nextTransfersCritical.charAt(i);
                    if (next != '-' && next != lastTransfersCritical.charAt(i)) {
                        anyTransferCriticalChanged = true;
                        break;
                    }
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
                    reminderSoundId = SOUND_REMIND_IMPORTANT;
                    newNotified.leftTimeReminded = REMINDER_SECOND_MS;
                }
            } else if (nextEventTimeLeftMs < REMINDER_FIRST_MS + 20000) {
                log.info("next event {} < 6 mins : already reminded {}", nextEventTimeLeftMs, lastNotified.leftTimeReminded);
                nextReminderTimeMs = nowTime + nextEventTimeLeftMs - REMINDER_SECOND_MS;
                if (lastNotified.leftTimeReminded > REMINDER_FIRST_MS) {
                    log.info("reminding 6 mins = {}", nextReminderTimeMs);
                    reminderSoundId = SOUND_REMIND_NORMAL;
                    newNotified.leftTimeReminded = REMINDER_FIRST_MS;
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
            if (nextReminderTimeMs > 0 && nextReminderTimeMs < nextRefreshTimeMs + 20000)
                nextRefreshTimeMs = nextReminderTimeMs;
        }

        final boolean anyChanges = timeChanged || posChanged || nextTransferCriticalChanged || anyTransferCriticalChanged;
        log.info("timeChanged={}, posChanged={} nextTransferCriticalChanged={} anyTransferCriticalChanged={} reminderSoundId={}",
                timeChanged, posChanged, nextTransferCriticalChanged, anyTransferCriticalChanged, reminderSoundId);

        if (nextTripReloadTimeMs > 0 && nextTripReloadTimeMs < nextRefreshTimeMs)
            nextRefreshTimeMs = nextTripReloadTimeMs;

        newNotified.refreshNotificationRequiredAt = nextRefreshTimeMs;
        newNotified.refreshTripRequiredAt = nextTripReloadTimeMs;

        if (nextRefreshTimeMs > 0) {
            log.info("refreshing in {} secs at {}, reminder at {}, trip reload at {}",
                    (nextRefreshTimeMs - nowTime) / 1000,
                    NavigationAlarmManager.LOG_TIME_FORMAT.format(nextRefreshTimeMs),
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
                // .setContentIntent(getPendingActivityIntent(false, true))
                .setContentIntent(getPendingActionIntent(ACTION_REFRESH, trip))
                //.setDeleteIntent(getPendingActivityIntent(context, true))
                .setDeleteIntent(getPendingActionIntent(ACTION_DELETE, trip))
                .setAutoCancel(false)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setWhen(nowTime)
                .setTimeoutAfter(duration)
                .setExtras(extras)
                .addAction(R.drawable.ic_clear_white_24dp, context.getString(R.string.navigation_opennav_shownextevent),
                        getPendingActivityIntent(false, true, trip))
                .addAction(R.drawable.ic_navigation_white_24dp, context.getString(R.string.navigation_opennav_showtrip),
                        getPendingActivityIntent(false, false, trip))
                .addAction(R.drawable.ic_clear_white_24dp, context.getString(R.string.navigation_stopnav_stop),
                        getPendingActivityIntent(true, false, trip));

        if (anyChanges) {
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
        getNotificationManager(context).notify(notificationTag, 0, notification);

        if (anyChanges) {
            playAlarmSoundAndVibration(-1, SOUND_ALARM, VIBRATION_PATTERN_ALARM, onRide);
        } else if (reminderSoundId != 0 && reminderSoundId != SOUND_REMIND_VIA_NOTIFICATION) {
            playAlarmSoundAndVibration(-1, reminderSoundId, VIBRATION_PATTERN_REMIND, onRide);
        }

        lastNotified = newNotified;
        return anyChanges || reminderSoundId != 0;
    }

    private void playAlarmSoundAndVibration(
            final int soundUsage,
            final int aSoundId,
            final long[] vibrationPattern,
            boolean onRide) {
        final int actualSoundId = configuration.soundEnabled ? getActualSound(aSoundId) : 0;
        final int actualUsage = soundUsage >= 0 ? soundUsage : getAudioUsageForSound(aSoundId, onRide);
        final NotificationSoundManager soundManager = NotificationSoundManager.getInstance();
        soundManager.playAlarmSoundAndVibration(actualUsage, actualSoundId, vibrationPattern);
    }

    private void playBeep(
            final int soundUsage,
            final int soundId) {
        if (prefs.getBoolean(Constants.PREFS_KEY_NAVIGATION_REFRESH_BEEP, true))
            playAlarmSoundAndVibration(soundUsage, soundId, null, false);
    }

    public void remove() {
        getNotificationManager(context).cancel(notificationTag, 0);
    }

    private PendingIntent getPendingActivityIntent(
            final boolean deleteRequest, final boolean showNextEvent,
            final Trip trip) {
        final Intent intent = TripNavigatorActivity.buildStartIntent(
                context, intentData.network, trip, intentData.renderConfig,
                deleteRequest, showNextEvent, false);
        return PendingIntent.getActivity(context,
                (deleteRequest ? 1 : 0) + (showNextEvent ? 2 : 0),
                intent, PendingIntent.FLAG_IMMUTABLE);
    }

    public static class ActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            NavigationAlarmManager.runOnHandlerThread(() -> {
                final NavigationNotification navigationNotification = new NavigationNotification(context, intent);
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

    private long refresh() {
        log.info("refreshing notification");
        final long now = new Date().getTime();
        final long refreshRequiredAt = lastNotified.refreshNotificationRequiredAt;
        if (now < refreshRequiredAt)
            return refreshRequiredAt; // ignore multiple alarms in short time
        Trip newTrip = null;
        if (lastNotified.refreshTripRequiredAt > 0 && now >= lastNotified.refreshTripRequiredAt) {
            try {
                log.info("refreshing trip");
                final Navigator navigator = new Navigator(intentData.network, intentData.trip);
                newTrip = navigator.refresh();
            } catch (IOException e) {
                log.error("error while refreshing trip", e);
            }
            if (newTrip != null) {
                final boolean alarmPlayed = update(newTrip);
                context.sendBroadcast(new Intent(ACTION_UPDATE_TRIGGER));
                if (!alarmPlayed) {
                    playBeep(AudioAttributes.USAGE_NOTIFICATION, R.raw.nav_refresh_beep);
                }
            } else {
                playBeep(AudioAttributes.USAGE_NOTIFICATION, R.raw.nav_refresh_error);
                update(null);
            }
        } else {
            update(null);
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
                    valueStr = context.getString(R.string.directions_trip_details_next_event_no_time_left);
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

        remoteViews.setViewVisibility(R.id.navigation_notification_next_event_positions,
                tripRenderer.nextEventPositionsAvailable ? View.VISIBLE : View.GONE);

        if (tripRenderer.nextEventArrivalPosName != null) {
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_position_from, View.VISIBLE);
            remoteViews.setTextViewText(R.id.navigation_notification_next_event_position_from, tripRenderer.nextEventArrivalPosName);
            remoteViewsSetBackgroundColor(remoteViews, R.id.navigation_notification_next_event_position_from,
                    context.getColor(tripRenderer.nextEventArrivalPosChanged ? R.color.bg_position_changed : R.color.bg_position));
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

        if (tripRenderer.nextEventStopChange) {
            final int iconId = tripRenderer.nextEventTransferIconId;
            remoteViews.setImageViewResource(R.id.navigation_notification_next_event_positions_walk_icon,
                    iconId != 0 ? iconId : R.drawable.ic_directions_walk_grey600_24dp);
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_positions_walk_icon, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.navigation_notification_next_event_positions_walk_arrow, View.VISIBLE);
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
    }

    private static void remoteViewsSetBackgroundColor(final RemoteViews remoteViews, int viewId, int color) {
        remoteViews.setInt(viewId, "setBackgroundColor", color);
    }
}
