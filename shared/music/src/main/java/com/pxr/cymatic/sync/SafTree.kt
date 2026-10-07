package com.pxr.cymatic.sync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
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
        resolver.openInputStream(uri)?.buffered()?.use { input ->
            write(destination, mimeType) { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        } ?: throw FileNotFoundException("Could not open $source for reading")
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

        val displayName = parts.last()
        val existing = child(parent, displayName)
        check(existing?.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) {
            "$displayName exists and is a directory"
        }
        val file = existing?.uri ?: createChild(parent, displayName, mimeType)
        try {
            resolver.openOutputStream(file, "wt")?.use(writer)
                ?: throw FileNotFoundException("Could not open $displayName for writing")
        } catch (error: Throwable) {
            runCatching {
                if (DocumentsContract.deleteDocument(resolver, file)) forget(file)
            }
            throw error
        }
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
