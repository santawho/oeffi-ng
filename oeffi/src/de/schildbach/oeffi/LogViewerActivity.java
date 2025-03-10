package de.schildbach.oeffi;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class LogViewerActivity extends OeffiActivity {
    private static final Logger log = LoggerFactory.getLogger(LogViewerActivity.class);

    public static void start(final Context context) {
        final Intent intent = new Intent(context, LogViewerActivity.class);
        context.startActivity(intent);
    }

    private MyActionBar actionBar;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.logviewer_content);

        actionBar = findViewById(R.id.action_bar);
        actionBar.setBack(null);
        actionBar.setBackgroundColor(getResources().getColor(R.color.bg_action_bar_logviewer));
        actionBar.setPrimaryTitle(R.string.global_options_show_log_title);
        actionBar.addProgressButton().setOnClickListener(view -> refresh());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        final File logFile = Application.getInstance().getLogFile();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            actionBar.startProgress();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } catch (IOException e) {
            log.error("reading {}", logFile, e);
            return;
        } finally {
            actionBar.stopProgress();
        }
        final TextView textView = findViewById(R.id.logviewer_list);
        textView.setText(sb.toString());
        final ScrollView scrollView = findViewById(R.id.logviewer_scroll);
        scrollView.getChildAt(0).post(() -> {
            final int height = textView.getHeight();
            scrollView.scrollTo(0, height);
        });
    }
}
