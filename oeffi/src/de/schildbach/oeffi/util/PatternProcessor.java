package de.schildbach.oeffi.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.schildbach.oeffi.Application;

public abstract class PatternProcessor implements SpeechInput.ResultListener {
    private final int[] patternIds;
    private Matcher matcher;

    public PatternProcessor(final int[] patternIds) {
        this.patternIds = patternIds;
    }

    public String getField(final String name) {
        try {
            return matcher.group(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public abstract boolean process();

    @Override
    public boolean onSpeechInputResult(final String spokenSentence) {
        for (int patternId : patternIds) {
            matcher = Pattern
                    .compile(Application.getInstance().getResources().getString(patternId))
                    .matcher(spokenSentence);
            if (matcher.find()) {
                if (process())
                    return true;
            }
        }

        return false;
    }
}
