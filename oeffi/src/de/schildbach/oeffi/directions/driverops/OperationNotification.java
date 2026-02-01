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

package de.schildbach.oeffi.directions.driverops;

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
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.widget.RemoteViews;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.directions.navigation.NavigationAlarmManager;
import de.schildbach.oeffi.directions.navigation.Navigator;
import de.schildbach.oeffi.directions.navigation.TripRenderer;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.TimeZoneSelector;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.JourneyRef;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.provider.db.DbProvider;

public class OperationNotification {
    private static final String CHANNEL_ID_GUIDE = "operation";
    private static final String TAG_PREFIX_COMMON = OperationNotification.class.getName() + ":";
    private static final String TAG_PREFIX_GUIDE = TAG_PREFIX_COMMON + "guide:";
    private static final long KEEP_NOTIFICATION_FOR_MINUTES = 30;
    private static final int ACTION_REFRESH = 1;
    private static final int ACTION_DELETE = 2;
    private static final String INTENT_EXTRA_ACTION = OperationNotification.class.getName() + ".action";
    private static final String EXTRA_INTENTDATA = OperationNotification.class.getName() + ".intentdata";
    private static final String EXTRA_LASTNOTIFIED = OperationNotification.class.getName() + ".lastnotified";
    private static final String EXTRA_CONFIGURATION = OperationNotification.class.getName() + ".config";
    private static final String EXTRA_DATA = OperationNotification.class.getName() + ".data";
    public static final String ACTION_UPDATE_TRIGGER = OperationNotification.class.getName() + ".updatetrigger";

    private static final Logger log = LoggerFactory.getLogger(OperationNotification.class);

    private static boolean notificationChannelsCreated;

    public static void startup(final Context context) {
        createNotificationChannels(context);
        DeviceWakeupReceiver.register(context);
    }

    private static void createNotificationChannels(final Context context) {
        if (notificationChannelsCreated)
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createInstructionsChannel(context);
        }

        notificationChannelsCreated = true;
    }

    public static class DeviceWakeupReceiver extends BroadcastReceiver {
        public static void register(final Context context) {
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_SCREEN_ON); // screen being turned on
            intentFilter.addAction(Intent.ACTION_USER_PRESENT); // screen being unlocked
            ContextCompat.registerReceiver(context, new DeviceWakeupReceiver(), intentFilter, ContextCompat.RECEIVER_EXPORTED);
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            onDeviceWakingUp(context);
        }
    }

    private static void createInstructionsChannel(final Context context) {
        final CharSequence name = context.getString(R.string.operation_notification_channel_name);
        final String description = context.getString(R.string.operation_notification_channel_description);
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID_GUIDE, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getNotificationManager(context).createNotificationChannel(channel);
    }

    private static NotificationManagerCompat getNotificationManager(final Context context) {
        // return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return NotificationManagerCompat.from(context);
    }

    private static int getAudioStreamForSound(final int soundId) {
        return AudioManager.STREAM_NOTIFICATION;
    }

    private static final String[] REQUIRED_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? new String[]{
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

    private static Notification getActiveNotification(final Context context, final String notificationTag) {
        log.info("looking for active notifications for tag={}", notificationTag);
        StatusBarNotification latestStatusBarNotification = null;
        final List<StatusBarNotification> activeNotifications =
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

    public static long refreshAllGuides(final Context context) {
        final AtomicLong minRefreshAt = new AtomicLong(Long.MAX_VALUE);
        forAllActiveNotifications(context, "refresh", operationNotification -> {
            final long refreshAt = operationNotification.refresh();
            if (refreshAt > 0 && refreshAt < minRefreshAt.get())
                minRefreshAt.set(refreshAt);
            return true;
        });
        return minRefreshAt.get();
    }

    public static boolean isTripUnderOperation(final Context context, final String aTripId) {
        final AtomicBoolean found = new AtomicBoolean(false);
        forAllActiveNotifications(context, "getTripIds", operationNotification -> {
            final String tripId = operationNotification.intentData.trip.getUniqueId();
            if (aTripId.equals(tripId)) {
                found.set(true);
                return false;
            }
            return true;
        });
        return found.get();
    }

    private static void forAllActiveNotifications(
            final Context context,
            final String logText,
            final Function<OperationNotification, Boolean> action) {
        final List<StatusBarNotification> activeNotifications =
                getNotificationManager(context).getActiveNotifications();
        for (final StatusBarNotification statusBarNotification : activeNotifications) {
            final String tag = statusBarNotification.getTag();
            if (!tag.startsWith(TAG_PREFIX_GUIDE))
                continue;
            log.info("{} notification with tag={} posttime={}, id={}, key={}",
                    logText,
                    tag,
                    statusBarNotification.getPostTime(),
                    statusBarNotification.getId(),
                    statusBarNotification.getKey());
            final Notification notification = statusBarNotification.getNotification();
            final Bundle extras = notification.extras;
            if (extras == null)
                continue;
            if (!action.apply(new OperationNotification(notification, tag, null)))
                break;
        }
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
            new OperationNotification(intent).remove();
        });
    }

    private static void onDeviceWakingUp(final Context context) {
        // the screen has just either been turned on or unlocked
        final boolean isScreenOn = ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isInteractive();
        final boolean isUnlocked = !((KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();
        log.info("device waking up, the screen now is {} {}", isScreenOn ? "on" : "off", isUnlocked ? "unlocked" : "locked");
    }

    private static int uniqueCounter;

    public static final class Configuration implements Serializable {
        private static final long serialVersionUID = -3466636027523660100L;

        public long beginningOfOperation;

        public Configuration(final int numLegSlots) {
        }
    }

    public static final class ExtraData implements Serializable {
        private static final long serialVersionUID = -2218877489048279370L;

        public boolean refreshAllLegs;

        public ExtraData(final int numLegSlots) {
        }
    }

    private final Application context;
    private final String notificationTag;
    private final TripDetailsActivity.IntentData intentData;
    private TripRenderer.NotificationData lastNotified;
    private Configuration configuration;
    private final ExtraData extraData;

    public OperationNotification(final Intent intent) {
        this(null, null, new TripDetailsActivity.IntentData(intent));
    }

    private OperationNotification(
            final Notification aNotification,
            final String aNotificationTag,
            final TripDetailsActivity.IntentData aIntentData
    ) {
        this.context = Application.getInstance();
        final Notification notification;
        if (aNotification != null) {
            notificationTag = aNotificationTag;
            notification = aNotification;
        } else {
            notificationTag = TAG_PREFIX_GUIDE + aIntentData.trip.getUniqueId();
            notification = getActiveNotification(context, notificationTag);
        }
        if (notification == null) {
            this.intentData = aIntentData;
            final int numLegSlots = 1 + aIntentData.trip.legs.size();
            final Configuration conf = new Configuration(numLegSlots);
            conf.beginningOfOperation = System.currentTimeMillis();
            this.configuration = conf;
            this.extraData = new ExtraData(numLegSlots);
            this.lastNotified = null;
        } else {
            final Bundle extras = notification.extras;
            this.intentData = (TripDetailsActivity.IntentData) Objects.deserialize(extras.getByteArray(EXTRA_INTENTDATA));
            this.configuration = (Configuration) Objects.deserialize(extras.getByteArray(EXTRA_CONFIGURATION));
            this.extraData = (ExtraData) Objects.deserialize(extras.getByteArray(EXTRA_DATA));
            this.lastNotified = (TripRenderer.NotificationData) Objects.deserialize(extras.getByteArray(EXTRA_LASTNOTIFIED));
        }

        final StringBuilder b = new StringBuilder();
        for (final Trip.Leg leg : this.intentData.trip.legs) {
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
        log.info("OPERATION for TRIP: {}", b);
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
            new OperationNotification(intent).internUpdateFromForeground(trip, configuration);
            if (doneListener != null)
                doneListener.run();
        });
    }

    private void internUpdateFromForeground(final Trip newTrip, final Configuration newConfiguration) {
        if (newConfiguration != null)
            this.configuration = newConfiguration;
        final Trip trip = newTrip != null ? newTrip : getTrip();
        update(trip);
    }

//    static Long pppp;

    @SuppressLint("ScheduleExactAlarm")
    private void update(
            final Trip aTrip) {
        final TimeZoneSelector systemTimeZoneSelector = Application.getInstance().getSystemTimeZoneSelector();
        final Trip trip = aTrip != null ? aTrip : getTrip();
        final Date tripUpdatedAtDate = trip.updatedAt;
        log.info("updating with {} trip updated at {}", aTrip != null ? "new" : "old", NavigationAlarmManager.LOG_TIME_FORMAT.format(tripUpdatedAtDate));
        final long tripUpdatedAt = tripUpdatedAtDate.getTime();
        final Date now = new Date();
        final long nowTime = now.getTime();
        final TripRenderer tripRenderer = new TripRenderer(null, trip, false, now);
        final Trip.Public operationLeg = trip.getFirstPublicLeg();
        long nextRefreshTimeMs;
        String nextRefreshTimeReason;
        final long nextTripReloadTimeMs;
        if (tripRenderer.nextEventEarliestTime != null) {
            if (tripRenderer.nextEventIsInitialIndividual) {
                final Trip.Leg firstLeg = trip.legs.isEmpty() ? null : trip.legs.get(0);
            } else if (tripRenderer.nextEventTypeIsPublic) {
            } else if (tripRenderer.currentLeg.transferTo != null) {
            } else {
            }
            final long timeLeft = tripRenderer.nextEventEarliestTime.getTime() - nowTime;
            if (timeLeft < 240000) {
                // last 4 minutes and after, 30 secs refresh interval
                nextRefreshTimeReason = String.format("#1, timeLeft=%d", timeLeft);
                nextRefreshTimeMs = nowTime + 30000;
                nextTripReloadTimeMs = tripUpdatedAt + 60000;
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
        }
//nextRefreshTimeMs = nowTime + 30000;
        final Date timeoutAt = new Date(trip.getLastArrivalTime().getTime() + KEEP_NOTIFICATION_FOR_MINUTES * 60000);
        final long duration = timeoutAt.getTime() - nowTime;
        if (duration <= 1000) {
            remove();
            return;
        }
        final RemoteViews notificationLayout = new RemoteViews(context.getPackageName(), R.layout.operation_notification);
        setupNotificationView(notificationLayout, tripRenderer, now);
        // final RemoteViews notificationLayoutExpanded = new RemoteViews(context.getPackageName(), R.layout.operation_notification);
        // setupNotificationView(context, notificationLayoutExpanded, tripRenderer, now, newNotified);
        notificationLayout.setOnClickPendingIntent(R.id.operation_notification_open_full,
                getPendingActivityIntent(OperationNavigatorActivity.DELETEREQUEST_NOT_REQUESTED,
                        null, trip));
        notificationLayout.setOnClickPendingIntent(R.id.operation_notification_status,
                getPendingActionIntent(ACTION_REFRESH, trip));

        final TripRenderer.NotificationData newNotified = tripRenderer.notificationData;
        boolean timeChanged = false;
        boolean posChanged = false;
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
        }
        final Trip.Public arrivalLeg = newNotified.publicArrivalLegIndex < 0 ? null :
                (Trip.Public) trip.legs.get(newNotified.publicArrivalLegIndex);
        final Trip.Public departureLeg = newNotified.publicDepartureLegIndex < 0 ? null :
                (Trip.Public) trip.legs.get(newNotified.publicDepartureLegIndex);
        if (lastNotified == null || newNotified.currentLegCIndex != lastNotified.currentLegCIndex) {
            if (lastNotified == null) {
                lastNotified = new TripRenderer.NotificationData();
            } else {
                log.info("switching leg from {} to {}", lastNotified.currentLegCIndex, newNotified.currentLegCIndex);
            }
            lastNotified.leftTimeReminded = Long.MAX_VALUE;
            lastNotified.eventTime = newNotified.eventTime; // was .plannedEventTime but next announcement tells the change anyways
            if (newNotified.currentLegCIndex >= 0) {
                // in public transport
                lastNotified.departurePosition = newNotified.plannedDeparturePosition;
            }
        }
        if (newNotified.departurePosition != null && !newNotified.departurePosition.equals(lastNotified.departurePosition)) {
            log.info("switching departure position from {} to {}", lastNotified.departurePosition, newNotified.departurePosition);
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

        log.info("timeChanged={}, posChanged={}", timeChanged, posChanged);

        if (nextTripReloadTimeMs > 0 && nextTripReloadTimeMs < nextRefreshTimeMs) {
            nextRefreshTimeReason = String.format("#10, nextRefreshTimeMs=%d, nextTripReloadTimeMs=%d", nextRefreshTimeMs, nextTripReloadTimeMs);
            nextRefreshTimeMs = nextTripReloadTimeMs;
        }

        newNotified.refreshNotificationRequiredAt = nextRefreshTimeMs;
        newNotified.refreshTripRequiredAt = nextTripReloadTimeMs;

        if (nextRefreshTimeMs > 0) {
            log.info("refreshing in {} secs at {} (reason: {}), trip reload at {}",
                    (nextRefreshTimeMs - nowTime) / 1000,
                    NavigationAlarmManager.LOG_TIME_FORMAT.format(nextRefreshTimeMs),
                    nextRefreshTimeReason,
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
        newExtraData.refreshAllLegs = true;

        extras.putByteArray(EXTRA_DATA, Objects.serialize(newExtraData));

        final TimeZoneSelector timeZoneSelector = getNetworkTimeZoneSelector();
        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID_GUIDE)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setGroup(notificationTag)
                .setSmallIcon(R.drawable.ic_oeffi_operations_grey600_36dp)
                .setColorized(true).setColor(context.getColor(R.color.bg_trip_details_public_now))
                .setSubText(context.getString(R.string.operation_notification_subtext,
                        operationLeg.line.label,
                        Formats.formatTime(timeZoneSelector,
                                operationLeg.departureStop.getDepartureTime(true))))
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
                .addAction(R.drawable.ic_clear_white_24dp, context.getString(R.string.operation_opennav_shownextevent),
                        getPendingActivityIntent(OperationNavigatorActivity.DELETEREQUEST_NOT_REQUESTED,
                                TripDetailsActivity.Page.NEXT_EVENT, trip))
                .addAction(R.drawable.ic_navigation_white_24dp, context.getString(R.string.operation_opennav_showtrip),
                        getPendingActivityIntent(OperationNavigatorActivity.DELETEREQUEST_NOT_REQUESTED,
                                TripDetailsActivity.Page.ITINERARY, trip))
                .addAction(R.drawable.ic_clear_white_24dp, context.getString(R.string.operation_stopnav_stop),
                        getPendingActivityIntent(OperationNavigatorActivity.DELETEREQUEST_ASK,
                                TripDetailsActivity.Page.ITINERARY, trip))
                .setSilent(true);

        final Notification notification = notificationBuilder.build();
        log.info("set notification with tag={}", notificationTag);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            getNotificationManager(context).notify(notificationTag, 0, notification);
        }

        lastNotified = newNotified;
    }

    public void remove() {
        getNotificationManager(context).cancel(notificationTag, 0);
    }

    private PendingIntent getPendingActivityIntent(
            final int deleteRequest,
            final TripDetailsActivity.Page setShowPage,
            final Trip trip) {
        final Intent intent = getActivityIntent(deleteRequest, setShowPage, trip);
        return PendingIntent.getActivity(context,
                deleteRequest + (setShowPage == null ? 0 : (setShowPage.pageNum << 3)),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Intent getActivityIntent(
            final int deleteRequest,
            final TripDetailsActivity.Page setShowPage,
            final Trip trip) {
        return OperationNavigatorActivity.buildStartIntent(
                context,
                intentData.network,
                trip != null ? trip : getTrip(),
                intentData.renderConfig,
                deleteRequest,
                setShowPage,
                null,
                false);
    }

    public static class ActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            NavigationAlarmManager.runOnHandlerThread(() -> {
                final OperationNotification operationNotification = new OperationNotification(intent);
                switch (intent.getIntExtra(INTENT_EXTRA_ACTION, 0)) {
                    case ACTION_REFRESH:
                        operationNotification.update(null);
                        break;
                    case ACTION_DELETE:
                        operationNotification.remove();
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
                newTrip = navigator.refresh(extraData.refreshAllLegs, now);
            } catch (IOException e) {
                log.error("error while refreshing trip", e);
            }
            if (newTrip != null) {
                update(newTrip);
                context.sendBroadcast(new Intent(ACTION_UPDATE_TRIGGER));
            } else {
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
        final int colorNormal = context.getColor(R.color.bg_level0_default);
        final int colorHighIfPublic = tripRenderer.nextEventTypeIsPublic ? colorHighlight : colorNormal;
        final int colorHighIfChangeover = tripRenderer.nextEventTypeIsPublic ? colorNormal : colorHighlight;
    }

    private TimeZoneSelector getNetworkTimeZoneSelector() {
        NetworkId network = getNetwork();
        if (network == null)
            network = context.prefsGetNetworkId();
        return context.getPreferredNetworkTimeZoneSelector(network);
    }
}
