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

import android.content.Context;
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
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.ResourceUri;

public class NotificationSoundManager {
    private static NotificationSoundManager instance;
    private static final Logger log = LoggerFactory.getLogger(NotificationSoundManager.class);

    public static NotificationSoundManager getInstance() {
        if (instance == null) {
            instance = new NotificationSoundManager();
        }
        return instance;
    }

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private TextToSpeech textToSpeech;
    private boolean isTextToSpeechUp;
    private AudioFocusRequest currentAudioFocusRequest;
    private final ArrayList<Speakable> speakableQueue = new ArrayList<>();

    public static class Speakable {
        public final String text;
        public final int stream;

        public Speakable(final String text, final int stream) {
            this.text = text;
            this.stream = stream;
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
                final int result = textToSpeech.setLanguage(locale);
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(final String utteranceId) {
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
                        if (!textToSpeech.isSpeaking())
                            onSpeechEnd();
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
        if (soundId != 0) {
            final AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(soundUsage)
                    .build();
            final Ringtone alarmTone = RingtoneManager.getRingtone(context, ResourceUri.fromResource(context, soundId));
            alarmTone.setAudioAttributes(audioAttributes);
            requestAudioFocus(audioAttributes);
            log.info("sound starts playing: {} usage {}", soundId, soundUsage);
            alarmTone.play();
            while (alarmTone.isPlaying()) {
//                log.info("sound is playing");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            log.info("sound has stopped: {} usage {}", soundId, soundUsage);
            boolean isSpeaking = false;
            if (speakTexts != null && !speakTexts.isEmpty()) {
                final int audioStream = getAudioStreamFromUsage(soundUsage);
                for (String text : speakTexts) {
                    log.info("speaking: \"{}\" stream {}", text, audioStream);
                    isSpeaking |= speak(text, audioStream);
                }
            }
            while (textToSpeech.isSpeaking()) {
//                log.info("speech is speaking");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            abandonAudioFocusRequest();
        }
    }

    private void requestAudioFocus(final AudioAttributes audioAttributes) {
        if (currentAudioFocusRequest != null)
            return;

        log.info("requesting audio focus");
        final AudioManager audioManager = getAudioManager();
        currentAudioFocusRequest =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(false)
                        .build();
        audioManager.requestAudioFocus(currentAudioFocusRequest);
    }

    private void abandonAudioFocusRequest() {
        if (currentAudioFocusRequest == null)
            return;

        log.info("abandoning audio focus");
        final AudioManager audioManager = getAudioManager();
        audioManager.abandonAudioFocusRequest(currentAudioFocusRequest);
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
        for (AudioDeviceInfo device : devices) {
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
            Speakable speakable;
            synchronized (speakableQueue) {
                if (speakableQueue.isEmpty())
                    break;

                speakable = speakableQueue.get(0);
            }
            if (!speakSync(speakable))
                return false;
            synchronized (speakableQueue) {
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

    private void onSpeechEnd() {
//        abandonAudioFocusRequest();
    }
}
