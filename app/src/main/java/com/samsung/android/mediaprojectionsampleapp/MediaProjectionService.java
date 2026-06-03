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
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MediaProjectionService extends Service {

    public static final String ACTION_START = "ACTION_START_CAPTURE";
    public static final String ACTION_STOP = "ACTION_STOP_CAPTURE";
    public static final String ACTION_STOPPED = "com.samsung.android.mediaprojectionsampleapp.ACTION_STOPPED";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String TAG = "MediaProjectionService";
    private static final int NOTIFICATION_ID = 1001;
    private static boolean isRunning = false;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final List<String> capturedFilePaths = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }

    public static boolean isRunning() {
        return isRunning;
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
        isRunning = false;

        if (!capturedFilePaths.isEmpty()) {
            String[] paths = capturedFilePaths.toArray(new String[0]);
            Log.d(TAG, "Batch Scanning " + paths.length + " files for Gallery...");
            MediaScannerConnection.scanFile(this, paths, null, null);
            capturedFilePaths.clear();
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
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
                isRunning = true;
                mediaProjection.registerCallback(callback, new Handler(Looper.getMainLooper()));
                createVirtualDisplay();
                Log.d(TAG, "Capture started");
            } else {
                Log.d(TAG, "MediaProjection is null");
            }
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
            OutputStream fos = null;
            Bitmap bitmap = null;
            Image image = reader.acquireNextImage();
            if (image == null)
                return;
            try {
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

                // Write bitmap to Root custom folder
                File root = Environment.getExternalStorageDirectory();
                File customDir = new File(root, "MediaProjectionSampleApp");
                if (!customDir.exists()) {
                    if (!customDir.mkdirs()) {
                        Log.e(TAG, "Failed to create directory at root: " + customDir.getAbsolutePath());
                    }
                }

                String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date());
                String capturedImageName = "SC-" + timeStamp + ".png";
                File file = new File(customDir, capturedImageName);
                fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                fos = null;
                
                //Log.d(TAG, "Screenshot saved to root: " + file.getAbsolutePath());
                capturedFilePaths.add(file.getAbsolutePath());
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final MediaProjection.Callback callback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.d(TAG, "MediaProjection stopped");
            isRunning = false;
            cleanup();

            stopForeground(STOP_FOREGROUND_REMOVE);

            // Notify MainActivity that capture has stopped
            Log.d(TAG, "Sending ACTION_STOPPED broadcast");
            Intent intent = new Intent(ACTION_STOPPED);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);

            stopSelf();
        }
    };
}
