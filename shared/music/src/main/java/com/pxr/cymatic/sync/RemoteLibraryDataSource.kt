package com.pxr.cymatic.sync

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.CymaticDatabase
import com.pxr.cymatic.data.media.LibraryChanges
import com.pxr.cymatic.data.media.PlaylistRepository
import java.io.IOException
import java.io.EOFException
import kotlinx.coroutines.runBlocking

@UnstableApi
internal class RemoteLibraryDataSource(private val context: Context) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var local: DataSource? = null
    private var remote: RemoteLibraryDownload? = null
    private var spec: DataSpec? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var started = false
    private var network = false
    private var streamClient: Pair<String, RemoteLibraryClient>? = null

    override fun addTransferListener(transferListener: TransferListener) {
        if (transferListener !in listeners) listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        check(spec == null) { "A media source is already open" }
        spec = dataSpec
        try {
            val key = if (dataSpec.uri.scheme == "cymatic") {
                RemoteLibraryItemKey(requireNotNull(dataSpec.uri.host), dataSpec.uri.pathSegments.single())
            } else null
            val record = key?.let { runBlocking { CymaticDatabase.getInstance(context).audioDao().getAudio(it.audioId()) } }
            val saved = record?.offlineUri
            val offline = saved?.takeIf { RemoteLibraryRepository.isReadable(context, it) }
            if (saved != null && offline == null) runBlocking {
                CymaticDatabase.getInstance(context).audioDao().upsertAudio(listOf(record.copy(offlineUri = null)))
                AudioRepository.getInstance(context).invalidate()
                PlaylistRepository.getInstance(context).invalidate()
                LibraryChanges.notifyChanged()
            }
            network = key != null && offline == null
            listeners.forEach { it.onTransferInitializing(this, dataSpec, network) }
            remaining = if (!network) {
                val delegate = DefaultDataSource.Factory(context).createDataSource()
                local = delegate
                delegate.open(if (offline == null) dataSpec else dataSpec.withUri(Uri.parse(offline)))
            } else {
                val source = SyncCatalogStore.source(context, key!!.sourceId)
                    ?: throw IOException("This library connection is unavailable. Reconnect in Settings.")
                val client = streamClient?.takeIf { it.first == source.id }?.second
                    ?: RemoteLibraries.client(context, source).also { streamClient = source.id to it }
                val response = client.openStream(key.remoteId, dataSpec.position, dataSpec.length)
                remote = response
                val size = response.contentLength?.toLongOrNull()?.takeIf { it >= 0L }
                    ?: record?.size?.takeIf { it > 0 && it < Int.MAX_VALUE }
                        ?.let { (it.toLong() - dataSpec.position).coerceAtLeast(0L) }
                    ?: C.LENGTH_UNSET.toLong()
                if (dataSpec.length == C.LENGTH_UNSET.toLong()) size
                    else if (size < 0) dataSpec.length else minOf(size, dataSpec.length)
            }
            started = true
            listeners.forEach { it.onTransferStart(this, dataSpec, network) }
            return remaining
        } catch (error: Throwable) {
            runCatching { close() }.onFailure { error.addSuppressed(it) }
            if (error is IOException) throw error
            throw IOException(error.message ?: "Could not open the track", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = if (remaining < 0) length else minOf(length.toLong(), remaining).toInt()
        val received = local?.read(buffer, offset, count) ?: remote?.input?.read(buffer, offset, count)
            ?: throw IOException("No track is open")
        if (received == C.RESULT_END_OF_INPUT && remaining > 0)
            throw EOFException("The audio stream ended before the expected length")
        if (received > 0) {
            if (remaining >= 0) remaining -= received
            spec?.let { dataSpec -> listeners.forEach { it.onBytesTransferred(this, dataSpec, network, received) } }
        }
        return received
    }

    override fun getUri(): Uri? = spec?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = local?.responseHeaders ?: remote?.responseHeaders.orEmpty()

    override fun close() {
        try {
            try { local?.close() } finally { remote?.close() }
        } finally {
            if (started) spec?.let { dataSpec -> listeners.forEach { it.onTransferEnd(this, dataSpec, network) } }
            local = null
            remote = null
            spec = null
            started = false
            remaining = C.LENGTH_UNSET.toLong()
        }
    }

    class Factory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = RemoteLibraryDataSource(context.applicationContext)
    }
}
