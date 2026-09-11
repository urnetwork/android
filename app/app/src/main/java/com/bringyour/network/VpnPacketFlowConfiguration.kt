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
    // killSwitch = !device.routeLocal ("Allow local traffic when disconnected").
    // When on, the tunnel must capture/block even without an exit: that is the
    // feature, not a bug. connectRequested = device.connectEnabled: when the
    // user wants their own traffic tunneled, never escape on a transient
    // provider dip (intent does not flap; liveness does).
    val killSwitch: Boolean,
    val connectRequested: Boolean,
    val includedAppIds: Set<String>,
    val excludedAppIds: Set<String>,
    val dnsIpv4s: List<String>,
    val clientIpv4: String?,
    // the IPv6 half of the tunnel: its DNS servers and interface address, from
    // the sdk device like the IPv4 ones. null when the sdk handed back no
    // usable address; the builder then falls back to a documentation address
    // so the family still fails closed rather than leaking
    val dnsIpv6s: List<String>,
    val clientIpv6: String?,
    val ipv6Policy: VpnIpv6Policy = VpnIpv6Policy.CAPTURE,
)

/**
 * The tunnel is dual-stack: in the capture modes it advertises the IPv6
 * address, the ::/0 route (minus link-local, unique-local, multicast and
 * loopback) and the IPv6 DNS servers exactly as it does for IPv4, so IPv6
 * traffic rides the tunnel and, with the kill switch, fails closed instead of
 * leaking around it. Escape mode adds nothing for either family.
 */
internal enum class VpnIpv6Policy {
    CAPTURE,
}

/**
 * Which routing policy to apply when building the VPN packet flow.
 *
 * ESCAPE keeps the tunnel technically up (so provide stays armed) but lets no
 * real app see it: used when the device is offline OR when there is no live
 * provider exit yet (connected == false). Failing closed on !connected stops
 * other apps being captured into a tunnel that has no working egress, which
 * blackholes their DNS and connectivity.
 */
internal enum class VpnPacketFlowMode {
    ESCAPE,
    PER_APP_ALLOWLIST,
    DENYLIST,
}

/**
 * Decides the routing mode from the tunnel config. Pure, so it is
 * unit-testable without an Android runtime.
 */
internal fun vpnPacketFlowMode(
    offline: Boolean,
    connected: Boolean,
    killSwitch: Boolean,
    connectRequested: Boolean,
    includedAppIds: Set<String>,
): VpnPacketFlowMode = when {
    offline -> VpnPacketFlowMode.ESCAPE
    // Escape only when the tunnel is up PURELY for provide (no kill switch,
    // no connect intent, no live exit). In every other not-connected case the
    // user asked for capture (kill switch) or for their traffic to be held
    // (connect): those must not escape, or they leak to the ISP in the clear.
    !connected && !killSwitch && !connectRequested -> VpnPacketFlowMode.ESCAPE
    includedAppIds.isNotEmpty() -> VpnPacketFlowMode.PER_APP_ALLOWLIST
    else -> VpnPacketFlowMode.DENYLIST
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
    return usableDnsServers(clientIpv4, deviceDnsIpv4s, fallbackDnsIpv4s, ::isIpv4Literal)
}

/**
 * vpnDnsServersForClient for the IPv6 half: only IPv6 literals, never the
 * assigned IPv6 tunnel address.
 */
internal fun vpnDnsServersIpv6ForClient(
    clientIpv6: String?,
    deviceDnsIpv6s: List<String>,
    fallbackDnsIpv6s: List<String>,
): List<String> {
    return usableDnsServers(clientIpv6, deviceDnsIpv6s, fallbackDnsIpv6s, ::isIpv6Literal)
}

private fun usableDnsServers(
    clientAddress: String?,
    deviceDnsServers: List<String>,
    fallbackDnsServers: List<String>,
    isLiteral: (String) -> Boolean,
): List<String> {
    fun usable(addresses: List<String>): List<String> {
        return addresses
            .map(String::trim)
            .filter { isLiteral(it) && it != clientAddress }
            .distinct()
    }

    return usable(deviceDnsServers).ifEmpty {
        usable(fallbackDnsServers)
    }
}

/**
 * Validate the IPv4 tunnel address at the platform boundary so a malformed or
 * future SDK value cannot make VpnService.Builder advertise the wrong family.
 */
internal fun vpnTunnelIpv4Address(address: String?): String? {
    val normalized = address?.trim() ?: return null
    return normalized.takeIf(::isIpv4Literal)
}

/** vpnTunnelIpv4Address for the IPv6 tunnel address: an IPv6 literal, or null. */
internal fun vpnTunnelIpv6Address(address: String?): String? {
    val normalized = address?.trim() ?: return null
    return normalized.takeIf(::isIpv6Literal)
}

/**
 * A native IPv6 literal without a zone: hex groups and colons only, parsed by
 * the platform without any name resolution, which the character check
 * guarantees. An IPv4-mapped address (::ffff:a.b.c.d) parses as IPv4 and is
 * rejected: it is not an address the IPv6 half of the tunnel can carry.
 */
private fun isIpv6Literal(address: String): Boolean {
    if (!address.contains(':') || address.contains('%') || address.contains('[')) {
        return false
    }
    if (!address.all { it == ':' || it == '.' || it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        return false
    }
    return try {
        java.net.InetAddress.getByName(address) is java.net.Inet6Address
    } catch (_: Exception) {
        false
    }
}

private fun isIpv4Literal(address: String): Boolean {
    val parts = address.split('.', limit = 5)
    return parts.size == 4 && parts.all { part ->
        part.isNotEmpty() &&
            part.all(Char::isDigit) &&
            part.toIntOrNull()?.let { it in 0..255 } == true
    }
}
