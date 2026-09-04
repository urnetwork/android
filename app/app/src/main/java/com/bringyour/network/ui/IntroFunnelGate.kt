package com.bringyour.network.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot gate for the intro funnel.
 *
 * The SDK local-state flag is the only source of truth: it is armed when a
 * network is created and cleared the moment the funnel is presented, so a
 * recreated activity (rotation, process death, relaunch) reads "already
 * prompted" and never shows the funnel again. [allow] mirrors the flag in
 * memory and drops synchronously with the clear, so the presenting effect
 * never re-runs against a stale "armed" snapshot.
 */
class IntroFunnelGate(
    private val readCanPrompt: () -> Boolean,
    private val clearCanPrompt: () -> Unit,
) {
    private val _allow = MutableStateFlow(readCanPrompt())
    val allow: StateFlow<Boolean> = _allow.asStateFlow()

    /** Marks the funnel as prompted: persists the clear and drops [allow] together. */
    fun markPrompted() {
        clearCanPrompt()
        _allow.value = false
    }

    /** Re-reads the persisted flag, for when the device behind it is replaced. */
    fun refresh() {
        val next = readCanPrompt()
        if (_allow.value != next) {
            _allow.value = next
        }
    }

    companion object {
        /** The funnel shows only for a non-pro network whose flag is still armed. */
        fun shouldPrompt(isPro: Boolean, canPrompt: Boolean): Boolean = !isPro && canPrompt
    }
}
