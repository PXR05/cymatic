package com.pxr.cymatic.data.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio_files ORDER BY uri COLLATE NOCASE ASC")
    suspend fun getAllAudio(): List<AudioEntity>

    @Query("SELECT id, date_modified, size FROM audio_files")
    suspend fun getAudioIndex(): List<AudioIndexEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudio(records: List<AudioEntity>)

    @Query("DELETE FROM audio_files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Collection<Long>)

    @Query("SELECT * FROM audio_files WHERE id IN (:ids)")
    suspend fun getAudioByIds(ids: List<Long>): List<AudioEntity>
}
