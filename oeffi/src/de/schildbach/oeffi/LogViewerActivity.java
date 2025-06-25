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
import android.os.Bundle;
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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

import de.schildbach.oeffi.util.ColorHash;

public class LogViewerActivity extends OeffiActivity {
    private static final Logger log = LoggerFactory.getLogger(LogViewerActivity.class);

    private static final ColorHash colorHash = new ColorHash(
            Arrays.asList(0.20, 0.30, 0.40), // available lightness values
            Arrays.asList(0.50, 0.70, 0.90), // available saturation values
            0, 360,                  // hue range
            ColorHash::javaHash              // try ColorHash::javaHash  or  ColorHash::bkdrHash
    );

    public static void start(final Context context) {
        final Intent intent = new Intent(context, LogViewerActivity.class);
        context.startActivity(intent);
    }

    private MyActionBar actionBar;
    private boolean firstLoad;

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

    private void refresh(final boolean scrollToEnd) {
        final File logFile = Application.getInstance().getLogFile();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            actionBar.startProgress();
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) sb.append("<br>");
                final String htmlLine = makeHtmlLine(line);
                sb.append(htmlLine);
                first = false;
            }
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
    }

    private String makeHtmlLine(final String line) {
        final int loggerNameStart = line.indexOf('[');
        final int loggerNameEnd = line.indexOf(']');
        if (loggerNameStart >= 0 && loggerNameEnd > loggerNameStart) {
            final String loggerName = line.substring(loggerNameStart + 1, loggerNameEnd);
            final String color = colorHash.toHexString(loggerName);
            return "<font color=\"" + color + "\">" + Html.escapeHtml(line) + "</font>";
        }
        return line;
    }
}
