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
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
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
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Trip;

public class NavigationNotification {
    private static final String CHANNEL_ID = "navigation";
    private static final long KEEP_NOTIFICATION_FOR_MINUTES = 30;
    private static final String INTENT_EXTRA_REOPEN = NavigationNotification.class.getName() + ".reopen";

    private static final Logger log = LoggerFactory.getLogger(NavigationNotification.class);

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
    private final String notificationTag;
    private long nextRefreshTime;
    private Date timeoutAt;

    public NavigationNotification(final Trip trip, final Intent intent) {
        notificationTag = trip.getUniqueId();
        intentData = new TripDetailsActivity.IntentData(intent);
    }

    public void update(final Context context, final Trip oldTrip, final Trip trip, final boolean foreGround) {
        createNotificationChannel(context);
        final Date now = new Date();
        final long nowTime = now.getTime();
        boolean changes = false;
        final TripRenderer tripRenderer = new TripRenderer(trip, false, now);
        if (tripRenderer.nextEventEarliestTime != null) {
            final long timeLeft = tripRenderer.nextEventEarliestTime.getTime() - nowTime;
            if (timeLeft < 240000) {
                // last 4 minutes and after, 30 secs refresh interval
                nextRefreshTime = nowTime + 30000;
            } else {
                // approaching, refresh after 25% of the remaining time
                nextRefreshTime = nowTime + timeLeft / 4;
            }
        } else {
            final long timeOver = nowTime - trip.getLastArrivalTime().getTime();
            if (timeOver < 300000) {
                // max 5 minutes after the trip, 60 secs refresh interval
                nextRefreshTime = nowTime + 60000;
            } else {
                // no refresh
                nextRefreshTime = 0;
            }
        }
        timeoutAt = new Date(trip.getLastArrivalTime().getTime() + KEEP_NOTIFICATION_FOR_MINUTES * 60000);
        final RemoteViews notificationLayout = new RemoteViews(context.getPackageName(), R.layout.navigation_notification);
        changes |= setupNotificationView(context, notificationLayout, tripRenderer, now);
        // final RemoteViews notificationLayoutExpanded = new RemoteViews(context.getPackageName(), R.layout.navigation_notification);
        // changes |= setupNotificationView(notificationLayoutExpanded, trip);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_oeffi_directions)
//                .setSmallIcon(R.drawable.ic_oeffi_directions_grey600_36dp)
//                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_oeffi_directions))
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
                .setSilent(foreGround || !changes)
                .addAction(R.drawable.ic_clear_white_24dp, context.getString(R.string.navigation_stopnav_stop),
                        getPendingActivityIntent(context, true, false)) // getPendingDeleteIntent(context, false))
                .addAction(R.drawable.ic_navigation_white_24dp, context.getString(R.string.navigation_stopnav_showtrip),
                        getPendingActivityIntent(context, false, false));

        if (timeoutAt != null) {
            final long duration = timeoutAt.getTime() - nowTime;
            if (duration <= 1000) {
                remove(context);
                return;
            }
            builder.setTimeoutAfter(duration);
        }

        getNotificationManager(context).notify(notificationTag, intentData.trip.getUniqueId().hashCode(), builder.build());

        if (nextRefreshTime > 0) {
            log.info("refreshing in {} secs at {}",
                    (nextRefreshTime - nowTime) / 1000,
                    new SimpleDateFormat("HH:mm").format(nextRefreshTime));
            getAlarmManager(context)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRefreshTime, getPendingRefreshIntent(context));
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
            final NavigationNotification navigationNotification = new NavigationNotification(intentData.trip, intent);
            if (intent.getBooleanExtra(INTENT_EXTRA_REOPEN, false)) {
                navigationNotification.update(context, null, intentData.trip, true);
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
        if (newTrip != null)
            update(context, oldTrip, newTrip, false);
    }

    public static class RefreshReceiver extends BroadcastReceiver {
        private Runnable navigationRefreshRunnable;
        private HandlerThread backgroundThread;
        private Handler backgroundHandler;

        public RefreshReceiver() {
            backgroundThread = new HandlerThread("queryTripsThread", Process.THREAD_PRIORITY_BACKGROUND);
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final TripDetailsActivity.IntentData intentData = new TripDetailsActivity.IntentData(intent);
            final NavigationNotification navigationNotification = new NavigationNotification(intentData.trip, intent);
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

    private boolean setupNotificationView(
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

        return false;
    }

    private static void remoteViewsSetBackgroundColor(final RemoteViews remoteViews, int viewId, int color) {
        remoteViews.setInt(viewId, "setBackgroundColor", color);
    }
}
