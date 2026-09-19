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
            return if (scrollId != null) "artist/$encodedName/all?scrollId=$scrollId" else "artist/$encodedName/all"
        }
    }

    object ArtistAlbumSongs : Screen("artist/{artistName}/album/{albumName}?scrollId={scrollId}") {
        fun createRoute(artistName: String, albumName: String, scrollId: Long? = null): String {
            val base = "artist/${Uri.encode(artistName)}/album/${Uri.encode(albumName)}"
            return if (scrollId != null) "$base?scrollId=$scrollId" else base
        }
    }

    object Albums : Screen("albums")

    object AlbumSongs : Screen("album/{albumName}?scrollId={scrollId}") {
        fun createRoute(albumName: String, scrollId: Long? = null): String {
            val encodedName = Uri.encode(albumName)
            return if (scrollId != null) "album/$encodedName?scrollId=$scrollId" else "album/$encodedName"
        }
    }

    object Playlists : Screen("playlists")

    object PlaylistSongs : Screen("playlist/{playlistId}?scrollId={scrollId}") {
        fun createRoute(playlistId: Long, scrollId: Long? = null): String {
            return if (scrollId != null) "playlist/$playlistId?scrollId=$scrollId" else "playlist/$playlistId"
        }
    }

    object Settings : Screen("settings")

    object EQSettings : Screen("setting/eq")

    object PlaybackSettings : Screen("setting/playback")

    object StorageSettings : Screen("setting/storage")

    object LibrarySyncSettings : Screen("setting/library-sync")

    object VersionSettings : Screen("setting/version")

    object Queue : Screen("queue")
}
