package com.pxr.cymatic.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.pxr.cymatic.data.library.artistDisplayTextOrNull
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata

const val QUEUE_SOURCE_KEY = "queue_source"

fun createMediaItem(audioFile: AudioFile, queueSource: String? = null): MediaItem {
    val extras =
        Bundle().apply {
            if (!queueSource.isNullOrBlank()) {
                putString(QUEUE_SOURCE_KEY, queueSource)
            }
            audioFile.metadata.duration?.let { putLong("duration", it) }
            audioFile.metadata.bitRate?.let { putLong("bit_rate", it) }
            audioFile.metadata.sampleRate?.let { putLong("sample_rate", it) }
            audioFile.metadata.format?.let { putString("format", it) }
            if (audioFile.metadata.artists.isNotEmpty()) {
                putStringArrayList("artists", ArrayList(audioFile.metadata.artists))
            }
        }

    val mediaMetadata =
        MediaMetadata.Builder()
            .setTitle(audioFile.metadata.title)
            .setArtist(artistDisplayTextOrNull(audioFile.metadata))
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
        .setMimeType(audioFile.metadata.format)
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
        duration =
            if (extras != null && extras.containsKey("duration")) extras.getLong("duration")
            else null,
        bitRate =
            if (extras != null && extras.containsKey("bit_rate")) extras.getLong("bit_rate")
            else null,
        sampleRate =
            if (extras != null && extras.containsKey("sample_rate")) extras.getLong("sample_rate")
            else null,
        format = extras?.getString("format"),
        artworkUri = meta.artworkUri,
        artists = extras?.getStringArrayList("artists")?.toList().orEmpty(),
    )
}

fun Player.playAudioSelection(
    audioFile: AudioFile,
    queue: List<AudioFile>? = null,
    queueSource: String? = null,
) {
    if (currentMediaItem?.mediaId == audioFile.id.toString()) {
        togglePlayback()
    } else {
        val songs = queue ?: listOf(audioFile)
        val index = songs.indexOfFirst { it.id == audioFile.id }
        if (index >= 0) playAudioQueue(songs, queueSource, index)
    }
}

fun Player.playAudioQueue(
    songs: List<AudioFile>,
    queueSource: String? = null,
    startIndex: Int = 0,
) {
    if (songs.isEmpty()) return
    setMediaItems(songs.map { createMediaItem(it, queueSource) }, startIndex, 0L)
    prepare()
    play()
}

fun Player.enqueueAudio(
    songs: List<AudioFile>,
    queueSource: String? = null,
    next: Boolean = false,
) {
    if (songs.isEmpty()) return
    if (mediaItemCount == 0) {
        playAudioQueue(songs, queueSource)
    } else {
        val items = songs.map { createMediaItem(it, queueSource) }
        if (next) addMediaItems((currentMediaItemIndex + 1).coerceAtLeast(0), items)
        else addMediaItems(items)
    }
}

fun Player.togglePlayback() {
    if (isPlaying) pause()
    else {
        prepare()
        play()
    }
}

fun Player.cycleRepeat() {
    repeatMode =
        when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
}
