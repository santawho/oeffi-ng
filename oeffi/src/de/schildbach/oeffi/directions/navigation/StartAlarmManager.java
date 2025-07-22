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

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Date;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Trip;

public class StartAlarmManager {
    public interface StartAlarmDialogFinishedListener {
        void onStartAlarmDialogFinished(boolean isAlarmActive);
    }

    public void showConfigureStartAlarmDialog(
            @NonNull final Context context,
            final Intent navigationNotificationIntent,
            final StartAlarmDialogFinishedListener finishedListener) {
        final ConfigureStartAlarmDialog dialog = new ConfigureStartAlarmDialog(
                context, navigationNotificationIntent, finishedListener);
        dialog.show();
    }

    private String getDefaultTimePrefKey(final NetworkId networkId, final String stationId) {
        return String.format("start_alarm_%s_%s", networkId.name(), stationId);
    }

    public Integer getDefaultTime(final NetworkId networkId, final String stationId) {
        final String key = getDefaultTimePrefKey(networkId, stationId);
        final int value = Application.getInstance().getSharedPreferences().getInt(key, -1);
        return value < 0 ? null : value;
    }

    public void setDefaultTime(final NetworkId networkId, final String stationId, final Integer minutes) {
        final String key = getDefaultTimePrefKey(networkId, stationId);
        final SharedPreferences.Editor edit = Application.getInstance().getSharedPreferences().edit();
        if (minutes == null)
            edit.remove(key);
        else
            edit.putInt(key, minutes);
        edit.apply();
    }

    private void saveDefault(final NetworkId networkId, final Location location, final int minutes) {
        setDefaultTime(networkId, location.id, minutes);
    }

    private void deleteDefault(final NetworkId networkId, final Location location) {
        setDefaultTime(networkId, location.id, null);
    }

    private class ConfigureStartAlarmDialog extends Dialog {
        public static final int MIN_TIME_VALUE = 8;
        public static final int MAX_TIME_VALUE = 120;

        final StartAlarmDialogFinishedListener finishedListener;
        final Intent navigationNotificationIntent;
        private NumberPicker timePicker;
        private CheckBox saveDefaultCheckBox;

        public ConfigureStartAlarmDialog(
                @NonNull final Context context,
                final Intent navigationNotificationIntent,
                final StartAlarmDialogFinishedListener finishedListener) {
            super(context);
            this.navigationNotificationIntent = navigationNotificationIntent;
            this.finishedListener = finishedListener;
        }

        @Override
        protected void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final NavigationNotification navigationNotification = new NavigationNotification(getContext(), navigationNotificationIntent);
            final NetworkId networkId = navigationNotification.getNetwork();
            final NavigationNotification.Configuration configuration = Objects.clone(navigationNotification.getConfiguration());
            final Integer currentTimeValue = configuration.startAlarmMinutes;
            final boolean isAlarmActive = currentTimeValue != null;

            final Trip.Public firstPublicLeg = navigationNotification.getTrip().getFirstPublicLeg();
            final Integer currentDefaultTime = getDefaultTime(networkId, firstPublicLeg.departure.id);
            final boolean isDefaultSet = currentDefaultTime != null;

            int presetValue;
            if (isAlarmActive) {
                presetValue = currentTimeValue;
            } else if (isDefaultSet) {
                presetValue = currentDefaultTime;
            } else {
                final Date departureTime = firstPublicLeg.getDepartureTime();
                presetValue = ((int) ((departureTime.getTime() - new Date().getTime()) / 60000) + MIN_TIME_VALUE) / 2;
                if (presetValue < MIN_TIME_VALUE)
                    presetValue = MIN_TIME_VALUE;
                else if (presetValue > MAX_TIME_VALUE)
                    presetValue = MAX_TIME_VALUE;
            }

            final Context context = getContext();

            setContentView(R.layout.navigation_alarm_dialog);

            final String departureName = Formats.makeBreakableStationName(firstPublicLeg.departure.uniqueShortName());
            ((TextView) findViewById(R.id.navigation_alarm_dialog_message)).setText(
                    context.getString(R.string.navigation_alarm_dialog_message, departureName));

            timePicker = findViewById(R.id.navigation_alarm_dialog_time);
            timePicker.setMinValue(MIN_TIME_VALUE);
            timePicker.setMaxValue(MAX_TIME_VALUE);
            timePicker.setValue(presetValue);

            saveDefaultCheckBox = findViewById(R.id.navigation_alarm_dialog_save_default);
            saveDefaultCheckBox.setText(
                    context.getString(R.string.navigation_alarm_dialog_save_default_label, departureName));
            saveDefaultCheckBox.setChecked(false);
            ViewUtils.setVisibility(saveDefaultCheckBox, !isDefaultSet);

            final Button deleteDefaultButton = findViewById(R.id.navigation_alarm_dialog_delete_default);
            deleteDefaultButton.setText(
                    context.getString(R.string.navigation_alarm_dialog_delete_default_label, departureName));
            deleteDefaultButton.setOnClickListener(view -> {
                deleteDefault(networkId, firstPublicLeg.departure);
                ViewUtils.setVisibility(saveDefaultCheckBox, true);
                ViewUtils.setVisibility(deleteDefaultButton, false);
            });
            ViewUtils.setVisibility(deleteDefaultButton, isDefaultSet);

            findViewById(R.id.navigation_alarm_dialog_set_alarm)
                    .setOnClickListener(view -> {
                        final int timeValue = timePicker.getValue();
                        if (saveDefaultCheckBox.isChecked())
                            saveDefault(networkId, firstPublicLeg.departure, timeValue);
                        configuration.startAlarmMinutes = timeValue;
                        NavigationNotification.updateFromForeground(getContext(),
                                navigationNotificationIntent, null, configuration);
                        if (finishedListener != null)
                            finishedListener.onStartAlarmDialogFinished(true);
                        dismiss();
                    });

            final Button clearAlarmButton = findViewById(R.id.navigation_alarm_dialog_clear_alarm);
            clearAlarmButton.setOnClickListener(view -> {
                if (finishedListener != null)
                    finishedListener.onStartAlarmDialogFinished(false);
                configuration.startAlarmMinutes = null;
                NavigationNotification.updateFromForeground(getContext(),
                        navigationNotificationIntent, null, configuration);
                dismiss();
            });
            ViewUtils.setVisibility(clearAlarmButton, isAlarmActive);
        }
    }
}
