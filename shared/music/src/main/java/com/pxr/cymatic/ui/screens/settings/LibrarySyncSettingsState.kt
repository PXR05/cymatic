package com.pxr.cymatic.ui.screens.settings

import android.provider.DocumentsContract
import com.pxr.cymatic.sync.RemoteCatalog
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncLayout
import com.pxr.cymatic.sync.SyncNetwork
import com.pxr.cymatic.sync.SyncProgress

internal val syncIntervals =
    linkedMapOf(
        1L to "Every hour",
        6L to "Every 6 hours",
        12L to "Every 12 hours",
        24L to "Daily",
        72L to "Every 3 days",
        168L to "Weekly",
    )

internal enum class SyncSetupStep {
    CONNECTION,
    FOLDER,
    MUSIC,
}

internal data class LibrarySyncSettingsState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val url: String = "",
    val username: String = "",
    val hasPassword: Boolean = false,
    val directory: String = "",
    val folderAccessible: Boolean = false,
    val network: SyncNetwork = SyncNetwork.NEVER,
    val interval: Long = 24,
    val layout: SyncLayout = SyncLayout.ARTIST_ALBUM_TRACKS,
    val contentMode: SyncContentMode = SyncContentMode.ALL,
    val musicConfigured: Boolean = false,
    val selectedPlaylists: Set<String> = emptySet(),
    val catalog: RemoteCatalog? = null,
    val progress: SyncProgress = SyncProgress.Idle,
    val refreshingCatalog: Boolean = false,
    val starting: Boolean = false,
    val message: String? = null,
    val lastTime: Long = 0,
    val lastResult: String = "Not synced yet",
) {
    val connected: Boolean
        get() = hasPassword && url.isNotBlank() && username.isNotBlank()

    val running: Boolean
        get() =
            starting ||
                progress is SyncProgress.Preparing ||
                progress is SyncProgress.Downloading ||
                progress is SyncProgress.Cancelling

    val canEdit: Boolean
        get() = !loading && !saving && (!running || refreshingCatalog)

    val selection: Set<String>
        get() =
            catalog?.let {
                selectedPlaylists.intersect(
                    it.playlists.mapTo(mutableSetOf()) { playlist -> playlist.id }
                )
            } ?: selectedPlaylists

    val nextStep: SyncSetupStep?
        get() =
            when {
                !connected -> SyncSetupStep.CONNECTION
                !folderAccessible -> SyncSetupStep.FOLDER
                !musicConfigured -> SyncSetupStep.MUSIC
                contentMode == SyncContentMode.SELECTED_PLAYLISTS && selection.isEmpty() ->
                    SyncSetupStep.MUSIC
                else -> null
            }

    val musicLabel: String
        get() =
            if (contentMode == SyncContentMode.ALL) "All music"
            else if (selection.isEmpty()) "Choose playlists" else "${selection.size} playlists"

    val statusLabel: String
        get() =
            when (val current = progress) {
                is SyncProgress.Preparing ->
                    if (refreshingCatalog) "Connecting…" else current.message
                is SyncProgress.Downloading -> "Downloading ${current.current} of ${current.total}"
                SyncProgress.Cancelling -> "Stopping…"
                is SyncProgress.Failed -> "Sync needs attention"
                SyncProgress.Cancelled -> "Stopped"
                else ->
                    when {
                        starting -> "Starting…"
                        nextStep != null -> "Setup needed"
                        else -> lastResult
                    }
            }
}

internal fun syncHostLabel(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull() ?: url

internal fun syncFolderLabel(uri: String): String = runCatching {
    DocumentsContract.getTreeDocumentId(android.net.Uri.parse(uri)).substringAfter(':').ifBlank {
        "Selected folder"
    }
}
    .getOrDefault("Selected folder")

internal fun syncLayoutExample(layout: SyncLayout): String =
    when (layout) {
        SyncLayout.FLAT -> "Artist - Track.ext"
        SyncLayout.ARTIST_ALBUM_TRACKS -> "Artist / Album / Track.ext"
        SyncLayout.ALBUM_TRACKS -> "Album / Track.ext"
        SyncLayout.PLAYLIST_TRACKS -> "Playlist / Track.ext"
    }

internal fun syncNetworkLabel(network: SyncNetwork): String =
    when (network) {
        SyncNetwork.NEVER -> "Off · sync manually"
        SyncNetwork.WIFI -> "Wi-Fi only"
        SyncNetwork.ANY -> "Wi-Fi or mobile data"
    }
