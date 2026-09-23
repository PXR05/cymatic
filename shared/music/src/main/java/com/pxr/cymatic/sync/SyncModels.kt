package com.pxr.cymatic.sync

enum class SyncNetwork(val value: String, val label: String) {
    NEVER("never", "Never"),
    WIFI("wifi", "Wi-Fi only"),
    ANY("any", "Wi-Fi or mobile");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: NEVER
    }
}

enum class SyncLayout(val value: String, val label: String) {
    FLAT("flat", "Flat"),
    ARTIST_ALBUM_TRACKS("artist_album_tracks", "Artist / album / tracks"),
    ALBUM_TRACKS("album_tracks", "Album / tracks"),
    PLAYLIST_TRACKS("playlist_tracks", "Playlists / tracks");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: ARTIST_ALBUM_TRACKS
    }
}

enum class SyncContentMode(val value: String, val label: String) {
    ALL("all", "All music"),
    SELECTED_PLAYLISTS("selected_playlists", "Selected playlists");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: ALL
    }
}

data class RemoteTrack(
    val id: String,
    val filename: String,
    val size: Long,
    val updatedAt: String,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int? = null,
)

data class PlaylistPlacement(val name: String, val position: Int)

data class RemotePlaylist(val id: String, val name: String, val trackIds: List<String>)

data class RemoteCatalog(
    val tracks: List<RemoteTrack>,
    val playlists: List<RemotePlaylist>,
    val refreshedAt: Long,
)

const val UNFILED_PLAYLIST_ID = "__cymatic_not_in_playlists__"

fun catalogPlaylists(tracks: List<RemoteTrack>, playlists: List<RemotePlaylist>): List<RemotePlaylist> {
    val assigned = playlists.flatMapTo(mutableSetOf()) { it.trackIds }
    val unfiled = tracks.map { it.id }.filterNot(assigned::contains)
    return if (unfiled.isEmpty()) playlists else playlists + RemotePlaylist(
        id = UNFILED_PLAYLIST_ID,
        name = "Not in playlists",
        trackIds = unfiled,
    )
}

data class SyncTarget(val relativePath: String, val track: RemoteTrack)

object SyncPathPlanner {
    fun targets(
        track: RemoteTrack,
        layout: SyncLayout,
        playlists: List<PlaylistPlacement> = emptyList(),
    ): List<SyncTarget> {
        val extension = track.filename.substringAfterLast('.', "mp3").safeSegment("mp3")
        val title = track.title.safeSegment("Unknown title")
        val artist = track.artist.safeSegment("Unknown artist")
        val album = track.album.safeSegment("Unknown album")
        val number = track.trackNumber?.takeIf { it > 0 }?.toString()?.padStart(2, '0')
        val numberedName = listOfNotNull(number, title).joinToString(". ") + "." + extension
        return when (layout) {
            SyncLayout.FLAT -> listOf(SyncTarget("$artist - $title.$extension", track))
            SyncLayout.ARTIST_ALBUM_TRACKS -> listOf(SyncTarget("$artist/$album/$numberedName", track))
            SyncLayout.ALBUM_TRACKS -> listOf(SyncTarget("$album/$numberedName", track))
            SyncLayout.PLAYLIST_TRACKS -> {
                val placements = playlists.ifEmpty { listOf(PlaylistPlacement("Not in a playlist", 0)) }
                placements.map { placement ->
                    val playlist = placement.name.safeSegment("Unnamed playlist")
                    val prefix = if (placement.position >= 0) (placement.position + 1).toString().padStart(2, '0') else null
                    val name = listOfNotNull(prefix, title).joinToString(". ") + "." + extension
                    SyncTarget("$playlist/$name", track)
                }
            }
        }
    }

    private fun String.safeSegment(fallback: String): String {
        val clean = trim()
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .replace(Regex("[. ]+$"), "")
            .take(120)
        return clean.ifBlank { fallback }
    }
}
