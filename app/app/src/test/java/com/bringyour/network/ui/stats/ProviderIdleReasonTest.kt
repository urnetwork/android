package com.bringyour.network.ui.stats

import com.bringyour.network.ProvidePauseReason
import com.bringyour.network.R
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Why an enabled provider gets no traffic, from this device's own state
 * (P008, P077): the mode first, then a pause and what caused it, then no
 * traffic yet, and the line each reason shows.
 */
class ProviderIdleReasonTest {

    // Sdk.ProvideMode* are compile-time constants, which kotlin inlines: this
    // test never loads the sdk's native library
    private fun reason(
        controlMode: ProvideControlMode?,
        liveProvideMode: Long = Sdk.ProvideModePublic,
        providePaused: Boolean = false,
        provideNetworkMode: ProvideNetworkMode = ProvideNetworkMode.ALL,
        recentProviderBytes: Long = 4096,
        providePauseReason: ProvidePauseReason = ProvidePauseReason.NONE,
    ): ProviderIdleReason {
        return providerIdleReason(
            controlMode = controlMode,
            liveProvideMode = liveProvideMode,
            providePaused = providePaused,
            provideNetworkMode = provideNetworkMode,
            recentProviderBytes = recentProviderBytes,
            providePauseReason = providePauseReason,
        )
    }

    @Test
    fun neverHasNoReasonEvenWhenPausedOrIdle() {
        assertEquals(ProviderIdleReason.NONE, reason(ProvideControlMode.NEVER, liveProvideMode = Sdk.ProvideModeNone))
        assertEquals(
            ProviderIdleReason.NONE,
            reason(
                ProvideControlMode.NEVER,
                liveProvideMode = Sdk.ProvideModeNone,
                providePaused = true,
                provideNetworkMode = ProvideNetworkMode.WIFI,
                recentProviderBytes = 0,
            ),
        )
    }

    @Test
    fun anUnknownControlModeHasNoReason() {
        // the sdk's unexposed manual mode, or any mode this app does not know
        assertNull(ProvideControlMode.fromString("manual"))
        assertEquals(
            ProviderIdleReason.NONE,
            reason(ProvideControlMode.fromString("manual"), providePaused = true, recentProviderBytes = 0),
        )
        assertEquals(ProviderIdleReason.NONE, reason(null, liveProvideMode = Sdk.ProvideModeNetwork))
    }

    @Test
    fun networkModeIsNetworkOnlyEvenWhenPaused() {
        assertEquals(
            ProviderIdleReason.NETWORK_ONLY,
            reason(ProvideControlMode.NETWORK, liveProvideMode = Sdk.ProvideModeNetwork),
        )
        assertEquals(
            ProviderIdleReason.NETWORK_ONLY,
            reason(
                ProvideControlMode.NETWORK,
                liveProvideMode = Sdk.ProvideModeNetwork,
                providePaused = true,
                provideNetworkMode = ProvideNetworkMode.WIFI,
                recentProviderBytes = 0,
            ),
        )
    }

    @Test
    fun autoWhileDisconnectedSharesOnlyWithOwnDevices() {
        assertEquals(
            ProviderIdleReason.AUTO_NOT_CONNECTED,
            reason(ProvideControlMode.AUTO, liveProvideMode = Sdk.ProvideModeNetwork),
        )
        // before the sdk applies a tier
        assertEquals(
            ProviderIdleReason.AUTO_NOT_CONNECTED,
            reason(ProvideControlMode.AUTO, liveProvideMode = Sdk.ProvideModeNone),
        )
        // Auto + disconnected + paused
        assertEquals(
            ProviderIdleReason.AUTO_NOT_CONNECTED,
            reason(
                ProvideControlMode.AUTO,
                liveProvideMode = Sdk.ProvideModeNetwork,
                providePaused = true,
                provideNetworkMode = ProvideNetworkMode.WIFI,
                recentProviderBytes = 0,
            ),
        )
    }

    @Test
    fun autoWhileConnectedReadsLikeAlways() {
        assertEquals(ProviderIdleReason.NONE, reason(ProvideControlMode.AUTO))
        assertEquals(ProviderIdleReason.NO_TRAFFIC_YET, reason(ProvideControlMode.AUTO, recentProviderBytes = 0))
        assertEquals(
            ProviderIdleReason.PAUSED_WIFI_ONLY,
            reason(ProvideControlMode.AUTO, providePaused = true, provideNetworkMode = ProvideNetworkMode.WIFI),
        )
    }

    @Test
    fun pausedOnWifiOnlyIsPausedWifiOnly() {
        // Always + paused + WiFi
        assertEquals(
            ProviderIdleReason.PAUSED_WIFI_ONLY,
            reason(ProvideControlMode.ALWAYS, providePaused = true, provideNetworkMode = ProvideNetworkMode.WIFI),
        )
    }

    @Test
    fun pausedWithAllNetworksIsPausedNoNetwork() {
        // Always + paused + All
        assertEquals(
            ProviderIdleReason.PAUSED_NO_NETWORK,
            reason(ProvideControlMode.ALWAYS, providePaused = true, provideNetworkMode = ProvideNetworkMode.ALL),
        )
    }

    @Test
    fun aPauseWinsOverNoTraffic() {
        assertEquals(
            ProviderIdleReason.PAUSED_NO_NETWORK,
            reason(ProvideControlMode.ALWAYS, providePaused = true, recentProviderBytes = 0),
        )
    }

    @Test
    fun publicWithoutTrafficIsNoTrafficYet() {
        // Always + 0 bytes, Always + bytes
        assertEquals(ProviderIdleReason.NO_TRAFFIC_YET, reason(ProvideControlMode.ALWAYS, recentProviderBytes = 0))
        assertEquals(ProviderIdleReason.NONE, reason(ProvideControlMode.ALWAYS, recentProviderBytes = 1))
    }

    @Test
    fun noTrafficYetNeedsThePublicTier() {
        assertEquals(
            ProviderIdleReason.NONE,
            reason(ProvideControlMode.ALWAYS, liveProvideMode = Sdk.ProvideModeNetwork, recentProviderBytes = 0),
        )
    }

    @Test
    fun aBatterySaverPauseSaysBatterySaver() {
        assertEquals(
            ProviderIdleReason.PAUSED_BATTERY_SAVER,
            reason(
                ProvideControlMode.ALWAYS,
                providePaused = true,
                provideNetworkMode = ProvideNetworkMode.WIFI,
                recentProviderBytes = 0,
                providePauseReason = ProvidePauseReason.BATTERY_SAVER,
            ),
        )
        // Auto while connected
        assertEquals(
            ProviderIdleReason.PAUSED_BATTERY_SAVER,
            reason(
                ProvideControlMode.AUTO,
                providePaused = true,
                providePauseReason = ProvidePauseReason.BATTERY_SAVER,
            ),
        )
    }

    @Test
    fun aChargingOnlyPauseSaysNotCharging() {
        assertEquals(
            ProviderIdleReason.PAUSED_NOT_CHARGING,
            reason(
                ProvideControlMode.ALWAYS,
                providePaused = true,
                provideNetworkMode = ProvideNetworkMode.WIFI,
                providePauseReason = ProvidePauseReason.NOT_CHARGING,
            ),
        )
    }

    @Test
    fun aNetworkPauseStillSaysWhichNetwork() {
        assertEquals(
            ProviderIdleReason.PAUSED_WIFI_ONLY,
            reason(
                ProvideControlMode.ALWAYS,
                providePaused = true,
                provideNetworkMode = ProvideNetworkMode.WIFI,
                providePauseReason = ProvidePauseReason.NO_NETWORK,
            ),
        )
        assertEquals(
            ProviderIdleReason.PAUSED_NO_NETWORK,
            reason(ProvideControlMode.ALWAYS, providePaused = true, providePauseReason = ProvidePauseReason.NO_NETWORK),
        )
    }

    @Test
    fun theModeRulesWinOverABatteryPause() {
        assertEquals(
            ProviderIdleReason.NONE,
            reason(
                ProvideControlMode.NEVER,
                liveProvideMode = Sdk.ProvideModeNone,
                providePaused = true,
                providePauseReason = ProvidePauseReason.BATTERY_SAVER,
            ),
        )
        assertEquals(
            ProviderIdleReason.NETWORK_ONLY,
            reason(
                ProvideControlMode.NETWORK,
                liveProvideMode = Sdk.ProvideModeNetwork,
                providePaused = true,
                providePauseReason = ProvidePauseReason.NOT_CHARGING,
            ),
        )
        assertEquals(
            ProviderIdleReason.AUTO_NOT_CONNECTED,
            reason(
                ProvideControlMode.AUTO,
                liveProvideMode = Sdk.ProvideModeNetwork,
                providePaused = true,
                providePauseReason = ProvidePauseReason.BATTERY_SAVER,
            ),
        )
    }

    @Test
    fun aBatteryReasonWithoutAPauseIsNotShown() {
        // the device's own pause flag decides whether there is a pause
        assertEquals(
            ProviderIdleReason.NO_TRAFFIC_YET,
            reason(
                ProvideControlMode.ALWAYS,
                recentProviderBytes = 0,
                providePauseReason = ProvidePauseReason.BATTERY_SAVER,
            ),
        )
        assertEquals(
            ProviderIdleReason.NONE,
            reason(ProvideControlMode.ALWAYS, providePauseReason = ProvidePauseReason.NOT_CHARGING),
        )
    }

    @Test
    fun everyReasonButNoneHasItsLine() {
        assertNull(ProviderIdleReason.NONE.messageResourceId)
        assertEquals(R.string.provider_idle_auto_not_connected, ProviderIdleReason.AUTO_NOT_CONNECTED.messageResourceId)
        assertEquals(R.string.provider_idle_network_only, ProviderIdleReason.NETWORK_ONLY.messageResourceId)
        assertEquals(
            R.string.provider_idle_paused_battery_saver,
            ProviderIdleReason.PAUSED_BATTERY_SAVER.messageResourceId,
        )
        assertEquals(
            R.string.provider_idle_paused_not_charging,
            ProviderIdleReason.PAUSED_NOT_CHARGING.messageResourceId,
        )
        assertEquals(R.string.provider_idle_paused_wifi_only, ProviderIdleReason.PAUSED_WIFI_ONLY.messageResourceId)
        assertEquals(R.string.provider_idle_paused_no_network, ProviderIdleReason.PAUSED_NO_NETWORK.messageResourceId)
        assertEquals(R.string.provider_idle_no_traffic_yet, ProviderIdleReason.NO_TRAFFIC_YET.messageResourceId)
    }

    @Test
    fun onlyTheDeviceStateReasonsAreLocal() {
        assertTrue(ProviderIdleReason.AUTO_NOT_CONNECTED.local)
        assertTrue(ProviderIdleReason.NETWORK_ONLY.local)
        assertTrue(ProviderIdleReason.PAUSED_BATTERY_SAVER.local)
        assertTrue(ProviderIdleReason.PAUSED_NOT_CHARGING.local)
        assertTrue(ProviderIdleReason.PAUSED_WIFI_ONLY.local)
        assertTrue(ProviderIdleReason.PAUSED_NO_NETWORK.local)
        assertFalse(ProviderIdleReason.NO_TRAFFIC_YET.local)
        assertFalse(ProviderIdleReason.NONE.local)
    }
}
