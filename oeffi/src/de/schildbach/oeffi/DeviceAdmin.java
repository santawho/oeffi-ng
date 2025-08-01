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

import static android.content.Context.KEYGUARD_SERVICE;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class DeviceAdmin {
    public static class MyDeviceAdminReceiver extends DeviceAdminReceiver {
    }

    public static class MyAccessibilityService extends AccessibilityService {
        public static MyAccessibilityService instance;

        @Override
        public void onCreate() {
            super.onCreate();
            instance = this;
        }

        @Override
        public void onDestroy() {
            instance = null;
            super.onDestroy();
        }

        @Override
        protected void onServiceConnected() {
            super.onServiceConnected();
            final AccessibilityServiceInfo info = getServiceInfo();
            info.packageNames = new String[] { Application.getApplicationId() };
            // info.eventTypes = 0;
            // info.flags = 0;
            // info.feedbackType = AccessibilityServiceInfo.FEEDBACK_HAPTIC;
            // info.notificationTimeout = 100;
            setServiceInfo(info);
        }

        @Override
        public void onAccessibilityEvent(final AccessibilityEvent event) {
        }

        @Override
        public void onInterrupt() {
        }
    }

    public static boolean isScreenLocked() {
        final KeyguardManager keyguard = (KeyguardManager) Application.getInstance().getSystemService(KEYGUARD_SERVICE);
        return keyguard.isKeyguardLocked();
    }

    public static void enterLockScreenAndShutOffDisplay(final Activity activity, final boolean onlyWhenAlreadyLocked) {
        if (onlyWhenAlreadyLocked && !isScreenLocked())
            return;

        // NOTE: this works, but it disables the finger print unlock
        // lockUsingDeviceAdmin(activity);
        lockUsingAccessibilityService(activity);
    }

    public static void lockUsingAccessibilityService(final Activity activity) {
        if (MyAccessibilityService.instance == null)
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            MyAccessibilityService.instance.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
        }
    }

    public static void lockUsingDeviceAdmin(final Activity activity) {
        // NOTE: this works, but it disables the finger print unlock

        final DevicePolicyManager devicePolicyManager = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        final ComponentName adminComponent = new ComponentName(activity, MyDeviceAdminReceiver.class);
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            devicePolicyManager.lockNow();
        } else {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            // intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Additional text explaining why this needs to be added.");
            activity.startActivity(intent);
        }
    }
}
