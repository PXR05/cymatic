package com.pxr.cymatic.auto

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.ui.screens.library.UnknownAlbum
import com.pxr.cymatic.ui.screens.library.UnknownArtist
import com.pxr.cymatic.ui.screens.library.albumDisplayName
import com.pxr.cymatic.ui.screens.library.artistDisplayName
import com.pxr.cymatic.ui.screens.library.filterByAlbum
import com.pxr.cymatic.ui.screens.library.filterByArtist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future

@OptIn(UnstableApi::class)
class AutoMediaLibraryCallback(
    private val repository: AudioRepository,
    private val playlistRepository: PlaylistRepository,
    private val scope: CoroutineScope
) : MediaLibrarySession.Callback {
    companion object {
        const val ROOT_ID = "ROOT"
        const val NODE_SONGS = "NODE_SONGS"
        const val NODE_ALBUMS = "NODE_ALBUMS"
        const val NODE_ARTISTS = "NODE_ARTISTS"
        const val NODE_PLAYLISTS = "NODE_PLAYLISTS"
        const val PREFIX_ALBUM = "ALBUM/"
        const val PREFIX_ARTIST = "ARTIST/"
        const val PREFIX_PLAYLIST = "PLAYLIST/"
        const val PREFIX_TRACK = "TRACK/"
    }

    override fun onConnect(
        session: MediaSession,
        controller: ControllerInfo
    ): ConnectionResult {
        val sessionCommands = ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
        val playerCommands = ConnectionResult.DEFAULT_PLAYER_COMMANDS
        return ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val root = buildBrowsableItem(
            mediaId = ROOT_ID,
            title = "Cymatic",
            subtitle = null
        )
        val rootParams = LibraryParams.Builder()
            .setRecent(false)
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(root, rootParams))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.IO) {
        val items: List<MediaItem> = when {
            parentId == ROOT_ID -> buildRootChildren()
            parentId == NODE_SONGS -> buildAllSongs()
            parentId == NODE_ALBUMS -> buildAlbumNodes()
            parentId == NODE_ARTISTS -> buildArtistNodes()
            parentId == NODE_PLAYLISTS -> buildPlaylistNodes()
            parentId.startsWith(PREFIX_ALBUM) -> {
                val albumName = parentId.removePrefix(PREFIX_ALBUM)
                buildAlbumTracks(albumName)
            }
            parentId.startsWith(PREFIX_ARTIST) -> {
                val artistName = parentId.removePrefix(PREFIX_ARTIST)
                buildArtistTracks(artistName)
            }
            parentId.startsWith(PREFIX_PLAYLIST) -> {
                val playlistId = parentId.removePrefix(PREFIX_PLAYLIST).toLongOrNull()
                if (playlistId != null) buildPlaylistTracks(playlistId) else emptyList()
            }
            else -> emptyList()
        }

        val paged = items.drop(page * pageSize).take(pageSize)
        LibraryResult.ofItemList(paged, params)
    }

    @SuppressLint("WrongConstant")
    override fun onGetItem(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(Dispatchers.IO) {
        val trackId = mediaId.removePrefix(PREFIX_TRACK).toLongOrNull()
        if (trackId != null) {
            val audio = repository.getAudioByIds(listOf(trackId)).firstOrNull()
            if (audio != null) {
                LibraryResult.ofItem(buildPlayableItem(audio.id, audio.metadata.title, audio.metadata.artist, audio.metadata.artworkUri?.toString(), audio.uri.toString()), null)
            } else {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }
        } else {
            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
    }

    private fun buildRootChildren(): List<MediaItem> = listOf(
        buildBrowsableItem(NODE_SONGS, "All Songs", null),
        buildBrowsableItem(NODE_ALBUMS, "Albums", null),
        buildBrowsableItem(NODE_ARTISTS, "Artists", null),
        buildBrowsableItem(NODE_PLAYLISTS, "Playlists", null)
    )

    private suspend fun buildAllSongs(): List<MediaItem> {
        return repository.getAllAudio().map { audio ->
            buildPlayableItem(
                id = audio.id,
                title = audio.metadata.title,
                artist = audio.metadata.artist,
                artworkUriStr = audio.metadata.artworkUri?.toString(),
                contentUriStr = audio.uri.toString()
            )
        }
    }

    private suspend fun buildAlbumNodes(): List<MediaItem> {
        return repository.getAllAudio()
            .groupBy { albumDisplayName(it) }
            .keys.sorted()
            .map { albumName ->
                buildBrowsableItem(
                    mediaId = "$PREFIX_ALBUM$albumName",
                    title = albumName,
                    subtitle = null
                )
            }
    }

    private suspend fun buildAlbumTracks(albumName: String): List<MediaItem> {
        val target = albumName.ifBlank { UnknownAlbum }
        return filterByAlbum(repository.getAllAudio(), target)
            .map { audio ->
                buildPlayableItem(
                    id = audio.id,
                    title = audio.metadata.title,
                    artist = audio.metadata.artist,
                    artworkUriStr = audio.metadata.artworkUri?.toString(),
                    contentUriStr = audio.uri.toString()
                )
            }
    }

    private suspend fun buildArtistNodes(): List<MediaItem> {
        return repository.getAllAudio()
            .groupBy { artistDisplayName(it) }
            .keys.sorted()
            .map { artistName ->
                buildBrowsableItem(
                    mediaId = "$PREFIX_ARTIST$artistName",
                    title = artistName,
                    subtitle = null
                )
            }
    }

    private suspend fun buildArtistTracks(artistName: String): List<MediaItem> {
        val target = artistName.ifBlank { UnknownArtist }
        return filterByArtist(repository.getAllAudio(), target)
            .map { audio ->
                buildPlayableItem(
                    id = audio.id,
                    title = audio.metadata.title,
                    artist = audio.metadata.artist,
                    artworkUriStr = audio.metadata.artworkUri?.toString(),
                    contentUriStr = audio.uri.toString()
                )
            }
    }

    private suspend fun buildPlaylistNodes(): List<MediaItem> {
        return playlistRepository.getPlaylists().map { playlist ->
            buildBrowsableItem(
                mediaId = "$PREFIX_PLAYLIST${playlist.id}",
                title = playlist.name,
                subtitle = null
            )
        }
    }

    private suspend fun buildPlaylistTracks(playlistId: Long): List<MediaItem> {
        return playlistRepository.getPlaylistAudio(playlistId).map { audio ->
            buildPlayableItem(
                id = audio.id,
                title = audio.metadata.title,
                artist = audio.metadata.artist,
                artworkUriStr = audio.metadata.artworkUri?.toString(),
                contentUriStr = audio.uri.toString()
            )
        }
    }

    private fun buildBrowsableItem(
        mediaId: String,
        title: String,
        subtitle: String?
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildPlayableItem(
        id: Long,
        title: String?,
        artist: String?,
        artworkUriStr: String?,
        contentUriStr: String
    ): MediaItem {
        val artworkUri = artworkUriStr?.toUri()
        val contentUri = contentUriStr.toUri()
        val metadata = MediaMetadata.Builder()
            .setTitle(title ?: "Unknown")
            .setArtist(artist)
            .setArtworkUri(artworkUri)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(metadata)
            .build()
    }
}
