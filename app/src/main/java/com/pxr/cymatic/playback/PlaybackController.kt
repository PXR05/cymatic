package com.pxr.cymatic.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata

const val QUEUE_SOURCE_KEY = "queue_source"

fun createMediaItem(audioFile: AudioFile, queueSource: String? = null): MediaItem {
    val extras = Bundle().apply {
        if (!queueSource.isNullOrBlank()) {
            putString(QUEUE_SOURCE_KEY, queueSource)
        }
        audioFile.metadata.duration?.let { putLong("duration", it) }
        audioFile.metadata.bitRate?.let { putLong("bit_rate", it) }
        audioFile.metadata.sampleRate?.let { putLong("sample_rate", it) }
        audioFile.metadata.format?.let { putString("format", it) }
    }

    val mediaMetadata = MediaMetadata.Builder()
        .setTitle(audioFile.metadata.title)
        .setArtist(audioFile.metadata.artist)
        .setAlbumTitle(audioFile.metadata.album)
        .setArtworkUri(audioFile.metadata.artworkUri)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setMediaId(audioFile.id.toString())
        .setUri(audioFile.uri)
        .setMediaMetadata(mediaMetadata)
        .build()
}

fun MediaItem.toAudioMetadata(): AudioMetadata {
    val meta = mediaMetadata
    val extras = meta.extras
    return AudioMetadata(
        title = meta.title?.toString(),
        artist = meta.artist?.toString(),
        album = meta.albumTitle?.toString(),
        duration = if (extras != null && extras.containsKey("duration")) extras.getLong("duration") else null,
        bitRate = if (extras != null && extras.containsKey("bit_rate")) extras.getLong("bit_rate") else null,
        sampleRate = if (extras != null && extras.containsKey("sample_rate")) extras.getLong("sample_rate") else null,
        format = extras?.getString("format"),
        artworkUri = meta.artworkUri
    )
}

fun handleItemClick(
    mediaController: MediaController,
    audioFile: AudioFile,
    queue: List<AudioFile>? = null,
    queueSource: String? = null,
    navigateToPlayer: () -> Unit = { }
) {
    val mediaId = audioFile.id.toString()
    val currentItem = mediaController.currentMediaItem

    if (currentItem != null && currentItem.mediaId == mediaId) {
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else {
            mediaController.play()
        }
    } else {
        if (queue != null) {
            val mediaItems = queue.map { item ->
                createMediaItem(item, queueSource)
            }
            mediaController.setMediaItems(
                mediaItems,
                mediaItems.indexOfFirst { it.mediaId == mediaId },
                0L
            )
        } else {
            val mediaItem = createMediaItem(audioFile, queueSource)
            mediaController.setMediaItem(mediaItem)
        }
        mediaController.prepare()
        mediaController.play()
//        navigateToPlayer()
    }
}
