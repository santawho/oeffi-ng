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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.ResourceUri;

public class NotificationSoundManager {
    public static final String PREF_KEY_MEDIA_CHANNEL_AMPLIFICATION = "navigation_media_channel_amplification";

    private static NotificationSoundManager instance;
    private static final Logger log = LoggerFactory.getLogger(NotificationSoundManager.class);

    public static NotificationSoundManager getInstance() {
        if (instance == null) {
            instance = new NotificationSoundManager();
        }
        return instance;
    }

    private final HandlerThread backgroundThread;
    private final Handler backgroundHandler;
    private TextToSpeech textToSpeech;
    private boolean isTextToSpeechUp;
    private AudioFocusRequest currentAudioFocusRequest;
    private int savedVolume = -1;

    private final ArrayList<Speakable> speakableQueue = new ArrayList<>();

    public static class Speakable {
        public final String text;
        public final int stream;

        public Speakable(final String text, final int stream) {
            this.text = text;
            this.stream = stream;
        }
    }

    public static void logAvailableTextToSpeechServices() {
        final Application application = Application.getInstance();
        final PackageManager packageManager = application.getPackageManager();
        final List<ResolveInfo> resolveInfos = packageManager
                .queryIntentServices(new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0);
        String defaultEngine = Settings.Secure.getString(application.getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH);
        if (defaultEngine == null && !resolveInfos.isEmpty())
            defaultEngine = resolveInfos.get(0).serviceInfo.packageName;
        for (final ResolveInfo resolveInfo : resolveInfos) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            final ComponentName componentName = new ComponentName(serviceInfo.packageName, serviceInfo.name);
            final CharSequence label = serviceInfo.loadLabel(packageManager);
            final String engineName = TextUtils.isEmpty(label) ? serviceInfo.name : label.toString();
            final boolean isDefault = serviceInfo.packageName.equals(defaultEngine);
            Application.log.info((isDefault ? "default" : "available") + " TTS service: '" + engineName + "', " + componentName.flattenToString());
        }
    }

    private NotificationSoundManager() {
        backgroundThread = new HandlerThread("NavAlarmThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        textToSpeech = new TextToSpeech(getContext(), status -> {
            isTextToSpeechUp = status == TextToSpeech.SUCCESS;
            if (isTextToSpeechUp) {
                final String localeName = Application.getInstance().getString(R.string.locale);
                final Locale locale = new Locale(localeName);
                textToSpeech.setLanguage(locale);
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(final String utteranceId) {
//                        onSoundOutputStart();
                    }

                    @Override
                    public void onDone(final String utteranceId) {
                        onEnd(utteranceId, false);
                    }

                    @Override
                    public void onError(final String utteranceId) {
                        onEnd(utteranceId, true);
                    }

                    private void onEnd(final String utteranceId, final boolean isError) {
//                        if (!textToSpeech.isSpeaking())
//                            onSoundOutputEnd();
                    }
                });

                speakQueue();
            }
        });
    }

    private Context getContext() {
        return Application.getInstance();
    }

    private AudioManager getAudioManager() {
        return (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    public void playAlarmSoundAndVibration(
            final int soundUsage,
            final int soundId,
            final long[] vibrationPattern,
            final List<String> speakTexts) {
        backgroundHandler.post(() -> {
            internPlayAlarmSoundAndVibration(soundUsage, soundId, vibrationPattern, speakTexts);
        });
    }

    private void internPlayAlarmSoundAndVibration(
            final int soundUsage,
            final int soundId,
            final long[] vibrationPattern,
            final List<String> speakTexts) {
        final Context context = getContext();
        if (vibrationPattern != null) {
            ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(vibrationPattern, -1);
        }

        final boolean playSound = soundId != 0;
        final boolean playSpeech = speakTexts != null && !speakTexts.isEmpty();

        if (playSound) {
            final AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(soundUsage)
                    .build();
            final Ringtone alarmTone = RingtoneManager.getRingtone(context, ResourceUri.fromResource(context, soundId));
            alarmTone.setAudioAttributes(audioAttributes);
            requestAudioFocus(audioAttributes, playSpeech);
            log.info("sound starts playing: {} usage {}", soundId, soundUsage);
            alarmTone.play();
            while (alarmTone.isPlaying()) {
//                log.info("sound is playing");
                try {
                    Thread.sleep(100);
                } catch (final InterruptedException ie) {
                    break;
                }
            }
            log.info("sound has stopped: {} usage {}", soundId, soundUsage);
        }

        if (playSpeech) {
            if (!playSound) {
                final AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(soundUsage)
                        .build();
                requestAudioFocus(audioAttributes, playSpeech);
            }
            boolean isSpeaking = false;
            final int audioStream = getAudioStreamFromUsage(soundUsage);
            for (final String text : speakTexts) {
                log.info("speaking: \"{}\" stream {}", text, audioStream);
                isSpeaking |= speak(text, audioStream);
            }
            while (textToSpeech.isSpeaking()) {
//                log.info("speech is speaking");
                try {
                    Thread.sleep(100);
                } catch (final InterruptedException ie) {
                    break;
                }
            }
        }

        abandonAudioFocusRequest();
    }

    private void requestAudioFocus(final AudioAttributes audioAttributes, final boolean exclusive) {
        if (currentAudioFocusRequest != null)
            return;

        log.info("requesting audio focus");
        final AudioManager audioManager = getAudioManager();
        currentAudioFocusRequest =
                new AudioFocusRequest.Builder(
                            exclusive
                                ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                                : AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(false)
                        .build();
        try {
            audioManager.requestAudioFocus(currentAudioFocusRequest);
        } catch (final RuntimeException rte) {
            log.warn("requesting audio focus", rte);
        }

        if (exclusive && audioAttributes.getUsage() == AudioAttributes.USAGE_MEDIA && savedVolume < 0) {
            final int amplification = Application.getInstance().getSharedPreferences()
                    .getInt(PREF_KEY_MEDIA_CHANNEL_AMPLIFICATION, 0);
            if (amplification > 0) {
                try {
                    Thread.sleep(200);
                } catch (final InterruptedException rte) {
                    // ignore
                }
                final int volumeBeforePlaying = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                final int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                savedVolume = volumeBeforePlaying;
                final int playVolume = (int) ((float) (maxVolume - volumeBeforePlaying)
                        * (float) amplification / 100.0
                        + (float) volumeBeforePlaying
                        + 0.49);
                log.info("set volume {}, saving = {}", playVolume, savedVolume);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, playVolume, 0);
            } else {
                log.info("do not reduce to volume");
            }
        }
    }

    private void abandonAudioFocusRequest() {
        if (currentAudioFocusRequest == null)
            return;

        log.info("abandoning audio focus");
        final AudioManager audioManager = getAudioManager();

        if (savedVolume >= 0) {
            log.info("reset volume {}", savedVolume);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
            savedVolume = -1;
        } else {
            log.info("do not reset volume");
        }

        try {
            audioManager.abandonAudioFocusRequest(currentAudioFocusRequest);
        } catch (final RuntimeException rte) {
            log.warn("abandoning audio focus", rte);
        }
        currentAudioFocusRequest = null;
    }

    private static int getAudioStreamFromUsage(final int soundUsage) {
        switch (soundUsage) {
            case AudioAttributes.USAGE_ALARM:
                return AudioManager.STREAM_ALARM;
            case AudioAttributes.USAGE_MEDIA:
                return AudioManager.STREAM_MUSIC;
            case AudioAttributes.USAGE_NOTIFICATION_EVENT:
            case AudioAttributes.USAGE_NOTIFICATION:
            case AudioAttributes.USAGE_NOTIFICATION_RINGTONE:
            default:
                return AudioManager.STREAM_NOTIFICATION;
        }
    }

    public boolean isHeadsetConnected() {
        final AudioDeviceInfo[] devices = getAudioManager().getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (final AudioDeviceInfo device : devices) {
            final int deviceType = device.getType();
            switch (deviceType) {
                case AudioDeviceInfo.TYPE_HEARING_AID:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    public boolean speak(final String text, final int audioStream) {
        return speak(new Speakable(text, audioStream));
    }

    public boolean speak(final Speakable speakable) {
        synchronized (speakableQueue) {
            speakableQueue.add(speakable);
        }

        if (!isTextToSpeechUp)
            return false;

        return speakQueue();
    }

    private boolean speakQueue() {
        for (;;) {
            final Speakable speakable;
            synchronized (speakableQueue) {
                if (speakableQueue.isEmpty())
                    break;

                speakable = speakableQueue.get(0);
            }
            if (!speakSync(speakable))
                return false;
            synchronized (speakableQueue) {
                if (!speakableQueue.isEmpty())
                    speakableQueue.remove(0);
            }
        }
        return true;
    }

    private boolean speakSync(final Speakable speakable) {
        final Bundle params = new Bundle();
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, speakable.stream);

        final String utteranceId = NotificationSoundManager.class.getName();

        return TextToSpeech.SUCCESS ==
                textToSpeech.speak(speakable.text, TextToSpeech.QUEUE_ADD, params, utteranceId);
    }
}
