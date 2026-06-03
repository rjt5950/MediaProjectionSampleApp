# MediaProjectionSampleApp

A sample Android application demonstrating how to capture screen screenshots using the `MediaProjection` API and save them to a custom directory at the root of internal storage.

## Features

*   **High Performance Screen Capture**: Uses a dedicated `HandlerThread` for image processing and **Bitmap Pooling** to minimize memory churn and avoid UI lag.
*   **Foreground Service**: Robust capture handling via a Foreground Service with a persistent notification and a built-in **Stop Action**.
*   **Custom Storage**: Saves screenshots to a custom folder at the root of internal storage: `/sdcard/MediaProjectionSampleApp/`.
*   **Gallery Integration**: Uses `MediaScannerConnection` to batch-index screenshots so they appear in the system Gallery after the capture session ends.
*   **Modern Android Support**: Targets Android 15 (API 36) with full support for Scoped Storage and the latest notification permission models.
*   **Clean Architecture**:
    *   `Constants.java`: Centralized management of actions, keys, and identifiers.
    *   `PermissionManager.java`: Encapsulated logic for multi-step permission flows.
    *   **Broadcast UI Sync**: Real-time synchronization between service state and activity button text.

## Requirements

*   **Android Version**: Android 14+ (Target SDK 36).
*   **Permissions**:
    *   `POST_NOTIFICATIONS`: To show the foreground service notification.
    *   `FOREGROUND_SERVICE_MEDIA_PROJECTION`: Required for media projection services.
    *   `MANAGE_EXTERNAL_STORAGE`: Required to create and write to a custom folder at the root of internal storage.

## Setup and Usage

1.  **Notification Permission**: On launch, the app will ask for permission to show notifications.
2.  **All Files Access**: To save files to the root directory, grant "All Files Access" when prompted by following the redirect to system settings.
3.  **Start Capture**: Press the **Start Capture** button. Grant the system's "Screen Casting" permission.
4.  **Stop Capture**: 
    *   Press the **Stop Capture** button in the app.
    *   **NEW**: Click the **Stop Capture** action directly in the system notification shade.
    *   Stop via the system status bar icon.
5.  **View Results**: Screenshots are saved as `SC-yyyyMMdd-HHmmss.png`. They will be indexed and visible in your Gallery under the "MediaProjectionSampleApp" album after stopping the session.

## Project Structure

*   `MainActivity.java`: UI entry point, manages activity lifecycle and UI state updates.
*   `MediaProjectionService.java`: Core engine. Manages `MediaProjection`, `VirtualDisplay`, and background image processing.
*   `PermissionManager.java`: Handles sequential permission requests and system settings redirection.
*   `Constants.java`: Single source of truth for all shared keys and actions.
