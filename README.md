# Cymatic

A minimal Android music player with a click wheel interface, pixel typography,
and direct USB DAC playback.

## Screenshots

| Now Playing | Main menu |
| --- | --- |
| <img src="docs/screenshots/player-now-playing.jpg" alt="Now Playing, album art, FLAC format, direct USB output, and the click wheel" width="280"> | <img src="docs/screenshots/player-main-menu.jpg" alt="Main menu with artists, albums, playlists, queue, settings, and the click wheel" width="280"> |

| Track Queue | Settings |
| --- | --- |
| <img src="docs/screenshots/player-album-tracks.jpg" alt="Track queue with artist names, track durations, and the playback bar" width="280"> | <img src="docs/screenshots/player-interface-settings.jpg" alt="Settings page" width="280"> |

## Features

- **Click wheel player:** rotate to browse lists or adjust playback volume; use the
  center button to select, Menu to return home, and the outer buttons for playback.
- **Music library:** browse tracks, artists, albums, playlists, and the current queue.
- **Pixel interface:** monochrome light and dark themes, pixel icons, and scrolling
  track names with ellipses at clipped edges.
- **Interface controls:** adjust wheel size and sensitivity, screen padding, haptics,
  text scrolling, cover-art visibility, and screen-awake behavior.
- **Standby:** an optional idle timeout blanks the player display. Touch wakes it,
  and the same touch can continue into a wheel gesture.
- **Audio profiles:** remember volume per output device, with separate normal and
  direct USB volume levels, plus equalizer presets per device.
- **Remote library:** connect to AudioStream to browse songs and playlists, with optional
  offline downloads and automatic storage management.
- **DSD/DSF:** play mono/stereo DSF files through PCM conversion or bit-perfect
  DSD over PCM (DoP) on a compatible USB DAC.

## Install

Requires **Android 12 or newer**.

Download `cymatic-player-v<version>.apk` from
[GitHub Releases](https://github.com/PXR05/cymatic/releases/latest) and install it on your device.

Grant music access, then use **Settings → Storage** to scan your music. You can scan
all media or choose individual directories. **Settings → Interface**
contains the wheel, display-padding, scrolling, and standby controls.

Use **Settings → Remote library** to connect an AudioStream account. Songs and playlists
appear in the normal library immediately after connecting. Tap a remote song to stream
it; playback prefers a downloaded copy when one is available. Local-storage permission
is only needed for music stored outside Cymatic.

Long press a song, playlist, album, or artist and choose **Keep offline** to download it.
You can queue more items while downloads are running. A small animated pixel icon marks
queued or processing items in place. The download icon marks offline items, a half-filled
circle marks partially offline items, and the cloud marks stream-only items.
Download status updates as files complete.
Cymatic manages storage automatically; custom folders, bulk downloads, layout, and
scheduling remain in remote-library settings. Automatic downloads default to a daily
Wi-Fi check when first enabled. Uninstalling the app removes its private downloads.
Additional providers are planned; the connection UI currently supports AudioStream.

Hold **Menu → Show** in a library screen to choose **All music**, **Offline only**,
or **Not offline**. The filter is shared across library screens and remembered.
Partially offline playlists appear under **Not offline**. Use the active-filter label
or the same menu to return to all music.

## Wheel controls

Start a gesture on the outer ring and move around it to scroll. On Now Playing,
the same motion adjusts volume. You can also tap the displayed list items directly.

| Control | Browsing | Now Playing |
| --- | --- | --- |
| Center | Select the highlighted item | Return to the music browser |
| Menu | Return to the main menu | Return to the main menu |
| Previous | Go back | Previous track |
| Next | Select the highlighted item | Next track |
| Play/Pause | Open Now Playing | Toggle playback |

Hold Menu for quick settings. Hold the center button on Now Playing for track
actions, or while browsing for the selected item's context menu.

**Track actions → Output info** has separate pages for the overview, format and
decoder, processing, device and volume, USB stream health, and recent errors and
events. Rotate to browse pages and scroll their details; select to return. Choose
**Copy report** to copy the diagnostic text. Hardware details that Android cannot
observe are marked explicitly.

## Direct USB audio

Connect a USB DAC and enable **Settings → Playback → USB audio → Direct USB**, then allow USB
access when prompted. The hollow diamond indicates that direct USB output is active.

Direct USB supports mono/stereo PCM WAV, FLAC (including Ogg FLAC), ALAC in M4A/MP4,
MP3, and Ogg Vorbis/Opus. Lossy formats use Android decoders with 16-bit PCM output
at the codec's sample rate. Unsupported tracks or DAC configurations fall back to
Android playback.

### DSD playback

DSF files support DSD64, DSD128, DSD256, and DSD512, including their 48 kHz-family
rates. Normal playback converts DSD to 24-bit PCM at 176.4 or 192 kHz. Direct USB
uses DoP for DSD64/128 when the DAC supports its carrier rate, and PCM conversion
otherwise. DoP is a transport for the original DSD bits; it does not convert them
to PCM audio. Higher DSD rates use PCM conversion with the current USB transport.

Under **Settings → Playback → USB audio → DSD output**, each DAC has an Automatic, Convert to
PCM, or DoP setting. Automatic recognizes the Amanero Combo384 interface; unknown
DACs use PCM. Select DoP when your DAC manufacturer confirms DoP support. A failed
DoP connection retries PCM, then falls back to Android output if needed.

If Android does not index your DSF files, add their folder under
**Settings → Storage → Add directory** and rescan. DSF title, artist, album,
duration, and embedded ID3 cover art are read by Cymatic.

## Project layout

| Module | Purpose |
| --- | --- |
| `app-player` | Standalone music player (`com.pxr.cymatic`) |
| `app-launcher` | Home-screen launcher (`com.pxr.cymatic.launcher`) |
| `shared/music` | Playback, library, playlists, EQ, and music screens |
| `shared/ui` | Themes, fonts, icons, components, and animations |
| `shared/usb` | Native USB isochronous PCM transport |
| `shared/flac` | Integer FLAC decoding for Android and direct USB playback |
| `shared/alac` | Integer ALAC decoding for Android and direct USB playback |
| `shared/dsd` | DSF DSD-to-PCM conversion and bit-perfect DoP packing |
