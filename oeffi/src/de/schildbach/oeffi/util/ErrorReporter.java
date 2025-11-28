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

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import androidx.core.app.ActivityManagerCompat;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.URLs;
import de.schildbach.pte.NetworkId;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ErrorReporter implements Thread.UncaughtExceptionHandler {
    private static final String STACKTRACE_FILENAME = ".stacktrace";

    private Thread.UncaughtExceptionHandler previousHandler;
    private File stackTraceFile;
    private final StringBuilder report = new StringBuilder();
    private File filesDir, cacheDir;
    private NetworkId networkId;
    private boolean alreadyChecked;

    private static final Logger log = LoggerFactory.getLogger(ErrorReporter.class);

    private static ErrorReporter instance;

    public static ErrorReporter getInstance() {
        if (instance == null)
            instance = new ErrorReporter();
        return instance;
    }

    public void setNetworkId(final NetworkId networkId) {
        this.networkId = networkId;
    }

    public void init(final Context context) {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);

        filesDir = context.getFilesDir();
        cacheDir = context.getCacheDir();

        stackTraceFile = new File(cacheDir, STACKTRACE_FILENAME);

        report.append("=== collected at launch time ===\n\n");
        report.append("Network: " + (networkId != null ? networkId.name() : "-") + "\n\n");
        appendReport(report, context);
    }

    private static void appendReport(final StringBuilder report, final Context context) {
        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);

            report.append("Date: " + new Date() + "\n");
            report.append("Version: " + pi.versionName + " (" + pi.versionCode + ")\n");
            report.append("Package: " + pi.packageName + "\n");
            final String installerPackageName = Installer.installerPackageName(context);
            final Installer installer = Installer.from(installerPackageName);
            if (installer != null)
                report.append("Installer: " + installer.displayName + " (" + installerPackageName + ")\n");
            else
                report.append("Installer: unknown\n");
        } catch (final NameNotFoundException x) {
            x.printStackTrace();
        }

        try {
            appendDeviceInfo(report, context);
        } catch (final IOException x) {
            report.append("Exception while adding device info: ").append(x.getMessage()).append('\n');
        }

        report.append("\n\n\n");
    }

    private long getAvailableInternalMemorySize() {
        final File path = Environment.getDataDirectory();
        final StatFs stat = new StatFs(path.getPath());
        final long blockSize = stat.getBlockSize();
        final long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }

    private long getTotalInternalMemorySize() {
        final File path = Environment.getDataDirectory();
        final StatFs stat = new StatFs(path.getPath());
        final long blockSize = stat.getBlockSize();
        final long totalBlocks = stat.getBlockCount();
        return totalBlocks * blockSize;
    }

    public void uncaughtException(final Thread t, final Throwable exception) {
        log.warn("crashing because of uncaught exception", exception);
        try {
            report.append("=== collected at exception time ===\n\n");

            report.append("Network: " + (networkId != null ? networkId.name() : "-") + "\n\n");
            report.append("Total Internal memory: " + getTotalInternalMemorySize() + "\n");
            report.append("Available Internal memory: " + getAvailableInternalMemorySize() + "\n");
            report.append("\n");

            final Writer result = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(result);
            exception.printStackTrace(printWriter);
            final String stacktrace = result.toString();
            report.append(stacktrace + "\n");
            printWriter.close();

            // append contents of directories
            report.append("\nContents of FilesDir " + filesDir + ":\n");
            appendReport(report, filesDir, 0);
            report.append("\nContents of CacheDir " + cacheDir + ":\n");
            appendReport(report, cacheDir, 0);

            saveAsFile(report.toString());
        } catch (final Exception x) {
            x.printStackTrace();
        }

        previousHandler.uncaughtException(t, exception);
    }

    public static void appendDeviceInfo(final Appendable report, final Context context) throws IOException {
        final Resources res = context.getResources();
        final Configuration config = res.getConfiguration();
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        report.append("Manufacturer: " + Build.MANUFACTURER + "\n");
        report.append("Phone Model: " + Build.MODEL + "\n");
        report.append("Android Version: " + Build.VERSION.RELEASE + "\n");
        report.append("Android security patch level: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
        report.append("ABIs: ").append(Joiner.on(", ").skipNulls().join(Strings.emptyToNull(Build.CPU_ABI),
                Strings.emptyToNull(Build.CPU_ABI2))).append("\n");
        report.append("Board: " + Build.BOARD + "\n");
        report.append("Brand: " + Build.BRAND + "\n");
        report.append("Device: " + Build.DEVICE + "\n");
        report.append("Model: " + Build.MODEL + "\n");
        report.append("Product: " + Build.PRODUCT + "\n");
        report.append("Configuration: " + config + "\n");
        report.append("Screen Layout:" //
                + " size " + (config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) //
                + " long " + (config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_LONG_MASK) //
                + " layoutdir " + (config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_LAYOUTDIR_MASK) //
                + " round " + (config.screenLayout & android.content.res.Configuration.SCREENLAYOUT_ROUND_MASK) + "\n");
        report.append("Display Metrics: " + res.getDisplayMetrics() + "\n");
        report.append("Memory Class: " + activityManager.getMemoryClass() + "/" + activityManager.getLargeMemoryClass()
                + (ActivityManagerCompat.isLowRamDevice(activityManager) ? " (low RAM device)" : "") + "\n");
        report.append("Runtime: ").append(System.getProperty("java.vm.name")).append(" ")
                .append(System.getProperty("java.vm.version")).append("\n");
    }

    private static void sendErrorMail(final Application application, final String errorContent) {
        final Matcher m = Pattern.compile("Version: (.+?) ").matcher(errorContent);
        final String versionName = m.find() ? m.group(1) : "";
        final String subject = buildSubject(application, R.string.error_reporter_crash_mail_subject, versionName);
        send(application, subject, errorContent);
    }

    public static void sendBugMail(final Application application, final PackageInfo packageInfo) {
        final StringBuilder report = new StringBuilder(application.getString(R.string.error_reporter_questions));
        report.append("\n=== collected at reporting time ===\n\n");
        appendReport(report, application);

        // append contents of directories
        report.append("\nContents of FilesDir " + application.getFilesDir() + ":\n");
        appendReport(report, application.getFilesDir(), 0);

        report.append("\n\n");
        report.append(application.getString(R.string.error_reporter_footer));

        final String subject = buildSubject(application, R.string.error_reporter_bug_mail_subject, packageInfo.versionName);
        send(application, subject, report);
    }

    private static String buildSubject(final Context context, final int prefixResId, final String versionName) {
        final StringBuilder subject = new StringBuilder(context.getString(prefixResId));
        subject.append(": ");
        subject.append(versionName);
        final String installer = Installer.installerPackageName(context);
        if (installer != null)
            subject.append(", installer ").append(installer);
        subject.append(", android ").append(Build.VERSION.RELEASE);
        subject.append(", ").append(Build.MANUFACTURER);
        if (!Build.BRAND.equalsIgnoreCase(Build.MANUFACTURER))
            subject.append(' ').append(Build.BRAND);
        subject.append(' ').append(Build.MODEL);
        return subject.toString();
    }

    private static void send(final Application application, final String subject, final CharSequence report) {
        final File logDir = new File(application.getFilesDir(), "log");
        final ArrayList<Uri> attachments = new ArrayList<>();
//        attachments.add(null); // place holder for the body in EXTRA_TEXT array

        if (logDir.exists()) {
            final File[] files = logDir.listFiles();
            if (files != null) {
                for (final File logFile : files) {
                    if (logFile.isFile() && logFile.length() > 0)
                        attachments.add(application.getSharedFileContentUri(logFile));
                }
            }
        }

        final Intent intent;
        final int attachmentsSize = attachments.size();
        if (attachmentsSize <= 1) {
            // no attachment
            intent = new Intent(Intent.ACTION_SEND);
            intent.setType("message/rfc822");
            intent.putExtra(Intent.EXTRA_TEXT, report.toString());
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("text/plain");
//            final ArrayList<CharSequence> texts = new ArrayList<>(attachmentsSize);
//            texts.add(report);
//            for (int i=1; i<attachmentsSize; ++i) texts.add(null);
//            intent.putCharSequenceArrayListExtra(Intent.EXTRA_TEXT, texts);
            intent.putExtra(Intent.EXTRA_TEXT, report.toString());
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachments);
        }

        final String mailAddress = application.getString(R.string.error_reporter_mail_address);
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {mailAddress});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        application.startActivity(Intent
                .createChooser(intent, application.getString(R.string.error_reporter_mail_intent_chooser_title))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void saveAsFile(final String errorContent) {
        try {
            final FileOutputStream trace = new FileOutputStream(stackTraceFile);
            trace.write(errorContent.getBytes());
            trace.close();
        } catch (final IOException x) {
            // swallow
        }
    }

    private void sendError(final Application application) {
        try {
            final StringBuilder errorText = new StringBuilder();

            final BufferedReader input = new BufferedReader(new FileReader(stackTraceFile));
            String line;
            while ((line = input.readLine()) != null)
                errorText.append(line + "\n");
            input.close();

            errorText.append("\n\n");
            errorText.append(application.getString(R.string.error_reporter_footer));

            sendErrorMail(application, errorText.toString());
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    private static void appendReport(final StringBuilder report, final File file, final int indent) {
        for (int i = 0; i < indent; i++)
            report.append("  - ");

        final Formatter formatter = new Formatter(report);
        formatter.format(Locale.US, "%tF %tT  %s  [%d]\n", file.lastModified(), file.lastModified(), file.getName(),
                file.length());
        formatter.close();

        if (file.isDirectory())
            for (final File f : file.listFiles())
                appendReport(report, f, indent + 1);
    }

    private final static Pattern PATTERN_VERSION = Pattern.compile("<dt id=\"(\\d+)\">([^<]*)</dt>");

    public void check(final Activity activity, final int applicationVersionCode, final OkHttpClient okHttpClient) {
        if (alreadyChecked)
            return;
        if (Debug.isDebuggerConnected())
            return;
        if (!stackTraceFile.exists())
            return;

        alreadyChecked = true;

        String changeLogUrl = activity.getString(R.string.about_changelog_url);
        if (!changeLogUrl.isEmpty()) {
            dialog(activity, null);
            return;
        }

        final HttpUrl.Builder url = HttpUrl.parse(activity.getString(R.string.about_changelog_summary)).newBuilder();
        url.addQueryParameter("version", Integer.toString(applicationVersionCode));
        url.addQueryParameter("sdk", Integer.toString(Build.VERSION.SDK_INT));
        url.addQueryParameter("check", null);
        final Request.Builder request = new Request.Builder().url(url.build());
        final Call call = okHttpClient.newCall(request.build());
        final Handler callbackHandler = new Handler(Looper.myLooper());
        call.enqueue(new Callback() {
            public void onResponse(final Call call, final Response r) throws IOException {
                try (final Response response = r) {
                    final CharSequence page = response.body().string();
                    final Matcher m = PATTERN_VERSION.matcher(page);
                    if (m.find()) {
                        final int versionCode = Integer.parseInt(m.group(1));
                        final String versionName = m.group(2);
                        log.info("According to {}, the current version is {} ({})", url, versionName, versionCode);
                        if (versionCode > applicationVersionCode)
                            callback(versionName);
                        else
                            callback(null);
                    }
                }
            }

            public void onFailure(final Call call, final IOException x) {
                callback(null);
            }

            private void callback(final String newVersion) {
                callbackHandler.post(() -> dialog(activity, newVersion));
            }
        });
    }

    private void dialog(final Activity activity, final String newVersion) {
        final DialogBuilder builder = DialogBuilder.warn(activity, R.string.alert_crash_report_title);
        builder.setMessage(newVersion != null ? activity.getString(R.string.alert_crash_report_new_version, newVersion)
                : activity.getString(R.string.alert_crash_report_message));
        builder.setNegativeButton(R.string.alert_crash_report_negative, (dialog, which) -> stackTraceFile.delete());
        if (newVersion != null) {
            final Installer installer = Installer.from(activity);
            if (installer != null) {
                builder.setNeutralButton(installer.displayName, (dialog, which) -> {
                    stackTraceFile.delete();
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, installer.appMarketUriFor(activity)));
                });
            }
            builder.setPositiveButton(R.string.alert_crash_report_download, (dialog, which) -> {
                stackTraceFile.delete();
                activity.startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(URLs.getOeffiBaseUrl() + "download.html")));
            });
        } else {
            builder.setPositiveButton(R.string.alert_crash_report_positive, (dialog, which) -> {
                sendError(Application.getInstance());
                stackTraceFile.delete();
            });
        }
        builder.setOnCancelListener(dialog -> stackTraceFile.delete());

        try {
            builder.show();
        } catch (final Exception x) {
            log.warn("Problem showing crash report dialog", x);
        }
    }
}
