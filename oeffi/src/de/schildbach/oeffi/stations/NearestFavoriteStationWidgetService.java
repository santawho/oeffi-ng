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

import android.Manifest;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.RemoteViews;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.SettableFuture;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;
import de.schildbach.pte.exception.BlockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;

import java.io.Serializable;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NearestFavoriteStationWidgetService extends JobService {
    private AppWidgetManager appWidgetManager;
    private LocationManager locationManager;
    private ContentResolver contentResolver;
    private Executor executor = Executors.newFixedThreadPool(2);
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private static final int JOB_ID_PERIODIC = 0;
    private static final int JOB_ID_IMMEDIATE = 1;

    private static final Logger log = LoggerFactory.getLogger(NearestFavoriteStationWidgetService.class);

    public static void schedulePeriodic(final Context context) {
        final JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        final ComponentName providerName = new ComponentName(context, NearestFavoriteStationWidgetProvider.class);
        final boolean haveWidgets = AppWidgetManager.getInstance(context).getAppWidgetIds(providerName).length > 0;
        if (haveWidgets) {
            final JobInfo.Builder jobInfo = new JobInfo.Builder(JOB_ID_PERIODIC, new ComponentName(context,
                    NearestFavoriteStationWidgetService.class));
            jobInfo.setPeriodic(DateUtils.MINUTE_IN_MILLIS * 15);
            jobInfo.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            final JobInfo job = jobInfo.build();
            jobScheduler.schedule(job);
            log.info("Scheduled periodic job: {}", job);
        } else {
            jobScheduler.cancelAll();
        }
    }

    public static void scheduleImmediate(final Context context) {
        final JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        final ComponentName providerName = new ComponentName(context, NearestFavoriteStationWidgetProvider.class);
        final boolean haveWidgets = AppWidgetManager.getInstance(context).getAppWidgetIds(providerName).length > 0;
        if (haveWidgets) {
            final JobInfo.Builder jobInfo = new JobInfo.Builder(JOB_ID_IMMEDIATE, new ComponentName(context,
                    NearestFavoriteStationWidgetService.class));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                jobInfo.setExpedited(true);
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                jobInfo.setImportantWhileForeground(true);
            jobInfo.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            final JobInfo job = jobInfo.build();
            jobScheduler.schedule(job);
            log.info("Scheduled immediate job: {}", job);
        } else {
            jobScheduler.cancelAll();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        appWidgetManager = AppWidgetManager.getInstance(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        contentResolver = getContentResolver();
        backgroundThread = new HandlerThread("widgetServiceThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public void onDestroy() {
        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    private RemoteViews views;

    @Override
    public boolean onStartJob(final JobParameters params) {
        log.info("Job started: {}", params);
        executor.execute(() -> {
            runJob();
            jobFinished(params, false);
            log.info("Job finished: {}", params);
        });
        return true;
    }

    @Override
    public boolean onStopJob(final JobParameters params) {
        log.info("Job stopped: {}", params);
        return false;
    }

    @WorkerThread
    private void runJob() {
        final ComponentName providerName = new ComponentName(this, NearestFavoriteStationWidgetProvider.class);
        final int[] appWidgetIds = appWidgetManager.getAppWidgetIds(providerName);
        if (appWidgetIds.length == 0)
            return;

        views = new RemoteViews(getPackageName(), R.layout.station_widget_content);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            final PendingIntent intent = PendingIntent.getActivity(this, 0, new Intent(this,
                    NearestFavoriteStationsWidgetPermissionActivity.class), PendingIntent.FLAG_IMMUTABLE);
            widgetsMessage(appWidgetIds, getString(R.string.nearest_favorite_station_widget_no_location_permission), intent);
            log.info("No location permission");
            return;
        }

        log.info("Available location providers: {}", locationManager.getAllProviders());
        final String provider;
        final LocationProvider fused = locationManager.getProvider("fused");
        if (fused != null) {
            // prefer fused provider from Google Play Services
            provider = fused.getName();
        } else {
            // otherwise, we want to use as little power as possible
            final Criteria criteria = new Criteria();
            criteria.setPowerRequirement(Criteria.POWER_LOW);
            provider = locationManager.getBestProvider(criteria, true);
            if (provider == null || LocationManager.PASSIVE_PROVIDER.equals(provider)) {
                widgetsMessage(appWidgetIds, getString(R.string.acquire_location_no_provider), null);
                log.info("No location provider found");
                return;
            }
        }

        widgetsHeader(appWidgetIds, getString(R.string.acquire_location_start, provider));
        log.info("Acquiring {} location", provider);

        final SettableFuture<Location> future = SettableFuture.create();
        locationManager.requestSingleUpdate(provider, new LocationListener() {
            public void onLocationChanged(final Location location) {
                future.set(location);
            }

            public void onProviderEnabled(final String provider) {
            }

            public void onProviderDisabled(final String provider) {
            }

            public void onStatusChanged(final String provider, final int status, final Bundle extras) {
            }
        }, backgroundHandler.getLooper());

        try {
            final Location here = future.get(Constants.LOCATION_BACKGROUND_UPDATE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            log.info("Widgets: {}, location: {}", Arrays.toString(appWidgetIds), here);
            handleLocation(appWidgetIds, here);
        } catch (final TimeoutException x) {
            log.info("Widgets: {}, location timed out after {} ms", Arrays.toString(appWidgetIds),
                    Constants.LOCATION_BACKGROUND_UPDATE_TIMEOUT_MS);
            widgetsHeader(appWidgetIds, getString(R.string.acquire_location_timeout));
        } catch (final InterruptedException | ExecutionException x) {
            throw new RuntimeException(x);
        }
    }

    private void widgetsMessage(final int[] appWidgetIds, final String message, final PendingIntent intent) {
        setMessage(message);
        views.setTextViewText(R.id.station_widget_distance, null);
        views.setTextViewText(R.id.station_widget_lastupdated, null);
        for (final int appWidgetId : appWidgetIds) {
            views.setTextViewText(R.id.station_widget_header,
                    getString(R.string.nearest_favorite_station_widget_label));
            views.setOnClickPendingIntent(R.id.station_widget_content,
                    intent != null ? intent : clickIntent(appWidgetId));
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private void widgetsHeader(final int[] appWidgetIds, final String message) {
        for (final int appWidgetId : appWidgetIds) {
            setHeader(appWidgetId, message);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private void handleLocation(final int[] appWidgetIds, final Location here) {
        // determine nearest station
        final List<Favorite> favorites = new ArrayList<>();

        final Cursor favCursor = contentResolver.query(FavoriteStationsProvider.CONTENT_URI(), null,
                FavoriteStationsProvider.KEY_TYPE + "=?",
                new String[] { String.valueOf(FavoriteStationsProvider.TYPE_FAVORITE) }, null);

        if (favCursor != null) {
            final int networkCol = favCursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_NETWORK);
            final int stationIdCol = favCursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_ID);
            final int stationTypeCol = favCursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_TYPE);
            final int stationPlaceCol = favCursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_PLACE);
            final int stationNameCol = favCursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_NAME);
            final int stationLatCol = favCursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_LAT);
            final int stationLonCol = favCursor.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_STATION_LON);

            while (favCursor.moveToNext()) {
                final String network = favCursor.getString(networkCol);
                final String stationId = favCursor.getString(stationIdCol);
                String stationType = favCursor.getString(stationTypeCol);
                String stationPlace = favCursor.getString(stationPlaceCol);
                String stationName = favCursor.getString(stationNameCol);
                Point stationPoint = Point.from1E6(favCursor.getInt(stationLatCol), favCursor.getInt(stationLonCol));

                try {
                    final NetworkId networkId = NetworkId.valueOf(network);
                    NetworkProviderFactory.provider(networkId); // check if existent

                    if (stationPoint.getLatAsDouble() > 0 || stationPoint.getLonAsDouble() > 0) {
                        final float[] distanceBetweenResults = new float[1];
                        android.location.Location.distanceBetween(here.getLatitude(), here.getLongitude(),
                                stationPoint.getLatAsDouble(), stationPoint.getLonAsDouble(), distanceBetweenResults);
                        final float distance = distanceBetweenResults[0];
                        final Favorite favorite = new Favorite(networkId, stationId, LocationType.valueOf(stationType), stationPlace, stationName,
                                distance);
                        favorites.add(favorite);
                    }
                } catch (final IllegalArgumentException x) {
                    log.info("Unknown network {}, favorite {}", network, stationId);
                }
            }

            favCursor.close();

            Collections.sort(favorites);
            Arrays.sort(appWidgetIds);
            log.info("Distributing {} station favorites to {} app widgets", favorites.size(), appWidgetIds.length);

            final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(this);

            final int numFavorites = favorites.size();
            for (int i = 0; i < appWidgetIds.length; i++) {
                final int appWidgetId = appWidgetIds[appWidgetIds.length - i - 1];

                // reset
                views.setViewVisibility(R.id.station_widget_departures, View.GONE);
                views.setViewVisibility(R.id.station_widget_message, View.GONE);

                if (numFavorites > 0) {
                    final Favorite favorite = favorites.get(i % numFavorites);
                    log.debug("Favorite: {}", favorite);

                    views.setTextViewText(R.id.station_widget_distance, Formats.formatDistance(favorite.distance));
                    views.setViewVisibility(R.id.station_widget_distance, View.VISIBLE);

                    setHeader(appWidgetId, getString(R.string.nearest_favorite_station_widget_loading));
                    appWidgetManager.updateAppWidget(appWidgetId, views);

                    final NetworkProvider networkProvider = NetworkProviderFactory.provider(favorite.networkId);
                    final String stationId = favorite.id;

                    try {
                        final QueryDeparturesResult result = networkProvider.queryDepartures(stationId, new Date(), 100,
                                false);
                        setResult(appWidgetId, result, favorite, timeFormat);
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                    } catch (final ConnectException x) {
                        setHeader(appWidgetId, favorite.name);
                        setMessage(getString(R.string.nearest_favorite_station_widget_error_connect));
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                        log.info("Could not query departures for station " + stationId, x);
                    } catch (final BlockedException x) {
                        setHeader(appWidgetId, favorite.name);
                        setMessage(
                                getString(R.string.nearest_favorite_station_widget_error_blocked, x.getUrl().host()));
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                        log.info("Could not query departures for station " + stationId, x);
                    } catch (final SSLException x) {
                        setHeader(appWidgetId, favorite.name);
                        setMessage(getString(R.string.nearest_favorite_station_widget_error_ssl,
                                Throwables.getRootCause(x).getClass().getSimpleName()));
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                        log.info("Could not query departures for station " + stationId, x);
                    } catch (final Exception x) {
                        setHeader(appWidgetId, favorite.name);
                        setMessage(getString(R.string.nearest_favorite_station_widget_error_exception,
                                Throwables.getRootCause(x).toString()));
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                        log.info("Could not query departures for station " + stationId, x);
                    }
                } else {
                    setMessage(getString(R.string.nearest_favorite_station_widget_no_favorites));
                    views.setTextViewText(R.id.station_widget_header, null);
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }
            }
        }
    }

    private void setResult(final int appWidgetId, final QueryDeparturesResult result, final Favorite favorite,
            final java.text.DateFormat timeFormat) {
        views.setTextViewText(R.id.station_widget_lastupdated,
                getString(R.string.nearest_favorite_station_widget_lastupdated, timeFormat.format(new Date())));

        views.setTextViewText(R.id.station_widget_header, favorite.name);

        if (result.status == QueryDeparturesResult.Status.OK) {
            setMessage(getString(R.string.nearest_favorite_station_widget_no_departures));
            final StationDepartures stationDepartures = result.findStationDepartures(favorite.id);
            if (stationDepartures != null) {
                if (stationDepartures.location.name != null)
                    views.setTextViewText(R.id.station_widget_header, stationDepartures.location.name);

                final List<Departure> departures = stationDepartures.departures;

                if (!departures.isEmpty())
                    setDeparturesList(departures, appWidgetId);
                log.info("Got {} departures for favorite {}", departures.size(), favorite.id);
            } else {
                log.info("Got no station departures for favorite {}", favorite.id);
            }
        } else {
            log.info("Got {} for favorite {}", result.toShortString(), favorite.id);
            setMessage(getString(QueryDeparturesRunnable.statusMsgResId(result.status)));
        }
    }

    private void setDeparturesList(final List<Departure> departures, final int appWidgetId) {
        views.setViewVisibility(R.id.station_widget_message, View.GONE);
        views.setViewVisibility(R.id.station_widget_departures, View.VISIBLE);

        final Intent intent = new Intent(this, NearestFavoriteStationWidgetListService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra(NearestFavoriteStationWidgetListService.INTENT_EXTRA_DEPARTURES,
                Objects.serialize((Serializable) departures));
        intent.putExtra(NearestFavoriteStationWidgetListService.INTENT_EXTRA_DEPARTURES + ".hash",
                departures.hashCode());
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.station_widget_departures, intent);

        final PendingIntent clickIntent = clickIntent(appWidgetId);
        views.setOnClickPendingIntent(R.id.station_widget_content, clickIntent);
        views.setPendingIntentTemplate(R.id.station_widget_departures, clickIntent);
    }

    private void setHeader(final int appWidgetId, final String message) {
        views.setTextViewText(R.id.station_widget_header, message);
        views.setViewVisibility(R.id.station_widget_message, View.GONE);
        views.setOnClickPendingIntent(R.id.station_widget_content, clickIntent(appWidgetId));
    }

    private void setMessage(final String status) {
        views.setViewVisibility(R.id.station_widget_departures, View.GONE);
        views.setViewVisibility(R.id.station_widget_message, View.VISIBLE);
        views.setTextViewText(R.id.station_widget_message, status);
    }

    private PendingIntent clickIntent(final int appWidgetId) {
        final Intent intent = new Intent(this, NearestFavoriteStationWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] { appWidgetId });
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static class Favorite implements Comparable<Favorite> {
        public final NetworkId networkId;
        public final String id;
        public final LocationType type;
        public final String place;
        public final String name;
        public final float distance;

        public Favorite(
                final NetworkId networkId, final String id, final LocationType type,
                final String place, final String name,
                final float distance) {
            this.networkId = networkId;
            this.id = id;
            this.type = type;
            this.place = place;
            this.name = name;
            this.distance = distance;
        }

        public int compareTo(final Favorite other) {
            return Float.compare(this.distance, other.distance);
        }

        @Override
        public String toString() {
            return "Favorite[" + networkId + "," + id + ",'" + place + "','" + name + "'," + distance + "m]";
        }
    }
}
