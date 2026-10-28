# Anland V5 Agent Guide

Anland V5 is a high-performance Wayland display bridge for Android. A Linux compositor (producer: Niri, KDE, Gamescope) renders into shared DMA-BUFs brokered by a root daemon (`display_daemon`) and scanned out by an Android consumer app (`com.anland.consumer`).

## Environment Setup

- Java: JDK 17 (`JAVA_HOME` pointing to JDK root)
- Android SDK & NDK:
  - `ANDROID_HOME` set
  - NDK: `29.0.13113456` (`export NDK_DIR="$ANDROID_HOME/ndk/29.0.13113456"`)
  - Build-tools: `34.0.0`
- Host build tools: CMake 3.18+, zip, bash

## Build Commands

- Android Consumer APK:
  `cd consumers/anland_v5/android_consumer && ./gradlew assembleRelease`
  Artifact: `consumers/anland_v5/android_consumer/app/build/outputs/apk/release/app-release.apk`
- Display Daemon (Android arm64):
  `bash build_daemon_android.sh`
  Artifact: `build_daemon_android/display_daemon`
- Magisk Module (Daemon ZIP):
  `bash build_magisk_module.sh`
  Artifact: `anland-daemon.zip`
- Host/Linux Daemon & Libraries:
  `cmake -S . -B build && cmake --build build`

## Code & Architecture Conventions

- Subsystem Layout:
  - `daemon/`, `libdisplay_daemon/`: Broker daemon (`display_daemon`) passing FDs via `SCM_RIGHTS`
  - `consumers/anland_v5/android_consumer/`: Android client (SurfaceView, input bridge, M3 settings, HUD)
  - `consumers/anland_v5/android_consumer/app/src/main/jni/`: Native rendering (`libanland_consumer.so`) and root helpers (`libfdhelper.so`, `libinputgrab.so`)
  - `producers/`: Compositor backends (`niri/anland_backend/anland_niri_bridge.c`, `kde/`, `gamescope/`)
  - `common/`: Socket framing (`socket_utils.c`) and wire protocol (`protocol.h`)
  - `magisk_module/`: Root packaging (`module.prop`, `service.sh`, `sepolicy.rule`)
- Commits:
  - Conventional Commits: `<type>(<scope>): <summary>` (e.g. `fix(hud): ...`, `feat(deck): ...`, `fix(core): ...`)
- Input & Scancodes:
  - Mouse: Linux evdev codes (`0x110` BTN_LEFT, `0x111` BTN_RIGHT) with 40-50ms minimum tap hold
  - Gamepad: Type 16 (keys) + Type 17 (ABS axes). Dual D-Pad dispatch: Hat0 (`ABS_HAT0X`/`ABS_HAT0Y`) and `BTN_DPAD_*`. Triggers on `ABS_Z`/`ABS_RZ`

## Pitfalls & Gotchas

- Legacy Packaging: `useLegacyPackaging = true` in `app/build.gradle` is mandatory so `libfdhelper.so` and `libinputgrab.so` extract as executable binaries in `nativeLibraryDir` for `su -c`.
- Droidspaces Sockets: Sockets reside at `/data/local/Droidspaces/Net/anland-*.sock`. The app auto-detects and persists the active socket path in SharedPreferences; never hardcode `/data/local/tmp/display_daemon.sock` unconditionally.
- Navigation & Black Screen: Settings sub-activities must terminate via `finish()`. Spawning a new `MainActivity` intent creates duplicate tasks without active sockets, leaving a black screen.
- Handheld Viewport: In Steam Deck mode, anchor viewport height to 100% (`surfaceH = availH`, `surfaceOffsetY = 0f`). Never reduce height for aspect ratio constraints to avoid letterboxing.
- Reconnect Storms: Route debounced surface reconnects through a `Handler(Looper.getMainLooper())` rather than `View.postDelayed()` to avoid dropped runs during window transitions.
