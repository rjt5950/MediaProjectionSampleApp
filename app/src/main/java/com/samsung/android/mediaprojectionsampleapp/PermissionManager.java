package com.samsung.android.mediaprojectionsampleapp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionManager {

    private final Activity activity;
    private final ActivityResultLauncher<String> notificationPermissionLauncher;

    public PermissionManager(Activity activity, ActivityResultLauncher<String> notificationPermissionLauncher) {
        this.activity = activity;
        this.notificationPermissionLauncher = notificationPermissionLauncher;
    }

    public boolean hasRequiredPermissions() {
        boolean notificationGranted = true;
        notificationGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

        boolean allFilesGranted = true;
        allFilesGranted = Environment.isExternalStorageManager();

        return notificationGranted && allFilesGranted;
    }

    public void requestRequiredPermissions(Runnable onAllPermissionsGranted) {
        // 1. Check Notification Permission (Android 13+)
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.notification_channel_name)
                        .setMessage(R.string.notification_permission_rationale)
                        .setPositiveButton(R.string.ok, (dialog, which) -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
            return;
        }

        // 2. Check All Files Access (Android 11+)
        if (!Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.all_files_access_title)
                    .setMessage(R.string.all_files_access_rationale)
                    .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.addCategory("android.intent.category.DEFAULT");
                            intent.setData(Uri.parse(String.format("package:%s", activity.getPackageName())));
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            activity.startActivity(intent);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        // If we reach here, all permissions are granted
        onAllPermissionsGranted.run();
    }

    public void handleNotificationPermissionResult(boolean isGranted, Runnable onNext) {
        if (isGranted) {
            requestRequiredPermissions(onNext);
        } else {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                showSettingsDialog();
            } else {
                android.widget.Toast.makeText(activity, R.string.notification_permission_denied, android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.permission_required_title)
                .setMessage(R.string.notification_permission_permanently_denied)
                .setPositiveButton(R.string.settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                    intent.setData(uri);
                    activity.startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
