package de.schildbach.oeffi.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpeechInput {
    public interface ResultListener {
        boolean onSpeechInputResult(final String spokenSentence);
    }

    public interface SpeechInputTerminationListener {
        void onSpeechInputTermination(boolean success);
    }

    private static final Logger log = LoggerFactory.getLogger(SpeechInput.class);

    private final List<ResultListener> resultListeners = new ArrayList<>();
    private boolean isSpeechRecognitionRunning;
    private SpeechRecognizer speechRecognizer;

    public SpeechInput(final Context context) {
    }

    public void addResultListener(final ResultListener listener) {
        resultListeners.add(listener);
    }

    public void addResultListeners(final ResultListener[] listeners) {
        for (ResultListener listener : listeners)
            resultListeners.add(listener);
    }

    public void startSpeechRecognition(final Activity activity, final SpeechInputTerminationListener terminationListener) {
        if (isSpeechRecognitionRunning)
            return;

//        final String result =
////        "von Hamburg nach Hofheim über Limburg";
////        "von Hamburg über Limburg nach Hofheim";
//        "von Hamburg nach Hofheim";
////        "Abfahrten in Hofheim";
//        dispatchSpokenSentence(result.toLowerCase(activity.getResources().getConfiguration().getLocales().get(0)));

        if (speechRecognizer == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
            } else if (SpeechRecognizer.isRecognitionAvailable(activity)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            } else {
                // not available
            }

            if (speechRecognizer != null) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                } else {
                    speechRecognizer.setRecognitionListener(new RecognitionListener() {
                        @Override
                        public void onReadyForSpeech(final Bundle params) {
                        }

                        @Override
                        public void onBeginningOfSpeech() {
                        }

                        @Override
                        public void onRmsChanged(final float rmsdB) {
                        }

                        @Override
                        public void onBufferReceived(final byte[] buffer) {
                        }

                        @Override
                        public void onEndOfSpeech() {
                            isSpeechRecognitionRunning = false;
                        }

                        @Override
                        public void onError(final int error) {
                            log.info("speech recognition error {}", error);
                            isSpeechRecognitionRunning = false;
                            if (terminationListener != null) {
                                terminationListener.onSpeechInputTermination(false);
                            }
                        }

                        @Override
                        public void onPartialResults(final Bundle partialResults) {
                        }

                        @Override
                        public void onEvent(final int eventType, final Bundle params) {
                        }

                        @Override
                        public void onResults(final Bundle bundle) {
                            isSpeechRecognitionRunning = false;
                            final ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            if (results != null && !results.isEmpty()) {
                                final Locale locale = activity.getResources().getConfiguration().getLocales().get(0);
                                final String spokenSentence = results.get(0).toLowerCase(locale);
                                final boolean success = dispatchSpokenSentence(spokenSentence);
                                if (terminationListener != null) {
                                    terminationListener.onSpeechInputTermination(success);
                                }
                            }
                        }
                    });
                }
            }
        }

        if (speechRecognizer != null) {
            isSpeechRecognitionRunning = true;
            speechRecognizer.startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM));
        }
    }

    public void stopSpeechRecognition() {
        if (!isSpeechRecognitionRunning)
            return;

        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }

        isSpeechRecognitionRunning = false;
    }

    public boolean dispatchSpokenSentence(final String spokenSentence) {
        log.debug("speech input: {}", spokenSentence);
        for (ResultListener resultListener : resultListeners) {
            if (resultListener.onSpeechInputResult(spokenSentence))
                return true;
        }
        return false;
    }
}
