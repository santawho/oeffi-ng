package de.schildbach.oeffi.directions.navigation;

import static com.google.common.base.Preconditions.checkNotNull;

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
import android.widget.RemoteViews;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.TripDetailsActivity;
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

    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final TripDetailsActivity.IntentData intentData = new TripDetailsActivity.IntentData(intent);
            final NavigationNotification navigationNotification = new NavigationNotification(intentData.trip, intent);
            navigationNotification.refresh(context);
        }
    }

    public NavigationNotification(final Trip trip, final Intent intent) {
        notificationTag = trip.getUniqueId();
        intentData = new TripDetailsActivity.IntentData(intent);
    }

    public void update(final Context context, final Trip trip, final boolean foreGround) {
        createNotificationChannel(context);
        boolean changes = false;
        final RemoteViews notificationLayout = new RemoteViews(context.getPackageName(), R.layout.navigation_notification);
        changes |= setupNotificationView(notificationLayout, trip);
        // final RemoteViews notificationLayoutExpanded = new RemoteViews(context.getPackageName(), R.layout.navigation_notification);
        // changes |= setupNotificationView(notificationLayoutExpanded, trip);

        final PendingIntent pendingActivityIntent = getPendingActivityIntent(context);
        getNotificationManager(context).notify(notificationTag, intentData.trip.getUniqueId().hashCode(),
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_oeffi_directions_grey600_36dp)
                        .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_oeffi_directions))
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                        .setCustomContentView(notificationLayout)
                        // .setCustomBigContentView(notificationLayoutExpanded)
                        .setContentIntent(pendingActivityIntent)
                        .setDeleteIntent(pendingActivityIntent)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setSilent(foreGround || !changes)
                        .build());

        if (nextRefreshTime > 0) {
            getAlarmManager(context)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRefreshTime, getPendingAlarmIntent(context));
        }
    }

    public void remove(final Context context) {
        getNotificationManager(context).cancel(notificationTag, intentData.trip.getUniqueId().hashCode());
        getAlarmManager(context).cancel(getPendingAlarmIntent(context));
    }

    private PendingIntent getPendingActivityIntent(final Context context) {
        final Intent intent = TripNavigatorActivity.buildStartIntent(context, intentData.network, intentData.trip, intentData.renderConfig);
        return PendingIntent.getActivity(context, intentData.trip.getUniqueId().hashCode(), intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent getPendingAlarmIntent(final Context context) {
        final Intent intent = new Intent(context, NavigationNotification.Receiver.class);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_NETWORK, intentData.network);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_TRIP, intentData.trip);
        intent.putExtra(TripDetailsActivity.INTENT_EXTRA_RENDERCONFIG, intentData.renderConfig);
        return PendingIntent.getBroadcast(context, intentData.trip.getUniqueId().hashCode(), intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean setupNotificationView(final RemoteViews view, final Trip trip) {
        nextRefreshTime = System.currentTimeMillis() + 10000;
        return true;
    }

    private void refresh(final Context context) {
        final Trip trip = intentData.trip;
        update(context, trip, false);
    }
}
