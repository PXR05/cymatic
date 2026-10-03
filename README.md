# Cymatic

A minimal Android music player and a music-focused launcher, built from shared code.

| Module | Purpose |
| --- | --- |
| `app-player` | Standalone music player (`com.pxr.cymatic`) |
| `app-launcher` | Home-screen launcher (`com.pxr.cymatic.launcher`) |
| `shared/music` | Playback, library, playlists, EQ, and music screens |
| `shared/ui` | Themes, fonts, icons, components, and animations |
| `shared/usb` | Native USB isochronous PCM transport |
| `shared/flac` | Integer FLAC decoding for Android and direct USB playback |
| `shared/alac` | Integer ALAC decoding for Android and direct USB playback |

Both apps can be installed together. Each keeps its own settings and music database.

Direct USB supports mono/stereo PCM WAV, FLAC (including Ogg FLAC), ALAC in M4A/MP4,
MP3, and Ogg Vorbis/Opus. Lossy formats use Android decoders with 16-bit PCM output
at the codec's sample rate. Unsupported tracks or DAC configurations fall back to Android playback.

## Run

Use JDK 21 and Android SDK 36.1. Open the repository in Android Studio, sync Gradle,
then select **Cymatic Player** or **Cymatic Launcher** in the Run dropdown.
The native USB, FLAC and ALAC modules use Android NDK 29.0.14206865 and CMake 3.22.1.
Codec sources are included for offline builds: libFLAC 1.5.0 and Apple's ALAC decoder with local bounds checks.

```bash
./gradlew :app-player:assembleDebug :app-launcher:assembleDebug
```

APKs are in each app's `build/outputs/apk/debug/` folder. Use `assembleRelease` for
unsigned release builds.

## Checks and releases

```bash
./gradlew :shared:music:testDebugUnitTest :app-player:lintDebug :app-launcher:lintDebug
python scripts/verify_app_separation.py debug
```

Shared versions live in `gradle.properties`, dependencies live in
`gradle/libs.versions.toml`. CI builds both apps and signs both release APKs.
