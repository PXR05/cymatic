package com.pxr.cymatic.data.media

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AudioRepository private constructor(private val audioDao: AudioDao) {
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
                loadFromDb().also { cachedAudio = it }
            }
        }
    }

    private suspend fun loadFromDb(): List<AudioFile> =
        audioDao.getAllAudio().let { records ->
            val remoteCopies =
                records.filter { it.sourceId != null }.mapNotNull { it.offlineUri }.toSet()
            records.filter { it.sourceId != null || it.uri !in remoteCopies }
                .map { it.toAudioFile() }
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
            cachedAudio = loadFromDb()
        }
    }

    suspend fun deleteByIds(ids: Collection<Long>) {
        if (ids.isNotEmpty()) {
            audioDao.deleteByIds(ids)
            cacheMutex.withLock {
                cachedAudio = loadFromDb()
            }
        }
    }

    suspend fun getAudioByIds(ids: List<Long>): List<AudioFile> {
        if (ids.isEmpty()) return emptyList()
        val results = audioDao.getAudioByIds(ids).associateBy { it.id }
        return ids.mapNotNull { results[it]?.toAudioFile() }
    }

    internal suspend fun updateTechnicalMetadata(id: Long, metadata: AudioTechnicalMetadata) {
        audioDao.updateTechnicalMetadata(id, metadata.bitRate, metadata.sampleRate, metadata.codec)
        cacheMutex.withLock {
            cachedAudio =
                cachedAudio?.map { file ->
                    if (file.id != id) file
                    else
                        file.copy(
                            metadata =
                                file.metadata.copy(
                                    bitRate = metadata.bitRate ?: file.metadata.bitRate,
                                    sampleRate = metadata.sampleRate ?: file.metadata.sampleRate,
                                    format = metadata.codec ?: file.metadata.format,
                                )
                        )
                }
        }
    }

    suspend fun invalidate() {
        cacheMutex.withLock { cachedAudio = loadFromDb() }
    }

    companion object {
        @Volatile
        private var instance: AudioRepository? = null

        fun getInstance(context: Context): AudioRepository {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: AudioRepository(CymaticDatabase.getInstance(context).audioDao()).also {
                            instance = it
                        }
                }
        }
    }
}

fun AudioEntity.toAudioFile(): AudioFile {
    return AudioFile(
        id = id,
        uri = uri.toUri(),
        size = size,
        sourceId = sourceId,
        remoteId = remoteId,
        offlineUri = offlineUri?.toUri(),
        metadata =
            AudioMetadata(
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                bitRate = bitRate,
                sampleRate = sampleRate,
                format = format,
                artworkUri = artworkUri?.let(Uri::parse),
            ),
    )
}
