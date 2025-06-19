package de.schildbach.oeffi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;

import java.util.Calendar;
import java.util.Map;

import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.util.TimeSpec;
import de.schildbach.oeffi.stations.StationsActivity;
import de.schildbach.oeffi.util.SpeechInput;

public class OeffiSpeechInput extends SpeechInput {
    private final String TIME_PATTERN_EN = "((at (?<ABSHOUR>~N)( ((?<ABSMINUTE>~N)|o'clock))?)|(in (?<RELMINUTES>~N) minutes?)|(in (?<RELHOURS>~N) hours?))";
//    private final String TIME_PATTERN_DE = "((um (?[0-9][0-9]?):([0-9][0-9]) uhr)|(um (?<ABSHOUR>~N) uhr( (?<ABSMINUTE>~N))?)|(in (?<RELONEMINUTE>einer) minute)|(in (?<RELMINUTES>~N) minuten)|(in (?<RELONEHOUR>einer) stunde)|(in (?<RELHOURS>~N) stunden))";
    private final String TIME_PATTERN_DE = "((um (?<ABSHOUR>~N)(:(?<ABSMINUTE>~N))? uhr)|(in (?<RELONEMINUTE>einer) minute)|(in (?<RELMINUTES>~N) minuten)|(in (?<RELONEHOUR>einer) stunde)|(in (?<RELHOURS>~N) stunden))";

    public class CommandWithTimeDefinition extends SpeechInput.CommandDefinition<CommandWithTimeDefinition> {
        protected CommandWithTimeDefinition(final String commandName) {
            super(commandName);
            this.field("ABSHOUR")
                .field("ABSMINUTE")
                .field("RELONEMINUTE")
                .field("RELMINUTES")
                .field("RELONEHOUR")
                .field("RELHOURS");
        }

        private TimeSpec getTimeSpec(final Map<String, String> fields, final String language) {
            String value;
            TimeSpec timeSpec = null;
            if (fields.get("RELONEMINUTE") != null) {
                timeSpec = new TimeSpec.Relative(DateUtils.MINUTE_IN_MILLIS);
            } else if (fields.get("RELONEHOUR") != null) {
                timeSpec = new TimeSpec.Relative(DateUtils.HOUR_IN_MILLIS);
            } else if ((value = fields.get("RELMINUTES")) != null) {
                final Integer minutes = getNumberFromWord(value, language);
                if (minutes != null)
                    timeSpec = new TimeSpec.Relative(minutes * DateUtils.MINUTE_IN_MILLIS);
            } else if ((value = fields.get("RELHOURS")) != null) {
                final Integer hours = getNumberFromWord(value, language);
                if (hours != null)
                    timeSpec = new TimeSpec.Relative(hours * DateUtils.HOUR_IN_MILLIS);
            } else if ((value = fields.get("ABSHOUR")) != null) {
                final Integer hour = getNumberFromWord(value, language);
                final Integer minute = getNumberFromWord(fields.get("ABSMINUTE"), language);
                if (hour != null) {
                    final Calendar calendar = Calendar.getInstance();
                    final int nowHour = calendar.get(Calendar.HOUR_OF_DAY);
                    if (nowHour >= 18 && hour < 12) {
                        // tomorrow
                        calendar.add(Calendar.DATE, 1);
                    }
                    calendar.set(Calendar.HOUR_OF_DAY, hour);
                    calendar.set(Calendar.MINUTE, minute == null ? 0 : minute);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    timeSpec = new TimeSpec.Absolute(TimeSpec.DepArr.DEPART, calendar.getTimeInMillis());
                }
            }

            return timeSpec;
        }
    }
    private CommandWithTimeDefinition commandWithTime(final String commandName) {
        return addCommand(new CommandWithTimeDefinition(commandName));
    }

    public OeffiSpeechInput(final Context context) {
        super(context);

        commandWithTime("directions")
                .field("FROM")
                .field("TO")
                .field("VIA")
                .language("en")
                .pattern("("+ TIME_PATTERN_EN + " )?from (?<FROM>.+) to (?<TO>.+) via (?<VIA>.+)")
                .pattern("("+ TIME_PATTERN_EN + " )?from (?<FROM>.+) via (?<VIA>.+) to (?<TO>.+)")
                .pattern("("+ TIME_PATTERN_EN + " )?from (?<FROM>.+) to (?<TO>.+)")
                .language("de")
                .pattern("("+ TIME_PATTERN_DE + " )?von (?<FROM>.+) nach (?<TO>.+) über (?<VIA>.+)")
                .pattern("("+ TIME_PATTERN_DE + " )?von (?<FROM>.+) über (?<VIA>.+) nach (?<TO>.+)")
                .pattern("("+ TIME_PATTERN_DE + " )?von (?<FROM>.+) nach (?<TO>.+)")
                .action((aContext, commandDefinition, fields, language) -> {
                    final DirectionsActivity.Command command = new DirectionsActivity.Command();
                    command.fromText = fields.get("FROM");
                    command.toText = fields.get("TO");
                    command.viaText = fields.get("VIA");
                    command.time = commandDefinition.getTimeSpec(fields, language);
                    if (command.time == null)
                        command.time = new TimeSpec.Relative(0);
                    log.info("speech control: directions from {} via {} to {}", command.fromText, command.viaText, command.toText);
                    DirectionsActivity.start(aContext, command, Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    return true;
                });

        commandWithTime("stations")
                .field("LOC")
                .language("en")
                .pattern("departures " + TIME_PATTERN_EN + " at (?<LOC>.+)")
                .pattern("departures( at)? (?<LOC>.+)")
                .language("de")
                .pattern("abfahrten " + TIME_PATTERN_DE + " in (?<LOC>.+)")
                .pattern("abfahrten( in)? (?<LOC>.+)")
                .action((aContext, commandDefinition, fields, language) -> {
                    final StationsActivity.Command command = new StationsActivity.Command();
                    command.atText = fields.get("LOC");
                    command.time = commandDefinition.getTimeSpec(fields, language);
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
