# Remote Libraries & Offline Downloads

Cymatic can connect to multiple remote music libraries at the same time and blend their songs, albums, and playlists into your library alongside local music. Supported providers:

- **[AudioStream](https://github.com/PXR05/audiostream)**
- **Subsonic** (Navidrome, gonic, Airsonic-Advanced, Ampache, LMS, and compatible servers)

## Adding a remote library

1. Open **Settings → Remote library → Connections → Add library**.
2. Choose a provider (**AudioStream** or **Subsonic**).
3. Follow the guided steps to enter your server address, credentials, and optional display name.
   - If you omit `http://` or `https://`, Cymatic tries `https://` first and falls back to `http://`.
   - For OpenSubsonic servers, you can sign in with either a username and password (salted token auth) or an API key.

Once connected, your remote songs, albums, and playlists appear in your library immediately.

*Note: Local storage permission is not required if you only listen from remote libraries.*

## Managing connections

Open **Settings → Remote library → Connections** and select any library to:

- **Connection:** Edit the name, server address, username, password, or API key (edits autosave).
- **Library:** View song, album, and playlist counts or refresh the catalog without downloading audio.
- **Downloads:** Choose whether to keep music offline by individual selection (**Choose in library**), **All music**, or **Selected playlists**, or trigger **Download now**.
- **Streaming quality** *(Subsonic)*: Stream the untouched original file by default, or optionally transcode to MP3 on metered mobile networks. Offline downloads always fetch the original file.
- **Enabled:** Temporarily hide a library's tracks and playlists without deleting cached data or offline downloads.
- **Remove library:** Disconnect a library and remove its cached metadata (with the option to keep or delete files saved in a custom folder).

## Saving music offline

Long press any song, album, artist, or playlist and choose **Keep offline** to download it. You can keep queueing more items while downloads are running, and status icons update as files finish:

| Icon | Meaning |
| --- | --- |
| Animated icon | Queued or downloading |
| Download icon | Available offline |
| Half-filled circle | Partially available offline |
| Cloud icon | Stream-only |

## Storage & automatic downloads

Cymatic manages offline storage automatically, storing each library in its own isolated folder. Global options are available under **Settings → Remote library → Advanced**:

- Custom download folder (one subfolder per library) and folder layout
- Scheduled automatic downloads (defaults to a daily Wi-Fi check when offline music is first enabled)

Uninstalling Cymatic removes its private offline downloads.

## Filtering by offline status and source

Hold **Menu → Show** on any library screen to filter what is displayed:

- **Availability:** **All music**, **Offline only**, or **Not offline**
- **Source:** **All sources**, **This device**, or any enabled remote library

When more than one source is active, songs, albums, and playlists display their source name, and **Song Information** includes a **Source** row. Tap the active filter bar or open **Menu → Show** again to return to all music.
