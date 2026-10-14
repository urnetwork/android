package com.bringyour.network

import java.util.concurrent.locks.ReentrantLock

/**
 * Owns the actual cold packet-flow construction and cancellation boundary.
 * The native factory transfers its close action only when it returns. Native
 * completion may happen earlier and must not read that unpublished action.
 * Completion marks state and notifies the existing caller; native run owns its
 * deferred cancellation. Explicit close still cancels the published native loop.
 */
internal class PacketFlow(
    createIoLoop: (done: () -> Unit) -> (() -> Unit),
    private val endCallback: (PacketFlow) -> Unit,
) {
    private val stateLock = ReentrantLock()
    private var active = true

    private val closeIoLoop = createIoLoop {
        markInactive()
        endCallback(this)
    }

    private fun markInactive() {
        stateLock.lock()
        try {
            active = false
        } finally {
            stateLock.unlock()
        }
    }

    fun close() {
        markInactive()
        closeIoLoop()
    }

    fun isActive(): Boolean {
        stateLock.lock()
        try {
            return active
        } finally {
            stateLock.unlock()
        }
    }
}
