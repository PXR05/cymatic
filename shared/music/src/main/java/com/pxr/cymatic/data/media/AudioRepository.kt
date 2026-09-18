package com.pxr.cymatic.data.media

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AudioRepository private constructor(
    private val audioDao: AudioDao
) {
    @Volatile
    private var cachedAudio: List<AudioFile>? = null
    private val cacheMutex = Mutex()

    suspend fun getAllAudio(): List<AudioFile> {
        val cached = cachedAudio
        if (cached != null) return cached
        return cacheMutex.withLock {
            val doubleChecked = cachedAudio
            if (doubleChecked != null) {
                doubleChecked
            } else {
                val dbList = audioDao.getAllAudio().map { it.toAudioFile() }
                cachedAudio = dbList
                dbList
            }
        }
    }

    fun getCachedAudio(): List<AudioFile>? {
        return cachedAudio
    }

    suspend fun getAudioIndex(): Map<Long, AudioIndexEntry> {
        return audioDao.getAudioIndex().associateBy { it.id }
    }

    suspend fun upsertAudio(records: List<AudioEntity>) {
        audioDao.upsertAudio(records)
        cacheMutex.withLock {
            cachedAudio = null
        }
    }

    suspend fun deleteByIds(ids: Collection<Long>) {
        if (ids.isNotEmpty()) {
            audioDao.deleteByIds(ids)
            cacheMutex.withLock {
                cachedAudio = null
            }
        }
    }

    suspend fun getAudioByIds(ids: List<Long>): List<AudioFile> {
        if (ids.isEmpty()) return emptyList()
        val results = audioDao.getAudioByIds(ids).associateBy { it.id }
        return ids.mapNotNull { results[it]?.toAudioFile() }
    }

    companion object {
        @Volatile
        private var instance: AudioRepository? = null

        fun getInstance(context: Context): AudioRepository {
            return instance ?: synchronized(this) {
                instance ?: AudioRepository(CymaticDatabase.getInstance(context).audioDao())
                    .also { instance = it }
            }
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
