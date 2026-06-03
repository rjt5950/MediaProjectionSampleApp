package com.samsung.android.mediaprojectionsampleapp;

public class Constants {
    public static final String ACTION_START = "ACTION_START_CAPTURE";
    public static final String ACTION_STOP = "ACTION_STOP_CAPTURE";
    public static final String ACTION_STOPPED = "com.samsung.android.mediaprojectionsampleapp.ACTION_STOPPED";
    
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    
    public static final String NOTIFICATION_CHANNEL_ID = "screen_capture";
    public static final int NOTIFICATION_ID = 1001;
    
    public static final String FOLDER_NAME = "MediaProjectionSampleApp";
    public static final String SCREENSHOT_PREFIX = "SC-";
    public static final String SCREENSHOT_EXTENSION = ".png";
    public static final String DATE_FORMAT = "yyyyMMdd-HHmmss";
    
    public static final String VIRTUAL_DISPLAY_NAME = "ScreenCaptureSampleApp";
    public static final String HANDLER_THREAD_NAME = "ScreenCaptureThread";
}
