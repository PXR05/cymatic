// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.native

import io.github.chronosauros.contour.core.DeviceTarget
import io.github.chronosauros.contour.core.WalkPlay

object NativeHidReports {
    enum class Kind(val tag: Int, val controlType: Int) {
        INPUT(0x80, 1),
        OUTPUT(0x90, 2),
        FEATURE(0xB0, 3),
    }

    data class Key(val id: Int, val kind: Kind)

    data class Shape(val rawSizes: Map<Key, Int>, val owners: Map<Key, Int>) {
        fun rawSize(id: Int, kind: Kind): Int =
            rawSizes[Key(id, kind)] ?: error("Missing $kind report $id")

        fun payloadSize(id: Int, kind: Kind): Int = rawSize(id, kind) - 1

        fun qualifyMoondrop(model: MoondropCatalog.Model) {
            val frames = MoondropCodec.readQueries(model)
            require(
                payloadSize(model.reportId, Kind.OUTPUT) >=
                    frames.maxOf { it.minimumDescriptorOutputPayloadBytes }
            ) {
                "Incomplete Moondrop Output descriptor"
            }
            require(payloadSize(model.reportId, Kind.INPUT) >= 36) {
                "Moondrop Input lacks complete coefficient/metadata fields"
            }
            requireSameOwner(model.reportId, Kind.INPUT, Kind.OUTPUT)
        }

        fun qualify(target: DeviceTarget, id: Int) {
            for (kind in listOf(Kind.OUTPUT, Kind.INPUT)) {
                val have = rawSize(id, kind)
                val need = minimumRawBytes(target, kind)
                require(have >= need) {
                    "${target.caps.name} ${kind.name.lowercase()} report 0x%02x is $have bytes with the ID; its protocol needs at least $need"
                        .format(id)
                }
            }
            requireSameOwner(id, Kind.INPUT, Kind.OUTPUT)
        }

        fun qualifyGen1() {
            for (kind in listOf(Kind.OUTPUT, Kind.INPUT)) {
                val have = rawSize(MoondropGen1Catalog.REPORT_ID, kind)
                require(have == MoondropGen1Catalog.REPORT_BYTES) {
                    "Moondrop gen-1 ${kind.name.lowercase()} report 1 is $have bytes with the ID; its protocol needs exactly ${MoondropGen1Catalog.REPORT_BYTES}"
                }
            }
            requireSameOwner(MoondropGen1Catalog.REPORT_ID, Kind.INPUT, Kind.OUTPUT)
        }

        fun requireSameOwner(id: Int, a: Kind, b: Kind) {
            require(owners.getValue(Key(id, a)) == owners.getValue(Key(id, b))) {
                "Report kinds belong to different collections"
            }
        }

        fun wire(id: Int, kind: Kind, payload: ByteArray): ByteArray {
            val n = rawSize(id, kind)
            require(id in 1..255 && payload.size <= n - 1) { "Payload exceeds descriptor" }
            return ByteArray(n).also {
                it[0] = id.toByte()
                payload.copyInto(it, 1)
            }
        }

        fun payload(id: Int, kind: Kind, raw: ByteArray): ByteArray {
            require(raw.size == rawSize(id, kind) && (raw[0].toInt() and 255) == id) {
                "Raw report ID/length mismatch"
            }
            return raw.copyOfRange(1, raw.size)
        }
    }

    const val FIIO_MINIMUM_RAW_BYTES = 17

    fun minimumRawBytes(target: DeviceTarget, kind: Kind): Int =
        when (target.family) {
            DeviceTarget.Family.FIIO -> FIIO_MINIMUM_RAW_BYTES
            DeviceTarget.Family.KT_MICRO -> 1 + KtMicroCodec.PAYLOAD_BYTES
            DeviceTarget.Family.MOONDROP ->
                1 +
                    if (kind == Kind.INPUT) MoondropCodec.MINIMUM_INPUT_PAYLOAD_BYTES
                    else MoondropCodec.BAND_PAYLOAD_BYTES
            DeviceTarget.Family.WALKPLAY_READ_ONLY -> WalkPlay.REPORT_SIZE
            DeviceTarget.Family.MOONDROP_GEN1 -> MoondropGen1Catalog.REPORT_BYTES
            DeviceTarget.Family.WALKPLAY ->
                error("${target.caps.name} is not opened through its HID report descriptor")
        }

    private class Scan(
        val bits: Map<Key, Int>,
        val owners: Map<Key, Int>,
        val vendors: Map<Key, Boolean>,
    )

    fun parse(descriptor: ByteArray, undefinedDesktop: Boolean = false): Shape {
        val scan = scan(descriptor, undefinedDesktop)
        val owners = scan.owners
        val sizes =
            scan.bits
                .filterKeys { it.id != 0 }
                .filterKeys { scan.vendors[it] == true }
                .mapValues { (_, b) ->
                    require(b % 8 == 0 && b / 8 in 1..1023) { "Unsupported report size" }
                    1 + b / 8
                }
        return Shape(sizes, owners.filterKeys { it in sizes })
    }

    fun parseGen1(descriptor: ByteArray): Shape {
        val scan = scan(descriptor, false, consumerPage = true)
        val sizes =
            scan.bits
                .filterKeys { it.id != 0 }
                .filterKeys { scan.vendors[it] == true }
                .mapValues { (_, b) ->
                    require(b % 8 == 0 && b / 8 in 1..1023) { "Unsupported report size" }
                    1 + b / 8
                }
        return Shape(sizes, scan.owners.filterKeys { it in sizes })
    }

    data class Declared(
        val id: Int,
        val kind: Kind,
        val bytes: Int,
        val owner: Int,
        val vendor: Boolean,
    )

    fun inventory(descriptor: ByteArray, undefinedDesktop: Boolean = false): List<Declared> {
        val scan = scan(descriptor, undefinedDesktop)
        return scan.bits
            .filterKeys { it.id != 0 }
            .map { (k, b) ->
                Declared(
                    k.id,
                    k.kind,
                    if (b % 8 == 0) 1 + b / 8 else -1,
                    scan.owners.getValue(k),
                    scan.vendors[k] == true,
                )
            }
            .sortedWith(compareBy({ it.id }, { it.kind.ordinal }))
    }

    private fun scan(
        descriptor: ByteArray,
        undefinedDesktop: Boolean,
        consumerPage: Boolean = false,
    ): Scan {
        fun pageIsVendor(page: Int) = page in 0xFF00..0xFFFF || consumerPage && page == 0x0C
        data class Global(val size: Int = 0, val count: Int = 0, val id: Int = 0, val page: Int = 0)
        var g = Global()
        val stack = ArrayList<Global>()
        val vendorStack = ArrayList<Boolean>()
        val ownerStack = ArrayList<Int>()
        var nextOwner = 0
        var usages = 0
        var usage = -1
        val bits = HashMap<Key, Int>()
        val owners = HashMap<Key, Int>()
        val vendors = HashMap<Key, Boolean>()
        var i = 0
        while (i < descriptor.size) {
            val p = descriptor[i++].toInt() and 255
            require(p != 0xFE) { "Unsupported long descriptor item" }
            val n = if (p and 3 == 3) 4 else p and 3
            require(i + n <= descriptor.size) { "Truncated descriptor" }
            var value = 0
            repeat(n) { value = value or ((descriptor[i++].toInt() and 255) shl (it * 8)) }
            when (p and 0xFC) {
                0x04 -> g = g.copy(page = value)
                0x74 -> g = g.copy(size = value)
                0x94 -> g = g.copy(count = value)
                0x84 -> {
                    require(value in 1..255)
                    g = g.copy(id = value)
                }
                0xA4 -> stack.add(g)
                0xB4 -> {
                    require(stack.isNotEmpty())
                    g = stack.removeAt(stack.lastIndex)
                }
                0x08 -> {
                    usages++
                    usage = if (n <= 2) value else -1
                }
                0xA0 -> {
                    ownerStack.add(ownerStack.firstOrNull() ?: ++nextOwner)
                    vendorStack.add(
                        pageIsVendor(g.page) ||
                            vendorStack.lastOrNull() == true ||
                            undefinedDesktop &&
                                vendorStack.isEmpty() &&
                                value == 1 &&
                                g.page == 0x01 &&
                                usages == 1 &&
                                usage == 0
                    )
                }
                0xC0 -> {
                    require(ownerStack.isNotEmpty())
                    ownerStack.removeAt(ownerStack.lastIndex)
                    vendorStack.removeAt(vendorStack.lastIndex)
                }
                0x80,
                0x90,
                0xB0 -> {
                    require(g.size in 1..32 && g.count in 1..8192 && ownerStack.isNotEmpty())
                    val key = Key(g.id, Kind.entries.single { it.tag == (p and 0xFC) })
                    val owner = ownerStack.first()
                    require(key !in owners || owners[key] == owner) {
                        "Ambiguous report collection"
                    }
                    owners[key] = owner
                    bits[key] = (bits[key] ?: 0) + g.size * g.count
                    vendors[key] =
                        (vendors[key] ?: true) && (vendorStack.last() || g.page in 0xFF00..0xFFFF)
                }
            }
            if ((p and 0xFC) in setOf(0x80, 0x90, 0xB0, 0xA0, 0xC0)) {
                usages = 0
                usage = -1
            }
        }
        require(stack.isEmpty() && ownerStack.isEmpty()) { "Unclosed descriptor state" }
        return Scan(bits, owners, vendors)
    }
}
