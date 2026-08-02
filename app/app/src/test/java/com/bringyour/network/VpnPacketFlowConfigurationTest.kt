package com.bringyour.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPacketFlowConfigurationTest {
    private fun configuration(
        offline: Boolean = false,
        connected: Boolean = true,
        includedAppIds: Set<String> = emptySet(),
        excludedAppIds: Set<String> = emptySet(),
        dnsIpv4s: List<String> = listOf("65.49.70.65"),
        clientIpv4: String? = "10.0.0.1",
    ): VpnPacketFlowConfiguration {
        return VpnPacketFlowConfiguration(
            offline = offline,
            connected = connected,
            includedAppIds = includedAppIds,
            excludedAppIds = excludedAppIds,
            dnsIpv4s = dnsIpv4s,
            dnsIpv6s = emptyList(),
            clientIpv4 = clientIpv4,
        )
    }

    @Test
    fun inactivePacketFlowAlwaysRebuilds() {
        val configuration = configuration()

        assertTrue(vpnPacketFlowNeedsRebuild(false, configuration, configuration))
    }

    @Test
    fun unchangedActivePacketFlowDoesNotRebuild() {
        val configuration = configuration()

        assertFalse(vpnPacketFlowNeedsRebuild(true, configuration, configuration))
    }

    @Test
    fun activeOfflinePacketFlowRebuildsWhenConnectionComesOnline() {
        val applied = configuration(offline = true, connected = false)
        val desired = configuration(offline = false, connected = true)

        assertTrue(vpnPacketFlowNeedsRebuild(true, applied, desired))
    }

    @Test
    fun activePacketFlowRebuildsWhenConnectivityGenerationChanges() {
        val applied = configuration(connected = false)
        val desired = configuration(connected = true)

        assertTrue(vpnPacketFlowNeedsRebuild(true, applied, desired))
    }

    @Test
    fun activePacketFlowRebuildsWhenSplitRulesChange() {
        val applied = configuration()
        val desired = configuration(includedAppIds = setOf("com.android.chrome"))

        assertTrue(vpnPacketFlowNeedsRebuild(true, applied, desired))
    }

    @Test
    fun activePacketFlowRebuildsWhenDnsChanges() {
        val applied = configuration()
        val desired = configuration(dnsIpv4s = listOf("10.0.0.2"))

        assertTrue(vpnPacketFlowNeedsRebuild(true, applied, desired))
    }

    @Test
    fun activePacketFlowRebuildsWhenTunnelAddressChanges() {
        val applied = configuration()
        val desired = configuration(clientIpv4 = "10.0.0.2")

        assertTrue(vpnPacketFlowNeedsRebuild(true, applied, desired))
    }

    @Test
    fun assignedTunnelAddressIsNeverUsedAsDns() {
        val servers = vpnDnsServersForClient(
            clientIpv4 = "10.0.0.128",
            deviceDnsIpv4s = listOf("10.0.0.128"),
            fallbackDnsIpv4s = listOf("65.49.70.65"),
        )

        assertEquals(listOf("65.49.70.65"), servers)
    }

    @Test
    fun assignedTunnelAddressIsRemovedFromConfiguredDnsList() {
        val servers = vpnDnsServersForClient(
            clientIpv4 = "10.0.0.128",
            deviceDnsIpv4s = listOf("10.0.0.128", "9.9.9.9", "9.9.9.9"),
            fallbackDnsIpv4s = listOf("65.49.70.65"),
        )

        assertEquals(listOf("9.9.9.9"), servers)
    }
}
