package com.samsung.android.mediaprojectionsampleapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private Button toggleCaptureButton;
    private MediaProjectionManager projectionManager;
    private boolean isCapturing = false;
    private ActivityResultLauncher<Intent> projectionLauncher;
    private PermissionManager permissionManager;

    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "stopReceiver onReceive: " + intent.getAction());
            if (Constants.ACTION_STOPPED.equals(intent.getAction())) {
                isCapturing = false;
                updateToggleButton();
            }
        }
    };

    // ActivityResultLauncher for notification permission (Android 13+)
    private final ActivityResultLauncher<String> mPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                permissionManager.handleNotificationPermissionResult(isGranted, this::launchMediaProjection);
            });

    @Override
    protected void onStart() {
        super.onStart();
        isCapturing = MediaProjectionService.isRunning();
        updateToggleButton();
        IntentFilter filter = new IntentFilter(Constants.ACTION_STOPPED);
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

        permissionManager = new PermissionManager(this, mPermissionLauncher);
        toggleCaptureButton = findViewById(R.id.toggleCaptureButton);

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        projectionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK) {
                Log.d(TAG, "User denied screen sharing permission");
                return;
            }

            Log.d(TAG, "User granted screen sharing permission");

            Intent serviceIntent = new Intent(MainActivity.this, MediaProjectionService.class);
            serviceIntent.setAction(Constants.ACTION_START);
            serviceIntent.putExtra(Constants.EXTRA_RESULT_CODE, result.getResultCode());
            serviceIntent.putExtra(Constants.EXTRA_RESULT_DATA, result.getData());
            startForegroundService(serviceIntent);
            Log.d(TAG, "Foreground service started");

            isCapturing = true;
            updateToggleButton();
        });

        toggleCaptureButton.setOnClickListener(v -> {
            if (!isCapturing) {
                permissionManager.requestRequiredPermissions(this::launchMediaProjection);
            } else {
                Intent stopIntent = new Intent(MainActivity.this, MediaProjectionService.class);
                stopIntent.setAction(Constants.ACTION_STOP);
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
        toggleCaptureButton.setText(isCapturing ? R.string.stop_capture : R.string.start_capture);
    }

    /**
     * Launch the MediaProjection screen capture intent.
     */
    private void launchMediaProjection() {
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        projectionLauncher.launch(captureIntent);
    }
}
