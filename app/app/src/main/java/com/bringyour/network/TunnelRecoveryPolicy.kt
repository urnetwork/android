package com.bringyour.network

internal const val TRANSPORT_RECOVERY_DEBOUNCE_MILLIS = 350L
internal const val MINIMUM_TRANSPORT_RECOVERY_INTERVAL_MILLIS = 2_000L
internal const val WAKE_HEALTH_GRACE_MILLIS = 5_000L

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
    val delayMillis: Long = WAKE_HEALTH_GRACE_MILLIS,
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
 */
internal class TunnelRecoveryPolicy {
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
            (it + MINIMUM_TRANSPORT_RECOVERY_INTERVAL_MILLIS - nowMillis).coerceAtLeast(0L)
        } ?: 0L
        return ScheduledRecovery(
            generation = transportGeneration,
            delayMillis = maxOf(TRANSPORT_RECOVERY_DEBOUNCE_MILLIS, intervalDelay),
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
        return ScheduledRecovery(wakeGeneration, TRANSPORT_RECOVERY_DEBOUNCE_MILLIS)
    }

    fun beginWakeAudit(generation: Long, nowMillis: Long): WakeHealthAudit? {
        if (generation != wakeGeneration || pendingWakeReasons.isEmpty()) {
            return null
        }
        val audit = WakeHealthAudit(
            generation = generation,
            startedAtMillis = nowMillis,
            reasons = pendingWakeReasons.toSet(),
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
