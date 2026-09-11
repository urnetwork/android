package com.bringyour.network

import java.math.BigInteger
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The split route tables the VPN builder uses before excludeRoute (API 33):
 * the generated IPv4 table must equal the hand-generated RFC1918 table in
 * MainService, and the IPv6 table must tile ::/0 exactly once minus the
 * excluded scopes.
 */
class VpnRoutesTest {

    // the hand-generated table in MainService.updatePfd, verbatim
    private val mainServiceIpv4Routes = listOf(
        VpnRoute("224.0.0.0", 3), VpnRoute("208.0.0.0", 4), VpnRoute("200.0.0.0", 5),
        VpnRoute("196.0.0.0", 6), VpnRoute("194.0.0.0", 7), VpnRoute("193.0.0.0", 8),
        VpnRoute("192.0.0.0", 9), VpnRoute("192.192.0.0", 10), VpnRoute("192.128.0.0", 11),
        VpnRoute("192.176.0.0", 12), VpnRoute("192.160.0.0", 13), VpnRoute("192.172.0.0", 14),
        VpnRoute("192.170.0.0", 15), VpnRoute("192.169.0.0", 16), VpnRoute("128.0.0.0", 3),
        VpnRoute("176.0.0.0", 4), VpnRoute("160.0.0.0", 5), VpnRoute("168.0.0.0", 6),
        VpnRoute("174.0.0.0", 7), VpnRoute("173.0.0.0", 8), VpnRoute("172.128.0.0", 9),
        VpnRoute("172.64.0.0", 10), VpnRoute("172.32.0.0", 11), VpnRoute("172.0.0.0", 12),
        VpnRoute("64.0.0.0", 2), VpnRoute("32.0.0.0", 3), VpnRoute("16.0.0.0", 4),
        VpnRoute("0.0.0.0", 5), VpnRoute("12.0.0.0", 6), VpnRoute("8.0.0.0", 7),
        VpnRoute("11.0.0.0", 8),
    )

    private fun canonical(routes: List<VpnRoute>): Set<Pair<InetAddress, Int>> =
        routes.map { Pair(InetAddress.getByName(it.address), it.prefixLength) }.toSet()

    @Test
    fun generatedIpv4TableMatchesTheHandGeneratedTable() {
        assertEquals(canonical(mainServiceIpv4Routes), canonical(vpnIpv4CaptureRoutes()))
    }

    @Test
    fun ipv6TableTilesEverythingExceptTheExclusions() {
        val routes = vpnIpv6CaptureRoutes()
        val all = BigInteger.ONE.shiftLeft(128)
        val excluded = VPN_IPV6_EXCLUDED_PREFIXES.sumOf { vpnRouteSize(it, 128) }
        val covered = routes.sumOf { vpnRouteSize(it, 128) }
        assertEquals(all.subtract(excluded), covered)
        // no route is empty and none is the whole space
        assertTrue(routes.all { it.prefixLength in 1..128 })
    }

    @Test
    fun ipv6TableNeverOverlapsAnExclusion() {
        val routes = vpnIpv6CaptureRoutes()
        for (address in listOf(
            "fe80::1", "febf:ffff::1", "fc00::1", "fd00:7572:6e65::1", "fdff:ffff::1",
            "ff02::1", "ff0e::fb", "::1",
        )) {
            assertFalse("$address must stay on the native network", routes.any { vpnRouteContains(it, address) })
        }
    }

    @Test
    fun ipv6TableCoversPublicAddressesExactlyOnce() {
        val routes = vpnIpv6CaptureRoutes()
        for (address in listOf(
            "2001:4860:4860::8888", "2606:4700:4700::1111", "2001:db8::65:49:70:65",
            "2a00::1", "fec0::1", "fe00::1", "::", "::2", "64:ff9b::1", "2002::1",
        )) {
            assertEquals("$address must be captured by exactly one route", 1, routes.count { vpnRouteContains(it, address) })
        }
    }

    @Test
    fun ipv6ExclusionsAreTheLocalScopes() {
        assertEquals(
            listOf(
                VpnRoute("fe80::", 10),
                VpnRoute("fc00::", 7),
                VpnRoute("ff00::", 8),
                VpnRoute("::1", 128),
            ),
            VPN_IPV6_EXCLUDED_PREFIXES,
        )
    }

    @Test
    fun routeContainsRespectsTheFamily() {
        assertTrue(vpnRouteContains(VpnRoute("10.0.0.0", 8), "10.1.2.3"))
        assertFalse(vpnRouteContains(VpnRoute("10.0.0.0", 8), "11.0.0.1"))
        assertFalse(vpnRouteContains(VpnRoute("10.0.0.0", 8), "::a00:0"))
        assertTrue(vpnRouteContains(VpnRoute("::", 0), "2001:db8::1"))
    }
}
