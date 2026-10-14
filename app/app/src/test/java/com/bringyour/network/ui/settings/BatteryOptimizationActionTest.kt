package com.bringyour.network.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Support inbox 7932: Android's battery optimization setting cannot be found
 * by searching system settings, and the Google Play build had no in-app path
 * to it (the row was hidden because Play removes
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS). The other builds could only turn the
 * exemption on; once exempt the switch was disabled and did nothing.
 *
 * The row is now on every flavor, and every tap does something: the exemption
 * dialog where the flavor may ask for it, otherwise the system battery
 * optimization list (no permission needed), or App info when the list does not
 * resolve on the device.
 */
class BatteryOptimizationActionTest {
    companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private fun moduleFile(path: String): File {
            val file = listOf("", "app/", "app/app/")
                .map { File(it + path) }
                .firstOrNull { it.isFile }
            assertNotNull("$path not found", file)
            return file!!
        }

        private const val SETTINGS_SCREEN =
            "src/main/java/com/bringyour/network/ui/settings/SettingsScreen.kt"
    }

    @Test
    fun aFlavorThatCanAskRequestsTheExemptionWhileOptimized() {
        assertEquals(
            BatteryOptimizationAction.RequestExemption,
            batteryOptimizationAction(
                canRequestExemption = true,
                isIgnoringBatteryOptimizations = false,
                optimizationSettingsResolves = true,
            ),
        )
        assertEquals(
            BatteryOptimizationAction.RequestExemption,
            batteryOptimizationAction(
                canRequestExemption = true,
                isIgnoringBatteryOptimizations = false,
                optimizationSettingsResolves = false,
            ),
        )
    }

    @Test
    fun thePlayBuildOpensTheSystemListWhileOptimized() {
        assertEquals(
            BatteryOptimizationAction.OpenOptimizationSettings,
            batteryOptimizationAction(
                canRequestExemption = false,
                isIgnoringBatteryOptimizations = false,
                optimizationSettingsResolves = true,
            ),
        )
    }

    @Test
    fun thePlayBuildOpensTheSystemListWhenExempt() {
        assertEquals(
            BatteryOptimizationAction.OpenOptimizationSettings,
            batteryOptimizationAction(
                canRequestExemption = false,
                isIgnoringBatteryOptimizations = true,
                optimizationSettingsResolves = true,
            ),
        )
    }

    @Test
    fun anExemptAppOpensTheSystemListSoTheUserCanTurnItOff() {
        assertEquals(
            BatteryOptimizationAction.OpenOptimizationSettings,
            batteryOptimizationAction(
                canRequestExemption = true,
                isIgnoringBatteryOptimizations = true,
                optimizationSettingsResolves = true,
            ),
        )
    }

    @Test
    fun appInfoIsTheFallbackWhenTheSystemListDoesNotResolve() {
        for (canRequest in listOf(true, false)) {
            assertEquals(
                BatteryOptimizationAction.OpenAppDetails,
                batteryOptimizationAction(
                    canRequestExemption = canRequest,
                    isIgnoringBatteryOptimizations = true,
                    optimizationSettingsResolves = false,
                ),
            )
        }
        assertEquals(
            BatteryOptimizationAction.OpenAppDetails,
            batteryOptimizationAction(
                canRequestExemption = false,
                isIgnoringBatteryOptimizations = false,
                optimizationSettingsResolves = false,
            ),
        )
    }

    @Test
    fun theRowIsNotGatedOnTheExemptionRequest() {
        val screen = moduleFile(SETTINGS_SCREEN).readText()
        assertFalse(
            "the battery optimization row is still hidden when the flavor cannot request the exemption",
            Regex("""if \(supportsBatteryOptimizationExemption\(\)\) \{\s*/\*\*\s*\*\s*Battery optimization""")
                .containsMatchIn(screen),
        )
        assertFalse(
            "the battery optimization switch is still disabled once exempt",
            screen.contains("enabled = !isIgnored"),
        )
    }
}
