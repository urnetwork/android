package com.bringyour.network

import java.math.BigInteger
import java.net.InetAddress

/**
 * Route-table arithmetic for the VPN builder on Android versions before
 * Builder.excludeRoute (API 33): a capture-everything route minus the
 * prefixes that must stay on the native network, expressed as the list of
 * routes that exactly tiles the remainder.
 *
 * Pure (no Android runtime), so the generated tables are unit-testable: the
 * IPv4 table this produces is checked against the hand-generated RFC1918
 * table in MainService, and the IPv6 table against its exclusions.
 */
internal data class VpnRoute(val address: String, val prefixLength: Int)

/** The IPv6 prefixes the tunnel never captures, on every Android version. */
internal val VPN_IPV6_EXCLUDED_PREFIXES = listOf(
    // link-local: neighbor discovery and the router's own addresses
    VpnRoute("fe80::", 10),
    // unique local: a home or office network's own v6 space (the tunnel
    // interface's own /64 is inside this range and carries nothing but the
    // tunnel host, so excluding it costs nothing)
    VpnRoute("fc00::", 7),
    // multicast: mDNS, router advertisements, and every other link scope
    VpnRoute("ff00::", 8),
    VpnRoute("::1", 128),
)

/** The IPv4 prefixes the tunnel never captures: the RFC1918 private ranges. */
internal val VPN_IPV4_EXCLUDED_PREFIXES = listOf(
    VpnRoute("10.0.0.0", 8),
    VpnRoute("172.16.0.0", 12),
    VpnRoute("192.168.0.0", 16),
)

/** `::/0` minus the v6 exclusions, for builders without excludeRoute. */
internal fun vpnIpv6CaptureRoutes(): List<VpnRoute> =
    vpnCaptureRoutes(VpnRoute("::", 0), VPN_IPV6_EXCLUDED_PREFIXES, 128)

/** `0.0.0.0/0` minus the v4 exclusions, for builders without excludeRoute. */
internal fun vpnIpv4CaptureRoutes(): List<VpnRoute> =
    vpnCaptureRoutes(VpnRoute("0.0.0.0", 0), VPN_IPV4_EXCLUDED_PREFIXES, 32)

/**
 * The routes that tile `all` minus every prefix in `exclusions`. Each
 * exclusion is carved out of every route that overlaps it by halving the
 * route down to the exclusion's own length and keeping the halves that do
 * not contain it, so the result never overlaps an exclusion and, with the
 * exclusions, covers `all` exactly once.
 */
internal fun vpnCaptureRoutes(all: VpnRoute, exclusions: List<VpnRoute>, bits: Int): List<VpnRoute> {
    var routes = listOf(all.toPrefix(bits))
    for (exclusion in exclusions) {
        val excluded = exclusion.toPrefix(bits)
        routes = routes.flatMap { route -> route.exclude(excluded) }
    }
    return routes.map { it.toRoute() }
}

/** Whether `address` is inside `route`. */
internal fun vpnRouteContains(route: VpnRoute, address: String): Boolean {
    val bits = if (address.contains(':')) 128 else 32
    if ((route.address.contains(':')) != (bits == 128)) {
        return false
    }
    return route.toPrefix(bits).contains(addressValue(address))
}

/** The number of addresses `route` covers, for tiling checks. */
internal fun vpnRouteSize(route: VpnRoute, bits: Int): BigInteger =
    BigInteger.ONE.shiftLeft(bits - route.prefixLength)

/**
 * A prefix as an integer network address (low bits zero) and a length over
 * `bits` bits.
 */
private data class Prefix(val network: BigInteger, val length: Int, val bits: Int) {

    private fun mask(length: Int): BigInteger =
        BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE)
            .shiftRight(length)
            .shiftLeft(length)
            .and(BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE))

    /** The host bits beyond `length`, zeroed. */
    fun normalized(): Prefix = Prefix(network.and(mask(bits - length)), length, bits)

    fun contains(address: BigInteger): Boolean =
        address.and(mask(bits - length)) == network

    /** Whether `other` (a possibly longer prefix) lies inside this prefix. */
    fun containsPrefix(other: Prefix): Boolean =
        length <= other.length && contains(other.network)

    private fun children(): Pair<Prefix, Prefix> {
        val childLength = length + 1
        val high = network.setBit(bits - childLength)
        return Pair(Prefix(network, childLength, bits), Prefix(high, childLength, bits))
    }

    /** This prefix minus `excluded`: the halves on the way down that do not contain it. */
    fun exclude(excluded: Prefix): List<Prefix> {
        // disjoint: nothing to carve
        if (!containsPrefix(excluded) && !excluded.containsPrefix(this)) {
            return listOf(this)
        }
        // the exclusion covers this prefix entirely
        if (excluded.containsPrefix(this)) {
            return listOf()
        }
        // the exclusion is strictly inside: keep the half without it, descend the other
        val (low, high) = children()
        val (keep, descend) = if (low.containsPrefix(excluded)) Pair(high, low) else Pair(low, high)
        return listOf(keep) + descend.exclude(excluded)
    }

    fun toRoute(): VpnRoute {
        val bytes = ByteArray(bits / 8)
        val value = network.toByteArray()
        // toByteArray is big-endian two's complement with a possible leading
        // zero sign byte; copy the low `bits/8` bytes
        val copy = minOf(value.size, bytes.size)
        System.arraycopy(value, value.size - copy, bytes, bytes.size - copy, copy)
        return VpnRoute(InetAddress.getByAddress(bytes).hostAddress, length)
    }
}

private fun VpnRoute.toPrefix(bits: Int): Prefix =
    Prefix(addressValue(address), prefixLength, bits).normalized()

private fun addressValue(address: String): BigInteger =
    BigInteger(1, InetAddress.getByName(address).address)
