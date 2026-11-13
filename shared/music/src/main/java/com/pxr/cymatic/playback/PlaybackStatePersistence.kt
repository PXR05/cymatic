package com.pxr.cymatic.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.store.PlaybackStore
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
internal class PlaybackStatePersistence(
    private val context: Context,
    private val player: ExoPlayer,
    private val fadingPlayer: FadingPlayer,
    private val serviceScope: CoroutineScope,
) {
    @Volatile
    var lastSavedPositionMs = -1L
        private set

    private companion object {
        const val TAG = "PlaybackService"
    }

    fun save() {
        serviceScope.launch {
            try {
                val state = withContext(Dispatchers.Main) { snapshot() }
                if (state == null) {
                    PlaybackStore.clear()
                    lastSavedPositionMs = -1L
                } else {
                    PlaybackStore.saveState(state)
                    lastSavedPositionMs = state.positionMs
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist playback state", e)
            }
        }
    }

    suspend fun restore() {
        val stored = PlaybackStore.loadState() ?: return
        val audioRepository = AudioRepository.getInstance(context)

        val currentId = stored.queueIds.getOrNull(stored.currentIndex)
        val audioFiles = audioRepository.getAudioByIds(stored.queueIds)
        if (audioFiles.isEmpty()) {
            if (currentId != null) {
                SettingsStore.setLocked(false)
            }
            return
        }

        val mediaItems = audioFiles.map { createMediaItem(it, stored.queueSource) }
        val targetIndex =
            if (currentId != null) {
                audioFiles.indexOfFirst { it.id == currentId }
            } else {
                -1
            }

        if (currentId != null && targetIndex < 0) {
            SettingsStore.setLocked(false)
            Log.w(TAG, "Current audio file not found in database, cannot restore playback state")
        }

        val safeIndex =
            if (targetIndex >= 0) targetIndex
            else stored.currentIndex.coerceIn(0, mediaItems.lastIndex)

        withContext(Dispatchers.Main) {
            if (player.mediaItemCount > 0) return@withContext
            player.shuffleModeEnabled = stored.shuffleEnabled
            player.repeatMode = stored.repeatMode
            fadingPlayer.setMediaItems(mediaItems, safeIndex, stored.positionMs)
            player.playWhenReady = stored.wasPlaying
            player.prepare()
        }
    }

    fun snapshot(): PlaybackStore.PersistedPlaybackState? {
        val queueIds: MutableList<Long> = mutableListOf()
        for (i in 0 until player.mediaItemCount) {
            val mediaId = player.getMediaItemAt(i).mediaId
            val id = mediaId.toLongOrNull() ?: continue
            queueIds.add(id)
        }
        if (queueIds.isEmpty()) return null
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val queueSource =
            player.currentMediaItem?.mediaMetadata?.extras?.getString(QUEUE_SOURCE_KEY)?.takeIf {
                it.isNotBlank()
            }
        return PlaybackStore.PersistedPlaybackState(
            queueIds = queueIds,
            currentIndex = currentIndex,
            positionMs = player.currentPosition,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queueSource = queueSource,
            wasPlaying = player.playWhenReady,
        )
    }
}
