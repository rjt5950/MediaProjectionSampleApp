package com.samsung.android.mediaprojectionsampleapp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

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

    // ActivityResultLauncher for notification permission (Android 13+)
    private final ActivityResultLauncher<String> mPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "Notification permission granted");
                    launchMediaProjection();
                } else {
                    Log.w(TAG, "Notification permission denied - cannot show foreground service notification");
                    if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                            android.Manifest.permission.POST_NOTIFICATIONS)) {
                        // Permission denied (not permanently), show rationale
                        Toast.makeText(MainActivity.this, "Notification permission is required to show capture status",
                                Toast.LENGTH_LONG).show();
                    } else {
                        // Permission permanently denied
                        Toast.makeText(MainActivity.this, "Notification permission is permanently denied",
                                Toast.LENGTH_LONG).show();
                    }
                }
            });

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
        toggleCaptureButton.setText(isCapturing ? "Stop Capture" : "Start Capture");
    }

    private boolean hasRequiredPermissions() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRequiredPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted, proceed with capture
            launchMediaProjection();
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
            // User previously denied, show rationale before requesting
            Toast.makeText(this, "Notification permission is required to show capture status", Toast.LENGTH_LONG).show();
        } else {
            // First time request or permission permanently denied
            // In both cases, launch the permission request
            // If permanently denied, Android will not show the dialog and callback will receive false
        }
        mPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
    }

    /**
     * Launch the MediaProjection screen capture intent.
     */
    private void launchMediaProjection() {
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        projectionLauncher.launch(captureIntent);
    }
}
