package de.schildbach.oeffi.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

public class ResourceUri {
    public static Uri fromResource(final Context context, final int resourceId) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.getPackageName())
                .path(Integer.toString(resourceId))
                .build();
    }
}
