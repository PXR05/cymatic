package com.pxr.cymatic.playback

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import com.pxr.cymatic.audio.EqAudioProcessor
import com.pxr.cymatic.audio.alac.integerAlacMetadata
import com.pxr.cymatic.audio.dsd.dsfHeader
import com.pxr.cymatic.audio.flac.integerFlacMetadata
import com.pxr.cymatic.audio.resolveActiveOutput
import com.pxr.cymatic.audio.usb.UsbDsdSupport
import com.pxr.cymatic.audio.usb.UsbPlaybackCoordinator
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.audio.usb.UsbVolumeState
import com.pxr.cymatic.data.model.EqPreset
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

@UnstableApi
internal class PlaybackOutputMonitor(
    private val context: Context,
    private val player: ExoPlayer,
    private val equalizer: EqAudioProcessor,
    private val routing: UsbPlaybackCoordinator,
    private val fading: FadingPlayer,
) : AnalyticsListener {
    private var decoderName: String? = null
    private var decoderActive = false
    private var decoderInitializationMs: Long? = null
    private var reuse: DecoderReuseEvaluation? = null
    private var counters: DecoderCounters? = null
    private var audioTrack: AudioSink.AudioTrackConfig? = null
    private var underruns = 0L
    private var underrunDetails = JSONObject()
    private var lastSource: Format? = null
    private val audio = context.getSystemService(AudioManager::class.java)
    private val version = context.packageManager.getPackageInfo(context.packageName, 0)
    private var platformCodec = JSONObject()

    override fun onAudioEnabled(
        eventTime: AnalyticsListener.EventTime,
        decoderCounters: DecoderCounters,
    ) {
        counters = decoderCounters
        OutputInfoState.event("Audio renderer enabled")
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        this.decoderName = decoderName
        decoderActive = true
        decoderInitializationMs = initializationDurationMs
        platformCodec = codecInfo(decoderName, lastSource?.sampleMimeType)
        OutputInfoState.event("Decoder initialized: $decoderName (${initializationDurationMs} ms)")
    }

    override fun onAudioDecoderReleased(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
    ) {
        decoderActive = false
        OutputInfoState.event("Decoder released: $decoderName")
    }

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        lastSource = format
        reuse = decoderReuseEvaluation
        decoderName?.let { platformCodec = codecInfo(it, format.sampleMimeType) }
        OutputInfoState.event(
            "Renderer input: ${format.sampleMimeType}, ${format.sampleRate} Hz, ${format.channelCount} channels"
        )
    }

    override fun onAudioTrackInitialized(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig,
    ) {
        audioTrack = audioTrackConfig
        OutputInfoState.event(
            "AudioTrack initialized: ${audioTrackConfig.sampleRate} Hz / ${pcmEncodingName(audioTrackConfig.encoding)}"
        )
    }

    override fun onAudioTrackReleased(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig,
    ) {
        if (audioTrack === audioTrackConfig) audioTrack = null
        OutputInfoState.event("AudioTrack released")
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long,
    ) {
        underruns++
        underrunDetails =
            JSONObject()
                .put("bufferBytes", bufferSize)
                .put("bufferDurationMs", known(bufferSizeMs))
                .put("elapsedSinceFeedMs", elapsedSinceLastFeedMs)
        OutputInfoState.event(
            "AudioTrack underrun: buffer=$bufferSize bytes, last feed ${elapsedSinceLastFeedMs} ms ago"
        )
    }

    override fun onAudioSinkError(
        eventTime: AnalyticsListener.EventTime,
        audioSinkError: Exception,
    ) {
        error("Audio sink", audioSinkError)
    }

    override fun onAudioCodecError(
        eventTime: AnalyticsListener.EventTime,
        audioCodecError: Exception,
    ) {
        error("Audio codec", audioCodecError)
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        error("Player ${error.errorCodeName}", error)
    }

    override fun onMediaItemTransition(
        eventTime: AnalyticsListener.EventTime,
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        OutputInfoState.event("Track selected: ${mediaItem?.mediaMetadata?.title ?: "none"}")
    }

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        OutputInfoState.event("Playback state: ${stateName(state)}")
    }

    private fun error(stage: String, error: Throwable) {
        val causes =
            generateSequence(error) { it.cause }
                .joinToString(" → ") { "${it.javaClass.simpleName}: ${it.message}" }
        OutputInfoState.event("$stage: ${causes.take(4000)}")
    }

    suspend fun observe() {
        player.addAnalyticsListener(this)
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                if (OutputInfoState.observed) {
                    val report = runCatching {
                        buildReport()
                    }
                        .getOrElse {
                            listOf(
                                outputInfoSection(
                                    "Output info",
                                    JSONObject()
                                        .put("snapshot", "Temporarily unavailable: ${it.message}"),
                                )
                            )
                        }
                    OutputInfoState.publish(report)
                }
                delay(500L)
            }
        } finally {
            player.removeAnalyticsListener(this)
        }
    }

    private fun buildReport(): List<OutputInfoSection> {
        val item = player.currentMediaItem
        val source =
            player.audioFormat ?: lastSource.takeIf { player.playbackState != Player.STATE_IDLE }
        val sink = OutputInfoState.sinkSnapshot()
        val decoded = sink.optJSONObject("configuredFormat")
        val usb = sink.optJSONObject("usb")
        val layout = usb?.optJSONObject("outputLayout")
        val feedback = usb?.optJSONObject("feedback")
        val direct = UsbPlaybackState.routeToUsb
        val active = UsbPlaybackState.active.value
        val route = routing.diagnosticSnapshot()
        val policy = UsbDsdSupport.diagnosticSnapshot()
        val device = resolveActiveOutput(audio)
        val dsd = decoded?.optJSONObject("dsdProcessing")
        val dop = dsd?.optString("mode")?.contains("DoP") == true
        val dsf = source?.dsfHeader()
        val sourceDepth =
            source?.integerFlacMetadata()?.bitsPerSample
                ?: source?.integerAlacMetadata()?.bits
                ?: source?.pcmEncoding?.let(::encodingBits)
        val sourceDescription =
            source?.let {
                "${dsf?.let { info -> "DSF / ${info.label}" } ?: it.sampleMimeType?.substringAfterLast('/')?.removePrefix("x-")?.uppercase() ?: "Unknown"} · ${rate(it.sampleRate.toLong())} · ${channels(it.channelCount)}"
            } ?: "Not prepared"
        val decodedDescription =
            if (decoded != null && decoded.optInt("sampleRateHz", -1) > 0) {
                "${if(dop) "DoP" else "PCM"} · ${rate(decoded.optLong("sampleRateHz"))} · ${decoded.optString("pcmEncoding")} · ${channels(decoded.optInt("channels"))}"
            } else "Not configured"
        val outputRate =
            if (direct)
                usb?.optLong("confirmedClockRateHz", usb.optLong("selectedFixedRateHz", -1)) ?: -1
            else audioTrack?.sampleRate?.toLong() ?: -1
        val outputDescription =
            if (direct && layout != null) {
                "${if(dop) "DoP carrier" else "PCM"} · ${rate(outputRate)} · ${layout.optInt("validBits")} valid bits / ${layout.optInt("containerBytes") * 8}-bit container · ${channels(layout.optInt("channels"))}"
            } else
                audioTrack?.let {
                    "${rate(it.sampleRate.toLong())} · ${pcmEncodingName(it.encoding)} · ${channels(Integer.bitCount(it.channelConfig))}"
                } ?: "No active output configuration"
        val outputName =
            if (direct) usb?.optString("deviceName", "USB DAC") ?: "USB DAC" else device.label
        val processing =
            when {
                dsf != null && !dop -> "DSD converted to PCM"
                direct && dop -> "DSD payload sent through DoP; host EQ and software gain bypassed"
                direct -> "PCM passthrough after decoding; host EQ and software gain bypassed"
                else -> "Android PCM pipeline; system may mix or resample"
            }
        val overview =
            section(
                "Overview",
                "Track" to (item?.mediaMetadata?.title?.toString() ?: "No track selected"),
                "Playback" to
                    if (player.isPlaying) "Playing"
                    else if (player.playWhenReady) stateName(player.playbackState)
                    else "Paused / ${stateName(player.playbackState)}",
                "Route" to
                    if (direct) if (active) "Direct USB" else "Direct USB · not streaming"
                    else "Android audio",
                "Source" to sourceDescription,
                "Decoded" to decodedDescription,
                "Processing" to processing,
                "Output" to "$outputName · $outputDescription",
                "Status" to
                    UsbPlaybackState.status.value.takeIf { direct || UsbPlaybackState.enabled },
                "App version" to version.versionName,
            )

        val codec =
            section(
                "Format & decoder",
                "Source" to sourceDescription,
                "Source bit depth" to
                    if (dsf != null) "1-bit DSD"
                    else sourceDepth?.let { "$it bits" } ?: "Not reported",
                "Source bitrate (reported)" to
                    (source?.averageBitrate?.toLong()?.takeIf { it > 0 }
                            ?: item?.toAudioMetadata()?.bitRate)
                        ?.let { "${it / 1000} kbps" },
                "DSF bit order" to dsf?.let { if (it.lsbFirst) "LSB first" else "MSB first" },
                "Decoder" to (decoderName ?: "Not initialized / PCM may bypass a codec"),
                "Decoder state" to
                    if (decoderActive) "Active" else "Last initialized decoder is inactive",
                "Decoder implementation" to
                    platformCodec.optString("implementation", "Not reported"),
                "Decoder initialization" to decoderInitializationMs?.let { "$it ms" },
                "Decoded audio" to decodedDescription,
                "DSD handling" to dsd?.optString("mode"),
                "DSD conversion filter" to
                    if (dsf != null && !dop) "40 kHz low-pass FIR; 24-bit PCM output" else null,
                "Encoder delay / padding" to
                    source
                        ?.takeIf { it.encoderDelay > 0 || it.encoderPadding > 0 }
                        ?.let {
                            "${it.encoderDelay} / ${it.encoderPadding} frames, trimmed by the sink"
                        },
            )

        val selectedPreset =
            SettingsStore.currentEqDevicePresets[SettingsStore.currentActiveAudioDevice]
                ?: SettingsStore.currentEqSelectedPreset
        val preset =
            SettingsStore.currentEqPresets.firstOrNull { it.name == selectedPreset }
                ?: SettingsStore.currentEqPresets.firstOrNull()
                ?: EqPreset.defaultPreset()
        val eq = if (direct) null else equalizer.diagnosticSnapshot()
        val fade = fading.diagnosticSnapshot()
        val eqActive = eq?.optBoolean("configured") == true && eq.optBoolean("passThrough") == false
        val processingFields =
            mutableListOf<Pair<String, Any?>>(
                "Equalizer" to
                    if (direct) "Bypassed by direct USB"
                    else if (eqActive) "Applied"
                    else if (SettingsStore.currentEqGlobalEnabled) "Flat / not processing"
                    else "Off",
                "EQ preset" to
                    preset.name.takeIf { !direct && SettingsStore.currentEqGlobalEnabled },
                "EQ preamp" to
                    if (eqActive)
                        "${preset.preamp} dB requested; linear gain ${eq?.opt("preampLinear")}"
                    else null,
                "Active EQ filters" to eq?.optInt("activeFilters")?.takeIf { eqActive },
                "EQ rate mismatch" to
                    if (eqActive && eq?.optBoolean("coefficientRateMatchesInput", true) == false)
                        "Filter rate ${rate(eq.optLong("coefficientSampleRateHz"))}; PCM rate ${rate(eq.optLong("inputSampleRateHz"))}"
                    else null,
                "EQ clipping" to eq?.optString("clipping")?.takeIf { eqActive },
                "Fade" to
                    if (direct) "Bypassed"
                    else if (fade.optBoolean("enabled"))
                        "Enabled · ${fade.optLong("durationMs")} ms · ${if(fade.optBoolean("fadeInProgress")) "in progress" else "idle"}"
                    else "Off",
                "Speed / pitch" to
                    "${player.playbackParameters.speed}× / ${player.playbackParameters.pitch}×",
                "Silence skipping" to
                    if (direct) "Bypassed" else if (player.skipSilenceEnabled) "On" else "Off",
                "DSD processing" to dsd?.optString("mode"),
                "Bit depth at sink / AudioTrack" to
                    if (!direct && audioTrack != null)
                        "${decoded?.optString("pcmEncoding")} → ${pcmEncodingName(audioTrack!!.encoding)}"
                    else null,
                "Sample rate at sink / AudioTrack" to
                    if (!direct && audioTrack != null)
                        "${rate(decoded?.optLong("sampleRateHz", -1) ?: -1)} → ${rate(audioTrack!!.sampleRate.toLong())}"
                    else null,
            )
        if (eqActive)
            eq?.optJSONArray("bands")?.let { bands ->
                repeat(bands.length()) { index ->
                    val band = bands.optJSONObject(index) ?: return@repeat
                    processingFields +=
                        "EQ filter ${index + 1}" to
                            "${band.optString("type")} · ${band.opt("frequencyHz")} Hz · ${band.opt("gainDb")} dB · Q ${band.opt("q")}"
                }
            }
        val dspPage = section("Processing", *processingFields.toTypedArray())

        val volume = UsbVolumeState.level.value
        val devicePage =
            section(
                "Device & volume",
                "Output device" to outputName,
                "Connection" to if (direct) "Direct USB" else device.type,
                "Output format" to outputDescription,
                "Clock confirmation" to
                    if (direct)
                        if (usb?.optBoolean("sampleRateReadbackConfirmed") == true)
                            "Confirmed by DAC clock readback"
                        else "Fixed descriptor rate / no live clock confirmation"
                    else null,
                "Volume control" to
                    if (direct) "DAC hardware" else "Android system volume + player gain",
                "DAC volume requested" to
                    if (direct) "${UsbVolumeState.requestedPercent.value}%" else null,
                "DAC volume confirmed" to
                    if (direct) volume.percent?.let { "$it%" } ?: "Not confirmed" else null,
                "DAC attenuation" to if (direct) volume.decibels?.let { "$it dB" } else null,
                "DAC mute" to if (direct) volume.muted else null,
                "DAC volume status" to volume.message.takeIf { direct },
                "System music volume" to
                    if (!direct)
                        "${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} / ${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"
                    else null,
                "System mute" to
                    if (!direct) audio.isStreamMute(AudioManager.STREAM_MUSIC) else null,
                "Player software gain" to
                    if (!direct) "${(player.volume * 100).toInt()}%" else null,
                "DSD output preference" to policy.optString("selectedMode").takeIf { direct },
                "Automatic DoP recognition" to
                    policy.optBoolean("knownDopInterface").takeIf { direct && dsf != null },
                "Routing confidence" to device.detection.takeIf { !direct },
                "Hardware processing" to
                    if (direct) "Internal DAC filters / conversion are not observable"
                    else "Final mixer or DAC rate and encoding are not exposed by Android",
            )

        val pending = usb?.optLong("pendingBytes", 0) ?: 0
        val frameBytes =
            (layout?.optInt("channels", 0) ?: 0) * (layout?.optInt("containerBytes", 0) ?: 0)
        val usbPage =
            section(
                "USB stream",
                "State" to
                    if (direct) if (active) "Open" else "Not initialized"
                    else "Direct USB is not in use",
                "Status" to UsbPlaybackState.status.value,
                "Synchronization" to usb?.optJSONObject("transport")?.optString("synchronization"),
                "Requested / confirmed rate" to
                    if (direct)
                        "${rate(decoded?.optLong("sampleRateHz", -1) ?: -1)} / ${rate(outputRate)}"
                    else null,
                "Clock feedback rate" to
                    feedback?.optDouble("lastRateHz")?.takeIf { it.isFinite() }?.let { "$it Hz" },
                "Clock feedback range" to
                    feedback?.let { "${it.opt("minimumRateHz")} .. ${it.opt("maximumRateHz")} Hz" },
                "Queued USB audio" to
                    if (direct && outputRate > 0 && frameBytes > 0)
                        "${pending * 1000 / outputRate / frameBytes} ms ($pending bytes)"
                    else null,
                "USB underruns" to usb?.opt("underruns"),
                "USB packet errors" to usb?.opt("packetErrors"),
                "USB short packets" to usb?.opt("shortPackets"),
                "Feedback rejected / timed out" to
                    feedback?.let { "${it.opt("rejectedPackets")} / ${it.opt("timeouts")}" },
                "Transfer error" to usb?.optLong("transferError")?.takeIf { it != 0L },
                "Frames delivered to USB" to usb?.opt("completedFrames"),
                "Statistics scope" to
                    "Current USB connection; completion does not prove analog playback or bit-perfect output",
            )

        val errorFields =
            mutableListOf<Pair<String, Any?>>(
                "Player error" to
                    (player.playerError?.let { "${it.errorCodeName}: ${it.message}" } ?: "None"),
                "Fallback reason" to route.optString("currentTrackFallbackReason"),
                "AudioTrack underruns" to underruns,
                "Last underrun" to
                    if (underruns > 0)
                        "${underrunDetails.opt("elapsedSinceFeedMs")} ms since last feed; ${underrunDetails.opt("bufferDurationMs")} ms buffer"
                    else null,
            )
        counters?.let {
            it.ensureUpdated()
            if (it.skippedOutputBufferCount > 0)
                errorFields += "Skipped decoder buffers" to it.skippedOutputBufferCount
        }
        OutputInfoState.eventSnapshot().takeLast(15).forEachIndexed { index, event ->
            val time = event.substringBefore(" ms · ").toLongOrNull()
            val message = event.substringAfter(" ms · ", event)
            errorFields +=
                "Event ${index + 1}" to
                    if (time != null)
                        "${(SystemClock.elapsedRealtime() - time) / 1000}s ago · $message"
                    else message
        }
        return listOf(
            overview,
            codec,
            dspPage,
            devicePage,
            usbPage,
            section("Errors & events", *errorFields.toTypedArray()),
        )
    }

    private fun codecInfo(name: String, mime: String?): JSONObject = runCatching {
        val codec =
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull {
                it.name == name || it.canonicalName == name
            }
        JSONObject()
            .put(
                "implementation",
                when {
                    codec == null -> "Native CPU decoder / PCM path"
                    codec.isHardwareAccelerated -> "Android hardware codec"
                    codec.isSoftwareOnly -> "Android software codec"
                    else -> "Android codec (${mime ?: "audio"})"
                },
            )
    }
        .getOrElse { JSONObject().put("implementation", "Not reported") }

    private fun section(title: String, vararg fields: Pair<String, Any?>): OutputInfoSection =
        OutputInfoSection(
            title,
            fields
                .filter { it.second != null && it.second != JSONObject.NULL && it.second != "" }
                .map { OutputInfoField(it.first, it.second.toString()) },
        )

    private fun channels(count: Int): String =
        when (count) {
            1 -> "Mono"
            2 -> "Stereo"
            else -> if (count > 0) "$count channels" else "Channels not reported"
        }

    private fun rate(hz: Long): String = if (hz > 0) formatSampleRate(hz) else "Not reported"

    private fun encodingBits(encoding: Int): Int? =
        when (encoding) {
            C.ENCODING_PCM_8BIT -> 8
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT -> 32
            else -> null
        }
}

private fun stateName(state: Int): String =
    when (state) {
        Player.STATE_BUFFERING -> "Buffering"
        Player.STATE_READY -> "Ready"
        Player.STATE_ENDED -> "Ended"
        else -> "Idle / stopped"
    }
