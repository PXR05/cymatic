package com.pxr.cymatic.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.pxr.cymatic.data.model.AudioFile

const val QUEUE_SOURCE_KEY = "queue_source"

fun createMediaItem(audioFile: AudioFile, queueSource: String? = null): MediaItem {
    val extras = Bundle().apply {
        if (!queueSource.isNullOrBlank()) {
            putString(QUEUE_SOURCE_KEY, queueSource)
        }
    }

    val mediaMetadata = MediaMetadata.Builder()
        .setTitle(audioFile.metadata.title)
        .setArtist(audioFile.metadata.artist)
        .setArtworkUri(audioFile.metadata.artworkUri)
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setMediaId(audioFile.id.toString())
        .setUri(audioFile.uri)
        .setMediaMetadata(mediaMetadata)
        .build()
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
