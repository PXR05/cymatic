package com.pxr.cymatic.data.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio_files ORDER BY uri COLLATE NOCASE ASC")
    suspend fun getAllAudio(): List<AudioEntity>

    @Query("SELECT id, date_modified, size FROM audio_files")
    suspend fun getAudioIndex(): List<AudioIndexEntry>

    @Upsert suspend fun upsertAudio(records: List<AudioEntity>)

    @Query("DELETE FROM audio_files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Collection<Long>)

    @Query("SELECT * FROM audio_files WHERE id IN (:ids)")
    suspend fun getAudioByIds(ids: List<Long>): List<AudioEntity>

    @Query(
        "UPDATE audio_files SET bit_rate = COALESCE(:bitRate, bit_rate), sample_rate = COALESCE(:sampleRate, sample_rate), format = COALESCE(:format, format) WHERE id = :id"
    )
    suspend fun updateTechnicalMetadata(
        id: Long,
        bitRate: Long?,
        sampleRate: Long?,
        format: String?,
    )
}
