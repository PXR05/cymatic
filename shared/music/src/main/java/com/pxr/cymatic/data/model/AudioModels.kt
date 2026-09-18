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
    val artworkUri: Uri?
)

data class AudioFile(
    val id: Long,
    val uri: Uri,
    val size: Int,
    val metadata: AudioMetadata
)

