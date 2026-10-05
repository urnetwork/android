package com.bringyour.network

import com.bringyour.network.ui.shared.models.ProvidePowerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The provide pause (P077): one decision from the network, Battery Saver, the
 * charger and the user's power mode, so no source clears another's pause, the
 * mode's storage, and the battery facts the decision reads.
 */
class ProvidePausePolicyTest {

    private data class Row(
        val networkAvailable: Boolean,
        val powerSave: Boolean,
        val charging: Boolean,
        val batteryPct: Int?,
        val mode: ProvidePowerMode,
        val paused: Boolean,
        val reason: ProvidePauseReason,
    )

    // networkAvailable, powerSave, charging, batteryPct, mode -> paused, reason
    private val table = listOf(
        // no matching network pauses in every mode, and is the reason first
        Row(false, false, false, 80, ProvidePowerMode.ALWAYS, true, ProvidePauseReason.NO_NETWORK),
        Row(false, true, false, 80, ProvidePowerMode.PAUSE_IN_BATTERY_SAVER, true, ProvidePauseReason.NO_NETWORK),
        Row(false, false, false, 80, ProvidePowerMode.CHARGING_ONLY, true, ProvidePauseReason.NO_NETWORK),
        Row(false, false, true, 80, ProvidePowerMode.CHARGING_ONLY, true, ProvidePauseReason.NO_NETWORK),
        // always never pauses for power
        Row(true, false, false, 80, ProvidePowerMode.ALWAYS, false, ProvidePauseReason.NONE),
        Row(true, true, false, 5, ProvidePowerMode.ALWAYS, false, ProvidePauseReason.NONE),
        // the default pauses in Battery Saver, on battery or not
        Row(true, false, false, 80, ProvidePowerMode.PAUSE_IN_BATTERY_SAVER, false, ProvidePauseReason.NONE),
        Row(true, false, false, 5, ProvidePowerMode.PAUSE_IN_BATTERY_SAVER, false, ProvidePauseReason.NONE),
        Row(true, true, false, 80, ProvidePowerMode.PAUSE_IN_BATTERY_SAVER, true, ProvidePauseReason.BATTERY_SAVER),
        Row(true, true, true, 80, ProvidePowerMode.PAUSE_IN_BATTERY_SAVER, true, ProvidePauseReason.BATTERY_SAVER),
        Row(true, false, true, 80, ProvidePowerMode.PAUSE_IN_BATTERY_SAVER, false, ProvidePauseReason.NONE),
        // charging only pauses on battery, and in Battery Saver
        Row(true, false, false, 80, ProvidePowerMode.CHARGING_ONLY, true, ProvidePauseReason.NOT_CHARGING),
        Row(true, false, false, 100, ProvidePowerMode.CHARGING_ONLY, true, ProvidePauseReason.NOT_CHARGING),
        Row(true, false, true, 80, ProvidePowerMode.CHARGING_ONLY, false, ProvidePauseReason.NONE),
        Row(true, false, true, 100, ProvidePowerMode.CHARGING_ONLY, false, ProvidePauseReason.NONE),
        Row(true, true, false, 80, ProvidePowerMode.CHARGING_ONLY, true, ProvidePauseReason.BATTERY_SAVER),
        Row(true, true, true, 80, ProvidePowerMode.CHARGING_ONLY, true, ProvidePauseReason.BATTERY_SAVER),
        // a device without a battery is on external power
        Row(true, false, false, null, ProvidePowerMode.CHARGING_ONLY, false, ProvidePauseReason.NONE),
        Row(true, false, false, null, ProvidePowerMode.PAUSE_IN_BATTERY_SAVER, false, ProvidePauseReason.NONE),
    )

    @Test
    fun everyRowOfTheTable() {
        table.forEach { row ->
            val decision = providePauseDecision(
                ProvidePauseFacts(
                    networkAvailable = row.networkAvailable,
                    powerSave = row.powerSave,
                    charging = row.charging,
                    batteryPct = row.batteryPct,
                    mode = row.mode,
                )
            )
            assertEquals(row.toString(), ProvidePauseDecision(row.paused, row.reason), decision)
        }
    }

    @Test
    fun aNetworkCannotClearABatteryPause() {
        // the network callback used to set providePaused = false on its own
        // whenever a matching network appeared
        val batterySaver = ProvidePauseFacts(
            networkAvailable = false,
            powerSave = true,
            charging = false,
            batteryPct = 40,
            mode = ProvidePowerMode.DEFAULT,
        )
        assertEquals(true, providePauseDecision(batterySaver).paused)
        assertEquals(
            ProvidePauseDecision(paused = true, reason = ProvidePauseReason.BATTERY_SAVER),
            providePauseDecision(batterySaver.copy(networkAvailable = true)),
        )

        val unplugged = batterySaver.copy(powerSave = false, mode = ProvidePowerMode.CHARGING_ONLY)
        assertEquals(
            ProvidePauseDecision(paused = true, reason = ProvidePauseReason.NOT_CHARGING),
            providePauseDecision(unplugged.copy(networkAvailable = true)),
        )
    }

    @Test
    fun theChargerCannotClearANetworkPause() {
        val noNetwork = ProvidePauseFacts(
            networkAvailable = false,
            powerSave = false,
            charging = false,
            batteryPct = 40,
            mode = ProvidePowerMode.CHARGING_ONLY,
        )
        assertEquals(
            ProvidePauseDecision(paused = true, reason = ProvidePauseReason.NO_NETWORK),
            providePauseDecision(noNetwork.copy(charging = true)),
        )
    }

    @Test
    fun theDefaultModePausesInBatterySaver() {
        assertEquals(ProvidePowerMode.PAUSE_IN_BATTERY_SAVER, ProvidePowerMode.DEFAULT)
    }

    @Test
    fun powerModesRoundTripThroughStorage() {
        ProvidePowerMode.entries.forEach { mode ->
            assertEquals(mode, ProvidePowerMode.fromString(ProvidePowerMode.toString(mode)))
        }
        assertNull(ProvidePowerMode.fromString(null))
        assertNull(ProvidePowerMode.fromString("sometimes"))
    }

    @Test
    fun pluggedInOnAnySourceIsCharging() {
        // BatteryManager.BATTERY_PLUGGED_AC = 1, USB = 2, WIRELESS = 4, DOCK = 8
        listOf(1, 2, 4, 8).forEach { plugged ->
            assertEquals(
                BatteryPowerFacts(charging = true, batteryPct = 80),
                batteryPowerFacts(plugged = plugged, present = true, level = 80, scale = 100),
            )
        }
        assertEquals(
            BatteryPowerFacts(charging = false, batteryPct = 80),
            batteryPowerFacts(plugged = 0, present = true, level = 80, scale = 100),
        )
    }

    @Test
    fun theLevelIsAPercentOfTheScale() {
        assertEquals(50, batteryPowerFacts(plugged = 0, present = true, level = 100, scale = 200).batteryPct)
        assertEquals(100, batteryPowerFacts(plugged = 0, present = true, level = 120, scale = 100).batteryPct)
        // unknown readings have no level
        assertNull(batteryPowerFacts(plugged = 0, present = true, level = -1, scale = 100).batteryPct)
        assertNull(batteryPowerFacts(plugged = 0, present = true, level = 50, scale = 0).batteryPct)
        // no battery at all
        assertNull(batteryPowerFacts(plugged = 1, present = false, level = 0, scale = 100).batteryPct)
    }
}
