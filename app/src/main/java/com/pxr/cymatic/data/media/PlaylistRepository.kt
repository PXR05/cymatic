package com.pxr.cymatic.data.media

import android.content.Context
import com.pxr.cymatic.data.model.AudioFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

class PlaylistRepository private constructor(
    private val playlistDao: PlaylistDao
) {
    @Volatile
    private var cachedPlaylists: List<Playlist>? = null
    
    @Volatile
    private var cachedPlaylistAudio = mapOf<Long, List<AudioFile>>()
    
    private val playlistMutex = Mutex()

    suspend fun getPlaylists(): List<Playlist> {
        val cached = cachedPlaylists
        if (cached != null) return cached
        return playlistMutex.withLock {
            val doubleChecked = cachedPlaylists
            if (doubleChecked != null) {
                doubleChecked
            } else {
                val dbList = playlistDao.getPlaylists().map { it.toPlaylist() }
                cachedPlaylists = dbList
                dbList
            }
        }
    }

    fun getCachedPlaylists(): List<Playlist>? = cachedPlaylists

    fun getCachedPlaylistAudio(playlistId: Long): List<AudioFile>? = cachedPlaylistAudio[playlistId]

    suspend fun getPlaylist(playlistId: Long): Playlist? {
        val cached = cachedPlaylists?.find { it.id == playlistId }
        if (cached != null) return cached
        return playlistDao.getPlaylist(playlistId)?.toPlaylist()
    }

    suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        val id = playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
        playlistMutex.withLock {
            cachedPlaylists = null
        }
        return id
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) {
        val existing = playlistDao.getPlaylist(playlistId) ?: return
        playlistDao.updatePlaylist(
            existing.copy(
                name = name.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
        playlistMutex.withLock {
            cachedPlaylists = null
        }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
        playlistMutex.withLock {
            cachedPlaylists = null
            cachedPlaylistAudio = cachedPlaylistAudio - playlistId
        }
    }

    suspend fun getPlaylistAudio(playlistId: Long): List<AudioFile> {
        val cached = cachedPlaylistAudio[playlistId]
        if (cached != null) return cached
        return playlistMutex.withLock {
            val doubleChecked = cachedPlaylistAudio[playlistId]
            if (doubleChecked != null) {
                doubleChecked
            } else {
                val dbList = playlistDao.getPlaylistAudio(playlistId).map { it.toAudioFile() }
                val newMap = cachedPlaylistAudio.toMutableMap()
                newMap[playlistId] = dbList
                cachedPlaylistAudio = newMap
                dbList
            }
        }
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
        playlistMutex.withLock {
            cachedPlaylistAudio = cachedPlaylistAudio - playlistId
        }
    }

    suspend fun removeAudioFromPlaylist(playlistId: Long, audioId: Long) {
        playlistDao.removePlaylistItem(playlistId, audioId)
        playlistMutex.withLock {
            cachedPlaylistAudio = cachedPlaylistAudio - playlistId
        }
    }

    suspend fun replacePlaylistAudio(playlistId: Long, audioIds: List<Long>) {
        playlistDao.replacePlaylistItems(
            playlistId = playlistId,
            audioIds = audioIds,
            addedAt = System.currentTimeMillis()
        )
        playlistMutex.withLock {
            cachedPlaylistAudio = cachedPlaylistAudio - playlistId
        }
    }

    companion object {
        @Volatile
        private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository {
            return instance ?: synchronized(this) {
                instance ?: PlaylistRepository(CymaticDatabase.getInstance(context).playlistDao())
                    .also { instance = it }
            }
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
