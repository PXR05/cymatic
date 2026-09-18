package com.pxr.cymatic.data.media

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "audio_files")
data class AudioEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val size: Int,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long?,
    @ColumnInfo(name = "bit_rate") val bitRate: Long?,
    @ColumnInfo(name = "sample_rate") val sampleRate: Long?,
    val format: String?,
    @ColumnInfo(name = "artwork_uri") val artworkUri: String?,
    @ColumnInfo(name = "date_modified") val dateModified: Long
)

data class AudioIndexEntry(
    val id: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    val size: Int
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["name"], unique = true)]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlist_id", "audio_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AudioEntity::class,
            parentColumns = ["id"],
            childColumns = ["audio_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id", "position"]),
        Index(value = ["audio_id"])
    ]
)
data class PlaylistItemEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "audio_id") val audioId: Long,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long
)
