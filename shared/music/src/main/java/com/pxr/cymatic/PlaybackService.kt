package com.pxr.cymatic

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.pxr.cymatic.audio.DeviceVolumeMemory
import com.pxr.cymatic.audio.EqAudioProcessor
import com.pxr.cymatic.audio.usb.UsbExtractorsFactory
import com.pxr.cymatic.audio.usb.UsbPlaybackCoordinator
import com.pxr.cymatic.audio.usb.UsbRenderersFactory
import com.pxr.cymatic.auto.AutoMediaLibraryCallback
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.model.selectedOrDefault
import com.pxr.cymatic.data.store.EqSettingsStore
import com.pxr.cymatic.data.store.PlaybackStore
import com.pxr.cymatic.data.store.UsbPlaybackSettings
import com.pxr.cymatic.design.R as DesignR
import com.pxr.cymatic.music.R
import com.pxr.cymatic.playback.FadingPlayer
import com.pxr.cymatic.playback.OutputInfoState
import com.pxr.cymatic.playback.PlaybackDeviceTracker
import com.pxr.cymatic.playback.PlaybackOutputMonitor
import com.pxr.cymatic.playback.PlaybackStateLogger
import com.pxr.cymatic.playback.PlaybackStatePersistence
import com.pxr.cymatic.playback.PlaybackTechnicalMetadata
import com.pxr.cymatic.playback.SquareArtworkBitmapLoader
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    private lateinit var deviceTracker: PlaybackDeviceTracker
    private lateinit var persistence: PlaybackStatePersistence
    private lateinit var logger: PlaybackStateLogger
    private lateinit var usbPlayback: UsbPlaybackCoordinator

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_name)
                .build()
                .apply { setSmallIcon(DesignR.mipmap.ic_launcher) }
        )

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

        player =
            ExoPlayer.Builder(this)
                .setReleaseTimeoutMs(3000L)
                .setRenderersFactory(UsbRenderersFactory(this, eqAudioProcessor))
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(this, UsbExtractorsFactory())
                        .setDataSourceFactory(
                            com.pxr.cymatic.sync.RemoteLibraryDataSource.Factory(this)
                        )
                )
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

        fadingPlayer = FadingPlayer(player, serviceScope)
        persistence = PlaybackStatePersistence(this, player, fadingPlayer, serviceScope)
        logger = PlaybackStateLogger(player)
        deviceTracker = PlaybackDeviceTracker(audioManager, player, serviceScope)
        usbPlayback = UsbPlaybackCoordinator(this, player, fadingPlayer, audioAttributes)
        serviceScope.launch(Dispatchers.Main) {
            PlaybackOutputMonitor(
                    this@PlaybackService,
                    player,
                    eqAudioProcessor,
                    usbPlayback,
                    fadingPlayer,
                )
                .observe()
        }

        val audioRepository = AudioRepository.getInstance(this)
        player.addListener(PlaybackTechnicalMetadata(player, audioRepository, serviceScope))
        val playlistRepository = PlaylistRepository.getInstance(this)
        val libraryCallback =
            AutoMediaLibraryCallback(audioRepository, playlistRepository, serviceScope)

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                requireNotNull(packageManager.getLaunchIntentForPackage(packageName)) {
                        "The host app must declare a launch activity"
                    }
                    .apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        mediaLibrarySession =
            MediaLibrarySession.Builder(this, fadingPlayer, libraryCallback)
                .setId("audio_session")
                .setSessionActivity(pendingIntent)
                .setBitmapLoader(CacheBitmapLoader(SquareArtworkBitmapLoader(this)))
                .setCustomLayout(buildLockScreenLayout())
                .build()

        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    refreshCurrentLibraryMetadata()
                    logger.log(
                        "media item transition: ${mediaItem?.mediaId}, reason=${
                            logger.transitionReasonName(
                                reason
                            )
                        }"
                    )
                    persistence.save()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    logger.log("isPlaying changed: $isPlaying")
                    persistence.save()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    logger.log("playback state changed: ${logger.playbackStateName(playbackState)}")
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    logger.log(
                        "playWhenReady changed: $playWhenReady, reason=${
                            logger.playWhenReadyReasonName(
                                reason
                            )
                        }"
                    )
                    if (
                        !playWhenReady &&
                            reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                    ) {
                        OutputInfoState.event(
                            "Playback auto-paused: ${logger.playWhenReadyReasonName(reason)}"
                        )
                    }
                }

                override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                    logger.log(
                        "playback suppression changed: ${
                            logger.suppressionReasonName(
                                playbackSuppressionReason
                            )
                        }",
                        warn = playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                    )
                    if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                        OutputInfoState.event(
                            "Playback suppressed: ${logger.suppressionReasonName(playbackSuppressionReason)}"
                        )
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(
                        TAG,
                        "Player error: code=${error.errorCodeName}, message=${error.message}",
                        error,
                    )
                    logger.log("player error state")
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    val names = buildList {
                        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED))
                            add("PLAYBACK_STATE")
                        if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED))
                            add("PLAY_WHEN_READY")
                        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) add("IS_PLAYING")
                        if (events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED))
                            add("SUPPRESSION")
                        if (events.contains(Player.EVENT_PLAYER_ERROR)) add("PLAYER_ERROR")
                        if (events.contains(Player.EVENT_POSITION_DISCONTINUITY))
                            add("POSITION_DISCONTINUITY")
                        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION))
                            add("MEDIA_ITEM_TRANSITION")
                        if (events.contains(Player.EVENT_TIMELINE_CHANGED)) add("TIMELINE")
                        if (events.contains(Player.EVENT_TRACKS_CHANGED)) add("TRACKS")
                        if (events.contains(Player.EVENT_AUDIO_ATTRIBUTES_CHANGED))
                            add("AUDIO_ATTRIBUTES")
                        if (events.contains(Player.EVENT_DEVICE_VOLUME_CHANGED))
                            add("DEVICE_VOLUME")
                        if (events.contains(Player.EVENT_VOLUME_CHANGED)) add("VOLUME")
                    }
                        .joinToString(", ")
                        .ifBlank { "unlisted" }
                    logger.log("events: $names")
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    persistence.save()
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    persistence.save()
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                        persistence.save()
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    logger.log("audio session changed: $audioSessionId")
                    serviceScope.launch { applyCurrentEqSettings() }
                }
            }
        )

        usbPlayback.start(false)
        serviceScope.launch { initializePlayback() }

        serviceScope.launch {
            while (isActive) {
                delay(20_000L)
                val shouldSave =
                    withContext(Dispatchers.Main) {
                        player.isPlaying &&
                            abs(player.currentPosition - persistence.lastSavedPositionMs) >= 20_000L
                    }
                if (shouldSave) {
                    persistence.save()
                }
            }
        }

        serviceScope.launch {
            combine(
                    EqSettingsStore.eqGlobalEnabledFlow,
                    EqSettingsStore.eqPresetsFlow,
                    EqSettingsStore.effectiveEqSelectedPresetFlow,
                ) { enabled, presets, selectedName ->
                    Triple(enabled, presets, selectedName)
                }
                .distinctUntilChanged()
                .collect { (enabled, presets, selectedName) ->
                    Log.d(
                        TAG,
                        "EQ settings changed - enabled: $enabled, selected preset: $selectedName",
                    )
                    applyCurrentEqSettings(enabled, presets, selectedName)
                }
        }

        deviceTracker.start()
    }

    private suspend fun initializePlayback() {
        UsbPlaybackSettings.dsdModesFlow.first()
        val directUsbEnabled = UsbPlaybackSettings.enabledFlow.first()
        withContext(Dispatchers.Main) {
            usbPlayback.setEnabled(directUsbEnabled)
        }
        serviceScope.launch {
            DeviceVolumeMemory(audioManager).monitor()
        }
        serviceScope.launch(Dispatchers.Main) {
            UsbPlaybackSettings.enabledFlow.collect { usbPlayback.setEnabled(it) }
        }
        serviceScope.launch(Dispatchers.Main) {
            UsbPlaybackSettings.dsdModesFlow.drop(1).collect { usbPlayback.refreshDsdOutput() }
        }
        persistence.restore()
        serviceScope.launch {
            com.pxr.cymatic.data.media.LibraryChanges.version.collect {
                refreshCurrentLibraryMetadata()
            }
        }
    }

    private fun refreshCurrentLibraryMetadata() {
        serviceScope.launch {
            val id =
                withContext(Dispatchers.Main) { player.currentMediaItem?.mediaId?.toLongOrNull() }
                    ?: return@launch
            val metadata =
                AudioRepository.getInstance(this@PlaybackService)
                    .getAudioByIds(listOf(id))
                    .firstOrNull()
                    ?.metadata ?: return@launch
            withContext(Dispatchers.Main) {
                val item =
                    player.currentMediaItem?.takeIf { it.mediaId == id.toString() }
                        ?: return@withContext
                val known = item.mediaMetadata.extras
                val bitRate =
                    metadata.bitRate?.takeIf { it > 0L && (known?.getLong("bit_rate") ?: 0L) <= 0L }
                val sampleRate =
                    metadata.sampleRate?.takeIf {
                        it > 0L && (known?.getLong("sample_rate") ?: 0L) <= 0L
                    }
                val duration =
                    metadata.duration?.takeIf {
                        it > 0L && (known?.getLong("duration") ?: 0L) <= 0L
                    }
                val artwork = metadata.artworkUri ?: item.mediaMetadata.artworkUri
                if (
                    bitRate == null &&
                        sampleRate == null &&
                        duration == null &&
                        artwork == item.mediaMetadata.artworkUri
                )
                    return@withContext
                val extras = Bundle(item.mediaMetadata.extras ?: Bundle())
                bitRate?.let { extras.putLong("bit_rate", it) }
                sampleRate?.let { extras.putLong("sample_rate", it) }
                duration?.let { extras.putLong("duration", it) }
                val updated =
                    item.mediaMetadata.buildUpon().setExtras(extras).setArtworkUri(artwork).build()
                if (updated != item.mediaMetadata) {
                    player.replaceMediaItem(
                        player.currentMediaItemIndex,
                        item.buildUpon().setMediaMetadata(updated).build(),
                    )
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    private fun buildLockScreenLayout(): List<CommandButton> {
        val shuffle =
            CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                .setDisplayName("Shuffle")
                .setIconResId(DesignR.drawable.ic_pixel_shuffle)
                .build()

        val repeat =
            CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE)
                .setDisplayName("Repeat")
                .setIconResId(DesignR.drawable.ic_pixel_repeat)
                .build()

        return listOf(shuffle, repeat)
    }

    override fun onDestroy() {
        val state =
            try {
                persistence.snapshot()
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
        usbPlayback.close()
        deviceTracker.close()
        mediaLibrarySession.release()
        fadingPlayer.release()
        OutputInfoState.clear()
        super.onDestroy()
    }

    private suspend fun applyCurrentEqSettings(
        enabled: Boolean = EqSettingsStore.currentEqGlobalEnabled,
        presets: List<EqPreset> = EqSettingsStore.currentEqPresets,
        selectedName: String = EqSettingsStore.currentEqSelectedPreset,
    ) {
        if (!enabled) {
            eqAudioProcessor.disable()
        } else {
            val preset = presets.selectedOrDefault(selectedName)
            val sampleRate =
                withContext(Dispatchers.Main) {
                    player.audioFormat?.sampleRate ?: 44100
                }
            Log.d(TAG, "Applying EQ preset '${preset.name}' at ${sampleRate}Hz")
            eqAudioProcessor.updateBands(preset.preamp, preset.bands, sampleRate)
        }

        withContext(Dispatchers.Main) {
            val audioOffloadPreferences =
                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    )
                    .setIsGaplessSupportRequired(true)
                    .build()
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setAudioOffloadPreferences(audioOffloadPreferences)
                    .build()
            Log.d(TAG, "Audio offload disabled; eqEnabled=$enabled")
        }
    }
}
