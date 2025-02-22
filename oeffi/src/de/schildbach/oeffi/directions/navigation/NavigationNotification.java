package de.schildbach.oeffi.directions.navigation;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.Date;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.pte.dto.Trip;

public class NavigationNotification {
    private static final String CHANNEL_ID = "navigation";

    private static void createNotificationChannel(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.navigation_notification_channel_name);
            String description = context.getString(R.string.navigation_notification_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            final NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            getNotificationManager(context).createNotificationChannel(channel);
        }
    }

    private static NotificationManager getNotificationManager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static AlarmManager getAlarmManager(Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public static boolean requestPermissions(final Activity activity, final int requestCode) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            return true;
        ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.POST_NOTIFICATIONS }, requestCode);
        return false;
    }

    private final TripDetailsActivity.IntentData intentData;
    private String notificationTag;
    private long nextRefreshTime;

    public NavigationNotification(final Trip trip, final Intent intent) {
        notificationTag = trip.getUniqueId();
        intentData = new TripDetailsActivity.IntentData(intent);
    }

    public void update(final Context context, final Trip trip, final boolean foreGround) {
        createNotificationChannel(context);
        boolean changes = false;
        final RemoteViews notificationLayout = new RemoteViews(context.getPackageName(), R.layout.navigation_notification);
        changes |= setupNotificationView(context, notificationLayout, trip);
        // final RemoteViews notificationLayoutExpanded = new RemoteViews(context.getPackageName(), R.layout.navigation_notification);
        // changes |= setupNotificationView(notificationLayoutExpanded, trip);

        getNotificationManager(context).notify(notificationTag, intentData.trip.getUniqueId().hashCode(),
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_oeffi_directions_grey600_36dp)
                        .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_oeffi_directions))
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                        .setCustomContentView(notificationLayout)
                        // .setCustomBigContentView(notificationLayoutExpanded)
                        .setContentIntent(getPendingActivityIntent(context, false))
                        //.setDeleteIntent(getPendingActivityIntent(context, true))
                        .setDeleteIntent(getPendingDeleteIntent(context))
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setSilent(foreGround || !changes)
                        .build());

        if (nextRefreshTime > 0) {
            getAlarmManager(context)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRefreshTime, getPendingRefreshIntent(context));
        }
    }

    public void remove(final Context context) {
        getNotificationManager(context).cancel(notificationTag, intentData.trip.getUniqueId().hashCode());
        getAlarmManager(context).cancel(getPendingRefreshIntent(context));
    }

    private PendingIntent getPendingActivityIntent(final Context context, boolean deleteRequest) {
        final Intent intent = TripNavigatorActivity.buildStartIntent(context, intentData.network, intentData.trip, intentData.renderConfig, deleteRequest);
        int requestCode = intentData.trip.getUniqueId().hashCode();
        if (deleteRequest)
            requestCode = requestCode + 1;
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    public static class DeleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final TripDetailsActivity.IntentData intentData = new TripDetailsActivity.IntentData(intent);
            final NavigationNotification navigationNotification = new NavigationNotification(intentData.trip, intent);
            navigationNotification.update(context, intentData.trip, true);
        }
    }

    private PendingIntent getPendingDeleteIntent(final Context context) {
        final Intent intent = new Intent(context, NavigationNotification.DeleteReceiver.class);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_NETWORK, intentData.network);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_TRIP, intentData.trip);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_RENDERCONFIG, intentData.renderConfig);
        return PendingIntent.getBroadcast(context, intentData.trip.getUniqueId().hashCode() + 2, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    public static class RefreshReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final TripDetailsActivity.IntentData intentData = new TripDetailsActivity.IntentData(intent);
            final NavigationNotification navigationNotification = new NavigationNotification(intentData.trip, intent);
            navigationNotification.refresh(context);
        }
    }

    private PendingIntent getPendingRefreshIntent(final Context context) {
        final Intent intent = new Intent(context, NavigationNotification.RefreshReceiver.class);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_NETWORK, intentData.network);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_TRIP, intentData.trip);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_RENDERCONFIG, intentData.renderConfig);
        return PendingIntent.getBroadcast(context, intentData.trip.getUniqueId().hashCode() + 3, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean setupNotificationView(final Context context, final RemoteViews remoteViews, final Trip trip) {
        final Date now = new Date();
        TripRenderer tripRenderer = new TripRenderer(trip, false, now);

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

        nextRefreshTime = now.getTime() + 10000;
        return true;
    }

    private static void remoteViewsSetBackgroundColor(final RemoteViews remoteViews, int viewId, int color) {
        remoteViews.setInt(viewId, "setBackgroundColor", color);
    }

    private void refresh(final Context context) {
        final Trip trip = intentData.trip;
        update(context, trip, false);
    }
}
