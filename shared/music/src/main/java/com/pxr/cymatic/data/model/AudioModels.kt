package com.pxr.cymatic.data.model

import android.net.Uri

data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long?,
    val bitRate: Long?,
    val sampleRate: Long?,
    val format: String?,
    val artworkUri: Uri?,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val artists: List<String> = emptyList(),
)

data class AudioFile(
    val id: Long,
    val uri: Uri,
    val size: Int,
    val metadata: AudioMetadata,
    val sourceId: String? = null,
    val remoteId: String? = null,
    val offlineUri: Uri? = null,
) {
    val availability: LibraryAvailability
        get() = if (sourceId == null || offlineUri != null) LibraryAvailability.OFFLINE else LibraryAvailability.STREAM
}

