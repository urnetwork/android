package com.bringyour.network

import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The split route tables the VPN builder uses (IPv6 on every Android version):
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

    /** Every prefix handed to the production builder must be admissible. */
    @Test
    fun productionIpv6SubmissionUsesAdmissibleCapturePrefixes() {
        val builder = StrictRouteBuilder()
        vpnSubmitIpv6Routes(builder::addRoute)
        assertEquals(canonical(vpnIpv6CaptureRoutes()), canonical(builder.addedRoutes))
        assertTrue(builder.addedRoutes.all { InetAddress.getByName(it.address) is Inet6Address })
    }

    /** The old modern branch fails after the three valid local exclusions. */
    @Test
    fun oldModernIpv6ExclusionsRejectLoopbackWithBadAddress() {
        val builder = StrictRouteBuilder()
        builder.addRoute(VpnRoute("::", 0))
        val exception = assertThrows(IllegalArgumentException::class.java) {
            for (route in VPN_IPV6_EXCLUDED_PREFIXES) {
                builder.excludeRoute(route)
            }
        }
        assertEquals("Bad address", exception.message)
        assertEquals(canonical(VPN_IPV6_EXCLUDED_PREFIXES.dropLast(1)), canonical(builder.excludedRoutes))
    }

    /** The submitted capture table has exactly the same policy on every API. */
    @Test
    fun submittedIpv6RoutesExactlyComplementAllNativeExclusions() {
        val builder = StrictRouteBuilder()
        vpnSubmitIpv6Routes(builder::addRoute)
        val routes = builder.addedRoutes
        for ((index, route) in routes.withIndex()) {
            for (other in routes.drop(index + 1) + VPN_IPV6_EXCLUDED_PREFIXES) {
                assertFalse(
                    "${route.address}/${route.prefixLength} overlaps ${other.address}/${other.prefixLength}",
                    vpnRouteContains(route, other.address) || vpnRouteContains(other, route.address),
                )
            }
        }
        assertEquals(
            BigInteger.ONE.shiftLeft(128),
            routes.sumOf { vpnRouteSize(it, 128) } +
                VPN_IPV6_EXCLUDED_PREFIXES.sumOf { vpnRouteSize(it, 128) },
        )
    }

    /** Any-local network prefixes are valid routes, unlike loopback prefixes. */
    @Test
    fun strictBuilderAcceptsAnyLocalAndFullLengthNetworkRoutes() {
        val builder = StrictRouteBuilder()
        for (route in listOf(
            VpnRoute("0.0.0.0", 0), VpnRoute("0.0.0.0", 32),
            VpnRoute("::", 0), VpnRoute("::", 128),
            VpnRoute("192.0.2.1", 32), VpnRoute("2001:db8::1", 128),
        )) {
            builder.addRoute(route)
        }
        assertEquals(6, builder.addedRoutes.size)
    }

    /** addRoute rejects loopback and nonzero host bits without suppressing them. */
    @Test
    fun strictBuilderRejectsLoopbackAndUnalignedNetworkRoutes() {
        for (route in listOf(
            VpnRoute("127.0.0.0", 8), VpnRoute("127.0.0.1", 32), VpnRoute("::1", 128),
            VpnRoute("192.0.2.1", 24), VpnRoute("2001:db8::1", 64),
        )) {
            val exception = assertThrows(IllegalArgumentException::class.java) {
                StrictRouteBuilder().addRoute(route)
            }
            assertEquals("Bad address", exception.message)
        }
    }

    /** IpPrefix masks exclusions before the shared builder address check. */
    @Test
    fun strictBuilderNormalizesExcludedPrefixesBeforeCheckingLoopback() {
        val builder = StrictRouteBuilder()
        for (route in listOf(
            VpnRoute("192.0.2.1", 24), VpnRoute("2001:db8::1", 64), VpnRoute("::1", 64),
        )) {
            builder.excludeRoute(route)
        }
        assertEquals(
            canonical(listOf(VpnRoute("192.0.2.0", 24), VpnRoute("2001:db8::", 64), VpnRoute("::", 64))),
            canonical(builder.excludedRoutes),
        )
    }

    /** Prefix limits follow the parsed address family, including mapped IPv4. */
    @Test
    fun strictBuilderChecksPrefixLimitsForEachAddressFamily() {
        for (route in listOf(
            VpnRoute("192.0.2.0", -1), VpnRoute("192.0.2.0", 33),
            VpnRoute("2001:db8::", -1), VpnRoute("2001:db8::", 129),
            VpnRoute("::ffff:192.0.2.1", 128),
        )) {
            assertThrows(IllegalArgumentException::class.java) { StrictRouteBuilder().addRoute(route) }
            assertThrows(IllegalArgumentException::class.java) { StrictRouteBuilder().excludeRoute(route) }
        }
    }

    /**
     * Minimal JVM sink for the API35 VpnService route contract: addRoute first
     * requires network alignment; IpPrefix-based exclusions normalize instead.
     * Both then reject loopback and enforce the parsed family's prefix bounds.
     * Numeric test addresses never perform name resolution.
     */
    private class StrictRouteBuilder {
        val addedRoutes = mutableListOf<VpnRoute>()
        val excludedRoutes = mutableListOf<VpnRoute>()

        /** Mirrors Builder.addRoute(InetAddress, prefixLength). */
        fun addRoute(route: VpnRoute) {
            val address = InetAddress.getByName(route.address)
            val network = networkAddress(address, route.prefixLength)
            require(address == network) { "Bad address" }
            checkRoute(network, route.prefixLength)
            addedRoutes += route
        }

        /** Mirrors Builder.excludeRoute(IpPrefix(address, prefixLength)). */
        fun excludeRoute(route: VpnRoute) {
            val network = networkAddress(InetAddress.getByName(route.address), route.prefixLength)
            checkRoute(network, route.prefixLength)
            excludedRoutes += VpnRoute(network.hostAddress, route.prefixLength)
        }

        /** The only literal address families accepted by the Android builder. */
        private fun addressBits(address: InetAddress): Int = when (address) {
            is Inet4Address -> 32
            is Inet6Address -> 128
            else -> throw IllegalArgumentException("Unsupported family")
        }

        /** IpPrefix zeroes host bits and validates family-dependent lengths. */
        private fun networkAddress(address: InetAddress, prefixLength: Int): InetAddress {
            require(prefixLength in 0..addressBits(address)) { "Bad prefixLength" }
            val bytes = address.address
            for (index in bytes.indices) {
                val prefixBits = (prefixLength - index * 8).coerceIn(0, 8)
                bytes[index] = (bytes[index].toInt() and (0xff shl (8 - prefixBits))).toByte()
            }
            return InetAddress.getByAddress(bytes)
        }

        /** Mirrors VpnService.check; any-local prefixes remain admissible. */
        private fun checkRoute(address: InetAddress, prefixLength: Int) {
            require(!address.isLoopbackAddress) { "Bad address" }
            require(prefixLength in 0..addressBits(address)) { "Bad prefixLength" }
        }
    }
}
