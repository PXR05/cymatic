package com.pxr.cymatic

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@UnstableApi
class PlaybackService : MediaLibraryService() {

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
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    persistPlaybackState()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
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
                Log.d("PlaybackService", "EQ settings changed - enabled: $enabled, selected preset: $selectedName")
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
            Log.e("PlaybackService", "Failed to build persisted state on destroy", e)
            null
        }
        serviceScope.launch {
            if (state != null) PlaybackStore.saveState(state)
        }
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
            Log.d("PlaybackService", "Applying EQ preset '${preset.name}' at ${sampleRate}Hz")
            eqAudioProcessor.updateBands(preset.preamp, preset.bands, sampleRate)
        }

        withContext(Dispatchers.Main) {
            val offloadMode = if (enabled) {
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            } else {
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
            }
            val audioOffloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(offloadMode)
                .setIsGaplessSupportRequired(true)
                .build()
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(audioOffloadPreferences)
                .build()
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
                Log.e("PlaybackService", "Failed to persist playback state", e)
            }
        }
    }

    private suspend fun restorePlaybackState() {
        val stored = PlaybackStore.loadState() ?: return
        val audioRepository = AudioRepository.getInstance(this)

        val currentId = stored.queueIds.getOrNull(stored.currentIndex)
        if (currentId != null) {
            val currentFile = audioRepository.getAudioByIds(listOf(currentId)).firstOrNull()
            if (currentFile != null) {
                val currentMediaItem = createMediaItem(currentFile, stored.queueSource)
                withContext(Dispatchers.Main) {
                    player.shuffleModeEnabled = stored.shuffleEnabled
                    player.repeatMode = stored.repeatMode
                    player.setMediaItem(currentMediaItem, stored.positionMs)
                    player.playWhenReady = stored.wasPlaying
                    player.prepare()
                }
            } else {
                SettingsStore.setLocked(false)
                Log.w("PlaybackService", "Current audio file not found in database, cannot restore playback state")
            }
        }

        val audioFiles = audioRepository.getAudioByIds(stored.queueIds)
        if (audioFiles.isEmpty()) return

        val mediaItems = audioFiles.map { createMediaItem(it, stored.queueSource) }
        val safeIndex = stored.currentIndex.coerceIn(0, mediaItems.lastIndex)

        withContext(Dispatchers.Main) {
            player.setMediaItems(mediaItems, safeIndex, player.currentPosition)
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
}
