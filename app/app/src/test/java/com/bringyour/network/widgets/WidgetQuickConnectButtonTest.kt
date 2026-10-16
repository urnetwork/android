package com.bringyour.network.widgets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard widget's quick connect button shows on only for a signed-in
 * device (review of the connect tap follow-ups, 2026-10-16). Signed out, it
 * opens sign-in, so a quick connect start still reported as waiting must not
 * tint it or label it Disconnect. The button's composition needs a device;
 * its use of the entry is read from the source.
 */
class WidgetQuickConnectButtonTest {

    private fun entry(isConfigured: Boolean, isOn: Boolean, isConnecting: Boolean) = WidgetEntry(
        nowMillis = 1_000_000L,
        tunnel = WidgetTunnelSnapshot.inactive(1_000_000L),
        balance = null,
        isOn = isOn,
        isConfigured = isConfigured,
        isConnecting = isConnecting,
    )

    @Test
    fun theButtonShowsOnOnlyForASignedInDevice() {
        for (isOn in listOf(false, true)) {
            for (isConnecting in listOf(false, true)) {
                assertFalse(
                    "signed out, isOn=$isOn isConnecting=$isConnecting",
                    entry(isConfigured = false, isOn = isOn, isConnecting = isConnecting).quickConnectOn,
                )
                assertEquals(
                    "signed in, isOn=$isOn isConnecting=$isConnecting",
                    isOn || isConnecting,
                    entry(isConfigured = true, isOn = isOn, isConnecting = isConnecting).quickConnectOn,
                )
            }
        }
    }

    @Test
    fun theButtonReadsWhetherItShowsOn() {
        val path = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/java/com/bringyour/network/widgets/DashboardWidget.kt") }
            .firstOrNull { it.isFile }
        assertNotNull(path)
        val source = path!!.readText()
        val from = source.indexOf("private fun QuickConnectButton(entry: WidgetEntry) {")
        assertTrue(0 <= from)
        val button = source.substring(from, source.indexOf("\n}\n", from))
        assertTrue(button.contains(".background(if (entry.quickConnectOn) WidgetTheme.tint else WidgetTheme.card)"))
        assertTrue(button.contains("if (entry.quickConnectOn) R.string.disconnect else R.string.connect"))
    }
}
