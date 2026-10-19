package com.bringyour.network.acceptance

/** Immutable SDK window counters; unknown reason text is never retained. */
internal class PhysicalPublicWindowSnapshot(
    val targetSize: Long,
    stallReason: String?,
    val providerStateInEvaluation: Long,
    val providerStateEvaluationFailed: Long,
    val providerStateNotAdded: Long,
    val providerStateAdded: Long,
    val providerStateRemoved: Long,
) {
    val stallReason: String? = when (stallReason) {
        "", "evaluating", "platform-unreachable", "providers-unresponsive", "rate-limited", "auth-failing" -> stallReason
        else -> null
    }

    fun statusFields(): Map<String, Any> = buildMap {
        put("targetSize", targetSize)
        stallReason?.let { put("stallReason", it) }
        put("providerStateInEvaluation", providerStateInEvaluation)
        put("providerStateEvaluationFailed", providerStateEvaluationFailed)
        put("providerStateNotAdded", providerStateNotAdded)
        put("providerStateAdded", providerStateAdded)
        put("providerStateRemoved", providerStateRemoved)
    }
}

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
    val windowBefore: PhysicalPublicWindowSnapshot? = null,
    val windowAfter: PhysicalPublicWindowSnapshot? = null,
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
    val windowBefore: PhysicalPublicWindowSnapshot? = null,
    val windowAfter: PhysicalPublicWindowSnapshot? = null,
) {
    fun statusFields(): Map<String, Any> = buildMap {
        put("observationIndex", observationIndex)
        put("elapsedMs", elapsedMillis)
        put("expectedConnectionGeneration", expectedConnectionGeneration)
        put("generationBefore", generationBefore)
        put("generationAfter", generationAfter)
        put("controllerConnected", controllerConnected)
        put("connectEnabled", connectEnabled)
        put("tunnelStarted", tunnelStarted)
        put("minSatisfied", minSatisfied)
        put("failed", failed)
        put("providerCount", providerCount)
        put("usProvidersValid", usProvidersValid)
        windowBefore?.let { put("windowBefore", it.statusFields()) }
        windowAfter?.let { put("windowAfter", it.statusFields()) }
    }
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
                windowBefore = state.windowBefore,
                windowAfter = state.windowAfter,
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
