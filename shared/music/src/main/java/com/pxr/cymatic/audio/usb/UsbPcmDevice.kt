package com.pxr.cymatic.audio.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.pxr.cymatic.usb.UsbIsochronousConnection
import com.pxr.cymatic.usb.UsbPcmStream
import com.pxr.cymatic.usb.UsbTransferResult
import java.io.Closeable
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal class UsbPcmDevice private constructor(private val connection: UsbDeviceConnection) :
    Closeable {
    private val token = Any()
    private var owned = false
    private var native: UsbIsochronousConnection? = null
    private var control: UsbInterface? = null
    private var stream: UsbInterface? = null
    private var idle: UsbInterface? = null
    private var controlClaimed = false
    private var streamClaimed = false
    private var controlDriver: String? = null
    private var streamDriver: String? = null
    private var clock: UsbSamplingClock? = null
    private var endpoint = 0
    private var capacity = 0
    private var interval = 0
    private var feedbackEndpoint = 0
    private var feedbackCapacity = 0
    private var feedbackInterval = 0
    private var feedbackRefreshMs = 0
    private var primingFrames = 0
    lateinit var layout: UsbPcmLayout
        private set

    private var rate = 0
    private var frameBytes = 4
    val asynchronous: Boolean
        get() = feedbackEndpoint != 0

    @Volatile private var probeCancelled = false

    @Volatile private var continuous: UsbPcmStream? = null
    private var volume: UsbHardwareVolume? = null
    private var closed = false
    val report = JSONObject()

    private fun configure(
        device: UsbDevice,
        frequency: Int,
        requireVolume: Boolean,
        sourceLayout: UsbPcmLayout,
    ) {
        report
            .put("deviceName", runCatching { device.productName }.getOrNull() ?: "USB DAC")
            .put("vendorId", device.vendorId)
            .put("productId", device.productId)
        UsbOutputOwnership.acquire(token)
        owned = true
        val transport = UsbIsochronousConnection(connection).also { native = it }
        val speed = transport.speed()
        report.put(
            "usbSpeed",
            when (speed) {
                2 -> "FULL"
                3 -> "HIGH"
                else -> "UNKNOWN ($speed)"
            },
        )
        require(speed == 2 || speed == 3) { "Unsupported USB speed: $speed" }
        val configurationBytes = ByteArray(1)
        check(
            connection.controlTransfer(
                0x80,
                8,
                0,
                0,
                configurationBytes,
                1,
                500,
            ) == 1
        ) {
            "Active configuration could not be read"
        }
        val configurationId = configurationBytes.u8(0)
        val configuration =
            (0 until device.configurationCount).map(device::getConfiguration).singleOrNull {
                it.id == configurationId
            } ?: error("Active USB configuration is unresolved")
        val interfaces = (0 until configuration.interfaceCount).map(configuration::getInterface)
        val descriptors = connection.rawDescriptors ?: byteArrayOf()
        val parsed = UsbAudioDescriptors.parse(descriptors)
        val ticks = if (speed == 3) 8000 else 1000
        require(frequency in 8000..384000)
        val candidates =
            parsed.formats
                .filter {
                    it.configuration == configurationId &&
                        it.protocol in listOf(0, 0x20) &&
                        it.pcm &&
                        it.channels == sourceLayout.channels &&
                        it.containerBytes in 2..4 &&
                        it.validBits in sourceLayout.validBits..it.containerBytes * 8 &&
                        it.channelMask in listOf(0L, sourceLayout.channelMask) &&
                        it.controlInterface != null &&
                        (it.protocol == 0 || it.clockSourceId != null) &&
                        (it.protocol != 0 || it.advertisesRate(frequency))
                }
                .sortedBy {
                    if (
                        it.containerBytes == sourceLayout.containerBytes &&
                            it.validBits == sourceLayout.validBits
                    )
                        0
                    else 1 + (it.validBits - sourceLayout.validBits) * 4 + it.containerBytes
                }
        val selected =
            candidates.firstNotNullOfOrNull { value ->
                val usbInterface =
                    interfaces.singleOrNull {
                        it.id == value.interfaceNumber &&
                            it.alternateSetting == value.alternateSetting
                    } ?: return@firstNotNullOfOrNull null
                val endpoints =
                    UsbStreamingEndpoints.resolve(usbInterface, speed, descriptors, configurationId)
                        ?: return@firstNotNullOfOrNull null
                val serviceTicks = 1 shl (endpoints.output.interval - 1)
                if (
                    serviceTicks > ticks ||
                        ((frequency.toLong() * serviceTicks + ticks - 1) / ticks) *
                            sourceLayout.channels *
                            value.containerBytes > endpoints.packetCapacity ||
                        (value.protocol == 0 &&
                            !endpoints.samplingFrequencyControl &&
                            (value.continuousRates != null ||
                                value.sampleRates.distinct() != listOf(frequency)))
                ) {
                    null
                } else Triple(value, usbInterface, endpoints)
            }
                ?: error(
                    "DAC has no precision-preserving UAC1/UAC2 output for ${sourceLayout.description} at $frequency Hz"
                )
        val (format, streaming, endpoints) = selected
        layout = UsbPcmLayout(sourceLayout.channels, format.containerBytes, format.validBits)
        frameBytes = layout.frameBytes
        val inactive =
            interfaces.singleOrNull { it.id == format.interfaceNumber && it.alternateSetting == 0 }
                ?: error("USB streaming interface has no idle setting")
        val audioControl =
            interfaces.singleOrNull {
                it.id == format.controlInterface &&
                    it.alternateSetting == 0 &&
                    it.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                    it.interfaceSubclass == 1
            } ?: error("AudioControl interface is unresolved")
        val source =
            if (format.protocol == 0x20)
                parsed.clocks.single {
                    it.configuration == configurationId &&
                        it.id == format.clockSourceId &&
                        it.controlInterface == format.controlInterface
                }
            else null
        capacity = endpoints.packetCapacity
        interval = endpoints.output.interval
        endpoint = endpoints.output.address
        endpoints.feedback?.let {
            feedbackEndpoint = it.address
            feedbackCapacity = it.maxPacketSize
            feedbackInterval = it.interval
            feedbackRefreshMs = endpoints.feedbackRefreshMs
        }
        rate = frequency
        report
            .put("format", format.json())
            .put("endpoint", endpoint)
            .put("maxPacketBytes", capacity)
            .put("interval", interval)
            .put("transport", endpoints.json())
            .put("sourceLayout", sourceLayout.json())
            .put("outputLayout", layout.json())
            .put("pcmPackingChanged", sourceLayout != layout)
            .put("sourcePrecisionPreserved", true)
        controlDriver = transport.interfaceDriver(audioControl.id)
        streamDriver = transport.interfaceDriver(streaming.id)
        report.put(
            "initialInterfaceDrivers",
            JSONArray(
                listOf(
                    JSONObject()
                        .put("interface", audioControl.id)
                        .put("driver", controlDriver ?: JSONObject.NULL),
                    JSONObject()
                        .put("interface", streaming.id)
                        .put("driver", streamDriver ?: JSONObject.NULL),
                )
            ),
        )
        control = audioControl
        stream = streaming
        idle = inactive
        controlClaimed = connection.claimInterface(audioControl, true)
        check(controlClaimed) { "AudioControl interface could not be claimed" }
        streamClaimed = connection.claimInterface(inactive, true)
        check(streamClaimed) { "USB streaming interface could not be claimed" }
        check(connection.setInterface(inactive)) { "USB streaming interface could not be stopped" }
        val samplingClock =
            if (source != null) UsbSamplingClock.Uac2(connection, report, source)
            else UsbSamplingClock.Uac1(connection, report, format, endpoints)
        clock = samplingClock
        if (source != null) samplingClock.configure(frequency)
        if (requireVolume) {
            val identity = runCatching { device.serialNumber }.getOrNull().orEmpty()
            UsbVolumeState.beginDevice("${device.vendorId}:${device.productId}:$identity")
            val feature = parsed.playbackVolume(format)
            if (feature == null) {
                UsbVolumeState.unavailable("DAC hardware volume is unavailable")
                error(
                    "DAC has no resolved writable hardware volume; direct playback stopped to avoid uncontrolled output"
                )
            }
            UsbHardwareVolume(connection, feature) { continuous?.stop() }
                .also {
                    volume = it
                    report.put("hardwareVolume", it.report)
                    it.initialize()
                }
        }
        check(connection.setInterface(streaming)) { "USB streaming setting could not be activated" }
        if (source == null) samplingClock.configure(frequency)
        if (endpoints.lockDelayApplies && endpoints.lockDelay != 0) {
            val frames =
                if (endpoints.lockDelayUnits == 1)
                    (frequency.toLong() * endpoints.lockDelay + 999) / 1000
                else endpoints.lockDelay.toLong()
            require(frames in 1..frequency * 2L) { "DAC clock lock delay exceeds two seconds" }
            primingFrames = frames.toInt()
            report.put("clockLockPrimingFrames", primingFrames)
        }
    }

    fun writeProbe(pcm: ByteArray): UsbTransferResult {
        require(pcm.isNotEmpty() && pcm.size % 4 == 0 && pcm.all { it == 0.toByte() })
        val silence = if (frameBytes == 4) pcm else ByteArray(pcm.size / 4 * frameBytes)
        if (!asynchronous && primingFrames == 0)
            return checkNotNull(native)
                .writePcm(endpoint, capacity, interval, rate, frameBytes, silence)
        val output = continuousStream()
        return output
            .writeSilentProbe(rate, frameBytes, silence) { probeCancelled }
            .also {
                report.putUsbStreamStatistics(output.statistics()).put("transferError", it.error)
            }
    }

    fun cancelProbe() {
        probeCancelled = true
        native?.cancel()
        continuous?.stop()
    }

    fun checkVolume() {
        volume?.failure?.let {
            throw IllegalStateException(
                "DAC hardware volume failed: ${it.message}",
                it,
            )
        }
    }

    fun continuousStream(): UsbPcmStream =
        continuous
            ?: UsbPcmStream(
                    connection.fileDescriptor,
                    endpoint,
                    capacity,
                    interval,
                    rate,
                    frameBytes,
                    feedbackEndpoint,
                    feedbackCapacity,
                    feedbackInterval,
                    feedbackRefreshMs,
                    primingFrames,
                )
                .also { continuous = it }

    override fun close() {
        if (closed) return
        closed = true
        val cleanup = JSONArray()
        fun restore(label: String, action: () -> Boolean) {
            if (!runCatching(action).getOrDefault(false)) cleanup.put(label)
        }
        volume?.close()
        continuous?.stop()
        val stopped =
            !streamClaimed ||
                (idle != null && runCatching { connection.setInterface(idle) }.getOrDefault(false))
        if (!stopped) cleanup.put("Could not stop streaming interface")
        if (stopped) volume?.restore()?.forEach(cleanup::put)
        else if (volume != null)
            cleanup.put("DAC volume restoration skipped because streaming state is unknown")
        if (stopped)
            clock?.let { samplingClock ->
                restore("Could not restore original clock rate") { samplingClock.restore() }
            }
        else if (clock != null)
            cleanup.put("Clock restoration skipped because streaming state is unknown")
        if (streamClaimed)
            stream?.let {
                restore("Could not release streaming interface") {
                    connection.releaseInterface(it)
                }
            }
        if (controlClaimed)
            control?.let {
                restore("Could not release AudioControl interface") {
                    connection.releaseInterface(it)
                }
            }
        val drivers = JSONArray()
        fun restoreDriver(usbInterface: UsbInterface, expected: String?) {
            val result =
                JSONObject()
                    .put("interface", usbInterface.id)
                    .put("expectedDriver", expected ?: JSONObject.NULL)
            drivers.put(result)
            try {
                val transport = checkNotNull(native)
                var driver = transport.interfaceDriver(usbInterface.id)
                if (driver == null && expected != null) {
                    result.put("connectResult", transport.reconnectInterface(usbInterface.id))
                    driver = transport.interfaceDriver(usbInterface.id)
                }
                val restored = driver == expected
                result.put("driver", driver ?: JSONObject.NULL).put("restored", restored)
                if (!restored) cleanup.put("Interface ${usbInterface.id} driver was not restored")
            } catch (e: Exception) {
                result.put("error", e.message ?: e.javaClass.simpleName).put("restored", false)
                cleanup.put("Could not verify interface ${usbInterface.id} driver restoration")
            }
        }
        control?.let { restoreDriver(it, controlDriver) }
        stream?.let { restoreDriver(it, streamDriver) }
        report.put("interfaceDriverRestoration", drivers).put("cleanupWarnings", cleanup)
        try {
            if (native != null) native?.close() else connection.close()
        } finally {
            continuous?.releaseAfterConnectionClosed()
            if (owned) UsbOutputOwnership.release(token)
        }
    }

    companion object {
        fun open(
            context: Context,
            rate: Int,
            requested: UsbDevice? = null,
            requireVolume: Boolean = true,
            sourceLayout: UsbPcmLayout = UsbPcmLayout(2, 2, 16),
        ): UsbPcmDevice {
            val manager = context.getSystemService(UsbManager::class.java)
            val devices = manager.deviceList.values.filter(::isUsbAudioDevice)
            val device =
                if (requested != null) devices.singleOrNull { it.deviceId == requested.deviceId }
                else devices.singleOrNull()
            checkNotNull(device) { "Connect exactly one USB DAC for direct playback" }
            check(manager.hasPermission(device)) {
                "USB access is required; reconnect the DAC to request permission"
            }
            val connection = manager.openDevice(device) ?: error("USB DAC could not be opened")
            val result = UsbPcmDevice(connection)
            try {
                result.configure(device, rate, requireVolume, sourceLayout)
                return result
            } catch (e: Throwable) {
                result.close()
                if (requireVolume) {
                    UsbVolumeState.unavailable(e.message ?: "DAC volume unavailable")
                    UsbPlaybackState.record(
                        result.report
                            .put("capturedAt", Instant.now().toString())
                            .put("initializationFailed", true)
                            .put("error", e.message ?: e.javaClass.simpleName)
                            .put("digitalEqualityVerified", false)
                    )
                }
                throw e
            }
        }
    }
}
