// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

object KtMicroReset {

    const val COMMAND = 0x43

    const val SETTLE_MS = 3000L

    val ERASE_NOTE =
        "CLEAR (the vendor's flash clear) erases the EQ the Allegro Mini saved and puts the manufacturer's bands back; what else it erases is unknown. " +
            "Contour reads the EQ switch, all five bands and the Global Gain first and writes them back afterwards."

    internal fun restorable(model: KtMicroCatalog.Model): Set<Int> =
        setOf(model.registers.enableRegister) +
            model.registers.bands.flatMap { listOf(it.gainFrequency, it.qType) }

    private fun hex(v: List<Int>) = v.joinToString(" ") { "%02x".format(it) }

    fun diff(before: KtMicroResetBackup, after: KtMicroResetBackup): List<String> {
        val out = ArrayList<String>()
        val a = after.snapshot.registers.associateBy { it.register }
        for (r in before.snapshot.registers) {
            val now = a[r.register]
            if (now == null)
                out +=
                    "register 0x%02x (%s): not read after the reset"
                        .format(r.register, name(r.register))
            else if (now.value != r.value)
                out +=
                    "register 0x%02x (%s): %s -> %s"
                        .format(r.register, name(r.register), hex(r.value), hex(now.value))
        }
        val g0 = before.globalGain
        val g1 = after.globalGain
        if (g0?.value != g1?.value)
            out +=
                "register 0x3b (Global Gain): ${g0?.value?.let(::hex) ?: "no reply"} -> ${g1?.value?.let(::hex) ?: "no reply"}"
        return out
    }

    fun name(register: Int): String =
        when (register) {
            KtMicroCodec.EQ_ENABLE_REGISTER -> "EQ switch"
            in 0x26..0x2F ->
                "band ${(register - 0x26) / 2 + 1} " +
                    if ((register - 0x26) % 2 == 0) "gain and frequency" else "Q and type"
            KtMicroCodec.DIGITAL_GAIN_REGISTER -> "digital DAC gain"
            KtMicroCodec.GLOBAL_GAIN_REGISTER -> "Global Gain"
            KtMicroCodec.CHANNEL_FLAGS_REGISTER -> "channel flags"
            else -> "register"
        }
}

class KtMicroResetBackup(
    val takenAtMs: Long,
    val snapshot: KtMicroCodec.Snapshot,
    val globalGain: KtMicroCodec.RegisterValue?,
) {

    fun problems(model: KtMicroCatalog.Model): List<String> {
        val out = ArrayList<String>()
        if (snapshot.model != model) out += "The backup is of another KT model"
        val have = snapshot.registers.map { it.register }.toSet()
        KtMicroReset.restorable(model)
            .filterNot { it in have }
            .forEach {
                out += "Register 0x%02x (%s) was not read".format(it, KtMicroReset.name(it))
            }
        if (globalGain == null)
            out += "The Global Gain (0x3B) gave no valid reply: it could not be written back"
        else if (KtMicroCodec.globalGainStep(globalGain) == null)
            out +=
                "The Global Gain (0x3B) reads ${globalGain.value.joinToString(" ") { "%02x".format(it) }}, not a step 1-15: it could not be written back"
        return out
    }

    fun toText(): String = buildString {
        appendLine(HEADER)
        appendLine("model ${snapshot.model.productName}")
        appendLine("taken $takenAtMs")
        for (r in snapshot.registers.sortedBy { it.register }) appendLine(
            "register 0x%02x %s".format(r.register, r.value.joinToString(" ") { "%02x".format(it) })
        )
        globalGain?.let {
            appendLine(
                "register 0x%02x %s"
                    .format(it.register, it.value.joinToString(" ") { v -> "%02x".format(v) })
            )
        }
    }

    companion object {
        const val HEADER = "contour-ktmicro-reset-backup 1"
    }
}

class KtMicroResetTicket internal constructor(val backupTakenAtMs: Long) {
    private var spent = false

    internal fun spend() {
        require(!spent) { "A reset confirmation is good for one reset" }
        spent = true
    }
}

data class KtMicroResetSent(val acknowledged: Boolean, val note: String?)

data class KtMicroRestoreResult(val lines: List<String>, val ok: Boolean)

class KtMicroResetFlow(private val model: KtMicroCatalog.Model) {
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

    var backup: KtMicroResetBackup? = null
        private set

    var after: KtMicroResetBackup? = null
        private set

    var changes: List<String> = emptyList()
        private set

    fun begin(taken: KtMicroResetBackup) {
        require(step == Step.START) { "A reset is already under way" }
        require(model in KtMicroCatalog.allModels) { "Unqualified KT model" }
        require(model.resetOffered) {
            "${model.productName} has no reset in Contour: CLEAR (0x43) is never sent to it"
        }
        val problems = taken.problems(model)
        require(problems.isEmpty()) {
            "Backup incomplete, nothing sent: " + problems.joinToString("; ")
        }
        backup = taken
        step = Step.BACKED_UP
    }

    fun confirm(ownerConsent: Boolean): KtMicroResetTicket {
        require(step == Step.BACKED_UP) { "Back the DAC up first" }
        require(ownerConsent) { "The owner has not confirmed the reset" }
        step = Step.CONFIRMED
        return KtMicroResetTicket(requireNotNull(backup).takenAtMs)
    }

    fun sent() {
        require(step == Step.CONFIRMED) { "No confirmed reset to send" }
        step = Step.SENT
    }

    fun compared(read: KtMicroResetBackup): List<String> {
        require(step == Step.SENT) { "Nothing was sent" }
        after = read
        changes = KtMicroReset.diff(requireNotNull(backup), read)
        step = Step.COMPARED
        return changes
    }

    fun restored() {
        require(step == Step.COMPARED) { "Compare the DAC with the backup first" }
        step = Step.RESTORED
    }
}
