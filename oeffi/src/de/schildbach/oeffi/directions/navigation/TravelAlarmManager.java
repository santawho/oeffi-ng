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

import static com.google.common.base.Preconditions.checkNotNull;

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
import de.schildbach.pte.dto.Timestamp;
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

    public class TravelAlarmState {
        final TripRenderer.LegContainer legContainer;
        final boolean alarmIsForDeparture;
        final NetworkId networkId;
        final int legIndex;
        final Trip.Public publicLeg;
        final boolean isInitialIndividualLeg;
        final int walkMinutes;
        final long travelAlarmExplicitMs;
        final long currentDefaultTime;
        final boolean isDefaultSet;
        final boolean isLocationDefaultSet;
        final boolean isAlarmActive;
        final boolean isAlarmDisabled;
        final long currentTravelAlarmAtMs;

        public TravelAlarmState(
            final TripRenderer.LegContainer legContainer,
            final boolean alarmIsForDeparture,
            final NavigationNotification navigationNotification) {
            this.legContainer = legContainer;
            this.alarmIsForDeparture = alarmIsForDeparture;

            networkId = navigationNotification.getNetwork();
            final NavigationNotification.Configuration configuration = navigationNotification.getConfiguration();
            final NavigationNotification.ExtraData extraData = navigationNotification.getExtraData();
            final Trip trip = navigationNotification.getTrip();

            legIndex = legContainer.legIndex;
            publicLeg = checkNotNull(legContainer.publicLeg);
            if (alarmIsForDeparture) {
                final Trip.Leg firstLeg = trip.legs.isEmpty() ? null : trip.legs.get(0);
                if (firstLeg instanceof Trip.Individual) {
                    isInitialIndividualLeg = legIndex == 1;
                    walkMinutes = ((Trip.Individual) firstLeg).min;
                } else {
                    isInitialIndividualLeg = legIndex == 0;
                    walkMinutes = 0;
                }
                if (isInitialIndividualLeg) {
                    final long defaultTimeLocationStart = getDefaultTimeStart(networkId, publicLeg.departure.id, walkMinutes);
                    if (defaultTimeLocationStart > 0) {
                        currentDefaultTime = defaultTimeLocationStart;
                        isDefaultSet = true;
                        isLocationDefaultSet = true;
                    } else {
                        currentDefaultTime = getStartAlarmTimeDefault();
                        isDefaultSet = currentDefaultTime > 0;
                        isLocationDefaultSet = false;
                    }
                } else {
                    currentDefaultTime = getDepartureAlarmTimeDefault();
                    isDefaultSet = currentDefaultTime > 0;
                    isLocationDefaultSet = false;
                }
                travelAlarmExplicitMs = configuration.travelAlarmExplicitMsForLegDeparture[legIndex];
                currentTravelAlarmAtMs = extraData.currentTravelAlarmAtMsForLegDeparture[legIndex];
            } else {
                isInitialIndividualLeg = false;
                currentDefaultTime = getArrivalAlarmTimeDefault();
                isDefaultSet = currentDefaultTime > 0;
                isLocationDefaultSet = false;
                travelAlarmExplicitMs = configuration.travelAlarmExplicitMsForLegArrival[legIndex];
                currentTravelAlarmAtMs = extraData.currentTravelAlarmAtMsForLegArrival[legIndex];
                walkMinutes = 0;
            }

            isAlarmDisabled = travelAlarmExplicitMs == 0;
            isAlarmActive = (travelAlarmExplicitMs >= 0) ? (travelAlarmExplicitMs > 0) : isDefaultSet;
        }
    }

    public void fireAlarm(
            final Context context,
            final TripDetailsActivity.IntentData intentData,
            final TripRenderer tripRenderer,
            final boolean travelAlarmIsForDeparture,
            final boolean travelAlarmIsForJourneyStart) {
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

        final String channelId;
        final int messageResId;
        final String title;
        if (travelAlarmIsForJourneyStart) {
            channelId = START_CHANNEL_ID;
            title = context.getString(R.string.navigation_travelalarm_notification_start_title,
                    tripRenderer.trip.to.uniqueShortName());
            messageResId = R.string.navigation_travelalarm_notification_start_message;
        } else {
            final int titleResId;
            if (travelAlarmIsForDeparture) {
                channelId = DEPARTURE_CHANNEL_ID;
                titleResId = R.string.navigation_travelalarm_notification_departure_title;
                messageResId = R.string.navigation_travelalarm_notification_departure_message;
            } else {
                channelId = ARRIVAL_CHANNEL_ID;
                titleResId = R.string.navigation_travelalarm_notification_arrival_title;
                messageResId = R.string.navigation_travelalarm_notification_arrival_message;
            }
            title = context.getString(titleResId, tripRenderer.nextEventTargetName);
        }
        final String message = context.getString(messageResId,
                tripRenderer.nextEventTargetName,
                Formats.formatTime(context, tripRenderer.nextEventEarliestTime, Timestamp.SYSTEM_OFFSET),
                Formats.formatTime(context, tripRenderer.nextEventEstimatedTime, Timestamp.SYSTEM_OFFSET),
                tripRenderer.nextEventTimeLeftValue, tripRenderer.nextEventTimeLeftUnit);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, channelId)
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
            final boolean alarmIsForDeparture,
            final Intent navigationNotificationIntent,
            final TravelAlarmDialogFinishedListener finishedListener) {
        final ConfigureTravelAlarmDialog dialog = new ConfigureTravelAlarmDialog(
                legContainer, alarmIsForDeparture, navigationNotificationIntent, finishedListener);
        dialog.show();
    }

    private class ConfigureTravelAlarmDialog extends Dialog {
        public static final int MIN_TIME_VALUE = 1;
        public static final int MIN_TIME_SUGGEST_VALUE = 8;
        public static final int MAX_TIME_VALUE = 120;

        final TravelAlarmDialogFinishedListener finishedListener;
        final TripRenderer.LegContainer legContainer;
        final boolean alarmIsForDeparture;
        final Intent navigationNotificationIntent;
        private NumberPicker timePicker;
        private CheckBox saveDefaultCheckBox;

        public ConfigureTravelAlarmDialog(
                final TripRenderer.LegContainer legContainer,
                final boolean alarmIsForDeparture,
                final Intent navigationNotificationIntent,
                final TravelAlarmDialogFinishedListener finishedListener) {
            super(context);
            this.legContainer = legContainer;
            this.alarmIsForDeparture = alarmIsForDeparture;
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
            final TravelAlarmState travelAlarmState = new TravelAlarmState(legContainer, alarmIsForDeparture, navigationNotification);

            final String statusText;
            if (travelAlarmState.isAlarmDisabled) {
                statusText = context.getString(R.string.navigation_alarm_dialog_status_alarm_disabled_text);
            } else if (travelAlarmState.currentTravelAlarmAtMs > 0) {
                final String alarmTime = Formats.formatTime(context,
                        System.currentTimeMillis(), travelAlarmState.currentTravelAlarmAtMs, Timestamp.SYSTEM_OFFSET);
                statusText = context.getString(R.string.navigation_alarm_dialog_status_alarm_at_text, alarmTime);
            } else {
                statusText = context.getString(R.string.navigation_alarm_dialog_status_alarm_inactive_text);
            }

            int presetValue;
            if (travelAlarmState.travelAlarmExplicitMs > 0) {
                presetValue = (int) (travelAlarmState.travelAlarmExplicitMs / 60000);
            } else if (travelAlarmState.isDefaultSet) {
                presetValue = (int) (travelAlarmState.currentDefaultTime / 60000);
            } else if (travelAlarmState.isInitialIndividualLeg) {
                presetValue = travelAlarmState.walkMinutes;
                if (presetValue < MIN_TIME_SUGGEST_VALUE)
                    presetValue = MIN_TIME_SUGGEST_VALUE;
                else if (presetValue > MAX_TIME_VALUE)
                    presetValue = MAX_TIME_VALUE;
            } else {
                presetValue = MIN_TIME_SUGGEST_VALUE;
            }

            final Activity context = (Activity) TravelAlarmManager.this.context;
            setContentView(R.layout.navigation_alarm_dialog);
            ((TextView) findViewById(R.id.navigation_alarm_dialog_status)).setText(statusText);

            saveDefaultCheckBox = findViewById(R.id.navigation_alarm_dialog_save_default);
            final Button deleteDefaultButton = findViewById(R.id.navigation_alarm_dialog_delete_default);

            if (travelAlarmState.isInitialIndividualLeg) {
                String departureName = Formats.makeBreakableStationName(travelAlarmState.publicLeg.departure.uniqueShortName());
                if (travelAlarmState.walkMinutes > 0)
                    departureName = getContext().getString(R.string.navigation_alarm_dialog_stationname_with_walk, departureName, travelAlarmState.walkMinutes);
                ((TextView) findViewById(R.id.navigation_alarm_dialog_message)).setText(
                        context.getString(R.string.navigation_alarm_dialog_message_start, departureName));

                saveDefaultCheckBox = findViewById(R.id.navigation_alarm_dialog_save_default);
                saveDefaultCheckBox.setText(
                        context.getString(R.string.navigation_alarm_dialog_save_default_label, departureName));
                saveDefaultCheckBox.setChecked(false);
                ViewUtils.setVisibility(saveDefaultCheckBox, !travelAlarmState.isLocationDefaultSet);

                deleteDefaultButton.setText(
                        context.getString(R.string.navigation_alarm_dialog_delete_default_label, departureName));
                deleteDefaultButton.setOnClickListener(view -> {
                    deleteDefaultStart(travelAlarmState.networkId, travelAlarmState.publicLeg.departure, travelAlarmState.walkMinutes);
                    ViewUtils.setVisibility(saveDefaultCheckBox, true);
                    ViewUtils.setVisibility(deleteDefaultButton, false);
                });
                ViewUtils.setVisibility(deleteDefaultButton, travelAlarmState.isLocationDefaultSet);
            } else {
                ViewUtils.setVisibility(saveDefaultCheckBox, false);
                ViewUtils.setVisibility(deleteDefaultButton, false);
                if (alarmIsForDeparture) {
                    String departureName = Formats.makeBreakableStationName(travelAlarmState.publicLeg.departure.uniqueShortName());
                    ((TextView) findViewById(R.id.navigation_alarm_dialog_message)).setText(
                            context.getString(R.string.navigation_alarm_dialog_message_departure, departureName));
                } else {
                    String arrivalName = Formats.makeBreakableStationName(travelAlarmState.publicLeg.arrival.uniqueShortName());
                    ((TextView) findViewById(R.id.navigation_alarm_dialog_message)).setText(
                            context.getString(R.string.navigation_alarm_dialog_message_arrival, arrivalName));
                }
            }

            timePicker = findViewById(R.id.navigation_alarm_dialog_time);
            timePicker.setMinValue(MIN_TIME_VALUE);
            timePicker.setMaxValue(MAX_TIME_VALUE);
            timePicker.setValue(presetValue);

            final NavigationNotification.Configuration configuration = Objects.clone(navigationNotification.getConfiguration());

            findViewById(R.id.navigation_alarm_dialog_set_alarm)
                    .setOnClickListener(view -> {
                        final long timeValue = (long) timePicker.getValue() * 60000;
                        if (saveDefaultCheckBox.isChecked())
                            saveDefaultStart(travelAlarmState.networkId, travelAlarmState.publicLeg.departure, travelAlarmState.walkMinutes, timeValue);

                        if (alarmIsForDeparture)
                            configuration.setTravelAlarmExplicitMsForLegDeparture(travelAlarmState.legIndex, timeValue);
                        else
                            configuration.setTravelAlarmExplicitMsForLegArrival(travelAlarmState.legIndex, timeValue);

                        NavigationNotification.updateFromForeground(getContext(),
                                navigationNotificationIntent, configuration,
                                () -> context.runOnUiThread(this::dismiss));
                    });

            final Button clearAlarmButton = findViewById(R.id.navigation_alarm_dialog_clear_alarm);
            clearAlarmButton.setOnClickListener(view -> {
                if (alarmIsForDeparture)
                    configuration.setTravelAlarmExplicitMsForLegDeparture(travelAlarmState.legIndex, 0);
                else
                    configuration.setTravelAlarmExplicitMsForLegArrival(travelAlarmState.legIndex, 0);

                NavigationNotification.updateFromForeground(getContext(),
                        navigationNotificationIntent, configuration,
                        () -> context.runOnUiThread(this::dismiss));
            });
            ViewUtils.setVisibility(clearAlarmButton, travelAlarmState.isAlarmActive);
        }
    }
}
