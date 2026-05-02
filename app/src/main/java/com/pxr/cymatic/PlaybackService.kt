package com.pxr.cymatic

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pxr.cymatic.data.media.AudioStoreDatabase
import com.pxr.cymatic.data.store.PlaybackStore
import com.pxr.cymatic.playback.QUEUE_SOURCE_KEY
import com.pxr.cymatic.playback.createMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : MediaSessionService() {
    lateinit var player: ExoPlayer
        private set
    private lateinit var mediaSession: MediaSession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player)
            .setId("audio_session")
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                persistPlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                persistPlaybackState()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                persistPlaybackState()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                persistPlaybackState()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                persistPlaybackState()
            }
        })

        serviceScope.launch {
            restorePlaybackState()
        }

        serviceScope.launch {
            while (isActive) {
                persistPlaybackState()
                delay(5_000L)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        val state = try {
            buildPersistedState()
        } catch (e: Exception) {
            Log.e("PlaybackService", "Failed to build persisted state on destroy", e)
            null
        }
        serviceScope.launch {
            if (state != null) {
                PlaybackStore.saveState(state)
            }
        }
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun persistPlaybackState() {
        serviceScope.launch {
            try {
                val state = withContext(Dispatchers.Main) {
                    buildPersistedState()
                }
                if (state == null) {
                    PlaybackStore.clear()
                } else {
                    PlaybackStore.saveState(state)
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Failed to persist playback state", e)
            }
        }
    }

    private suspend fun restorePlaybackState() {
        val stored = PlaybackStore.loadState() ?: return
        val audioStore = AudioStoreDatabase.getInstance(this)

        val currentId = stored.queueIds.getOrNull(stored.currentIndex)
        if (currentId != null) {
            val currentFile = audioStore.getAudioByIds(listOf(currentId)).firstOrNull()
            if (currentFile != null) {
                val currentMediaItem = createMediaItem(currentFile, stored.queueSource)
                withContext(Dispatchers.Main) {
                    player.shuffleModeEnabled = stored.shuffleEnabled
                    player.repeatMode = stored.repeatMode
                    player.setMediaItem(currentMediaItem, stored.positionMs)
                    player.playWhenReady = stored.wasPlaying
                    player.prepare()
                }
            }
        }

        val audioFiles = audioStore.getAudioByIds(stored.queueIds)
        if (audioFiles.isEmpty()) return

        val mediaItems = audioFiles.map { audioFile ->
            createMediaItem(audioFile, stored.queueSource)
        }
        val safeIndex = stored.currentIndex.coerceIn(0, mediaItems.lastIndex)

        withContext(Dispatchers.Main) {
            player.setMediaItems(mediaItems, safeIndex, player.currentPosition)
        }
    }

    private fun buildPersistedState(): PlaybackStore.PersistedPlaybackState? {
        val queueIds: MutableList<Long> = mutableListOf()
        for (i in 0 until player.mediaItemCount) {
            val mediaId = player.getMediaItemAt(i).mediaId ?: continue
            val id = mediaId.toLongOrNull() ?: continue
            queueIds.add(id)
        }
        if (queueIds.isEmpty()) return null
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val queueSource = player.currentMediaItem?.mediaMetadata?.extras
            ?.getString(QUEUE_SOURCE_KEY)
            ?.takeIf { it.isNotBlank() }
        return PlaybackStore.PersistedPlaybackState(
            queueIds = queueIds,
            currentIndex = currentIndex,
            positionMs = player.currentPosition,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queueSource = queueSource,
            wasPlaying = player.playWhenReady
        )
    }
}
