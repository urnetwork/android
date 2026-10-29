package com.bringyour.network

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Quick Settings tile, the dashboard widget and the VPN notification say
 * what QuickConnect.status and vpnNotificationPresentation decide
 * (QuickConnectStatusTest, VpnNotificationPolicyTest), and follow the window
 * and the tunnel that decide it (owner report 2026-10-16). Read from the
 * source, since each needs a device and the system.
 */
class QuickConnectStatusWiringTest {

    private fun source(name: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/java/com/bringyour/network/$name") }
            .firstOrNull { it.isFile }
        assertNotNull(name, file)
        return file!!.readText()
    }

    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        assertTrue("missing $start", 0 <= from)
        val to = source.indexOf(end, from)
        assertTrue("missing after $start: $end", 0 <= to)
        return source.substring(from, to)
    }

    @Test
    fun theTileSaysTheStatusAndFollowsTheWindowAndTheTunnel() {
        val tile = source("QuickConnectTileService.kt")
        val render = between(tile, "    private fun render() {", "\n    }\n")
        assertTrue(render, render.contains("quickConnectStatusText(status)"))
        assertTrue(render, render.contains("val status = QuickConnect.status(app)"))
        assertFalse(render, render.contains("R.string.tile_status_connected"))
        val listening = between(tile, "    override fun onStartListening() {", "\n    }\n")
        assertTrue(listening, listening.contains("addWindowStatusChangeListener"))
        assertTrue(listening, listening.contains("addTunnelChangeListener"))
    }

    @Test
    fun theDashboardSaysTheStatusAndFollowsTheWindowAndTheTunnel() {
        val title = between(source("widgets/DashboardWidget.kt"), "internal fun locationTitle(", "\n}\n")
        assertTrue(title, title.contains("entry.status != com.bringyour.network.ui.connect.QuickConnectStatus.CONNECTED"))
        assertFalse(title, title.contains("entry.isOn"))
        assertTrue(source("widgets/WidgetEntry.kt").contains("status = app?.let { QuickConnect.status(it) }"))
        val attach = between(source("widgets/WidgetSnapshotWriter.kt"), "    private fun attach(device: DeviceLocal?) {", "\n    }\n")
        assertTrue(attach, attach.contains("addWindowStatusChangeListener"))
        assertTrue(attach, attach.contains("addTunnelChangeListener"))
    }

    @Test
    fun theNotificationSaysTheWindowsAndTheTunnelsStatus() {
        val service = source("MainService.kt")
        val build = between(service, "    private fun buildForegroundNotification(", "\n    }\n")
        assertTrue(build, build.contains("minSatisfied = windowMinSatisfied"))
        assertTrue(build, build.contains("failed = windowFailed"))
        assertTrue(build, build.contains("tunnelStarted = currentDevice?.tunnelStarted == true"))
        // the words for each status are vpnNotificationText's (VpnNotificationTextTest)
        assertTrue(build, build.contains("vpnNotificationText(presentation, notificationStrings)"))
        val words = between(source("VpnNotificationText.kt"), "internal fun vpnNotificationText(", "\n}\n")
        assertTrue(words, words.contains("VpnNotificationStatus.CONNECT_FAILED ->"))
        assertTrue(words, words.contains("VpnNotificationStatus.TUNNEL_DISCONNECTED ->"))
        val attach = between(service, "    private fun attachToCurrentDevice() {", "\n    }\n")
        assertTrue(attach, attach.contains("tunnelChangeSub = currentDevice.addTunnelChangeListener"))
    }

    @Test
    fun theAppKeepsTheActiveTileSayingTheStatus() {
        // an active tile is bound only when the app asks, and stops listening
        // once it answers (QuickConnectTileRefresh): for the life of the
        // process the app follows the device, its window and its tunnel
        val tile = source("QuickConnectTileService.kt")
        val follow = between(tile, "        fun follow(app: MainApplication) {", "\n        }\n")
        assertTrue(follow, follow.contains("app.deviceManager.addDeviceChangeListener"))
        assertTrue(follow, follow.contains("addWindowStatusChangeListener { refresh.changed() }"))
        assertTrue(follow, follow.contains("addTunnelChangeListener { refresh.changed() }"))
        assertTrue(follow, follow.contains("refresh.deviceChanged()"))
        assertTrue(follow, follow.contains("rendered = { renderedStatus }"))
        val render = between(tile, "    private fun render() {", "\n    }\n")
        assertTrue(render, render.contains("renderedStatus = status"))
        val initialize = between(source("MainApplication.kt"), "    private fun initializeApplicationState() {", "\n    }\n")
        assertTrue(initialize, initialize.contains("QuickConnectTileService.follow(this)"))
    }

    @Test
    fun aLockedTapTogglesTheTileButTheCoverWidgetUnlocksFirst() {
        // the tile toggles from the lock screen without unlocking (product
        // decision, 2026-09-01); the overview widget's toggle, on the lock
        // screen and Samsung's cover screen, toggles only after the user
        // unlocks, and a cancelled unlock toggles nothing
        val click = between(source("QuickConnectTileService.kt"), "    override fun onClick() {", "\n    }\n")
        assertFalse(click, click.contains("unlockAndRun"))
        assertTrue(click, click.contains("toggle()"))
        assertFalse(click, click.contains("QuickConnect.toggle("))
        val trampoline = source("QuickConnectActivity.kt")
        val unlock = between(trampoline, "keyguard.requestDismissKeyguard(", "override fun onDismissError()")
        val succeeded = unlock.substringAfter("override fun onDismissSucceeded()").substringBefore("override fun onDismissCancelled()")
        assertTrue(succeeded, succeeded.contains("QuickConnect.toggle("))
        assertFalse(unlock, unlock.substringAfter("override fun onDismissCancelled()").contains("QuickConnect.toggle("))
    }
}
