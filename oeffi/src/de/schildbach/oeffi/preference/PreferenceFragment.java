package de.schildbach.oeffi.preference;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import java.util.Map;
import java.util.function.Function;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.util.DialogBuilder;

public abstract class PreferenceFragment extends androidx.preference.PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static abstract class ActionHandler {
        // return true, to finish the parenting PreferenceActivity after handling the action
        // return false to do it later by calling dismissParentingActivity()
        abstract boolean handleAction(final PreferenceActivity context, final String prefkey);

        protected void dismissParentingActivity(final PreferenceActivity context) {
            context.finish();
        }
    }

    public static boolean isSuperClassOf(final String fragmentClassName) {
        try {
            return PreferenceFragment.class.isAssignableFrom(Class.forName(fragmentClassName));
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    protected PreferenceActivity preferenceActivity;
    protected SharedPreferences prefs;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        preferenceActivity = (PreferenceActivity) context;
        prefs = Application.getInstance().getSharedPreferences();
    }

    @Override
    public void onStart() {
        super.onStart();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        final CharSequence title = getPreferenceScreen().getTitle();
        if (title != null && title.length() > 0)
            preferenceActivity.setSubTitle(title);
    }

    public CharSequence getTitle() {
        return getPreferenceScreen().getTitle();
    }

    protected void setupDynamicSummary(final String preferenceKey, final int summaryResId) {
        setupDynamicSummary(preferenceKey, summaryResId, null);
    }

    protected void setupDynamicSummary(
            final String preferenceKey,
            final int summaryResId,
            final Function<Object, Object> valueMapper) {
        final Preference preference = findPreference(preferenceKey);
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            final Object realValue = valueMapper == null ? newValue : valueMapper.apply(newValue);
            preference.setSummary(getString(summaryResId, realValue));
            return true;
        });
        final Map<String, ?> all = preference.getSharedPreferences().getAll();
        final Object value = all.get(preference.getKey());
        final Object realValue = valueMapper == null ? value : valueMapper.apply(value);
        preference.setSummary(getString(summaryResId, realValue));
    }

    public void preferenceChanged(final String preferenceKey, final Object newValue) {
        final Preference preference = findPreference(preferenceKey);
        if (preference == null)
            return;
        final Preference.OnPreferenceChangeListener onPreferenceChangeListener = preference.getOnPreferenceChangeListener();
        if (onPreferenceChangeListener == null)
            return;
        onPreferenceChangeListener.onPreferenceChange(preference, newValue);
    }

    protected void disablePreference(final String preferenceName) {
        disablePreference(findPreference(preferenceName));
    }

    protected void disablePreference(final Preference preference) {
        if (preference == null)
            return;

        preference.setEnabled(false);
    }

    protected void removePreference(final String preferenceName) {
        removePreference(findPreference(preferenceName));
    }

    protected void removePreference(final Preference preference) {
        if (preference == null)
            return;

        preference.getParent().removePreference(preference);
    }

    protected void addPreference(final Preference preference) {
        addPreferenceToGroup(preference, getPreferenceScreen());
    }

    protected void addPreferenceToGroup(final Preference preference, final PreferenceGroup preferenceGroup) {
        preferenceGroup.addPreference(preference);
    }

    protected void setupActionPreference(
            final String prefkey,
            final Class<? extends ActionHandler> actionHandlerClass) {
        final Preference preference = findPreference(prefkey);
        PreferenceActivity.setActionIntent(preference, actionHandlerClass);
    }

    protected void setupActionPreference(
            final String prefkey,
            final Class<? extends PreferenceFragment> fragmentClass,
            final Class<? extends ActionHandler> actionHandlerClass) {
        final Preference preference = findPreference(prefkey);
        PreferenceActivity.setActionIntent(preference, preferenceActivity, fragmentClass, actionHandlerClass);
    }

    protected void setupCustomPreference(
            final String prefkey,
            final CustomPreference.ClickHandler clickHandler) {
        final Preference preference = findPreference(prefkey);
        if (preference instanceof CustomPreference)
            ((CustomPreference) preference).setClickHandler(clickHandler);
    }

    public static abstract class ShowHelpHandler extends ActionHandler {
        protected int getHelpTextResourceId() {
            return 0;
        }

        protected CharSequence getHelpText() {
            final int helpTextResourceId = getHelpTextResourceId();
            if (helpTextResourceId == 0)
                return null;
            return Application.getInstance().getText(helpTextResourceId);
        }

        @Override
        public boolean handleAction(final PreferenceActivity context, final String prefkey) {
            final CharSequence helpText = getHelpText();
            if (helpText == null)
                return true;
            DialogBuilder.get(context)
                    .setMessage(helpText)
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener(dialog1 -> dismissParentingActivity(context))
                    .show();
            return false;
        }
    }

    protected boolean isPreferenceRequiringRestart(final String key) {
        return false;
    }

    private static boolean restartRequired;

    public static void clearRestartRequired() {
        restartRequired = false;
    }
    public static boolean isRestartRequired() {
        return restartRequired;
    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences sharedPreferences, @Nullable final String key) {
        if (key == null)
            return;
        if (isPreferenceRequiringRestart(key))
            restartRequired = true;
    }
}
