package com.bringyour.network.acceptance

/** Finite test-owned labels: never derive status evidence from exception messages. */
internal enum class PhysicalWaitStage(val wireValue: String, val description: String) {
    PEER_TRAFFIC_COUNTERS("peer-traffic-counters", "bidirectional peer traffic counters"),
    CLIENT_DISCONNECT("client-disconnect", "client disconnect"),
    PROVIDER_STOP("provider-stop", "provider stop"),
    TRANSPORT_POLICY("transport-policy", "transport policy"),
    US_COUNTRY_POOL("us-country-pool", "explicit United States country pool"),
    PUBLIC_VPN_CONNECTION("public-vpn-connection", "public VPN connection"),
    US_PROVIDER_CARRIER_EVIDENCE("us-provider-carrier-evidence", "live US provider and carrier evidence"),
    SAME_NETWORK_PROVIDER("same-network-provider", "same-network provider"),
    CONNECTABLE_PEER("connectable-peer", "connectable same-network peer"),
    PEER_VPN_CONNECTION("peer-vpn-connection", "same-network peer VPN connection"),
    PEER_CARRIER_EVIDENCE("peer-carrier-evidence", "live exact peer and carrier evidence"),
    PROVIDER_TRAFFIC_COUNTERS("provider-traffic-counters", "bidirectional provider traffic counters"),
}

internal class PhysicalWaitTimeout(
    val stage: PhysicalWaitStage,
    timeoutMillis: Long,
    cause: Throwable?,
) : AssertionError("Timed out waiting for ${stage.description} after ${timeoutMillis / 1_000}s", cause)

/** Same polling/deadline behavior as the physical session, with a typed timeout. */
internal fun waitForPhysicalCondition(
    stage: PhysicalWaitStage,
    timeoutMillis: Long,
    nowMillis: () -> Long,
    sleepMillis: (Long) -> Unit,
    condition: () -> Boolean,
) {
    val deadline = nowMillis() + timeoutMillis
    var lastError: Throwable? = null
    while (nowMillis() < deadline) {
        try {
            if (condition()) return
        } catch (error: Throwable) {
            lastError = error
        }
        sleepMillis(100)
    }
    throw PhysicalWaitTimeout(stage, timeoutMillis, lastError)
}

/** No messages, causes, stack traces, dynamic labels, or stale previous stages. */
internal fun physicalWaitFailureEvidence(error: Throwable): Map<String, String>? =
    (error as? PhysicalWaitTimeout)?.let {
        mapOf("stage" to it.stage.wireValue, "failure" to "wait-timeout")
    }
