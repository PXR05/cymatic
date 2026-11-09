package com.pxr.cymatic.sync

import android.net.Uri
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal interface SyncStorage {
    fun exists(relativePath: String): Boolean
    fun documentUri(relativePath: String): Uri?
    fun size(relativePath: String): Long?
    fun absolutePath(relativePath: String): String?
    fun delete(relativePath: String): Boolean
    fun readText(relativePath: String): String?
    fun writeText(relativePath: String, value: String)
    fun copy(source: String, destination: String, mimeType: String, checkCancelled: () -> Unit)
    fun write(relativePath: String, mimeType: String, writer: (OutputStream) -> Unit)
}
internal class ManagedSyncStorage(root: File) : SyncStorage {
    private val root = root.canonicalFile

    internal fun file(path: String): File {
        require(path.isNotBlank() && !File(path).isAbsolute) { "A relative file path is required" }
        val target = File(root, path).canonicalFile
        require(target.toPath().startsWith(root.toPath()) && target != root) {
            "The file path must stay inside the download folder"
        }
        return target
    }

    override fun exists(relativePath: String) = file(relativePath).isFile
    override fun documentUri(relativePath: String): Uri? =
        file(relativePath).takeIf { it.isFile }?.let(Uri::fromFile)
    override fun size(relativePath: String): Long? = file(relativePath).takeIf { it.isFile }?.length()
    override fun absolutePath(relativePath: String): String? = null
    override fun delete(relativePath: String) = file(relativePath).delete()
    override fun readText(relativePath: String) = file(relativePath).takeIf { it.isFile }?.readText()
    override fun writeText(relativePath: String, value: String) =
        write(relativePath, "application/json") { it.write(value.toByteArray(Charsets.UTF_8)) }

    override fun copy(source: String, destination: String, mimeType: String, checkCancelled: () -> Unit) {
        require(source != destination) { "Source and destination must differ" }
        file(source).inputStream().buffered().use { input ->
            write(destination, mimeType) { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    override fun write(relativePath: String, mimeType: String, writer: (OutputStream) -> Unit) {
        val target = file(relativePath)
        val parent = target.parentFile!!
        check(parent.isDirectory || parent.mkdirs()) { "Could not create download folder" }
        val temporary = File(parent, ".cymatic-sync-${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { output ->
                writer(output)
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}
