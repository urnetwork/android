package com.bringyour.network

/**
 * Every timing value of [TunnelRecoveryPolicy]. The defaults are the shipped
 * behavior; tests and callers pass their own instance to change any of them.
 */
internal data class TunnelRecoverySettings(
    /**
     * Coalesces a burst of path signals into one transport recovery; 0 does not
     * wait for the burst to settle (the minimum interval still spaces kicks).
     */
    val transportRecoveryDebounceMillis: Long = 350L,
    /**
     * The least time between two transport recoveries. Every kick cancels any
     * dial in flight, so a default that flaps re-dials once per interval
     * instead of once per flip. 0 leaves only the debounce between kicks.
     */
    val minimumTransportRecoveryIntervalMillis: Long = 2_000L,
    /** Coalesces wake signals (screen-on, unlock, foreground) before the probe pass; 0 audits each one. */
    val wakeAuditDebounceMillis: Long = 350L,
    /**
     * How long a wake audit lets the probe pass run before judging the provider
     * window; 0 judges it at once, before the probes can answer.
     */
    val wakeHealthGraceMillis: Long = 5_000L,
)

internal data class ScheduledRecovery(
    val generation: Long,
    val delayMillis: Long,
)

internal data class TransportRecovery(
    val reasons: Set<String>,
    val physicalPathChange: Boolean,
)

internal data class WakeHealthAudit(
    val generation: Long,
    val startedAtMillis: Long,
    val reasons: Set<String>,
    val delayMillis: Long,
)

internal data class WakeHealthFacts(
    val connectRequested: Boolean,
    val physicalNetworkAvailable: Boolean,
    val providerCount: Int,
)

internal enum class WakeHealthDecision {
    INVALIDATED,
    LOCAL_MODE,
    OFFLINE,
    PROVIDERS_HEALTHY,
    COVERED_BY_TRANSPORT_RECOVERY,
    RECOVER_TRANSPORTS,
}

/**
 * Main-looper state machine for wake and network recovery. Android callbacks
 * arrive in bursts (screen-on, unlock, process foreground, capabilities and
 * default-network changes); generations coalesce those into one bounded action.
 * All timing comes from [settings].
 */
internal class TunnelRecoveryPolicy(
    private val settings: TunnelRecoverySettings = TunnelRecoverySettings(),
) {
    private var transportGeneration = 0L
    private var wakeGeneration = 0L
    private var lastTransportRecoveryAt: Long? = null
    private var lastPhysicalPathSignalAt: Long? = null
    private val pendingTransportReasons = linkedSetOf<String>()
    private var pendingPhysicalPathChange = false
    private val pendingWakeReasons = linkedSetOf<String>()

    fun requestTransportRecovery(
        nowMillis: Long,
        reason: String,
        physicalPathChange: Boolean,
    ): ScheduledRecovery {
        transportGeneration += 1
        pendingTransportReasons.add(reason)
        pendingPhysicalPathChange = pendingPhysicalPathChange || physicalPathChange
        if (physicalPathChange) {
            lastPhysicalPathSignalAt = nowMillis
        }
        val intervalDelay = lastTransportRecoveryAt?.let {
            (it + settings.minimumTransportRecoveryIntervalMillis - nowMillis).coerceAtLeast(0L)
        } ?: 0L
        return ScheduledRecovery(
            generation = transportGeneration,
            delayMillis = maxOf(settings.transportRecoveryDebounceMillis, intervalDelay),
        )
    }

    fun consumeTransportRecovery(generation: Long, nowMillis: Long): TransportRecovery? {
        if (generation != transportGeneration || pendingTransportReasons.isEmpty()) {
            return null
        }
        val recovery = TransportRecovery(
            reasons = pendingTransportReasons.toSet(),
            physicalPathChange = pendingPhysicalPathChange,
        )
        pendingTransportReasons.clear()
        pendingPhysicalPathChange = false
        lastTransportRecoveryAt = nowMillis
        return recovery
    }

    fun requestWakeAudit(reason: String): ScheduledRecovery {
        wakeGeneration += 1
        pendingWakeReasons.add(reason)
        return ScheduledRecovery(wakeGeneration, settings.wakeAuditDebounceMillis)
    }

    fun beginWakeAudit(generation: Long, nowMillis: Long): WakeHealthAudit? {
        if (generation != wakeGeneration || pendingWakeReasons.isEmpty()) {
            return null
        }
        val audit = WakeHealthAudit(
            generation = generation,
            startedAtMillis = nowMillis,
            reasons = pendingWakeReasons.toSet(),
            delayMillis = settings.wakeHealthGraceMillis,
        )
        pendingWakeReasons.clear()
        return audit
    }

    fun evaluateWakeAudit(
        audit: WakeHealthAudit,
        facts: WakeHealthFacts,
    ): WakeHealthDecision {
        if (audit.generation != wakeGeneration) return WakeHealthDecision.INVALIDATED
        if (!facts.connectRequested) return WakeHealthDecision.LOCAL_MODE
        if (!facts.physicalNetworkAvailable) return WakeHealthDecision.OFFLINE
        if (facts.providerCount > 0) return WakeHealthDecision.PROVIDERS_HEALTHY
        if (
            lastTransportRecoveryAt?.let { it >= audit.startedAtMillis } == true ||
            lastPhysicalPathSignalAt?.let { it >= audit.startedAtMillis } == true
        ) {
            return WakeHealthDecision.COVERED_BY_TRANSPORT_RECOVERY
        }
        return WakeHealthDecision.RECOVER_TRANSPORTS
    }

    fun cancelWakeAudits() {
        wakeGeneration += 1
        pendingWakeReasons.clear()
    }

    fun reset() {
        transportGeneration += 1
        wakeGeneration += 1
        lastTransportRecoveryAt = null
        lastPhysicalPathSignalAt = null
        pendingTransportReasons.clear()
        pendingPhysicalPathChange = false
        pendingWakeReasons.clear()
    }
}
