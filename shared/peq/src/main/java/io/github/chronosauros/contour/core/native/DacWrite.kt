// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

object DacWrite {

    val NEVER_SEND: Set<Int> = ProbePlan.FIIO_FORBIDDEN

    val READ_ONLY: Set<Int> = setOf(0x20)

    fun checkSetting(config: FiioConfig, s: DacSetting): Int {
        val cmd = requireNotNull(s.writeCommand) { "${s.label} has no write command" }
        require(cmd in 0..255) { "${s.label}: bad command $cmd" }
        require(cmd !in NEVER_SEND) { "0x%02x is on the never-send list".format(cmd) }
        require(cmd !in READ_ONLY) {
            "${s.label} (0x%02x) is read-only: switching it re-enumerates the DAC".format(cmd)
        }
        require(config.settings.any { it.id == s.id && it.writeCommand == cmd }) {
            "${s.label} (0x%02x) is not in the ${config.productName} list".format(cmd)
        }
        return cmd
    }

    fun hardMax(s: DacSetting): Int = maxOf(s.max, s.maxRule?.maxima?.maxOrNull() ?: s.max)

    fun checkValue(s: DacSetting, v: Int) {
        when (s.kind) {
            SettingKind.TOGGLE,
            SettingKind.CHOICE ->
                require(v in s.options.indices) {
                    "${s.label}: $v is not an option (0..${s.options.lastIndex})"
                }
            SettingKind.LEVEL ->
                require(v in s.min..hardMax(s)) {
                    "${s.label}: $v is outside ${s.min}..${hardMax(s)}"
                }
            SettingKind.BALANCE ->
                require(v in -s.max..s.max) { "${s.label}: $v is outside -${s.max}..${s.max}" }
        }
    }

    fun check(config: FiioConfig, s: DacSetting, v: Int): Int =
        checkSetting(config, s).also { checkValue(s, v) }

    fun payload(s: DacSetting, v: Int): List<Int> =
        when (s.kind) {
            SettingKind.BALANCE -> if (v < 0) listOf(0, -v) else listOf(v, 0)
            else -> listOf(v)
        }
}
