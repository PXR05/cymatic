package com.pxr.cymatic.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.audio.dsd.DSD_MIME_TYPE
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class DsfDocument(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long,
) {
    fun index() = AudioIndexEntry(id, modified, size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
}

internal suspend fun scanDsfDocuments(
    context: Context,
    trees: List<String>,
    scanAllMedia: Boolean,
): Map<Long, DsfDocument> {
    val results = linkedMapOf<Long, DsfDocument>()
    val mediaIds = mutableMapOf<String, Long>()
    run {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        runCatching {
            context.contentResolver
                .query(
                    collection,
                    arrayOf(
                        "_id",
                        "_display_name",
                        "_size",
                        "date_modified",
                        "relative_path",
                        "volume_name",
                    ),
                    "LOWER(_display_name) LIKE ?",
                    arrayOf("%.dsf"),
                    null,
                )
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val id = cursor.getLong(0)
                        val name = cursor.getString(1) ?: continue
                        val volume =
                            cursor.getString(5).let {
                                if (it == "external_primary") "primary" else it
                            }
                        mediaIds[
                            "${volume?.lowercase(Locale.ROOT)}:${cursor.getString(4).orEmpty()}$name"] =
                            id
                        if (scanAllMedia)
                            results[id] =
                                DsfDocument(
                                    id,
                                    ContentUris.withAppendedId(collection, id),
                                    name,
                                    cursor.getLong(2),
                                    cursor.getLong(3),
                                )
                    }
                }
        }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }
    val visited = mutableSetOf<String>()
    for (treeString in trees.distinct()) {
        val tree = treeString.toUri()
        val root = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
        val pending = ArrayDeque<Pair<String, Int>>().apply { add(root to 0) }
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (documentId, depth) = pending.removeFirst()
            if (depth > 64 || !visited.add("${tree.authority}:$documentId")) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            runCatching {
                context.contentResolver
                    .query(
                        children,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        ),
                        null,
                        null,
                        null,
                    )
                    ?.use { cursor ->
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val childId = cursor.getString(0) ?: continue
                            if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                                pending.add(childId to depth + 1)
                            } else if (
                                cursor.getString(1)?.startsWith(".cymatic-sync-") != true &&
                                cursor.getString(1)?.endsWith(".dsf", ignoreCase = true) == true
                            ) {
                                val digest =
                                    MessageDigest.getInstance("SHA-256")
                                        .digest("${tree.authority}:$childId".toByteArray())
                                val id =
                                    mediaIds[
                                        childId.substringBefore(':').lowercase(Locale.ROOT) +
                                            ":" +
                                            childId.substringAfter(':')]
                                        ?: -((ByteBuffer.wrap(digest).long and Long.MAX_VALUE)
                                            .coerceAtLeast(1))
                                results[id] =
                                    DsfDocument(
                                        id,
                                        DocumentsContract.buildDocumentUriUsingTree(tree, childId),
                                        cursor.getString(1),
                                        cursor.getLong(3),
                                        cursor.getLong(4) / 1000,
                                    )
                            }
                        }
                    }
            }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }
    return results
}

@UnstableApi
internal suspend fun readDsfRecords(
    context: Context,
    documents: Collection<DsfDocument>,
): List<AudioEntity> = documents.mapNotNull { document ->
    currentCoroutineContext().ensureActive()
    runCatching {
        val metadata = readDsfMetadata(context, document.uri) ?: return@runCatching null
        val info = metadata.header
        AudioEntity(
            document.id,
            document.uri.toString(),
            document.index().size,
            metadata.title ?: document.name.substringBeforeLast('.'),
            metadata.artist,
            metadata.album,
            info.durationUs / 1000,
            info.sampleRate.toLong() * info.channels,
            info.sampleRate.toLong(),
            DSD_MIME_TYPE,
            metadata.artworkUri,
            document.modified,
        )
    }
        .getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else null }
}
