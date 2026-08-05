package com.bringyour.network.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLocationStateTest {

    private val tokyo = MockLocationTarget(
        clientId = "client-1",
        label = "Tokyo, Japan",
        lat = 35.6762,
        lon = 139.6503,
    )

    private fun resolve(
        enabled: Boolean = true,
        devOptionsEnabled: Boolean = true,
        isSelectedMockApp: Boolean = true,
        locationServicesEnabled: Boolean = true,
        tunnelUp: Boolean = true,
        target: MockLocationTarget? = tokyo,
        orphaned: Boolean = false,
    ): MockLocationStatus {
        return resolveMockLocationStatus(
            enabled = enabled,
            devOptionsEnabled = devOptionsEnabled,
            isSelectedMockApp = isSelectedMockApp,
            locationServicesEnabled = locationServicesEnabled,
            tunnelUp = tunnelUp,
            target = target,
            orphaned = orphaned,
        )
    }

    @Test
    fun disabledWinsOverEverySignalWhenNotOrphaned() {
        assertEquals(MockLocationStatus.DISABLED, resolve(enabled = false))
        // gates are not reported while the toggle is off
        assertEquals(
            MockLocationStatus.DISABLED,
            resolve(
                enabled = false,
                devOptionsEnabled = false,
                isSelectedMockApp = false,
                locationServicesEnabled = false,
                tunnelUp = false,
                target = null,
            ),
        )
    }

    @Test
    fun orphanedWinsEvenWhenDisabled() {
        // the flag only clears on successful cleanup; until then the user
        // must see the recovery instructions regardless of the toggle
        assertEquals(MockLocationStatus.ORPHANED, resolve(enabled = false, orphaned = true))
    }

    @Test
    fun orphanedWinsOverActiveConditions() {
        assertEquals(MockLocationStatus.ORPHANED, resolve(orphaned = true))
    }

    @Test
    fun orphanedWinsOverGates() {
        assertEquals(
            MockLocationStatus.ORPHANED,
            resolve(
                devOptionsEnabled = false,
                isSelectedMockApp = false,
                locationServicesEnabled = false,
                orphaned = true,
            ),
        )
    }

    @Test
    fun devOptionsGateComesFirst() {
        assertEquals(
            MockLocationStatus.NEEDS_DEV_OPTIONS,
            resolve(
                devOptionsEnabled = false,
                isSelectedMockApp = false,
                locationServicesEnabled = false,
            ),
        )
    }

    @Test
    fun selectionGateComesSecond() {
        assertEquals(
            MockLocationStatus.NEEDS_SELECTION,
            resolve(
                isSelectedMockApp = false,
                locationServicesEnabled = false,
            ),
        )
    }

    @Test
    fun locationServicesGateComesThird() {
        assertEquals(
            MockLocationStatus.NEEDS_LOCATION_ON,
            resolve(locationServicesEnabled = false),
        )
    }

    @Test
    fun eligibleWhenNoTunnelAndNoTarget() {
        assertEquals(
            MockLocationStatus.ELIGIBLE,
            resolve(tunnelUp = false, target = null),
        )
    }

    @Test
    fun targetPresentButTunnelDownIsEligible() {
        assertEquals(MockLocationStatus.ELIGIBLE, resolve(tunnelUp = false))
    }

    @Test
    fun tunnelUpButNoTargetIsEligible() {
        assertEquals(MockLocationStatus.ELIGIBLE, resolve(target = null))
    }

    @Test
    fun activeOnlyWhenTunnelUpAndTargetPresent() {
        assertEquals(MockLocationStatus.ACTIVE, resolve())
    }

    private fun state(
        devOptionsEnabled: Boolean = true,
        mockAppSelected: Boolean = true,
        locationServicesEnabled: Boolean = true,
    ) = MockLocationState(
        status = MockLocationStatus.DISABLED,
        enabled = false,
        target = null,
        devOptionsEnabled = devOptionsEnabled,
        mockAppSelected = mockAppSelected,
        locationServicesEnabled = locationServicesEnabled,
    )

    // The toggle opens the setup guide only when setup is incomplete, and the
    // guide marks its steps from these signals. Both must stay readable while
    // the feature is off, when `status` is DISABLED no matter how the device
    // is configured.
    @Test
    fun setupCompleteIsReportedWhileTheFeatureIsOff() {
        val complete = state()
        assertEquals(MockLocationStatus.DISABLED, complete.status)
        assertTrue(complete.setupComplete)
    }

    @Test
    fun setupIsIncompleteWhenAnySignalIsMissing() {
        assertFalse(state(devOptionsEnabled = false).setupComplete)
        assertFalse(state(mockAppSelected = false).setupComplete)
        assertFalse(state(locationServicesEnabled = false).setupComplete)
    }
}
