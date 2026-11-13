// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.BuildChannel
import io.github.chronosauros.contour.core.DeviceProtocol
import io.github.chronosauros.contour.core.Proven
import io.github.chronosauros.contour.core.WalkPlay

object WalkPlayReset {

    val OFFERED: Set<String> = setOf("micro", "echo-a", "trn")

    val VERIFIED: Set<String> = emptySet()

    fun offered(profile: WalkPlayProfile): Boolean = profile.id in OFFERED

    fun enabled(profile: WalkPlayProfile): Boolean = offered(profile) && Proven.on(proven(profile))

    fun proven(profile: WalkPlayProfile): Boolean = profile.id in VERIFIED

    val PURE_HZ: List<Int> = listOf(50, 200, 500, 1000, 2500, 5000, 10000, 15000)
    const val PURE_Q = 0.75
    const val PURE_TAG = 1
    const val PURE_BAND_LEN = WalkPlay.BAND_LEN

    private const val BAND_GAP_MS = 20L
    private const val STEP_GAP_MS = 50L
    private const val FLASH_GAP_MS = 200L

    const val SETTLE_MS = 3000L

    val ERASE_NOTE: String
        get() =
            "A factory reset puts the EQ back to the manufacturer's flat bands and 0 dB, and may erase more" +
                (if (Proven.channel == BuildChannel.PERF) " (two of its steps are not understood). "
                else ". ") +
                "Contour saves a copy of all bands, the preamp and every setting it can read first and offers to write them back afterwards."

    internal fun pureBands(): List<ByteArray> = PURE_HZ.mapIndexed { i, hz ->
        WalkPlay.bandWriteReport(
            WalkPlay.BandWrite(i, hz.toDouble(), 0.0, PURE_Q, WalkPlay.TYPE_PK),
            PURE_TAG,
            PURE_BAND_LEN,
        )
    }

    internal fun sequence(
        profile: WalkPlayProfile,
        ticket: WalkPlayResetTicket,
    ): List<WalkPlay.Step> {
        require(offered(profile)) { "${profile.name} has no factory reset in Contour" }
        ticket.spend(profile)
        val steps = ArrayList<WalkPlay.Step>()
        steps += WalkPlay.Step(WalkPlay.preampWriteReport(0.0), STEP_GAP_MS)
        pureBands().forEachIndexed { i, r ->
            steps += WalkPlay.Step(r, if (i == PURE_HZ.lastIndex) 120 else BAND_GAP_MS)
        }
        steps +=
            WalkPlay.Step(
                WalkPlay.report(WalkPlay.WRITE, 0x0A, 0x04, 0x00, 0x00, 0xFF, 0xFF),
                STEP_GAP_MS,
            )
        steps += WalkPlay.Step(WalkPlay.report(WalkPlay.WRITE, 0x01, WalkPlay.END), FLASH_GAP_MS)
        for (n in 1..4) steps +=
            WalkPlay.Step(WalkPlay.report(WalkPlay.WRITE, 0x05, n), BAND_GAP_MS)
        steps += WalkPlay.Step(WalkPlay.report(WalkPlay.WRITE, 0x17, WalkPlay.END), FLASH_GAP_MS)
        return steps
    }

    fun diff(
        before: WalkPlayResetBackup,
        after: WalkPlayResetBackup,
        labelOf: (String) -> String = { it },
        valueOf: (String, Int) -> String = { _, v -> v.toString() },
    ): List<String> {
        val out = ArrayList<String>()
        val a = after.bands.associateBy { it.index }
        for (b in before.bands.sortedBy { it.index }) {
            val r = a[b.index]
            if (r == null) {
                out += "band ${b.index + 1}: not read after the reset"
                continue
            }
            if (r != b)
                out +=
                    if (Proven.channel == BuildChannel.PERF)
                        "band ${b.index + 1}: ${bandText(b)} -> ${bandText(r)}"
                    else "band ${b.index + 1}: changed"
        }
        if (before.preampDb != after.preampDb)
            out += "preamp: ${before.preampDb} dB -> ${after.preampDb} dB"
        if (before.slot != after.slot) out += "slot byte: ${before.slot} -> ${after.slot}"
        for (id in (before.settings.keys + after.settings.keys).toSortedSet()) {
            val p = before.settings[id]
            val q = after.settings[id]
            if (p != q)
                out +=
                    "${labelOf(id)}: ${p?.let { valueOf(id, it) } ?: "no reply"} -> ${q?.let { valueOf(id, it) } ?: "no reply"}"
        }
        return out
    }

    private fun bandText(r: WalkPlay.Registers) =
        "type ${r.typeCode} ${r.freq} Hz Q ${r.q256}/256 gain ${r.gain256}/256"
}

data class WalkPlayResetBackup(
    val profileId: String,
    val takenAtMs: Long,
    val firmware: String,
    val slot: Int,
    val preampDb: Int,
    val bands: List<WalkPlay.Registers>,
    val settings: Map<String, Int>,
    val unreadSettings: List<String> = emptyList(),
) {

    fun writes(rateHz: Double = WalkPlay.DESIGN_FS): List<WalkPlay.BandWrite> =
        bands
            .sortedBy { it.index }
            .map { r ->
                WalkPlay.BandWrite(
                    r.index,
                    r.freq.toDouble(),
                    r.gain256 / 256.0,
                    r.q256 / 256.0,
                    r.typeCode,
                    rateHz = rateHz,
                )
            }

    fun problems(profile: WalkPlayProfile): List<String> {
        if (profileId != profile.id) return listOf("The backup is of $profileId, not ${profile.id}")
        val out = ArrayList<String>()
        if (
            bands.size != profile.bands ||
                bands.map { it.index }.sorted() != (0 until profile.bands).toList()
        )
            out += "Expected all ${profile.bands} bands, the backup holds ${bands.size}"
        if (preampDb !in Byte.MIN_VALUE..Byte.MAX_VALUE)
            out += "The preamp ($preampDb dB) does not fit one byte"
        else if (preampDb > profile.preampMaxDb)
            out +=
                "The DAC holds a preamp of $preampDb dB, above the ${profile.preampMaxDb} dB Contour writes: it could not be written back"
        val len =
            if (profile.protocol == DeviceProtocol.TRN) WalkPlay.TRN_BAND_LEN else WalkPlay.BAND_LEN
        for (w in
            runCatching { writes(profile.caps.coefficientRateHz) }.getOrDefault(emptyList())) {
            val n = w.index + 1
            if (w.typeCode !in WalkPlay.TYPE_LSQ..WalkPlay.TYPE_HP) {
                out += "Band $n: type ${w.typeCode} cannot be written back"
                continue
            }
            runCatching { WalkPlay.bandWriteReport(w, slot, len) }
                .exceptionOrNull()
                ?.let { out += "Band $n cannot be written back exactly: ${it.message}" }
        }
        return out
    }

    fun toText(): String = buildString {
        appendLine(HEADER)
        appendLine("profile $profileId")
        appendLine("taken $takenAtMs")
        appendLine("firmware ${firmware.ifEmpty { "-" }}")
        appendLine("slot $slot")
        appendLine("preamp $preampDb")
        for (b in bands.sortedBy { it.index }) appendLine(
            "band ${b.index} ${b.freq} ${b.q256} ${b.gain256} ${b.typeCode}"
        )
        for ((id, v) in settings.toSortedMap()) appendLine("setting $id $v")
        for (id in unreadSettings.sorted()) appendLine("nosetting $id")
    }

    companion object {
        const val HEADER = "contour-walkplay-reset-backup 1"

        fun parse(text: String): WalkPlayResetBackup {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            require(lines.firstOrNull() == HEADER) { "Not a Contour WalkPlay reset backup" }
            var profile: String? = null
            var taken: Long? = null
            var firmware: String? = null
            var slot: Int? = null
            var preamp: Int? = null
            val bands = ArrayList<WalkPlay.Registers>()
            val settings = HashMap<String, Int>()
            val unread = ArrayList<String>()
            fun num(s: String, line: String) =
                s.toIntOrNull() ?: throw IllegalArgumentException("Bad number in \"$line\"")
            for (line in lines.drop(1)) {
                val p = line.split(' ')
                when (p[0]) {
                    "profile" ->
                        profile =
                            p.getOrNull(1) ?: throw IllegalArgumentException("Bad line \"$line\"")
                    "taken" ->
                        taken =
                            p.getOrNull(1)?.toLongOrNull()
                                ?: throw IllegalArgumentException("Bad line \"$line\"")
                    "firmware" ->
                        firmware =
                            (p.getOrNull(1) ?: throw IllegalArgumentException("Bad line \"$line\""))
                                .let { if (it == "-") "" else it }
                    "slot" -> slot = num(p.getOrNull(1) ?: "", line)
                    "preamp" -> preamp = num(p.getOrNull(1) ?: "", line)
                    "band" -> {
                        require(p.size == 6) { "Bad line \"$line\"" }
                        bands +=
                            WalkPlay.Registers(
                                num(p[1], line),
                                num(p[2], line),
                                num(p[3], line),
                                num(p[4], line),
                                num(p[5], line),
                            )
                    }
                    "setting" -> {
                        require(p.size == 3) { "Bad line \"$line\"" }
                        require(settings.put(p[1], num(p[2], line)) == null) {
                            "Setting ${p[1]} twice"
                        }
                    }
                    "nosetting" -> {
                        require(p.size == 2) { "Bad line \"$line\"" }
                        unread += p[1]
                    }
                    else -> throw IllegalArgumentException("Unknown line \"$line\"")
                }
            }
            require(
                profile != null &&
                    taken != null &&
                    firmware != null &&
                    slot != null &&
                    preamp != null
            ) {
                "The backup lacks its profile, time, firmware, slot or preamp"
            }
            return WalkPlayResetBackup(
                profile,
                taken,
                firmware,
                slot,
                preamp,
                bands.sortedBy { it.index },
                settings,
                unread,
            )
        }
    }
}

class WalkPlayResetTicket internal constructor(val profileId: String, val backupTakenAtMs: Long) {
    private var spent = false

    internal fun spend(profile: WalkPlayProfile) {
        require(profile.id == profileId) {
            "The reset confirmation is for $profileId, not ${profile.id}"
        }
        require(!spent) { "A reset confirmation is good for one reset" }
        spent = true
    }
}

data class WalkPlayResetSent(val steps: Int, val note: String?)

data class WalkPlayRestoreResult(val lines: List<String>, val ok: Boolean)

class WalkPlayResetFlow(private val profile: WalkPlayProfile) {
    enum class Step {
        START,
        BACKED_UP,
        CONFIRMED,
        SENT,
        COMPARED,
        RESTORED,
    }

    var step: Step = Step.START
        private set

    var backup: WalkPlayResetBackup? = null
        private set

    var after: WalkPlayResetBackup? = null
        private set

    var changes: List<String> = emptyList()
        private set

    fun begin(taken: WalkPlayResetBackup) {
        require(step == Step.START) { "A reset is already under way" }
        require(WalkPlayReset.offered(profile)) {
            "${profile.name} has no factory reset in Contour"
        }
        val problems = taken.problems(profile)
        require(problems.isEmpty()) {
            "Backup incomplete, nothing sent: " + problems.joinToString("; ")
        }
        backup = taken
        step = Step.BACKED_UP
    }

    fun confirm(ownerConsent: Boolean): WalkPlayResetTicket {
        require(step == Step.BACKED_UP) { "Back the DAC up first" }
        require(ownerConsent) { "The owner has not confirmed the reset" }
        step = Step.CONFIRMED
        return WalkPlayResetTicket(profile.id, requireNotNull(backup).takenAtMs)
    }

    fun sent() {
        require(step == Step.CONFIRMED) { "No confirmed reset to send" }
        step = Step.SENT
    }

    fun compared(
        read: WalkPlayResetBackup,
        labelOf: (String) -> String = { it },
        valueOf: (String, Int) -> String = { _, v -> v.toString() },
    ): List<String> {
        require(step == Step.SENT) { "Nothing was sent" }
        require(read.profileId == profile.id) { "The read is of ${read.profileId}" }
        after = read
        changes = WalkPlayReset.diff(requireNotNull(backup), read, labelOf, valueOf)
        step = Step.COMPARED
        return changes
    }

    fun restored() {
        require(step == Step.COMPARED) { "Compare the DAC with the backup first" }
        step = Step.RESTORED
    }
}
