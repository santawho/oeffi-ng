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

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;

public class AppChooser {
    public static class ComponentInfo {
        static final String SEPARATOR = "/";

        final private String packageName;
        final private String className;
        final private String label;
        final int typeIndex;

        public ComponentInfo(
                final String packageName,
                final String className,
                final String label,
                final int typeIndex) {
            this.packageName = packageName;
            this.className = className;
            this.label = label;
            this.typeIndex = typeIndex;
        }

        public ComponentInfo(final String prefValue) {
            final String[] split = prefValue == null ? null : prefValue.split(SEPARATOR, 4);
            if (split != null && split.length == 4) {
                packageName = split[0];
                className = split[1];
                typeIndex = Formats.parseInt(split[2], 0);
                label = split[3];
            } else {
                packageName = null;
                className = null;
                label = null;
                typeIndex = 0;
            }
        }

        public String toPrefValue() {
            if (packageName == null)
                return null;
            return packageName + SEPARATOR + className + SEPARATOR + typeIndex + SEPARATOR + label;
        }

        public String getLabel() {
            return label;
        }

        public ComponentName getComponentName() {
            return new ComponentName(packageName, className);
        }
    }

    public static class IntentAndDescription {
        public Intent intent;
        public String description;

        public IntentAndDescription(
                final Intent intent,
                final String description) {
            this.intent = intent;
            this.description = description;
        }

        public IntentAndDescription(
                final Intent intent) {
            this.intent = intent;
            this.description = null;
        }
    }

    public static void chooseActivityForIntent(
            final Activity context,
            final IntentAndDescription[] intents,
            final String contentDescription,
            final Consumer<ComponentInfo> resultConsumer) {
        new AppChooser().run(context, intents, contentDescription, resultConsumer);
    }

    private Consumer<ComponentInfo> resultConsumer;
    private Dialog dialog;

    private void run(
            final Activity context,
            final IntentAndDescription[] intents,
            final String contentDescription,
            final Consumer<ComponentInfo> resultConsumer) {
        this.resultConsumer = resultConsumer;

        final PackageManager packageManager = Application.getInstance().getPackageManager();

        final DialogBuilder dialogBuilder = DialogBuilder.get(context, R.layout.app_chooser_content);
        final LinearLayout contentView = (LinearLayout) dialogBuilder.getView();
        final String title = context.getString(R.string.app_chooser_title, contentDescription);
        ((TextView) contentView.findViewById(R.id.app_chooser_title)).setText(title);

        final String myPackageName = context.getPackageName();
        final LinearLayout appsView = contentView.findViewById(R.id.app_chooser_apps);

        int numEntries = 0;
        for (int typeIndex = 0; typeIndex < intents.length; typeIndex++) {
            final IntentAndDescription intentAndDescription = intents[typeIndex];
            if (intentAndDescription == null)
                continue;
            final String description = intentAndDescription.description;
            final List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intentAndDescription.intent, PackageManager.MATCH_ALL);
            for (final ResolveInfo resolveInfo : resolveInfos) {
                final ActivityInfo activityInfo = resolveInfo.activityInfo;
                if (activityInfo.packageName.equals(myPackageName))
                    continue;

                final ViewGroup itemView = (ViewGroup) context.getLayoutInflater().inflate(R.layout.app_chooser_item, null);

                final Drawable icon = activityInfo.loadIcon(packageManager);
                final ImageView iconView = itemView.findViewById(R.id.app_chooser_item_image);
                iconView.setImageDrawable(icon);

                final TextView nameView = itemView.findViewById(R.id.app_chooser_item_name);
                nameView.setText(getLabel(resolveInfo, description, packageManager));

                final int finalTypeIndex = typeIndex;
                itemView.setOnClickListener(v -> onClick(resolveInfo, finalTypeIndex, description));

                appsView.addView(itemView);
                numEntries += 1;
            }
        }

        if (numEntries > 0)
            contentView.findViewById(R.id.app_chooser_no_apps).setVisibility(View.GONE);
        else
            appsView.setVisibility(View.GONE);

        dialogBuilder.setCanceledOnTouchOutside(true);
        dialog = dialogBuilder.create();
        dialog.show();
    }

    private void onClick(final ResolveInfo resolveInfo, final int typeIndex, final String description) {
        dialog.dismiss();

        final PackageManager packageManager = Application.getInstance().getPackageManager();

        final ActivityInfo activityInfo = resolveInfo.activityInfo;
        resultConsumer.accept(new ComponentInfo(
                activityInfo.packageName, activityInfo.name,
                getLabel(resolveInfo, description, packageManager).toString(),
                typeIndex));
    }

    private static CharSequence getLabel(
            final ResolveInfo resolveInfo,
            final String description,
            final PackageManager packageManager) {
        final ActivityInfo activityInfo = resolveInfo.activityInfo;
        CharSequence applicationLabel;
        try {
            final ApplicationInfo applicationInfo = packageManager.getApplicationInfo(activityInfo.packageName, PackageManager.GET_META_DATA);
            applicationLabel = packageManager.getApplicationLabel(applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            applicationLabel = null;
        }
        final CharSequence activityLabel = activityInfo.loadLabel(packageManager);
        CharSequence label = (applicationLabel == null || applicationLabel.equals(activityLabel))
                ? activityLabel
                : applicationLabel + " / " + activityLabel;
        if (description != null)
            label = label + " (" + description + ")";
        return label;
    }
}
