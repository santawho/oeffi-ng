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

    private NavigationAlarmManager() {
        backgroundThread = new HandlerThread("NavAlarmThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public void start(long newRefreshAt) {
        if (!stopped) {
            if (newRefreshAt + MIN_PERIOD_MS < refreshAt) {
                refreshAt = newRefreshAt;
                log.info("start alarm: setting real refresh at {}", LOG_TIME_FORMAT.format(refreshAt));
                try {
                    restart();
                } catch (IOException e) {
                    log.error("error when starting trip refresher", e);
                }
            } else {
                log.info("start alarm: keeping real refresh at {} instead of new at {}",
                        LOG_TIME_FORMAT.format(refreshAt),
                        LOG_TIME_FORMAT.format(newRefreshAt));
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
            log.info("refresh alarm was fired");
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
        log.info("refresh alarm being handled");
        try {
            if (!stopped) {
                refresh();
                restart();
            }
        } catch (IOException e) {
            log.error("error when refreshing trips from alarms", e);
        }
    }

    private void restart() throws IOException {
        while (!stopped) {
            if (refreshAt == Long.MAX_VALUE) {
                log.info("restart alarm: no navigation running, stopping alarms");
                break;
            }
            long timeToWait = refreshAt - new Date().getTime();
            if (timeToWait > 0) {
                if (timeToWait < 5000) {
                    timeToWait = 5000;
                    log.info("restart alarm: refresh at {}, time to wait at least = {}", LOG_TIME_FORMAT.format(refreshAt), timeToWait);
                } else {
                    log.info("restart alarm: refresh at {}, time to wait = {}", LOG_TIME_FORMAT.format(refreshAt), timeToWait);
                }
                final AlarmManager alarmManager = getSystemAlarmManager();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                    long triggerAtMillis = ClockUtils.elapsedTimePlus(timeToWait);
                    alarmManager.setExactAndAllowWhileIdle(
                            // AlarmManager.RTC_WAKEUP, refreshAt,
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis,
                            getPendingRefreshIntent());
                }
                break;
            }
            log.info("new real at {} is in past, refreshing now", LOG_TIME_FORMAT.format(refreshAt));
            refresh();
        }
    }

    private void refresh() {
        refreshAt = NavigationNotification.refreshAll(getContext());
        log.info("refresh alarm: next notification refresh of all at {}", LOG_TIME_FORMAT.format(refreshAt));
        final long minNext = new Date().getTime() + MIN_PERIOD_MS;
        log.info("refresh alarm: not earlier than at {}", LOG_TIME_FORMAT.format(minNext));
        if (refreshAt < minNext) {
            refreshAt = minNext;
            log.info("refresh alarm: setting next refresh at {}", LOG_TIME_FORMAT.format(refreshAt));
        } else {
            log.info("refresh alarm: keeping next refresh at {}", LOG_TIME_FORMAT.format(refreshAt));
        }
    }
}
