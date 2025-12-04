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

package de.schildbach.oeffi.stations;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.Style;
import de.schildbach.pte.dto.PTDate;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;

public class NearestFavoriteStationWidgetListService extends RemoteViewsService {
    public static final String INTENT_EXTRA_DEPARTURES = RemoteViewsFactory.class.getName() + ".departures";
    public static final String INTENT_EXTRA_CANSHOWJOURNEYS = RemoteViewsFactory.class.getName() + ".canShowJourneys";

    public static Intent getStartIntent(
            final Context context,
            final int appWidgetId,
            final List<Departure> departures,
            final boolean canShowJourneys) {
        final Intent intent = new Intent(context, NearestFavoriteStationWidgetListService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra(INTENT_EXTRA_DEPARTURES,
                Objects.serialize((Serializable) departures));
        intent.putExtra(INTENT_EXTRA_DEPARTURES + ".hash",
                departures.hashCode());
        intent.putExtra(INTENT_EXTRA_CANSHOWJOURNEYS, canShowJourneys);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        return intent;
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(final Intent intent) {
        return new RemoteViewsFactory(this.getApplicationContext(), intent);
    }

    private static class RemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private final Context context;
        private final java.text.DateFormat timeFormat;
        private final List<Departure> departures;
        private final boolean canShowJourneys;

        public RemoteViewsFactory(final Context context, final Intent intent) {
            this.context = context;
            this.timeFormat = DateFormat.getTimeFormat(context);
            this.departures = (List<Departure>) Objects.deserialize(intent.getByteArrayExtra(INTENT_EXTRA_DEPARTURES));
            this.canShowJourneys = intent.getBooleanExtra(INTENT_EXTRA_CANSHOWJOURNEYS, false);
        }

        @Override
        public void onCreate() {
        }

        @Override
        public void onDestroy() {
        }

        @Override
        public void onDataSetChanged() {
        }

        @Override
        public int getCount() {
            return departures.size();
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public long getItemId(final int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public RemoteViews getViewAt(final int viewPosition) {
            final Departure departure = departures.get(viewPosition);

            final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.station_widget_entry);

            if (canShowJourneys) {
                views.setOnClickFillInIntent(R.id.station_widget_entry, new Intent()
                        .putExtra(StationDetailsActivity.INTENT_EXTRA_JOURNEYREF, Objects.serializeToString(departure.journeyRef)));
            }

            // line
            final Line line = departure.line;
            views.setTextViewText(R.id.station_widget_entry_line, line.label);
            final Style style = line.style;
            if (style != null) {
                views.setTextColor(R.id.station_widget_entry_line, style.foregroundColor);
                views.setInt(R.id.station_widget_entry_line, "setBackgroundColor", style.backgroundColor);
            }

            // destination
            final de.schildbach.pte.dto.Location destination = departure.destination;
            if (destination != null) {
                views.setTextViewText(R.id.station_widget_entry_destination,
                        Constants.DESTINATION_ARROW_PREFIX + destination.uniqueShortName());
            } else {
                views.setTextViewText(R.id.station_widget_entry_destination, "");
            }

            // message
            views.setViewVisibility(R.id.station_widget_entry_msg,
                    departure.message != null ? View.VISIBLE : View.GONE);

            // position
            final Position position = departure.getPosition();
            if (position != null) {
                views.setViewVisibility(R.id.station_widget_entry_position, View.VISIBLE);
                views.setTextViewText(R.id.station_widget_entry_position, position.toString());
                views.setInt(R.id.station_widget_entry_position, "setBackgroundResource",
                        position.equals(departure.plannedPosition)
                                ? R.color.bg_position_darkdefault
                                : R.color.bg_position_changed);
            } else {
                views.setViewVisibility(R.id.station_widget_entry_position, View.GONE);
            }

            // delay
            final PTDate predictedTime = departure.predictedTime;
            final PTDate plannedTime = departure.plannedTime;
            final boolean isPredicted = predictedTime != null;
            final long delay = predictedTime != null && plannedTime != null
                    ? predictedTime.getTime() - plannedTime.getTime() : 0;
            final long delayMins = delay / DateUtils.MINUTE_IN_MILLIS;
            views.setViewVisibility(R.id.station_widget_entry_delay, delayMins != 0 ? View.VISIBLE : View.GONE);
            final SpannableString delayStr = new SpannableString(String.format(Locale.US, "(%+d)", delayMins));
            delayStr.setSpan(new StyleSpan(isPredicted ? Typeface.BOLD_ITALIC : Typeface.BOLD), 0, delayStr.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            views.setTextViewText(R.id.station_widget_entry_delay, delayStr);

            // time
            long time;
            if (predictedTime != null)
                time = predictedTime.getTime();
            else if (plannedTime != null)
                time = plannedTime.getTime();
            else
                throw new IllegalStateException();
            final SpannableString timeStr = new SpannableString(timeFormat.format(time));
            if (isPredicted)
                timeStr.setSpan(new StyleSpan(Typeface.ITALIC), 0, timeStr.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            views.setTextViewText(R.id.station_widget_entry_time, timeStr);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return new RemoteViews(context.getPackageName(), R.layout.station_widget_entry_loading);
        }
    }
}
