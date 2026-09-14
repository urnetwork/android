package com.bringyour.network

/**
 * Relays the platform's memory trim callbacks to the SDK, which maps each
 * level to a reclamation (Sdk.reportMemoryTrimLevel). The Application owns the
 * one instance: it receives every trim callback for the process, whereas a
 * Service also receives them and relaying from both would report each twice.
 *
 * Levels pass through unchanged so the SDK stays the single owner of the
 * mapping. onLowMemory reports TRIM_MEMORY_COMPLETE, its documented
 * equivalent. Nothing is relayed until application state (and so gomobile) is
 * initialized: a cold VPN-service start defers loading the SDK, and a memory
 * callback must not defeat that early-promotion path.
 */
class MemoryTrimRelay(
    private val sdkReady: () -> Boolean,
    private val report: (Long) -> Unit,
) {
    fun onTrimMemory(level: Int) {
        if (sdkReady()) report(level.toLong())
    }

    fun onLowMemory() {
        if (sdkReady()) report(LOW_MEMORY_TRIM_LEVEL)
    }

    companion object {
        // ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        const val LOW_MEMORY_TRIM_LEVEL = 80L
    }
}
