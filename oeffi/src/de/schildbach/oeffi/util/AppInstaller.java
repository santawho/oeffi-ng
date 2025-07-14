package de.schildbach.oeffi.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AppInstaller {
    private static final Logger log = LoggerFactory.getLogger(AppInstaller.class);
    private final Activity context;
    final Consumer<Boolean> doneListener;

    public static boolean isApkUrlAvailable() {
        final String apkUrl = getApkUrl();
        return apkUrl != null && !apkUrl.isEmpty();
    }

    public static String getApkUrl() {
        final String apkUrl = "https://github.com/santawho/oeffi-ng/releases/latest/download/oeffi-ng.apk";
        log.info("apk url = {}", apkUrl);
        return apkUrl;
    }

    public static String getInstallerPackageName() {
        final Application application = Application.getInstance();
        return application.getPackageManager().getInstallerPackageName(application.getPackageName());
        // "com.android.packageinstaller"
        // "com.google.android.packageinstaller"
    }

    public static boolean hasExternalInstaller() {
        final String installerPackageName = getInstallerPackageName();
        log.info("package installer = {}", installerPackageName);
        return !(installerPackageName == null
                || "com.android.packageinstaller".equals(installerPackageName)
                || "com.google.android.packageinstaller".equals(installerPackageName)
        );
    }

    public AppInstaller(final Activity context, final Consumer<Boolean> doneListener) {
        this.context = context;
        this.doneListener = doneListener;
    }

    public void downloadAndInstallApk() {
        if (!isApkUrlAvailable() || hasExternalInstaller()) {
            done(false);
            return;
        }

        try {
            final Application application = Application.getInstance();
            final String apkUrl = getApkUrl();
            log.info("downloading APK file for install from {}", apkUrl);
            final ProgressDialog progressDialog = new ProgressDialog(context);
            final HttpUrl remoteUrl = HttpUrl.parse(apkUrl);
            final File localFile = new File(getShareDir(application), "install.apk");
            if (localFile.exists())
                localFile.delete();
            final Downloader downloader = new Downloader(application.getCacheDir());
            final ListenableFuture<Integer> download = downloader.download(application.okHttpClient(),
                    remoteUrl, localFile);
            Futures.addCallback(download, new FutureCallback<Integer>() {
                public void onSuccess(final @Nullable Integer status) {
                    progressDialog.dismiss();
                    installApk(localFile);
                }

                public void onFailure(final Throwable t) {
                    progressDialog.dismiss();
                    log.error("download {} failed", remoteUrl, t);
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.app_installer_download_failed_title)
                            .setMessage(R.string.app_installer_download_failed_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .setOnDismissListener(dialog -> done(false))
                            .create().show();
                }
            }, new UiThreadExecutor());
            progressDialog.setMessage(application.getString(R.string.app_installer_download_progress_message));
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, application.getString(android.R.string.cancel), (dialog, which) -> {
                download.cancel(true);
                done(false);
            });
            progressDialog.show();
        } catch (Exception e) {
            done(false);
            throw new RuntimeException(e);
        }
    }

    private void done(final boolean success) {
        if (doneListener != null)
            doneListener.accept(success);
    }

    private File getShareDir(final Application application) throws IOException {
        final File shareDir = new File(application.getFilesDir(), "share");

        if (!shareDir.exists())
            Files.createDirectory(shareDir.toPath());

        return shareDir;
    }

    private void installApk(final File apkFile) {
        final Application application = Application.getInstance();
        final String installerPackageName = getInstallerPackageName();
        log.info("installing APK file {} using installer {}", apkFile, installerPackageName);

        final Uri contentUri = FileProvider.getUriForFile(application, application.getPackageName(), apkFile);
        context.startActivity(new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setPackage(installerPackageName)
                .setDataAndType(contentUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        done(true);
    }

    public void checkForUpdate() {
        if (!isApkUrlAvailable())
            return;

        final Application application = Application.getInstance();
        final String manifestUrl = application.getString(R.string.about_update_manifest_url);
        final String modifiedStr = application.getString(R.string.about_update_modified);
        new Thread(() -> {
            try (Response response = new OkHttpClient().newCall(
                    new Request.Builder().url(manifestUrl).head().build()).execute()) {
                final int code = response.code();
                if (code != 200) {
                    log.error("cannot HEAD {}: code {}", manifestUrl, code);
                    return;
                }
                final String lastModifiedStr = response.header("Last-Modified");
                if (lastModifiedStr != null) {
                    final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                    final Date thisModified = dateFormat.parse(modifiedStr);
                    final Date lastModified = dateFormat.parse(lastModifiedStr);
                    final boolean isNewVersionAvailable = lastModified != null && lastModified.after(thisModified);
                    if (isNewVersionAvailable) {
                        context.runOnUiThread(() -> {
                            if (hasExternalInstaller()) {
                                new AlertDialog.Builder(context)
                                        .setTitle(R.string.alert_update_available_title)
                                        .setMessage(R.string.alert_update_available_message_external_installer)
                                        .setPositiveButton(android.R.string.ok, (d, i) -> done(false))
                                        .create().show();
                            } else {
                                new AlertDialog.Builder(context)
                                        .setTitle(R.string.alert_update_available_title)
                                        .setMessage(R.string.alert_update_available_message)
                                        .setPositiveButton(R.string.alert_update_available_button_yes, (d, i) -> {
                                            downloadAndInstallApk();
                                        })
                                        .setNeutralButton(R.string.alert_update_available_button_download_only, (dialog, which) -> {
                                            showExternalDownloader();
                                        })
                                        .setNegativeButton(R.string.alert_update_available_button_no, null)
                                        .create().show();
                            }
                        });
                    }
                }
            } catch (IOException | ParseException e) {
                log.error("cannot HEAD {}: {}", manifestUrl, e.getMessage());
            }
        }).start();
    }

    public void showExternalDownloader() {
        if (!isApkUrlAvailable())
            return;

        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getApkUrl())));
    }
}
