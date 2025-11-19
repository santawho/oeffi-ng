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

package de.schildbach.oeffi.util;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;

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

import de.schildbach.oeffi.Application;

public class SpeechInput {
    public interface ResultListener {
        boolean onSpeechInputResult(final Context activityContext, final String spokenSentence);
    }

    public interface SpeechInputTerminationListener {
        void onSpeechInputTermination(boolean success);
        void onSpeechInputError(final String hint);
    }

    public interface CommandProcessor<CmdDef extends CommandDefinition> {
        boolean onVoiceCommandDetected(
                final Context activityContext,
                final CmdDef commandDefinition,
                final Map<String, String> fields,
                final String language);
    }

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    public class CommandDefinition<CmdDef extends CommandDefinition<?>> implements ResultListener {
        final String name;
        protected List<String> fieldNames = new ArrayList<>();
        protected Map<String, List<Pattern>> languageToPatterns = new HashMap<>();
        protected CommandProcessor commandProcessor;
        protected String currentLanguage;
        protected List<Pattern> currentPatterns;
        protected String currentNumberPattern;

        protected CommandDefinition(final String commandName) {
            name = commandName;
        }

        public CommandDefinition<CmdDef> field(final String fieldName) {
            fieldNames.add(fieldName);
            return this;
        }

        public CommandDefinition<CmdDef> language(final String lang) {
            currentLanguage = lang;
            currentPatterns = new ArrayList<>();
            languageToPatterns.put(currentLanguage, currentPatterns);
            currentNumberPattern = buildNumberPattern(lang);
            return this;
        }

        private String buildNumberPattern(final String lang) {
            String[] numberWords = getNumberWords(lang);
            if (numberWords == null)
                numberWords = getNumberWords(getFallbackLanguage());
            final StringBuilder builder = new StringBuilder();
            builder.append("(([0-9]+)");
            for (final String numberWord : numberWords)
                builder.append(String.format("|(%s)", numberWord));
            builder.append(")");
            return builder.toString();
        }

        public CommandDefinition<CmdDef> pattern(final String aPatternString) {
            final String patternString = String.format("^%s$", aPatternString
                    .replaceAll("~N", currentNumberPattern));
            currentPatterns.add(Pattern.compile(patternString));
            return this;
        }

        public CommandDefinition<CmdDef> action(final CommandProcessor<CmdDef> processor) {
            commandProcessor = processor;
            return this;
        }

        @Override
        public boolean onSpeechInputResult(final Context activityContext, final String spokenSentence) {
            String activeLanguage = getActiveLanguage();
            List<Pattern> patterns = languageToPatterns.get(activeLanguage);
            if (patterns == null) {
                activeLanguage = getFallbackLanguage();
                patterns = languageToPatterns.get(activeLanguage);
            }
            if (patterns == null)
                return false;
            for (final Pattern pattern : patterns) {
                final Matcher matcher = pattern.matcher(spokenSentence);
                if (matcher.find()) {
                    final Map<String, String> fields = new HashMap<>();
                    for (final String fieldName : fieldNames) {
                        try {
                            final String value = matcher.group(fieldName);
                            fields.put(fieldName, value);
                        } catch (final IllegalArgumentException iae) {
                            // do not populate that field
                        }
                    }
                    if (commandProcessor != null) {
                        if (commandProcessor.onVoiceCommandDetected(activityContext, this, fields, activeLanguage)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    public CommandDefinition<?> command(final String commandName) {
        return addCommand(new CommandDefinition(commandName));
    }

    public <CmdDef extends CommandDefinition<?>> CmdDef addCommand(final CmdDef commandDefinition) {
        addResultListener(commandDefinition);
        return commandDefinition;
    }

    public static void logAvailableSpeechRecognitionServices() {
        final Application application = Application.getInstance();
        final String defaultEngine = Settings.Secure.getString(application.getContentResolver(), "voice_recognition_service");
        final PackageManager packageManager = application.getPackageManager();
        final List<ResolveInfo> resolveInfos = packageManager
                .queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), PackageManager.ResolveInfoFlags.of(0));
        for (final ResolveInfo resolveInfo : resolveInfos) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            final ComponentName componentName = new ComponentName(serviceInfo.packageName, serviceInfo.name);
            final CharSequence label = serviceInfo.loadLabel(packageManager);
            final String engineName = TextUtils.isEmpty(label) ? serviceInfo.name : label.toString();
            final boolean isDefault = componentName.flattenToString().equals(defaultEngine);
            Application.log.info((isDefault ? "default" : "available") + " voice recognition service: '" + engineName + "', " + componentName.flattenToString());
        }
//        final ComponentName defaultSpeechService = ComponentName.unflattenFromString(serviceComponent);
    }

    public SpeechInput(final Context context) {
        this.context = context;
    }

    protected final Context context;
    private final List<ResultListener> resultListeners = new ArrayList<>();
    private boolean isSpeechRecognitionRunning;
    private SpeechRecognizer speechRecognizer;
    private Activity activityContext;

    protected Locale getActiveLocale() {
        return context.getResources().getConfiguration().getLocales().get(0);
    }

    protected String getActiveLanguage() {
        return getActiveLocale().getLanguage();
    }

    protected String getFallbackLanguage() {
        return "en";
    }

    protected String[] getNumberWords(final String language) {
        if ("en".equals(language))
            return new String[] {
                    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"
            };

        if ("de".equals(language))
            return new String[] {
                    "null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun"
            };

        return null;
    }

    protected Integer getNumberFromWord(final String numberWord, final String language) {
        if (numberWord == null || numberWord.isEmpty())
            return null;
        if (Character.isDigit(numberWord.charAt(0))) {
            try {
                return Integer.parseInt(numberWord);
            } catch (final NumberFormatException nfe) {
                return null;
            }
        }
        String[] numberWords = getNumberWords(language);
        if (numberWords == null)
            numberWords = getNumberWords(getFallbackLanguage());
        for (int i = 0; i < numberWords.length; i++) {
            final String word = numberWords[i];
            if (numberWord.equals(word))
                return i;
        }
        return null;
    }

    public void addResultListener(final ResultListener listener) {
        resultListeners.add(listener);
    }

    public void startSpeechRecognition(final Activity activityContext, final SpeechInputTerminationListener terminationListener) {
        if (isSpeechRecognitionRunning)
            return;

        if (speechRecognizer == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(activityContext)) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activityContext);
            } else if (SpeechRecognizer.isRecognitionAvailable(activityContext)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activityContext);
            } else {
                // not available
                log.warn("no speech recognizer available");
            }

            if (speechRecognizer != null) {
                if (ContextCompat.checkSelfPermission(activityContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                    ActivityCompat.requestPermissions(activityContext, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                    return;
                }

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
                            if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                                    || error == SpeechRecognizer.ERROR_NO_MATCH) {
                                terminationListener.onSpeechInputTermination(false);
                            } else {
                                terminationListener.onSpeechInputError(String.format("code=%d", error));
                            }
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

        if (speechRecognizer != null) {
            isSpeechRecognitionRunning = true;
            this.activityContext = activityContext;
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
        for (final ResultListener resultListener : resultListeners) {
            if (resultListener.onSpeechInputResult(activityContext, spokenSentence)) {
                return true;
            }
        }
        return false;
    }
}
