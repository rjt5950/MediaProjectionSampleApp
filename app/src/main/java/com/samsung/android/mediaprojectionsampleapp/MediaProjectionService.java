package com.samsung.android.mediaprojectionsampleapp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;

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

    private static final String TAG = "MediaProjectionService";
    private static boolean isRunning = false;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final List<String> capturedFilePaths = new ArrayList<>();

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Bitmap reusableBitmap;

    @Override
    public void onCreate() {
        super.onCreate();

        projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        backgroundThread = new HandlerThread(Constants.HANDLER_THREAD_NAME);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public void onDestroy() {
        Logger.d(TAG, "Service onDestroy");
        cleanup();
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
        }
        super.onDestroy();
    }

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_NOT_STICKY;

        String action = intent.getAction();
        if (Constants.ACTION_START.equals(action)) {
            startCapture(intent);
        } else if (Constants.ACTION_STOP.equals(action)) {
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
        Logger.d(TAG, "Cleaning up resources");
        isRunning = false;

        if (reusableBitmap != null) {
            reusableBitmap.recycle();
            reusableBitmap = null;
        }

        if (!capturedFilePaths.isEmpty()) {
            String[] paths = capturedFilePaths.toArray(new String[0]);
            Logger.d(TAG, "Batch Scanning " + paths.length + " files for Gallery...");
            MediaScannerConnection.scanFile(this, paths, null, null);
            capturedFilePaths.clear();
        }

        stopVirtualDisplay();

        if (mediaProjection != null) {
            mediaProjection.unregisterCallback(callback);
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void stopVirtualDisplay() {
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    private void startCapture(Intent intent) {
        int resultCode = intent.getIntExtra(Constants.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra(Constants.EXTRA_RESULT_DATA);
        startForeground(Constants.NOTIFICATION_ID, createNotification());

        if (data != null) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection != null) {
                isRunning = true;
                mediaProjection.registerCallback(callback, new Handler(Looper.getMainLooper()));
                createVirtualDisplay();
                Logger.d(TAG, "Capture started");
            } else {
                Logger.d(TAG, "MediaProjection is null");
            }
        }
    }

    private Notification createNotification() {
        String channelId = Constants.NOTIFICATION_CHANNEL_ID;
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        NotificationChannel notificationChannel = new NotificationChannel(channelId, 
                getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(notificationChannel);

        Intent stopIntent = new Intent(this, MediaProjectionService.class);
        stopIntent.setAction(Constants.ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat
                .Builder(this, channelId)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_screen_capture)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, 
                        getString(R.string.stop_capture), stopPendingIntent)
                .build();
    }

    private void createVirtualDisplay() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        virtualDisplay = mediaProjection.createVirtualDisplay(Constants.VIRTUAL_DISPLAY_NAME, width,
                height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        imageReader.setOnImageAvailableListener(reader -> {
            OutputStream fos = null;
            Image image = reader.acquireLatestImage();
            if (image == null)
                return;
            try {
                Image.Plane[] planes = image.getPlanes();
                if (planes.length == 0) return;

                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - (pixelStride * width);

                int bitmapWidth = width + rowPadding / pixelStride;

                // Reuse bitmap if possible to reduce GC pressure
                if (reusableBitmap == null || reusableBitmap.getWidth() != bitmapWidth || reusableBitmap.getHeight() != height) {
                    if (reusableBitmap != null) {
                        reusableBitmap.recycle();
                    }
                    reusableBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888);
                }

                reusableBitmap.copyPixelsFromBuffer(buffer);

                // Write bitmap to Root custom folder
                File root = Environment.getExternalStorageDirectory();
                File customDir = new File(root, Constants.FOLDER_NAME);
                if (!customDir.exists()) {
                    if (!customDir.mkdirs()) {
                        Logger.e(TAG, "Failed to create directory at root: " + customDir.getAbsolutePath());
                    }
                }

                String timeStamp = new SimpleDateFormat(Constants.DATE_FORMAT, Locale.getDefault()).format(new Date());
                String capturedImageName = Constants.SCREENSHOT_PREFIX + timeStamp + Constants.SCREENSHOT_EXTENSION;
                File file = new File(customDir, capturedImageName);
                fos = new FileOutputStream(file);
                reusableBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
                fos = null;
                
                capturedFilePaths.add(file.getAbsolutePath());
            } catch (Exception e) {
                Logger.e(TAG, "Error processing image: " + e.getMessage(), e);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ioe) {
                        Logger.e(TAG, "Error closing stream", ioe);
                    }
                }
                image.close();
            }
        }, backgroundHandler);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final MediaProjection.Callback callback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Logger.d(TAG, "MediaProjection stopped");
            isRunning = false;
            cleanup();

            stopForeground(STOP_FOREGROUND_REMOVE);

            // Notify MainActivity that capture has stopped
            Logger.d(TAG, "Sending ACTION_STOPPED broadcast");
            Intent intent = new Intent(Constants.ACTION_STOPPED);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);

            stopSelf();
        }
    };
}
