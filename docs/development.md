# Development

## Requirements

- Android SDK (compile/target SDK 36, minSdk 31)
- Android NDK `29.0.14206865`
- CMake `3.22.1`
- JDK 11 or newer

## Project layout

| Module | Purpose |
| --- | --- |
| `app` | Player screens, navigation, click wheel controls, themes, fonts, and UI components (`com.pxr.cymatic`) |
| `shared/music` | Playback, library, playlists, EQ, remote library sync, and in-app license catalog |
| `shared/usb` | Native USB isochronous PCM transport (`usbdevfs`) |
| `shared/peq` | USB DAC hardware PEQ protocols, DSP math, device records, and Android HID client (vendored from Contour) |
| `shared/flac` | Native integer FLAC decoding (`libFLAC`) for Android and direct USB playback |
| `shared/alac` | Native integer ALAC decoding (Apple ALAC) for Android and direct USB playback |
| `shared/dsd` | Native DSF DSD-to-PCM conversion and bit-perfect DoP packing |
| `buildSrc` | Custom Gradle tasks (including the PEQ device catalog generator) |

## Build & test

Build a debug APK:

```sh
./gradlew :app:assembleDebug
```

Build a release APK (minified with R8):

```sh
./gradlew :app:assembleRelease
```

Run unit tests:

```sh
./gradlew testDebugUnitTest
```

App version code and version name are set in `gradle.properties` (`cymaticVersionCode` and `cymaticVersionName`).

## Native & vendored dependencies

- **FLAC:** `shared/flac/third_party/README.md`
- **ALAC:** `shared/alac/third_party/README.md`
- **USB DAC PEQ (Contour):** `shared/peq/README.md`

## Third-party license inventory

In-app license notices live under `shared/music/src/main/assets/licenses/` and are loaded by `LicenseCatalog`. After changing Gradle dependencies, refresh the inventory with `scripts/update_license_inventory.py` (using `gradle/license-inventory.gradle`).
