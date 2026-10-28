package com.bringyour.network.widgets

import com.bringyour.network.SlowInternetNoticePhase
import com.bringyour.network.SlowInternetPresentation
import com.bringyour.network.TransportDelayUi
import com.bringyour.network.ui.connect.QuickConnectStatus
import com.bringyour.network.ui.connect.SlowInternetPillContent
import com.bringyour.network.vpnNotificationPresentation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard widget's slow internet pill (apple/DELAY.md §5.7, §7.4): it
 * shows while the notification shows its slow content, with the location it
 * describes; the tunnel at the notification's level, the in-app pill's copy
 * and its choice of what fits, without words in the compact size; one
 * description for the header; the short size's balance label row giving way
 * where the pill leaves no room; and a re-render only when the pill comes
 * or goes, or its tunnel or label changes. The Glance composition needs a
 * device; its wiring is read from the source.
 */
class DashboardSlowInternetPillTest {

    private val slow = TransportDelayUi(
        upQuality = 0.25,
        downQuality = 0.75,
        upBitsPerSecond = 48_000,
        downBitsPerSecond = 85_000,
        upQueueDelayMillis = 800,
        downQueueDelayMillis = 1_900,
    )

    private val tunnel = WidgetTunnelSnapshot(
        updatedAtMillis = 1_000_000L,
        tunnelActive = true,
        providing = false,
        location = WidgetLocationSnapshot("Germany", "de", "", "", "Germany", false, false, 12, "663F46"),
    )

    /** A signed-in entry, connected with the tunnel up, the notification showing [slowInternet]; [status] overrides the title's. */
    private fun entry(
        slowInternet: TransportDelayUi? = slow,
        isConfigured: Boolean = true,
        isOn: Boolean = true,
        isConnecting: Boolean = false,
        tunnel: WidgetTunnelSnapshot = this.tunnel,
        status: QuickConnectStatus? = null,
    ): WidgetEntry = if (status == null) {
        WidgetEntry(1_000_000L, tunnel, null, isOn, isConfigured, isConnecting, slowInternet = slowInternet)
    } else {
        WidgetEntry(1_000_000L, tunnel, null, isOn, isConfigured, isConnecting, status, slowInternet)
    }

    /** Each character half its size wide: enough to tell the parts apart. */
    private val measure = { text: String, sizeSp: Float -> text.length * sizeSp / 2f }

    @Test
    fun thePillShowsWithTheSlowContent() {
        assertEquals(DashboardSlowInternet(level = 3, content = SlowInternetPillContent(slow)), dashboardSlowInternet(entry()))
        // none: the provider count
        assertNull(dashboardSlowInternet(entry(slowInternet = null)))
    }

    @Test
    fun thePillGoesWithTheTunnelDataAndTheConnectedTitle() {
        // signed out, disconnected, the tunnel down: no tunnel data
        assertNull(dashboardSlowInternet(entry(isConfigured = false, isOn = false)))
        assertNull(dashboardSlowInternet(entry(isOn = false)))
        assertNull(dashboardSlowInternet(entry(tunnel = tunnel.copy(tunnelActive = false))))
        // a title that says anything but connected, as the notification keeps
        // its slow content for a connected status
        assertNull(dashboardSlowInternet(entry(isConnecting = true)))
        for (status in QuickConnectStatus.entries.filter { it != QuickConnectStatus.CONNECTED }) {
            assertNull(status.name, dashboardSlowInternet(entry(status = status)))
        }
    }

    @Test
    fun theTunnelIsAtTheNotificationsLevel() {
        for (quality in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            val reading = TransportDelayUi(downQuality = quality, downQueueDelayMillis = 640)
            val notification = vpnNotificationPresentation(
                connectRequested = true,
                systemAlwaysOn = false,
                providerCount = 3,
                minSatisfied = true,
                failed = false,
                locationProviderCount = 0,
                tunnelStarted = true,
                locationName = "Germany",
                city = null,
                region = null,
                country = null,
                bestAvailableLabel = "Best available provider",
                slowInternetPhase = SlowInternetNoticePhase.SLOW,
                transportDelay = reading,
            ).slowInternet as SlowInternetPresentation.Slow
            assertEquals("quality $quality", notification.level, dashboardSlowInternet(entry(slowInternet = reading))!!.level)
        }
    }

    @Test
    fun theCopyIsTheInAppPills() {
        val content = dashboardSlowInternet(entry())!!.content
        assertEquals(SlowInternetPillContent.Label.POOR_SIGNAL, content.label)
        assertEquals("800 ms", content.up)
        assertEquals("1.9 s", content.down)
        assertEquals(SlowInternetPillContent.Direction.DOWN, content.worse)
        assertEquals(
            SlowInternetPillContent.Label.FALLBACK,
            dashboardSlowInternet(entry(slowInternet = slow.copy(fallback = true)))!!.content.label,
        )
        // no delay shows the limited rate; neither hides the direction
        val ratesOnly = dashboardSlowInternet(entry(slowInternet = TransportDelayUi(downQuality = 0.5, upBitsPerSecond = 150_000)))!!.content
        assertEquals("150 kb/s", ratesOnly.up)
        assertNull(ratesOnly.down)
    }

    @Test
    fun aTightRowDropsTheBetterDirectionThenTheNumbers() {
        val pill = dashboardSlowInternet(entry())!!
        val content = pill.content
        val full = slowInternetPillWidth(content, "Poor signal", compact = false, measure)
        val worse = slowInternetPillWidth(content.worseOnly, "Poor signal", compact = false, measure)
        val alone = slowInternetPillWidth(content.labelOnly, "Poor signal", compact = false, measure)
        fun fittedIn(roomDp: Float) = pill.fitted(compact = false) {
            slowInternetPillWidth(it, "Poor signal", compact = false, measure) <= roomDp
        }

        assertEquals(content, fittedIn(full))
        // the download's 1.9 s is the worse: the upload goes first
        assertEquals(SlowInternetPillContent(slow).copy(up = null), fittedIn(full - 0.5f))
        assertEquals(content.labelOnly, fittedIn(worse - 0.5f))
        // the label alone is the last choice, whatever the room
        assertEquals(content.labelOnly, fittedIn(alone - 0.5f))
    }

    @Test
    fun eachPartThatShowsTakesItsWidth() {
        val content = SlowInternetPillContent(slow)
        val full = slowInternetPillWidth(content, "Poor signal", compact = false, measure)
        val worse = slowInternetPillWidth(content.worseOnly, "Poor signal", compact = false, measure)
        val alone = slowInternetPillWidth(content.labelOnly, "Poor signal", compact = false, measure)
        // each direction adds its gap, arrow and number: the two differ by
        // their numbers, "800 ms" and "1.9 s", one character at 11 sp
        assertEquals(5.5f, (full - worse) - (worse - alone), 0.001f)
        // a longer label, a translation, takes its width
        val longer = slowInternetPillWidth(content, "Schwaches Signal", compact = false, measure)
        assertEquals(measure("Schwaches Signal", 11f) - measure("Poor signal", 11f), longer - full, 0.001f)
    }

    @Test
    fun theCompactPillHasNoWords() {
        val pill = dashboardSlowInternet(entry())!!
        val content = pill.content
        // the worse number, then the tunnel alone
        assertEquals(content.worseOnly, pill.fitted(compact = true) { true })
        assertEquals(content.labelOnly, pill.fitted(compact = true) { it.down == null })
        assertEquals(content.labelOnly, pill.fitted(compact = true) { false })
        // and is measured without the label
        val words = slowInternetPillWidth(content.worseOnly, "Poor signal", compact = false, measure)
        val compact = slowInternetPillWidth(content.worseOnly, "Poor signal", compact = true, measure)
        assertTrue(compact <= words - measure("Poor signal", 11f))
        assertEquals(compact, slowInternetPillWidth(content.worseOnly, "Schwaches Signal", compact = true, measure), 0.001f)
    }

    @Test
    fun theShortSizeDropsTheBalanceLabelRowWhereThePillLeavesNoRoom() {
        // lines at the default font scale: 14 and 11 sp, a third taller with the font's padding
        val title = 14f * 4f / 3f
        val line = 11f * 4f / 3f
        // the header with the pill, the label row and the bar take 80 dp: a
        // 108 dp widget inside its 14 dp padding
        assertEquals(80f, dashboardShortHeightDp(title, line, balanceLabel = true), 0.01f)
        assertTrue(dashboardShortKeepsBalanceLabel(roomDp = 108.5f - 28f, titleDp = title, lineDp = line))
        assertFalse(dashboardShortKeepsBalanceLabel(roomDp = 107.5f - 28f, titleDp = title, lineDp = line))
        // the shortest short widget, 100 dp, drops the row and the bar fits:
        // the short size has no footer to go first
        assertFalse(dashboardShortKeepsBalanceLabel(roomDp = 100f - 28f, titleDp = title, lineDp = line))
        assertTrue(dashboardShortHeightDp(title, line, balanceLabel = false) <= 100f - 28f)
        // larger text needs more room
        assertFalse(dashboardShortKeepsBalanceLabel(roomDp = 108.5f - 28f, titleDp = title * 1.3f, lineDp = line * 1.3f))
        // a short header is still the quick connect button's 36 dp
        assertEquals(36f + 10f + 8f, dashboardShortHeightDp(titleDp = 10f, lineDp = 5f, balanceLabel = false), 0.01f)
    }

    @Test
    fun theHeaderReadsAsOneElement() {
        val content = SlowInternetPillContent(slow)
        fun read(content: SlowInternetPillContent, label: String = "Poor signal") =
            dashboardSlowInternetDescription("Germany", label, content, "Upload delay", "Download delay")

        assertEquals("Germany. Poor signal. Upload delay 800 ms, Download delay 1.9 s", read(content))
        assertEquals("Germany. Fallback. Download delay 1.9 s", read(content.copy(up = null), label = "Fallback"))
        assertEquals("Germany. Poor signal", read(content.labelOnly))
    }

    @Test
    fun onlyThePillComingOrGoingOrItsTunnelOrLabelReRendersIt() {
        assertFalse(dashboardSlowInternetChanged(null, null))
        assertTrue(dashboardSlowInternetChanged(null, slow))
        assertTrue(dashboardSlowInternetChanged(slow, null))
        assertFalse(dashboardSlowInternetChanged(slow, slow))
        // new numbers wait for the next re-render, and so does the better
        // direction's level: the tunnel follows the worse
        assertFalse(dashboardSlowInternetChanged(slow, slow.copy(upQueueDelayMillis = 900, downQueueDelayMillis = 2_400, downBitsPerSecond = 60_000)))
        assertFalse(dashboardSlowInternetChanged(slow, slow.copy(upQuality = 0.5)))
        // the tunnel's level, or the label
        assertTrue(dashboardSlowInternetChanged(slow, slow.copy(downQuality = 1.0)))
        assertTrue(dashboardSlowInternetChanged(slow, slow.copy(downQuality = 0.5)))
        assertTrue(dashboardSlowInternetChanged(slow, slow.copy(fallback = true)))
    }

    @Test
    fun theServicePublishesTheSlowContentForTheHeader() {
        fun source(path: String): String {
            val file = listOf("", "app/", "app/app/")
                .map { File(it + "src/main/java/com/bringyour/network/$path") }
                .firstOrNull { it.isFile }
            assertNotNull(path, file)
            return file!!.readText()
        }
        fun function(source: String, signature: String): String {
            val from = source.indexOf(signature)
            assertTrue(signature, 0 <= from)
            return source.substring(from, source.indexOf("\n    }\n", from))
        }

        // the dwell's slow content on each observation, none once the device goes
        val service = source("MainService.kt")
        val update = function(service, "    private fun updateSlowInternetNotice(notify: Boolean = true) {")
        assertTrue(update, update.contains("widgetSnapshotWriter?.publishSlowInternet(next.slowReading)"))
        val detach = function(service, "    private fun detachDeviceBindings(closePacketFlow: Boolean) {")
        assertTrue(detach, detach.contains("widgetSnapshotWriter?.publishSlowInternet(null)"))

        // the writer keeps it for the entry, and re-renders on the changes that show
        val publish = function(source("widgets/WidgetSnapshotWriter.kt"), "    fun publishSlowInternet(reading: TransportDelayUi?) {")
        assertTrue(publish, publish.contains("WidgetSnapshotStore.slowInternet = reading"))
        assertTrue(publish, publish.contains("if (dashboardSlowInternetChanged(previous, reading)) refresh.reloadDashboard()"))
        assertTrue(source("widgets/WidgetEntry.kt").contains("slowInternet = WidgetSnapshotStore.slowInternet,"))
        assertTrue(function(source("widgets/WidgetSnapshot.kt"), "    fun clear(context: Context) {").contains("slowInternet = null"))

        // the header draws the pill in the provider count's place
        val widget = source("widgets/DashboardWidget.kt")
        assertTrue(widget.contains("val slowInternet = dashboardSlowInternet(entry)"))
        val headerFrom = widget.indexOf("internal fun DashboardHeader(")
        assertTrue("no DashboardHeader", 0 <= headerFrom)
        val header = widget.substring(headerFrom, widget.indexOf("\n}\n", headerFrom))
        val pill = header.indexOf("DashboardSlowInternetPill(")
        val subtitle = header.indexOf("} else if (!compact) {\n                Text(subtitle(context, entry)")
        assertTrue(header, 0 <= pill && pill < subtitle)
        assertTrue(header, header.contains("column = column.semantics { contentDescription = description }"))

        // the short size drops the balance's label row only while the pill shows
        assertTrue(widget.contains("val balanceLabel = slowInternet == null || dashboardShortKeepsBalanceLabel("))
        assertTrue(widget.contains("BalanceBar(entry.balance, label = balanceLabel)"))

        // Glance drops a container's children past ten: each direction's
        // arrow and number are a row of their own, or both directions with
        // the tunnel and the label would be eleven and lose the download
        val pillSource = source("widgets/DashboardSlowInternetPill.kt")
        // the composable's loop, after the width's
        val directions = pillSource.substring(pillSource.lastIndexOf("for ((direction, number) in content.numbers()) {"))
        assertTrue(directions, directions.substringBefore("Text(").contains("Row(verticalAlignment = Alignment.CenterVertically) {"))
    }
}
