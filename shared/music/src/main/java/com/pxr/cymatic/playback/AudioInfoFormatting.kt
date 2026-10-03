package com.pxr.cymatic.playback

import java.math.BigDecimal

fun formatSampleRate(rate: Long): String =
    "${BigDecimal.valueOf(rate).movePointLeft(3).stripTrailingZeros().toPlainString()} kHz"
