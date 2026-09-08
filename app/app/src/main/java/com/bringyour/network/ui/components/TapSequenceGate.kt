package com.bringyour.network.ui.components

/**
 * Counts a hidden tap sequence: `count` taps, each within `windowMillis` of
 * the previous one, complete the sequence. A gap longer than the window
 * resets the count to zero, and a completed sequence resets it too, so the
 * taps after a completion start a fresh sequence. Pure, single-threaded;
 * the caller supplies the clock.
 */
class TapSequenceGate(
    private val count: Int = 5,
    private val windowMillis: Long = 2_000L,
) {
    private var taps = 0
    private var lastTapMillis = 0L

    /** The number of taps in the current sequence, for tests. */
    val progress: Int
        get() = taps

    /** Registers a tap at `nowMillis`; true when this tap completes the sequence. */
    fun tap(nowMillis: Long): Boolean {
        if (taps > 0 && nowMillis - lastTapMillis > windowMillis) {
            taps = 0
        }
        lastTapMillis = nowMillis
        taps += 1
        if (taps < count) {
            return false
        }
        taps = 0
        return true
    }

    fun reset() {
        taps = 0
    }
}
