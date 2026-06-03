package com.samsung.android.mediaprojectionsampleapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import android.provider.Settings;
import android.net.Uri;
import android.os.Environment;

import androidx.appcompat.app.AlertDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MediaProjectionSample";
    private Button toggleCaptureButton;
    private MediaProjectionManager projectionManager;
    private boolean isCapturing = false;
    private ActivityResultLauncher<Intent> projectionLauncher;

    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "stopReceiver onReceive: " + intent.getAction());
            if (MediaProjectionService.ACTION_STOPPED.equals(intent.getAction())) {
                isCapturing = false;
                updateToggleButton();
            }
        }
    };

    // ActivityResultLauncher for notification permission (Android 13+)
    private final ActivityResultLauncher<String> mPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted");
                    launchMediaProjection();
                } else {
                    Log.w(TAG, "Notification permission denied");
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                            android.Manifest.permission.POST_NOTIFICATIONS)) {
                        // Permission permanently denied
                        showSettingsDialog();
                    } else {
                        Toast.makeText(this, "Notification permission is required to show capture status",
                                Toast.LENGTH_LONG).show();
                    }
                }
            });

    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Notification permission is permanently denied. Please enable it in settings to use screen capture.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        isCapturing = MediaProjectionService.isRunning();
        updateToggleButton();
        IntentFilter filter = new IntentFilter(MediaProjectionService.ACTION_STOPPED);
        registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(stopReceiver);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toggleCaptureButton = findViewById(R.id.toggleCaptureButton);

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        projectionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK) {
                Log.d(TAG, "User denied screen sharing permission");
                return;
            }

            Log.d(TAG, "User granted screen sharing permission");

            Intent serviceIntent = new Intent(MainActivity.this, MediaProjectionService.class);
            serviceIntent.setAction(MediaProjectionService.ACTION_START);
            serviceIntent.putExtra(MediaProjectionService.EXTRA_RESULT_CODE, result.getResultCode());
            serviceIntent.putExtra(MediaProjectionService.EXTRA_RESULT_DATA, result.getData());
            startForegroundService(serviceIntent);
            Log.d(TAG, "Foreground service started");

            isCapturing = true;
            updateToggleButton();
        });

        toggleCaptureButton.setOnClickListener(v -> {
            if (!isCapturing) {
                if (hasRequiredPermissions()) {
                    launchMediaProjection();
                } else {
                    requestRequiredPermissions();
                }
            } else {
                Intent stopIntent = new Intent(MainActivity.this, MediaProjectionService.class);
                stopIntent.setAction(MediaProjectionService.ACTION_STOP);
                startService(stopIntent);

                isCapturing = false;
                updateToggleButton();
            }
        });

        updateToggleButton();
    }

    /**
     * Update the toggle button text and state based on capture status.
     */
    private void updateToggleButton() {
        Log.d(TAG, "updateToggleButton: isCapturing = " + isCapturing);
        toggleCaptureButton.setText(isCapturing ? "Stop Capture" : "Start Capture");
    }

    private boolean hasRequiredPermissions() {
        boolean notificationGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean allFilesGranted;
        allFilesGranted = Environment.isExternalStorageManager();
        return notificationGranted && allFilesGranted;
    }

    private void requestRequiredPermissions() {
        // 1. Check Notification Permission (Android 13+)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                new AlertDialog.Builder(this)
                        .setTitle("Notification Permission Needed")
                        .setMessage("This app needs notification permission to show screen capture status in the status bar.")
                        .setPositiveButton("OK", (dialog, which) -> mPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS))
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                mPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
            return;
        }

        // 2. Check All Files Access (Android 11+)
        if (!Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this)
                    .setTitle("All Files Access Required")
                    .setMessage("This app needs 'All Files Access' to save screenshots in a custom folder at the root of internal storage.")
                    .setPositiveButton("Go to Settings", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.addCategory("android.intent.category.DEFAULT");
                            intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                            startActivity(intent);
                        } catch (Exception e) {
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // If we reach here, all permissions are granted
        launchMediaProjection();
    }

    /**
     * Launch the MediaProjection screen capture intent.
     */
    private void launchMediaProjection() {
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        projectionLauncher.launch(captureIntent);
    }
}
