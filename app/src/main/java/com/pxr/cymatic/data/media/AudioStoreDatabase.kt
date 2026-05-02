package com.pxr.cymatic.data.media

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import androidx.core.database.sqlite.transaction
import androidx.core.net.toUri
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata

private const val DATABASE_NAME = "audio_store.db"
private const val DATABASE_VERSION = 1
private const val TABLE_AUDIO = "audio_files"

class AudioStoreDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    data class AudioIndexEntry(
        val id: Long,
        val dateModified: Long,
        val size: Int
    )

    data class AudioDbRecord(
        val id: Long,
        val uri: String,
        val size: Int,
        val title: String?,
        val artist: String?,
        val album: String?,
        val duration: Long?,
        val bitRate: Long?,
        val sampleRate: Long?,
        val format: String?,
        val artworkUri: String?,
        val dateModified: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_AUDIO (
                id INTEGER PRIMARY KEY,
                uri TEXT NOT NULL,
                size INTEGER NOT NULL,
                title TEXT,
                artist TEXT,
                album TEXT,
                duration INTEGER,
                bit_rate INTEGER,
                sample_rate INTEGER,
                format TEXT,
                artwork_uri TEXT,
                date_modified INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_AUDIO")
        onCreate(db)
    }

    fun getAllAudio(): List<AudioFile> {
        val results = mutableListOf<AudioFile>()
        readableDatabase.query(
            TABLE_AUDIO,
            null,
            null,
            null,
            null,
            null,
            "uri COLLATE NOCASE ASC"
        ).use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow("id")
            val uriColumn = cursor.getColumnIndexOrThrow("uri")
            val sizeColumn = cursor.getColumnIndexOrThrow("size")
            val titleColumn = cursor.getColumnIndexOrThrow("title")
            val artistColumn = cursor.getColumnIndexOrThrow("artist")
            val albumColumn = cursor.getColumnIndexOrThrow("album")
            val durationColumn = cursor.getColumnIndexOrThrow("duration")
            val bitRateColumn = cursor.getColumnIndexOrThrow("bit_rate")
            val sampleRateColumn = cursor.getColumnIndexOrThrow("sample_rate")
            val formatColumn = cursor.getColumnIndexOrThrow("format")
            val artworkColumn = cursor.getColumnIndexOrThrow("artwork_uri")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = cursor.getString(uriColumn).toUri()
                val size = cursor.getInt(sizeColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val duration = cursor.getLongOrNull(durationColumn)
                val bitRate = cursor.getLongOrNull(bitRateColumn)
                val sampleRate = cursor.getLongOrNull(sampleRateColumn)
                val format = cursor.getString(formatColumn)
                val artworkUri = cursor.getString(artworkColumn)?.let(Uri::parse)

                results += AudioFile(
                    id,
                    uri,
                    size,
                    AudioMetadata(
                        title,
                        artist,
                        album,
                        duration,
                        bitRate,
                        sampleRate,
                        format,
                        artworkUri
                    )
                )
            }
        }
        return results
    }

    fun getAudioIndex(): Map<Long, AudioIndexEntry> {
        val results = mutableMapOf<Long, AudioIndexEntry>()
        readableDatabase.query(
            TABLE_AUDIO,
            arrayOf("id", "date_modified", "size"),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow("id")
            val dateModifiedColumn = cursor.getColumnIndexOrThrow("date_modified")
            val sizeColumn = cursor.getColumnIndexOrThrow("size")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                results[id] = AudioIndexEntry(
                    id,
                    cursor.getLong(dateModifiedColumn),
                    cursor.getInt(sizeColumn)
                )
            }
        }
        return results
    }

    fun upsertAudio(records: List<AudioDbRecord>) {
        if (records.isEmpty()) return
        writableDatabase.transaction {
            try {
                for (record in records) {
                    val values = ContentValues().apply {
                        put("id", record.id)
                        put("uri", record.uri)
                        put("size", record.size)
                        put("title", record.title)
                        put("artist", record.artist)
                        put("album", record.album)
                        put("duration", record.duration)
                        put("bit_rate", record.bitRate)
                        put("sample_rate", record.sampleRate)
                        put("format", record.format)
                        put("artwork_uri", record.artworkUri)
                        put("date_modified", record.dateModified)
                    }
                    insertWithOnConflict(
                        TABLE_AUDIO,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            } finally {
            }
        }
    }

    fun deleteByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        writableDatabase.delete(
            TABLE_AUDIO,
            "id IN ($placeholders)",
            ids.map { it.toString() }.toTypedArray()
        )
    }

    fun getAudioByIds(ids: List<Long>): List<AudioFile> {
        if (ids.isEmpty()) return emptyList()
        val results = mutableMapOf<Long, AudioFile>()
        val placeholders = ids.joinToString(",") { "?" }
        readableDatabase.query(
            TABLE_AUDIO,
            null,
            "id IN ($placeholders)",
            ids.map { it.toString() }.toTypedArray(),
            null,
            null,
            null
        ).use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow("id")
            val uriColumn = cursor.getColumnIndexOrThrow("uri")
            val sizeColumn = cursor.getColumnIndexOrThrow("size")
            val titleColumn = cursor.getColumnIndexOrThrow("title")
            val artistColumn = cursor.getColumnIndexOrThrow("artist")
            val albumColumn = cursor.getColumnIndexOrThrow("album")
            val durationColumn = cursor.getColumnIndexOrThrow("duration")
            val bitRateColumn = cursor.getColumnIndexOrThrow("bit_rate")
            val sampleRateColumn = cursor.getColumnIndexOrThrow("sample_rate")
            val formatColumn = cursor.getColumnIndexOrThrow("format")
            val artworkColumn = cursor.getColumnIndexOrThrow("artwork_uri")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = cursor.getString(uriColumn).toUri()
                val size = cursor.getInt(sizeColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val duration = cursor.getLongOrNull(durationColumn)
                val bitRate = cursor.getLongOrNull(bitRateColumn)
                val sampleRate = cursor.getLongOrNull(sampleRateColumn)
                val format = cursor.getString(formatColumn)
                val artworkUri = cursor.getString(artworkColumn)?.let(Uri::parse)

                results[id] = AudioFile(
                    id,
                    uri,
                    size,
                    AudioMetadata(
                        title,
                        artist,
                        album,
                        duration,
                        bitRate,
                        sampleRate,
                        format,
                        artworkUri
                    )
                )
            }
        }
        return ids.mapNotNull { results[it] }
    }

    private fun Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (isNull(columnIndex)) null else getLong(columnIndex)
    }

    companion object {
        @Volatile
        private var instance: AudioStoreDatabase? = null

        fun getInstance(context: Context): AudioStoreDatabase {
            return instance ?: synchronized(this) {
                instance ?: AudioStoreDatabase(context.applicationContext).also { instance = it }
            }
        }
    }
}
