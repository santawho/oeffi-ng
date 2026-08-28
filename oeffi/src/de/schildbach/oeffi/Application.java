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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.directions.driverops.OperationNotification;
import de.schildbach.oeffi.directions.driverops.OperationsActivity;
import de.schildbach.oeffi.directions.QueryHistoryProvider;
import de.schildbach.oeffi.directions.navigation.NavigationNotification;
import de.schildbach.oeffi.directions.navigation.NotificationSoundManager;
import de.schildbach.oeffi.mapview.OeffiMapView;
import de.schildbach.oeffi.plans.PlansPickerActivity;
import de.schildbach.oeffi.stations.FavoriteStationsActivity;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.stations.StationsActivity;
import de.schildbach.oeffi.util.AppInstaller;
import de.schildbach.oeffi.util.ErrorReporter;
import de.schildbach.oeffi.util.ResourcesInterceptor;
import de.schildbach.oeffi.util.SettingsUtil;
import de.schildbach.oeffi.util.SpeechInput;
import de.schildbach.oeffi.util.TimeZoneSelector;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.provider.ApiProvider;
import de.schildbach.pte.provider.NetworkProvider;
import de.schildbach.pte.util.HttpClient;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class Application extends android.app.Application {
    public static final Logger log = LoggerFactory.getLogger(Application.class);

    private String APP_USER_AGENT;
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:151.0) Gecko/20100101 Firefox/151.0";

    private static Application instance;

    public static Application getInstance() {
        return instance;
    }

    public static String getApplicationId() {
        return instance.getPackageName();
    }

    private String commonPackageName;
    private PackageInfo packageInfo;
    private OkHttpClient okHttpClient;
    private File logFile;
    private SpeechInput speechInput;
    private SharedPreferences prefs;
    private String appName;
    private TimeZoneSelector systemTimeZoneSelector;

    public Application() {
        instance = this;
    }

    private static final String CUSTOMIZATION_PREFS_KEY = Application.class.getName() + ".CustomizationConfiguration";

    public void setCustomizationConfiguration(final Properties properties) throws IOException {
        if (properties == null) {
            prefs.edit().remove(CUSTOMIZATION_PREFS_KEY).commit();
        } else {
            final StringWriter sw = new StringWriter();
            properties.store(sw, "");
            sw.close();
            final String prefValue = sw.toString();
            prefs.edit().putString(CUSTOMIZATION_PREFS_KEY, prefValue).commit();
        }
    }

    public boolean isCustomizationConfigurationSet() {
        return prefs.contains(CUSTOMIZATION_PREFS_KEY);
    }

    private Context resourcesInterceptorContext;

    @Override
    public Resources getResources() {
        if (resourcesInterceptorContext == this)
            return super.getResources();
        if (resourcesInterceptorContext == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(this);
            final String prefValue = prefs.getString(CUSTOMIZATION_PREFS_KEY, null);
            Properties properties = null;
            if (prefValue != null) {
                try {
                    properties = new Properties();
                    properties.load(new StringReader(prefValue));
                } catch (final IOException ioe) {
                    log.error("cannot load properties: {}", ioe.getMessage());
                    properties = null;
                }
            }
            resourcesInterceptorContext = ResourcesInterceptor.getApplicationContext(
                    this, super.getResources(), properties);
        }
        return resourcesInterceptorContext.getResources();
    }

    public File getLogFile() {
        return logFile;
    }

    @Override
    public File getCacheDir() {
        return super.getCacheDir();
    }

    @Override
    public File getDataDir() {
        return super.getDataDir();
    }

    public String getUserAgent(final ApiProvider.UserAgentType userAgentType) {
        switch (userAgentType) {
            case NONE:
            case PROVIDER_SPECIFIC:
                return null;
            case APP:
                return getAppUserAgent();
            case ANY:
            case BROWSER:
            default:
                return BROWSER_USER_AGENT;
        }
    }

    public String getAppUserAgent() {
        if (APP_USER_AGENT == null) {
            // see https://scientiamobile.com/how-to-correctly-form-user-agents-for-mobile-apps/
            APP_USER_AGENT = getApplicationId() + "/" + packageInfo.versionName
                    + " " + System.getProperty("http.agent");
        }
        return APP_USER_AGENT;
    }

    public SpeechInput getSpeechInput() {
        return speechInput;
    }

    public SharedPreferences getSharedPreferences() {
        return prefs;
    }

    public void postTerminate(@Nullable final Activity activity) {
        final Intent activityIntent;
        if (activity != null) {
            activityIntent = activity.getIntent();
            activity.finish();
        } else {
            activityIntent = null;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (activityIntent != null) {
                try {
                    startActivity(activityIntent);
                } catch (final Exception e) {
                    // ignore
                }
            }
            System.exit(0);
        }, 500);
    }

    public String getApplicationLanguage() {
        // return Locale.getDefault().getLanguage();
        return getString(R.string.locale);
    }

    public String getTranslatedString(final int resId) {
        final String mappedString = getString(resId);
        final String translatedString = getTranslatedString(mappedString, getApplicationLanguage());
        if (translatedString != null)
            return translatedString;
        return getTranslatedString(mappedString, "en");
    }

    public static String getTranslatedString(final String mappedString, final String language) {
        String defaultValue = null;
        final String[] languageValues = mappedString.split("~~");
        for (final String languageValue : languageValues) {
            final String[] languageAndValue = languageValue.split("~");
            final String lang;
            final String value;
            if (languageAndValue.length < 2) {
                lang = null;
                value = languageAndValue[0];
            } else {
                lang = languageAndValue[0];
                value = languageAndValue[1];
            }
            if (language.equals(lang))
                return value;
            if (defaultValue == null)
                defaultValue = value;
        }
        return defaultValue;
    }

    public File getShareDir() throws IOException {
        final File shareDir = new File(getFilesDir(), "share");

        if (!shareDir.exists())
            Files.createDirectory(shareDir.toPath());

        return shareDir;
    }

    public Uri getSharedFileContentUri(final File file) {
        return FileProvider.getUriForFile(this, getPackageName(), file);
    }

    public boolean isDeveloperElementsEnabled() {
        return prefs.getBoolean(Constants.PREFS_KEY_USER_INTERFACE_DEVELOPER_OPTIONS_SHOW_EXTRA_INFOS_ENABLED, false);
    }

    public TimeZoneSelector getSystemTimeZoneSelector() {
        return systemTimeZoneSelector;
    }

    public TimeZoneSelector getPreferredNetworkTimeZoneSelector(final NetworkId network) {
        return new TimeZoneSelector(this, prefs.getString(Constants.PREFS_KEY_PREFERRED_TIMEZONE, "location"), network);
    }

    public NetworkId getDefaultNetwork() {
        return NetworkId.DEUTSCHLANDTICKET;
    }

    public String getPrefsKeyNetwork(final boolean forOperations) {
        return forOperations ? Constants.PREFS_KEY_OPERATIONS_NETWORK_PROVIDER : Constants.PREFS_KEY_NETWORK_PROVIDER;
    }

    public NetworkId prefsGetNetworkId(final boolean forOperations) {
        final String prefsKey = getPrefsKeyNetwork(forOperations);
        final String networkName = prefs.getString(prefsKey, null);
        if (networkName != null) {
            try {
                return NetworkId.valueOf(networkName);
            } catch (final IllegalArgumentException x) {
                log.warn("Unknown selected network: {}, falling back to default", networkName);
            }
        }
        final NetworkId defaultNetwork = getDefaultNetwork();
        if (defaultNetwork == null)
            return null;

        prefs.edit().putString(prefsKey, defaultNetwork.name()).apply();
        return defaultNetwork;
    }

    public NetworkProvider.Optimize prefsGetOptimizeTrip() {
        final String optimize = prefs.getString(Constants.PREFS_KEY_OPTIMIZE_TRIP, null);
        if (optimize != null)
            return NetworkProvider.Optimize.valueOf(optimize);
        else
            return null;
    }

    public NetworkProvider.WalkSpeed prefsGetWalkSpeed() {
        return NetworkProvider.WalkSpeed.valueOf(prefs.getString(Constants.PREFS_KEY_WALK_SPEED, NetworkProvider.WalkSpeed.NORMAL.name()));
    }

    public Integer prefsGetMinTransferTime() {
        final int value = Integer.parseInt(prefs.getString(Constants.PREFS_KEY_MIN_TRANSFER_TIME, "-1"));
        return value < 0 ? null : value;
    }

    public NetworkProvider.Accessibility prefsGetAccessibility() {
        return NetworkProvider.Accessibility.valueOf(prefs.getString(Constants.PREFS_KEY_ACCESSIBILITY, NetworkProvider.Accessibility.NEUTRAL.name()));
    }

    public boolean prefsIsBicycleTravel() {
        return prefs.getBoolean(Constants.PREFS_KEY_BICYCLE_TRAVEL, false);
    }

    public int prefsGetMaxWalkDistance() {
        final String s = prefs.getString(Constants.PREFS_KEY_MAX_WALK_DISTANCE, "2000");
        try {
            return Integer.parseInt(s);
        } catch (final NumberFormatException nfe) {
            return 0;
        }
    }

    public void restart() {
        startActivity(new Intent(this, DirectionsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        System.exit(0);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        commonPackageName = getClass().getPackage().getName();

        systemTimeZoneSelector = new TimeZoneSelector(this);
        initLogging();

        new SettingsUtil(this).restoreIfRequested();

        ErrorReporter.getInstance().init(this);

        try {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (final NameNotFoundException x) {
            throw new RuntimeException(x);
        }

        this.appName = getString(R.string.app_name);
        log.info("=== Starting app {} version {} ({})", appName, packageInfo.versionName, packageInfo.versionCode);

        final String dimensionSelector = getString(R.string.dimension_selector);

        NotificationSoundManager.logAvailableTextToSpeechServices();
        SpeechInput.logAvailableSpeechRecognitionServices();

        createShortcuts();

        NavigationNotification.startup(this);
        OperationNotification.startup(this);

        HttpClient.setUserAgentFactory(this::getUserAgent);
        final OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.followRedirects(true);
        builder.followSslRedirects(false);
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

        OeffiMapView.init();

        speechInput = new OeffiSpeechInput(this);

        // 2020-11-22: delete unused downloaded station databases
        final FilenameFilter filter = (dir, name) -> name.endsWith(".db") || name.endsWith(".db.meta");
        for (final File file : getFilesDir().listFiles(filter))
            file.delete();

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

        // 2025-11-18: migrate CMTA to use BART
        final String CMTA = "CMTA";
        migrateSelectedNetwork(CMTA, NetworkId.BART);
        FavoriteStationsProvider.deleteFavoriteStations(this, CMTA);
        QueryHistoryProvider.deleteQueryHistory(this, CMTA);

        // 2025-11-18: migrate RTACHICAGO to use BART
        final String RTACHICAGO = "RTACHICAGO";
        migrateSelectedNetwork(RTACHICAGO, NetworkId.BART);
        FavoriteStationsProvider.deleteFavoriteStations(this, RTACHICAGO);
        QueryHistoryProvider.deleteQueryHistory(this, RTACHICAGO);
    }

    public String getAppName() {
//        return "Öffi";
        return appName;
    }

    private void initLogging() {
        final File logDir = new File(getFilesDir(), "log");
        logFile = new File(logDir, "oeffi.log");
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

    private void migrateSelectedNetwork(final String fromName, final NetworkId to) {
        migrateSelectedNetwork(Constants.PREFS_KEY_NETWORK_PROVIDER, fromName, to);
        migrateSelectedNetwork(Constants.PREFS_KEY_OPERATIONS_NETWORK_PROVIDER, fromName, to);
    }

    private void migrateSelectedNetwork(final String prefsKey, final String fromName, final NetworkId to) {
        if (fromName.equals(prefs.getString(prefsKey, null)))
            prefs.edit().putString(prefsKey, to.name()).commit();
    }

    public PackageInfo packageInfo() {
        return packageInfo;
    }

    public ComponentName getComponentName(final Class<?> componentClass) {
        return new ComponentName(this, componentClass.getName());
    }

    public ComponentName getComponentName(final String componentName) {
        return new ComponentName(this,
                componentName.startsWith(".") ? commonPackageName + componentName : componentName);
    }

    public boolean isComponentEnabled(final Class<?> componentClass, final boolean defaultValue) {
        return isComponentEnabled(getComponentName(componentClass), defaultValue);
    }

    public boolean isComponentEnabled(final String componentName, final boolean defaultValue) {
        return isComponentEnabled(getComponentName(componentName), defaultValue);
    }

    public boolean isComponentEnabled(final ComponentName componentName, final boolean defaultValue) {
        final int setting = getPackageManager().getComponentEnabledSetting(componentName);
        if (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            return defaultValue;
        return setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public void setComponentEnabled(final Class<?> componentClass, final boolean enabled) {
        setComponentEnabled(getComponentName(componentClass), enabled);
    }

    public void setComponentEnabled(final String componentName, final boolean enabled) {
        setComponentEnabled(getComponentName(componentName), enabled);
    }

    public void setComponentEnabled(final ComponentName componentName, final boolean enabled) {
        getPackageManager().setComponentEnabledSetting(componentName,
                enabled
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    public OkHttpClient okHttpClient() {
        return okHttpClient;
    }

    public boolean isDriverMode() {
        return prefs.getBoolean(Constants.PREFS_KEY_EXTRAS_DRIVERMODE_ENABLED, false);
    }

    public String versionName() {
        return packageInfo().versionName;
    }

    public int versionCode() {
        return packageInfo().versionCode;
    }

    public String oeffiOriginalVersionName() {
        return getString(R.string.oeffi_original_version_name);
    }

    public int oeffiOriginalVersionCode() {
        return getResources().getInteger(R.integer.oeffi_original_version_code);
    }

    private void createShortcuts() {
        ShortcutManagerCompat.removeAllDynamicShortcuts(this);
        createLauncherShortcut("idDS",
                DirectionsActivity.class,
                StationsActivity.class,
                R.string.stations_activity_title,
                R.drawable.ic_oeffi_stations_grey600_36dp,
                null);
        createLauncherShortcut("idDF",
                DirectionsActivity.class,
                FavoriteStationsActivity.Main.class,
                R.string.stations_favorite_stations_title,
                R.drawable.ic_oeffi_favorites_grey600_36dp,
                null);
        createLauncherShortcut("idDP",
                DirectionsActivity.class,
                PlansPickerActivity.class,
                R.string.plans_activity_title,
                R.drawable.ic_oeffi_plans_grey600_36dp,
                null);
        if (isDriverMode()) {
            final Bundle extras = new Bundle();
//            extras.putBoolean(Constants.KEY_EXTRAS_DRIVERMODE_ENABLED, true);
            createLauncherShortcut("idDO",
                    DirectionsActivity.class,
                    OperationsActivity.class,
                    R.string.operations_activity_title,
                    R.drawable.ic_oeffi_operations_grey600_36dp,
                    extras);
        }
//        createLauncherShortcut("idSD",
//                StationsActivity.class,
//                DirectionsActivity.class,
//                R.string.directions_activity_title,
//                R.drawable.ic_oeffi_directions_grey600_36dp);
//        createLauncherShortcut("idSP",
//                StationsActivity.class,
//                PlansPickerActivity.class,
//                R.string.plans_activity_title,
//                R.drawable.ic_oeffi_plans_grey600_36dp);
//        createLauncherShortcut("idPD",
//                PlansPickerActivity.class,
//                DirectionsActivity.class,
//                R.string.directions_activity_title,
//                R.drawable.ic_oeffi_directions_grey600_36dp);
//        createLauncherShortcut("idPS",
//                PlansPickerActivity.class,
//                StationsActivity.class,
//                R.string.stations_activity_title,
//                R.drawable.ic_oeffi_stations_grey600_36dp);
    }

    private void createLauncherShortcut(
            final String shortcutId,
            final Class<?> sourceActivityClass,
            final Class<?> targetActivityClass,
            final int titleId,
            final int iconId,
            final Bundle extras) {
        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClass(this, targetActivityClass);
        if (extras != null)
            intent.putExtras(extras);
        ShortcutManagerCompat.pushDynamicShortcut(this, new ShortcutInfoCompat
                .Builder(this, shortcutId)
                .setActivity(new ComponentName(this, sourceActivityClass))
                .setShortLabel(getString(titleId))
                .setIcon(IconCompat.createWithResource(this, iconId))
                .setIntent(intent)
                .build());
    }

    public static boolean isDarkMode() {
        return isDarkMode(instance);
    }

    public static boolean isDarkMode(final Context context) {
        return context.getResources().getBoolean(R.bool.isNightMode);
    }

    @Override
    public Context createConfigurationContext(final Configuration overrideConfiguration) {
        return super.createConfigurationContext(
                updateOverrideConfiguration(this, overrideConfiguration));
    }

    public Configuration updateOverrideConfiguration(
            final Context context,
            final Configuration originalConfiguration) {
        final Configuration configuration = new Configuration(originalConfiguration != null
                ? originalConfiguration
                : context.getResources().getConfiguration());

        final String setting = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("user_interface_darkmode_switch", "system");
        if ("on".equals(setting)) {
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else if ("off".equals(setting)) {
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        }

        return configuration;
    }

    public void shareApp(final Activity contextActivity, final boolean apkFile) {
        if (!AppInstaller.isApkUrlAvailable())
            return;
        final String shareTitle = getShareTitle();
        final String url = apkFile ? AppInstaller.getApkUrl() : AppInstaller.getInstructionsUrl();
        final String shareText = getString(R.string.global_options_share_app_text,
                Application.getInstance().getAppName(), url);
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.putExtra(Intent.EXTRA_SUBJECT, shareTitle);
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        contextActivity.startActivity(Intent.createChooser(intent, shareTitle));
    }

    @NonNull
    public String getShareTitle() {
        return getString(R.string.global_options_share_app_title, Application.getInstance().getAppName());
    }
}
