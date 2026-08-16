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
    val clientIpv4: String?,
    val ipv6Policy: VpnIpv6Policy = VpnIpv6Policy.BLOCK_UNSUPPORTED,
)

/**
 * Remote providers do not forward IPv6. Do not advertise an IPv6 address,
 * route, DNS server, or allowFamily bypass while the VPN is active. Android
 * then marks the unsupported family unavailable instead of leaking it around
 * the tunnel.
 */
internal enum class VpnIpv6Policy {
    BLOCK_UNSUPPORTED,
}

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
            .filter { isIpv4Literal(it) && it != clientIpv4 }
            .distinct()
    }

    return usable(deviceDnsIpv4s).ifEmpty {
        usable(fallbackDnsIpv4s)
    }
}

/**
 * The remote-provider tunnel is IPv4-only. Validate values at the platform
 * boundary so a malformed or future SDK value cannot make VpnService.Builder
 * advertise an IPv6 address or DNS transport.
 */
internal fun vpnTunnelIpv4Address(address: String?): String? {
    val normalized = address?.trim() ?: return null
    return normalized.takeIf(::isIpv4Literal)
}

private fun isIpv4Literal(address: String): Boolean {
    val parts = address.split('.', limit = 5)
    return parts.size == 4 && parts.all { part ->
        part.isNotEmpty() &&
            part.all(Char::isDigit) &&
            part.toIntOrNull()?.let { it in 0..255 } == true
    }
}
