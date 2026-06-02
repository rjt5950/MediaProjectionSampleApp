package com.samsung.android.mediaprojectionsampleapp;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MediaProjectionSample";
    private static final int VIRTUAL_DISPLAY_FLAG = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private int imagesProduced = 0;
    private String storageDirectory;
    private Button toggleCaptureButton;
    private Display mDisplay;
    private int mScreenDensity;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mRotation;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private ImageReader mImageReader;
    private OrientationChangeCallback mOrientationChangeCallback;
    private MediaProjection mMediaProjection;
    private MediaProjectionManager mProjectionManager;
    private VirtualDisplay mVirtualDisplay;

    // ActivityResultLauncher for notification permission (Android 13+)
    private final ActivityResultLauncher<String> mPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            Log.d(TAG, "Notification permission granted");
                            launchMediaProjection();
                        } else {
                            Log.w(TAG, "Notification permission denied - cannot show foreground service notification");
                            Toast.makeText(this, "Notification permission required for screen capture",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    // ActivityResultLauncher for MediaProjection permission
    private final ActivityResultLauncher<Intent> mMediaProjectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK) {
                            Log.d(TAG, "User denied screen sharing permission");
                            return;
                        }

                        Log.d(TAG, "User granted screen sharing permission");
                        mMediaProjection = mProjectionManager.getMediaProjection(
                                result.getResultCode(), result.getData());
                        updateToggleButton();
                        Toast.makeText(getApplicationContext(),
                                "User granted MediaProjection to capture screens", Toast.LENGTH_SHORT).show();

                        if (mMediaProjection != null) {
                            setupStorageDirectory();

                            // Register media projection stop callback
                            MediaProjectionStopCallback mMediaProjectionCallback = new MediaProjectionStopCallback();
                            mMediaProjection.registerCallback(mMediaProjectionCallback, null);

                            // Register orientation change callback
                            mOrientationChangeCallback = new OrientationChangeCallback(MainActivity.this);
                            if (mOrientationChangeCallback.canDetectOrientation()) {
                                mOrientationChangeCallback.enable();
                            }

                            // Create virtual display depending on device width & height
                            createVirtualDisplay();
                        } else {
                            Log.e(TAG, "MediaProjection is null.");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize display metrics
        initDisplayMetrics();

        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // Initialize handler thread for background image processing
        mHandlerThread = new HandlerThread("ImageProcessingThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        toggleCaptureButton = findViewById(R.id.toggleCaptureButton);
        toggleCaptureButton.setOnClickListener(v -> {
            if (mMediaProjection == null) {
                // Start capture
                Log.d(TAG, "Capture session is starting");
                if (hasRequiredPermissions()) {
                    launchMediaProjection();
                } else {
                    requestRequiredPermissions();
                }
            } else {
                // Stop capture
                Log.d(TAG, "Capture session is stopping");
                Intent intent = new Intent(MainActivity.this, MediaProjectionService.class);
                intent.setAction(MediaProjectionService.ACTION_STOP_FOREGROUND_SERVICE);
                startService(intent);
                Log.d(TAG, "Foreground service stopped.");
                stopProjection();

                Toast.makeText(getApplicationContext(),
                        "Checkout screenshots in " + getRelativeStoragePath() + "directory.",
                        Toast.LENGTH_LONG).show();
            }
        });

        updateToggleButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateToggleButton();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateToggleButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up handler thread
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
        }
        // Release virtual display
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        // Release image reader
        if (mImageReader != null) {
            mImageReader.setOnImageAvailableListener(null, null);
        }
    }

    private void initDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        mDisplay = getWindowManager().getDefaultDisplay();
        mDisplay.getRealMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mDisplayHeight = metrics.heightPixels;
        mDisplayWidth = metrics.widthPixels;
    }

    private void setupStorageDirectory() {
        File externalFilesDir = getExternalFilesDir(null);
        if (externalFilesDir != null) {
            storageDirectory = externalFilesDir.getAbsolutePath() + "/screenshots/";
            File storageDir = new File(storageDirectory);
            if (!storageDir.exists()) {
                boolean success = storageDir.mkdirs();
                if (!success) {
                    Log.e(TAG, "Failed to create file storage directory.");
                }
            }
        } else {
            Log.e(TAG, "Failed to create file storage directory, getExternalFilesDir is null.");
        }
    }

    private String getRelativeStoragePath() {
        if (storageDirectory != null) {
            return storageDirectory.replace("/storage/emulated/0", "") + "directory.";
        }
        return "screenshots directory.";
    }

    private void stopProjection() {
        mHandler.post(() -> {
            if (mMediaProjection != null) {
                mMediaProjection.stop();
            }
        });
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.cancelAll();
    }

    private void createVirtualDisplay() {
        // Re-initialize display metrics to get current values
        initDisplayMetrics();

        Log.d(TAG, "createVirtualDisplay mDisplayWidth: " + mDisplayWidth
                + " mDisplayHeight: " + mDisplayHeight + " mScreenDensity: " + mScreenDensity);

        // Start capture reader
        mImageReader = ImageReader.newInstance(mDisplayWidth, mDisplayHeight,
                PixelFormat.RGBA_8888, 2);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                mDisplayWidth, mDisplayHeight, mScreenDensity, VIRTUAL_DISPLAY_FLAG,
                mImageReader.getSurface(), null, mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
    }

    /**
     * Update the toggle button text and state based on capture status.
     */
    private void updateToggleButton() {
        if (toggleCaptureButton != null) {
            if (mMediaProjection == null) {
                toggleCaptureButton.setText("Start Capture");
                toggleCaptureButton.setEnabled(true);
            } else {
                toggleCaptureButton.setText("Stop Capture");
                toggleCaptureButton.setEnabled(true);
            }
        }
    }

    /**
     * Check if all required permissions are granted.
     * For Android 13+ (API 33+), POST_NOTIFICATIONS permission is required for foreground service.
     */
    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Request required permissions.
     * For Android 13+ (API 33+), requests POST_NOTIFICATIONS permission.
     */
    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)) {
                Toast.makeText(this, "Notification permission is required to show capture status",
                        Toast.LENGTH_LONG).show();
            }
            mPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /**
     * Launch the MediaProjection screen capture intent.
     */
    private void launchMediaProjection() {
        Log.d(TAG, "Launching MediaProjection screen capture");
        Intent intent = new Intent(MainActivity.this, MediaProjectionService.class);
        intent.setAction(MediaProjectionService.ACTION_START_FOREGROUND_SERVICE);
        startService(intent);
        Log.d(TAG, "Foreground service started");
        // Launch screen capture intent using ActivityResultLauncher
        mMediaProjectionLauncher.launch(mProjectionManager.createScreenCaptureIntent());
    }

    /**
     * Inner class for handling image capture.
     * Note: Using non-static class to access outer class members (storageDirectory, imagesProduced, etc.)
     * For better memory management in production, consider using WeakReference or passing data via constructor.
     */
    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "Image available");
            Image image = null;
            FileOutputStream fos = null;
            Bitmap bitmap = null;

            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    if (planes.length == 0) return;

                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - (pixelStride * mDisplayWidth);

                    // Create bitmap with correct dimensions
                    bitmap = Bitmap.createBitmap(mDisplayWidth + rowPadding / pixelStride,
                            mDisplayHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    // Write bitmap to file
                    if (storageDirectory != null) {
                        fos = new FileOutputStream(storageDirectory + "mediaProjectionScreen_"
                                + imagesProduced + ".png");
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        imagesProduced++;
                        Log.d(TAG, "Screenshot saved: " + (imagesProduced - 1));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing image: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
                if (bitmap != null) {
                    bitmap.recycle();
                }
                if (image != null) {
                    image.close();
                }
            }
        }
    }

    /**
     * Static inner class to prevent memory leaks.
     */
    private class OrientationChangeCallback extends OrientationEventListener {
        public OrientationChangeCallback(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            final int rotation = mDisplay.getRotation();
            if (rotation != mRotation) {
                mRotation = rotation;
                try {
                    // Clean up
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null)
                        mImageReader.setOnImageAvailableListener(null, null);

                    // Re-create virtual display depending on device width / height
                    createVirtualDisplay();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Static inner class to prevent memory leaks.
     */
    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.i(TAG, "Stopping projection " + mMediaProjection);
            mHandler.post(() -> {
                if (mVirtualDisplay != null)
                    mVirtualDisplay.release();
                if (mImageReader != null)
                    mImageReader.setOnImageAvailableListener(null, null);
                if (mOrientationChangeCallback != null)
                    mOrientationChangeCallback.disable();
            });
            mMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
            if (mMediaProjection != null) {
                mMediaProjection = null;
                updateToggleButton();
            }
        }
    }
}
