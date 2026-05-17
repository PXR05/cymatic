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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
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
import com.pxr.cymatic.playback.QUEUE_SOURCE_KEY
import com.pxr.cymatic.playback.createMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

@UnstableApi
class PlaybackService : MediaLibraryService() {
//    private var currentUsbSink: UsbAudioSink? = null

    lateinit var player: ExoPlayer
        private set
    private lateinit var mediaLibrarySession: MediaLibrarySession

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eqAudioProcessor = EqAudioProcessor()
    private lateinit var audioManager: AudioManager
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private lateinit var libraryCallback: AutoMediaLibraryCallback
    private lateinit var sessionActivityPendingIntent: PendingIntent
    private val playerRebuildMutex = Mutex()
    private var activeRenderersMode: RenderersMode = RenderersMode.DEFAULT
    private var pendingExclusiveUntilPlay: Boolean = false

    private enum class RenderersMode {
        USB,
        EQ,
        DEFAULT
    }

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_name)
                .build()
        )

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val audioRepository = AudioRepository.getInstance(this)
        val playlistRepository = PlaylistRepository.getInstance(this)
        libraryCallback =
            AutoMediaLibraryCallback(audioRepository, playlistRepository, serviceScope)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val storedState = try {
            runBlocking(Dispatchers.IO) { PlaybackStore.loadState() }
        } catch (e: Exception) {
            Log.w("PlaybackService", "Failed to read playback state on startup", e)
            null
        }
        pendingExclusiveUntilPlay =
            SettingsStore.currentUsbExclusive && storedState?.wasPlaying != true
        activeRenderersMode = effectiveRenderersMode(
            SettingsStore.currentUsbExclusive,
            SettingsStore.currentEqGlobalEnabled
        )
        player = buildPlayerForMode(activeRenderersMode)
        mediaLibrarySession = buildMediaLibrarySession(player)
        setupPlayerListeners(player)

        serviceScope.launch {
            SettingsStore.usbExclusiveFlow
                .combine(SettingsStore.eqGlobalEnabledFlow) { usbExclusive, eqEnabled ->
                    usbExclusive to effectiveRenderersMode(usbExclusive, eqEnabled)
                }
                .distinctUntilChanged()
                .collect { (usbExclusive, mode) ->
                    val isPlaying = withContext(Dispatchers.Main) { player.isPlaying }
                    if (usbExclusive && !isPlaying) {
                        pendingExclusiveUntilPlay = true
                    } else if (!usbExclusive) {
                        pendingExclusiveUntilPlay = false
                    }
                    val effectiveMode = effectiveRenderersMode(
                        usbExclusive,
                        SettingsStore.currentEqGlobalEnabled
                    )
                    if (effectiveMode != activeRenderersMode) {
                        rebuildPlayerForMode(effectiveMode)
                    }
                }
        }

        serviceScope.launch {
            restorePlaybackState()
        }

        serviceScope.launch {
            while (isActive) {
                persistPlaybackState()
                delay(5_000L)
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
                Log.d(
                    "PlaybackService",
                    "EQ settings changed - enabled: $enabled, selected preset: $selectedName"
                )
                if (!enabled) {
                    eqAudioProcessor.disable()
                } else {
                    val preset = presets.firstOrNull { it.name == selectedName }
                        ?: presets.firstOrNull()
                        ?: EqPreset.defaultPreset()
                    val sampleRate = withContext(Dispatchers.Main) {
                        player.audioFormat?.sampleRate ?: 44100
                    }
                    eqAudioProcessor.updateBands(preset.preamp, preset.bands, sampleRate)
                }
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
            if (state != null) {
                PlaybackStore.saveState(state)
            }
        }
        audioDeviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        mediaLibrarySession.release()
        player.release()
        super.onDestroy()
    }

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

//    private fun createUsbRenderersFactory(): DefaultRenderersFactory {
//        val factory = object : DefaultRenderersFactory(this) {
//            override fun buildAudioSink(
//                context: Context,
//                enableFloatOutput: Boolean,
//                enableOffload: Boolean
//            ): AudioSink {
//                val hasLibFlac = try {
//                    Class.forName("androidx.media3.decoder.flac.LibflacAudioRenderer")
//                    true
//                } catch (_: ClassNotFoundException) {
//                    false
//                }
//
//                val useFloat = !hasLibFlac
//
//                val delegate = DefaultAudioSink.Builder(context)
//                    .setEnableFloatOutput(useFloat)
//                    .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
//                    .build()
//
//                val config = UsbAudioSinkConfig(
//                    bitPerfectEnabled = true,
//                    forceRouteToSpeaker = false
//                )
//
//                return UsbAudioSink(delegate, context, config).also {
//                    currentUsbSink = it
//                }
//            }
//        }
//
//        factory.setExtensionRendererMode(
//            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
//        )
//
//        return factory
//    }

    private fun registerAudioDeviceTracking() {
        fun updateActiveDevice() {
            serviceScope.launch {
                SettingsStore.setActiveAudioDevice(resolveActiveOutput(audioManager).key)
            }
        }

        audioDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                updateActiveDevice()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                updateActiveDevice()
            }
        }.also { callback ->
            audioManager.registerAudioDeviceCallback(callback, null)
        }
        updateActiveDevice()
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
                Log.w(
                    "PlaybackService",
                    "Current audio file not found in database, cannot restore playback state"
                )
            }
        }

        val audioFiles = audioRepository.getAudioByIds(stored.queueIds)
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
            wasPlaying = player.isPlaying
        )
    }

    private fun resolveRenderersMode(usbExclusive: Boolean, eqEnabled: Boolean): RenderersMode {
        return when {
            usbExclusive -> RenderersMode.USB
            eqEnabled -> RenderersMode.EQ
            else -> RenderersMode.DEFAULT
        }
    }

    private fun effectiveRenderersMode(usbExclusive: Boolean, eqEnabled: Boolean): RenderersMode {
        if (usbExclusive && pendingExclusiveUntilPlay) {
            return if (eqEnabled) RenderersMode.EQ else RenderersMode.DEFAULT
        }
        return resolveRenderersMode(usbExclusive, eqEnabled)
    }

    private fun maybeActivateExclusiveOnPlay() {
        if (!pendingExclusiveUntilPlay) return
        if (!SettingsStore.currentUsbExclusive) return
        if (activeRenderersMode == RenderersMode.USB) return
        pendingExclusiveUntilPlay = false
        serviceScope.launch {
            rebuildPlayerForMode(RenderersMode.USB)
        }
    }

    private fun buildPlayerForMode(mode: RenderersMode): ExoPlayer {
//        if (mode != RenderersMode.USB) {
//            currentUsbSink = null
//        }
        val renderersFactory = when (mode) {
//            RenderersMode.USB -> createUsbRenderersFactory()
            RenderersMode.EQ -> createEqRenderersFactory()
            RenderersMode.DEFAULT -> DefaultRenderersFactory(this)
            else -> DefaultRenderersFactory(this)
        }
        val loadControl = when (mode) {
//            RenderersMode.USB -> UsbAudioSink.wrapLoadControl(
//                DefaultLoadControl.Builder()
//                    .setBufferDurationsMs(5000, 15000, 2000, 3000)
//                    .build()
//            ) { currentUsbSink?.isNativeEngineActive == true }

            else -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(5000, 15000, 2000, 3000)
                .build()
        }
        return ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .build()
//            .also { newPlayer ->
//                if (mode == RenderersMode.USB) {
//                    currentUsbSink?.attachToPlayer(newPlayer)
//                }
//            }
    }

    private fun buildMediaLibrarySession(player: ExoPlayer): MediaLibrarySession {
        return MediaLibrarySession.Builder(this, player, libraryCallback)
            .setId("audio_session")
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    private fun setupPlayerListeners(target: ExoPlayer) {
        target.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                persistPlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                persistPlaybackState()
                if (isPlaying) {
                    maybeActivateExclusiveOnPlay()
                }
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
    }

    private data class PlayerSnapshot(
        val mediaItems: List<MediaItem>,
        val currentIndex: Int,
        val positionMs: Long,
        val wasPlaying: Boolean,
        val shuffleEnabled: Boolean,
        val repeatMode: Int
    )

    private fun captureSnapshot(target: ExoPlayer): PlayerSnapshot? {
        if (target.mediaItemCount == 0) return null
        val items = (0 until target.mediaItemCount).map { index ->
            target.getMediaItemAt(index)
        }
        return PlayerSnapshot(
            mediaItems = items,
            currentIndex = target.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = target.currentPosition,
            wasPlaying = target.isPlaying,
            shuffleEnabled = target.shuffleModeEnabled,
            repeatMode = target.repeatMode
        )
    }

    private suspend fun rebuildPlayerForMode(mode: RenderersMode) {
        playerRebuildMutex.lock()
        try {
            val snapshot = withContext(Dispatchers.Main) {
                captureSnapshot(player)
            }
            val oldPlayer = player
            val oldSession = mediaLibrarySession

            val newPlayer = buildPlayerForMode(mode)
            setupPlayerListeners(newPlayer)

            val sessionUpdated = withContext(Dispatchers.Main) {
                trySetSessionPlayer(oldSession, newPlayer)
            }

            if (sessionUpdated) {
                withContext(Dispatchers.Main) {
                    snapshot?.let {
                        newPlayer.shuffleModeEnabled = it.shuffleEnabled
                        newPlayer.repeatMode = it.repeatMode
                        newPlayer.setMediaItems(it.mediaItems, it.currentIndex, it.positionMs)
                        newPlayer.prepare()
                        if (it.wasPlaying) {
                            newPlayer.play()
                        } else {
                            newPlayer.pause()
                        }
                    }
                    oldPlayer.release()
                }

                player = newPlayer
                activeRenderersMode = mode
                return
            }

            withContext(Dispatchers.Main) {
                oldSession.release()
                oldPlayer.release()
            }

            val newSession = buildMediaLibrarySession(newPlayer)

            withContext(Dispatchers.Main) {
                snapshot?.let {
                    newPlayer.shuffleModeEnabled = it.shuffleEnabled
                    newPlayer.repeatMode = it.repeatMode
                    newPlayer.setMediaItems(it.mediaItems, it.currentIndex, it.positionMs)
                    newPlayer.prepare()
                    if (it.wasPlaying) {
                        newPlayer.play()
                    } else {
                        newPlayer.pause()
                    }
                }
            }

            mediaLibrarySession = newSession
            player = newPlayer
            activeRenderersMode = mode
        } finally {
            playerRebuildMutex.unlock()
        }
    }

    private fun trySetSessionPlayer(session: MediaLibrarySession, newPlayer: ExoPlayer): Boolean {
        return try {
            val method = session.javaClass.getMethod("setPlayer", Player::class.java)
            method.invoke(session, newPlayer)
            true
        } catch (_: Exception) {
            false
        }
    }
}

