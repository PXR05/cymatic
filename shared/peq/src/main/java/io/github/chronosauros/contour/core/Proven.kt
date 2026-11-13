// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core

enum class BuildChannel {
    RELEASE,
    PERF,
}

object Proven {
    @Volatile var channel: BuildChannel = BuildChannel.RELEASE

    fun channelFor(buildType: String): BuildChannel =
        if (buildType == "perf" || buildType == "debug") BuildChannel.PERF else BuildChannel.RELEASE

    fun allows(proven: Boolean, channel: BuildChannel): Boolean =
        proven || channel == BuildChannel.PERF

    fun on(proven: Boolean): Boolean = allows(proven, channel)

    internal inline fun <T> asChannel(ch: BuildChannel, block: () -> T): T {
        val before = channel
        channel = ch
        try {
            return block()
        } finally {
            channel = before
        }
    }
}
