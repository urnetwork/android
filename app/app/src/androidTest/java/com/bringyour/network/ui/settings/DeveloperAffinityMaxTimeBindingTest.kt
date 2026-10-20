package com.bringyour.network.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bringyour.sdk.ReliabilitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only against the regenerated gomobile AAR; no device or network is created. */
@RunWith(AndroidJUnit4::class)
class DeveloperAffinityMaxTimeBindingTest {
    @Test
    fun regeneratedBindingRoundTripsSignedLongMillisecondsWithoutABooleanAlias() {
        val settings = ReliabilitySettings()
        assertEquals(0L, settings.affinityMaxTimeMillis)
        val setter = ReliabilitySettings::class.java.getMethod("setAffinityMaxTimeMillis", java.lang.Long.TYPE)
        val getter = ReliabilitySettings::class.java.getMethod("getAffinityMaxTimeMillis")
        assertEquals(java.lang.Long.TYPE, getter.returnType)
        assertEquals(java.lang.Void.TYPE, setter.returnType)
        for (value in listOf(Long.MIN_VALUE, -9_223_372_036_854L, -1L, 0L, 1L, 60_000L, 9_223_372_036_854L, Long.MAX_VALUE)) {
            settings.affinityMaxTimeMillis = value
            assertEquals(value, settings.affinityMaxTimeMillis)
        }
        assertFalse(ReliabilitySettings::class.java.methods.any {
            it.name == "getFreshFlowAffinity" || it.name == "setFreshFlowAffinity"
        })
        // A newly allocated settings value is not an effective Connect config.
        // The SDK runtime tests separately prove its getter supplies the 60000 ms default.
        assertEquals(0L, ReliabilitySettings().affinityMaxTimeMillis)
    }
}
