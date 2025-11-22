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

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Looper;
import android.provider.CalendarContract;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class CalendarHelper {
    public static boolean hasCalendarPermissions(final Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean requestCalendarPermissions(final Context context) {
        if (hasCalendarPermissions(context))
            return true;
        if (!(context instanceof Activity))
            return false;
        final Activity activity = (Activity) context;
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR))
            return false;
        if (Looper.getMainLooper().isCurrentThread()) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_CALENDAR}, 0);
        } else {
            activity.runOnUiThread(() -> {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_CALENDAR}, 0);
            });
        }
        return true;
    }

    static final String CALENDAR_PROJECTION[] = {
            CalendarContract.Calendars._ID,                           // 0
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,         // 1
            CalendarContract.Calendars.ACCOUNT_NAME,                  // 2
            CalendarContract.Calendars.OWNER_ACCOUNT,                 // 3
            CalendarContract.Calendars.IS_PRIMARY                     // 4
    };

    public static Integer findCalendarForName(final Context context, final String calendarName) {
        if (!hasCalendarPermissions(context))
            return null;

        final ContentResolver contentResolver = context.getContentResolver();
        final Cursor cursor = contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
                CALENDAR_PROJECTION, null, null, null);
        Integer foundCalendarId = null;
        if (cursor != null && cursor.moveToFirst()) {
            final int idCol = cursor.getColumnIndex(CALENDAR_PROJECTION[0]);
            final int nameCol = cursor.getColumnIndex(CALENDAR_PROJECTION[1]);
//            final int accountNameCol = cursor.getColumnIndex(CALENDAR_PROJECTION[2]);
//            final int ownerAccountCol = cursor.getColumnIndex(CALENDAR_PROJECTION[3]);
//            final int isPrimaryCol = cursor.getColumnIndex(CALENDAR_PROJECTION[4]);
            do {
                final int calID = cursor.getInt(idCol);
                final String calName = cursor.getString(nameCol);
//                final String accountName = managedCursor.getString(accountNameCol);
//                final String ownerAccount = managedCursor.getString(ownerAccountCol);
//                final String isPrimary = managedCursor.getString(isPrimaryCol);
                if (calName.equals(calendarName)) {
                    foundCalendarId = calID;
                    break;
                }
            } while (cursor.moveToNext());
            cursor.close();
        }
        return foundCalendarId;
    }
}
