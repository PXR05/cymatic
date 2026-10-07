package com.pxr.cymatic.sync

import java.util.UUID

internal data class SyncManifestEntry(
    val remoteId: String,
    val updatedAt: String,
    val size: Long,
    val localSize: Long = size,
) {
    fun matches(track: RemoteTrack): Boolean =
        remoteId == track.id && updatedAt == track.updatedAt && size == track.size

    companion object {
        fun from(track: RemoteTrack, localSize: Long = track.size) =
            SyncManifestEntry(track.id, track.updatedAt, track.size, localSize)
    }
}

internal interface SyncLocalFiles {
    fun exists(path: String): Boolean

    fun size(path: String): Long?

    fun copy(source: String, destination: String)
}

internal class SyncFileReuse(
    previous: Map<String, SyncManifestEntry>,
    desired: List<SyncTarget>,
    private val files: SyncLocalFiles,
    private val saveManifest: (Map<String, SyncManifestEntry>) -> Unit,
) {
    private val manifest = previous.toMutableMap()
    private val pathsById =
        previous.keys
            .groupBy { previous.getValue(it).remoteId }
            .mapValues { (_, paths) -> paths.toMutableSet() }
            .toMutableMap()
    private val wanted = desired.associate { it.track.id to it.track }
    val entries: Map<String, SyncManifestEntry>
        get() = manifest.toMap()

    fun reuse(target: SyncTarget): Boolean {
        val path = target.relativePath
        val old = manifest[path]
        if (old?.matches(target.track) == true && validFile(path, old)) return true
        if (old == null && target.track.size >= 0 && files.size(path) == target.track.size) {
            record(target)
            return true
        }
        val source =
            pathsById[target.track.id].orEmpty().firstOrNull { candidate ->
                val entry = manifest.getValue(candidate)
                entry.matches(target.track) && validFile(candidate, entry)
            } ?: return false

        prepareWrite(target)
        files.copy(source, path)
        record(target, manifest.getValue(source).localSize)
        return true
    }

    fun prepareWrite(target: SyncTarget) {
        val path = target.relativePath
        val old = manifest[path] ?: return
        val wantedTrack = wanted[old.remoteId] ?: return
        if (!old.matches(wantedTrack) || !validFile(path, old)) return
        val directory = path.substringBeforeLast('/', "")
        val temporary =
            listOf(directory, ".cymatic-sync-${UUID.randomUUID()}.tmp")
                .filter(String::isNotEmpty)
                .joinToString("/")
        try {
            files.copy(path, temporary)
        } catch (error: SyncLocalCommitException) {
            put(error.temporaryPath, old.copy(localSize = error.localSize))
            saveManifest(entries)
            throw error
        }
        put(temporary, old)
        saveManifest(entries)
    }

    fun record(target: SyncTarget, localSize: Long = target.track.size) {
        put(target.relativePath, SyncManifestEntry.from(target.track, localSize))
        saveManifest(entries)
    }

    fun discardMissing(path: String) {
        if (files.exists(path)) return
        manifest.remove(path)?.let { pathsById[it.remoteId]?.remove(path) }
        saveManifest(entries)
    }

    private fun validFile(path: String, entry: SyncManifestEntry): Boolean =
        if (entry.localSize >= 0)
            files.size(path)?.let { it == entry.localSize } ?: files.exists(path)
        else files.exists(path)

    private fun put(path: String, entry: SyncManifestEntry) {
        manifest.put(path, entry)?.let { pathsById[it.remoteId]?.remove(path) }
        pathsById.getOrPut(entry.remoteId) { mutableSetOf() }.add(path)
    }
}
