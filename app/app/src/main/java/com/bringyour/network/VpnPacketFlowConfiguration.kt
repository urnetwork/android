package com.bringyour.network

/**
 * Complete material state used to establish an Android VPN packet flow.
 *
 * Keeping the desired and applied states as immutable snapshots prevents a
 * listener from marking a DNS, split, address, or connectivity change applied
 * before Builder.establish() has actually succeeded.
 */
internal data class VpnPacketFlowConfiguration(
    val offline: Boolean,
    val connected: Boolean,
    val includedAppIds: Set<String>,
    val excludedAppIds: Set<String>,
    val dnsIpv4s: List<String>,
    val dnsIpv6s: List<String>,
    val clientIpv4: String?,
)

internal fun vpnPacketFlowNeedsRebuild(
    packetFlowActive: Boolean,
    applied: VpnPacketFlowConfiguration?,
    desired: VpnPacketFlowConfiguration,
): Boolean {
    return !packetFlowActive || applied != desired
}

/**
 * Removes Android's unusable DNS-to-self destination. An address assigned by
 * VpnService.Builder.addAddress is installed in the kernel's local table, so a
 * query sent to it never appears on the TUN descriptor. The SDK normally emits
 * a distinct DnsUpgradeMaskAddress; this guard also protects older bindings and
 * a persisted custom mask that collides with the newly assigned address.
 */
internal fun vpnDnsServersForClient(
    clientIpv4: String?,
    deviceDnsIpv4s: List<String>,
    fallbackDnsIpv4s: List<String>,
): List<String> {
    fun usable(addresses: List<String>): List<String> {
        return addresses
            .map(String::trim)
            .filter { it.isNotEmpty() && it != clientIpv4 }
            .distinct()
    }

    return usable(deviceDnsIpv4s).ifEmpty {
        usable(fallbackDnsIpv4s)
    }
}
