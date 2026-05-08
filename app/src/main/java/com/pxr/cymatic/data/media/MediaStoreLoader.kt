package com.pxr.cymatic.data.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.pxr.cymatic.data.media.AudioStoreDatabase.AudioDbRecord
import com.pxr.cymatic.data.media.AudioStoreDatabase.AudioIndexEntry
import com.pxr.cymatic.data.model.AudioFile

fun loadCachedAudioFiles(context: Context): List<AudioFile> {
    return AudioStoreDatabase.getInstance(context).getAllAudio()
}

fun syncAudioFilesToDb(
    context: Context,
    directories: List<String> = emptyList(),
    scanAllMedia: Boolean = true
): List<AudioFile> {
    val database = AudioStoreDatabase.getInstance(context)
    val mediaIndex = queryMediaStoreIndex(context, directories, scanAllMedia)
    Log.d(
        "MediaStoreLoader",
        "Found ${mediaIndex.size} audio entries in MediaStore for directories=$directories scanAllMedia=$scanAllMedia"
    )
    val dbIndex = database.getAudioIndex()

    val toDelete = dbIndex.keys - mediaIndex.keys
    Log.d(
        "MediaStoreLoader",
        "Deleting ${toDelete.size} entries from DB that no longer exist in MediaStore"
    )
    database.deleteByIds(toDelete)

    val toUpsert = mediaIndex.filter { (id, mediaEntry) ->
        val dbEntry = dbIndex[id]
        dbEntry == null ||
                dbEntry.dateModified != mediaEntry.dateModified ||
                dbEntry.size != mediaEntry.size
    }.keys

    if (toUpsert.isNotEmpty()) {
        val records = queryMediaStoreDetails(context, toUpsert.toList(), directories, scanAllMedia)
        database.upsertAudio(records)
    }

    return database.getAllAudio()
}

private fun queryMediaStoreIndex(
    context: Context,
    directories: List<String>,
    scanAllMedia: Boolean
): Map<Long, AudioIndexEntry> {
    val results = mutableMapOf<Long, AudioIndexEntry>()
    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.SIZE
    )
    val (selection, args) = buildRelativePathSelection(directories, scanAllMedia)

    Log.d(
        "MediaStoreLoader",
        "Querying MediaStore for index with selection=$selection args=${args?.joinToString()}"
    )

    if (!scanAllMedia && selection == null) {
        Log.d("MediaStoreLoader", "No valid directories provided for scanning, skipping MediaStore query")
        return emptyMap()
    }

    context.contentResolver.query(
        collection,
        projection,
        selection,
        args,
        null
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val dateModified = cursor.getLong(dateModifiedColumn)
            val size = cursor.getInt(sizeColumn)
            results[id] = AudioIndexEntry(id, dateModified, size)
        }
    }

    return results
}

private fun queryMediaStoreDetails(
    context: Context,
    ids: List<Long>,
    directories: List<String>,
    scanAllMedia: Boolean
): List<AudioDbRecord> {
    val records = mutableListOf<AudioDbRecord>()
    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.BITRATE,
        MediaStore.Audio.Media.SAMPLERATE,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DATE_MODIFIED
    )
    val (baseSelection, baseArgs) = buildRelativePathSelection(directories, scanAllMedia)

    for (chunk in ids.chunked(800)) {
        val (idSelection, idArgs) = buildIdSelection(chunk)
        val selection = if (baseSelection == null) {
            idSelection
        } else {
            "($baseSelection) AND ($idSelection)"
        }
        val args = if (baseArgs == null) {
            idArgs
        } else {
            baseArgs + idArgs
        }

        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            records += cursor.toAudioRecords()
        }
    }

    return records
}

private fun Cursor.toAudioRecords(): List<AudioDbRecord> {
    val records = mutableListOf<AudioDbRecord>()
    val idColumn = getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
    val titleColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
    val artistColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
    val albumColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
    val durationColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
    val bitRateColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
    val sampleRateColumn = getColumnIndex(MediaStore.Audio.Media.SAMPLERATE)
    val formatColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
    val sizeColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
    val albumIdColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
    val dateModifiedColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

    while (moveToNext()) {
        val id = getLong(idColumn)
        val title = getString(titleColumn)
        val artist = getString(artistColumn)
        val album = getString(albumColumn)
        val duration = if (isNull(durationColumn)) null else getLong(durationColumn)
        val bitRate = if (isNull(bitRateColumn)) null else getLong(bitRateColumn)
        val sampleRate = if (sampleRateColumn >= 0 && !isNull(sampleRateColumn)) {
            getLong(sampleRateColumn)
        } else {
            null
        }
        val format = getString(formatColumn)
        val size = getInt(sizeColumn)
        val albumId = getLong(albumIdColumn)
        val dateModified = getLong(dateModifiedColumn)

        val contentUri: Uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            id
        )

        val artworkUri = ContentUris.withAppendedId(
            "content://media/external/audio/albumart".toUri(),
            albumId
        )

        records += AudioDbRecord(
            id = id,
            uri = contentUri.toString(),
            size = size,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            bitRate = bitRate,
            sampleRate = sampleRate,
            format = format,
            artworkUri = artworkUri.toString(),
            dateModified = dateModified
        )
    }

    return records
}

private fun buildIdSelection(ids: List<Long>): Pair<String, Array<String>> {
    val placeholders = ids.joinToString(",") { "?" }
    val selection = "${MediaStore.Audio.Media._ID} IN ($placeholders)"
    val args = ids.map { it.toString() }.toTypedArray()
    return selection to args
}

private fun buildRelativePathSelection(
    directories: List<String>,
    scanAllMedia: Boolean
): Pair<String?, Array<String>?> {
    Log.d(
        "MediaStoreLoader",
        "Building relative path selection for directories=$directories scanAllMedia=$scanAllMedia"
    )
    if (scanAllMedia || directories.isEmpty()) return null to null
    val relativePaths = directories.mapNotNull { uriString ->
        runCatching {
            val uri = uriString.toUri()
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(':', limit = 2)
            if (parts.size < 2) return@runCatching null
            val path = parts[1]
            if (path.isBlank()) null else if (path.endsWith("/")) path else "$path/"
        }.getOrNull()
    }.distinct()

    if (relativePaths.isEmpty()) return null to null

    val selection = relativePaths.joinToString(" OR ") {
        "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? ESCAPE '\\'"
    }
    val args = relativePaths.map { path ->
        "${escapeLike(path)}%"
    }.toTypedArray()

    return selection to args
}

private fun escapeLike(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
