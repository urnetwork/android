package com.bringyour.network.acceptance

/** Test-side evidence only: requested policy is never a substitute for live routing. */
internal data class PhysicalCountryCandidate(
    val countryCode: String,
    val locationId: String?,
    val isCountry: Boolean,
    val bestAvailable: Boolean,
)

internal fun physicalUsCountryIndex(candidates: List<PhysicalCountryCandidate>): Int? =
    candidates.indices.filter {
        val candidate = candidates[it]
        candidate.countryCode == "US" && !candidate.locationId.isNullOrBlank() &&
            candidate.isCountry && !candidate.bestAvailable
    }.singleOrNull()

internal data class PhysicalProviderEvidence(val clientId: String, val countryCode: String, val hasLocation: Boolean)

internal fun physicalLiveCountry(providers: List<PhysicalProviderEvidence>): String {
    if (providers.isEmpty() || providers.any { !it.hasLocation || it.countryCode.isBlank() }) return ""
    return providers.map { it.countryCode }.distinct().singleOrNull().orEmpty()
}

internal data class PhysicalCarrierBytes(val egress: Long, val ingress: Long)

/** Observe real packet counters since this connection began, including Auto fallback.
 * Retired/cumulative traffic and requested transport preferences cannot select a carrier.
 * Multiple progressing carriers remain explicit, rather than guessing a winner.
 */
internal fun physicalSelectedCarriers(
    baseline: Map<String, PhysicalCarrierBytes>,
    current: Map<String, PhysicalCarrierBytes>,
    connected: Boolean,
): String {
    if (!connected) return ""
    return current.filter { (carrier, count) ->
        val prior = baseline[carrier] ?: PhysicalCarrierBytes(0, 0)
        carrier in setOf("h1", "h3", "dns", "dnspump", "p2p") &&
            count.egress >= prior.egress && count.ingress >= prior.ingress &&
            (count.egress > prior.egress || count.ingress > prior.ingress)
    }.keys.sorted().joinToString("+")
}

internal fun physicalSelectedPeer(
    requestedPeerId: String?,
    providers: List<PhysicalProviderEvidence>,
    connected: Boolean,
): String = requestedPeerId?.takeIf { requested ->
    connected && requested.isNotBlank() && providers.isNotEmpty() &&
        providers.all { it.clientId == requested }
}.orEmpty()
