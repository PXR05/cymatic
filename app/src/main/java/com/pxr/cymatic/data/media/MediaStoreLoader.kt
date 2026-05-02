package com.pxr.cymatic.data.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import com.pxr.cymatic.data.media.AudioStoreDatabase.AudioDbRecord
import com.pxr.cymatic.data.media.AudioStoreDatabase.AudioIndexEntry
import com.pxr.cymatic.data.model.AudioFile

fun loadCachedAudioFiles(context: Context): List<AudioFile> {
    return AudioStoreDatabase.getInstance(context).getAllAudio()
}

fun syncAudioFilesToDb(context: Context): List<AudioFile> {
    val database = AudioStoreDatabase.getInstance(context)
    val mediaIndex = queryMediaStoreIndex(context)
    val dbIndex = database.getAudioIndex()

    val toDelete = dbIndex.keys - mediaIndex.keys
    database.deleteByIds(toDelete)

    val toUpsert = mediaIndex.filter { (id, mediaEntry) ->
        val dbEntry = dbIndex[id]
        dbEntry == null ||
            dbEntry.dateModified != mediaEntry.dateModified ||
            dbEntry.size != mediaEntry.size
    }.keys

    if (toUpsert.isNotEmpty()) {
        val records = queryMediaStoreDetails(context, toUpsert.toList())
        database.upsertAudio(records)
    }

    return database.getAllAudio()
}

private fun queryMediaStoreIndex(context: Context): Map<Long, AudioIndexEntry> {
    val results = mutableMapOf<Long, AudioIndexEntry>()
    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.SIZE
    )

    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
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

private fun queryMediaStoreDetails(context: Context, ids: List<Long>): List<AudioDbRecord> {
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

    for (chunk in ids.chunked(800)) {
        val (selection, args) = buildIdSelection(chunk)
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
