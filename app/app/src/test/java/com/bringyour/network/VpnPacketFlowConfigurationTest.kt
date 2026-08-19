package com.bringyour.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPacketFlowConfigurationTest {
    private fun configuration(
        offline: Boolean = false,
        connected: Boolean = true,
        killSwitch: Boolean = false,
        connectRequested: Boolean = false,
        includedAppIds: Set<String> = emptySet(),
        excludedAppIds: Set<String> = emptySet(),
        dnsIpv4s: List<String> = listOf("65.49.70.65"),
        clientIpv4: String? = "10.0.0.1",
    ): VpnPacketFlowConfiguration {
        return VpnPacketFlowConfiguration(
            offline = offline,
            connected = connected,
            killSwitch = killSwitch,
            connectRequested = connectRequested,
            includedAppIds = includedAppIds,
            excludedAppIds = excludedAppIds,
            dnsIpv4s = dnsIpv4s,
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

    @Test
    fun tunnelAddressAcceptsOnlyIpv4() {
        assertEquals("169.254.2.1", vpnTunnelIpv4Address(" 169.254.2.1 "))
        assertEquals(null, vpnTunnelIpv4Address("fd00::1"))
        assertEquals(null, vpnTunnelIpv4Address("example.com"))
        assertEquals(null, vpnTunnelIpv4Address("192.0.2.999"))
    }

    @Test
    fun tunnelDnsNeverAdvertisesIpv6() {
        val servers = vpnDnsServersForClient(
            clientIpv4 = "169.254.2.1",
            deviceDnsIpv4s = listOf("fd00::53", "9.9.9.9"),
            fallbackDnsIpv4s = listOf("65.49.70.65"),
        )

        assertEquals(listOf("9.9.9.9"), servers)
    }

    @Test
    fun unsupportedIpv6IsBlockedWithoutBeingAdvertisedOnTheTunnel() {
        assertEquals(
            VpnIpv6Policy.BLOCK_UNSUPPORTED,
            configuration().ipv6Policy,
        )
    }

    @Test
    fun notConnectedProvideOnlyRoutesToEscape() {
        // The reported bug: provide mode Always keeps the tunnel up whenever on
        // a network. With no live exit, no kill switch, and no connect request,
        // the tunnel is up purely to provide: escape (add no routes/DNS) so the
        // device's own apps keep native connectivity.
        assertEquals(
            VpnPacketFlowMode.ESCAPE,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = false, connectRequested = false, includedAppIds = emptySet()),
        )
    }

    @Test
    fun offlineAlwaysRoutesToEscapeEvenWhenConnected() {
        assertEquals(
            VpnPacketFlowMode.ESCAPE,
            vpnPacketFlowMode(offline = true, connected = true, killSwitch = false, connectRequested = false, includedAppIds = setOf("com.android.chrome")),
        )
    }

    @Test
    fun killSwitchKeepsCapturingEvenWhenNotConnected() {
        // Security regression guard: the kill switch ("Allow local traffic when
        // disconnected" off) is implemented BY capturing everything with no
        // exit. ESCAPE must never release the user's traffic to the ISP while
        // the kill switch is on, even when not connected.
        assertEquals(
            VpnPacketFlowMode.DENYLIST,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = true, connectRequested = false, includedAppIds = emptySet()),
        )
    }

    @Test
    fun killSwitchKeepsAllowingExplicitIncludesWhenNotConnected() {
        assertEquals(
            VpnPacketFlowMode.PER_APP_ALLOWLIST,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = true, connectRequested = false, includedAppIds = setOf("com.android.chrome")),
        )
    }

    @Test
    fun connectRequestedKeepsTrafficCapturedDuringProviderDip() {
        // Liveness-flap guard: `connected` is a liveness signal that can
        // momentarily empty during attach or provider rotation. When the user
        // requested connect, intent does not flap, so do not escape: keep the
        // user's traffic captured (do not leak it to the ISP).
        assertEquals(
            VpnPacketFlowMode.DENYLIST,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = false, connectRequested = true, includedAppIds = emptySet()),
        )
    }

    @Test
    fun connectRequestedWithIncludesStaysAllowlistDuringProviderDip() {
        assertEquals(
            VpnPacketFlowMode.PER_APP_ALLOWLIST,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = false, connectRequested = true, includedAppIds = setOf("com.android.chrome")),
        )
    }

    @Test
    fun connectedWithNoIncludesRoutesToDenylist() {
        assertEquals(
            VpnPacketFlowMode.DENYLIST,
            vpnPacketFlowMode(offline = false, connected = true, killSwitch = false, connectRequested = false, includedAppIds = emptySet()),
        )
    }

    @Test
    fun connectedWithIncludesRoutesToPerAppAllowlist() {
        assertEquals(
            VpnPacketFlowMode.PER_APP_ALLOWLIST,
            vpnPacketFlowMode(offline = false, connected = true, killSwitch = false, connectRequested = false, includedAppIds = setOf("com.android.chrome")),
        )
    }

    @Test
    fun notConnectedProvideOnlyIgnoresPerAppIncludesAndStillEscapes() {
        // Fails closed even when the user configured per-app includes: with no
        // live exit and no capture reason, nothing should be captured.
        assertEquals(
            VpnPacketFlowMode.ESCAPE,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = false, connectRequested = false, includedAppIds = setOf("com.android.chrome")),
        )
    }

    // ---------------------------------------------------------------------
    // Corrected-semantics guards (appended). Each of these fails under at
    // least one of the two superseded rule sets:
    //   7.22:        escape <=> offline
    //   first pass:  escape <=> offline || !connected   (defeated the kill switch)
    // ---------------------------------------------------------------------

    private val someIncludes = setOf("com.android.chrome")

    private fun mode(
        offline: Boolean = false,
        connected: Boolean = true,
        killSwitch: Boolean = false,
        connectRequested: Boolean = false,
        includedAppIds: Set<String> = emptySet(),
    ): VpnPacketFlowMode = vpnPacketFlowMode(
        offline = offline,
        connected = connected,
        killSwitch = killSwitch,
        connectRequested = connectRequested,
        includedAppIds = includedAppIds,
    )

    private fun describe(
        offline: Boolean,
        connected: Boolean,
        killSwitch: Boolean,
        connectRequested: Boolean,
        includedAppIds: Set<String>,
    ): String =
        "offline=$offline connected=$connected killSwitch=$killSwitch " +
            "connectRequested=$connectRequested includes=${includedAppIds.size}"

    // --- offline dominates every other input -----------------------------

    @Test
    fun offlineWithKillSwitchStillEscapes() {
        // Offline means there is no underlying network at all, so there is
        // nothing to leak to: keep the interface but route nothing into it.
        assertEquals(
            "offline must escape even with the kill switch on",
            VpnPacketFlowMode.ESCAPE,
            mode(offline = true, connected = false, killSwitch = true),
        )
    }

    @Test
    fun offlineWithConnectRequestedStillEscapes() {
        assertEquals(
            "offline must escape even when the user requested connect",
            VpnPacketFlowMode.ESCAPE,
            mode(offline = true, connected = false, connectRequested = true),
        )
    }

    @Test
    fun offlineWithEveryCaptureReasonSetStillEscapes() {
        assertEquals(
            "offline outranks connected + killSwitch + connectRequested + includes",
            VpnPacketFlowMode.ESCAPE,
            mode(
                offline = true,
                connected = true,
                killSwitch = true,
                connectRequested = true,
                includedAppIds = someIncludes,
            ),
        )
    }

    @Test
    fun offlineEscapesForEveryCombinationOfTheOtherInputs() {
        for (connected in listOf(false, true)) {
            for (killSwitch in listOf(false, true)) {
                for (connectRequested in listOf(false, true)) {
                    for (includes in listOf(emptySet(), someIncludes)) {
                        assertEquals(
                            "offline must always escape: " +
                                describe(true, connected, killSwitch, connectRequested, includes),
                            VpnPacketFlowMode.ESCAPE,
                            mode(
                                offline = true,
                                connected = connected,
                                killSwitch = killSwitch,
                                connectRequested = connectRequested,
                                includedAppIds = includes,
                            ),
                        )
                    }
                }
            }
        }
    }

    // --- kill switch must never be defeated by an escape -----------------

    @Test
    fun killSwitchNeverEscapesRegardlessOfConnectivityOrIncludes() {
        // This is the CRITICAL regression the first-pass fix introduced:
        // `!connected -> ESCAPE` silently released user traffic to the ISP in
        // the clear while the kill switch was on. Escaping is never correct
        // while the user asked for capture-without-exit.
        for (connected in listOf(false, true)) {
            for (connectRequested in listOf(false, true)) {
                for (includes in listOf(emptySet(), someIncludes)) {
                    val actual = mode(
                        offline = false,
                        connected = connected,
                        killSwitch = true,
                        connectRequested = connectRequested,
                        includedAppIds = includes,
                    )
                    assertNotEquals(
                        "kill switch must never escape (that leaks to the ISP): " +
                            describe(false, connected, true, connectRequested, includes),
                        VpnPacketFlowMode.ESCAPE,
                        actual,
                    )
                    assertEquals(
                        "kill switch must honour per-app includes: " +
                            describe(false, connected, true, connectRequested, includes),
                        if (includes.isEmpty()) {
                            VpnPacketFlowMode.DENYLIST
                        } else {
                            VpnPacketFlowMode.PER_APP_ALLOWLIST
                        },
                        actual,
                    )
                }
            }
        }
    }

    @Test
    fun connectedWithKillSwitchRoutesToDenylist() {
        assertEquals(
            "kill switch plus a live exit is ordinary full capture",
            VpnPacketFlowMode.DENYLIST,
            mode(connected = true, killSwitch = true),
        )
    }

    @Test
    fun connectedWithKillSwitchAndIncludesRoutesToPerAppAllowlist() {
        assertEquals(
            "per-app includes still take precedence under the kill switch",
            VpnPacketFlowMode.PER_APP_ALLOWLIST,
            mode(connected = true, killSwitch = true, includedAppIds = someIncludes),
        )
    }

    // --- connect intent must never be defeated by an escape --------------

    @Test
    fun connectRequestedNeverEscapesRegardlessOfConnectivityOrIncludes() {
        // `connected` is a liveness signal (providerStateAdded > 0) that can
        // momentarily empty during attach or provider rotation. Intent does
        // not flap, so a dip must not drop the user's traffic onto the ISP.
        for (connected in listOf(false, true)) {
            for (killSwitch in listOf(false, true)) {
                for (includes in listOf(emptySet(), someIncludes)) {
                    val actual = mode(
                        offline = false,
                        connected = connected,
                        killSwitch = killSwitch,
                        connectRequested = true,
                        includedAppIds = includes,
                    )
                    assertNotEquals(
                        "connect intent must never escape: " +
                            describe(false, connected, killSwitch, true, includes),
                        VpnPacketFlowMode.ESCAPE,
                        actual,
                    )
                    assertEquals(
                        "connect intent must honour per-app includes: " +
                            describe(false, connected, killSwitch, true, includes),
                        if (includes.isEmpty()) {
                            VpnPacketFlowMode.DENYLIST
                        } else {
                            VpnPacketFlowMode.PER_APP_ALLOWLIST
                        },
                        actual,
                    )
                }
            }
        }
    }

    @Test
    fun connectedWithConnectRequestedRoutesToDenylist() {
        assertEquals(
            "the normal connected-client state is full capture",
            VpnPacketFlowMode.DENYLIST,
            mode(connected = true, connectRequested = true),
        )
    }

    @Test
    fun connectedWithConnectRequestedAndIncludesRoutesToPerAppAllowlist() {
        assertEquals(
            "the normal connected-client state with includes is allowlist capture",
            VpnPacketFlowMode.PER_APP_ALLOWLIST,
            mode(connected = true, connectRequested = true, includedAppIds = someIncludes),
        )
    }

    // --- the reported user scenario, and its non-buggy neighbours --------

    @Test
    fun provideAlwaysWithoutClientConnectionEscapesInsteadOfBlackholingDns() {
        // v2026.8.17 user report: provide mode "Always" holds the TUN up
        // whenever on a network. Not connected as a client, no kill switch, no
        // connect intent => every other app was captured into a tunnel with no
        // egress and lost DNS. That state must escape.
        assertEquals(
            "provide-only with no live exit must not capture other apps",
            VpnPacketFlowMode.ESCAPE,
            mode(offline = false, connected = false, killSwitch = false, connectRequested = false),
        )
    }

    @Test
    fun provideAlwaysWithoutClientConnectionEscapesEvenWithPerAppIncludes() {
        assertEquals(
            "stale per-app includes must not resurrect capture with no live exit",
            VpnPacketFlowMode.ESCAPE,
            mode(
                offline = false,
                connected = false,
                killSwitch = false,
                connectRequested = false,
                includedAppIds = someIncludes,
            ),
        )
    }

    @Test
    fun provideWithLiveRelayDoesNotEscape() {
        // Provide/relay regression guard: once there is a live exit the tunnel
        // is useful again, so it must go back to capturing. Escaping here
        // would silently disable the VPN for a connected user.
        assertNotEquals(
            "a live exit must never escape",
            VpnPacketFlowMode.ESCAPE,
            mode(offline = false, connected = true, killSwitch = false, connectRequested = false),
        )
        assertEquals(
            "a live exit with no includes is denylist capture",
            VpnPacketFlowMode.DENYLIST,
            mode(offline = false, connected = true, killSwitch = false, connectRequested = false),
        )
        assertEquals(
            "a live exit with includes is allowlist capture",
            VpnPacketFlowMode.PER_APP_ALLOWLIST,
            mode(
                offline = false,
                connected = true,
                killSwitch = false,
                connectRequested = false,
                includedAppIds = someIncludes,
            ),
        )
    }

    @Test
    fun escapeRequiresAllThreeCaptureReasonsToBeAbsent() {
        // Exhaustive online sweep: ESCAPE is reachable online only from the
        // single provide-only state. Any other online escape is a leak.
        for (connected in listOf(false, true)) {
            for (killSwitch in listOf(false, true)) {
                for (connectRequested in listOf(false, true)) {
                    for (includes in listOf(emptySet(), someIncludes)) {
                        val escaped = mode(
                            offline = false,
                            connected = connected,
                            killSwitch = killSwitch,
                            connectRequested = connectRequested,
                            includedAppIds = includes,
                        ) == VpnPacketFlowMode.ESCAPE
                        assertEquals(
                            "online escape is allowed only when nothing wants capture: " +
                                describe(false, connected, killSwitch, connectRequested, includes),
                            !connected && !killSwitch && !connectRequested,
                            escaped,
                        )
                    }
                }
            }
        }
    }

    // --- the complete 5-input truth table, written out by hand -----------

    private data class ModeCase(
        val offline: Boolean,
        val connected: Boolean,
        val killSwitch: Boolean,
        val connectRequested: Boolean,
        val hasIncludes: Boolean,
        val expected: VpnPacketFlowMode,
    )

    @Test
    fun fullTruthTableMatchesTheCorrectedSemantics() {
        val escape = VpnPacketFlowMode.ESCAPE
        val denylist = VpnPacketFlowMode.DENYLIST
        val allowlist = VpnPacketFlowMode.PER_APP_ALLOWLIST
        val cases = listOf(
            // online (offline = false)
            ModeCase(offline = false, connected = false, killSwitch = false, connectRequested = false, hasIncludes = false, expected = escape),
            ModeCase(offline = false, connected = false, killSwitch = false, connectRequested = false, hasIncludes = true, expected = escape),
            ModeCase(offline = false, connected = false, killSwitch = false, connectRequested = true, hasIncludes = false, expected = denylist),
            ModeCase(offline = false, connected = false, killSwitch = false, connectRequested = true, hasIncludes = true, expected = allowlist),
            ModeCase(offline = false, connected = false, killSwitch = true, connectRequested = false, hasIncludes = false, expected = denylist),
            ModeCase(offline = false, connected = false, killSwitch = true, connectRequested = false, hasIncludes = true, expected = allowlist),
            ModeCase(offline = false, connected = false, killSwitch = true, connectRequested = true, hasIncludes = false, expected = denylist),
            ModeCase(offline = false, connected = false, killSwitch = true, connectRequested = true, hasIncludes = true, expected = allowlist),
            ModeCase(offline = false, connected = true, killSwitch = false, connectRequested = false, hasIncludes = false, expected = denylist),
            ModeCase(offline = false, connected = true, killSwitch = false, connectRequested = false, hasIncludes = true, expected = allowlist),
            ModeCase(offline = false, connected = true, killSwitch = false, connectRequested = true, hasIncludes = false, expected = denylist),
            ModeCase(offline = false, connected = true, killSwitch = false, connectRequested = true, hasIncludes = true, expected = allowlist),
            ModeCase(offline = false, connected = true, killSwitch = true, connectRequested = false, hasIncludes = false, expected = denylist),
            ModeCase(offline = false, connected = true, killSwitch = true, connectRequested = false, hasIncludes = true, expected = allowlist),
            ModeCase(offline = false, connected = true, killSwitch = true, connectRequested = true, hasIncludes = false, expected = denylist),
            ModeCase(offline = false, connected = true, killSwitch = true, connectRequested = true, hasIncludes = true, expected = allowlist),
            // offline (offline = true): always escape
            ModeCase(offline = true, connected = false, killSwitch = false, connectRequested = false, hasIncludes = false, expected = escape),
            ModeCase(offline = true, connected = false, killSwitch = false, connectRequested = false, hasIncludes = true, expected = escape),
            ModeCase(offline = true, connected = false, killSwitch = false, connectRequested = true, hasIncludes = false, expected = escape),
            ModeCase(offline = true, connected = false, killSwitch = false, connectRequested = true, hasIncludes = true, expected = escape),
            ModeCase(offline = true, connected = false, killSwitch = true, connectRequested = false, hasIncludes = false, expected = escape),
            ModeCase(offline = true, connected = false, killSwitch = true, connectRequested = false, hasIncludes = true, expected = escape),
            ModeCase(offline = true, connected = false, killSwitch = true, connectRequested = true, hasIncludes = false, expected = escape),
            ModeCase(offline = true, connected = false, killSwitch = true, connectRequested = true, hasIncludes = true, expected = escape),
            ModeCase(offline = true, connected = true, killSwitch = false, connectRequested = false, hasIncludes = false, expected = escape),
            ModeCase(offline = true, connected = true, killSwitch = false, connectRequested = false, hasIncludes = true, expected = escape),
            ModeCase(offline = true, connected = true, killSwitch = false, connectRequested = true, hasIncludes = false, expected = escape),
            ModeCase(offline = true, connected = true, killSwitch = false, connectRequested = true, hasIncludes = true, expected = escape),
            ModeCase(offline = true, connected = true, killSwitch = true, connectRequested = false, hasIncludes = false, expected = escape),
            ModeCase(offline = true, connected = true, killSwitch = true, connectRequested = false, hasIncludes = true, expected = escape),
            ModeCase(offline = true, connected = true, killSwitch = true, connectRequested = true, hasIncludes = false, expected = escape),
            ModeCase(offline = true, connected = true, killSwitch = true, connectRequested = true, hasIncludes = true, expected = escape),
        )

        assertEquals("the table must cover all 2^5 input combinations", 32, cases.size)
        assertEquals(
            "each combination must appear exactly once",
            32,
            cases.map { it.copy(expected = escape) }.toSet().size,
        )
        for (case in cases) {
            val includes = if (case.hasIncludes) someIncludes else emptySet()
            assertEquals(
                "truth table row: " +
                    describe(case.offline, case.connected, case.killSwitch, case.connectRequested, includes),
                case.expected,
                mode(
                    offline = case.offline,
                    connected = case.connected,
                    killSwitch = case.killSwitch,
                    connectRequested = case.connectRequested,
                    includedAppIds = includes,
                ),
            )
        }
    }

    // --- the new fields must be material state (they drive the rebuild) --

    @Test
    fun activePacketFlowRebuildsWhenKillSwitchIsToggled() {
        // If killSwitch were left out of the configuration's equality, turning
        // the kill switch on while the tunnel was escaping would never rebuild
        // the TUN, and the kill switch would silently do nothing.
        val applied = configuration(connected = false, killSwitch = false)
        val desired = configuration(connected = false, killSwitch = true)

        assertTrue(
            "toggling the kill switch must rebuild the packet flow",
            vpnPacketFlowNeedsRebuild(true, applied, desired),
        )
    }

    @Test
    fun activePacketFlowRebuildsWhenConnectIsRequested() {
        val applied = configuration(connected = false, connectRequested = false)
        val desired = configuration(connected = false, connectRequested = true)

        assertTrue(
            "requesting connect must rebuild the packet flow",
            vpnPacketFlowNeedsRebuild(true, applied, desired),
        )
    }

    @Test
    fun configurationCarriesTheCaptureIntentThroughToTheMode() {
        // End-to-end over the data class: the same snapshot that the service
        // diffs for rebuilds is the one that decides the mode.
        val provideOnly = configuration(connected = false)
        val killSwitchOn = configuration(connected = false, killSwitch = true)

        assertEquals(
            "provide-only snapshot escapes",
            VpnPacketFlowMode.ESCAPE,
            vpnPacketFlowMode(
                offline = provideOnly.offline,
                connected = provideOnly.connected,
                killSwitch = provideOnly.killSwitch,
                connectRequested = provideOnly.connectRequested,
                includedAppIds = provideOnly.includedAppIds,
            ),
        )
        assertEquals(
            "kill-switch snapshot captures",
            VpnPacketFlowMode.DENYLIST,
            vpnPacketFlowMode(
                offline = killSwitchOn.offline,
                connected = killSwitchOn.connected,
                killSwitch = killSwitchOn.killSwitch,
                connectRequested = killSwitchOn.connectRequested,
                includedAppIds = killSwitchOn.includedAppIds,
            ),
        )
        assertNotEquals(
            "the two snapshots must not compare equal",
            provideOnly,
            killSwitchOn,
        )
    }
}