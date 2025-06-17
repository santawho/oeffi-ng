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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpeechInput {
    public interface ResultListener {
        boolean onSpeechInputResult(final String spokenSentence);
    }

    public interface SpeechInputTerminationListener {
        void onSpeechInputTermination(boolean success);
    }

    public interface CommandProcessor {
        boolean onVoiceCommandDetected(final Map<String, String> fields);
    }

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    public class CommandDefinition implements ResultListener {
        final String name;
        protected List<String> fieldNames = new ArrayList<>();
        protected Map<String, List<Pattern>> languageToPatterns = new HashMap<>();
        protected CommandProcessor commandProcessor;
        protected String currentLanguage;

        protected CommandDefinition(final String commandName) {
            name = commandName;
            currentLanguage = "";
            languageToPatterns.put(currentLanguage, new ArrayList<>());
        }

        public CommandDefinition field(final String fieldName) {
            fieldNames.add(fieldName);
            return this;
        }

        public CommandDefinition language(final String lang) {
            currentLanguage = lang;
            languageToPatterns.put(currentLanguage, new ArrayList<>());
            return this;
        }

        public CommandDefinition pattern(final String patternString) {
            languageToPatterns.get(currentLanguage).add(Pattern.compile(patternString));
            return this;
        }

        public CommandDefinition action(final CommandProcessor processor) {
            commandProcessor = processor;
            return this;
        }

        @Override
        public boolean onSpeechInputResult(final String spokenSentence) {
            final String activeLanguage = getActiveLanguage();
            List<Pattern> patterns = languageToPatterns.get(activeLanguage);
            if (patterns == null)
                patterns = languageToPatterns.get("");
            if (patterns == null)
                return false;
            for (Pattern pattern : patterns) {
                final Matcher matcher = pattern.matcher(spokenSentence);
                if (matcher.find()) {
                    final Map<String, String> fields = new HashMap<>();
                    for (String fieldName : fieldNames) {
                        try {
                            final String value = matcher.group(fieldName);
                            fields.put(fieldName, value);
                        } catch (IllegalArgumentException e) {
                            // do not populate that field
                        }
                    }
                    if (commandProcessor != null) {
                        if (commandProcessor.onVoiceCommandDetected(fields)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    public CommandDefinition command(final String commandName) {
        final CommandDefinition commandDefinition = new CommandDefinition(commandName);
        addResultListener(commandDefinition);
        return commandDefinition;
    }

    public SpeechInput(final Context context) {
        this.context = context;
    }

    protected final Context context;
    private final List<ResultListener> resultListeners = new ArrayList<>();
    private boolean isSpeechRecognitionRunning;
    private SpeechRecognizer speechRecognizer;

    protected Locale getActiveLocale() {
        return context.getResources().getConfiguration().getLocales().get(0);
    }

    protected String getActiveLanguage() {
        return getActiveLocale().getLanguage();
    }

    public void addResultListener(final ResultListener listener) {
        resultListeners.add(listener);
    }

    public void startSpeechRecognition(final Activity activity, final SpeechInputTerminationListener terminationListener) {
        if (isSpeechRecognitionRunning)
            return;

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
                            if (!isSpeechRecognitionRunning)
                                stopSpeechRecognition();
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
                                final String spokenSentence = results.get(0).toLowerCase(getActiveLocale());
                                dispatchSpokenSentence(spokenSentence, terminationListener);
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

    protected boolean dispatchSpokenSentence(
            final String spokenSentence,
            final SpeechInputTerminationListener terminationListener) {
        stopSpeechRecognition();
        final boolean success = dispatchSpokenSentence(spokenSentence);
        if (terminationListener != null) {
            terminationListener.onSpeechInputTermination(success);
        }
        return success;
    }

    public boolean dispatchSpokenSentence(final String spokenSentence) {
        log.debug("speech input: {}", spokenSentence);
        for (ResultListener resultListener : resultListeners) {
            if (resultListener.onSpeechInputResult(spokenSentence)) {
                return true;
            }
        }
        return false;
    }
}
