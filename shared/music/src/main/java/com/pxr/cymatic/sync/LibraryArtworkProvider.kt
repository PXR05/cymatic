package com.pxr.cymatic.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import com.pxr.cymatic.data.media.CymaticDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class LibraryArtworkProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/*"
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ) = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Artwork is read only")
        val application = requireNotNull(context).applicationContext
        val parts = uri.pathSegments
        if (parts.size != 2 || !parts[0].matches(Regex("[a-f0-9]{64}")))
            throw FileNotFoundException("Invalid library artwork")
        val key = RemoteLibraryItemKey(parts[0], parts[1])
        val pipe = ParcelFileDescriptor.createReliablePipe()
        scope.launch {
            try {
                val file = load(application, key)
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                }
            } catch (error: Exception) {
                runCatching { pipe[1].closeWithError(error.message ?: "Cover unavailable") }
            }
        }
        return pipe[0]
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val locks = ConcurrentHashMap<Long, Mutex>()
        private val prefetchSlots = Semaphore(2)

        internal fun cache(context: Context, key: RemoteLibraryItemKey) {
            scope.launch {
                prefetchSlots.withPermit { runCatching { load(context, key) } }
            }
        }

        fun uri(context: Context, key: RemoteLibraryItemKey, version: Long): Uri = Uri.Builder()
            .scheme("content").authority("${context.packageName}.library-artwork")
            .appendPath(key.sourceId).appendPath(key.remoteId)
            .appendQueryParameter("v", version.toString()).build()

        private suspend fun load(context: Context, key: RemoteLibraryItemKey): File =
            locks.getOrPut(key.audioId()) { Mutex() }.withLock {
                val record = CymaticDatabase.getInstance(context).audioDao().getAudio(key.audioId())
                    ?: throw FileNotFoundException("Song not found")
                val directory = File(context.filesDir, "library-artwork")
                check(directory.isDirectory || directory.mkdirs()) { "Could not create artwork cache" }
                val target = File(directory, "${record.id}-${record.dateModified}.img")
                if (target.isFile && target.length() > 0) return@withLock target
                val embedded = record.offlineUri?.let { local ->
                    runCatching {
                        MediaMetadataRetriever().use { reader ->
                            val uri = Uri.parse(local)
                            if (uri.scheme == "file") reader.setDataSource(uri.path)
                            else reader.setDataSource(context, uri)
                            reader.embeddedPicture
                        }
                    }.getOrNull()
                }
                val atomic = AtomicFile(target)
                val output = atomic.startWrite()
                try {
                    if (embedded != null) output.write(embedded)
                    else {
                        val source = SyncCatalogStore.source(context, key.sourceId)
                            ?: throw FileNotFoundException("Library connection unavailable")
                        RemoteLibraries.client(context, source).openArtwork(key.remoteId)
                            .use { response ->
                                val type = response.contentType.orEmpty().substringBefore(';')
                                if (!type.startsWith("image/")) throw IOException("The server did not return cover art")
                                val input = response.input
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > 16L * 1024 * 1024) throw IOException("Cover image is too large")
                                    output.write(buffer, 0, count)
                                }
                                if (total == 0L) throw IOException("Cover image is empty")
                            }
                    }
                    atomic.finishWrite(output)
                } catch (error: Throwable) {
                    atomic.failWrite(output)
                    throw error
                }
                target
            }
    }
}
