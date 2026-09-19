package com.pxr.cymatic.sync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.OutputStream
import java.io.File

internal class SafTree(
    private val resolver: ContentResolver,
    treeUri: Uri,
) {
    private val root = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )

    fun exists(relativePath: String): Boolean = find(relativePath) != null

    fun size(relativePath: String): Long? {
        val uri = find(relativePath) ?: return null
        val projection = arrayOf(DocumentsContract.Document.COLUMN_SIZE)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return null
    }

    fun absolutePath(relativePath: String): String? = runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(root)
        val parts = documentId.split(':', limit = 2)
        if (parts.size != 2) return@runCatching null
        val storageRoot = if (parts[0].equals("primary", ignoreCase = true)) {
            "/storage/emulated/0"
        } else {
            "/storage/${parts[0]}"
        }
        File(File(storageRoot, parts[1]), relativePath).absolutePath
    }.getOrNull()

    fun delete(relativePath: String): Boolean {
        val uri = find(relativePath) ?: return false
        return DocumentsContract.deleteDocument(resolver, uri)
    }

    fun readText(relativePath: String): String? = find(relativePath)?.let { uri ->
        resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }

    fun writeText(relativePath: String, value: String) {
        write(relativePath, "application/json") { output ->
            output.write(value.toByteArray(Charsets.UTF_8))
        }
    }

    fun write(relativePath: String, mimeType: String, writer: (OutputStream) -> Unit) {
        val parts = relativePath.split('/').filter(String::isNotBlank)
        require(parts.isNotEmpty()) { "A file name is required" }
        var parent = root
        parts.dropLast(1).forEach { segment ->
            val existing = child(parent, segment)
            parent = when {
                existing == null -> DocumentsContract.createDocument(
                    resolver,
                    parent,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment,
                ) ?: throw FileNotFoundException("Could not create directory $segment")
                existing.mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> existing.uri
                else -> throw IllegalStateException("$segment exists and is not a directory")
            }
        }

        val displayName = parts.last()
        child(parent, displayName)?.let { DocumentsContract.deleteDocument(resolver, it.uri) }
        val file = DocumentsContract.createDocument(resolver, parent, mimeType, displayName)
            ?: throw FileNotFoundException("Could not create $displayName")
        try {
            resolver.openOutputStream(file, "w")?.use(writer)
                ?: throw FileNotFoundException("Could not open $displayName for writing")
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, file) }
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
        val parentId = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        resolver.query(children, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == displayName) {
                    return Child(
                        uri = DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(idColumn)),
                        mimeType = cursor.getString(mimeColumn),
                    )
                }
            }
        }
        return null
    }

    private data class Child(val uri: Uri, val mimeType: String)
}
