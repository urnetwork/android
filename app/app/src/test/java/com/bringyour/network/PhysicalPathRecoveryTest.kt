package com.bringyour.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalPathRecoveryTest {
    private val settings = TunnelRecoverySettings()
    private val debounce = settings.transportRecoveryDebounceMillis

    private fun replay() = SwitchReplay(settings)

    /**
     * Feeds PhysicalPathRecovery the way MainApplication's two callbacks do and
     * replays its handler on a fake clock: every reason replaces the pending
     * recovery, which fires after TunnelRecoveryPolicy's delay.
     */
    private class SwitchReplay(settings: TunnelRecoverySettings) {
        val path = PhysicalPathRecovery<String>()
        private val policy = TunnelRecoveryPolicy(settings)
        private var nowMillis = 0L
        private var pending: ScheduledRecovery? = null
        private var pendingAtMillis = 0L
        private val kicks = mutableListOf<Pair<Long, Set<String>>>()

        private fun advanceTo(millis: Long) {
            val scheduled = pending
            if (scheduled != null && pendingAtMillis <= millis) {
                pending = null
                policy.consumeTransportRecovery(scheduled.generation, pendingAtMillis)
                    ?.let { kicks.add(pendingAtMillis to it.reasons) }
            }
            nowMillis = millis
        }

        private fun request(reason: String?) {
            reason ?: return
            val scheduled = policy.requestTransportRecovery(nowMillis, reason, physicalPathChange = true)
            pending = scheduled
            pendingAtMillis = nowMillis + scheduled.delayMillis
        }

        /** A registration that finds [network] connected and default. */
        fun onlineOn(network: String, interfaceName: String, vararg addresses: String) {
            memberAvailable(0, network)
            defaultAvailable(0, network)
            defaultLink(0, network, interfaceName, *addresses)
        }

        fun memberAvailable(atMillis: Long, network: String) {
            advanceTo(atMillis)
            request(path.onMemberAvailable(network).recoveryReason)
        }

        fun memberLost(atMillis: Long, network: String) {
            advanceTo(atMillis)
            request(path.onMemberLost(network).recoveryReason)
        }

        fun defaultAvailable(atMillis: Long, network: String) {
            advanceTo(atMillis)
            request(path.onDefaultAvailable(network))
        }

        fun defaultLost(atMillis: Long, network: String) {
            advanceTo(atMillis)
            path.onDefaultLost(network)
        }

        fun defaultLink(atMillis: Long, network: String, interfaceName: String, vararg addresses: String) {
            advanceTo(atMillis)
            request(
                path.onDefaultLinkPropertiesChanged(
                    network,
                    PhysicalLinkSnapshot(interfaceName, addresses.toSet()),
                ),
            )
        }

        /** Every re-dial, as (time, coalesced reasons). */
        fun settle(): List<Pair<Long, Set<String>>> {
            advanceTo(Long.MAX_VALUE / 2)
            return kicks
        }
    }

    @Test
    fun wifiJoiningCellularRedialsOnceWhenTheDefaultMoves() {
        // Android's usual cell -> wifi move: the join arrives at L3 connect,
        // the default moves only once wifi validates, IPv6 lands after IPv4.
        val replay = replay()
        replay.onlineOn("cell", "rmnet0", "198.51.100.7")

        replay.memberAvailable(10_000, "wifi")
        replay.defaultAvailable(10_900, "wifi")
        replay.defaultLink(10_900, "wifi", "wlan0", "192.0.2.10")
        replay.defaultLink(11_400, "wifi", "wlan0", "192.0.2.10", "2001:db8::10")

        assertEquals(listOf(10_900L + debounce to setOf(RECOVERY_DEFAULT_NETWORK_CHANGED)), replay.settle())
    }

    @Test
    fun cellularLingeringOutAfterTheMoveDoesNotRedial() {
        val replay = replay()
        replay.onlineOn("cell", "rmnet0", "198.51.100.7")
        replay.memberAvailable(10_000, "wifi")
        replay.defaultAvailable(10_900, "wifi")

        replay.memberLost(40_900, "cell")

        assertEquals(listOf(10_900L + debounce to setOf(RECOVERY_DEFAULT_NETWORK_CHANGED)), replay.settle())
    }

    @Test
    fun backgroundCellularBounceDoesNotRedialWifi() {
        val replay = replay()
        replay.onlineOn("wifi", "wlan0", "192.0.2.10")
        replay.memberAvailable(0, "cell")

        replay.memberLost(5_000, "cell")
        replay.memberAvailable(8_000, "cell-2")

        assertTrue(replay.settle().isEmpty())
    }

    @Test
    fun wifiLossWithCellularUpRedialsOnce() {
        val replay = replay()
        replay.onlineOn("wifi", "wlan0", "192.0.2.10")
        replay.memberAvailable(0, "cell")

        replay.memberLost(20_000, "wifi")
        replay.defaultLost(20_000, "wifi")
        replay.defaultAvailable(20_001, "cell")

        assertEquals(listOf(20_001L + debounce to setOf(RECOVERY_DEFAULT_NETWORK_CHANGED)), replay.settle())
    }

    @Test
    fun badWifiAvoidanceRedialsAtTheMoveAndNotAgainWhenWifiLeaves() {
        val replay = replay()
        replay.onlineOn("wifi", "wlan0", "192.0.2.10")
        replay.memberAvailable(0, "cell")

        // wifi lost validation and the default moved while it stays associated
        replay.defaultAvailable(30_000, "cell")
        replay.memberLost(33_000, "wifi")

        assertEquals(listOf(30_000L + debounce to setOf(RECOVERY_DEFAULT_NETWORK_CHANGED)), replay.settle())
    }

    @Test
    fun pathReturningAfterOfflineRedialsOnce() {
        val replay = replay()
        replay.onlineOn("wifi", "wlan0", "192.0.2.10")
        replay.memberLost(10_000, "wifi")
        replay.defaultLost(10_000, "wifi")

        replay.memberAvailable(13_000, "cell")
        replay.defaultAvailable(13_000, "cell")

        assertEquals(
            listOf(
                13_000L + debounce to setOf(RECOVERY_PHYSICAL_NETWORK_RETURNED, RECOVERY_DEFAULT_NETWORK_CHANGED),
            ),
            replay.settle(),
        )
    }

    @Test
    fun firstPathAfterARegistrationWithoutNetworkRedials() {
        val replay = replay()
        replay.path.startedWithoutNetwork()

        replay.memberAvailable(60_000, "cell")
        replay.defaultAvailable(60_000, "cell")
        replay.defaultLink(60_000, "cell", "rmnet0", "198.51.100.7")

        assertEquals(listOf(60_000L + debounce to setOf(RECOVERY_PHYSICAL_NETWORK_RETURNED)), replay.settle())
    }

    @Test
    fun firstPathAfterARegistrationWithNetworkIsOnlyABaseline() {
        val replay = replay()
        replay.onlineOn("cell", "rmnet0", "198.51.100.7")

        assertTrue(replay.settle().isEmpty())
    }

    @Test
    fun addressRemovedFromTheDefaultRedials() {
        val replay = replay()
        replay.onlineOn("wifi", "wlan0", "192.0.2.10", "2001:db8::10")

        // the DHCP lease moved
        replay.defaultLink(5_000, "wifi", "wlan0", "192.0.2.11", "2001:db8::10")

        assertEquals(listOf(5_000L + debounce to setOf(RECOVERY_DEFAULT_LINK_CHANGED)), replay.settle())
    }

    @Test
    fun nonDefaultDebounceIsHonored() {
        val replay = SwitchReplay(TunnelRecoverySettings(transportRecoveryDebounceMillis = 1_000L))
        replay.onlineOn("cell", "rmnet0", "198.51.100.7")

        replay.memberAvailable(10_000, "wifi")
        replay.defaultAvailable(10_900, "wifi")
        replay.defaultLink(10_900, "wifi", "wlan0", "192.0.2.10")
        replay.defaultLink(11_400, "wifi", "wlan0", "192.0.2.10", "2001:db8::10")

        assertEquals(listOf(11_900L to setOf(RECOVERY_DEFAULT_NETWORK_CHANGED)), replay.settle())
    }

    @Test
    fun addressingOfANetworkThatIsNotTheDefaultDoesNotRedial() {
        val replay = replay()
        replay.onlineOn("cell", "rmnet0", "198.51.100.7")
        replay.memberAvailable(10_000, "wifi")

        replay.defaultLink(10_100, "wifi", "wlan0", "192.0.2.10")
        replay.defaultLink(10_200, "wifi", "wlan0")

        assertTrue(replay.settle().isEmpty())
    }

    @Test
    fun linkLocalAddressesAreLeftOutOfTheSnapshot() {
        val addresses = listOf("fe80::1", "169.254.3.4", "192.0.2.10", "2001:db8::10")
            .map(InetAddress::getByName)

        assertEquals(
            PhysicalLinkSnapshot(
                "wlan0",
                setOf(
                    InetAddress.getByName("192.0.2.10").hostAddress!!,
                    InetAddress.getByName("2001:db8::10").hostAddress!!,
                ),
            ),
            physicalLinkSnapshot("wlan0", addresses),
        )
        assertEquals("", physicalLinkSnapshot(null, emptyList()).interfaceName)
    }
}
