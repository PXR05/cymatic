package com.pxr.cymatic.data.media

import android.content.Context
import com.pxr.cymatic.data.model.AudioFile

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

class PlaylistRepository private constructor(
    private val playlistDao: PlaylistDao
) {
    suspend fun getPlaylists(): List<Playlist> {
        return playlistDao.getPlaylists().map { it.toPlaylist() }
    }

    suspend fun getPlaylist(playlistId: Long): Playlist? {
        return playlistDao.getPlaylist(playlistId)?.toPlaylist()
    }

    suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        val existing = playlistDao.getPlaylist(playlistId) ?: return
        playlistDao.updatePlaylist(
            existing.copy(
                name = name.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun getPlaylistAudio(playlistId: Long): List<AudioFile> {
        return playlistDao.getPlaylistAudio(playlistId).map { it.toAudioFile() }
    }

    suspend fun addAudioToPlaylist(playlistId: Long, audioId: Long) {
        playlistDao.upsertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                audioId = audioId,
                position = playlistDao.nextPosition(playlistId),
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeAudioFromPlaylist(playlistId: Long, audioId: Long) {
        playlistDao.removePlaylistItem(playlistId, audioId)
    }

    suspend fun replacePlaylistAudio(playlistId: Long, audioIds: List<Long>) {
        playlistDao.replacePlaylistItems(
            playlistId = playlistId,
            audioIds = audioIds,
            addedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun getInstance(context: Context): PlaylistRepository {
            return PlaylistRepository(CymaticDatabase.getInstance(context).playlistDao())
        }
    }
}

private fun PlaylistEntity.toPlaylist(): Playlist {
    return Playlist(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
