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

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryStoredTripsProvider;
import de.schildbach.oeffi.preference.DriverModeAlarmFragment;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.TimeZoneSelector;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.PTDate;
import de.schildbach.pte.dto.Trip;

public class OperationAlarmManager {
    private static final Logger log = LoggerFactory.getLogger(OperationAlarmManager.class);

    private static final String DEFAULT_CHANNEL_ID = "operationalarm-default";
    private static final String TAG_PREFIX = OperationAlarmManager.class.getName() + ":";
    private static final String NOTIFICATION_TAG = TAG_PREFIX + "alarm";

    private static boolean notificationChannelCreated;

    public static void createNotificationChannels(final Context context) {
        if (notificationChannelCreated)
            return;

        final NotificationManager notificationManager = getNotificationManager(context);
        createNotificationChannel(DEFAULT_CHANNEL_ID,
                context.getString(R.string.drivermode_operationalarm_notification_default_channel_name),
                context.getString(R.string.drivermode_operationalarm_notification_default_channel_description),
                notificationManager);

        notificationChannelCreated = true;
    }

    private static void createNotificationChannel(
            final String channelId,
            final CharSequence name,
            final String description,
            final NotificationManager notificationManager) {
        final NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableLights(true);
        channel.enableVibration(true);
        channel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        notificationManager.createNotificationChannel(channel);
    }

    private static NotificationManager getNotificationManager(final Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static HandlerThread backgroundThread;
    private static Handler backgroundHandler;

    private Notification findNotificationByTag(final String tag) {
        final StatusBarNotification[] activeNotifications = getNotificationManager(context).getActiveNotifications();
        for (final StatusBarNotification statusBarNotification : activeNotifications) {
            if (tag.equals(statusBarNotification.getTag()))
                return statusBarNotification.getNotification();
        }
        return null;
    }

    public static void setupAlarm() {
        final Application application = Application.getInstance();
        final NetworkId networkId = application.getDefaultNetwork();
        new OperationAlarmManager(application).setupAlarm(networkId);
    }

    private final Context context;

    public OperationAlarmManager(final Context context) {
        this.context = context;

        if (backgroundThread == null) {
            backgroundThread = new HandlerThread("OpsAlarmThread", Process.THREAD_PRIORITY_BACKGROUND);
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }
    }

    public static void runOnHandlerThread(final Runnable runnable) {
        backgroundHandler.post(runnable);
    }

    public static class RefreshReceiver extends BroadcastReceiver {
        public static PendingIntent getPendingIntent(final Context context) {
            return PendingIntent.getBroadcast(context, 0,
                    new Intent(context, RefreshReceiver.class),
                    PendingIntent.FLAG_IMMUTABLE);
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            log.info("refresh alarm was fired");
            final OperationAlarmManager alarmManager = new OperationAlarmManager(context);
            runOnHandlerThread(() -> alarmManager.fireAlarm(new Date()));
        }
    }

    private PendingIntent getPendingRefreshIntent() {
        return RefreshReceiver.getPendingIntent(context);
    }

    public void setupAlarm(final NetworkId networkId) {
        if (!canScheduleExactAlarms())
            return;

        final int numberOfActiveNotifications = OperationNotification.getNumberOfActiveNotifications(context);
        if (numberOfActiveNotifications > 0)
            return;

        final Date now = new Date();
        final Trip trip = getNextScheduledOperation(networkId, now);
        final long alarmTime = getNextAlarmTime(trip, now);
        if (alarmTime == 0) {
            clearAlarm();
            return;
        }

        log.info("setting operations alarm at {}", new Date(alarmTime));
//alarmTime = now.getTime() + 10000L;

        final AlarmManager alarmManager = getSystemAlarmManager();
        final PendingIntent refreshIntent = getPendingRefreshIntent();
        alarmManager.cancel(refreshIntent);
        final PendingIntent showOperationsIntent = PendingIntent.getActivity(context, 99,
                OperationsActivity.buildStartIntent(context, null),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setAlarmClock(
                new AlarmManager.AlarmClockInfo(alarmTime, showOperationsIntent),
                refreshIntent);
    }

    public void clearAlarm() {
        final AlarmManager alarmManager = getSystemAlarmManager();
        final PendingIntent refreshIntent = getPendingRefreshIntent();
        alarmManager.cancel(refreshIntent);
    }

    private long getNextAlarmTime(final Trip trip, final Date now) {
        if (trip == null) {
            // no operations scheduled
            return 0;
        }

        final Trip.Public operationLeg = trip.getFirstPublicLeg();
        final PTDate departureTime = operationLeg.getDepartureTime(true);
        final long timeLeft = departureTime.getTime() - now.getTime();

        final List<DriverModeAlarmFragment.BreakDef> defs = DriverModeAlarmFragment.getBreakDefinitions();
        DriverModeAlarmFragment.BreakDef useDef = null;
        for (final DriverModeAlarmFragment.BreakDef def : defs) {
            if (timeLeft < def.minimumDurationMillis) {
                if (timeLeft > def.leadTimeMillis)
                    useDef = def;
                break;
            }
            useDef = def;
        }
        if (useDef == null) {
            // next operation is too near
            return 0;
        }
        return departureTime.getTime() - useDef.leadTimeMillis;
    }

    private Trip getNextScheduledOperation(final NetworkId networkId, final Date now) {
        final Uri uri = QueryStoredTripsProvider.CONTENT_URI_BUILDER(networkId, QueryStoredTripsProvider.USAGE_OPERATION).build();
        byte[] tripBlob = null;
        try (final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null) {
                final int colTime = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_DEPARTURE_TIME);
                final int colState = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_STATE_FLAGS);
                final int colTrip = cursor.getColumnIndexOrThrow(QueryStoredTripsProvider.KEY_TRIP);
                long lowestTime = Long.MAX_VALUE;
                while (cursor.moveToNext()) {
                    final int stateFlags = cursor.getInt(colState);
                    final boolean isDone = (stateFlags & QueryStoredTripsProvider.STATE_FLAG_DONE) != 0;
                    if (isDone)
                        continue;
                    final long departureTime = cursor.getLong(colTime);
                    if (now != null && departureTime < now.getTime())
                        continue;
                    if (departureTime < lowestTime) {
                        lowestTime = departureTime;
                        tripBlob = cursor.getBlob(colTrip);
                    }
                }
            }
        }
        if (tripBlob == null)
            return null;
        return (Trip) Objects.deserialize(tripBlob);
    }

    public void fireAlarm(final Date now) {
        log.debug("alarm fired, open operation activity");
        final NetworkId networkId = Application.getInstance().getDefaultNetwork();
        final Trip trip = getNextScheduledOperation(networkId, now);
        if (trip == null)
            return;

        final String notificationTag = NOTIFICATION_TAG;

        final PendingIntent contentIntent = PendingIntent.getActivity(context, 99,
                OperationsActivity.buildStartIntent(context, null),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final PendingIntent fullScreenIntent = PendingIntent.getActivity(context, 99,
                OperationsActivity.buildStartIntent(context, notificationTag),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        final String channelId = DEFAULT_CHANNEL_ID;
        final Trip.Public operationLeg = trip.getFirstPublicLeg();
        final String title = context.getString(R.string.drivermode_operationalarm_notification_start_title,
                operationLeg.line.label,
                trip.to.uniqueShortName());
        final TimeZoneSelector timeZoneSelector = Application.getInstance().getSystemTimeZoneSelector();
        final PTDate departureTime = operationLeg.getDepartureTime(true);
        final String message = context.getString(R.string.drivermode_operationalarm_notification_start_message,
                Formats.formatTimeDiff(context, new Date(), departureTime),
                Formats.formatTime(timeZoneSelector, departureTime, PTDate.SYSTEM_OFFSET));

        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, channelId)
                .setContentIntent(contentIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(message))
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_oeffi_directions_grey600_36dp)
                .setOngoing(false)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setWhen(0)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true);

        final Notification notification = notificationBuilder.build();
        notification.flags |= NotificationCompat.FLAG_INSISTENT;
        log.info("set alarm notification with tag={}", notificationTag);
        getNotificationManager(context).notify(notificationTag, 0, notification);

        // prepare another alarm (for this same trip) if required
        setupAlarm(networkId);
    }

    public void dismissAlarm(final String notificationTag) {
        getNotificationManager(context).cancel(notificationTag, 0);
    }

    public void showAlarmPopupDialog(final String notificationTag) {
        if (notificationTag == null)
            return;
        final Notification notification = findNotificationByTag(notificationTag);
        if (notification == null)
            return;
        final Bundle extras = notification.extras;
        final String title = extras.getString(Notification.EXTRA_TITLE);
        final String message = extras.getString(Notification.EXTRA_TEXT);

        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.navigation_alarm_popup);
        ((TextView) dialog.findViewById(R.id.navigation_alarm_popup_title)).setText(title);
        ((TextView) dialog.findViewById(R.id.navigation_alarm_popup_message)).setText(message);
        dialog.findViewById(R.id.navigation_alarm_popup_button_ok).setOnClickListener(v -> {
            // dismissAlarm(notificationTag); -- done in dismiss listener
            dialog.dismiss();
        });
        dialog.setOnKeyListener((d, keyCode, event) -> {
            // dismissAlarm(notificationTag); -- done in dismiss listener
            dialog.dismiss();
            return true;
        });
        dialog.setOnDismissListener(d -> {
            dismissAlarm(notificationTag);
        });
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private AlarmManager getSystemAlarmManager() {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    private @NonNull NotificationManager getSystemNotificationManager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private boolean canScheduleExactAlarms() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || getSystemAlarmManager().canScheduleExactAlarms();
    }
}
