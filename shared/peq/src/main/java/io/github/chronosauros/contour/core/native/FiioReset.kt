// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.BuildChannel
import io.github.chronosauros.contour.core.Proven

object FiioReset {

    const val COMMAND = 0x21

    internal val DATA: List<Int> = listOf(0x00)

    const val SETTLE_MS = 3000L

    private val OFFERED: Set<String> =
        setOf(
            FiioCatalog.MELODY.productName,
            FiioCatalog.TINY_A.productName,
            FiioCatalog.KA15.productName,
        )

    val VERIFIED: Set<String> = setOf(FiioCatalog.KA15.productName, FiioCatalog.MELODY.productName)

    fun offered(config: FiioConfig?): Boolean = config != null && config.productName in OFFERED

    private val NOT_IN_APP: Set<String> = setOf(FiioCatalog.TINY_A.productName)

    fun inApp(config: FiioConfig?): Boolean =
        offered(config) && config != null && config.productName !in NOT_IN_APP

    fun enabled(config: FiioConfig?): Boolean = offered(config) && Proven.on(proven(config))

    fun enabledInApp(config: FiioConfig?): Boolean = inApp(config) && enabled(config)

    fun proven(config: FiioConfig?): Boolean = config != null && config.productName in VERIFIED

    fun replugSeen(
        detachesAtSend: Int,
        detachesNow: Int,
        attachesAtSend: Int,
        attachesNow: Int,
        sawMissing: Boolean,
    ): Boolean = detachesNow > detachesAtSend && (attachesNow > attachesAtSend || sawMissing)

    fun restoreRank(id: String): Int =
        when (id) {
            "volume-steps" -> 0
            "volume" -> 2
            else -> 1
        }

    val ERASE_NOTE =
        "A factory reset may erase the USER slots with their names and the DAC's settings. Contour saves a copy of all of them first and offers to write them back afterwards."

    fun diff(
        config: FiioConfig,
        before: FiioResetBackup,
        after: FiioResetBackup,
        labelOf: (String) -> String = { it },
        valueOf: (String, Int) -> String = { _, v -> v.toString() },
    ): List<String> {
        val out = ArrayList<String>()
        val afterSlots = after.slots.associateBy { it.slot }
        for (b in before.slots.sortedBy { it.slot }) {
            val label = config.slotLabels[b.slot] ?: "Slot ${b.slot}"
            val a = afterSlots[b.slot]
            if (a == null) {
                out += "$label: not read after the reset"
                continue
            }
            if (b.name != a.name)
                out +=
                    "$label name: ${b.name?.ifEmpty { null } ?: "(none)"} -> ${a.name?.ifEmpty { null } ?: "(none)"}"
            val x = b.snapshot.copy(activeSlot = 0)
            val y = a.snapshot.copy(activeSlot = 0)
            if (x != y) {
                val registers =
                    x.bands.zip(y.bands).count { (p, q) -> p != q } +
                        kotlin.math.abs(x.bands.size - y.bands.size)

                val detail =
                    if (Proven.channel == BuildChannel.PERF) " ($registers filter registers differ)"
                    else ""
                out +=
                    "$label EQ: ${x.count} filters, preamp ${x.preampTenths / 10.0} dB -> ${y.count} filters, preamp ${y.preampTenths / 10.0} dB$detail"
            }
        }
        for (id in (before.settings.keys + after.settings.keys).toSortedSet()) {
            val p = before.settings[id]
            val q = after.settings[id]
            if (p != q)
                out +=
                    "${labelOf(id)}: ${p?.let { valueOf(id, it) } ?: "no reply"} -> ${q?.let { valueOf(id, it) } ?: "no reply"}"
        }
        return out
    }
}

data class FiioSlotBackup(val slot: Int, val name: String?, val snapshot: FiioSnapshot) {
    init {
        require(snapshot.activeSlot == slot) {
            "Slot $slot backup holds the registers of slot ${snapshot.activeSlot}"
        }
    }
}

data class FiioResetBackup(
    val productName: String,
    val takenAtMs: Long,
    val activeSlot: Int?,
    val slots: List<FiioSlotBackup>,
    val settings: Map<String, Int>,
    val unreadSettings: List<String> = emptyList(),
) {

    fun problems(config: FiioConfig): List<String> {
        val out = ArrayList<String>()
        if (productName != config.productName)
            return listOf("The backup is of $productName, not ${config.productName}")
        val codec = FiioCodec(config)
        val bySlot = slots.groupBy { it.slot }
        for (slot in config.userSlots.sorted()) {
            val label = config.slotLabels[slot] ?: "Slot $slot"
            val s = bySlot[slot]?.singleOrNull()
            if (s == null) {
                out += "$label was not read"
                continue
            }
            if (config.userSlotNames && s.name == null) out += "$label: the name was not read"
            runCatching { codec.importExact(s.snapshot) }
                .exceptionOrNull()
                ?.let { out += "$label cannot be written back exactly: ${it.message}" }
        }
        (bySlot.keys - config.userSlots).forEach {
            out += "Slot $it is not a USER slot of ${config.productName}"
        }
        return out
    }

    fun toText(): String = buildString {
        appendLine(HEADER)
        appendLine("product $productName")
        appendLine("taken $takenAtMs")
        appendLine("active ${activeSlot ?: "-"}")
        for (s in slots.sortedBy { it.slot }) {
            appendLine(
                "slot ${s.slot} name ${s.name ?: "-"} count ${s.snapshot.count} preamp ${s.snapshot.preampTenths}"
            )
            for (b in s.snapshot.bands.sortedBy { it.index }) appendLine(
                "band ${s.slot} ${b.index} ${b.frequencyHz} ${b.gainTenths} ${b.qHundredths} ${b.typeCode}"
            )
        }
        for ((id, v) in settings.toSortedMap()) appendLine("setting $id $v")
        for (id in unreadSettings.sorted()) appendLine("nosetting $id")
    }

    companion object {
        const val HEADER = "contour-reset-backup 1"

        fun parse(text: String): FiioResetBackup {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            require(lines.firstOrNull() == HEADER) { "Not a Contour reset backup" }
            var product: String? = null
            var taken: Long? = null
            var active: Int? = null
            var haveActive = false
            class Slot(
                val slot: Int,
                val name: String?,
                val count: Int,
                val preamp: Int,
                val bands: ArrayList<FiioRegisters> = ArrayList(),
            )
            val slots = LinkedHashMap<Int, Slot>()
            val settings = HashMap<String, Int>()
            val unread = ArrayList<String>()
            fun num(s: String, line: String) =
                s.toIntOrNull() ?: throw IllegalArgumentException("Bad number in \"$line\"")
            for (line in lines.drop(1)) {
                val p = line.split(' ')
                when (p[0]) {
                    "product" -> product = line.removePrefix("product ").trim()
                    "taken" ->
                        taken =
                            p.getOrNull(1)?.toLongOrNull()
                                ?: throw IllegalArgumentException("Bad line \"$line\"")
                    "active" -> {
                        haveActive = true
                        active = p.getOrNull(1)?.takeIf { it != "-" }?.let { num(it, line) }
                    }
                    "slot" -> {

                        require(
                            p.size == 8 && p[2] == "name" && p[4] == "count" && p[6] == "preamp"
                        ) {
                            "Bad line \"$line\""
                        }
                        val slot = num(p[1], line)
                        require(
                            slots.put(
                                slot,
                                Slot(
                                    slot,
                                    p[3].takeIf { it != "-" },
                                    num(p[5], line),
                                    num(p[7], line),
                                ),
                            ) == null
                        ) {
                            "Slot $slot twice"
                        }
                    }
                    "band" -> {
                        require(p.size == 7) { "Bad line \"$line\"" }
                        val s =
                            slots[num(p[1], line)]
                                ?: throw IllegalArgumentException("Band before its slot: \"$line\"")
                        s.bands +=
                            FiioRegisters(
                                num(p[2], line),
                                num(p[3], line),
                                num(p[4], line),
                                num(p[5], line),
                                num(p[6], line),
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
            require(product != null && taken != null && haveActive) {
                "The backup lacks its product, time or active slot"
            }
            val product0 = product
            return FiioResetBackup(
                product0,
                requireNotNull(taken),
                active,
                slots.values.map { s ->
                    require(s.bands.size == s.count) {
                        "Slot ${s.slot}: ${s.bands.size} bands for count ${s.count}"
                    }
                    FiioSlotBackup(
                        s.slot,
                        s.name,
                        FiioSnapshot(
                            product0,
                            s.slot,
                            s.count,
                            s.preamp,
                            s.bands.sortedBy { it.index },
                        ),
                    )
                },
                settings,
                unread,
            )
        }
    }
}

class FiioResetTicket internal constructor(val productName: String, val backupTakenAtMs: Long) {
    private var spent = false

    internal fun spend(config: FiioConfig) {
        require(config.productName == productName) {
            "The reset confirmation is for $productName, not ${config.productName}"
        }
        require(!spent) { "A reset confirmation is good for one reset" }
        spent = true
    }
}

data class FiioResetSent(val echoed: Boolean, val note: String?)

data class FiioRestoreResult(val lines: List<String>, val ok: Boolean)

class FiioResetFlow(private val config: FiioConfig) {
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

    var backup: FiioResetBackup? = null
        private set

    var after: FiioResetBackup? = null
        private set

    var changes: List<String> = emptyList()
        private set

    fun begin(taken: FiioResetBackup) {
        require(step == Step.START) { "A reset is already under way" }
        require(FiioReset.offered(config)) {
            "${config.productName} has no factory reset in Contour"
        }
        val problems = taken.problems(config)
        require(problems.isEmpty()) {
            "Backup incomplete, nothing sent: " + problems.joinToString("; ")
        }
        backup = taken
        step = Step.BACKED_UP
    }

    fun confirm(ownerConsent: Boolean): FiioResetTicket {
        require(step == Step.BACKED_UP) { "Back the DAC up first" }
        require(ownerConsent) { "The owner has not confirmed the reset" }
        step = Step.CONFIRMED
        return FiioResetTicket(config.productName, requireNotNull(backup).takenAtMs)
    }

    fun sent() {
        require(step == Step.CONFIRMED) { "No confirmed reset to send" }
        step = Step.SENT
    }

    fun compared(
        read: FiioResetBackup,
        labelOf: (String) -> String = { it },
        valueOf: (String, Int) -> String = { _, v -> v.toString() },
    ): List<String> {
        require(step == Step.SENT) { "Nothing was sent" }
        require(read.productName == config.productName) { "The read is of ${read.productName}" }
        after = read
        changes = FiioReset.diff(config, requireNotNull(backup), read, labelOf, valueOf)
        step = Step.COMPARED
        return changes
    }

    fun restored() {
        require(step == Step.COMPARED) { "Compare the DAC with the backup first" }
        step = Step.RESTORED
    }
}
