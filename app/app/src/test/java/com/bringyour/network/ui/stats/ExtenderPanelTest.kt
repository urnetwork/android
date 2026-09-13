package com.bringyour.network.ui.stats

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The extender panel's display rules (EXTENDER.md K4): which extenders get a
 * ring, the gossip state mapping, and the counts the panel prints.
 */
class ExtenderPanelTest {

    private fun extender(ip: String, colorHex: String, inUse: Int) =
        ExtenderUi(ip = ip, colorHex = colorHex, inUse = inUse)

    @Test
    fun onlyExtendersCarryingAConnectionGetARing() {
        val panel = ExtenderPanelUi(
            extenders = listOf(
                extender("192.0.2.1", "3cdd67", 1),
                extender("192.0.2.2", "aabbcc", 0),
                extender("2001:db8::1", "dd4f3c", 3),
            ),
        )

        // in the sdk's order, in each extender's own color
        assertEquals(listOf("3cdd67", "dd4f3c"), panel.ringColorHexes)
    }

    @Test
    fun noExtendersDrawsNoRings() {
        assertEquals(listOf<String>(), ExtenderPanelUi().ringColorHexes)
    }

    @Test
    fun gossipStatesMapToTheirDotAndLabel() {
        assertEquals(ExtenderGossipUi.CONNECTED, ExtenderGossipUi.fromSdk("connected"))
        assertEquals(ExtenderGossipUi.CONNECTING, ExtenderGossipUi.fromSdk("connecting"))
        assertEquals(ExtenderGossipUi.DISCONNECTED, ExtenderGossipUi.fromSdk("disconnected"))

        assertEquals(R.string.connected, ExtenderGossipUi.CONNECTED.labelResId())
        assertEquals(R.string.gossip_connecting, ExtenderGossipUi.CONNECTING.labelResId())
        assertEquals(R.string.disconnected, ExtenderGossipUi.DISCONNECTED.labelResId())
    }

    @Test
    fun anUnknownOrMissingGossipStateReadsAsDisconnected() {
        assertEquals(ExtenderGossipUi.DISCONNECTED, ExtenderGossipUi.fromSdk(""))
        assertEquals(ExtenderGossipUi.DISCONNECTED, ExtenderGossipUi.fromSdk(null))
        assertEquals(ExtenderGossipUi.DISCONNECTED, ExtenderGossipUi.fromSdk("degraded"))
        assertEquals(ExtenderGossipUi.DISCONNECTED, ExtenderPanelUi().gossip)
    }

    @Test
    fun theCountsAreTheStatusCountsAndNotTheRingCount() {
        // K4: an address that just went on hold with a live connection counts
        // as active and not as reserve, so the panel reports both as given
        val panel = ExtenderPanelUi(
            extenders = listOf(
                extender("192.0.2.1", "3cdd67", 1),
                extender("192.0.2.2", "aabbcc", 2),
            ),
            activeCount = 2,
            reserveCount = 1,
        )

        assertEquals(2, panel.ringColorHexes.size)
        assertEquals(2, panel.activeCount)
        assertEquals(1, panel.reserveCount)
    }

    @Test
    fun aDeviceWithNoExtenderNetworkHidesThePanel() {
        assertFalse(ExtenderPanelUi().present)
        // a reserve with nothing dialed yet is still an extender network
        assertTrue(ExtenderPanelUi(reserveCount = 4).present)
        assertTrue(
            ExtenderPanelUi(extenders = listOf(extender("192.0.2.1", "3cdd67", 0))).present
        )
    }
}
