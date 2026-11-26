package com.pxr.cymatic.sync

import android.content.Context
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.LibraryAvailability
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class RadioNotice(
    val id: Long,
    val message: String,
    val failed: Boolean = false,
)

data class RadioSession(
    val seedAudioId: Long,
    val offlineOnly: Boolean,
)

sealed interface RadioBuildResult {
    data class Ready(
        val tracks: List<AudioFile>,
        val queueSource: String,
        val note: String? = null,
    ) : RadioBuildResult

    data class Failed(val message: String) : RadioBuildResult
}

object LibraryRadioManager {
    private const val RADIO_QUEUE_SOURCE_PREFIX = "radio:"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJob = AtomicReference<Job?>(null)
    private val noticeIds = AtomicLong()
    private val mutableNotice = MutableStateFlow<RadioNotice?>(null)
    val notice = mutableNotice.asStateFlow()

    fun formatQueueSource(seedAudioId: Long, offlineOnly: Boolean): String =
        "$RADIO_QUEUE_SOURCE_PREFIX$seedAudioId:${if (offlineOnly) "offline" else "all"}"

    fun parseQueueSource(queueSource: String?): RadioSession? {
        if (queueSource == null || !queueSource.startsWith(RADIO_QUEUE_SOURCE_PREFIX)) return null
        val parts = queueSource.removePrefix(RADIO_QUEUE_SOURCE_PREFIX).split(':')
        if (parts.size != 2) return null
        val seedId = parts[0].toLongOrNull() ?: return null
        val offlineOnly =
            when (parts[1]) {
                "offline" -> true
                "all" -> false
                else -> return null
            }
        return RadioSession(seedId, offlineOnly)
    }

    fun canStartRadio(audioFile: AudioFile): Boolean {
        val sourceId = audioFile.sourceId ?: return false
        if (audioFile.remoteId.isNullOrBlank()) return false
        val config = RemoteSourceRegistry.cached(sourceId) ?: return false
        if (!config.enabled) return false
        val provider = RemoteProvider.from(config.provider) ?: return false
        return provider.radioSupport != RadioSupport.NONE
    }

    fun startRadio(
        context: Context,
        seed: AudioFile,
        onReady: (tracks: List<AudioFile>, queueSource: String) -> Unit,
    ) {
        val app = context.applicationContext
        activeJob.getAndSet(null)?.cancel()
        lateinit var job: Job
        job = scope.launch {
            var pendingNotice: RadioNotice? = null
            try {
                val offlineOnly = LibraryFilterStore.filter.value == LibraryFilter.OFFLINE
                val sourceId = seed.sourceId
                val remoteId = seed.remoteId
                val cachedConfig = sourceId?.let { RemoteSourceRegistry.cached(it) }
                val provider = cachedConfig?.let { RemoteProvider.from(it.provider) }
                val hasLocalSeed =
                    sourceId != null &&
                            remoteId != null &&
                            provider?.radioSupport == RadioSupport.EMBEDDINGS &&
                            RadioEmbeddingStore.read(
                                app,
                                sourceId
                            )?.byAudioId?.containsKey(remoteId) == true

                if (!hasLocalSeed) {
                    pendingNotice =
                        showNotice("Starting radio…", failed = false, autoDismiss = false)
                }

                when (val result = buildRadio(app, seed, offlineOnly = offlineOnly)) {
                    is RadioBuildResult.Ready -> {
                        if (pendingNotice != null) {
                            mutableNotice.compareAndSet(pendingNotice, null)
                        }
                        if (!result.note.isNullOrBlank()) {
                            showNotice(result.note, failed = false)
                        }
                        withContext(Dispatchers.Main) {
                            onReady(result.tracks, result.queueSource)
                        }
                    }

                    is RadioBuildResult.Failed -> {
                        showNotice(result.message, failed = true)
                    }
                }
            } catch (_: CancellationException) {
                if (pendingNotice != null) {
                    mutableNotice.compareAndSet(pendingNotice, null)
                }
            } catch (error: Throwable) {
                showNotice(error.message ?: "Could not start radio", failed = true)
            } finally {
                activeJob.compareAndSet(job, null)
            }
        }
        activeJob.set(job)
    }

    suspend fun buildRadio(
        context: Context,
        seed: AudioFile,
        offlineOnly: Boolean = LibraryFilterStore.filter.value == LibraryFilter.OFFLINE,
        limit: Int = 25,
    ): RadioBuildResult {
        val app = context.applicationContext
        val sourceId = seed.sourceId
            ?: return RadioBuildResult.Failed("Radio is only available for remote tracks")
        val remoteId = seed.remoteId?.takeIf { it.isNotBlank() }
            ?: return RadioBuildResult.Failed("Radio is unavailable for this track")
        val config = RemoteSourceRegistry.get(sourceId, app)
        if (config == null || !config.enabled) {
            return RadioBuildResult.Failed("This library is disabled")
        }
        val provider = RemoteProvider.from(config.provider)
        if (provider == null || provider.radioSupport == RadioSupport.NONE) {
            return RadioBuildResult.Failed("Radio is not supported for this library")
        }
        val source = config.toSource()

        if (provider.radioSupport == RadioSupport.EMBEDDINGS) {
            var stored = RadioEmbeddingStore.read(app, sourceId)
            if ((stored == null || remoteId !in stored.byAudioId) && SyncCredentialStore.has(
                    app,
                    sourceId
                )
            ) {
                runCatching {
                    val client = RemoteLibraries.client(app, source, ClientPurpose.RADIO)
                    RadioEmbeddingStore.sync(app, source, client)
                }
                stored = RadioEmbeddingStore.read(app, sourceId)
            }

            val seedVec = stored?.byAudioId?.get(remoteId)
            if (seedVec != null) {
                val embeddingsById = stored.byAudioId
                val allTracks = AudioRepository.getInstance(app).getAllAudio()
                val byRemoteId = HashMap<String, AudioFile>()
                val candidates = ArrayList<RadioCandidate>()
                for (track in allTracks) {
                    if (track.sourceId != sourceId) continue
                    val trackRemoteId = track.remoteId ?: continue
                    if (trackRemoteId == remoteId) continue
                    if (offlineOnly && track.availability != LibraryAvailability.OFFLINE) continue
                    val vec = embeddingsById[trackRemoteId] ?: continue
                    byRemoteId[trackRemoteId] = track
                    candidates.add(
                        RadioCandidate(
                            id = trackRemoteId,
                            embedding = vec,
                            artist = track.metadata.artist,
                            album = track.metadata.album,
                        )
                    )
                }
                val pickedIds = RadioQueueBuilder.build(
                    seed = seedVec,
                    excludeId = remoteId,
                    candidates = candidates,
                    opts = RadioQueueOptions(
                        limit = limit,
                        seedArtist = seed.metadata.artist,
                        seedAlbum = seed.metadata.album,
                    ),
                )
                val pickedTracks = pickedIds.mapNotNull { byRemoteId[it] }
                if (pickedTracks.isEmpty()) {
                    return RadioBuildResult.Failed(
                        if (offlineOnly) "No similar offline tracks found"
                        else "No similar tracks indexed yet"
                    )
                }
                val note =
                    if (offlineOnly) "Offline radio · ${pickedTracks.size} similar tracks" else null
                val queueSource = formatQueueSource(seed.id, offlineOnly)
                return RadioBuildResult.Ready(listOf(seed) + pickedTracks, queueSource, note = note)
            }
        }

        return buildFromServer(app, source, config, provider, seed, offlineOnly, limit)
    }

    suspend fun buildRadioExtension(
        context: Context,
        session: RadioSession,
        existingAudioIds: Set<Long>,
        recentTailAudioIds: List<Long>,
        limit: Int = 20,
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val effectiveOfflineOnly =
            session.offlineOnly || LibraryFilterStore.filter.value == LibraryFilter.OFFLINE
        val audioRepo = AudioRepository.getInstance(app)
        val seed = audioRepo.getAudioByIds(listOf(session.seedAudioId)).firstOrNull()
            ?: return@withContext emptyList()
        val sourceId = seed.sourceId ?: return@withContext emptyList()
        val seedRemoteId =
            seed.remoteId?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        val config = RemoteSourceRegistry.get(sourceId, app)
        if (config == null || !config.enabled) return@withContext emptyList()
        val provider = RemoteProvider.from(config.provider)
        if (provider == null || provider.radioSupport == RadioSupport.NONE) {
            return@withContext emptyList()
        }
        val source = config.toSource()
        val tailTracks = audioRepo.getAudioByIds(recentTailAudioIds.takeLast(8))

        if (provider.radioSupport == RadioSupport.EMBEDDINGS) {
            val stored = RadioEmbeddingStore.read(app, sourceId)
            val seedVec = stored?.byAudioId?.get(seedRemoteId)
            if (seedVec != null) {
                val embeddingsById = requireNotNull(stored).byAudioId
                val allTracks = audioRepo.getAllAudio()
                val excludedRemoteIds = HashSet<String>(existingAudioIds.size + 1)
                excludedRemoteIds.add(seedRemoteId)
                for (track in allTracks) {
                    if (track.sourceId == sourceId && track.id in existingAudioIds) {
                        track.remoteId?.let(excludedRemoteIds::add)
                    }
                }
                val byRemoteId = HashMap<String, AudioFile>()
                val candidates = ArrayList<RadioCandidate>()
                for (track in allTracks) {
                    if (track.sourceId != sourceId) continue
                    val trackRemoteId = track.remoteId ?: continue
                    if (trackRemoteId in excludedRemoteIds) continue
                    if (effectiveOfflineOnly && track.availability != LibraryAvailability.OFFLINE) continue
                    val vec = embeddingsById[trackRemoteId] ?: continue
                    byRemoteId[trackRemoteId] = track
                    candidates.add(
                        RadioCandidate(
                            id = trackRemoteId,
                            embedding = vec,
                            artist = track.metadata.artist,
                            album = track.metadata.album,
                        )
                    )
                }
                val tailEmbedding = tailTracks.lastOrNull()?.remoteId?.let { embeddingsById[it] }
                val pickedIds = RadioQueueBuilder.build(
                    seed = seedVec,
                    excludeId = seedRemoteId,
                    candidates = candidates,
                    opts = RadioQueueOptions(
                        limit = limit,
                        excludeIds = excludedRemoteIds,
                        initialRecentArtists = tailTracks.takeLast(4)
                            .mapNotNull { it.metadata.artist },
                        initialRecentAlbums = tailTracks.takeLast(3)
                            .mapNotNull { it.metadata.album },
                        anchorBlend = tailEmbedding,
                    ),
                )
                return@withContext pickedIds.mapNotNull { byRemoteId[it] }
            }
        }

        val queryRemoteId =
            tailTracks.lastOrNull()?.takeIf { it.sourceId == sourceId }?.remoteId ?: seedRemoteId
        val requestLimit = if (effectiveOfflineOnly) 100 else maxOf(limit * 2, 50)
        val firstBatch = fetchServerExtension(
            context = app,
            source = source,
            queryRemoteId = queryRemoteId,
            requestLimit = requestLimit,
            existingAudioIds = existingAudioIds,
            seedAudioId = seed.id,
            offlineOnly = effectiveOfflineOnly,
            limit = limit,
        )
        if (firstBatch.isNotEmpty() || queryRemoteId == seedRemoteId) {
            return@withContext firstBatch
        }
        fetchServerExtension(
            context = app,
            source = source,
            queryRemoteId = seedRemoteId,
            requestLimit = requestLimit,
            existingAudioIds = existingAudioIds,
            seedAudioId = seed.id,
            offlineOnly = effectiveOfflineOnly,
            limit = limit,
        )
    }

    private suspend fun fetchServerExtension(
        context: Context,
        source: RemoteLibrarySource,
        queryRemoteId: String,
        requestLimit: Int,
        existingAudioIds: Set<Long>,
        seedAudioId: Long,
        offlineOnly: Boolean,
        limit: Int,
    ): List<AudioFile> {
        val remoteTracks =
            runCatching {
                val client = RemoteLibraries.client(context, source, ClientPurpose.RADIO)
                client.getRadioTracks(queryRemoteId, requestLimit)
            }.getOrNull() ?: return emptyList()

        val audioIds = remoteTracks.map { source.itemKey(it.id).audioId() }
        return AudioRepository.getInstance(context)
            .getAudioByIds(audioIds)
            .asSequence()
            .filter { it.id != seedAudioId && it.id !in existingAudioIds }
            .distinctBy { it.id }
            .filter { !offlineOnly || it.availability == LibraryAvailability.OFFLINE }
            .take(limit)
            .toList()
    }

    private suspend fun buildFromServer(
        context: Context,
        source: RemoteLibrarySource,
        config: RemoteSourceConfig,
        provider: RemoteProvider,
        seed: AudioFile,
        offlineOnly: Boolean,
        limit: Int,
    ): RadioBuildResult {
        val remoteId = requireNotNull(seed.remoteId)
        val requestLimit = if (offlineOnly) 100 else limit
        val remoteTracks =
            try {
                val client = RemoteLibraries.client(context, source, ClientPurpose.RADIO)
                client.getRadioTracks(remoteId, requestLimit)
            } catch (error: Throwable) {
                val message =
                    if (provider.radioSupport == RadioSupport.EMBEDDINGS) {
                        "Track not analyzed for radio yet · connect to sync"
                    } else {
                        "Radio unavailable · ${connectionError(error, provider)}"
                    }
                return RadioBuildResult.Failed(message)
            }

        val audioIds = remoteTracks.map { source.itemKey(it.id).audioId() }
        val resolved =
            AudioRepository.getInstance(context)
                .getAudioByIds(audioIds)
                .asSequence()
                .filter { it.id != seed.id }
                .distinctBy { it.id }
                .filter { !offlineOnly || it.availability == LibraryAvailability.OFFLINE }
                .take(limit)
                .toList()

        if (resolved.isEmpty()) {
            return RadioBuildResult.Failed(
                if (offlineOnly) "No similar offline tracks found"
                else "No similar tracks found on ${config.displayName}"
            )
        }
        val note = if (offlineOnly) "Offline radio · ${resolved.size} tracks" else null
        val queueSource = formatQueueSource(seed.id, offlineOnly)
        return RadioBuildResult.Ready(listOf(seed) + resolved, queueSource, note = note)
    }

    private fun showNotice(
        message: String,
        failed: Boolean,
        autoDismiss: Boolean = true,
    ): RadioNotice {
        val notice = RadioNotice(noticeIds.incrementAndGet(), message, failed)
        mutableNotice.value = notice
        if (autoDismiss) {
            scope.launch {
                delay(if (failed) 5500L else 3500L)
                mutableNotice.compareAndSet(notice, null)
            }
        }
        return notice
    }
}
