package de.schildbach.oeffi.assistant;

import static de.schildbach.oeffi.preference.AssistantFragment.KEY_ASSISTANT_BUTTON_NAVIGATION_INSTRUCTION_ENABLED;
import static de.schildbach.oeffi.preference.AssistantFragment.KEY_ASSISTANT_BUTTON_NEARBY_STATIONS_ENABLED;
import static de.schildbach.oeffi.preference.AssistantFragment.KEY_ASSISTANT_HEADSET_NAVIGATION_INSTRUCTION_ENABLED;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.directions.navigation.NavigationNotification;
import de.schildbach.oeffi.stations.StationsActivity;

public class AssistantActivity extends Activity {
    private static final long DOUBLE_ACTION_TIME_MS = 5000;
    private static final long DELAY_SPEAK_ON_HEADSET_MS = 1500;

    static long timeOfLastAction;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();
        final boolean isHeadsetAction = Intent.ACTION_VOICE_COMMAND.equals(action);
        final boolean isButtonAction = Intent.ACTION_ASSIST.equals(action);

        final long now = System.currentTimeMillis();
        final long timeSinceLastAction = now - timeOfLastAction;
        timeOfLastAction = now;
        final boolean isDoubleAction = timeSinceLastAction < DOUBLE_ACTION_TIME_MS;

        boolean haveSpokenNavigationInstruction = false;

        final SharedPreferences prefs = Application.getInstance().getSharedPreferences();

        if (isButtonAction) {
            if (!isDoubleAction
                    && prefs.getBoolean(KEY_ASSISTANT_BUTTON_NAVIGATION_INSTRUCTION_ENABLED, false)) {
                haveSpokenNavigationInstruction = actionSpeakNavigationInstruction(0);
            }

            if (!haveSpokenNavigationInstruction
                    && prefs.getBoolean(KEY_ASSISTANT_BUTTON_NEARBY_STATIONS_ENABLED, true)) {
                actionStartNearbyStations();
            }
        } else if (isHeadsetAction) {
            if (prefs.getBoolean(KEY_ASSISTANT_HEADSET_NAVIGATION_INSTRUCTION_ENABLED, true)) {
                haveSpokenNavigationInstruction = actionSpeakNavigationInstruction(DELAY_SPEAK_ON_HEADSET_MS);
            }
        }

        finish();
    }

    private void actionStartNearbyStations() {
        StationsActivity.start(this, false);
    }

    private boolean actionSpeakNavigationInstruction(final long delayMs) {
        return NavigationNotification.makeAllGuidesSpeak(this, delayMs);
    }
}
