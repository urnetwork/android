package com.bringyour.network.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Support inbox 7932: the Google Play build must stay within Play policy
 * (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS stays removed, the direct exemption
 * dialog is never fired) while still giving users a way to the setting: the
 * system battery optimization list, which needs no permission.
 */
class PlayBatteryOptimizationTest {
    private fun moduleFile(path: String): File {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + path) }
            .firstOrNull { it.isFile }
        assertNotNull("$path not found", file)
        return file!!
    }

    @Test
    fun playDoesNotRequestTheExemptionDirectly() {
        assertFalse(supportsBatteryOptimizationExemption())
    }

    @Test
    fun playKeepsTheRestrictedPermissionRemoved() {
        val manifest = moduleFile("src/play/AndroidManifest.xml").readText()
        assertTrue(
            Regex(
                "android:name=\"android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS\"\\s*tools:node=\"remove\""
            ).containsMatchIn(manifest)
        )
    }

    @Test
    fun playOpensTheSystemBatteryOptimizationList() {
        for (exempt in listOf(false, true)) {
            assertEquals(
                BatteryOptimizationAction.OpenOptimizationSettings,
                batteryOptimizationAction(
                    canRequestExemption = supportsBatteryOptimizationExemption(),
                    isIgnoringBatteryOptimizations = exempt,
                    optimizationSettingsResolves = true,
                ),
            )
        }
    }
}
