package com.pxr.cymatic.sync

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

internal class SyncManifestStore(context: Context, identity: String) {
    private val file: AtomicFile

    init {
        val key =
            MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
        val directory = File(context.filesDir, "sync-manifests")
        check(directory.isDirectory || directory.mkdirs()) {
            "Could not create sync checkpoint folder"
        }
        file = AtomicFile(File(directory, "$key.json"))
    }

    fun read(): Map<String, SyncManifestEntry>? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        return file.openRead().bufferedReader().use { decode(it.readText()) }
    }

    fun write(entries: Map<String, SyncManifestEntry>) {
        val bytes = encode(entries).toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    companion object {
        fun decode(value: String): Map<String, SyncManifestEntry> {
            val entries = JSONObject(value).getJSONArray("files")
            return buildMap {
                for (index in 0 until entries.length()) {
                    val item = entries.getJSONObject(index)
                    put(
                        item.getString("path"),
                        SyncManifestEntry(
                            remoteId = item.getString("remoteId"),
                            updatedAt = item.optString("updatedAt"),
                            size = item.optLong("size", -1L),
                        ),
                    )
                }
            }
        }

        fun encode(entries: Map<String, SyncManifestEntry>): String {
            val files = JSONArray()
            entries.forEach { (path, entry) ->
                files.put(
                    JSONObject()
                        .put("path", path)
                        .put("remoteId", entry.remoteId)
                        .put("updatedAt", entry.updatedAt)
                        .put("size", entry.size)
                )
            }
            return JSONObject().put("version", 1).put("files", files).toString()
        }
    }
}
