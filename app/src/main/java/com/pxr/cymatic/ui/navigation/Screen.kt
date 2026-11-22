package com.pxr.cymatic.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Home : Screen("home")

    object AllSongs : Screen("all_songs?scrollId={scrollId}") {
        fun createRoute(scrollId: Long? = null): String {
            return if (scrollId != null) "all_songs?scrollId=$scrollId" else "all_songs"
        }
    }

    object Artists : Screen("artists")

    object ArtistAlbums : Screen("artist/{artistName}") {
        fun createRoute(artistName: String): String = "artist/${Uri.encode(artistName)}"
    }

    object ArtistSongs : Screen("artist/{artistName}/all?scrollId={scrollId}") {
        fun createRoute(artistName: String, scrollId: Long? = null): String {
            val encodedName = Uri.encode(artistName)
            return if (scrollId != null) "artist/$encodedName/all?scrollId=$scrollId"
            else "artist/$encodedName/all"
        }
    }

    object ArtistAlbumSongs :
        Screen("artist/{artistName}/album/{albumName}?scrollId={scrollId}&sourceKey={sourceKey}") {
        fun createRoute(
            artistName: String,
            albumName: String,
            scrollId: Long? = null,
            sourceKey: String? = null,
        ): String {
            val base = "artist/${Uri.encode(artistName)}/album/${Uri.encode(albumName)}"
            val params = buildList {
                if (scrollId != null) add("scrollId=$scrollId")
                if (!sourceKey.isNullOrBlank()) add("sourceKey=${Uri.encode(sourceKey)}")
            }
            return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        }
    }

    object Albums : Screen("albums")

    object AlbumSongs : Screen("album/{albumName}?scrollId={scrollId}&sourceKey={sourceKey}") {
        fun createRoute(
            albumName: String,
            scrollId: Long? = null,
            sourceKey: String? = null,
        ): String {
            val base = "album/${Uri.encode(albumName)}"
            val params = buildList {
                if (scrollId != null) add("scrollId=$scrollId")
                if (!sourceKey.isNullOrBlank()) add("sourceKey=${Uri.encode(sourceKey)}")
            }
            return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        }
    }

    object Playlists : Screen("playlists")

    object PlaylistSongs : Screen("playlist/{playlistId}?scrollId={scrollId}") {
        fun createRoute(playlistId: Long, scrollId: Long? = null): String {
            return if (scrollId != null) "playlist/$playlistId?scrollId=$scrollId"
            else "playlist/$playlistId"
        }
    }

    object Settings : Screen("settings")

    object EQSettings : Screen("setting/eq")

    object PlaybackSettings : Screen("setting/playback")

    object UsbSettings : Screen("setting/usb")

    object StorageSettings : Screen("setting/storage")

    object LibrarySyncSettings : Screen("setting/library-sync")

    object AboutSettings : Screen("setting/about")

    object ReportBug : Screen("setting/about/report-bug")

    object VersionSettings : Screen("setting/version")

    object LicensesSettings : Screen("setting/licenses")

    object Queue : Screen("queue")

    object SongInfo : Screen("song/{mediaId}/info") {
        fun createRoute(id: Long) = "song/$id/info"
    }

    object AddToPlaylist : Screen("song/{mediaId}/playlists") {
        fun createRoute(id: Long) = "song/$id/playlists"
    }

    object OutputInfo : Screen("playback/output-info")

    object OutputInfoSection : Screen("playback/output-info/{section}") {
        fun createRoute(section: String) = "playback/output-info/${Uri.encode(section)}"
    }

    object InterfaceSettings : Screen("interface_settings")

    object TextScrollingSettings : Screen("interface_settings/text-scrolling")

    object NowPlaying : Screen("now_playing")

    object QuickSettings : Screen("quick_settings")

    object TrackActions : Screen("track_actions")

    object ScanStatus : Screen("setting/storage/status")

    object UsbDeviceReport : Screen("setting/usb/device/{deviceName}") {
        fun createRoute(name: String) = "setting/usb/device/${Uri.encode(name)}"
    }

    object BackgroundPlaybackHelp : Screen("setting/usb/background-help")

    object UsbGraph : Screen("usb_graph")

    object VersionGraph : Screen("version_graph")

    object Changelog : Screen("setting/version/changelog")

    object EqGraph : Screen("eq_graph")

    object EqPresets : Screen("setting/eq/presets")

    object EqBands : Screen("setting/eq/bands")

    object EqBand : Screen("setting/eq/band/{bandIndex}") {
        fun createRoute(index: Int) = "setting/eq/band/$index"
    }

    object EqGraphPage : Screen("setting/eq/response")

    object HardwareEq : Screen("setting/eq/dac")

    object HardwareEqPresets : Screen("setting/eq/dac/presets")

    object HardwareEqBands : Screen("setting/eq/dac/bands")

    object HardwareEqBand : Screen("setting/eq/dac/band/{bandIndex}") {
        fun createRoute(index: Int) = "setting/eq/dac/band/$index"
    }

    object HardwareEqDevice : Screen("setting/eq/dac/device")

    object HardwareEqIssues : Screen("setting/eq/dac/issues")

    object HardwareEqGraphPage : Screen("setting/eq/dac/response")

    object SyncGraph : Screen("sync_graph")

    object SyncConnections : Screen("setting/library-sync/connections")

    object SyncAddSource : Screen("setting/library-sync/add")

    object SyncAddSourceConnection : Screen("setting/library-sync/add/{provider}") {
        fun createRoute(provider: String) = "setting/library-sync/add/${Uri.encode(provider)}"
    }

    object SyncSource : Screen("setting/library-sync/source/{sourceId}") {
        fun createRoute(sourceId: String) = "setting/library-sync/source/${Uri.encode(sourceId)}"
    }

    object SyncSourceConnection : Screen("setting/library-sync/source/{sourceId}/connection") {
        fun createRoute(sourceId: String) =
            "setting/library-sync/source/${Uri.encode(sourceId)}/connection"
    }

    object SyncSourceLibrary : Screen("setting/library-sync/source/{sourceId}/library") {
        fun createRoute(sourceId: String) =
            "setting/library-sync/source/${Uri.encode(sourceId)}/library"
    }

    object SyncSourceBulkDownloads : Screen("setting/library-sync/source/{sourceId}/bulk") {
        fun createRoute(sourceId: String) =
            "setting/library-sync/source/${Uri.encode(sourceId)}/bulk"
    }

    object SyncSourcePlaylists : Screen("setting/library-sync/source/{sourceId}/playlists") {
        fun createRoute(sourceId: String) =
            "setting/library-sync/source/${Uri.encode(sourceId)}/playlists"
    }

    object SyncSourceRemove : Screen("setting/library-sync/source/{sourceId}/remove") {
        fun createRoute(sourceId: String) =
            "setting/library-sync/source/${Uri.encode(sourceId)}/remove"
    }

    object SyncAdvanced : Screen("setting/library-sync/advanced")

    object SyncSchedule : Screen("setting/library-sync/schedule")

    object SyncStatus : Screen("setting/library-sync/status")

    object SyncError : Screen("setting/library-sync/error")

    object SyncHelp : Screen("setting/library-sync/help")

    object LicensesGraph : Screen("licenses_graph")

    object LicenseGroupPage : Screen("setting/licenses/group/{group}") {
        fun createRoute(group: String) = "setting/licenses/group/${Uri.encode(group)}"
    }

    object LicenseDocument : Screen("setting/licenses/group/{group}/document/{documentIndex}") {
        fun createRoute(group: String, index: Int) =
            "setting/licenses/group/${Uri.encode(group)}/document/$index"
    }
}

fun resolveScreenRoute(pattern: String, argument: (String) -> String?): String? {
    var route = pattern
    for (match in Regex("\\{([^}]+)\\}").findAll(pattern)) {
        val value = argument(match.groupValues[1]) ?: return null
        route = route.replace(match.value, Uri.encode(value))
    }
    return route
}

fun legacyPlayerRoutes(
    panel: String,
    previousPanel: String,
    backStack: List<String>,
): List<String> {
    fun route(value: String): String? =
        when (value) {
            "NOW_PLAYING" -> Screen.NowPlaying.route
            "QUICK_SETTINGS" -> Screen.QuickSettings.route
            "TRACK_ACTIONS" -> Screen.TrackActions.route
            else -> null
        }

    val playerRoutes =
        setOf(Screen.NowPlaying.route, Screen.QuickSettings.route, Screen.TrackActions.route)
    if (backStack.any { it in playerRoutes }) return emptyList()
    val current = route(panel) ?: return emptyList()
    val previous = route(previousPanel)
    return listOfNotNull(previous?.takeIf { it != current }, current)
}
