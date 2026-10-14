package com.bringyour.network.acceptance

/** Current SDK window evidence, bracketed around the live-provider read. */
internal data class PhysicalPublicRouteState(
    val controllerConnected: Boolean,
    val connectEnabled: Boolean,
    val tunnelStarted: Boolean,
    val generationBefore: Long,
    val generationAfter: Long,
    val minSatisfied: Boolean,
    val failed: Boolean,
    val providers: List<PhysicalProviderEvidence>,
)

/** Last successful route read only; no provider identities, list aliases, or history. */
internal data class PhysicalPublicReadinessSnapshot(
    val observationIndex: Long,
    val elapsedMillis: Long,
    val expectedConnectionGeneration: Long,
    val generationBefore: Long,
    val generationAfter: Long,
    val controllerConnected: Boolean,
    val connectEnabled: Boolean,
    val tunnelStarted: Boolean,
    val minSatisfied: Boolean,
    val failed: Boolean,
    val providerCount: Int,
    val usProvidersValid: Boolean,
) {
    fun statusFields(): Map<String, Any> = mapOf(
        "observationIndex" to observationIndex,
        "elapsedMs" to elapsedMillis,
        "expectedConnectionGeneration" to expectedConnectionGeneration,
        "generationBefore" to generationBefore,
        "generationAfter" to generationAfter,
        "controllerConnected" to controllerConnected,
        "connectEnabled" to connectEnabled,
        "tunnelStarted" to tunnelStarted,
        "minSatisfied" to minSatisfied,
        "failed" to failed,
        "providerCount" to providerCount,
        "usProvidersValid" to usProvidersValid,
    )
}

/**
 * The existing public startup budget begins after connect/consent, as before.
 * SDK discovery and its own admission ping run without application traffic.
 * Keep carrier-byte proof after this gate; the egress attempt runs exactly once.
 */
internal fun <T> runPhysicalPublicEgress(
    expectedConnectionGeneration: Long,
    timeoutMillis: Long,
    nowMillis: () -> Long,
    sleepMillis: (Long) -> Unit,
    routeState: () -> PhysicalPublicRouteState,
    egressProof: () -> T,
    canceled: () -> Boolean = { Thread.currentThread().isInterrupted },
): T {
    require(expectedConnectionGeneration > 0) { "public connection generation must be positive" }
    require(timeoutMillis > 0) { "public startup timeout must be positive" }
    val startedAt = nowMillis()
    var lastError: Throwable? = null
    var lastObservation: PhysicalPublicReadinessSnapshot? = null
    var observations = 0L
    fun checkCanceled() {
        if (canceled()) throw InterruptedException("public startup canceled")
    }
    fun timeout(): Nothing = throw PhysicalWaitTimeout(
        PhysicalWaitStage.PUBLIC_VPN_CONNECTION, timeoutMillis, lastError, lastObservation,
    )
    while (true) {
        checkCanceled()
        if (nowMillis() - startedAt >= timeoutMillis) timeout()
        val ready = try {
            val state = routeState()
            val usProvidersValid = physicalLiveCountry(state.providers) == "US"
            lastObservation = PhysicalPublicReadinessSnapshot(
                observationIndex = ++observations,
                elapsedMillis = nowMillis() - startedAt,
                expectedConnectionGeneration = expectedConnectionGeneration,
                generationBefore = state.generationBefore,
                generationAfter = state.generationAfter,
                controllerConnected = state.controllerConnected,
                connectEnabled = state.connectEnabled,
                tunnelStarted = state.tunnelStarted,
                minSatisfied = state.minSatisfied,
                failed = state.failed,
                providerCount = state.providers.size,
                usProvidersValid = usProvidersValid,
            )
            state.controllerConnected && state.connectEnabled && state.tunnelStarted &&
                state.generationBefore == expectedConnectionGeneration &&
                state.generationAfter == expectedConnectionGeneration &&
                state.minSatisfied && !state.failed &&
                usProvidersValid
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Exception) {
            lastError = error
            false
        }
        checkCanceled()
        val elapsed = nowMillis() - startedAt
        if (elapsed >= timeoutMillis) timeout()
        if (ready) break
        sleepMillis(minOf(100L, timeoutMillis - elapsed))
    }
    checkCanceled()
    return egressProof()
}
