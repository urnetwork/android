package com.bringyour.network

/**
 * Tracks the app's preferred physical network, including loss -> replacement.
 * The first callback is a baseline; every later identity transition means
 * existing sockets may still be pinned to the former path.
 */
internal class DefaultNetworkTracker<T> {
    private var initialized = false
    private var current: T? = null
    private var lostSinceLastAvailable = false

    fun onAvailable(network: T): Boolean {
        val changed = initialized && (lostSinceLastAvailable || current != network)
        initialized = true
        current = network
        lostSinceLastAvailable = false
        return changed
    }

    fun onLost(network: T) {
        if (initialized && current == network) {
            current = null
            lostSinceLastAvailable = true
        }
    }
}
