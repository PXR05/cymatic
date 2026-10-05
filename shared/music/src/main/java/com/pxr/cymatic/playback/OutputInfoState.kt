package com.pxr.cymatic.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.audio.dsd.DsdOutput
import com.pxr.cymatic.audio.usb.usbPcmLayout
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class OutputInfoField(val name: String, val value: String)

data class OutputInfoSection(val title: String, val fields: List<OutputInfoField>)

data class OutputInfoReport(val sections: List<OutputInfoSection> = emptyList()) {
    fun text(): String =
        sections.joinToString("\n\n") { section ->
            section.title + "\n" + section.fields.joinToString("\n") { "${it.name}: ${it.value}" }
        }
}

@UnstableApi
internal object OutputInfoState {
    private data class Sink(val owner: Any, val snapshot: () -> JSONObject)

    private val sink = AtomicReference<Sink?>(null)
    private val events = ArrayDeque<String>()
    private val mutableReport = MutableStateFlow(OutputInfoReport())
    val report = mutableReport.asStateFlow()
    val observed: Boolean
        get() = mutableReport.subscriptionCount.value > 0

    fun publish(sections: List<OutputInfoSection>) {
        mutableReport.value = OutputInfoReport(sections)
    }

    fun registerSink(owner: Any, snapshot: () -> JSONObject) {
        sink.set(Sink(owner, snapshot))
    }

    fun unregisterSink(owner: Any) {
        val current = sink.get()
        if (current?.owner === owner) sink.compareAndSet(current, null)
    }

    fun sinkSnapshot(): JSONObject =
        runCatching { sink.get()?.snapshot?.invoke() }.getOrNull()
            ?: JSONObject().put("state", "No configured sink or snapshot unavailable")

    @Synchronized
    fun event(message: String) {
        events.addLast("${SystemClock.elapsedRealtime()} ms · $message")
        while (events.size > 40) events.removeFirst()
    }

    @Synchronized fun eventSnapshot(): List<String> = events.toList()

    @Synchronized
    fun clear() {
        sink.set(null)
        events.clear()
        mutableReport.value = OutputInfoReport()
    }
}

internal fun known(value: Long): Any =
    if (value < 0 || value == C.TIME_UNSET) "Not reported" else value

@UnstableApi
internal fun audioFormatInfo(format: Format?): JSONObject {
    if (format == null) return JSONObject().put("state", "Not configured")
    val data =
        JSONObject()
            .put("sampleRateHz", format.sampleRate)
            .put("channels", format.channelCount)
            .put("pcmEncoding", pcmEncodingName(format.pcmEncoding))
            .put("encoderDelayFrames", format.encoderDelay)
            .put("encoderPaddingFrames", format.encoderPadding)
    format.usbPcmLayout()?.let { data.put("integerPcmLayout", it.json()) }
    (format.customData as? DsdOutput)?.let { dsd ->
        data.put(
            "dsdProcessing",
            JSONObject()
                .put("mode", dsd.label)
                .put("sourceBitRateHz", dsd.sourceRate)
                .put("filterCutoffHz", if (dsd.dop) 0 else 40000),
        )
    }
    return data
}

internal fun pcmEncodingName(encoding: Int): String =
    when (encoding) {
        C.ENCODING_PCM_8BIT -> "Unsigned PCM8"
        C.ENCODING_PCM_16BIT -> "Signed PCM16 little-endian"
        C.ENCODING_PCM_24BIT -> "Signed PCM24 little-endian"
        C.ENCODING_PCM_32BIT -> "Signed PCM32 little-endian"
        C.ENCODING_PCM_FLOAT -> "Float32 PCM"
        C.ENCODING_INVALID,
        Format.NO_VALUE -> "Not specified / encoded audio"
        else -> "Encoding $encoding"
    }

internal fun outputInfoSection(title: String, values: JSONObject): OutputInfoSection {
    val fields = mutableListOf<OutputInfoField>()
    fun flatten(path: String, value: Any?) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys().asSequence().toList()
                if (keys.isEmpty()) fields += OutputInfoField(path, "None")
                else
                    keys.forEach {
                        flatten(if (path.isEmpty()) it else "$path / $it", value.opt(it))
                    }
            }
            is JSONArray ->
                if (value.length() == 0) fields += OutputInfoField(path, "None")
                else
                    (0 until value.length()).forEach { flatten("$path [${it + 1}]", value.opt(it)) }
            else ->
                fields +=
                    OutputInfoField(
                        path.replace(Regex("([a-z])([A-Z])"), "$1 $2"),
                        if (value == null || value == JSONObject.NULL) "Not reported"
                        else value.toString(),
                    )
        }
    }
    flatten("", values)
    return OutputInfoSection(title, fields)
}
