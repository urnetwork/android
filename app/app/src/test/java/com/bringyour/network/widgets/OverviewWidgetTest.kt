package com.bringyour.network.widgets

import com.bringyour.network.widgets.render.ContractStackRenderer
import com.bringyour.network.widgets.render.SparklineRenderer
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overview widget (apple/DUO.md, "Samsung cover screen (item 16)",
 * option A): its layout at the cover's size and larger; what its globe and
 * tiles show connected, disconnected and signed out; today's traffic by
 * local hour; the contract circles that fit a tile; and its declaration, a
 * home screen receiver and a keyguard receiver with Samsung's sub_screen
 * meta-data, refreshed with any of the three widgets, its toggle asking
 * for an unlock first. The Glance composition and the activity need a
 * device; their wiring is read from the sources.
 */
class OverviewWidgetTest {

    private val utc = ZoneId.of("UTC")
    private val mib = 1024L * 1024L
    private val gib = 1024L * mib
    private val now = ZonedDateTime.of(2026, 10, 28, 15, 30, 0, 0, utc).toInstant().toEpochMilli()

    private val providers = listOf(
        WidgetProviderSnapshot("p1", "Germany", "de", "Berlin", "Berlin", 52.52, 13.40, now - 60_000, "663F46"),
        WidgetProviderSnapshot("p2", "Japan", "jp", "Tokyo", "Tokyo", 35.68, 139.69, now - 120_000, "F94144"),
    )

    private fun contract(id: String) = WidgetContractSnapshot(id, usedByteCount = 1, totalByteCount = 2, bitRate = 0, hasStream = false)

    private val peers = listOf(
        WidgetContractPeerSnapshot("a", listOf(contract("s1"), contract("s2")), listOf(contract("r1")), 3 * mib, 1 * mib, now),
        WidgetContractPeerSnapshot("b", listOf(contract("s3")), emptyList(), 2 * mib, 0, now),
    )

    /** 10 MiB in the day's first hour and 32 MiB in its third. */
    private val today = WidgetTodaySnapshot(localDayStartMillis(now, utc), listOf(10 * mib, 0, 32 * mib))

    private val balance = WidgetBalanceSnapshot(
        updatedAtMillis = now,
        startBalanceByteCount = 5 * gib,
        balanceByteCount = 4 * gib,
        openTransferByteCount = 0,
        isPro = false,
    )

    /** Signed in and connected with the tunnel up, unless a test says otherwise. */
    private fun entry(
        isConfigured: Boolean = true,
        isOn: Boolean = true,
        balance: WidgetBalanceSnapshot? = this.balance,
        today: WidgetTodaySnapshot = this.today,
    ) = WidgetEntry(
        nowMillis = now,
        tunnel = WidgetTunnelSnapshot(
            updatedAtMillis = now,
            tunnelActive = true,
            providing = false,
            providers = providers,
            contracts = peers,
            today = today,
        ),
        balance = balance,
        isOn = isOn,
        isConfigured = isConfigured,
    )

    @Test
    fun theLayoutIsTheMockupsAtTheCoversSize() {
        val layout = overviewLayout(352f, 339f)
        // the tiles a third of the width, the globe four fifths of the
        // shorter side, a fifth past the left edge and a tenth past the bottom
        assertEquals(120f, layout.tileWidthDp, 0.01f)
        assertEquals(271.2f, layout.globeDp, 0.01f)
        assertEquals(-54.24f, layout.globeLeftDp, 0.01f)
        assertEquals(94.92f, layout.globeTopDp, 0.01f)
    }

    @Test
    fun theLayoutGrowsWithTheWidget() {
        val tablet = overviewLayout(600f, 600f)
        assertEquals(200f, tablet.tileWidthDp, 0.01f)
        assertEquals(480f, tablet.globeDp, 0.01f)
        // whatever the size, the globe bleeds off the bottom left corner,
        // the one the Flip's cameras cover
        for ((width, height) in listOf(352f to 339f, 600f to 600f, 720f to 339f, 352f to 720f)) {
            val layout = overviewLayout(width, height)
            assertTrue("$width x $height", layout.globeLeftDp < 0f)
            assertTrue("$width x $height", height < layout.globeTopDp + layout.globeDp)
        }
    }

    @Test
    fun connectedTheGlobeAndTheTilesShowTheTunnel() {
        val overview = overview(entry(), utc)
        assertFalse(overview.dimmed)
        assertEquals(providers, overview.providers)
        assertEquals("4.00 GiB", overview.balance)
        assertEquals("42.0 MiB", overview.traffic)
        assertEquals(listOf(10 * mib, 0L, 32 * mib), overview.trafficHours)
        // every peer's contracts in one send stack and one receive stack, most relevant peer first
        assertEquals("4", overview.contracts)
        assertEquals(listOf("s1", "s2", "s3"), overview.send?.map { it.id })
        assertEquals(listOf("r1"), overview.receive?.map { it.id })
        assertEquals(5 * mib, overview.sendByteCount)
        assertEquals(1 * mib, overview.receiveByteCount)
    }

    @Test
    fun disconnectedTheGlobeDimsAndTrafficAndContractsReadADash() {
        for (overview in listOf(overview(entry(isOn = false), utc), overview(entry(isConfigured = false, isOn = false, balance = null), utc))) {
            assertTrue(overview.dimmed)
            assertTrue(overview.providers.isEmpty())
            assertEquals("—", overview.traffic)
            assertNull(overview.trafficHours)
            assertEquals("—", overview.contracts)
            assertNull(overview.send)
            assertNull(overview.receive)
        }
        // the balance stays; signed out there is none
        assertEquals("4.00 GiB", overview(entry(isOn = false), utc).balance)
        assertEquals("—", overview(entry(isConfigured = false, isOn = false, balance = null), utc).balance)
    }

    @Test
    fun anEarlierDaysTrafficIsNoneToday() {
        val yesterday = WidgetTodaySnapshot(localDayStartMillis(now - 86_400_000L, utc), listOf(5 * mib))
        val overview = overview(entry(today = yesterday), utc)
        assertEquals("0 B", overview.traffic)
        assertEquals(emptyList<Long>(), overview.trafficHours)
    }

    @Test
    fun todayCountsThisDevicesTrafficByLocalHour() {
        val zone = ZoneId.of("Europe/Berlin")
        val accumulator = WidgetThroughputAccumulator(zone = { zone })
        val nine = ZonedDateTime.of(2026, 10, 28, 9, 15, 0, 0, zone).toInstant().toEpochMilli()
        // the first reading is a baseline
        accumulator.recordClient(egress = 100, ingress = 1_000, nowMillis = nine)
        accumulator.recordClient(egress = 300, ingress = 5_000, nowMillis = nine + 60_000L)
        accumulator.recordClient(egress = 400, ingress = 9_000, nowMillis = nine + 3_600_000L)
        // traffic relayed while providing is not this device's
        accumulator.recordProvider(egress = 1, ingress = 1, nowMillis = nine)
        accumulator.recordProvider(egress = 1_000_000, ingress = 1_000_000, nowMillis = nine + 3_700_000L)

        val today = accumulator.today
        assertEquals(localDayStartMillis(nine, zone), today.dayStartMillis)
        assertEquals(List(9) { 0L } + listOf(4_200L, 4_100L), today.hourlyByteCounts)
        assertEquals(8_300L, today.byteCount)
    }

    @Test
    fun aNewDayStartsOverAndAResumeKeepsTheDay() {
        val zone = ZoneId.of("Europe/Berlin")
        val lateEvening = ZonedDateTime.of(2026, 10, 28, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val accumulator = WidgetThroughputAccumulator(zone = { zone })
        accumulator.recordClient(egress = 0, ingress = 0, nowMillis = lateEvening)
        accumulator.recordClient(egress = 0, ingress = 700, nowMillis = lateEvening + 60_000L)
        assertEquals(700L, accumulator.today.hourlyByteCounts[23])

        // a restart resumes the day from the snapshot
        val resumed = WidgetThroughputAccumulator(accumulator.snapshot, accumulator.today, zone = { zone })
        resumed.recordClient(egress = 0, ingress = 1_000, nowMillis = lateEvening + 120_000L)
        resumed.recordClient(egress = 0, ingress = 1_300, nowMillis = lateEvening + 180_000L)
        assertEquals(1_000L, resumed.today.byteCount)

        // past midnight it starts over
        val afterMidnight = lateEvening + 40 * 60_000L
        resumed.recordClient(egress = 0, ingress = 1_350, nowMillis = afterMidnight)
        assertEquals(localDayStartMillis(afterMidnight, zone), resumed.today.dayStartMillis)
        assertEquals(listOf(50L), resumed.today.hourlyByteCounts)
        // and yesterday's reads as nothing today
        assertEquals(0L, accumulator.today.asOf(afterMidnight, zone).byteCount)
    }

    @Test
    fun theContractCirclesThatFitATile() {
        val style = ContractStackRenderer.Style(density = 1f, compact = true)
        val capacity = stackCapacity(102, style)
        assertEquals(5, capacity)
        assertTrue(ContractStackRenderer.width(capacity, style) <= 102)
        assertTrue(102 < ContractStackRenderer.width(capacity + 1, style))
        // one at least, however narrow
        assertEquals(1, stackCapacity(1, style))
    }

    @Test
    fun theSparklineScalesEachHourToTheBusiest() {
        val fractions = SparklineRenderer.fractions(listOf(0L, 50L, 100L, 1L), slots = 6)
        assertEquals(6, fractions.size)
        assertEquals(0f, fractions[0], 0f)
        assertEquals(0.5f, fractions[1], 0.001f)
        assertEquals(1f, fractions[2], 0.001f)
        // a little traffic still shows, and the hours ahead show none
        assertEquals(SparklineRenderer.MINIMUM_FRACTION, fractions[3], 0.001f)
        assertEquals(0f, fractions[4], 0f)
        assertEquals(0f, fractions[5], 0f)
        assertTrue(SparklineRenderer.fractions(emptyList(), slots = 24).all { it == 0f })
    }

    @Test
    fun theOverviewIsOfferedOnTheHomeScreenAndOnTheCoverScreen() {
        val manifest = moduleFile("src/main/AndroidManifest.xml").readText()
        val home = receiver(manifest, ".widgets.OverviewWidgetReceiver")
        assertTrue(home, home.contains("android:resource=\"@xml/widget_overview_info\""))
        assertFalse(home, home.contains("com.samsung.android.appwidget.provider"))
        // the cover's receiver: Samsung's meta-data beside Android's
        val cover = receiver(manifest, ".widgets.CoverWidgetReceiver")
        assertTrue(cover, cover.contains("android:name=\"android.appwidget.provider\""))
        assertTrue(cover, cover.contains("android:resource=\"@xml/widget_cover_info\""))
        assertTrue(cover, cover.contains("android:name=\"com.samsung.android.appwidget.provider\""))
        assertTrue(cover, cover.contains("android:resource=\"@xml/widget_cover_samsung\""))

        val homeInfo = moduleFile("src/main/res/xml/widget_overview_info.xml").readText()
        for (attribute in listOf(
            "android:minWidth=\"352dp\"",
            "android:minHeight=\"339dp\"",
            "android:targetCellWidth=\"4\"",
            "android:targetCellHeight=\"4\"",
            "android:resizeMode=\"horizontal|vertical\"",
            "android:widgetCategory=\"home_screen\"",
        )) {
            assertTrue(attribute, homeInfo.contains(attribute))
        }
        // the four attributes Samsung's guide sets for the Flex Window
        val coverInfo = moduleFile("src/main/res/xml/widget_cover_info.xml").readText()
        for (attribute in listOf(
            "android:minWidth=\"352dp\"",
            "android:minHeight=\"339dp\"",
            "android:resizeMode=\"horizontal|vertical\"",
            "android:widgetCategory=\"keyguard\"",
        )) {
            assertTrue(attribute, coverInfo.contains(attribute))
        }
        val samsung = moduleFile("src/main/res/xml/widget_cover_samsung.xml").readText()
        assertTrue(samsung, samsung.contains("<samsung-appwidget-provider"))
        assertTrue(samsung, samsung.contains("display=\"sub_screen\""))

        // both receivers show the one widget
        val receivers = source("widgets/GlanceWidgetRefresh.kt")
        for (name in listOf("class OverviewWidgetReceiver", "class CoverWidgetReceiver")) {
            val body = between(receivers, name, "\n}\n")
            assertTrue(body, body.contains("override val glanceAppWidget: GlanceAppWidget = OverviewWidget()"))
            assertTrue(body, body.contains("widgetsChanged(context)"))
        }
    }

    @Test
    fun theWriterOpensTheContractsAndTheToggleAsksForAnUnlock() {
        // the contract circles come from the contracts controller
        assertTrue(source("widgets/WidgetSnapshotWriter.kt").contains("refresh.hasWidgets(WidgetKinds.OVERVIEW)"))
        assertTrue(
            source("widgets/OverviewWidget.kt")
                .contains("DashboardHeader(entry, dashboardSlowInternet(entry), compact = false, unlockFirst = true)"),
        )
        // locked, the trampoline shows over the keyguard to ask for the
        // unlock, and toggles only once it succeeds
        val activity = source("QuickConnectActivity.kt")
        val unlock = between(
            activity,
            "if (action == ACTION_UNLOCK_TOGGLE && keyguard?.isKeyguardLocked == true) {",
            "\n            return\n        }",
        )
        assertTrue(unlock, unlock.contains("setShowWhenLocked(true)"))
        assertTrue(unlock, unlock.contains("keyguard.requestDismissKeyguard(this"))
        assertEquals(unlock, 1, Regex("QuickConnect\\.toggle\\(").findAll(unlock).count())
        assertTrue(unlock, between(unlock, "override fun onDismissSucceeded() {", "}").contains("QuickConnect.toggle("))
    }

    // gradle runs unit tests with the module directory as the working
    // directory; the other candidates cover runners that start a level up
    private fun moduleFile(path: String): File {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + path) }
            .firstOrNull { it.isFile }
        assertNotNull("$path not found", file)
        return file!!
    }

    private fun source(path: String): String = moduleFile("src/main/java/com/bringyour/network/$path").readText()

    // the text between two markers, failing when either is missing
    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        assertTrue("missing: $start", 0 <= from)
        val to = source.indexOf(end, from + start.length)
        assertTrue("missing after $start: $end", 0 <= to)
        return source.substring(from, to)
    }

    /** The manifest's receiver element named [name]. */
    private fun receiver(manifest: String, name: String): String {
        val at = manifest.indexOf("android:name=\"$name\"")
        assertTrue("no receiver $name", 0 <= at)
        val from = manifest.lastIndexOf("<receiver", at)
        val to = manifest.indexOf("</receiver>", at)
        assertTrue("no receiver element around $name", 0 <= from && 0 <= to)
        return manifest.substring(from, to)
    }
}
