package com.bringyour.network

/**
 * The device's transport delay (connect/DELAY.md §10.4) as plain data: the
 * sdk's TransportDelay is a gomobile proxy that needs the native library, so
 * the connect screen and the VPN notification read this mirror
 * (ui.connect.transportDelayUi), and their tests build it directly. Per
 * direction, the quantized quality (0 normal to 1 worst), the worst
 * network-limited rate and the worst standing queue in the sdk's window
 * (0: none), and whether the worst reading came from a DNS-carried fallback
 * transport (D10).
 */
data class TransportDelayUi(
    val upQuality: Double = 0.0,
    val downQuality: Double = 0.0,
    val upBitsPerSecond: Long = 0L,
    val downBitsPerSecond: Long = 0L,
    val upQueueDelayMillis: Long = 0L,
    val downQueueDelayMillis: Long = 0L,
    val fallback: Boolean = false,
) {
    /** The indicator follows the worse direction (apple/DELAY.md §1). */
    val quality: Double get() = maxOf(upQuality, downQuality)

    /** The connection is slow: the indicator shows. */
    val slow: Boolean get() = 0.0 < quality

    companion object {
        /** Normal: no delay, nothing to show. Also before the first reading. */
        val None = TransportDelayUi()
    }
}

/**
 * A standing queue delay as the slow internet surfaces show it: whole
 * milliseconds under a second ("640 ms"), one decimal under 10 s ("1.2 s"),
 * then whole seconds ("12 s"). The units are not localized, the same on every
 * platform; values round half up, and a value that rounds to 10 s shows as
 * whole seconds.
 */
internal fun formatTransportDelayMillis(delayMillis: Long): String {
    val millis = delayMillis.coerceAtLeast(0L)
    if (millis < 1_000L) {
        return "$millis ms"
    }
    val tenths = (millis + 50L) / 100L
    if (tenths < 100L) {
        return "${tenths / 10L}.${tenths % 10L} s"
    }
    return "${(millis + 500L) / 1_000L} s"
}

/**
 * A network-limited rate as the slow internet surfaces show it: whole kbit/s
 * under 1 Mbit/s, at least 1 ("48 kb/s"), then Mbit/s with one decimal
 * ("1.4 Mb/s"). The units are not localized; values round half up.
 */
internal fun formatTransportDelayBitRate(bitsPerSecond: Long): String {
    val rate = bitsPerSecond.coerceAtLeast(0L)
    val kilobits = (rate + 500L) / 1_000L
    if (kilobits < 1_000L) {
        return "${kilobits.coerceAtLeast(1L)} kb/s"
    }
    val tenths = (rate + 50_000L) / 100_000L
    return "${tenths / 10L}.${tenths % 10L} Mb/s"
}

/**
 * One direction's line, "Upload delay 640 ms · 48 kb/s": the direction's
 * label, its delay, and its rate when the sdk reports a limited one. A 0
 * value hides its part: no delay hides the line, since it is the direction's
 * delay line, and no limited rate leaves the delay alone.
 */
internal fun transportDelayLine(label: String, delayMillis: Long, bitsPerSecond: Long): String? {
    if (delayMillis <= 0L) {
        return null
    }
    val delay = "$label ${formatTransportDelayMillis(delayMillis)}"
    return if (0L < bitsPerSecond) {
        "$delay · ${formatTransportDelayBitRate(bitsPerSecond)}"
    } else {
        delay
    }
}

/** The upload line then the download line, each only when it shows ([transportDelayLine]). */
internal fun transportDelayLines(
    delay: TransportDelayUi,
    uploadLabel: String,
    downloadLabel: String,
): List<String> = listOfNotNull(
    transportDelayLine(uploadLabel, delay.upQueueDelayMillis, delay.upBitsPerSecond),
    transportDelayLine(downloadLabel, delay.downQueueDelayMillis, delay.downBitsPerSecond),
)
