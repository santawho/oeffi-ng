package de.schildbach.oeffi.assistant;

import de.schildbach.oeffi.preference.AssistantFragment;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.directions.navigation.NavigationNotification;
import de.schildbach.oeffi.stations.StationsActivity;

public class AssistantActivity extends Activity {
    private static final long DOUBLE_ACTION_TIME_MS = 5000;
    private static final long DELAY_SPEAK_ON_HEADSET_MS = 1500;
    private static final Logger log = LoggerFactory.getLogger(AssistantActivity.class);

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
            if (!isDoubleAction) {
                final boolean speakInstruction = prefs.getBoolean(AssistantFragment.KEY_ASSISTANT_BUTTON_NAVIGATION_INSTRUCTION_ENABLED, false);
                final boolean showInformation = prefs.getBoolean(AssistantFragment.KEY_ASSISTANT_BUTTON_NAVIGATION_SCREEN_ENABLED, true);

                if (speakInstruction || showInformation) {
                    haveSpokenNavigationInstruction = actionSpeakNavigationInstruction(speakInstruction, showInformation, 0);
                }
            }

            if (!haveSpokenNavigationInstruction) {
                if (prefs.getBoolean(AssistantFragment.KEY_ASSISTANT_BUTTON_NEARBY_STATIONS_ENABLED, true)) {
                    actionStartNearbyStations();
                } else {
                    startFallbackAssistant(intent);
                }
            }
        }

        if (isHeadsetAction) {
            if (prefs.getBoolean(AssistantFragment.KEY_ASSISTANT_HEADSET_NAVIGATION_INSTRUCTION_ENABLED, true)) {
                haveSpokenNavigationInstruction = actionSpeakNavigationInstruction(true, false, DELAY_SPEAK_ON_HEADSET_MS);
            }

            if (!haveSpokenNavigationInstruction) {
                startFallbackAssistant(intent);
            }
        }

        finish();
    }

    private void startFallbackAssistant(final Intent intent) {
        final String fallbackAssistantPackageName =
                AssistantFragment.getFallbackAssistantPackageName(AssistantFragment.KEY_ASSISTANT_FALLBACK_APP);
        if (fallbackAssistantPackageName == null)
            return;

        final Intent newIntent = new Intent()
                .setPackage(fallbackAssistantPackageName)
                .setAction(intent.getAction())
                .putExtras(intent)
                .setFlags(intent.getFlags());

        final PackageManager packageManager = getPackageManager();
        final List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(newIntent, PackageManager.MATCH_ALL);
        if (resolveInfos.isEmpty())
            return;
        final ResolveInfo resolveInfo = resolveInfos.get(0);
        final String activityClassName = resolveInfo.activityInfo.name;

        intent.setClassName(fallbackAssistantPackageName, activityClassName);

        try {
            startActivity(newIntent);
        } catch (final Exception e) {
            log.error("cannot forward to external assistant app {}/{}: {}", fallbackAssistantPackageName, activityClassName, e.getMessage());
        }
    }

    private void actionStartNearbyStations() {
        StationsActivity.start(this, false);
    }

    private boolean actionSpeakNavigationInstruction(
            final boolean speakInstruction,
            final boolean showInformation,
            final long delayMs) {
        return NavigationNotification.requestAction(this, speakInstruction, showInformation, delayMs);
    }
}
