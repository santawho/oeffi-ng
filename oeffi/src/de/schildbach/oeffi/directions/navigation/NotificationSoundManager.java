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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Vibrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.oeffi.Application;
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

    private NotificationSoundManager() {
        backgroundThread = new HandlerThread("NavAlarmThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private Context getContext() {
        return Application.getInstance();
    }

    private AudioManager getAudioManager() {
        return (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    public void playAlarmSoundAndVibration(final int soundUsage, final int soundId, final long[] vibrationPattern) {
        backgroundHandler.post(() -> {
            internPlayAlarmSoundAndVibration(soundUsage, soundId, vibrationPattern);
        });
    }

    private void internPlayAlarmSoundAndVibration(final int soundUsage, final int soundId, final long[] vibrationPattern) {
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
            final AudioManager audioManager = getAudioManager();
            final AudioFocusRequest audioFocusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(audioAttributes)
                            .setAcceptsDelayedFocusGain(false)
                            .setWillPauseWhenDucked(false)
                            .build();
            audioManager.requestAudioFocus(audioFocusRequest);
            log.info("sound starts playing: {} usage {}", soundId, soundUsage);
            alarmTone.play();
            while (alarmTone.isPlaying()) {
//                    log.info("sound is playing");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            log.info("sound has stopped: {} usage {}", soundId, soundUsage);
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
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
}
