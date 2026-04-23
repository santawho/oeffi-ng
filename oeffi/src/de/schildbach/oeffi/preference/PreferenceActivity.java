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

package de.schildbach.oeffi.preference;

import android.app.Activity;
import android.app.ComponentCaller;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;

public class PreferenceActivity extends OeffiActivity {
    public static final String EXTRA_PREFKEY = "prefkey";
    public static final String EXTRA_HANDLER = "handler";
    public static final String EXTRA_SHOW_FRAGMENT = "show_fragment";

    public static void start(final Activity activity) {
        start(activity, SettingsFragment.class);
    }

    public static void start(
            final Activity activity,
            final Class<? extends PreferenceFragment> fragmentClass) {
        final Intent intent = new Intent(activity, PreferenceActivity.class);
        intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentClass.getName());
        activity.startActivity(intent);
    }

    public static void setActionIntent(
            final Preference preference,
            final Class<? extends PreferenceFragment.ActionHandler> actionHandlerClass) {
        preference.setIntent(new Intent(Application.getInstance(), PreferenceActivity.class)
                .putExtra(EXTRA_PREFKEY, preference.getKey())
                .putExtra(EXTRA_HANDLER, actionHandlerClass.getName()));
    }

    public static void setActionIntent(
            final Preference preference,
            final PreferenceActivity activity,
            final Class<? extends PreferenceFragment> fragmentClass,
            final Class<? extends PreferenceFragment.ActionHandler> actionHandlerClass) {
        preference.setIntent(new Intent(activity, PreferenceActivity.class)
                .putExtra(EXTRA_PREFKEY, preference.getKey())
                .putExtra(EXTRA_SHOW_FRAGMENT, fragmentClass.getName())
                .putExtra(EXTRA_HANDLER, actionHandlerClass.getName()));
    }

    private PreferenceFragment preferenceFragment;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_content);

        final Intent intent = getIntent();

        try {
            final String fragmentClassName = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
            final Class<?> fragmentClass = fragmentClassName == null ? SettingsFragment.class
                    : Class.forName(fragmentClassName);
            preferenceFragment = (PreferenceFragment) fragmentClass.newInstance();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.preferences_fragment_container, preferenceFragment)
                    .commit();
        } catch (final ClassNotFoundException | IllegalAccessException |
                       InstantiationException e) {
            throw new RuntimeException(e);
        }

        final View contentView = findViewById(android.R.id.content);
        final int paddingLeft = contentView.getPaddingLeft();
        final int paddingTop = contentView.getPaddingTop();
        final int paddingRight = contentView.getPaddingRight();
        final int paddingBottom = contentView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    paddingLeft + insets.left,
                    0, // paddingTop + insets.top,
                    paddingRight + insets.right,
                    paddingBottom + insets.bottom);
            return windowInsets;
        });

        final MyActionBar actionBar = getMyActionBar();
        actionBar.setBackgroundColor(getColor(R.color.bg_action_bar_settings));
        actionBar.setBack(v -> onBackPressed());
        // actionBar.setPrimaryTitle(getTitle());

        handleIntent(intent);
    }

    @Override
    public void onNewIntent(@NonNull final Intent intent, @NonNull final ComponentCaller caller) {
        super.onNewIntent(intent, caller);
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        final String prefkey = intent.getStringExtra(EXTRA_PREFKEY);
        final String handlerClassName = intent.getStringExtra(EXTRA_HANDLER);
        final boolean finishActivity;
        if (prefkey != null && handlerClassName != null) {
            try {
                final Class<?> handlerClass = Class.forName(handlerClassName);
                final PreferenceFragment.ActionHandler actionHandler = (PreferenceFragment.ActionHandler) handlerClass.newInstance();
                finishActivity = actionHandler.handleAction(this, prefkey);
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                finish();
                throw new RuntimeException(e);
            }
        } else {
            finishActivity = false;
        }
        if (finishActivity)
            finish();
    }

    @Override
    public void onBackPressedEvent() {
        realOnBackPressed();
    }

    public void setSubTitle(final CharSequence title) {
        final MyActionBar actionBar = getMyActionBar();
        final String mainTitle = getString(R.string.global_options_preferences_title);
        setTitle(mainTitle);
        actionBar.setSecondaryTitle(title.equals(mainTitle) ? null : title);
    }

    @Override
    public void setTitle(final CharSequence title) {
        super.setTitle(title);
        final MyActionBar actionBar = getMyActionBar();
        actionBar.setPrimaryTitle(title);
        actionBar.setSecondaryTitle(null);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
