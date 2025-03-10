package de.schildbach.oeffi.directions.navigation;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.util.ClockUtils;

public class NavigationAlarmManager {
    private final static long MIN_PERIOD_MS = 30000;
    private static NavigationAlarmManager instance;
    private static final Logger log = LoggerFactory.getLogger(NavigationAlarmManager.class);
    public static final SimpleDateFormat LOG_TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");


    public static NavigationAlarmManager getInstance() {
        if (instance == null) {
            instance = new NavigationAlarmManager();
        }
        return instance;
    }

    private boolean stopped;
    private long refreshAt = Long.MAX_VALUE;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    public NavigationAlarmManager() {
        backgroundThread = new HandlerThread("Navigation.queryTripsThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public void start(long newRefreshAt) {
        if (!stopped) {
            if (newRefreshAt + MIN_PERIOD_MS < refreshAt) {
                refreshAt = newRefreshAt;
                try {
                    restart();
                } catch (IOException e) {
                    log.error("error when starting trip refresher", e);
                }
            } else {
                log.info("keeping real refresh at {}", LOG_TIME_FORMAT.format(refreshAt));
            }
        }
    }

    public void stop() {
        stopped = true;
        refreshAt = Long.MAX_VALUE;
        getSystemAlarmManager().cancel(getPendingRefreshIntent());
    }

    private Context getContext() {
        return Application.getInstance();
    }

    private AlarmManager getSystemAlarmManager() {
        return (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
    }

    public static class RefreshReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            runOnHandlerThread(() -> getInstance().onRefreshTimer());
        }
    }

    public static void runOnHandlerThread(final Runnable runnable) {
        getInstance().backgroundHandler.post(runnable);
    }

    private PendingIntent getPendingRefreshIntent() {
        final Context context = getContext();
        final Intent intent = new Intent(context, RefreshReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private void onRefreshTimer() {
        try {
            if (!stopped) {
                refresh();
                restart();
            }
        } catch (IOException e) {
            log.error("error when refreshing trips from alarms", e);
        }
    }

    private void refresh() {
        refreshAt = NavigationNotification.refreshAll(getContext());
        final long minNext = new Date().getTime() + MIN_PERIOD_MS;
        if (refreshAt < minNext)
            refreshAt = minNext;
    }

    private void restart() throws IOException {
        while (!stopped) {
            if (refreshAt == Long.MAX_VALUE) {
                log.info("no navigation running, stopping alarms");
                break;
            }
            final long timeToWait = refreshAt - new Date().getTime();
            if (timeToWait > 0) {
                log.info("new real refresh at {}", LOG_TIME_FORMAT.format(refreshAt));

                final AlarmManager alarmManager = getSystemAlarmManager();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            // AlarmManager.RTC_WAKEUP, refreshAt,
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, ClockUtils.clockToElapsedTime(refreshAt),
                            getPendingRefreshIntent());
                }
                break;
            }
            log.info("new real at {} is in past, refreshing now", LOG_TIME_FORMAT.format(refreshAt));
            refresh();
        }
    }
}
