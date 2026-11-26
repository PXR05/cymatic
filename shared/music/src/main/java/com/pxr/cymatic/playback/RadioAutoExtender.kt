package com.pxr.cymatic.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pxr.cymatic.sync.LibraryRadioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
internal class RadioAutoExtender(
    private val context: Context,
    private val player: ExoPlayer,
    private val serviceScope: CoroutineScope,
) {
    private var activeJob: Job? = null
    private var lastExhaustedKey: String? = null
    private var lastExhaustedAtMs: Long = 0L

    fun checkAndExtend() {
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        val queueSource =
            player.currentMediaItem?.mediaMetadata?.extras?.getString(QUEUE_SOURCE_KEY)
        val session = LibraryRadioManager.parseQueueSource(queueSource) ?: return
        val queue = player.playbackQueue()
        if (queue.currentIndex < 0) return

        val remaining = queue.items.size - 1 - queue.currentIndex
        if (remaining > TRIGGER_REMAINING_COUNT) return

        val exhaustedKey = "$queueSource|${player.mediaItemCount}"
        if (
            exhaustedKey == lastExhaustedKey &&
            System.currentTimeMillis() - lastExhaustedAtMs < RETRY_COOLDOWN_MS
        ) {
            return
        }
        if (activeJob?.isActive == true) return

        val existingIds = HashSet<Long>(player.mediaItemCount)
        for (i in 0 until player.mediaItemCount) {
            player.getMediaItemAt(i).mediaId.toLongOrNull()?.let(existingIds::add)
        }
        val tailIds = queue.items.takeLast(8).mapNotNull { it.mediaItem.mediaId.toLongOrNull() }

        activeJob =
            serviceScope.launch(Dispatchers.IO) {
                val nextTracks =
                    LibraryRadioManager.buildRadioExtension(
                        context = context,
                        session = session,
                        existingAudioIds = existingIds,
                        recentTailAudioIds = tailIds,
                        limit = EXTENSION_BATCH_SIZE,
                    )
                withContext(Dispatchers.Main) {
                    val liveQueueSource =
                        player.currentMediaItem?.mediaMetadata?.extras?.getString(QUEUE_SOURCE_KEY)
                    if (liveQueueSource != queueSource) return@withContext

                    val liveIds = HashSet<Long>(player.mediaItemCount)
                    for (i in 0 until player.mediaItemCount) {
                        player.getMediaItemAt(i).mediaId.toLongOrNull()?.let(liveIds::add)
                    }
                    val toAppend = nextTracks.filter { it.id !in liveIds }
                    if (toAppend.isEmpty()) {
                        lastExhaustedKey = "$queueSource|${player.mediaItemCount}"
                        lastExhaustedAtMs = System.currentTimeMillis()
                    } else {
                        lastExhaustedKey = null
                        player.addMediaItems(toAppend.map { createMediaItem(it, queueSource) })
                    }
                }
            }
    }

    private companion object {
        const val TRIGGER_REMAINING_COUNT = 5
        const val EXTENSION_BATCH_SIZE = 20
        const val RETRY_COOLDOWN_MS = 60_000L
    }
}
