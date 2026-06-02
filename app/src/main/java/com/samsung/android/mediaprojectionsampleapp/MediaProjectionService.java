package com.samsung.android.mediaprojectionsampleapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

public class MediaProjectionService extends Service {

    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    private static final String TAG = "MediaProjectionService";
    private static final String CHANNEL_ID = "MediaProjectionChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_LOW;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called with intent: " + (intent != null ? "not null" : "null"));

        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_START_FOREGROUND_SERVICE:
                        Log.d(TAG, "ACTION_START_FOREGROUND_SERVICE");
                        startForegroundService();
                        break;
                    case ACTION_STOP_FOREGROUND_SERVICE:
                        Log.d(TAG, "ACTION_STOP_FOREGROUND_SERVICE");
                        stopForegroundService();
                        break;
                }
            }
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundService() {
        Log.d(TAG, "Start foreground service called");
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Capture Service")
                .setContentText("Capturing screen content")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    private void stopForegroundService() {
        Log.d(TAG, "Stop foreground service");

        // Stop foreground service and remove the notification
        stopForeground(STOP_FOREGROUND_REMOVE);

        // Stop the foreground service
        stopSelf();
    }

    private void createNotificationChannel() {
        Log.d(TAG, "Create notification channel");
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "MediaProjection Service Channel",
                CHANNEL_IMPORTANCE
        );
        serviceChannel.setDescription("Channel for MediaProjection foreground service");

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }
}
