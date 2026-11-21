package com.pxr.cymatic.sync

import android.content.Context
import android.net.Uri
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import org.json.JSONObject
import java.io.File
import java.io.OutputStream

internal class PrefixedSyncStorage(
    private val base: SyncStorage,
    prefix: String,
) : SyncStorage {
    private val cleanPrefix = prefix.trim('/')

    internal fun prefixed(relativePath: String): String {
        val clean = relativePath.trimStart('/')
        return if (cleanPrefix.isEmpty()) clean else "$cleanPrefix/$clean"
    }

    override fun exists(relativePath: String): Boolean = base.exists(prefixed(relativePath))

    override fun documentUri(relativePath: String): Uri? = base.documentUri(prefixed(relativePath))

    override fun size(relativePath: String): Long? = base.size(prefixed(relativePath))

    override fun absolutePath(relativePath: String): String? =
        base.absolutePath(prefixed(relativePath))

    override fun delete(relativePath: String): Boolean = base.delete(prefixed(relativePath))

    override fun readText(relativePath: String): String? = base.readText(prefixed(relativePath))

    override fun writeText(relativePath: String, value: String) =
        base.writeText(prefixed(relativePath), value)

    override fun copy(
        source: String,
        destination: String,
        mimeType: String,
        checkCancelled: () -> Unit,
    ) = base.copy(prefixed(source), prefixed(destination), mimeType, checkCancelled)

    override fun write(
        relativePath: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ) = base.write(prefixed(relativePath), mimeType, writer)
}

internal object SyncStorages {
    fun uniqueFolderName(
        displayName: String,
        existingFolderNames: Set<String>,
        checkFolderOwner: ((String) -> String?)? = null,
        sourceId: String? = null,
    ): String {
        val base = SyncPathPlanner.safeSegment(displayName, "Library")
        val used = existingFolderNames.mapTo(mutableSetOf()) { it.lowercase() }
        var candidate = base
        var suffix = 2
        while (true) {
            val conflictInRegistry = candidate.lowercase() in used
            val owner = checkFolderOwner?.invoke(candidate)
            val conflictOnDisk =
                !owner.isNullOrBlank() && (sourceId == null || owner != sourceId)
            if (!conflictInRegistry && !conflictOnDisk) return candidate
            candidate = "$base $suffix"
            suffix++
        }
    }

    suspend fun resolveFolderName(
        context: Context,
        config: RemoteSourceConfig,
        directory: String,
    ): String {
        config.folderName?.let { return it }
        val others =
            RemoteSourceRegistry.all(context)
                .filter { it.id != config.id }
                .mapNotNullTo(mutableSetOf()) { it.folderName?.takeIf(String::isNotBlank) }
        val saf = directory.takeIf(String::isNotBlank)?.let {
            runCatching { SafTree(context.contentResolver, Uri.parse(it)) }.getOrNull()
        }
        val assigned =
            uniqueFolderName(
                displayName = config.displayName,
                existingFolderNames = others,
                checkFolderOwner = saf?.let { tree ->
                    { folder ->
                        runCatching {
                            tree.readText("$folder/.cymatic-sync.json")
                                ?.let { JSONObject(it).optString("sourceId") }
                        }.getOrNull()
                    }
                },
                sourceId = config.id,
            )
        RemoteSourceRegistry.update(config.id) { it.copy(folderName = assigned) }
        return assigned
    }

    suspend fun forSource(
        context: Context,
        config: RemoteSourceConfig,
        directory: String,
    ): Pair<SyncStorage, String> {
        if (directory.isBlank()) {
            return ManagedSyncStorage(File(context.filesDir, "library-downloads/${config.id}")) to
                    (config.folderName ?: "")
        }
        val folderName = resolveFolderName(context, config, directory)
        val root = SafTree(context.contentResolver, Uri.parse(directory))
        val storage: SyncStorage =
            if (folderName.isEmpty()) root else PrefixedSyncStorage(root, folderName)
        return storage to folderName
    }
}
