# ARPaint 2.0 :paintbrush:

An augmented reality Android application using [Google ARCore](https://developers.google.com/ar) and [Jetpack Compose](https://developer.android.com/jetpack/compose) that enables painting in real 3D space on a live camera feed.

Revamped and modernized with **Jetpack Compose**, **Material 3 GUI**, and **hardware-accelerated MP4 Video Recording**.

---

## What's New in 2.0 :rocket:

- **Video Recording & Gallery Export** :movie_camera:
  - Added a dedicated Record button in the UI.
  - Live recording indicator with pulsing animation and elapsed time badge.
  - Automatically captures the combined AR scene (camera feed + 3D drawn strokes) at full device resolution using hardware H.264/AVC encoding.
  - Exports directly to Android's `Movies/ARPaint/` media store for immediate viewing in Gallery and Google Photos.
  - Post-recording popup with quick "Share" and "Play" buttons.

- **Jetpack Compose & Material 3 GUI** :sparkles:
  - Fully migrated overlay UI to Jetpack Compose with Material 3 styling.
  - Modern bottom floating action dock with glassmorphism surface.
  - AR Tracking Status pill (Active / Detecting surfaces / Paused) with live pulsing indicator.

- **Modern Popups with Close Buttons** :white_check_mark:
  - **Color Picker Popup**: Beautiful Material 3 dialog with 16 preset color chips, RGB sliders, hex code readout, live swatch preview, and an explicit **Close button**.
  - **Line Thickness Popup**: Real-time live stroke canvas preview, quick preset chips (Fine, Medium, Thick, Marker), smooth slider, and an explicit **Close button**.
  - **Clear Canvas Confirmation Dialog**: Safe confirmation prompt with Cancel (Close) and Clear All options.
  - **Video Saved Dialog**: Preview card with direct Share, Play, and Close actions.

- **Major Stability & Performance Fixes** :shield:
  - **Fixed `BadTokenException`**: Replaced legacy detached context popups with native Compose dialogs.
  - **Fixed `IndexOutOfBoundsException` (-1)**: Added bounds checking when starting strokes and undoing moves.
  - **Fixed `ConcurrentModificationException`**: Synchronized stroke data structures between UI touch inputs and OpenGL rendering threads.
  - **Fixed Direct Buffer Memory Leak**: Reused vertex direct buffers in `LineShaderRenderer` instead of allocating new native buffers every frame.
  - **Fixed In-Place Touch Mutation**: Corrected `LineUtils.screenPointToRay` to prevent mutating incoming coordinate vectors.
  - **Safe ARCore Lifecycle**: Graceful handling of ARCore installation prompts, missing camera permissions, and device compatibility checks.

---

## Core Features :fire:

- Draw in real-time 3D space anchored to the physical world :camera:
- Change line thickness dynamically with live preview :straight_ruler:
- Custom color palette with presets & RGB tuning :art:
- Record AR videos and save them to your device :video_camera:
- Undo strokes and clear canvas :arrow_backward:
- Full-screen immersive AR with Material 3 controls :iphone:

---

## Tech Stack & Dependencies :gear:

- **Language:** Kotlin & Java
- **UI Framework:** Jetpack Compose + Material 3
- **AR Engine:** Google ARCore 1.41.0
- **Rendering:** OpenGL ES 2.0 (`GLSurfaceView`)
- **Video Encoding:** `MediaRecorder` + EGL Window Surface (H.264 / MP4)
- **Minimum SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **Build System:** Gradle 8.4 with Android Gradle Plugin 8.2.2
