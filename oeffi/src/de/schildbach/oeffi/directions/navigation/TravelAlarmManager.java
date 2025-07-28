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

package de.schildbach.oeffi.directions.navigation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.TripDetailsActivity;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Trip;

public class TravelAlarmManager {
    private static Logger log = LoggerFactory.getLogger(TravelAlarmManager.class);

    public interface TravelAlarmDialogFinishedListener {
        void onTravelAlarmDialogFinished();
    }

    private static final String PREF_KEY_TRAVELALARM_TIME_RATIO = "travelalarm_time_ratio";
    private static final String PREF_KEY_TRAVELALARM_START_TIME_DEFAULT = "travelalarm_start_time_default";
    private static final String PREF_KEY_TRAVELALARM_START_LEAD_TIME = "travelalarm_start_lead_time";
    private static final String PREF_KEY_TRAVELALARM_ARRIVAL_TIME_DEFAULT = "travelalarm_arrival_time_default";
    private static final String PREF_KEY_TRAVELALARM_ARRIVAL_LEAD_TIME = "travelalarm_arrival_lead_time";
    private static final String PREF_KEY_TRAVELALARM_DEPARTURE_TIME_DEFAULT = "travelalarm_departure_time_default";
    private static final String PREF_KEY_TRAVELALARM_DEPARTURE_LEAD_TIME = "travelalarm_departure_lead_time";
    public static final String PREF_KEY_TRAVELALARM_START = "travelalarm_start_%s_%s_%d";

    private static final String[] LEGACY_CHANNEL_IDS = new String[]{ "startalarm" };
    private static final String START_CHANNEL_ID = "travelalarm-start";
    private static final String DEPARTURE_CHANNEL_ID = "travelalarm-departure";
    private static final String ARRIVAL_CHANNEL_ID = "travelalarm-arrival";
    private static final String TAG_PREFIX = TravelAlarmManager.class.getName() + ":";

    private static boolean notificationChannelCreated;

    public static void createNotificationChannel(final Context context) {
        if (notificationChannelCreated)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager notificationManager = getNotificationManager(context);
            for (String channelId : LEGACY_CHANNEL_IDS) {
                notificationManager.deleteNotificationChannel(channelId);
            }

            createNotificationChannel(START_CHANNEL_ID,
                    context.getString(R.string.navigation_travelalarm_notification_start_channel_name),
                    context.getString(R.string.navigation_travelalarm_notification_start_channel_description),
                    notificationManager);
            createNotificationChannel(DEPARTURE_CHANNEL_ID,
                    context.getString(R.string.navigation_travelalarm_notification_departure_channel_name),
                    context.getString(R.string.navigation_travelalarm_notification_departure_channel_description),
                    notificationManager);
            createNotificationChannel(ARRIVAL_CHANNEL_ID,
                    context.getString(R.string.navigation_travelalarm_notification_arrival_channel_name),
                    context.getString(R.string.navigation_travelalarm_notification_arrival_channel_description),
                    notificationManager);
        }
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

    private final Context context;

    public TravelAlarmManager(final Context context) {
        this.context = context;
    }

    private Notification findNotificationByTag(final String tag) {
        final StatusBarNotification[] activeNotifications = getNotificationManager(context).getActiveNotifications();
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            if (tag.equals(statusBarNotification.getTag()))
                return statusBarNotification.getNotification();
        }
        return null;
    }

    private SharedPreferences getSharedPreferences() {
        return Application.getInstance().getSharedPreferences();
    }

    public int getTimeRatioPercent() {
        return getSharedPreferences().getInt(PREF_KEY_TRAVELALARM_TIME_RATIO, 33);
    }

    @SuppressLint("DefaultLocale")
    private String getDefaultTimeStartPrefKey(final NetworkId networkId, final String stationId, final int walkMinutes) {
        return String.format(PREF_KEY_TRAVELALARM_START, networkId.name(), stationId, walkMinutes);
    }

    public long getDefaultTimeStart(final NetworkId networkId, final String stationId, final int walkMinutes) {
        final String key = getDefaultTimeStartPrefKey(networkId, stationId, walkMinutes);
        return getSharedPreferences().getLong(key, -1);
    }

    public void setDefaultTimeStart(final NetworkId networkId, final String stationId, final int walkMinutes, final Long millis) {
        final String key = getDefaultTimeStartPrefKey(networkId, stationId, walkMinutes);
        final SharedPreferences.Editor edit = getSharedPreferences().edit();
        if (millis == null)
            edit.remove(key);
        else
            edit.putLong(key, millis);
        edit.apply();
    }

    private void saveDefaultStart(final NetworkId networkId, final Location location, final int walkMinutes, final Long millis) {
        setDefaultTimeStart(networkId, location.id, walkMinutes, millis);
    }

    private void deleteDefaultStart(final NetworkId networkId, final Location location, final int walkMinutes) {
        setDefaultTimeStart(networkId, location.id, walkMinutes, null);
    }

    private long getMinutePreferenceInMillis(final String key, final int defValue) {
        final String string = getSharedPreferences().getString(key, null);
        final int v = (string == null) ? defValue : Integer.parseInt(string);
        if (v <= 0)
            return v;
        return (long) v * 60000;
    }

    public long getStartAlarmTimeDefault() {
        return getMinutePreferenceInMillis(PREF_KEY_TRAVELALARM_START_TIME_DEFAULT, 0);
    }

    public long getStartAlarmLeadTime() {
        return getMinutePreferenceInMillis(PREF_KEY_TRAVELALARM_START_LEAD_TIME, 15);
    }

    public long getArrivalAlarmTimeDefault() {
        return getMinutePreferenceInMillis(PREF_KEY_TRAVELALARM_ARRIVAL_TIME_DEFAULT, 0);
    }

    public long getArrivalAlarmLeadTime() {
        return getMinutePreferenceInMillis(PREF_KEY_TRAVELALARM_ARRIVAL_LEAD_TIME, 120);
    }

    public long getDepartureAlarmTimeDefault() {
        return getMinutePreferenceInMillis(PREF_KEY_TRAVELALARM_DEPARTURE_TIME_DEFAULT, 0);
    }

    public long getDepartureAlarmLeadTime() {
        return getMinutePreferenceInMillis(PREF_KEY_TRAVELALARM_DEPARTURE_LEAD_TIME, 30);
    }

    private long getWeightedTime(final long earliestEventTime, final long estimatedEventTime) {
        final int timeRatioPercent = getTimeRatioPercent();
        return (earliestEventTime * (100 - timeRatioPercent) + estimatedEventTime * timeRatioPercent) / 100;
    }

    public long getStartAlarm(
            final long earliestEventTime, final long estimatedEventTime,
            final long explicitSettingMs,
            final NetworkId networkId, final String stationId, final int walkMinutes,
            final long beginningOfNavigation, final long now) {
        if (explicitSettingMs == 0)
            return 0;
        else if (explicitSettingMs > 0)
            return getWeightedTime(earliestEventTime, estimatedEventTime) - explicitSettingMs;

        long alarmTimeBeforeEventMs = getDefaultTimeStart(networkId, stationId, walkMinutes);
        if (alarmTimeBeforeEventMs < 0) {
            alarmTimeBeforeEventMs = getStartAlarmTimeDefault();
            if (alarmTimeBeforeEventMs <= 0)
                return 0;
        }
        final long alarmTimeMs = getWeightedTime(earliestEventTime, estimatedEventTime) - alarmTimeBeforeEventMs;
        final long leadTime = getStartAlarmLeadTime();
        if (alarmTimeMs - beginningOfNavigation < leadTime)
            return 0;
        return alarmTimeMs;
    }

    public long getArrivalAlarm(
            final long earliestEventTime, final long estimatedEventTime,
            final long explicitSettingMs,
            final long beginningOfLeg, final long now) {
        if (explicitSettingMs == 0)
            return 0;
        else if (explicitSettingMs > 0)
            return getWeightedTime(earliestEventTime, estimatedEventTime) - explicitSettingMs;

        long alarmTimeBeforeEventMs = getArrivalAlarmTimeDefault();
        if (alarmTimeBeforeEventMs <= 0)
            return 0;
        final long alarmTimeMs = getWeightedTime(earliestEventTime, estimatedEventTime) - alarmTimeBeforeEventMs;
        final long leadTime = getArrivalAlarmLeadTime();
        if (alarmTimeMs - beginningOfLeg < leadTime)
            return 0;
        return alarmTimeMs;
    }

    public long getDepartureAlarm(
            final long earliestEventTime, final long estimatedEventTime,
            final long explicitSettingMs,
            final long beginningOfChangeover, final long now) {
        if (explicitSettingMs == 0)
            return 0;
        else if (explicitSettingMs > 0)
            return getWeightedTime(earliestEventTime, estimatedEventTime) - explicitSettingMs;

        long alarmTimeBeforeEventMs = getDepartureAlarmTimeDefault();
        if (alarmTimeBeforeEventMs <= 0)
            return 0;
        final long alarmTimeMs = getWeightedTime(earliestEventTime, estimatedEventTime) - alarmTimeBeforeEventMs;
        final long leadTime = getDepartureAlarmLeadTime();
        if (alarmTimeMs - beginningOfChangeover < leadTime)
            return 0;
        return alarmTimeMs;
    }

    public void fireAlarm(
            final Context context,
            final TripDetailsActivity.IntentData intentData,
            final TripRenderer tripRenderer) {
        log.debug("alarm fired, open navigator activity");
        final Trip trip = tripRenderer.trip;
        final String notificationTag = TAG_PREFIX + trip.getUniqueId();

        final PendingIntent contentIntent = PendingIntent.getActivity(context, 99,
                TripNavigatorActivity.buildStartIntent(
                        context, intentData.network, trip, intentData.renderConfig,
                        false, true, null, false),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final PendingIntent fullScreenIntent = PendingIntent.getActivity(context, 99,
                TripNavigatorActivity.buildStartIntent(
                        context, intentData.network, trip, intentData.renderConfig,
                        false, true, notificationTag, false),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        final String title = context.getString(R.string.navigation_travelalarm_notification_start_title,
                tripRenderer.trip.to.uniqueShortName());
        final String message = context.getString(R.string.navigation_travelalarm_notification_departure_message,
                tripRenderer.nextEventTargetName,
                Formats.formatTime(context, tripRenderer.nextEventEarliestTime),
                Formats.formatTime(context, tripRenderer.nextEventEstimatedTime),
                tripRenderer.nextEventTimeLeftValue, tripRenderer.nextEventTimeLeftUnit);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                context, START_CHANNEL_ID)
                .setContentIntent(contentIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
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
        dialog.findViewById(R.id.navigation_travelalarm_popup_button_ok).setOnClickListener(v -> {
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

    public void showConfigureTravelAlarmDialog(
            final TripRenderer.LegContainer legContainer,
            final Intent navigationNotificationIntent,
            final TravelAlarmDialogFinishedListener finishedListener) {
        final ConfigureTravelAlarmDialog dialog = new ConfigureTravelAlarmDialog(
                legContainer, navigationNotificationIntent, finishedListener);
        dialog.show();
    }

    private class ConfigureTravelAlarmDialog extends Dialog {
        public static final int MIN_TIME_VALUE = 1;
        public static final int MIN_TIME_SUGGEST_VALUE = 8;
        public static final int MAX_TIME_VALUE = 120;

        final TravelAlarmDialogFinishedListener finishedListener;
        final TripRenderer.LegContainer legContainer;
        final Intent navigationNotificationIntent;
        private NumberPicker timePicker;
        private CheckBox saveDefaultCheckBox;

        public ConfigureTravelAlarmDialog(
                final TripRenderer.LegContainer legContainer,
                final Intent navigationNotificationIntent,
                final TravelAlarmDialogFinishedListener finishedListener) {
            super(context);
            this.legContainer = legContainer;
            this.navigationNotificationIntent = navigationNotificationIntent;
            this.finishedListener = finishedListener;
            setOnDismissListener(dialog -> {
                if (finishedListener != null)
                    finishedListener.onTravelAlarmDialogFinished();
            });
        }

        @Override
        protected void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final NavigationNotification navigationNotification = new NavigationNotification(getContext(), navigationNotificationIntent);
            final NetworkId networkId = navigationNotification.getNetwork();
            final NavigationNotification.Configuration configuration = Objects.clone(navigationNotification.getConfiguration());
            final NavigationNotification.ExtraData extraData = navigationNotification.getExtraData();

            final int legIndex;
            final boolean alarmIsForDeparture;
            if (legContainer.publicLeg != null) {
                legIndex = legContainer.legIndex;
                alarmIsForDeparture = false;
            } else if (legContainer.individualLeg != null) {
                legIndex = legContainer.transferTo.legIndex;
                alarmIsForDeparture = true;
            } else {
                legIndex = legContainer.transferTo.legIndex;
                alarmIsForDeparture = true;
            }

            final long currentTravelAlarmAtMs;
            final long travelAlarmExplicitMs;
            if (alarmIsForDeparture) {
                currentTravelAlarmAtMs = extraData.currentTravelAlarmAtMsForLegDeparture[1 + legIndex];
                travelAlarmExplicitMs = configuration.travelAlarmExplicitMsForLegDeparture[1 + legIndex];
            } else {
                currentTravelAlarmAtMs = extraData.currentTravelAlarmAtMsForLegArrival[1 + legIndex];
                travelAlarmExplicitMs = configuration.travelAlarmExplicitMsForLegArrival[1 + legIndex];
            }

            final boolean isAlarmActive = (travelAlarmExplicitMs >= 0) ? (travelAlarmExplicitMs > 0) : (currentTravelAlarmAtMs > 0);

            final Trip trip = navigationNotification.getTrip();
            final boolean isInitialIndividualLeg = legIndex < 0 || (legIndex == 0 && legContainer.publicLeg == null);
            final Trip.Public firstPublicLeg = trip.getFirstPublicLeg();
            final Trip.Leg firstLeg = trip.legs.isEmpty() ? null : trip.legs.get(0);
            final int walkMinutes = (firstLeg instanceof Trip.Individual) ? ((Trip.Individual) firstLeg).min : 0;
            long currentDefaultTime;
            final boolean isDefaultSet;
            final boolean isLocationDefaultSet;
            if (isInitialIndividualLeg) {
                currentDefaultTime = getDefaultTimeStart(networkId, firstPublicLeg.departure.id, walkMinutes);
                if (currentDefaultTime > 0) {
                    isDefaultSet = true;
                    isLocationDefaultSet = true;
                } else {
                    currentDefaultTime = getStartAlarmTimeDefault();
                    isDefaultSet = currentDefaultTime > 0;
                    isLocationDefaultSet = false;
                }
            } else if (legContainer.publicLeg != null) {
                currentDefaultTime = getArrivalAlarmTimeDefault();
                isDefaultSet = currentDefaultTime > 0;
                isLocationDefaultSet = false;
            } else {
                currentDefaultTime = getDepartureAlarmTimeDefault();
                isDefaultSet = currentDefaultTime > 0;
                isLocationDefaultSet = false;
            }

            int presetValue;
            if (travelAlarmExplicitMs > 0) {
                presetValue = (int) (travelAlarmExplicitMs / 60000);
            } else if (isDefaultSet) {
                presetValue = (int) (currentDefaultTime / 60000);
            } else if (firstPublicLeg == null) {
                presetValue = MIN_TIME_SUGGEST_VALUE;
            } else {
                presetValue = walkMinutes;
                if (presetValue < MIN_TIME_SUGGEST_VALUE)
                    presetValue = MIN_TIME_SUGGEST_VALUE;
                else if (presetValue > MAX_TIME_VALUE)
                    presetValue = MAX_TIME_VALUE;
            }

            final Activity context = (Activity) TravelAlarmManager.this.context;

            setContentView(R.layout.navigation_alarm_dialog);

            saveDefaultCheckBox = findViewById(R.id.navigation_alarm_dialog_save_default);
            final Button deleteDefaultButton = findViewById(R.id.navigation_alarm_dialog_delete_default);

            if (isInitialIndividualLeg && firstPublicLeg != null) {
                String departureName = Formats.makeBreakableStationName(firstPublicLeg.departure.uniqueShortName());
                if (walkMinutes > 0)
                    departureName = getContext().getString(R.string.navigation_alarm_dialog_stationname_with_walk, departureName, walkMinutes);
                ((TextView) findViewById(R.id.navigation_alarm_dialog_message)).setText(
                        context.getString(R.string.navigation_alarm_dialog_message_start, departureName));

                saveDefaultCheckBox = findViewById(R.id.navigation_alarm_dialog_save_default);
                saveDefaultCheckBox.setText(
                        context.getString(R.string.navigation_alarm_dialog_save_default_label, departureName));
                saveDefaultCheckBox.setChecked(false);
                ViewUtils.setVisibility(saveDefaultCheckBox, !isLocationDefaultSet);

                deleteDefaultButton.setText(
                        context.getString(R.string.navigation_alarm_dialog_delete_default_label, departureName));
                deleteDefaultButton.setOnClickListener(view -> {
                    deleteDefaultStart(networkId, firstPublicLeg.departure, walkMinutes);
                    ViewUtils.setVisibility(saveDefaultCheckBox, true);
                    ViewUtils.setVisibility(deleteDefaultButton, false);
                });
                ViewUtils.setVisibility(deleteDefaultButton, isLocationDefaultSet);
            } else {
                ViewUtils.setVisibility(saveDefaultCheckBox, false);
                ViewUtils.setVisibility(deleteDefaultButton, false);
                if (legContainer.publicLeg != null) {
                    String arrivalName = Formats.makeBreakableStationName(legContainer.publicLeg.arrival.uniqueShortName());
                    ((TextView) findViewById(R.id.navigation_alarm_dialog_message)).setText(
                            context.getString(R.string.navigation_alarm_dialog_message_arrival, arrivalName));
                } else {
                    String departureName = Formats.makeBreakableStationName(legContainer.transferTo.publicLeg.departure.uniqueShortName());
                    ((TextView) findViewById(R.id.navigation_alarm_dialog_message)).setText(
                            context.getString(R.string.navigation_alarm_dialog_message_departure, departureName));
                }
            }

            timePicker = findViewById(R.id.navigation_alarm_dialog_time);
            timePicker.setMinValue(MIN_TIME_VALUE);
            timePicker.setMaxValue(MAX_TIME_VALUE);
            timePicker.setValue(presetValue);

            findViewById(R.id.navigation_alarm_dialog_set_alarm)
                    .setOnClickListener(view -> {
                        final long timeValue = (long) timePicker.getValue() * 60000;
                        if (saveDefaultCheckBox.isChecked())
                            saveDefaultStart(networkId, firstPublicLeg.departure, walkMinutes, timeValue);
                        if (alarmIsForDeparture) {
                            configuration.travelAlarmExplicitMsForLegDeparture[1 + legIndex] = timeValue;
                            configuration.travelAlarmIdForLegDeparture[1 + legIndex] = System.currentTimeMillis();
                        } else {
                            configuration.travelAlarmExplicitMsForLegArrival[1 + legIndex] = timeValue;
                            configuration.travelAlarmIdForLegArrival[1 + legIndex] = System.currentTimeMillis();
                        }
                        NavigationNotification.updateFromForeground(getContext(),
                                navigationNotificationIntent, configuration,
                                () -> context.runOnUiThread(this::dismiss));
                    });

            final Button clearAlarmButton = findViewById(R.id.navigation_alarm_dialog_clear_alarm);
            clearAlarmButton.setOnClickListener(view -> {
                if (alarmIsForDeparture) {
                    configuration.travelAlarmExplicitMsForLegDeparture[1 + legIndex] = 0;
                    configuration.travelAlarmIdForLegDeparture[1 + legIndex] = System.currentTimeMillis();
                } else {
                    configuration.travelAlarmExplicitMsForLegArrival[1 + legIndex] = 0;
                    configuration.travelAlarmIdForLegArrival[1 + legIndex] = System.currentTimeMillis();
                }
                NavigationNotification.updateFromForeground(getContext(),
                        navigationNotificationIntent, configuration,
                        () -> context.runOnUiThread(this::dismiss));
            });
            ViewUtils.setVisibility(clearAlarmButton, isAlarmActive);
        }
    }
}
