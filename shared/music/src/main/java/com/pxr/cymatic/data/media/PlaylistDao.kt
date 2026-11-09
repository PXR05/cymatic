package com.pxr.cymatic.data.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE sourceId = :sourceId AND remoteId = :remoteId LIMIT 1")
    suspend fun getRemotePlaylist(sourceId: String, remoteId: String): PlaylistEntity?

    @Query("""
        SELECT playlists.id AS playlistId, COUNT(audio_files.id) AS total,
        SUM(CASE WHEN audio_files.id IS NOT NULL AND (audio_files.sourceId IS NULL OR audio_files.offlineUri IS NOT NULL) THEN 1 ELSE 0 END) AS offline
        FROM playlists LEFT JOIN playlist_items ON playlists.id = playlist_items.playlist_id
        LEFT JOIN audio_files ON playlist_items.audio_id = audio_files.id
        GROUP BY playlists.id
    """)
    suspend fun availability(): List<PlaylistAvailabilityCount>

    @Query("UPDATE OR IGNORE playlist_items SET audio_id = :newId WHERE audio_id = :oldId")
    suspend fun remapAudio(oldId: Long, newId: Long)
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    suspend fun getPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylist(playlistId: Long): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query(
        """
        SELECT audio_files.* FROM audio_files
        INNER JOIN playlist_items ON playlist_items.audio_id = audio_files.id
        WHERE playlist_items.playlist_id = :playlistId
        ORDER BY playlist_items.position ASC
        """
    )
    suspend fun getPlaylistAudio(playlistId: Long): List<AudioEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlist_id = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId AND audio_id = :audioId")
    suspend fun removePlaylistItem(playlistId: Long, audioId: Long)

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistItems(items: List<PlaylistItemEntity>)

    @Transaction
    suspend fun replacePlaylistItems(playlistId: Long, audioIds: List<Long>, addedAt: Long) {
        clearPlaylistItems(playlistId)
        upsertPlaylistItems(
            audioIds.mapIndexed { index, audioId ->
                PlaylistItemEntity(
                    playlistId = playlistId,
                    audioId = audioId,
                    position = index,
                    addedAt = addedAt
                )
            }
        )
    }
}
