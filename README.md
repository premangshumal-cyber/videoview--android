# KinePulse — Full-Featured Android Media Player (VideoView Engine)

KinePulse (`com.kinepulse.player`) is a clean-room Android multimedia player written in Kotlin (`minSdk = 26`, `targetSdk = 35`). It reproduces the UX, navigation, gestures, library management, and HUD workflows of a full-featured open-source media player while strictly using the Android framework's `android.widget.VideoView` and `android.media.MediaPlayer`.

## How to Get the Installable `app-debug.apk`
### Option A: One-Click Local Build (Windows / macOS / Linux)
1. Extract this ZIP archive.
2. Double-click `build_apk.bat` (Windows) or run `./build_apk.sh` (macOS/Linux), or open the folder in **Android Studio** and click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
3. Your installable APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

### Option B: Zero-Install Cloud Build (GitHub Actions)
1. Push this extracted folder to any free GitHub repository.
2. Included workflow `.github/workflows/build-apk.yml` automatically runs JDK 17 + Android SDK in the cloud and uploads **`KinePulse-debug-apk` (`app-debug.apk`)** under the **Actions** tab artifacts in ~2 minutes.
