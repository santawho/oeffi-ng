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

package de.schildbach.oeffi;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.google.common.base.Stopwatch;
import de.schildbach.oeffi.directions.QueryHistoryProvider;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.util.ErrorReporter;
import de.schildbach.pte.NetworkId;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.concurrent.TimeUnit;

public class Application extends android.app.Application {
    private static Application instance;

    public static Application getInstance() {
        return instance;
    }

    public static String getApplicationId() {
        return instance.getPackageName();
    }

    private PackageInfo packageInfo;
    private OkHttpClient okHttpClient;

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public Application() {
        instance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        initLogging();

        ErrorReporter.getInstance().init(this);

        try {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (final NameNotFoundException x) {
            throw new RuntimeException(x);
        }

        log.info("=== Starting app version {} ({})", packageInfo.versionName, packageInfo.versionCode);

        final OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.followRedirects(false);
        builder.followSslRedirects(true);
        builder.connectTimeout(5, TimeUnit.SECONDS);
        builder.writeTimeout(5, TimeUnit.SECONDS);
        builder.readTimeout(15, TimeUnit.SECONDS);
        final HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(final String message) {
                log.debug(message);
            }
        });
        interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        builder.addNetworkInterceptor(interceptor);
        okHttpClient = builder.build();

        initMaps();

        final Stopwatch watch = Stopwatch.createStarted();

        // 2020-11-22: delete unused downloaded station databases
        final FilenameFilter filter = (dir, name) -> name.endsWith(".db") || name.endsWith(".db.meta");
        for (final File file : getFilesDir().listFiles(filter))
            file.delete();

        // 2023-01-09: migrate VMS to use VVO
        final String VMS = "VMS";
        migrateSelectedNetwork(VMS, NetworkId.VVO);
        FavoriteStationsProvider.migrateFavoriteStations(this, VMS, NetworkId.VVO);
        QueryHistoryProvider.migrateQueryHistory(this, VMS, NetworkId.VVO);

        // 2023-11-05: migrate TFI to use RT
        final String TFI = "TFI";
        migrateSelectedNetwork(TFI, NetworkId.RT);
        FavoriteStationsProvider.deleteFavoriteStations(this, TFI);
        QueryHistoryProvider.deleteQueryHistory(this, TFI);

        // 2023-11-16: migrate AVV to use AVV_AUGSBURG
        final String AVV = "AVV";
        migrateSelectedNetwork(AVV, NetworkId.AVV_AUGSBURG);
        FavoriteStationsProvider.deleteFavoriteStations(this, AVV);
        QueryHistoryProvider.deleteQueryHistory(this, AVV);

        // 2023-12-17: migrate SNCB to use RT
        final String SNCB = "SNCB";
        migrateSelectedNetwork(SNCB, NetworkId.RT);
        FavoriteStationsProvider.deleteFavoriteStations(this, SNCB);
        QueryHistoryProvider.deleteQueryHistory(this, SNCB);

        // 2024-04-27: EFA-ID migration of MVV
        FavoriteStationsProvider.migrateFavoriteStationIds(this, NetworkId.MVV, "0", "10000", 91000000);
        QueryHistoryProvider.migrateQueryHistoryIds(this, NetworkId.MVV, "0", "10000", 91000000);

        // 2024-08-09: migrate Finland to use RT
        final String FINLAND = "FINLAND";
        migrateSelectedNetwork(FINLAND, NetworkId.RT);
        FavoriteStationsProvider.deleteFavoriteStations(this, FINLAND);
        QueryHistoryProvider.deleteQueryHistory(this, FINLAND);

        // 2024-08-30: migrate Czech Republic to use RT
        final String CZECH_REPUBLIC = "CZECH_REPUBLIC";
        migrateSelectedNetwork(CZECH_REPUBLIC, NetworkId.RT);
        FavoriteStationsProvider.deleteFavoriteStations(this, CZECH_REPUBLIC);
        QueryHistoryProvider.deleteQueryHistory(this, CZECH_REPUBLIC);

        // 2024-08-30: migrate Italy to use RT
        final String IT = "IT";
        migrateSelectedNetwork(IT, NetworkId.RT);
        FavoriteStationsProvider.deleteFavoriteStations(this, IT);
        QueryHistoryProvider.deleteQueryHistory(this, IT);

        // 2024-08-30: migrate Paris to use RT
        final String PARIS = "PARIS";
        migrateSelectedNetwork(PARIS, NetworkId.RT);
        FavoriteStationsProvider.deleteFavoriteStations(this, PARIS);
        QueryHistoryProvider.deleteQueryHistory(this, PARIS);

        // 2024-08-30: migrate Spain to use RT
        final String SPAIN = "SPAIN";
        migrateSelectedNetwork(SPAIN, NetworkId.RT);
        FavoriteStationsProvider.deleteFavoriteStations(this, SPAIN);
        QueryHistoryProvider.deleteQueryHistory(this, SPAIN);

        // 2024-08-30: migrate Nicaragua to use RT
        final String NICARAGUA = "NICARAGUA";
        migrateSelectedNetwork(NICARAGUA, NetworkId.RT);
        FavoriteStationsProvider.deleteFavoriteStations(this, NICARAGUA);
        QueryHistoryProvider.deleteQueryHistory(this, NICARAGUA);

        log.info("Migrations took {}", watch);
    }

    private void initLogging() {
        final File logDir = new File(getFilesDir(), "log");
        final File logFile = new File(logDir, "oeffi.log");
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        final PatternLayoutEncoder filePattern = new PatternLayoutEncoder();
        filePattern.setContext(context);
        filePattern.setPattern("%d{HH:mm:ss,UTC} [%thread] %logger{0} - %msg%n");
        filePattern.start();

        final RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setFile(logFile.getAbsolutePath());

        final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(logDir.getAbsolutePath() + "/oeffi.%d{yyyy-MM-dd,UTC}.log.gz");
        rollingPolicy.setMaxHistory(7);
        rollingPolicy.start();

        fileAppender.setEncoder(filePattern);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.start();

        final PatternLayoutEncoder logcatTagPattern = new PatternLayoutEncoder();
        logcatTagPattern.setContext(context);
        logcatTagPattern.setPattern("%logger{0}");
        logcatTagPattern.start();

        final PatternLayoutEncoder logcatPattern = new PatternLayoutEncoder();
        logcatPattern.setContext(context);
        logcatPattern.setPattern("[%thread] %msg%n");
        logcatPattern.start();

        final LogcatAppender logcatAppender = new LogcatAppender();
        logcatAppender.setContext(context);
        logcatAppender.setTagEncoder(logcatTagPattern);
        logcatAppender.setEncoder(logcatPattern);
        logcatAppender.start();

        final ch.qos.logback.classic.Logger log = context.getLogger(Logger.ROOT_LOGGER_NAME);
        log.addAppender(fileAppender);
        log.addAppender(logcatAppender);
        log.setLevel(Level.DEBUG);
    }

    private void initMaps() {
        final IConfigurationProvider config = Configuration.getInstance();
        config.setOsmdroidBasePath(new File(getCacheDir(), "org.osmdroid"));
        config.setUserAgentValue(getPackageName());
    }

    private void migrateSelectedNetwork(final String fromName, final NetworkId to) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (fromName.equals(prefs.getString(Constants.PREFS_KEY_NETWORK_PROVIDER, null)))
            prefs.edit().putString(Constants.PREFS_KEY_NETWORK_PROVIDER, to.name()).commit();
    }

    public PackageInfo packageInfo() {
        return packageInfo;
    }

    public OkHttpClient okHttpClient() {
        return okHttpClient;
    }

    public static final String versionName(final Application application) {
        return application.packageInfo().versionName;
    }

    public static final int versionCode(final Application application) {
        return application.packageInfo().versionCode;
    }
}
