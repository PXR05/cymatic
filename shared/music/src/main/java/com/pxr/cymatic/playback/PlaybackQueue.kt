package com.pxr.cymatic.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline

data class PlaybackQueueItem(val mediaItem: MediaItem, val mediaItemIndex: Int)

data class PlaybackQueue(
    val items: List<PlaybackQueueItem> = emptyList(),
    val currentIndex: Int = -1
)

fun Timeline.mediaItemIndicesInPlaybackOrder(shuffleEnabled: Boolean): List<Int> {
    if (!shuffleEnabled) return (0 until windowCount).toList()

    val indices = mutableListOf<Int>()
    val visited = BooleanArray(windowCount)
    var index = getFirstWindowIndex(true)
    while (index in visited.indices && !visited[index]) {
        indices.add(index)
        visited[index] = true
        index = getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
    }
    return indices
}

internal fun List<Int>.startingWith(mediaItemIndex: Int): List<Int> {
    val position = indexOf(mediaItemIndex)
    return if (position <= 0) this else drop(position) + take(position)
}

fun Player.playbackQueue(): PlaybackQueue {
    val indices = currentTimeline.mediaItemIndicesInPlaybackOrder(shuffleModeEnabled)
    val items = indices.filter { it in 0 until mediaItemCount }
        .map { PlaybackQueueItem(getMediaItemAt(it), it) }
    return PlaybackQueue(items, items.indexOfFirst { it.mediaItemIndex == currentMediaItemIndex })
}
