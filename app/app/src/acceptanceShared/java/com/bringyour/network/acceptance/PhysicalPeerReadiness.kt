package com.bringyour.network.acceptance

/** Current routing evidence, not historical carrier byte totals. */
internal data class PhysicalPeerRouteState(
    val controllerConnected: Boolean,
    val connectEnabled: Boolean,
    val tunnelStarted: Boolean,
    val requestedPeerId: String?,
    val providers: List<PhysicalProviderEvidence>,
)

internal fun physicalPeerReadyForEgress(expectedPeerId: String, state: PhysicalPeerRouteState): Boolean =
    state.controllerConnected && state.connectEnabled && state.tunnelStarted &&
        state.requestedPeerId == expectedPeerId &&
        physicalSelectedPeer(expectedPeerId, state.providers, state.connectEnabled) == expectedPeerId

/** Owned by the instrumentation command thread; no identities or addresses in evidence. */
internal class PhysicalPeerConnectTiming(
    val startedAtMillis: Long,
    val startupTimeoutMillis: Long,
    private val commandStartedAtMillis: Long = startedAtMillis,
) {
    init {
        require(startupTimeoutMillis > 0) { "peer startup timeout must be positive" }
        require(commandStartedAtMillis <= startedAtMillis) { "peer command starts before connect" }
    }

    private var eligibleAtMillis: Long? = null
    private var egressProofStartedAtMillis: Long? = null
    private var egressProofFinishedAtMillis: Long? = null

    fun eligible(nowMillis: Long) { eligibleAtMillis = nowMillis }
    fun egressProofStarted(nowMillis: Long) { egressProofStartedAtMillis = nowMillis }
    fun egressProofFinished(nowMillis: Long) { egressProofFinishedAtMillis = nowMillis }

    fun evidence(nowMillis: Long): Map<String, Long> = buildMap {
        put("schemaVersion", 1L)
        put("startupTimeoutMs", startupTimeoutMillis)
        put("startupElapsedMs", (eligibleAtMillis ?: nowMillis) - startedAtMillis)
        put("connectionElapsedMs", nowMillis - startedAtMillis)
        put("commandElapsedMs", nowMillis - commandStartedAtMillis)
        put("connectionRequestedAfterCommandMs", startedAtMillis - commandStartedAtMillis)
        eligibleAtMillis?.let { put("eligibleAfterMs", it - startedAtMillis) }
        egressProofStartedAtMillis?.let { started ->
            put("egressProofStartedAfterMs", started - startedAtMillis)
            put("egressProofElapsedMs", (egressProofFinishedAtMillis ?: nowMillis) - started)
        }
        egressProofFinishedAtMillis?.let { put("egressProofFinishedAfterMs", it - startedAtMillis) }
    }
}

/** Completed peer-command evidence; sequence is local to this instrumentation session. */
internal data class PhysicalPeerConnectRecord(
    val commandSequence: Long,
    val successful: Boolean,
    val timing: Map<String, Long>?,
)

internal data class PhysicalPeerTimingStatus(
    val current: Map<String, Long>?,
    val lastConnect: PhysicalPeerConnectRecord?,
)

/** Command-thread-owned timing lifecycle; never accessed by the SDK sampler. */
internal class PhysicalPeerTimingEvidence {
    var commandSequence = 0L
        private set
    private var peerCommand = false
    private var current: PhysicalPeerConnectTiming? = null
    private var terminal: PhysicalPeerConnectRecord? = null
    private var lastConnect: PhysicalPeerConnectRecord? = null

    fun beginCommand() {
        commandSequence++
        peerCommand = false
        current = null
        terminal = null
    }

    fun beginPeerConnect() {
        peerCommand = true
        lastConnect = null
    }

    fun startConnect(timing: PhysicalPeerConnectTiming) { current = timing }

    fun snapshot(nowMillis: Long, successful: Boolean? = null): PhysicalPeerTimingStatus {
        val currentTiming = if (terminal != null) terminal?.timing else current?.evidence(nowMillis)
        if (peerCommand && successful != null && terminal == null) {
            // Copy at the first terminal status: later commands, clocks, or
            // completion callbacks must not revise the recorded outcome.
            terminal = PhysicalPeerConnectRecord(commandSequence, successful, currentTiming?.toMap())
            lastConnect = terminal
        }
        return PhysicalPeerTimingStatus(currentTiming, lastConnect)
    }
}

/**
 * Startup owns one absolute deadline, including connect/consent work. The SDK's
 * eager window evaluation sends its own IpPing before provider admission; no
 * application bytes are required here. Egress and its existing traffic proof
 * execute once, with their own unchanged bounds, after this startup gate.
 */
internal fun <T> runPhysicalPeerEgress(
    expectedPeerId: String,
    timing: PhysicalPeerConnectTiming,
    nowMillis: () -> Long,
    sleepMillis: (Long) -> Unit,
    prepareConnection: () -> Unit,
    routeState: () -> PhysicalPeerRouteState,
    egressProof: () -> T,
    canceled: () -> Boolean = { Thread.currentThread().isInterrupted },
): T {
    require(expectedPeerId.isNotBlank()) { "expected peer ID must not be blank" }
    fun checkCanceled() {
        if (canceled()) throw InterruptedException("peer startup canceled")
    }
    fun expired(now: Long): Boolean = now - timing.startedAtMillis >= timing.startupTimeoutMillis
    var lastError: Throwable? = null
    fun timeout(): Nothing = throw PhysicalWaitTimeout(
        PhysicalWaitStage.PEER_VPN_CONNECTION,
        timing.startupTimeoutMillis,
        lastError,
    )

    checkCanceled()
    prepareConnection()
    while (true) {
        checkCanceled()
        if (expired(nowMillis())) timeout()
        val ready = try {
            physicalPeerReadyForEgress(expectedPeerId, routeState())
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Throwable) {
            lastError = error
            false
        }
        checkCanceled()
        val observedAt = nowMillis()
        if (expired(observedAt)) timeout()
        if (ready) {
            timing.eligible(observedAt)
            break
        }
        sleepMillis(minOf(100L, timing.startupTimeoutMillis - (observedAt - timing.startedAtMillis)))
    }

    checkCanceled()
    timing.egressProofStarted(nowMillis())
    try {
        return egressProof()
    } finally {
        timing.egressProofFinished(nowMillis())
    }
}
