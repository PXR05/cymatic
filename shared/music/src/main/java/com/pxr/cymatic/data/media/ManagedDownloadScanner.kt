package com.pxr.cymatic.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.pxr.cymatic.sync.ManagedSyncStorage
import com.pxr.cymatic.sync.RemoteTrack
import com.pxr.cymatic.sync.SyncCatalogStore
import com.pxr.cymatic.sync.SyncManifestStore
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ManagedDownload(val file: File, val track: RemoteTrack?, val id: Long) {
    fun index() = AudioIndexEntry(id, file.lastModified(), file.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
}
internal suspend fun scanManagedDownloads(context: Context): Map<Long, ManagedDownload> {
    val root = File(context.filesDir, "library-downloads")
    return buildMap {
        for (sourceFolder in root.listFiles().orEmpty().filter { it.isDirectory }) {
            currentCoroutineContext().ensureActive()
            val storage = ManagedSyncStorage(sourceFolder)
            val manifest = runCatching {
                SyncManifestStore.decode(storage.readText(".cymatic-sync.json") ?: return@runCatching emptyMap())
            }.getOrDefault(emptyMap())
            val tracks = SyncCatalogStore.read(context, sourceFolder.name)?.tracks.orEmpty().associateBy { it.id }
            for ((path, entry) in manifest) {
                currentCoroutineContext().ensureActive()
                if (path.substringAfterLast('/').startsWith(".cymatic-sync-")) continue
                val file = runCatching { storage.file(path) }.getOrNull() ?: continue
                if (!file.isFile || file.length() != entry.localSize) continue
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest("managed:${sourceFolder.name}:$path".toByteArray(Charsets.UTF_8))
                val id = -((ByteBuffer.wrap(digest).long and Long.MAX_VALUE).coerceAtLeast(1))
                put(id, ManagedDownload(file, tracks[entry.remoteId], id))
            }
        }
    }
}

internal suspend fun readManagedDownloadRecords(
    context: Context,
    downloads: Collection<ManagedDownload>,
): List<AudioEntity> = downloads.map { download ->
    currentCoroutineContext().ensureActive()
    var duration: Long? = null
    var artwork: String? = null
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(download.file.path)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.embeddedPicture?.let { bytes ->
                val directory = File(context.filesDir, "download-artwork")
                check(directory.isDirectory || directory.mkdirs())
                val target = File(directory, "${download.id}.img")
                target.writeBytes(bytes)
                artwork = Uri.fromFile(target).toString()
            }
        }
    }
    AudioEntity(
        id = download.id,
        uri = Uri.fromFile(download.file).toString(),
        size = download.index().size,
        title = download.track?.title ?: download.file.nameWithoutExtension,
        artist = download.track?.artist,
        album = download.track?.album,
        duration = duration,
        bitRate = null,
        sampleRate = null,
        format = null,
        artworkUri = artwork,
        dateModified = download.index().dateModified,
    )
}
