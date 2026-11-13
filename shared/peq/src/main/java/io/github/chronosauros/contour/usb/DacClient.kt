// Modified for Cymatic: All device families receive the disconnect/cancellation guard.
// Upstream: Chronosauros/contour 95d592df (Apache-2.0); see README.md and bundled notices.
package io.github.chronosauros.contour.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.SystemClock
import io.github.chronosauros.contour.core.BuildChannel
import io.github.chronosauros.contour.core.DacImport
import io.github.chronosauros.contour.core.DevicePlan
import io.github.chronosauros.contour.core.DeviceProtocol
import io.github.chronosauros.contour.core.DeviceTarget
import io.github.chronosauros.contour.core.Profile
import io.github.chronosauros.contour.core.Proven
import io.github.chronosauros.contour.core.WalkPlay
import io.github.chronosauros.contour.core.native.FamilyDevices
import io.github.chronosauros.contour.core.native.FamilyState
import io.github.chronosauros.contour.core.native.FiioMelodyExchange
import io.github.chronosauros.contour.core.native.FiioReset
import io.github.chronosauros.contour.core.native.FiioResetBackup
import io.github.chronosauros.contour.core.native.FiioResetSent
import io.github.chronosauros.contour.core.native.FiioResetTicket
import io.github.chronosauros.contour.core.native.FiioRestoreResult
import io.github.chronosauros.contour.core.native.KtMicroCatalog
import io.github.chronosauros.contour.core.native.KtMicroCodec
import io.github.chronosauros.contour.core.native.KtMicroSession
import io.github.chronosauros.contour.core.native.MoondropCatalog
import io.github.chronosauros.contour.core.native.MoondropCodec
import io.github.chronosauros.contour.core.native.MoondropGen1Catalog
import io.github.chronosauros.contour.core.native.MoondropGen1Session
import io.github.chronosauros.contour.core.native.MoondropSession
import io.github.chronosauros.contour.core.native.NativeHidReports
import io.github.chronosauros.contour.core.native.NativePort
import io.github.chronosauros.contour.core.native.NativeSession
import io.github.chronosauros.contour.core.native.NativeState
import io.github.chronosauros.contour.core.native.ProbePlan
import io.github.chronosauros.contour.core.native.ProbeReport
import io.github.chronosauros.contour.core.native.SettingReading
import io.github.chronosauros.contour.core.native.SettingWriteOutcome
import io.github.chronosauros.contour.core.native.WalkPlayProfile
import io.github.chronosauros.contour.core.native.WalkPlayReset
import io.github.chronosauros.contour.core.native.WalkPlayResetBackup
import io.github.chronosauros.contour.core.native.WalkPlayResetSent
import io.github.chronosauros.contour.core.native.WalkPlayResetTicket
import io.github.chronosauros.contour.core.native.WalkPlayRestoreResult
import io.github.chronosauros.contour.core.native.WalkPlaySession
import io.github.chronosauros.contour.core.native.WalkPlaySettings
import io.github.chronosauros.contour.core.native.WalkPlaySnapshot
import io.github.chronosauros.contour.core.native.WriteTestResult
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DacSnapshot(
    val firmware: String,
    val slot: Int,
    val bands: List<WalkPlay.DeviceBand>,
    val preampDb: Int?,
    val readMs: Long,
    val protocol: DeviceProtocol? = DeviceProtocol.MICRO,
    val nativeState: NativeState? = null,
    val target: DeviceTarget = DeviceTarget.of(requireNotNull(protocol)),
    val familyState: FamilyState? = null,
) {
    val shownPreamp: Double?
        get() =
            if (familyState != null) familyState.preampDb
            else if (nativeState != null) nativeState.preamp else preampDb?.toDouble()

    fun matches(profile: Profile): Boolean =
        familyState?.matches(profile)
            ?: nativeState?.matches(profile)
            ?: requireNotNull(protocol)
                .matches(profile, bands.map { it.registers }, requireNotNull(preampDb))

    fun importExact(): DacImport =
        importRaw().let { r ->
            if (r is DacImport.Rejected && Proven.channel != BuildChannel.PERF)
                DacImport.Rejected(listOf(NOT_EXACT))
            else r
        }

    private fun importRaw(): DacImport =
        familyState?.let { s ->
            runCatching<DacImport> { DacImport.Ready(s.importExact()) }
                .getOrElse {
                    DacImport.Rejected(
                        listOf(it.message ?: "The DAC state cannot be imported exactly")
                    )
                }
        }
            ?: nativeState?.let { s ->
                runCatching<DacImport> { DacImport.Ready(s.importExact()) }
                    .getOrElse {
                        DacImport.Rejected(
                            listOf(it.message ?: "Native state cannot be imported exactly")
                        )
                    }
            }
            ?: requireNotNull(protocol).importExact(bands, requireNotNull(preampDb))

    private companion object {
        const val NOT_EXACT =
            "This EQ on the DAC uses filters Contour can't edit exactly, so it is only shown."
    }
}

data class WriteResult(
    val verified: Boolean,
    val mismatches: List<String>,
    val writeMs: Long,
    val readBackMs: Long,
    val readBack: DacSnapshot?,
    val pending: PendingSave? = null,
    val previousIndex: Int? = null,
)

class PendingSave(
    val afterReturn: (suspend (DacHandle) -> Unit)? = null,
    val verify: (DacSnapshot) -> List<String>,
)

class DacClient(
    private val manager: UsbManager,
    private val guardFor: (DacHandle) -> (() -> Unit) = { {} },
    private val audioKeepAlive: (UsbDevice) -> AutoCloseable? = { null },
) {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "contour-usb") }
    private val usb = executor.asCoroutineDispatcher()

    fun shutdown() = executor.shutdown()

    private suspend fun <T> withDevice(
        device: DacHandle,
        postGuard: Boolean = true,
        block: (HidIo) -> T,
    ): T = hidLock.withLock {
        device.simulated?.let { open ->
            return@withLock withContext(usb) { open(guardFor(device)).use(block) }
        }
        val dev = requireNotNull(device.usb)
        val fiio = target(device).fiio
        val guard = guardFor(device)
        withContext(usb) {
            guard()
            val stream = if (fiio?.needsAudioStream == true) audioKeepAlive(dev) else null
            try {
                HidTransport.open(manager, dev, guard).use(block).also { if (postGuard) guard() }
            } finally {
                stream?.close()
            }
        }
    }

    private companion object {

        val hidLock = Mutex()
    }

    private fun target(device: DacHandle): DeviceTarget = device.target

    private fun protocol(device: DacHandle): DeviceProtocol =
        target(device).walkplay
            ?: throw IOException(
                "WalkPlay-only operation unavailable on ${target(device).caps.name}"
            )

    private fun nativeSession(device: DacHandle, t: HidIo, target: DeviceTarget) =
        NativeSession(
            target,
            object : NativePort {

                private val melody =
                    if (device.simulated == null && FiioMelodyExchange.appliesTo(target.fiio))
                        FiioMelodyExchange(
                            t.nativePayloadSize(
                                requireNotNull(target.fiio).reportId,
                                NativeHidReports.Kind.INPUT,
                            ),
                            { t.checkSession() },
                            { SystemClock.elapsedRealtime() },
                            { id, payload ->
                                t.sendNative(id, NativeHidReports.Kind.OUTPUT, payload)
                            },
                            { t.receive(it) },
                            FiioMelodyExchange.name(requireNotNull(target.fiio)),
                        )
                    else null
                override val deviceKey = device.key

                override fun guard() = t.checkSession()

                override fun pause(ms: Long) {
                    t.checkSession()
                    Thread.sleep(ms)
                    t.checkSession()
                }

                override fun send(id: Int, kind: NativeHidReports.Kind, payload: ByteArray) =
                    t.sendNative(id, kind, payload)

                override fun request(id: Int, payload: ByteArray, matches: (ByteArray) -> Boolean) =
                    melody?.request(id, payload, matches) ?: t.requestNative(id, payload, matches)
            },
        )

    private fun nativeSnapshot(target: DeviceTarget, state: NativeState, ms: Long) =
        DacSnapshot(
            "native",
            state.slot,
            emptyList(),
            null,
            ms,
            protocol = null,
            nativeState = state,
            target = target,
        )

    suspend fun readDevice(device: DacHandle): DacSnapshot =
        withDevice(device) { t ->
            val target = target(device)
            if (target.walkplay != null) readWalkPlay(device, t, target)
            else if (target.fiio == null) readFamily(device, t, target)
            else {
                val start = SystemClock.elapsedRealtime()
                val state = nativeSession(device, t, target).read()
                t.checkSession()
                val ms = SystemClock.elapsedRealtime() - start
                UsbLog.line(
                    "read: ${target.caps.name}, slot ${state.slot}, preamp ${state.preamp} dB, $ms ms"
                )
                nativeSnapshot(target, state, ms)
            }
        }

    suspend fun readSettings(device: DacHandle): List<SettingReading> =
        withDevice(device) { t ->
            val target = target(device)
            require(target.settings.isNotEmpty()) { "${target.caps.name} has no settings list" }
            val start = SystemClock.elapsedRealtime()

            target.walkplayProfile?.let { profile ->
                val r =
                    WalkPlaySession(profile, WalkPlayPort(t, device))
                        .readSettings(target.settings)
                        .map { SettingReading(it.id, it.value, null) }
                t.checkSession()
                UsbLog.line(
                    "settings: ${target.caps.name}, ${r.count { it.value != null }}/${r.size} answered, ${SystemClock.elapsedRealtime() - start} ms"
                )
                return@withDevice r
            }
            if (target.fiio == null)
                throw IOException("No settings reader for ${target.caps.name} yet")
            val r = nativeSession(device, t, target).readSettings(UsbLog::line)
            t.checkSession()
            UsbLog.line(
                "settings: ${target.caps.name}, ${r.count { it.value != null }}/${r.size} answered, ${SystemClock.elapsedRealtime() - start} ms"
            )
            r
        }

    suspend fun writeSetting(device: DacHandle, id: String, value: Int): SettingWriteOutcome =
        withDevice(device) { t ->
            val target = target(device)
            if (target.fiio == null || target.settings.isEmpty())
                throw IOException("No settings writer for ${target.caps.name}")
            nativeSession(device, t, target).writeSetting(id, value, UsbLog::line).also {
                t.checkSession()
            }
        }

    suspend fun writeWalkPlaySetting(
        device: DacHandle,
        id: String,
        value: Int,
    ): SettingWriteOutcome =
        withDevice(device) { t ->
            val target = target(device)
            val profile =
                target.walkplayProfile
                    ?: throw IOException("No WalkPlay settings writer for ${target.caps.name}")
            val row =
                target.settings.firstOrNull { it.id == id }?.takeIf { it.writeEnabled }
                    ?: throw IOException("${target.caps.name}: \"$id\" is not written by the app")
            UsbLog.line("write: ${target.caps.name} ${row.label} -> $value")
            val r =
                WalkPlaySession(profile, WalkPlayPort(t, device))
                    .writeSetting(id, value, explicitHold = true, save = id == WalkPlaySettings.MIC)
            t.checkSession()
            UsbLog.line("settings write: ${if (r.verified) "VERIFIED" else r.reason}")
            when {
                r.verified -> SettingWriteOutcome.Verified
                r.readback != null -> SettingWriteOutcome.Mismatch(r.readback!!)
                else -> SettingWriteOutcome.NoReply
            }
        }

    suspend fun writeEqSwitch(device: DacHandle, on: Boolean): WriteResult =
        withDevice(device) { t ->
            val target = target(device)
            val model =
                target.ktMicro ?: throw IOException("No EQ switch writer for ${target.caps.name}")
            val start = SystemClock.elapsedRealtime()
            val session = KtMicroSession(model, WalkPlayPort(t, device))
            UsbLog.line("write: ${target.caps.name} EQ switch -> ${if (on) "ON" else "OFF"}")
            session.writeSwitch(on, explicitHold = true)
            savePending(session, start)
        }

    suspend fun writeAllegroGlobalGain(device: DacHandle, step: Int): WriteResult =
        withDevice(device) { t ->
            val target = target(device)
            val model =
                target.ktMicro ?: throw IOException("No Global Gain writer for ${target.caps.name}")
            val start = SystemClock.elapsedRealtime()
            val session = KtMicroSession(model, WalkPlayPort(t, device))
            UsbLog.line(
                "write: ${target.caps.name} Global Gain -> step $step (${KtMicroCodec.globalGainDb(step)} dB)"
            )
            session.writeGlobalGain(step, explicitHold = true)
            savePending(session, start)
        }

    suspend fun writeDawnGain(
        device: DacHandle,
        register: MoondropCodec.GainRegister,
        db: Double,
    ): WriteResult =
        withDevice(device) { t ->
            val target = target(device)
            val model =
                target.moondrop ?: throw IOException("No gain writer for ${target.caps.name}")
            val start = SystemClock.elapsedRealtime()
            val session = MoondropSession(model, WalkPlayPort(t, device))
            UsbLog.line("write: ${target.caps.name} ${register.what} -> $db dB")
            val r = session.writeGain(register, db, explicitHold = true, persist = false)
            t.checkSession()
            val back = requireNotNull(r.readback)
            val ms = SystemClock.elapsedRealtime() - start
            UsbLog.line(
                "write: ${if (r.verified) "VERIFIED" else "MISMATCH"}${r.reason?.let { ", $it" }.orEmpty()}, $ms ms"
            )
            WriteResult(
                r.verified,
                listOfNotNull(r.reason),
                ms,
                0,
                familySnapshot(target, FamilyState.Dawn(model, back), 0),
            )
        }

    suspend fun captureForReset(device: DacHandle, takenAtMs: Long): FiioResetBackup =
        withDevice(device) { t ->
            val target = target(device)
            if (!FiioReset.inApp(target.fiio))
                throw IOException("${target.caps.name} has no factory reset")
            UsbLog.line("reset: reading ${target.caps.name} (USER slots, names, settings)")
            nativeSession(device, t, target).captureForReset(true, takenAtMs, UsbLog::line).also {
                t.checkSession()
            }
        }

    suspend fun factoryReset(device: DacHandle, ticket: FiioResetTicket): FiioResetSent {
        var sent: FiioResetSent? = null
        try {
            withDevice(device) { t ->
                val target = target(device)
                if (!FiioReset.inApp(target.fiio))
                    throw IOException("${target.caps.name} has no factory reset")
                UsbLog.line("reset: sending the factory reset to ${target.caps.name}")
                sent = nativeSession(device, t, target).factoryReset(ticket, UsbLog::line)
            }
        } catch (e: IOException) {

            UsbLog.line("reset: ${e.message}")
            return sent ?: throw e
        }
        return requireNotNull(sent)
    }

    suspend fun restoreAfterReset(device: DacHandle, backup: FiioResetBackup): FiioRestoreResult =
        withDevice(device) { t ->
            val target = target(device)
            if (!FiioReset.inApp(target.fiio))
                throw IOException("${target.caps.name} has no factory reset")
            UsbLog.line("reset: writing the saved copy back to ${target.caps.name}")
            nativeSession(device, t, target).restoreAfterReset(backup, UsbLog::line).also {
                t.checkSession()
            }
        }

    private fun resettable(target: DeviceTarget): WalkPlayProfile =
        target.walkplayProfile?.takeIf { WalkPlayReset.enabled(it) }
            ?: throw IOException("${target.caps.name} has no factory reset")

    suspend fun captureWalkPlayForReset(device: DacHandle, takenAtMs: Long): WalkPlayResetBackup =
        withDevice(device) { t ->
            val target = target(device)
            val profile = resettable(target)
            UsbLog.line("reset: reading ${target.caps.name} (bands, preamp, settings)")
            WalkPlaySession(profile, WalkPlayPort(t, device))
                .captureForReset(true, takenAtMs)
                .also { t.checkSession() }
        }

    suspend fun walkPlayFactoryReset(
        device: DacHandle,
        ticket: WalkPlayResetTicket,
    ): WalkPlayResetSent {
        var sent: WalkPlayResetSent? = null
        try {
            withDevice(device) { t ->
                val target = target(device)
                val profile = resettable(target)
                UsbLog.line("reset: sending the factory reset to ${target.caps.name}")
                sent =
                    WalkPlaySession(profile, WalkPlayPort(t, device))
                        .factoryReset(ticket, UsbLog::line)
            }
        } catch (e: IOException) {
            UsbLog.line("reset: ${e.message}")
            return sent ?: throw e
        }
        return requireNotNull(sent)
    }

    suspend fun restoreWalkPlayAfterReset(
        device: DacHandle,
        backup: WalkPlayResetBackup,
    ): WalkPlayRestoreResult =
        withDevice(device) { t ->
            val target = target(device)
            val profile = resettable(target)
            UsbLog.line("reset: writing the saved copy back to ${target.caps.name}")
            WalkPlaySession(profile, WalkPlayPort(t, device))
                .restoreAfterReset(backup, target.settings, UsbLog::line)
                .also { t.checkSession() }
        }

    suspend fun returnToIndex(device: DacHandle, index: Int): Int =
        withDevice(device) { t ->
            val target = target(device)
            val model = target.moondrop ?: throw IOException("No EQ index on ${target.caps.name}")
            UsbLog.line("switch: ${target.caps.name} back to EQ index $index")
            MoondropSession(model, WalkPlayPort(t, device))
                .selectIndex(index, explicitUserAction = true)
                .also {
                    t.checkSession()
                    UsbLog.line("switch: EQ index $it confirmed")
                }
        }

    suspend fun writeTest(
        device: DacHandle,
        id: String,
        to: Int?,
        sink: (String) -> Unit,
    ): WriteTestResult =
        withDevice(device) { t ->
            val target = target(device)
            if (target.fiio == null || target.settings.isEmpty())
                throw IOException("No settings writer for ${target.caps.name}")
            nativeSession(device, t, target).writeTest(id, to, sink).also { t.checkSession() }
        }

    suspend fun probe(device: DacHandle, label: String, sink: (String) -> Unit) {
        val target = target(device)
        val usbDevice =
            requireNotNull(device.usb) { "A simulated DAC has no USB descriptors to probe" }
        val report = ProbeReport(label, target.caps.name, sink) { SystemClock.elapsedRealtime() }
        report.note(
            "info",
            "vid %04x pid %04x ${if (target.fiio != null) "FiiO" else if (target.walkplay != null) "WalkPlay" else "1.6 family"}"
                .format(usbDevice.vendorId, usbDevice.productId),
        )
        val fiio = target.fiio
        val gap = fiio?.let { ProbePlan.fiioGapMs(it) }
        if (fiio != null && gap == null)
            report.note(
                "fiio",
                "skipped: FiiO Control drives ${fiio.productName} with a different protocol (24-bit command IDs)",
            )
        else if (fiio == null && target.walkplay == null)
            report.note(
                "hid",
                "skipped: ${target.caps.name} is read by its own session, the probe asks it nothing",
            )
        else
            try {
                withDevice(device) { t ->
                    if (fiio != null)
                        nativeSession(device, t, target)
                            .probeFiio(report, requireNotNull(gap))
                            .also { t.checkSession() }
                    else probeWalkPlay(device, t, target, report)
                }
            } catch (e: IOException) {
                val busy =
                    e.message.orEmpty().let {
                        "claimInterface" in it ||
                            "No unambiguous" in it ||
                            "openDevice failed" in it ||
                            "no HID interface" in it
                    }
                report.stopped(
                    if (busy) "${ProbePlan.deviceToken(target.caps.name)} hid busy: ${e.message}"
                    else "${ProbePlan.deviceToken(target.caps.name)} hid err ${e.message}"
                )
            }
        hidLock.withLock { withContext(usb) { UacProbe.run(manager, usbDevice, report) } }
        report.done()
    }

    private fun probeWalkPlay(
        device: DacHandle,
        t: HidIo,
        target: DeviceTarget,
        report: ProbeReport,
    ) {
        val layer = "walkplay"
        val session = walkPlaySession(device, t, target).first
        ProbePlan.WALKPLAY_COMMANDS.forEachIndexed { i, cmd ->
            if (i > 0) Thread.sleep(100)
            t.checkSession()
            val tx = ProbePlan.walkPlayRead(cmd)
            val name = "0x%02x".format(cmd)
            report.tx(layer, name, UsbLog.hex(tx))
            val rx = session.probeRequest(tx, cmd)
            if (rx != null) report.rx(layer, name, UsbLog.hex(rx)) else report.noReply(layer, name)
        }
    }

    suspend fun selectSlot(device: DacHandle, slot: Int): Int =
        withDevice(device) { t ->
            val start = SystemClock.elapsedRealtime()
            val confirmed =
                nativeSession(device, t, target(device)).selectSlot(slot, explicitUserAction = true)
            t.checkSession()
            UsbLog.line(
                "switch: slot $slot confirmed $confirmed, ${SystemClock.elapsedRealtime() - start} ms"
            )
            confirmed
        }

    suspend fun writeSettingThenLeave(device: DacHandle, id: String, value: Int) =
        withDevice(device, postGuard = false) { t ->
            val target = target(device)
            if (target.fiio == null || target.settings.isEmpty())
                throw IOException("No settings writer for ${target.caps.name}")
            nativeSession(device, t, target).sendSettingThenLeave(id, value, UsbLog::line)
        }

    suspend fun readSlot(device: DacHandle, slot: Int): DacSnapshot =
        withDevice(device) { t ->
            val target = target(device)
            val start = SystemClock.elapsedRealtime()
            UsbLog.line("native read: switch to slot $slot")
            val state = nativeSession(device, t, target).readSlot(slot, explicitUserAction = true)
            t.checkSession()
            UsbLog.line("read: ${target.caps.name}, slot ${state.slot}, preamp ${state.preamp} dB")
            nativeSnapshot(target, state, SystemClock.elapsedRealtime() - start)
        }

    suspend fun renameSlot(device: DacHandle, slot: Int, name: String): String =
        withDevice(device) { t ->
            UsbLog.line("native rename: slot $slot -> $name")
            nativeSession(device, t, target(device))
                .renameSlot(slot, name, explicitUserAction = true)
                .also { t.checkSession() }
        }

    suspend fun writeTemporaryBands(device: DacHandle, bands: List<WalkPlay.BandWrite>, slot: Int) =
        withDevice(device) { t ->
            val t0 = SystemClock.elapsedRealtime()
            UsbLog.line("A/B RAM: ${bands.size} changed bands, slot $slot, preamp kept")
            walkPlaySession(device, t, target(device))
                .first
                .writeTemporary(bands, slot, explicitHold = true)
            UsbLog.line(
                "A/B RAM: TEMP_WRITE sent, ${SystemClock.elapsedRealtime() - t0} ms (DSP not verified by read-back)"
            )
        }

    suspend fun writeProfile(
        device: DacHandle,
        profile: Profile,
        targetSlot: Int? = null,
        onProgress: ((Float) -> Unit)? = null,
    ): WriteResult {
        val target = target(device)

        if (target.readOnly)
            throw IOException(DeviceTarget.READ_ONLY_ISSUE.format(target.caps.name))
        target.echo?.let {
            return writeEcho(device, profile, target, it, onProgress)
        }
        target.moondrop?.let {
            return writeDawn(device, profile, target, it, onProgress)
        }
        target.ktMicro?.let {
            return writeAllegro(device, profile, target, it, onProgress)
        }
        target.gen1?.let {
            return writeGen1(device, profile, target, it, onProgress)
        }
        if (!target.native)
            return when (val plan = requireNotNull(target.walkplay).plan(profile)) {
                is DevicePlan.Rejected -> throw IOException(plan.issues.joinToString("; "))
                is DevicePlan.Ready -> writePlan(device, plan, onProgress)
            }
        target
            .issues(profile)
            .takeIf { it.isNotEmpty() }
            ?.let { throw IOException(it.joinToString("; ")) }
        return withDevice(device, postGuard = target.fiio?.disconnectOnSave != true) { t ->
            val start = SystemClock.elapsedRealtime()
            UsbLog.line("native write: target slot ${targetSlot ?: target.destinationSlot}")
            val r =
                nativeSession(device, t, target)
                    .write(
                        profile,
                        explicitHold = true,
                        targetSlot = targetSlot,
                        onProgress =
                            onProgress?.let { report ->
                                { done, total -> report(done.toFloat() / total) }
                            },
                    )

            r.expected
                ?.takeIf { r.pending }
                ?.let { expected ->
                    val ms = SystemClock.elapsedRealtime() - start
                    UsbLog.line(
                        "native write: SAVE sent, $ms ms; the DAC leaves the USB bus; not verified until it is read again"
                    )
                    return@withDevice WriteResult(
                        false,
                        emptyList(),
                        ms,
                        0,
                        null,
                        pending =
                            PendingSave { back ->
                                val state =
                                    back.nativeState as? NativeState.Fiio
                                        ?: return@PendingSave listOf("the DAC was not read back")
                                if (
                                    runCatching { state.codec.matches(expected, state.raw) }
                                        .getOrDefault(false)
                                )
                                    emptyList()
                                else
                                    listOf(
                                        "FiiO native register mismatch (the DAC reads slot ${state.slot}, ${state.raw.count} filters; wrote slot ${expected.activeSlot}, ${expected.count})"
                                    )
                            },
                    )
                }
            t.checkSession()
            r.reason?.let {
                UsbLog.line(
                    "native write: ${if (r.verified) "VERIFIED, note" else "NOT VERIFIED"}: $it"
                )
            }
            if (r.reason == null) UsbLog.line("native write: VERIFIED")
            WriteResult(
                r.verified,
                listOfNotNull(r.reason),
                SystemClock.elapsedRealtime() - start,
                0,
                nativeSnapshot(target, requireNotNull(r.readback), 0),
            )
        }
    }

    private fun familyPort(
        t: HidIo,
        device: DacHandle,
        target: DeviceTarget,
        tick: () -> Unit = {},
    ) =
        WalkPlayPort(
            t,
            device,
            if (target.gen1 != null) MoondropGen1Catalog.REPORT_ID else WalkPlay.REPORT_ID,
            tick,
        )

    private fun familySnapshot(target: DeviceTarget, state: FamilyState, ms: Long) =
        DacSnapshot(
            (state as? FamilyState.EchoA)?.snapshot?.firmware ?: "native",
            state.slot,
            emptyList(),
            null,
            ms,
            protocol = null,
            target = target,
            familyState = state,
        )

    private fun readFamily(device: DacHandle, t: HidIo, target: DeviceTarget): DacSnapshot {
        val port = familyPort(t, device, target)
        val start = SystemClock.elapsedRealtime()
        val dawn = target.moondrop
        val allegro = target.ktMicro
        val gen1 = target.gen1
        val state: FamilyState =
            when {
                dawn != null ->
                    FamilyState.Dawn(dawn, MoondropSession(dawn, port).read(withPreGain = true))
                gen1 != null -> FamilyState.Dusk(gen1, MoondropGen1Session(gen1, port).read())
                allegro != null ->
                    KtMicroSession(allegro, port).let { s ->
                        FamilyState.Allegro(
                            allegro,
                            s.read(),
                            if (allegro.globalGainUserSteps != null) s.readGlobalGain() else null,
                        )
                    }
                else -> FamilyDevices.readEchoA(port)
            }
        t.checkSession()
        val ms = SystemClock.elapsedRealtime() - start
        UsbLog.line("read: ${state.describe().lineSequence().first()}, $ms ms")
        return familySnapshot(target, state, ms)
    }

    private suspend fun writeDawn(
        device: DacHandle,
        profile: Profile,
        target: DeviceTarget,
        model: MoondropCatalog.Model,
        onProgress: ((Float) -> Unit)?,
    ): WriteResult {
        target
            .issues(profile)
            .takeIf { it.isNotEmpty() }
            ?.let { throw IOException(it.joinToString("; ")) }
        val planned = MoondropCodec.planFor(model, profile.bands, profile.effectivePreampDb())
        if (planned.issues.isNotEmpty()) throw IOException(planned.issues.joinToString("; "))
        return withDevice(device) { t ->
            val start = SystemClock.elapsedRealtime()

            val steps =
                Steps(
                    2 * 2 * MoondropCodec.readQueries(model).size + planned.bands.size + 9,
                    onProgress,
                )
            val session = MoondropSession(model, WalkPlayPort(t, device) { steps.tick() })
            UsbLog.line(
                "write: ${target.caps.name}: as MOONDROP Hub (EQ index switch to ${model.writableIndex} unchecked), ${planned.bands.size} bands, pre-gain ${planned.preGainDb} dB"
            )
            val r = session.write(planned.bands, planned.preGainDb, explicitHold = true)
            t.checkSession()
            val back = requireNotNull(r.readback)
            val previous = session.lastBefore?.activeIndex?.takeIf { it != back.activeIndex }
            val ms = SystemClock.elapsedRealtime() - start
            UsbLog.line(
                "write: ${if (r.verified) "VERIFIED" else "MISMATCH"}${r.reason?.let { ", $it" }.orEmpty()}, $ms ms" +
                    (previous?.let { "; the DAC was on EQ index $it" } ?: "") +
                    "; EQ index after: ${back.activeIndex}"
            )
            WriteResult(
                r.verified,
                listOfNotNull(r.reason),
                ms,
                0,
                familySnapshot(target, FamilyState.Dawn(model, back), 0),
                previousIndex = previous,
            )
        }
    }

    private suspend fun writeEcho(
        device: DacHandle,
        profile: Profile,
        target: DeviceTarget,
        echo: WalkPlayProfile,
        onProgress: ((Float) -> Unit)?,
    ): WriteResult {
        target
            .issues(profile)
            .takeIf { it.isNotEmpty() }
            ?.let { throw IOException(it.joinToString("; ")) }
        return withDevice(device) { t ->
            val start = SystemClock.elapsedRealtime()
            var steps: Steps? = null
            val session = WalkPlaySession(echo, WalkPlayPort(t, device) { steps?.tick() })
            val plan =
                when (val planned = session.plan(profile)) {
                    is DevicePlan.Rejected -> throw IOException(planned.issues.joinToString("; "))
                    is DevicePlan.Ready -> planned
                }
            steps = Steps(session.plannedFrames(plan), onProgress)
            UsbLog.line(
                "write: ${target.caps.name}: ${plan.bands.size} bands, preamp ${plan.preampDb} dB"
            )
            val r = session.writePlan(plan, explicitHold = true)
            t.checkSession()
            val back = requireNotNull(r.readback)
            val ms = SystemClock.elapsedRealtime() - start
            UsbLog.line(
                "write: ${if (r.verified) "VERIFIED" else "MISMATCH"}${r.reason?.let { ", $it" }.orEmpty()}, $ms ms"
            )
            WriteResult(
                r.verified,
                listOfNotNull(r.reason),
                ms,
                0,
                familySnapshot(target, FamilyState.EchoA(echo, back), 0),
            )
        }
    }

    private suspend fun writeGen1(
        device: DacHandle,
        profile: Profile,
        target: DeviceTarget,
        model: MoondropGen1Catalog.Model,
        onProgress: ((Float) -> Unit)?,
    ): WriteResult {
        target
            .issues(profile)
            .takeIf { it.isNotEmpty() }
            ?.let { throw IOException(it.joinToString("; ")) }

        return withDevice(device, postGuard = false) { t ->
            val start = SystemClock.elapsedRealtime()
            val steps =
                Steps(
                    io.github.chronosauros.contour.core.native.MoondropGen1Codec.plannedRequests(
                        model
                    ),
                    onProgress,
                )
            val session = MoondropGen1Session(model, familyPort(t, device, target) { steps.tick() })
            UsbLog.line(
                "write: ${target.caps.name}: Custom EQ, ${model.bandCount} bands (the missing ones neutral), meta records and coefficients, then the commit"
            )
            session.write(profile.bands, explicitHold = true)
            val planned = requireNotNull(session.lastPlanned)
            val ms = SystemClock.elapsedRealtime() - start
            UsbLog.line(
                "write: commit sent, $ms ms; the cable restarts (leaves the bus, comes back on preset ${model.powerUpPreset}); not verified until it is back, selected onto the Custom EQ and read again"
            )
            WriteResult(
                false,
                emptyList(),
                ms,
                0,
                null,
                pending =
                    PendingSave(
                        afterReturn = { d ->
                            val r = selectGen1Preset(d, model.customIndex)
                            if (!r.verified)
                                throw IOException(
                                    r.mismatches.firstOrNull()
                                        ?: "the cable did not take the Custom EQ"
                                )
                        }
                    ) { back ->
                        val state =
                            back.familyState as? FamilyState.Dusk
                                ?: return@PendingSave listOf("the DAC was not read back")
                        io.github.chronosauros.contour.core.native.MoondropGen1Codec.mismatches(
                            planned,
                            state.snapshot.activeIndex,
                            state.snapshot.bands,
                            model.customIndex,
                        )
                    },
            )
        }
    }

    suspend fun selectGen1Preset(device: DacHandle, index: Int): WriteResult =
        withDevice(device) { t ->
            val target = target(device)
            val model =
                target.gen1 ?: throw IOException("No preset to select on ${target.caps.name}")
            val start = SystemClock.elapsedRealtime()
            val session = MoondropGen1Session(model, familyPort(t, device, target))
            UsbLog.line("switch: ${target.caps.name} -> preset $index (${model.presetName(index)})")
            val confirmed = session.selectPreset(index, explicitUserAction = true)
            val back = session.read()
            t.checkSession()
            val ms = SystemClock.elapsedRealtime() - start
            UsbLog.line("switch: preset $index confirmed $confirmed, $ms ms")
            val reason =
                if (confirmed == index) null else "the cable reports preset $confirmed, not $index"
            WriteResult(
                confirmed == index,
                listOfNotNull(reason),
                ms,
                0,
                familySnapshot(target, FamilyState.Dusk(model, back), 0),
            )
        }

    private suspend fun writeAllegro(
        device: DacHandle,
        profile: Profile,
        target: DeviceTarget,
        model: KtMicroCatalog.Model,
        onProgress: ((Float) -> Unit)?,
    ): WriteResult {
        target
            .issues(profile)
            .takeIf { it.isNotEmpty() }
            ?.let { throw IOException(it.joinToString("; ")) }
        return withDevice(device) { t ->
            val start = SystemClock.elapsedRealtime()
            val steps =
                Steps(
                    2 * KtMicroCodec.readQueries(model).size + 2 * model.bandCount + 6,
                    onProgress,
                )
            val session = KtMicroSession(model, WalkPlayPort(t, device) { steps.tick() })
            UsbLog.line(
                "write: ${target.caps.name}: ${model.bandCount} bands, " +
                    if (model.sendTurnsEqOn) "EQ switch turned on if it is off"
                    else "EQ switch left as found"
            )
            session.write(
                profile.copy(preampDb = 0.0),
                explicitHold = true,
                eqEnabled = model.sendEqEnabled,
            )
            if (model.sendTurnsEqOn)
                UsbLog.line(
                    "write: EQ switch " +
                        if (
                            session.lastPlan?.frames?.any {
                                (it.payload[0].toInt() and 255) == KtMicroCodec.EQ_ENABLE_REGISTER
                            } == true
                        )
                            "was off: turned on"
                        else "was already on"
                )
            savePending(session, start)
        }
    }

    private fun savePending(session: KtMicroSession, start: Long): WriteResult {
        val plan = requireNotNull(session.lastPlan)
        val ms = SystemClock.elapsedRealtime() - start
        UsbLog.line(
            "write: SAVE sent, $ms ms; the DAC leaves the USB bus; not verified until it is read again"
        )
        return WriteResult(
            false,
            emptyList(),
            ms,
            0,
            null,
            pending =
                PendingSave { back ->
                    val state =
                        back.familyState as? FamilyState.Allegro
                            ?: return@PendingSave listOf("the DAC was not read back")

                    KtMicroCodec.mismatches(plan, state.snapshot, state.globalGain)
                },
        )
    }

    suspend fun restoreFlat(device: DacHandle): WriteResult =
        writePlan(device, protocol(device).flatPlan())

    suspend fun sendTestBand1(device: DacHandle): WriteResult =
        withDevice(device) { t ->
            require(protocol(device) == DeviceProtocol.MICRO) { "Test write is Micro-only" }
            val (session, port) = walkPlaySession(device, t, target(device))
            val now = session.read()
            val regs = now.bands.map { it.registers }
            val g = regs[0].gain256 - 128
            if (g < -10 * 256) throw IOException("band 1 already at -10 dB, refusing")
            val writes = regs.mapIndexed { i, r ->
                WalkPlay.BandWrite(
                    r.index,
                    r.freq.toDouble(),
                    (if (i == 0) g else r.gain256) / 256.0,
                    r.q256 / 256.0,
                    r.typeCode,
                    rateHz = DeviceProtocol.MICRO.caps.coefficientRateHz,
                )
            }
            walkPlayWrite(
                session,
                port,
                DevicePlan.Ready(writes, now.preampDb),
                DeviceProtocol.MICRO,
            )
        }

    private suspend fun writePlan(
        device: DacHandle,
        plan: DevicePlan.Ready,
        onProgress: ((Float) -> Unit)? = null,
    ): WriteResult =
        withDevice(device) { t ->
            val p = protocol(device)

            var steps: Steps? = null
            val (session, port) = walkPlaySession(device, t, target(device)) { steps?.tick() }
            steps = Steps(session.plannedFrames(plan), onProgress)
            walkPlayWrite(session, port, plan, p)
        }

    private class Steps(private val total: Int, private val report: ((Float) -> Unit)?) {
        private var done = 0

        fun tick() {
            done++
            report?.invoke(done.toFloat() / maxOf(total, done + 1))
        }
    }

    private fun walkPlaySession(
        device: DacHandle,
        t: HidIo,
        target: DeviceTarget,
        tick: () -> Unit = {},
    ): Pair<WalkPlaySession, WalkPlayPort> {
        val port = WalkPlayPort(t, device, tick = tick)
        return WalkPlaySession(requireNotNull(target.walkplayProfile), port) to port
    }

    private fun snapshotOf(p: DeviceProtocol, s: WalkPlaySnapshot, ms: Long) =
        DacSnapshot(s.firmware, s.slot, s.bands, s.preampDb, ms, p)

    private fun readWalkPlay(device: DacHandle, t: HidIo, target: DeviceTarget): DacSnapshot {
        val p = requireNotNull(target.walkplay)
        val t0 = SystemClock.elapsedRealtime()
        val s = walkPlaySession(device, t, target).first.read()
        val ms = SystemClock.elapsedRealtime() - t0
        UsbLog.line(
            "read: ${if (p.experimental) "${p.caps.name}, " else ""}firmware ${s.firmware}, slot ${s.slot}, preamp ${s.preampDb} dB, $ms ms"
        )
        return snapshotOf(p, s, ms)
    }

    private fun walkPlayWrite(
        session: WalkPlaySession,
        port: WalkPlayPort,
        plan: DevicePlan.Ready,
        p: DeviceProtocol,
    ): WriteResult {
        val r = session.writePlan(plan, explicitHold = true)
        val back = requireNotNull(r.readback)
        val now = SystemClock.elapsedRealtime()
        val writeMs = port.lastSendEndMs - port.firstSendMs
        val readMs = now - port.lastSendEndMs
        val mismatches = session.mismatches(plan, back, requireNotNull(session.lastSlot))
        val verified = mismatches.isEmpty()
        UsbLog.line(
            "write: ${if (verified) "VERIFIED" else "MISMATCH ${mismatches.size}"}, write $writeMs ms, read-back $readMs ms"
        )
        mismatches.forEach { UsbLog.line("  $it") }
        return WriteResult(verified, mismatches, writeMs, readMs, snapshotOf(p, back, readMs))
    }
}
