package de.schildbach.oeffi.preference;

import android.content.Context;
import android.os.Build;
import android.preference.Preference;

public class PreferenceFragment extends android.preference.PreferenceFragment {
    public static abstract class ActionHandler {
        // return true, to finish the parenting PreferenceActivity after handling the action
        // return false to do it later by calling dismissParentingActivity()
        abstract boolean handleAction(final PreferenceActivity context, final String prefkey);

        protected void dismissParentingActivity(final PreferenceActivity context) {
            context.finish();
        }
    }

    protected PreferenceActivity preferenceActivity;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        preferenceActivity = (PreferenceActivity) context;
    }

    protected void removeOrDisablePreference(final String preferenceName) {
        removeOrDisablePreference(findPreference(preferenceName));
    }

    protected void removeOrDisablePreference(final Preference preference) {
        if (preference == null)
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            preference.getParent().removePreference(preference);
        else
            preference.setEnabled(false);
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
}
