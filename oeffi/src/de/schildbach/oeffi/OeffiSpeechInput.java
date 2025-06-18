package de.schildbach.oeffi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.stations.StationsActivity;
import de.schildbach.oeffi.util.SpeechInput;

public class OeffiSpeechInput extends SpeechInput {
    public OeffiSpeechInput(final Context context) {
        super(context);

        command("directions")
                .field("FROM")
                .field("TO")
                .field("VIA")
                .pattern("from (?<FROM>.+) to (?<TO>.+) via (?<VIA>.+)")
                .pattern("from (?<FROM>.+) via (?<VIA>.+) to (?<TO>.+)")
                .pattern("from (?<FROM>.+) to (?<TO>.+)")
                .language("de")
                .pattern("von (?<FROM>.+) nach (?<TO>.+) über (?<VIA>.+)")
                .pattern("von (?<FROM>.+) über (?<VIA>.+) nach (?<TO>.+)")
                .pattern("von (?<FROM>.+) nach (?<TO>.+)")
                .action((aContext, fields) -> {
                    final DirectionsActivity.Command command = new DirectionsActivity.Command();
                    command.fromText = fields.get("FROM");
                    command.toText = fields.get("TO");
                    command.viaText = fields.get("VIA");
                    command.time = new TimeSpec.Relative(0);
                    log.info("speech control: directions from {} via {} to {}", command.fromText, command.viaText, command.toText);
                    DirectionsActivity.start(aContext, command, Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    return true;
                });

        command("stations")
                .field("LOC")
                .pattern("departures( at)? (?<LOC>.+)")
                .language("de")
                .pattern("abfahrten( in)? (?<LOC>.+)")
                .action((aContext, fields) -> {
                    final StationsActivity.Command command = new StationsActivity.Command();
                    command.atText = fields.get("LOC");
                    command.time = new TimeSpec.Relative(0);
                    log.info("speech control: departures at {}", command.atText);
                    StationsActivity.start(aContext, command, Intent.FLAG_ACTIVITY_CLEAR_TASK);
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
