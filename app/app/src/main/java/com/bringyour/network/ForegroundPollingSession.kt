package com.bringyour.network

internal enum class ForegroundPollingResume {
    INACTIVE,
    ACTIVE,
    EXPIRED,
}

/**
 * Keeps the logical state and deadline of a bounded polling session separate
 * from the foreground-owned coroutine that executes it. Backgrounding pauses
 * work without silently discarding an in-progress payment confirmation.
 */
internal class ForegroundPollingSession(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var deadlineMillis = 0L

    var active = false
        private set

    fun start(maxDurationMillis: Long) {
        require(maxDurationMillis >= 0)
        val now = nowMillis()
        deadlineMillis =
            if (Long.MAX_VALUE - maxDurationMillis < now) {
                Long.MAX_VALUE
            } else {
                now + maxDurationMillis
            }
        active = true
    }

    fun pause() {
        // The foreground owner cancels the physical job. Logical state stays
        // armed so resume can continue or perform one final expired refresh.
    }

    fun resume(): ForegroundPollingResume {
        if (!active) {
            return ForegroundPollingResume.INACTIVE
        }
        return if (nowMillis() >= deadlineMillis) {
            ForegroundPollingResume.EXPIRED
        } else {
            ForegroundPollingResume.ACTIVE
        }
    }

    fun hasExpired(): Boolean {
        return active && nowMillis() >= deadlineMillis
    }

    fun stop() {
        active = false
        deadlineMillis = 0L
    }
}
