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
import android.preference.Preference;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;

import java.util.List;

public class PreferenceActivity extends android.preference.PreferenceActivity {
    public static final String EXTRA_PREFKEY = "prefkey";
    public static final String EXTRA_HANDLER = "handler";

    public static void start(final Activity activity) {
        activity.startActivity(new Intent(activity, PreferenceActivity.class));
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

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
        handleIntent(getIntent());
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
    public void onBuildHeaders(final List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
        target.add(AboutFragment.getHeader());
        if (getResources().getBoolean(R.bool.flags_show_donate))
            loadHeadersFromResource(R.xml.preference_headers_donate, target);
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

    @Override
    protected boolean isValidFragment(final String fragmentName) {
        return CommonFragment.class.getName().equals(fragmentName)
                || UserInterfaceFragment.class.getName().equals(fragmentName)
                || DirectionsFragment.class.getName().equals(fragmentName)
                || NavigationFragment.class.getName().equals(fragmentName)
                || TravelAlarmFragment.class.getName().equals(fragmentName)
                || ShortcutsFragment.class.getName().equals(fragmentName)
                || AboutFragment.class.getName().equals(fragmentName)
                || DonateFragment.class.getName().equals(fragmentName);
    }
}
