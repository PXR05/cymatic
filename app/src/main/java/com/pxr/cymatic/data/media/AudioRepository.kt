package com.pxr.cymatic.data.media

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata

class AudioRepository private constructor(
    private val audioDao: AudioDao
) {
    suspend fun getAllAudio(): List<AudioFile> {
        return audioDao.getAllAudio().map { it.toAudioFile() }
    }

    suspend fun getAudioIndex(): Map<Long, AudioIndexEntry> {
        return audioDao.getAudioIndex().associateBy { it.id }
    }

    suspend fun upsertAudio(records: List<AudioEntity>) {
        audioDao.upsertAudio(records)
    }

    suspend fun deleteByIds(ids: Collection<Long>) {
        if (ids.isNotEmpty()) {
            audioDao.deleteByIds(ids)
        }
    }

    suspend fun getAudioByIds(ids: List<Long>): List<AudioFile> {
        if (ids.isEmpty()) return emptyList()
        val results = audioDao.getAudioByIds(ids).associateBy { it.id }
        return ids.mapNotNull { results[it]?.toAudioFile() }
    }

    companion object {
        fun getInstance(context: Context): AudioRepository {
            return AudioRepository(CymaticDatabase.getInstance(context).audioDao())
        }
    }
}

fun AudioEntity.toAudioFile(): AudioFile {
    return AudioFile(
        id = id,
        uri = uri.toUri(),
        size = size,
        metadata = AudioMetadata(
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            bitRate = bitRate,
            sampleRate = sampleRate,
            format = format,
            artworkUri = artworkUri?.let(Uri::parse)
        )
    )
}
