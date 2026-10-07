package com.pxr.cymatic.sync

import java.io.IOException
import java.io.OutputStream
import java.util.UUID

internal class SyncDocumentCommitException(val temporaryName: String, cause: Throwable) :
    IOException("Could not save sync file: ${cause.message ?: "storage error"}", cause)

internal class SyncLocalCommitException(
    val destinationPath: String,
    val temporaryPath: String,
    val localSize: Long,
    cause: Throwable,
) : IOException(cause.message, cause)

internal interface SyncWritableDocuments<D> {
    fun find(name: String): D?

    fun create(name: String, mimeType: String): D

    fun write(document: D, writer: (OutputStream) -> Unit)

    fun copy(source: D, destination: D)

    fun supportsRename(document: D): Boolean

    fun rename(document: D, name: String): D

    fun delete(document: D)
}

internal class SyncDocumentWriter<D>(private val documents: SyncWritableDocuments<D>) {
    fun write(name: String, mimeType: String, writer: (OutputStream) -> Unit) {
        val extension = name.substringAfterLast('.', "tmp")
        fun temporaryName() = ".cymatic-sync-${UUID.randomUUID()}.$extension"
        val pendingName = temporaryName()
        val temporary = documents.create(pendingName, mimeType)
        var bodyWritten = false
        try {
            documents.write(temporary) { output ->
                writer(output)
                bodyWritten = true
            }
        } catch (error: Throwable) {
            if (bodyWritten) throw SyncDocumentCommitException(pendingName, error)
            runCatching { documents.delete(temporary) }.onFailure(error::addSuppressed)
            throw error
        }

        try {
            val existing = documents.find(name)
            if (
                documents.supportsRename(temporary) &&
                    (existing == null || documents.supportsRename(existing))
            ) {
                var backup: D? = null
                try {
                    if (existing != null) backup = documents.rename(existing, temporaryName())
                    documents.rename(temporary, name)
                } catch (error: Throwable) {
                    backup?.let { saved ->
                        runCatching { documents.rename(saved, name) }
                            .onFailure(error::addSuppressed)
                    }
                    throw error
                }
                backup?.let { saved -> runCatching { documents.delete(saved) } }
                return
            }

            var backup: D? = null
            if (existing != null) {
                val saved = documents.create(temporaryName(), mimeType)
                try {
                    documents.copy(existing, saved)
                    backup = saved
                } catch (error: Throwable) {
                    runCatching { documents.delete(saved) }.onFailure(error::addSuppressed)
                    throw error
                }
            }
            val destination = existing ?: documents.create(name, mimeType)
            try {
                documents.copy(temporary, destination)
            } catch (error: Throwable) {
                if (backup != null) {
                    val saved = backup
                    runCatching { documents.copy(saved, destination) }
                        .onFailure(error::addSuppressed)
                } else {
                    runCatching { documents.delete(destination) }.onFailure(error::addSuppressed)
                }
                throw error
            }
            runCatching { documents.delete(temporary) }
            backup?.let { saved -> runCatching { documents.delete(saved) } }
        } catch (error: Throwable) {
            throw SyncDocumentCommitException(pendingName, error)
        }
    }
}
