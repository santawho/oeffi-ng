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

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import de.schildbach.oeffi.util.ColorHash;

public class LogViewerActivity extends OeffiActivity {
    private static final Logger log = LoggerFactory.getLogger(LogViewerActivity.class);

    private static final int BLOCK_SIZE = 200000;

    private static final ColorHash colorHash = new ColorHash(
            Arrays.asList(0.25, 0.27, 0.35, 0.40, 0.45), // available lightness values
            Arrays.asList(0.50, 0.60, 0.70, 0.80, 0.90), // available saturation values
            0, 360,                  // hue range
            ColorHash::md5Hash              // try ColorHash::javaHash  or  ColorHash::bkdrHash
    );

    public static void start(final Context context) {
        final Intent intent = new Intent(context, LogViewerActivity.class);
        context.startActivity(intent);
    }

    private MyActionBar actionBar;
    private boolean firstLoad;
    private final Map<String, String> colorMap = new HashMap<>();
    private long currentOffset;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.logviewer_content);

        firstLoad = true;
        actionBar = findViewById(R.id.action_bar);
        actionBar.setBack(null);
        actionBar.setBackgroundColor(getResources().getColor(R.color.bg_action_bar_logviewer));
        actionBar.setPrimaryTitle(R.string.global_options_show_log_title);
        actionBar.addProgressButton().setOnClickListener(view -> refresh(true));
        actionBar.addButton(R.drawable.ic_expand_more_white_24dp, R.string.log_viewer_button_forwards_button)
                .setOnClickListener(v -> moveRelative(BLOCK_SIZE / 2));
        actionBar.addButton(R.drawable.ic_expand_less_white_24dp, R.string.log_viewer_button_backwards_button)
                .setOnClickListener(v -> moveRelative(-(BLOCK_SIZE / 2)));

        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(firstLoad);
        firstLoad = false;
    }

    private void moveRelative(final long offset) {
        currentOffset += offset;
        refresh(false);
    }

    private void refresh(final boolean scrollToEnd) {
        final File logFile = Application.getInstance().getLogFile();
        final long fileSize = logFile.length();
        StringBuilder sb = new StringBuilder();
        final boolean endOfScrollReached;
        final long maxOffset = fileSize - BLOCK_SIZE;
        if (scrollToEnd)
            currentOffset = maxOffset;
        if (currentOffset < 0) {
            currentOffset = 0;
            endOfScrollReached = true;
        } else if (currentOffset > maxOffset) {
            currentOffset = maxOffset;
            endOfScrollReached = true;
        } else {
            endOfScrollReached = false;
        }
        actionBar.startProgress();
        try (FileInputStream fis = new FileInputStream(logFile)) {
            fis.skip(currentOffset);
            final byte[] block = new byte[BLOCK_SIZE];
            final int blockSize = fis.read(block);
            int startOfFirstLine = 0;
            if (currentOffset != 0) {
                while (startOfFirstLine < blockSize) {
                    if (block[startOfFirstLine] == '\n') {
                        startOfFirstLine += 1;
                        break;
                    }
                    startOfFirstLine += 1;
                }
            }
            boolean isLastLineComplete = blockSize > 0 && block[blockSize - 1] == '\n';
            final BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new ByteArrayInputStream(block, startOfFirstLine, blockSize - startOfFirstLine)));
            String line;
            String prevLine = null;
            int numLines = 0;
            while ((line = reader.readLine()) != null) {
                if (prevLine != null) {
                    if (numLines > 0)
                        sb.append("<br>");
                    sb.append(prevLine);
                    numLines += 1;
                }
                prevLine = makeHtmlLine(line);
            }
            if (isLastLineComplete && prevLine != null)
                sb.append(prevLine);
        } catch (IOException e) {
            log.error("reading {}", logFile, e);
            return;
        } finally {
            actionBar.stopProgress();
        }
        final TextView textView = findViewById(R.id.logviewer_list);
        textView.setText(Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_COMPACT));
        if (scrollToEnd) {
            final ScrollView scrollView = findViewById(R.id.logviewer_scroll);
            scrollView.getChildAt(0).post(() -> {
                final int height = textView.getHeight();
                scrollView.scrollTo(0, height);
            });
        }

        if (endOfScrollReached) {
            final int normalColor = getColor(R.color.bg_level0);
            final Color colorA = Color.valueOf(normalColor);
            final Color colorB = Color.valueOf(1 - colorA.red(), 1 - colorA.green(), 1 - colorA.blue(), colorA.alpha());
            final int invertedColor = colorB.toArgb();
            textView.setBackgroundColor(invertedColor);
            new Handler(Looper.getMainLooper()).postDelayed(() -> textView.setBackgroundColor(normalColor), 500);
        }
    }

    private String makeHtmlLine(final String line) {
        final int loggerNameStart = line.indexOf('[');
        final int loggerNameEnd = line.indexOf(']');
        if (loggerNameStart >= 0 && loggerNameEnd > loggerNameStart) {
            final String loggerName = line.substring(loggerNameStart + 1, loggerNameEnd);
            String color = colorMap.get(loggerName);
            if (color == null) {
                color = colorHash.toHexString(loggerName);
                colorMap.put(loggerName, color);
            }
            return "<font color=\"" + color + "\">" + Html.escapeHtml(line) + "</font>";
        }
        return line;
    }
}
