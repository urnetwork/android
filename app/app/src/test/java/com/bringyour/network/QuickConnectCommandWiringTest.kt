package com.bringyour.network

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The connects and disconnects made outside the connect screen run on the
 * connect command queue, off the main thread (owner report 2026-10-16), read
 * from the source, since QuickConnect and MainApplication need a device: the
 * Quick Settings tile, the shortcuts and the widget button (QuickConnect), the
 * notification's disconnect, the balance recovery and Always-on
 * (MainApplication). None opens a controller where it is called. What a queued
 * command does is DeviceConnectCommandTest's.
 */
class QuickConnectCommandWiringTest {

    private fun source(name: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/java/com/bringyour/network/$name") }
            .firstOrNull { it.isFile }
        assertNotNull(name, file)
        return file!!.readText()
    }

    private fun function(source: String, signature: String): String {
        val from = source.indexOf(signature)
        assertTrue("missing $signature", 0 <= from)
        return source.substring(from, source.indexOf("\n    }\n", from))
    }

    @Test
    fun noQuickConnectSurfaceCallsTheSdkWhereItIsTapped() {
        val quickConnect = source("QuickConnect.kt")
        assertFalse("QuickConnect opens a controller where it is tapped", quickConnect.contains("openConnectViewController("))
        assertTrue(function(quickConnect, "    private fun apply(").contains("connectCommands.runOnDevice("))
    }

    @Test
    fun theAppsOwnConnectsAndDisconnectsRunOnTheQueue() {
        val app = source("MainApplication.kt")
        assertFalse("MainApplication opens a controller where it is called", app.contains("openConnectViewController("))
        for (signature in listOf(
            "    fun disconnectVpnConnection(",
            "    fun observeBalanceRecovery(",
            "    private fun ensureAlwaysOnConnected(",
        )) {
            assertTrue(signature, function(app, signature).contains("connectCommands.runOnDevice("))
        }
    }
}
