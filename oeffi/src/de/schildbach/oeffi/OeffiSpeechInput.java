package de.schildbach.oeffi;

import android.app.Activity;
import android.content.Context;

import de.schildbach.oeffi.util.SpeechInput;

public class OeffiSpeechInput extends SpeechInput {
    public OeffiSpeechInput(final Context context) {
        super(context);

        command("directions")
                .field("FROM")
                .field("TO")
                .field("VIA")
                .pattern("from (?<FROM>.+) to (?<TO>.+) via (?<VIA>.+)")
                .pattern("from (?<FROM>.+) via (?<TO>.+) to (?<VIA>.+)")
                .pattern("from (?<FROM>.+) to (?<TO>.+)")
                .language("de")
                .pattern("von (?<FROM>.+) nach (?<TO>.+) über (?<VIA>.+)")
                .pattern("von (?<FROM>.+) über (?<TO>.+) nach (?<VIA>.+)")
                .pattern("von (?<FROM>.+) nach (?<TO>.+)")
                .action(fields -> {
                    final String from = fields.get("FROM");
                    final String to = fields.get("TO");
                    final String via = fields.get("VIA");
                    log.info("speech control: directions from {} via {} to {}", from, via, to);
                    return true;
                });

        command("stations")
                .field("LOC")
                .pattern("departures( at)? (?<LOC>.+)")
                .language("de")
                .pattern("abfahrten( in)? (?<LOC>.+)")
                .action(fields -> {
                    final String loc = fields.get("LOC");
                    log.info("speech control: departures at {}", loc);
                    return true;
                });
    }

    @Override
    public void startSpeechRecognition(final Activity activity, final SpeechInputTerminationListener terminationListener) {
        super.startSpeechRecognition(activity, terminationListener);

//        final String result =
////        "von Hamburg nach Hofheim über Limburg"
////        "von Hamburg über Limburg nach Hofheim"
////        "von Hamburg nach Hofheim"
//                "from Hamburg to Hofheim"
////        "Abfahrten in Hofheim"
//        ;
//        dispatchSpokenSentence(result.toLowerCase(getActiveLocale()), terminationListener);

    }
}
