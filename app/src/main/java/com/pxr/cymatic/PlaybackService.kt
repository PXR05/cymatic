package com.pxr.cymatic

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.pxr.cymatic.audio.EqAudioProcessor
import com.pxr.cymatic.audio.resolveActiveOutput
import com.pxr.cymatic.auto.AutoMediaLibraryCallback
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.store.PlaybackStore
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.playback.FadingPlayer
import com.pxr.cymatic.playback.QUEUE_SOURCE_KEY
import com.pxr.cymatic.playback.createMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.abs

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private companion object {
        const val TAG = "PlaybackService"
    }

    lateinit var player: ExoPlayer
        private set
    private lateinit var fadingPlayer: FadingPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eqAudioProcessor = EqAudioProcessor()
    private lateinit var audioManager: AudioManager
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var lastSavedPositionMs = -1L

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_name)
                .build()
                .apply { setSmallIcon(R.mipmap.ic_launcher) }
        )

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(createEqRenderersFactory())
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        fadingPlayer = FadingPlayer(player, serviceScope)

        val audioRepository = AudioRepository.getInstance(this)
        val playlistRepository = PlaylistRepository.getInstance(this)
        val libraryCallback = AutoMediaLibraryCallback(audioRepository, playlistRepository, serviceScope)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, fadingPlayer, libraryCallback)
            .setId("audio_session")
            .setSessionActivity(pendingIntent)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                logPlayerState("media item transition: ${mediaItem?.mediaId}, reason=${transitionReasonName(reason)}")
                persistPlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                logPlayerState("isPlaying changed: $isPlaying")
                persistPlaybackState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                logPlayerState("playback state changed: ${playbackStateName(playbackState)}")
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                logPlayerState("playWhenReady changed: $playWhenReady, reason=${playWhenReadyReasonName(reason)}")
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                logPlayerState(
                    "playback suppression changed: ${suppressionReasonName(playbackSuppressionReason)}",
                    warn = playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    TAG,
                    "Player error: code=${error.errorCodeName}, message=${error.message}",
                    error
                )
                logPlayerState("player error state")
            }

            override fun onEvents(player: Player, events: Player.Events) {
                val names = buildList {
                    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) add("PLAYBACK_STATE")
                    if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) add("PLAY_WHEN_READY")
                    if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) add("IS_PLAYING")
                    if (events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)) add("SUPPRESSION")
                    if (events.contains(Player.EVENT_PLAYER_ERROR)) add("PLAYER_ERROR")
                    if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) add("POSITION_DISCONTINUITY")
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) add("MEDIA_ITEM_TRANSITION")
                    if (events.contains(Player.EVENT_TIMELINE_CHANGED)) add("TIMELINE")
                    if (events.contains(Player.EVENT_TRACKS_CHANGED)) add("TRACKS")
                    if (events.contains(Player.EVENT_AUDIO_ATTRIBUTES_CHANGED)) add("AUDIO_ATTRIBUTES")
                    if (events.contains(Player.EVENT_DEVICE_VOLUME_CHANGED)) add("DEVICE_VOLUME")
                    if (events.contains(Player.EVENT_VOLUME_CHANGED)) add("VOLUME")
                }.joinToString(", ").ifBlank { "unlisted" }
                logPlayerState("events: $names")
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                persistPlaybackState()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                persistPlaybackState()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    persistPlaybackState()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                logPlayerState("audio session changed: $audioSessionId")
                serviceScope.launch { applyCurrentEqSettings() }
            }
        })

        serviceScope.launch { restorePlaybackState() }

        serviceScope.launch {
            while (isActive) {
                delay(20_000L)
                val shouldSave = withContext(Dispatchers.Main) {
                    player.isPlaying && abs(player.currentPosition - lastSavedPositionMs) >= 20_000L
                }
                if (shouldSave) {
                    persistPlaybackState()
                }
            }
        }

        serviceScope.launch {
            combine(
                SettingsStore.eqGlobalEnabledFlow,
                SettingsStore.eqPresetsFlow,
                SettingsStore.effectiveEqSelectedPresetFlow
            ) { enabled, presets, selectedName ->
                Triple(enabled, presets, selectedName)
            }.collect { (enabled, presets, selectedName) ->
                Log.d(TAG, "EQ settings changed - enabled: $enabled, selected preset: $selectedName")
                applyCurrentEqSettings(enabled, presets, selectedName)
            }
        }

        registerAudioDeviceTracking()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        val state = try {
            buildPersistedState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build persisted state on destroy", e)
            null
        }
        if (state != null) {
            try {
                runBlocking {
                    withTimeout(2000L) {
                        PlaybackStore.saveState(state)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save state on destroy", e)
            }
        }
        serviceScope.cancel()
        audioDeviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        mediaLibrarySession.release()
        fadingPlayer.release()
        super.onDestroy()
    }

    // --- EQ ---

    private fun createEqRenderersFactory(): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableOffload: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true)
                    .setAudioProcessors(arrayOf(eqAudioProcessor))
                    .build()
            }
        }
    }

    private suspend fun applyCurrentEqSettings(
        enabled: Boolean = SettingsStore.currentEqGlobalEnabled,
        presets: List<EqPreset> = SettingsStore.currentEqPresets,
        selectedName: String = SettingsStore.currentEqSelectedPreset
    ) {
        if (!enabled) {
            eqAudioProcessor.disable()
        } else {
            val preset = presets.firstOrNull { it.name == selectedName }
                ?: presets.firstOrNull()
                ?: EqPreset.defaultPreset()
            val sampleRate = withContext(Dispatchers.Main) {
                player.audioFormat?.sampleRate ?: 44100
            }
            Log.d(TAG, "Applying EQ preset '${preset.name}' at ${sampleRate}Hz")
            eqAudioProcessor.updateBands(preset.preamp, preset.bands, sampleRate)
        }

        withContext(Dispatchers.Main) {
            val audioOffloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                )
                .setIsGaplessSupportRequired(true)
                .build()
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(audioOffloadPreferences)
                .build()
            Log.d(TAG, "Audio offload disabled; eqEnabled=$enabled")
        }
    }

    // --- Audio device tracking ---

    private fun registerAudioDeviceTracking() {
        fun updateActiveDevice() {
            serviceScope.launch {
                SettingsStore.setActiveAudioDevice(resolveActiveOutput(audioManager).key)
            }
        }

        audioDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                updateActiveDevice()
                val hasBluetooth = addedDevices.any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (hasBluetooth) {
                    serviceScope.launch {
                        if (SettingsStore.currentResumeOnBluetoothReconnect) {
                            delay(500L)
                            withContext(Dispatchers.Main) {
                                if (player.mediaItemCount > 0 && !player.isPlaying) {
                                    player.play()
                                }
                            }
                        }
                    }
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                updateActiveDevice()
            }
        }.also { audioManager.registerAudioDeviceCallback(it, null) }

        updateActiveDevice()
    }

    // --- Playback state persistence ---

    private fun persistPlaybackState() {
        serviceScope.launch {
            try {
                val state = withContext(Dispatchers.Main) { buildPersistedState() }
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

    private suspend fun restorePlaybackState() {
        val stored = PlaybackStore.loadState() ?: return
        val audioRepository = AudioRepository.getInstance(this)

        val currentId = stored.queueIds.getOrNull(stored.currentIndex)
        val audioFiles = audioRepository.getAudioByIds(stored.queueIds)
        if (audioFiles.isEmpty()) {
            if (currentId != null) {
                SettingsStore.setLocked(false)
            }
            return
        }

        val mediaItems = audioFiles.map { createMediaItem(it, stored.queueSource) }
        val targetIndex = if (currentId != null) {
            audioFiles.indexOfFirst { it.id == currentId }
        } else {
            -1
        }

        if (currentId != null && targetIndex < 0) {
            SettingsStore.setLocked(false)
            Log.w(TAG, "Current audio file not found in database, cannot restore playback state")
        }

        val safeIndex = if (targetIndex >= 0) targetIndex else stored.currentIndex.coerceIn(0, mediaItems.lastIndex)

        withContext(Dispatchers.Main) {
            player.shuffleModeEnabled = stored.shuffleEnabled
            player.repeatMode = stored.repeatMode
            player.setMediaItems(mediaItems, safeIndex, stored.positionMs)
            player.playWhenReady = stored.wasPlaying
            player.prepare()
        }
    }

    private fun buildPersistedState(): PlaybackStore.PersistedPlaybackState? {
        val queueIds: MutableList<Long> = mutableListOf()
        for (i in 0 until player.mediaItemCount) {
            val mediaId = player.getMediaItemAt(i).mediaId
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

    private fun logPlayerState(message: String, warn: Boolean = false) {
        val stateMessage = "$message | " +
                "state=${playbackStateName(player.playbackState)}, " +
                "playWhenReady=${player.playWhenReady}, " +
                "isPlaying=${player.isPlaying}, " +
                "suppression=${suppressionReasonName(player.playbackSuppressionReason)}, " +
                "position=${player.currentPosition}, " +
                "buffered=${player.bufferedPosition}, " +
                "mediaId=${player.currentMediaItem?.mediaId}"
        if (warn) {
            Log.w(TAG, stateMessage)
        }
    }

    private fun playbackStateName(state: Int): String {
        return when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }
    }

    private fun playWhenReadyReasonName(reason: Int): String {
        return when (reason) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
            Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
            else -> "UNKNOWN($reason)"
        }
    }

    private fun suppressionReasonName(reason: Int): String {
        return when (reason) {
            Player.PLAYBACK_SUPPRESSION_REASON_NONE -> "NONE"
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> "TRANSIENT_AUDIO_FOCUS_LOSS"
            Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT -> "UNSUITABLE_AUDIO_OUTPUT"
            else -> "UNKNOWN($reason)"
        }
    }

    private fun transitionReasonName(reason: Int): String {
        return when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
            else -> "UNKNOWN($reason)"
        }
    }
}
