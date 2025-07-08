package de.schildbach.oeffi.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import java.util.function.Consumer;

import javax.annotation.Nullable;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import okhttp3.HttpUrl;

public class AppInstaller {
    private static final Logger log = LoggerFactory.getLogger(AppInstaller.class);
    private final Activity context;
    final Consumer<Boolean> doneListener;

    public AppInstaller(final Activity context, final Consumer<Boolean> doneListener) {
        this.context = context;
        this.doneListener = doneListener;
    }

    public void downloadAndInstallApk(final String apkUrl) {
        try {
            log.info("downloading APK file for install from {}", apkUrl);
            final Application application = Application.getInstance();
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

        final PackageManager packageManager = application.getPackageManager();
        final String installerPackageName = packageManager.getInstallerPackageName(application.getPackageName());
            // "com.android.packageinstaller"
            // "com.google.android.packageinstaller"
        log.info("installing APK file {} using installer {}", apkFile, installerPackageName);

        final Uri contentUri = FileProvider.getUriForFile(application, application.getPackageName(), apkFile);
        context.startActivity(new Intent(Intent.ACTION_VIEW)
                .setPackage(installerPackageName)
                .setDataAndType(contentUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        done(true);
    }
}
