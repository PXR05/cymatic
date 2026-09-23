package com.pxr.cymatic.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPathPlannerTest {
    private val track = RemoteTrack(
        id = "track-id",
        filename = "server-file.flac",
        size = 10,
        updatedAt = "2026-01-01T00:00:00Z",
        title = "Song: One",
        artist = "Artist/Name",
        album = "Album?",
        trackNumber = 3,
    )

    @Test
    fun artistAlbumLayoutSanitizesSegments() {
        val target = SyncPathPlanner.targets(track, SyncLayout.ARTIST_ALBUM_TRACKS).single()
        assertEquals("Artist_Name/Album_/03. Song_ One.flac", target.relativePath)
    }

    @Test
    fun playlistLayoutCreatesOneCopyPerPlaylist() {
        val targets = SyncPathPlanner.targets(
            track,
            SyncLayout.PLAYLIST_TRACKS,
            listOf(PlaylistPlacement("Morning", 0), PlaylistPlacement("Night", 4)),
        )
        assertEquals(listOf("Morning/01. Song_ One.flac", "Night/05. Song_ One.flac"), targets.map { it.relativePath })
    }

    @Test
    fun playlistLayoutKeepsUnassignedTracks() {
        val target = SyncPathPlanner.targets(track, SyncLayout.PLAYLIST_TRACKS).single()
        assertEquals("Not in a playlist/01. Song_ One.flac", target.relativePath)
    }

    @Test
    fun catalogAddsSpecialPlaylistForUnassignedTracks() {
        val assigned = track.copy(id = "assigned")
        val unassigned = track.copy(id = "unassigned")
        val playlists = catalogPlaylists(
            tracks = listOf(assigned, unassigned),
            playlists = listOf(RemotePlaylist("remote", "Remote list", listOf("assigned"))),
        )

        assertEquals(listOf("remote", UNFILED_PLAYLIST_ID), playlists.map { it.id })
        assertEquals(listOf("unassigned"), playlists.last().trackIds)
    }

    @Test
    fun catalogDoesNotAddEmptySpecialPlaylist() {
        val playlists = catalogPlaylists(
            tracks = listOf(track),
            playlists = listOf(RemotePlaylist("remote", "Remote list", listOf(track.id))),
        )

        assertEquals(listOf("remote"), playlists.map { it.id })
    }
}
