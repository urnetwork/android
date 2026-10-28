package com.bringyour.network.ui.connect

import com.bringyour.network.TransportDelayUi
import com.bringyour.network.formatTransportDelayBitRate
import com.bringyour.network.formatTransportDelayMillis

/**
 * What the connect screen's slow internet pill says (apple/DELAY.md §5.2, the
 * Apple pill's SlowInternetPillContent), as a value so its rules are unit
 * tested (SlowInternetPillContentTest). "Poor signal", or "Fallback" on the
 * fallback connection; then each direction's standing delay, or its limited
 * rate when it has no delay, and nothing when it has neither. The larger
 * delay is the worse direction, drawn amber. Recovered, the pill says
 * "Internet back to normal" instead.
 */
internal data class SlowInternetPillContent(
    val label: Label,
    /** "640 ms", or "48 kb/s" with no delay; null hides the direction. */
    val up: String?,
    val down: String?,
    /** The larger delay's direction; null when the delays are equal. */
    val worse: Direction?,
    /** The few seconds after a return to normal, before the pill leaves. */
    val recovered: Boolean = false,
) {

    enum class Label {
        POOR_SIGNAL,
        FALLBACK,
    }

    enum class Direction {
        UP,
        DOWN,
    }

    /** What the pill says for a slow reading. */
    constructor(reading: TransportDelayUi) : this(
        label = if (reading.fallback) Label.FALLBACK else Label.POOR_SIGNAL,
        up = directionText(reading.upQueueDelayMillis, reading.upBitsPerSecond),
        down = directionText(reading.downQueueDelayMillis, reading.downBitsPerSecond),
        worse = when {
            reading.downQueueDelayMillis < reading.upQueueDelayMillis -> Direction.UP
            reading.upQueueDelayMillis < reading.downQueueDelayMillis -> Direction.DOWN
            else -> null
        },
    )

    /**
     * With room for one direction: the worse one, or the download when
     * neither is worse (the upload when only it shows).
     */
    val worseOnly: SlowInternetPillContent
        get() = when (worse ?: if (down != null) Direction.DOWN else Direction.UP) {
            Direction.UP -> copy(down = null)
            Direction.DOWN -> copy(up = null)
        }

    /** With no room for numbers: the tunnel and the label. */
    val labelOnly: SlowInternetPillContent
        get() = copy(up = null, down = null)

    companion object {
        /**
         * What the pill shows once [delay] arrives, after [shown] (null when
         * the pill is not up, or is leaving):
         * - while the level is above 0, the slow content, which also takes
         *   the recovered state's place;
         * - on a return to normal from the slow content, the recovered
         *   state, which then holds;
         * - null, the pill leaving, when the reading goes away (not
         *   [available]: no connection is requested) or something more
         *   severe has the top of the screen ([hidden]). Neither is a
         *   recovery.
         */
        fun next(
            shown: SlowInternetPillContent?,
            delay: TransportDelayUi,
            available: Boolean,
            hidden: Boolean,
        ): SlowInternetPillContent? {
            if (hidden || !available) {
                return null
            }
            if (delay.slow) {
                return SlowInternetPillContent(delay)
            }
            if (shown == null) {
                return null
            }
            if (shown.recovered) {
                return shown
            }
            return shown.labelOnly.copy(worse = null, recovered = true)
        }
    }
}

/** A direction's text: its standing delay, else its limited rate, else none. A 0 value is none. */
private fun directionText(delayMillis: Long, bitsPerSecond: Long): String? = when {
    0L < delayMillis -> formatTransportDelayMillis(delayMillis)
    0L < bitsPerSecond -> formatTransportDelayBitRate(bitsPerSecond)
    else -> null
}
