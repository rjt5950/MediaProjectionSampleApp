package com.samsung.android.mediaprojectionsampleapp;

import android.Manifest;
import android.app.ComponentCaller;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MediaProjectionSample";
    private static final int PERMISSION_CODE = 1;
    private static final int VIRTUAL_DISPLAY_FLAG = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private int imagesProduced = 0;
    private String storageDirectory;
    private Button captureButton;
    private Button stopButton;
    private Display mDisplay;
    private int mScreenDensity;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mRotation;
    private Handler mHandler;
    private ImageReader mImageReader;
    private OrientationChangeCallback mOrientationChangeCallback;
    private MediaProjection mMediaProjection;
    private MediaProjectionManager mProjectionManager;
    private VirtualDisplay mVirtualDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DisplayMetrics metrics = new DisplayMetrics();
        mDisplay = getWindowManager().getDefaultDisplay();
        mDisplay.getRealMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mDisplayHeight = metrics.heightPixels;
        mDisplayWidth = metrics.widthPixels;

        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        try {
            if (ActivityCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_MEDIA_LOCATION}, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(v -> {
            Log.d(TAG, "Capture session is starting");
            Intent intent = new Intent(MainActivity.this, MediaProjectionService.class);
            intent.setAction(MediaProjectionService.ACTION_START_FOREGROUND_SERVICE);
            startService(intent);
            Log.d(TAG, "Foreground service started");
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(),
                    PERMISSION_CODE);
        });

        stopButton = findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "Capture session is stopping");
                Intent intent = new Intent(MainActivity.this, MediaProjectionService.class);
                intent.setAction(MediaProjectionService.ACTION_STOP_FOREGROUND_SERVICE);
                startService(intent);
                Log.d(TAG, "Foreground service stopped.");
                stopProjection();

                Toast.makeText(getApplicationContext(), "Checkout screenshots in "
                                + storageDirectory.replace("/storage/emulated/0", "") + "directory.",
                        Toast.LENGTH_LONG).show();
            }
        });

        updateButtons();

        new Thread(() -> {
            Looper.prepare();
            mHandler = new Handler();
            Looper.loop();
        }).start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data, @NonNull ComponentCaller caller) {
        super.onActivityResult(requestCode, resultCode, data, caller);
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Log.d(TAG, "User denied screen sharing permission");
            return;
        } else {
            Log.d(TAG, "User granted screen sharing permission");
        }
        if (requestCode == PERMISSION_CODE) {
            mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
            updateButtons();
            Toast.makeText(getApplicationContext(),
                    "User granted MediaProjection to capture screens", Toast.LENGTH_SHORT).show();

            if (mMediaProjection != null) {
                File externalFilesDir = getExternalFilesDir(null);
                if (externalFilesDir != null) {
                    storageDirectory = externalFilesDir.getAbsolutePath() + "/screenshots/";
                    File storageDir = new File(storageDirectory);
                    if (!storageDir.exists()) {
                        boolean success = storageDir.mkdirs();
                        if (!success) {
                            Log.e(TAG, "failed to create file storage directory.");
                            return;
                        }
                    }
                } else {
                    Log.e(TAG,
                            "failed to create file storage directory, getExternalFilesDir is null.");
                    return;
                }

                //register media projection stop callback
                MediaProjectionStopCallback mMediaProjectionCallback = new MediaProjectionStopCallback();
                mMediaProjection.registerCallback(mMediaProjectionCallback, null);

                // register orientation change callback
                mOrientationChangeCallback = new OrientationChangeCallback(this);
                if (mOrientationChangeCallback.canDetectOrientation()) {
                    mOrientationChangeCallback.enable();
                }

                //create virtual display depending on device width & height
                createVirtualDisplay();
            } else {
                Log.e(TAG, "MediaProjection is null.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtons();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateButtons();
    }

    private void stopProjection() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mMediaProjection != null) {
                    mMediaProjection.stop();
                }
            }
        });
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.cancelAll();
    }

    private void createVirtualDisplay() {
        DisplayMetrics metrics = new DisplayMetrics();
        mDisplay = getWindowManager().getDefaultDisplay();
        mDisplay.getRealMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mDisplayHeight = metrics.heightPixels;
        mDisplayWidth = metrics.widthPixels;

        Log.d(TAG, "createVirtualDisplay mDisplayWidth: " + mDisplayWidth
                + " mDisplayHeight: " + mDisplayHeight + " mScreenDensity: " + mScreenDensity);

        //start capture reader
        mImageReader = ImageReader.newInstance(mDisplayWidth, mDisplayHeight,
                PixelFormat.RGBA_8888, 2);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                mDisplayWidth, mDisplayHeight, mScreenDensity, VIRTUAL_DISPLAY_FLAG,
                mImageReader.getSurface(), null, mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
    }

    private void updateButtons() {
        if (captureButton != null && stopButton != null) {
            if (mMediaProjection == null) {
                captureButton.setEnabled(true);
                captureButton.setVisibility(View.VISIBLE);
                stopButton.setVisibility(View.GONE);
            } else {
                captureButton.setEnabled(false);
                captureButton.setVisibility(View.GONE);
                stopButton.setVisibility(View.VISIBLE);
                stopButton.setEnabled(true);
            }
        }
    }

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "image available");
            Image image = null;
            FileOutputStream fos = null;
            Bitmap bitmap = null;

            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - (pixelStride * mDisplayWidth);

                    //create bitmap
                    bitmap = Bitmap.createBitmap(mDisplayWidth + rowPadding / pixelStride,
                            mDisplayHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    //write bitmap to a file
                    fos = new FileOutputStream(storageDirectory + "mediaProjectionScreen_"
                            + imagesProduced + ".png");
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    imagesProduced++;
                }
            } catch (Exception e) {
                Log.e(TAG, " " + e.getMessage());
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
                    // clean up
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null)
                        mImageReader.setOnImageAvailableListener(null, null);

                    // re-create virtual display depending on device width / height
                    createVirtualDisplay();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.i(TAG, "stopping projection " + mMediaProjection);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mVirtualDisplay != null)
                        mVirtualDisplay.release();
                    if (mImageReader != null)
                        mImageReader.setOnImageAvailableListener(null, null);
                    if (mOrientationChangeCallback != null)
                        mOrientationChangeCallback.disable();
                }
            });
            mMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
            if (mMediaProjection != null) {
                mMediaProjection = null;
                updateButtons();
            }
        }
    }
}
