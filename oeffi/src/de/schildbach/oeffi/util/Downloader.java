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

import de.schildbach.oeffi.util.bzip2.BZip2CompressorInputStream;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static de.schildbach.pte.util.Preconditions.checkState;

public class Downloader {
    private final File cacheDir;
    private final ConcurrentHashMap<File, Semaphore> semaphores = new ConcurrentHashMap<>();

    private static final Random random = new Random();
    private static final Logger log = LoggerFactory.getLogger(Downloader.class);

    public interface ProgressCallback {
        void progress(long contentRead, long contentLength);
    }

    public Downloader(final File cacheDir) {
        this.cacheDir = cacheDir;
    }

    public CompletableFuture<Integer> download(final OkHttpClient okHttpClient, final HttpUrl remoteUrl,
                                                      final File targetFile) {
        return download(okHttpClient, remoteUrl, targetFile, false, null);
    }

    public CompletableFuture<Integer> download(final OkHttpClient okHttpClient, final HttpUrl remoteUrl,
            final File targetFile, final boolean unzip) {
        return download(okHttpClient, remoteUrl, targetFile, unzip, null);
    }

    public CompletableFuture<Integer> download(final OkHttpClient okHttpClient, final HttpUrl remoteUrl,
            final File targetFile, final boolean unzip, @Nullable final ProgressCallback progressCallback) {
        final CompletableFuture<Integer> future = new CompletableFuture<>();
        final Semaphore semaphore = semaphores.computeIfAbsent(targetFile, file -> new Semaphore(1));
        if (semaphore.tryAcquire()) {
            final Headers meta = targetFile.exists() ? loadMeta(targetFile) : null;
            final Request.Builder request = new Request.Builder();
            request.url(remoteUrl);
            if (meta != null) {
                final Date expires = meta.getDate("Expires");
                if (expires != null && System.currentTimeMillis() < expires.getTime()) {
                    log.info("Download '{}' skipped; using cached copy.", remoteUrl);
                    future.complete(HttpURLConnection.HTTP_NOT_MODIFIED);
                    semaphore.release();
                    return future;
                }

                final String lastModified = meta.get("Last-Modified");
                if (lastModified != null)
                    request.header("If-Modified-Since", lastModified);
                final String etag = meta.get("ETag");
                if (etag != null)
                    request.header("If-None-Match", etag);
            }
            final Call call = okHttpClient.newCall(request.build());
            call.enqueue(new Callback() {
                private final File tempFile = new File(cacheDir,
                        targetFile.getName() + ".part." + String.format("%04x", random.nextInt(0x10000)));

                public void onResponse(final Call call, final Response r) throws IOException {
                    try (final Response response = r) {
                        final int status = response.code();
                        if (status == HttpURLConnection.HTTP_OK) {
                            final ResponseBody body = response.body();
                            final InputStream is = unzip ? new BZip2CompressorInputStream(body.byteStream())
                                    : body.byteStream();
                            final OutputStream os = new FileOutputStream(tempFile);
                            final byte[] buf = new byte[4096];
                            long count = 0;
                            int read;
                            while (-1 != (read = is.read(buf)) && !future.isCancelled()) {
                                os.write(buf, 0, read);
                                count += read;
                                if (progressCallback != null) {
                                    final long contentRead = count;
                                    final long contentLength = body.contentLength();
                                    progressCallback.progress(contentRead, contentLength);
                                }
                            }
                            os.close();
                            saveMeta(targetFile, response.headers());
                            tempFile.renameTo(targetFile); // Atomic operation
                            log.info("Download '{}' successful; {} content bytes read.", call.request().url(), count);
                            future.complete(status);
                        } else if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            log.info("Download '{}' skipped; nothing changed.", call.request().url());
                            saveMeta(targetFile, response.headers());
                            future.complete(status);
                        } else {
                            log.info("Download '{}' failed: {} {}", call.request().url(), status, response.message());
                            future.completeExceptionally(new IOException(String.format(
                                    "download %s failed, status %d", call.request().url(), status)));
                        }
                        semaphore.release();
                    } finally {
                        tempFile.delete();
                    }
                }

                public void onFailure(final Call call, final IOException e) {
                    log.info("Downloading {} failed: {}", call.request().url(), e.getMessage());
                    future.completeExceptionally(e);
                    semaphore.release();
                }
            });
        } else {
            log.info("Download '{}' skipped; already in progress.", remoteUrl);
            future.complete(HttpURLConnection.HTTP_CONFLICT);
        }
        return future;
    }

    private void saveMeta(final File file, final Headers headers) {
        final String expires = headers.get("Expires");
        final String lastModified = headers.get("Last-Modified");
        final String etag = headers.get("ETag");
        final File metaFile = metaFile(file);
        if (expires != null || etag != null) {
            try (final PrintWriter writer = new PrintWriter(metaFile)) {
                if (expires != null)
                    writer.println("Expires: " + expires);
                if (lastModified != null)
                    writer.println("Last-Modified: " + lastModified);
                if (etag != null)
                    writer.println("ETag: " + etag);
            } catch (final IOException x) {
                log.warn("Problem saving expiration time " + metaFile, x);
            }
        } else {
            metaFile.delete();
        }
    }

    private Headers loadMeta(final File file) {
        checkState(file.exists());
        final Headers.Builder builder = new Headers.Builder();
        final File metaFile = metaFile(file);
        if (metaFile.exists()) {
            String line = null;
            try (final BufferedReader reader = new BufferedReader(new FileReader(metaFile), 128)) {
                while (true) {
                    line = reader.readLine();
                    if (line == null)
                        break;
                    final int sep = line.indexOf(':');
                    if (sep == -1)
                        break;
                    final String name = line.substring(0, sep).trim();
                    final String value = line.substring(sep + 1).trim();
                    builder.add(name, value);
                }
            } catch (final IOException x) {
                throw new RuntimeException("Problem loading meta data " + metaFile, x);
            } catch (final Exception x) {
                throw new RuntimeException("Problem parsing meta data: '" + line + "'", x);
            }
        }
        return builder.build();
    }

    private static File metaFile(final File file) {
        return new File(file.getPath() + ".meta");
    }

    public static void deleteDownload(final File file) {
        file.delete();
        metaFile(file).delete();
    }
}
