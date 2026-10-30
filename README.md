# Ambient Cốc Cốc v0.2 (prototype source)

Android Studio project for an experimental ambient-color border overlay, targeting Android 10+ (HONOR/MagicOS included).

## Build an APK automatically (recommended)
1. Create a GitHub repository and upload the contents of this folder.
2. Open the repository's **Actions** tab.
3. Select **Build Android APK** and press **Run workflow** (or push to `main`/`master`).
4. When the run finishes, open it and download the artifact named `AmbientCocCoc-debug-apk`.
5. Extract `app-debug.apk` and install it on the phone. Android may ask to allow installation from that browser/file manager.

The included workflow builds with Java 17, Gradle 8.9 and Android Gradle Plugin 8.7.3. The cloud runner downloads the Android SDK components it needs.

## Build locally
Open this folder in a recent Android Studio, allow Gradle sync, then use **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The debug APK is normally at `app/build/outputs/apk/debug/app-debug.apk`.

## First use
1. Open Ambient Cốc Cốc.
2. Choose the approximate video aspect ratio (usually 16:9).
3. Tap **BẬT AMBIENT MODE** and approve Android's screen-capture prompt.
4. If requested, allow display over other apps, return to Ambient Cốc Cốc, and tap enable again.
5. Switch to Cốc Cốc and play the video. Stop using the persistent notification's **Tắt hiệu ứng** action or the app.

## Important limitations
- This is an experimental prototype, not an officially tested HONOR/Cốc Cốc integration. The overlay assumes a centered video and selected aspect ratio; browser controls, status/navigation bars, rotation, split screen and non-standard layouts can make the glow region inaccurate.
- The effect samples the screen via Android MediaProjection and needs explicit user consent each session. Android shows a persistent foreground notification.
- DRM/protected video may appear black in capture. Some apps or system versions may restrict overlays or screen capture.
- The glow is drawn outside the estimated video rectangle; if the rectangle is wrong, it can overlap subtitles or browser controls.
- Test on ordinary, non-sensitive video first. Do not capture private content.
