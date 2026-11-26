package com.pxr.cymatic.sync

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class QuantizedEmbedding(
    val audioId: String,
    val updatedAtMs: Long,
    val scale: Float,
    val vector: ByteArray,
) {
    fun toFloatArray(): FloatArray {
        val out = FloatArray(vector.size)
        for (i in vector.indices) {
            out[i] = vector[i].toInt() * scale
        }
        return out
    }

    fun dot(other: QuantizedEmbedding): Float {
        val n = minOf(vector.size, other.vector.size)
        var acc = 0
        for (i in 0 until n) {
            acc += vector[i].toInt() * other.vector[i].toInt()
        }
        return acc * scale * other.scale
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuantizedEmbedding) return false
        return audioId == other.audioId &&
                updatedAtMs == other.updatedAtMs &&
                scale == other.scale &&
                vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        var result = audioId.hashCode()
        result = 31 * result + updatedAtMs.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + vector.contentHashCode()
        return result
    }
}

data class RadioEmbeddingsPage(
    val model: String,
    val analysisVersion: Int,
    val dim: Int,
    val hasMore: Boolean,
    val nextCursor: String,
    val maxUpdatedAtMs: Long,
    val records: List<QuantizedEmbedding>,
)

data class StoredRadioEmbeddings(
    val model: String,
    val analysisVersion: Int,
    val dim: Int,
    val maxUpdatedAtMs: Long,
    val byAudioId: Map<String, QuantizedEmbedding>,
)

object RadioEmbeddingBinaryCodec {
    private val MAGIC = byteArrayOf(0x41, 0x53, 0x45, 0x4D) // "ASEM"
    private const val VERSION = 1
    private const val QUANT_INT8 = 1

    fun decode(bytes: ByteArray): RadioEmbeddingsPage {
        if (bytes.size < 25) {
            throw IOException("Binary embeddings payload too short (${bytes.size} bytes)")
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(4)
        buf.get(magic)
        if (!magic.contentEquals(MAGIC)) {
            throw IOException("Invalid binary embeddings magic")
        }
        val version = buf.get().toInt() and 0xFF
        if (version != VERSION) {
            throw IOException("Unsupported binary embeddings version: $version")
        }
        val quantType = buf.get().toInt() and 0xFF
        if (quantType != QUANT_INT8) {
            throw IOException("Unsupported quantization type: $quantType")
        }
        val analysisVersion = buf.short.toInt() and 0xFFFF
        val dim = buf.short.toInt() and 0xFFFF
        if (dim <= 0) {
            throw IOException("Invalid embedding dimension: $dim")
        }
        val model = readUtf8(buf)
        if (buf.remaining() < 1 + 2 + 8 + 4) {
            throw IOException("Truncated binary embeddings header")
        }
        val flags = buf.get().toInt() and 0xFF
        val hasMore = (flags and 1) != 0
        val nextCursor = readUtf8(buf)
        if (buf.remaining() < 8 + 4) {
            throw IOException("Truncated binary embeddings header")
        }
        val maxUpdatedAtMs = buf.long
        val recordCountLong = buf.int.toLong() and 0xFFFFFFFFL
        val minRecordBytes = 2L + 8L + 4L + dim.toLong()
        if (recordCountLong > buf.remaining().toLong() / minRecordBytes) {
            throw IOException("Invalid binary embeddings record count: $recordCountLong")
        }
        val recordCount = recordCountLong.toInt()
        val records = ArrayList<QuantizedEmbedding>(recordCount)
        for (i in 0 until recordCount) {
            val audioId = readUtf8(buf)
            if (buf.remaining() < 8 + 4 + dim) {
                throw IOException("Truncated binary embedding record at index $i")
            }
            val updatedAtMs = buf.long
            val scale = buf.float
            val vector = ByteArray(dim)
            buf.get(vector)
            records.add(QuantizedEmbedding(audioId, updatedAtMs, scale, vector))
        }
        return RadioEmbeddingsPage(
            model = model,
            analysisVersion = analysisVersion,
            dim = dim,
            hasMore = hasMore,
            nextCursor = nextCursor,
            maxUpdatedAtMs = maxUpdatedAtMs,
            records = records,
        )
    }

    fun encode(page: RadioEmbeddingsPage): ByteArray {
        val modelBytes = page.model.toByteArray(Charsets.UTF_8)
        val cursorBytes =
            if (page.hasMore) page.nextCursor.toByteArray(Charsets.UTF_8) else ByteArray(0)
        val baos =
            ByteArrayOutputStream(25 + modelBytes.size + cursorBytes.size + page.records.size * (16 + page.dim))
        val out = DataOutputStream(baos)
        out.write(MAGIC)
        out.writeByte(VERSION)
        out.writeByte(QUANT_INT8)
        out.writeShort(page.analysisVersion)
        out.writeShort(page.dim)
        out.writeShort(modelBytes.size)
        out.write(modelBytes)
        out.writeByte(if (page.hasMore) 1 else 0)
        out.writeShort(cursorBytes.size)
        out.write(cursorBytes)
        out.writeLong(page.maxUpdatedAtMs)
        out.writeInt(page.records.size)
        for ((audioId, updatedAtMs, scale, vector) in page.records) {
            val idBytes = audioId.toByteArray(Charsets.UTF_8)
            out.writeShort(idBytes.size)
            out.write(idBytes)
            out.writeLong(updatedAtMs)
            out.writeFloat(scale)
            if (vector.size == page.dim) {
                out.write(vector)
            } else {
                val sized = ByteArray(page.dim)
                vector.copyInto(sized, endIndex = minOf(vector.size, page.dim))
                out.write(sized)
            }
        }
        out.flush()
        return baos.toByteArray()
    }

    private fun readUtf8(buf: ByteBuffer): String {
        if (buf.remaining() < 2) {
            throw IOException("Truncated UTF-8 length")
        }
        val len = buf.short.toInt() and 0xFFFF
        if (buf.remaining() < len) {
            throw IOException("Truncated UTF-8 string ($len bytes)")
        }
        val bytes = ByteArray(len)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}

object RadioEmbeddingStore {
    private const val TAG = "RadioEmbeddings"
    private const val DIR_NAME = "library-embeddings"
    private const val PAGE_LIMIT = 500
    private const val MAX_PAGES = 1000
    private val cache = mutableMapOf<String, StoredRadioEmbeddings>()

    private fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    private fun file(context: Context, sourceId: String): AtomicFile {
        require(sourceId.matches(Regex("[a-f0-9]{64}")))
        val directory = dir(context)
        check(directory.isDirectory || directory.mkdirs()) {
            "Could not create library embeddings folder"
        }
        return AtomicFile(File(directory, "$sourceId.bin"))
    }

    @Synchronized
    fun read(context: Context, sourceId: String): StoredRadioEmbeddings? {
        cache[sourceId]?.let { return it }
        val target = runCatching { file(context, sourceId) }.getOrNull() ?: return null
        if (!target.baseFile.isFile) return null
        return runCatching {
            val bytes = target.openRead().use { it.readBytes() }
            val page = RadioEmbeddingBinaryCodec.decode(bytes)
            StoredRadioEmbeddings(
                model = page.model,
                analysisVersion = page.analysisVersion,
                dim = page.dim,
                maxUpdatedAtMs = page.maxUpdatedAtMs,
                byAudioId = page.records.associateBy { it.audioId },
            )
        }.getOrNull()?.also { cache[sourceId] = it }
    }

    @Synchronized
    fun write(context: Context, sourceId: String, stored: StoredRadioEmbeddings) {
        val target = file(context, sourceId)
        val payload =
            RadioEmbeddingBinaryCodec.encode(
                RadioEmbeddingsPage(
                    model = stored.model,
                    analysisVersion = stored.analysisVersion,
                    dim = stored.dim,
                    hasMore = false,
                    nextCursor = "",
                    maxUpdatedAtMs = stored.maxUpdatedAtMs,
                    records = stored.byAudioId.values.toList(),
                )
            )
        val output = target.startWrite()
        try {
            output.write(payload)
            target.finishWrite(output)
        } catch (error: Throwable) {
            target.failWrite(output)
            throw error
        }
        cache[sourceId] = stored
    }

    @Synchronized
    fun delete(context: Context, sourceId: String) {
        cache.remove(sourceId)
        runCatching {
            file(context, sourceId).delete()
        }
    }

    @Synchronized
    fun deleteOrphans(context: Context, knownSourceIds: Set<String>) {
        dir(context)
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "bin" && it.nameWithoutExtension !in knownSourceIds }
            .forEach { it.delete() }
    }

    fun sync(
        context: Context,
        source: RemoteLibrarySource,
        client: RemoteLibraryClient,
        validTrackIds: Set<String>? = null,
        checkCancelled: () -> Unit = {},
    ): StoredRadioEmbeddings? {
        if (RemoteProvider.from(source.provider)?.radioSupport != RadioSupport.EMBEDDINGS) {
            return null
        }
        val app = context.applicationContext
        var existing = read(app, source.id)
        var sinceMs = existing?.maxUpdatedAtMs?.takeIf { it > 0L }
        var cursor: String? = null
        var firstPage = true
        var activeModel: String? = existing?.model
        var activeVersion: Int? = existing?.analysisVersion
        var activeDim: Int? = existing?.dim
        var maxUpdatedAtMs = existing?.maxUpdatedAtMs ?: 0L
        val merged = LinkedHashMap<String, QuantizedEmbedding>(existing?.byAudioId.orEmpty())
        var changed = false
        var pageCount = 0

        while (pageCount < MAX_PAGES) {
            checkCancelled()
            pageCount++
            val page = client.fetchRadioEmbeddings(
                sinceMs = if (cursor == null) sinceMs else null,
                cursor = cursor,
                limit = PAGE_LIMIT
            )

            if (firstPage && existing != null &&
                (page.model != existing.model ||
                        page.analysisVersion != existing.analysisVersion ||
                        page.dim != existing.dim)
            ) {
                existing = null
                merged.clear()
                sinceMs = null
                cursor = null
                maxUpdatedAtMs = 0L
                changed = true
                firstPage = false
                activeModel = page.model
                activeVersion = page.analysisVersion
                activeDim = page.dim
                pageCount = 0
                continue
            }
            firstPage = false
            activeModel = page.model
            activeVersion = page.analysisVersion
            activeDim = page.dim

            for (record in page.records) {
                val prev = merged.put(record.audioId, record)
                if (prev != record) changed = true
                if (record.updatedAtMs > maxUpdatedAtMs) {
                    maxUpdatedAtMs = record.updatedAtMs
                }
            }
            if (page.maxUpdatedAtMs > maxUpdatedAtMs) {
                maxUpdatedAtMs = page.maxUpdatedAtMs
            }

            if (!page.hasMore || page.nextCursor.isBlank()) break
            cursor = page.nextCursor
        }

        if (validTrackIds != null) {
            val it = merged.keys.iterator()
            while (it.hasNext()) {
                if (it.next() !in validTrackIds) {
                    it.remove()
                    changed = true
                }
            }
        }

        if (activeModel == null || activeVersion == null || activeDim == null) {
            return existing
        }

        val result = StoredRadioEmbeddings(
            model = activeModel,
            analysisVersion = activeVersion,
            dim = activeDim,
            maxUpdatedAtMs = maxUpdatedAtMs,
            byAudioId = merged,
        )
        if (changed || existing == null) {
            write(app, source.id, result)
        }
        return result
    }

    fun syncBestEffort(
        context: Context,
        source: RemoteLibrarySource,
        client: RemoteLibraryClient,
        validTrackIds: Set<String>? = null,
        checkCancelled: () -> Unit = {},
    ) {
        try {
            sync(context, source, client, validTrackIds, checkCancelled)
        } catch (cancelled: SyncCancelledException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.d(TAG, "Embedding sync skipped for ${source.id}: ${error.message}")
        }
    }
}
