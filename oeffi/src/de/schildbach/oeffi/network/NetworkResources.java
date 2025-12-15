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

package de.schildbach.oeffi.network;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import javax.annotation.Nullable;
import java.util.Locale;

import static java.util.Objects.requireNonNull;

import de.schildbach.pte.NetworkId;

public class NetworkResources {
    public final @Nullable Drawable icon;
    public final boolean isLogo;
    public final String label;
    public final @Nullable String comment;
    public final @Nullable String license;
    public final boolean cooperation;

    private NetworkResources(final @Nullable Drawable icon, final boolean isLogo, final String label,
            final @Nullable String comment, final @Nullable String license, final boolean cooperation) {
        this.icon = icon;
        this.isLogo = isLogo;
        this.label = requireNonNull(label);
        this.comment = comment;
        this.license = license;
        this.cooperation = cooperation;
    }

    public static NetworkResources instance(final Context context, final NetworkId networkId) {
        final String prefix = "network_" + networkId.name().toLowerCase(Locale.ENGLISH);
        final Resources res = context.getResources();
        final String packageName = context.getPackageName();

        final int iconId = res.getIdentifier(prefix + "_icon", "drawable", packageName);
        final int logoId = res.getIdentifier(prefix + "_logo", "drawable", packageName);
        final Drawable icon;
        final boolean isLogo;
        if (logoId != 0) {
            icon = res.getDrawable(logoId);
            isLogo = true;
        } else if (iconId != 0) {
            icon = res.getDrawable(iconId);
            isLogo = false;
        } else {
            icon = null;
            isLogo = false;
        }
        final int labelId = res.getIdentifier(prefix + "_label", "string", packageName);
        final String label = labelId != 0 ? res.getString(labelId) : networkId.name();
        final int commentId = res.getIdentifier(prefix + "_comment", "string", packageName);
        final String comment = commentId != 0 ? res.getString(commentId) : null;
        final int licenseId = res.getIdentifier(prefix + "_license", "string", packageName);
        final String license = licenseId != 0 ? res.getString(licenseId) : null;
        final boolean cooperation = isLogo;

        return new NetworkResources(icon, isLogo, label, comment, license, cooperation);
    }
}
