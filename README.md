# Cymatic

A minimal Android music player and a music-focused launcher, built from shared code.

| Module | Purpose |
| --- | --- |
| `app-player` | Standalone music player (`com.pxr.cymatic`) |
| `app-launcher` | Home-screen launcher (`com.pxr.cymatic.launcher`) |
| `shared/music` | Playback, library, playlists, EQ, and music screens |
| `shared/ui` | Themes, fonts, icons, components, and animations |

Both apps can be installed together. Each keeps its own settings and music database.

## Run

Use JDK 21 and Android SDK 36.1. Open the repository in Android Studio, sync Gradle,
then select **Cymatic Player** or **Cymatic Launcher** in the Run dropdown.

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
