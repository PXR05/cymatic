package com.pxr.cymatic.ui.screens.settings.sync

import android.provider.DocumentsContract
import com.pxr.cymatic.sync.CatalogProgress
import com.pxr.cymatic.sync.RemoteCatalog
import com.pxr.cymatic.sync.RemoteProvider
import com.pxr.cymatic.sync.RemoteSourceConfig
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

internal data class RemoteSourceSummary(
    val config: RemoteSourceConfig,
    val hasCredentials: Boolean,
    val catalog: RemoteCatalog?,
    val hasPinnedMusic: Boolean,
) {
    val providerLabel: String
        get() = RemoteProvider.from(config.provider)?.label ?: config.provider

    val statusText: String
        get() =
            when {
                !config.enabled -> "Disabled"
                !hasCredentials || config.lastError == "auth" -> "Sign in required"
                config.lastError == "unreachable" -> "Unreachable"
                catalog != null -> "${catalog.tracks.size} songs"
                else -> "Connected"
            }

    val subtitle: String
        get() =
            when {
                !config.enabled -> "$providerLabel · Disabled"
                !hasCredentials || config.lastError == "auth" -> "$providerLabel · Sign in required"
                config.lastError == "unreachable" -> "$providerLabel · Unreachable"
                else -> providerLabel
            }

    val hasOfflineSelection: Boolean
        get() {
            val mode =
                if (config.contentConfigured) SyncContentMode.from(config.contentMode)
                else SyncContentMode.NONE
            return when (mode) {
                SyncContentMode.NONE -> hasPinnedMusic
                SyncContentMode.ALL -> true
                SyncContentMode.SELECTED_PLAYLISTS ->
                    config.selectedPlaylists.isNotEmpty() || hasPinnedMusic
            }
        }
}

internal data class LibrarySyncSettingsState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val sources: List<RemoteSourceSummary> = emptyList(),
    val directory: String = "",
    val folderAccessible: Boolean = false,
    val network: SyncNetwork = SyncNetwork.NEVER,
    val interval: Long = 24,
    val layout: SyncLayout = SyncLayout.ARTIST_ALBUM_TRACKS,
    val progress: SyncProgress = SyncProgress.Idle,
    val catalogProgress: CatalogProgress = CatalogProgress.Idle,
    val starting: Boolean = false,
    val message: String? = null,
    val lastTime: Long = 0,
    val lastResult: String = "Not synced yet",
) {
    val connected: Boolean
        get() = sources.any { it.config.enabled && it.hasCredentials }

    val refreshingCatalog: Boolean
        get() = catalogProgress is CatalogProgress.Refreshing

    val running: Boolean
        get() =
            refreshingCatalog ||
                    starting ||
                    progress is SyncProgress.Preparing ||
                    progress is SyncProgress.Downloading ||
                    progress is SyncProgress.Cancelling

    val canEdit: Boolean
        get() = !loading && !saving && !running

    val hasOfflineSelection: Boolean
        get() = sources.any { it.config.enabled && it.hasCredentials && it.hasOfflineSelection }

    val nextStep: SyncSetupStep?
        get() =
            when {
                !connected -> SyncSetupStep.CONNECTION
                directory.isNotBlank() && !folderAccessible -> SyncSetupStep.FOLDER
                !hasOfflineSelection -> SyncSetupStep.MUSIC
                else -> null
            }

    val statusLabel: String
        get() =
            when (val cat = catalogProgress) {
                is CatalogProgress.Refreshing ->
                    cat.sourceName?.let { "Refreshing $it…" } ?: "Refreshing library…"

                else ->
                    when (val current = progress) {
                        is SyncProgress.Preparing ->
                            current.sourceName?.let { "$it · ${current.message}" }
                                ?: current.message

                        is SyncProgress.Downloading -> {
                            val prefix = current.sourceName?.let { "$it · " }.orEmpty()
                            "${prefix}Downloading ${current.current} of ${current.total}"
                        }

                        SyncProgress.Cancelling -> "Stopping…"
                        is SyncProgress.Failed -> "Sync needs attention"
                        SyncProgress.Cancelled -> "Stopped"
                        else ->
                            when {
                                starting -> "Starting…"
                                !connected -> "Add a remote library"
                                !hasOfflineSelection -> "Stream only"
                                nextStep != null -> "Check folder access"
                                else -> lastResult
                            }
                    }
            }

    val statusSummary: String
        get() =
            if (refreshingCatalog) "Refreshing…"
            else
                when (val current = progress) {
                    is SyncProgress.Preparing -> "Preparing"

                    is SyncProgress.Downloading ->
                        "Downloading (${current.current}/${current.total})"

                    SyncProgress.Cancelling -> "Stopping…"
                    is SyncProgress.Failed -> "Needs attention"
                    SyncProgress.Cancelled -> "Stopped"
                    else ->
                        when {
                            starting -> "Starting…"
                            !connected -> "No libraries"
                            !hasOfflineSelection -> "Stream only"
                            nextStep != null -> "Needs attention"
                            lastResult.startsWith("Synced") || lastResult.startsWith("Playlist") ->
                                "Synced"

                            lastResult.contains("fail", ignoreCase = true) ||
                                    lastResult.contains("incomplete", ignoreCase = true) ->
                                "Needs attention"

                            lastResult.contains("stopped", ignoreCase = true) -> "Stopped"
                            lastTime > 0 -> "Synced"
                            else -> "Idle"
                        }
                }
}

internal data class RemoteSourceState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val config: RemoteSourceConfig? = null,
    val hasCredentials: Boolean = false,
    val catalog: RemoteCatalog? = null,
    val hasPinnedMusic: Boolean = false,
    val directory: String = "",
    val folderAccessible: Boolean = true,
    val network: SyncNetwork = SyncNetwork.NEVER,
    val interval: Long = 24,
    val progress: SyncProgress = SyncProgress.Idle,
    val catalogProgress: CatalogProgress = CatalogProgress.Idle,
    val starting: Boolean = false,
    val message: String? = null,
) {
    val provider: RemoteProvider
        get() = config?.let { RemoteProvider.from(it.provider) } ?: RemoteProvider.AUDIOSTREAM

    val displayName: String
        get() = config?.displayName?.ifBlank { provider.label } ?: provider.label

    val url: String
        get() = config?.address.orEmpty()

    val username: String
        get() = config?.account.orEmpty()

    val enabled: Boolean
        get() = config?.enabled ?: true

    val connected: Boolean
        get() = hasCredentials && url.isNotBlank()

    val contentMode: SyncContentMode
        get() =
            if (config?.contentConfigured == true) SyncContentMode.from(config.contentMode)
            else SyncContentMode.NONE

    val selectedPlaylists: Set<String>
        get() = config?.selectedPlaylists.orEmpty()

    val selection: Set<String>
        get() =
            catalog?.let {
                selectedPlaylists.intersect(
                    it.playlists.mapTo(mutableSetOf()) { playlist -> playlist.id }
                )
            } ?: selectedPlaylists

    val refreshingCatalog: Boolean
        get() = catalogProgress is CatalogProgress.Refreshing

    val running: Boolean
        get() =
            refreshingCatalog ||
                    starting ||
                    progress is SyncProgress.Preparing ||
                    progress is SyncProgress.Downloading ||
                    progress is SyncProgress.Cancelling

    val canEdit: Boolean
        get() = !loading && !saving && !running

    val hasOfflineSelection: Boolean
        get() =
            when (contentMode) {
                SyncContentMode.NONE -> hasPinnedMusic
                SyncContentMode.ALL -> true
                SyncContentMode.SELECTED_PLAYLISTS -> selection.isNotEmpty() || hasPinnedMusic
            }

    val nextStep: SyncSetupStep?
        get() =
            when {
                !connected -> SyncSetupStep.CONNECTION
                directory.isNotBlank() && !folderAccessible -> SyncSetupStep.FOLDER
                !hasOfflineSelection -> SyncSetupStep.MUSIC
                else -> null
            }

    val musicLabel: String
        get() =
            when (contentMode) {
                SyncContentMode.NONE ->
                    if (hasPinnedMusic) "Chosen songs and playlists" else "Stream only"

                SyncContentMode.ALL -> "All music"
                SyncContentMode.SELECTED_PLAYLISTS ->
                    if (selection.isNotEmpty()) "${selection.size} playlists"
                    else if (hasPinnedMusic) "Chosen songs and playlists" else "Choose playlists"
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
