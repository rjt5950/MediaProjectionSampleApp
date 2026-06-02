package com.samsung.android.mediaprojectionsampleapp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MediaProjectionService extends Service {

    public static final String ACTION_START = "ACTION_START_CAPTURE";
    public static final String ACTION_STOP = "ACTION_STOP_CAPTURE";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String TAG = "MediaProjectionService";
    private static final int NOTIFICATION_ID = 1001;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private String storageDirectory;

    @Override
    public void onCreate() {
        super.onCreate();

        projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            startCapture(intent);
        } else if (ACTION_STOP.equals(action)) {
            stopCapture();
        }
        return START_NOT_STICKY;
    }

    private void stopCapture() {
        cleanup();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void cleanup() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.unregisterCallback(callback);
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void startCapture(Intent intent) {
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        startForeground(NOTIFICATION_ID, createNotification());

        if (data != null) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection != null) {
                setupStorageDirectory();
                mediaProjection.registerCallback(callback, new Handler(Looper.getMainLooper()));
                createVirtualDisplay();
                Log.d(TAG, "Capture started");
            } else {
                Log.d(TAG, "MediaProjection is null");
            }
        }
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

    private Notification createNotification() {
        String channelId = "screen_capture";
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        NotificationChannel notificationChannel = new NotificationChannel(channelId, "Screen " +
                "Capture", NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(notificationChannel);
        return new NotificationCompat
                .Builder(this, channelId)
                .setContentTitle("Screen Capture")
                .setContentText("Capturing screen")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
    }

    private void createVirtualDisplay() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCaptureSampleApp", width,
                height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        imageReader.setOnImageAvailableListener(reader -> {
            FileOutputStream fos = null;
            Bitmap bitmap = null;
            Image image = reader.acquireLatestImage();
            if (image == null)
                return;
            try {
                Log.d(TAG, "Screenshot available");

                Image.Plane[] planes = image.getPlanes();
                if (planes.length == 0) return;

                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - (pixelStride * width);

                // Create bitmap with correct dimensions
                bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height,
                        Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);

                // Write bitmap to file
                if (storageDirectory != null) {
                    String capturedImageName = "mediaProjectionScreen_" + getCurrentTime() + ".png";
                    fos = new FileOutputStream(storageDirectory + capturedImageName);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
//                    Log.d(TAG, "Screenshot " + capturedImageName + " saved.");
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
                image.close();
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private String getCurrentTime() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
        return LocalDateTime.now().format(dateTimeFormatter);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final MediaProjection.Callback callback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.d(TAG, "MediaProjection stopped");
            cleanup();

            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    };
}
