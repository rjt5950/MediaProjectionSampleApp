package com.samsung.android.mediaprojectionsampleapp;

import android.util.Log;

/**
 * Custom Logger to centralize logging logic.
 * Controls log visibility based on build type and provides robust error reporting.
 */
public class Logger {
    private static final String APP_TAG = "MediaProjectionApp";
    
    // Set this based on your production requirements. 
    // In a real app, you'd use BuildConfig.DEBUG.
    private static final boolean IS_DEBUG = BuildConfig.DEBUG;

    public static void d(String tag, String message) {
        if (IS_DEBUG) {
            Log.d(APP_TAG, formatMessage(tag, message));
        }
    }

    public static void i(String tag, String message) {
        Log.i(APP_TAG, formatMessage(tag, message));
    }

    public static void w(String tag, String message) {
        Log.w(APP_TAG, formatMessage(tag, message));
    }

    public static void e(String tag, String message) {
        Log.e(APP_TAG, formatMessage(tag, message));
    }

    public static void e(String tag, String message, Throwable throwable) {
        Log.e(APP_TAG, formatMessage(tag, message), throwable);
    }

    public static void v(String tag, String message) {
        if (IS_DEBUG) {
            Log.v(APP_TAG, formatMessage(tag, message));
        }
    }

    private static String formatMessage(String tag, String message) {
        return "[" + tag + "] " + message;
    }
}
