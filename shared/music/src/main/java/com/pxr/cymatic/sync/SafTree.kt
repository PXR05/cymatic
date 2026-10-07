package com.pxr.cymatic.sync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

internal class SafTree(
    private val resolver: ContentResolver,
    treeUri: Uri,
) {
    private val root =
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
    private val childrenByParent = mutableMapOf<Uri, MutableMap<String, Child>>()

    fun exists(relativePath: String): Boolean = find(relativePath) != null

    fun documentUri(relativePath: String): Uri? = find(relativePath)

    fun size(relativePath: String): Long? {
        val uri = find(relativePath) ?: return null
        val projection = arrayOf(DocumentsContract.Document.COLUMN_SIZE)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                val size = cursor.getLong(0)
                if (size > 0) return size
            }
        }
        return runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { it.statSize.takeIf { size -> size >= 0 } }
        }
            .getOrNull()
    }

    fun absolutePath(relativePath: String): String? = runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(root)
        val parts = documentId.split(':', limit = 2)
        if (parts.size != 2) return@runCatching null
        val storageRoot =
            if (parts[0].equals("primary", ignoreCase = true)) {
                "/storage/emulated/0"
            } else {
                "/storage/${parts[0]}"
            }
        File(File(storageRoot, parts[1]), relativePath).absolutePath
    }
        .getOrNull()

    fun delete(relativePath: String): Boolean {
        val uri = find(relativePath) ?: return false
        val deleted = DocumentsContract.deleteDocument(resolver, uri)
        if (deleted) forget(uri)
        return deleted
    }

    fun readText(relativePath: String): String? =
        find(relativePath)?.let { uri ->
            resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }

    fun writeText(relativePath: String, value: String) {
        write(relativePath, "application/json") { output ->
            output.write(value.toByteArray(Charsets.UTF_8))
        }
    }

    fun copy(source: String, destination: String, mimeType: String, checkCancelled: () -> Unit) {
        require(source != destination) { "Source and destination must differ" }
        val uri = find(source) ?: throw FileNotFoundException("Could not find $source")
        val expected = size(source)
        val input =
            resolver.openInputStream(uri)?.buffered()
                ?: throw FileNotFoundException("Could not open $source for reading")
        try {
            write(destination, mimeType) { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                }
                if (expected != null && copied != expected)
                    throw IOException("Incomplete local copy of $source")
            }
        } finally {
            runCatching { input.close() }
                .onFailure { Log.w("LibrarySync", "Could not close local source $source", it) }
        }
    }

    fun write(relativePath: String, mimeType: String, writer: (OutputStream) -> Unit) {
        val parts = relativePath.split('/').filter(String::isNotBlank)
        require(parts.isNotEmpty()) { "A file name is required" }
        var parent = root
        parts.dropLast(1).forEach { segment ->
            val existing = child(parent, segment)
            parent =
                when {
                    existing == null ->
                        createChild(parent, segment, DocumentsContract.Document.MIME_TYPE_DIR)
                    existing.mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> existing.uri
                    else -> throw IllegalStateException("$segment exists and is not a directory")
                }
        }

        val directory = parent
        val documents =
            object : SyncWritableDocuments<Uri> {
                override fun find(name: String): Uri? {
                    val found = child(directory, name) ?: return null
                    check(found.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
                        "$name exists and is a directory"
                    }
                    return found.uri
                }

                override fun create(name: String, mimeType: String) =
                    createChild(directory, name, mimeType)

                override fun write(document: Uri, writer: (OutputStream) -> Unit) {
                    resolver.openOutputStream(document, "wt")?.use(writer)
                        ?: throw FileNotFoundException("Could not open sync file for writing")
                }

                override fun copy(source: Uri, destination: Uri) {
                    val input =
                        resolver.openInputStream(source)?.buffered()
                            ?: throw FileNotFoundException("Could not open sync file for reading")
                    try {
                        write(destination) { output -> input.copyTo(output) }
                    } finally {
                        runCatching { input.close() }
                            .onFailure {
                                Log.w("LibrarySync", "Could not close local sync source", it)
                            }
                    }
                }

                override fun supportsRename(document: Uri): Boolean {
                    resolver
                        .query(
                            document,
                            arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                            null,
                            null,
                            null,
                        )
                        ?.use { cursor ->
                            return cursor.moveToFirst() &&
                                cursor.getInt(0) and
                                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
                        }
                    return false
                }

                override fun rename(document: Uri, name: String): Uri {
                    val originalName = displayName(document)
                    val renamed =
                        DocumentsContract.renameDocument(resolver, document, name)
                            ?: throw IOException("Could not rename sync file to $name")
                    forget(document)
                    try {
                        check(displayName(renamed) == name) {
                            "The sync folder could not preserve the filename $name"
                        }
                    } catch (error: Throwable) {
                        runCatching {
                                val restored =
                                    DocumentsContract.renameDocument(
                                        resolver,
                                        renamed,
                                        originalName,
                                    )
                                        ?: throw IOException(
                                            "Could not restore the filename $originalName"
                                        )
                                childrenByParent
                                    .getOrPut(directory) { mutableMapOf() }[originalName] =
                                    Child(restored, mimeType)
                            }
                            .onFailure(error::addSuppressed)
                        throw error
                    }
                    childrenByParent.getOrPut(directory) { mutableMapOf() }[name] =
                        Child(renamed, mimeType)
                    return renamed
                }

                override fun delete(document: Uri) {
                    if (!DocumentsContract.deleteDocument(resolver, document)) {
                        throw IOException("Could not remove temporary sync file")
                    }
                    forget(document)
                }
            }
        var written = 0L
        try {
            SyncDocumentWriter(documents).write(parts.last(), mimeType) { output ->
                val counted =
                    object : OutputStream() {
                        override fun write(value: Int) {
                            output.write(value)
                            written++
                        }

                        override fun write(buffer: ByteArray, offset: Int, length: Int) {
                            output.write(buffer, offset, length)
                            written += length
                        }

                        override fun flush() = output.flush()
                    }
                writer(counted)
            }
        } catch (error: SyncDocumentCommitException) {
            val temporaryPath = (parts.dropLast(1) + error.temporaryName).joinToString("/")
            throw SyncLocalCommitException(relativePath, temporaryPath, written, error)
        }
    }

    private fun displayName(document: Uri): String {
        resolver
            .query(
                document,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        throw FileNotFoundException("Could not verify the sync filename")
    }

    private fun find(relativePath: String): Uri? {
        var current = root
        for (segment in relativePath.split('/').filter(String::isNotBlank)) {
            current = child(current, segment)?.uri ?: return null
        }
        return current
    }

    private fun child(parent: Uri, displayName: String): Child? {
        childrenByParent[parent]?.let {
            return it[displayName]
        }
        val parentId = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
        val found = mutableMapOf<String, Child>()
        resolver.query(children, projection, null, null, null)?.use { cursor ->
            val idColumn =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                found[cursor.getString(nameColumn)] =
                    Child(
                        uri =
                            DocumentsContract.buildDocumentUriUsingTree(
                                parent,
                                cursor.getString(idColumn),
                            ),
                        mimeType = cursor.getString(mimeColumn),
                    )
            }
        } ?: throw IOException("Could not read sync folder contents")
        childrenByParent[parent] = found
        return found[displayName]
    }

    private fun createChild(parent: Uri, name: String, mimeType: String): Uri {
        val uri =
            DocumentsContract.createDocument(resolver, parent, mimeType, name)
                ?: throw FileNotFoundException("Could not create $name")
        try {
            resolver
                .query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )
                ?.use { cursor ->
                    if (!cursor.moveToFirst() || cursor.getString(0) != name) {
                        throw IOException("The sync folder could not preserve the filename $name")
                    }
                } ?: throw IOException("Could not verify the filename $name")
            childrenByParent.getOrPut(parent) { mutableMapOf() }[name] = Child(uri, mimeType)
            return uri
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw error
        }
    }

    private fun forget(uri: Uri) {
        childrenByParent.values.forEach { children ->
            children.entries.removeAll { it.value.uri == uri }
        }
    }

    private data class Child(val uri: Uri, val mimeType: String)
}
