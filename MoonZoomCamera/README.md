# MoonZoom Camera

A CameraX-based Android camera app scaffold: Android 15+ (API 35) only, tuned for
Samsung Galaxy S21-and-up back cameras, with a UI that mixes Samsung's zoom pill row,
Pixel's minimal top bar, and iOS's mode-carousel/shutter-ring bottom bar.

## Opening the project
1. Install Android Studio (Ladybug or newer).
2. `File > Open` and select the `MoonZoomCamera` folder.
3. Let Gradle sync — it will pull CameraX 1.4.0, AndroidX, and Material.
4. Run on a physical device (camera zoom behavior can't be meaningfully tested on an
   emulator). Requires a device running Android 15, since `minSdk = 35`.

## How zoom works
Rather than manually enumerating and switching between the ultra-wide / wide / tele
physical camera IDs, `CameraXManager` binds a single **logical** back camera
(`CameraSelector.DEFAULT_BACK_CAMERA`) and drives everything through
`Camera.cameraControl.setZoomRatio()`.

On S21-and-up (and most flagships since ~2020), the back camera is exposed to
Camera2/CameraX as one `LOGICAL_MULTI_CAMERA`. The HAL automatically switches between
the ultra-wide, wide, and periscope/tele sensors as the requested ratio crosses their
thresholds — that's the same mechanism Samsung's own camera app uses for "seamless"
zoom. It's simpler and more reliable than hardcoding physical camera IDs, which differ
per OEM/model and aren't guaranteed to be exposed via CameraX at all.

Every tap on a zoom chip (`UW`, `1x`, `2x`, `3x`, `5x`, `10x`, `30x`, `50x`, `100x`) is
clamped at runtime to that device's actual `[minZoomRatio, maxZoomRatio]`
(`CameraInfo.getZoomState()`). On a phone whose hardware tops out at, say, 15x, tapping
"30x" lands you at 15x — same "show ambition, clamp in practice" behavior Samsung's own
UI has across its lineup, since S21 base, S21+, and S21 Ultra all have different max
zoom ceilings.

Pinch-to-zoom is also wired up on the preview surface via `ScaleGestureDetector`.

## About "Moon Zoom" — please read this before shipping anything with that name
Samsung's actual Space Zoom moon shot (moon-scene detection + AI-driven multi-frame
super-resolution merge) is **not exposed by any public Android or Samsung SDK**. It
lives inside Samsung's closed-source camera HAL / Scene Optimizer. There's no
documented API to call into it from a third-party app, on S21 or any other model.

What's included here as a stand-in ("Moon Mode" toggle in the top bar):
- Jumps to the device's actual max zoom ratio.
- Leaves a clear `TODO` hook in `MainActivity.takePhoto()` for a real post-processing
  pipeline.

If you want to get closer to the real effect yourself, the realistic path is:
1. Bundle a small on-device object detector (TensorFlow Lite / ML Kit custom model)
   trained to recognize a bright circular object against a dark sky.
2. When detected at high zoom, capture a short burst instead of a single frame.
3. Run a multi-frame alignment + super-resolution merge (e.g. a simple burst-averaging
   or a lightweight SR model) before writing the final JPEG.

That's a legitimate, buildable computational-photography project — just a separate one
from "hook into Samsung's existing feature," which isn't possible.

## Getting a built APK without Android Studio (GitHub Actions)
This project includes `.github/workflows/build-apk.yml`, which builds a debug APK
automatically:

1. Push this project to a GitHub repo (public or private).
2. GitHub Actions will run on every push to `main` and on pull requests. You can also
   trigger it manually from the repo's **Actions** tab (workflow_dispatch).
3. Once the run finishes (green check), open the run, scroll to **Artifacts**, and
   download `moonzoom-camera-debug-apk`. Unzip it — that's `app-debug.apk`.
4. Copy the APK to your S21 (or any Android 15 device) and tap it to install (you'll
   need to allow "install unknown apps" for whichever app you used to transfer it).

This is a **debug** build (signed with the default debug key) — fine for testing on
your own device, but not for distribution. For a release build you'd need to set up
your own signing key and add a release job to the workflow.

## Building locally instead
```bash
cd MoonZoomCamera
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`. Requires JDK 17 and the Android
SDK (Android Studio installs both, or install the SDK command-line tools yourself and
point `ANDROID_HOME` at it).

```
app/src/main/java/com/moonzoom/camera/
  MainActivity.kt      - permissions, UI wiring, zoom chips, pinch-to-zoom, capture
  CameraXManager.kt     - CameraX binding + zoom ratio control
  ZoomPreset.kt          - the 0.6x/1x/2x/3x/5x/10x/30x/50x/100x preset list
app/src/main/res/
  layout/activity_main.xml   - Samsung zoom row + Pixel top bar + iOS bottom bar
  drawable/                   - pill chip bg, shutter ring, icons
```

## Known gaps to fill in before this is production-ready
- No settings screen (aspect ratio, timer, grid, RAW capture, etc.)
- No video-recording use case wired up yet (VideoCapture dependency is included, mode
  carousel has a Video label, but tapping it doesn't switch use cases yet).
- No launcher icon assets — add your own `mipmap-*/ic_launcher.png` (or an adaptive
  icon) and re-add `android:icon` in the manifest.
- Front camera currently reuses the same zoom-chip UI, which doesn't make sense for a
  single fixed-focal front sensor — you'll likely want to hide the zoom row when
  `toggleLensFacing()` switches to front.
- Moon Mode is a placeholder, as described above.
