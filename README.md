# MediaProjectionSampleApp

A sample Android application demonstrating how to capture screen screenshots using the `MediaProjection` API and save them to a custom directory at the root of internal storage.

## Features

*   **Screen Capture**: High-quality screen capture using `MediaProjection`.
*   **Foreground Service**: Robust capture handling via a Foreground Service with a persistent notification.
*   **Custom Storage**: Saves screenshots to a custom folder at the root of internal storage: `/sdcard/MediaProjectionSampleApp/`.
*   **Gallery Integration**: Uses `MediaScannerConnection` to batch-index screenshots so they appear in the system Gallery after the capture session ends.
*   **Modern Android Support**: Targets Android 15 (API 36) and handles scoped storage and notification permissions.
*   **UI Synchronization**: Uses a `BroadcastReceiver` to ensure the activity UI stays in sync with the service state, even when capture is stopped from the system status bar.

## Requirements

*   **Android Version**: Android 14+ (Target SDK 36).
*   **Permissions**:
    *   `POST_NOTIFICATIONS`: To show the foreground service notification.
    *   `FOREGROUND_SERVICE_MEDIA_PROJECTION`: Required for media projection services.
    *   `MANAGE_EXTERNAL_STORAGE`: Required to create and write to a custom folder at the root of internal storage.

## Setup and Usage

1.  **Notification Permission**: On launch, the app will ask for permission to show notifications. This is required to maintain the foreground capture session.
2.  **All Files Access**: To save files to the root directory, you must manually grant "All Files Access". The app will provide a dialog to redirect you to **Settings > Special app access > All files access**.
3.  **Start Capture**: Press the **Start Capture** button. Grant the system's "Screen Casting" permission when prompted.
4.  **Stop Capture**: Press the **Stop Capture** button or use the system's "Stop" button in the status bar/notification shade.
5.  **View Results**: Once the session stops, the screenshots will be indexed and visible in your Gallery under the "MediaProjectionSampleApp" album.

## Technical Details

*   **Filename Format**: `SCyyyyMMdd-HHmmss.png`
*   **Image Format**: PNG (Lossless)
*   **Communication**: The service sends an `ACTION_STOPPED` broadcast to the activity when the projection ends to update the button text.

## Project Structure

*   `MainActivity.java`: Manages permission flow, UI state, and start/stop triggers.
*   `MediaProjectionService.java`: Core logic for `MediaProjection`, `VirtualDisplay`, image processing (`ImageReader`), file I/O, and `MediaScanner` indexing.
