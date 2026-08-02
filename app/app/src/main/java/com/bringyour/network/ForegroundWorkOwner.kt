package com.bringyour.network

/**
 * Starts presentation work only while the app process is foregrounded and
 * stops it exactly once on background/close. The start and stop functions own
 * their concrete coroutine jobs or subscriptions.
 */
internal class ForegroundWorkOwner(
    private val start: () -> Unit,
    private val stop: () -> Unit,
) {
    private var foreground = false

    fun setForeground(nextForeground: Boolean) {
        if (foreground == nextForeground) {
            return
        }
        foreground = nextForeground
        if (foreground) {
            start()
        } else {
            stop()
        }
    }

    fun close() {
        if (foreground) {
            foreground = false
            stop()
        }
    }
}
