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
