package com.bringyour.network.widgets

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget pass before API 37 (apple/DUO.md item 15): the density the
 * widgets draw their bitmaps at, within the memory a widget update may
 * carry; the layouts at sizes past the old 624 x 422 dp cap, which the
 * dashboard, globe and contracts widgets no longer declare; and Glance's
 * ten children a container. The Glance compositions need a device; their
 * wiring is read from the sources.
 */
class WidgetScalingTest {

    @Test
    fun aWidgetWithinTheBudgetDrawsAtTheDisplaysDensity() {
        // a 4x2 dashboard on a 1080 x 2400 phone, portrait and landscape
        val sizes = listOf(DpSize(320.dp, 180.dp), DpSize(400.dp, 150.dp))
        assertEquals(2.625f, widgetBitmapDensity(2.625f, sizes, widgetBitmapBudgetBytes(1080, 2400)), 0f)
    }

    @Test
    fun aWidgetPastTheBudgetDrawsEverySizeAtTheDensityThatFits() {
        // a globe resized across a 2560 x 1600 tablet, at four sizes
        val sizes = List(4) { DpSize(1200.dp, 760.dp) }
        val budget = widgetBitmapBudgetBytes(2560, 1600)
        val density = widgetBitmapDensity(2f, sizes, budget)
        assertTrue(density < 2f)
        val bytes = sizes.sumOf { 4.0 * it.width.value * density * it.height.value * density }
        assertTrue("$bytes over $budget", bytes <= budget * 1.0001)
        // and no lower than it has to be
        assertTrue(budget * 0.99 <= bytes)
    }

    @Test
    fun theBudgetIsHalfOfAndroid17sCeiling() {
        // the system's ceiling is 6 bytes a pixel of the display (1.5 ARGB screens)
        assertEquals(6.0 * 1080 * 2400 * 0.5, widgetBitmapBudgetBytes(1080, 2400), 0.0)
    }

    @Test
    fun theDashboardChartsGrowWithATallWidget() {
        // at phone heights up to 310dp they are what they always were
        assertEquals(85f, dashboardChartHeightDp(320f, 300f, 0f), 0f)
        assertEquals(40f, dashboardChartHeightDp(320f, 200f, 0f), 0f)
        // a tablet shares its height between them instead of leaving a gap
        assertEquals(285f, dashboardChartHeightDp(1000f, 700f, 0f), 0f)
        // a tall narrow widget keeps charts, no taller than half their width
        assertEquals(111f, dashboardChartHeightDp(250f, 700f, 0f), 0f)
    }

    @Test
    fun theGlobeLaysOutForEverySize() {
        // 2x2: the globe alone
        assertEquals(GlobeArrangement.GlobeOnly, providerGlobeLayout(150f, 150f).arrangement)
        // 4x2: the list beside a globe as tall as the widget's room
        val phone = providerGlobeLayout(320f, 150f)
        assertEquals(GlobeArrangement.ListBeside, phone.arrangement)
        assertEquals(122f, phone.globeHeightDp, 0f)
        // 4x3: the list below
        assertEquals(GlobeArrangement.ListBelow, providerGlobeLayout(320f, 300f).arrangement)
        // a wide widget on a tablet: the list beside again, with room of its own
        val tablet = providerGlobeLayout(900f, 400f)
        assertEquals(GlobeArrangement.ListBeside, tablet.arrangement)
        assertTrue(200f <= 900f - 28f - 12f - tablet.globeWidthDp)
        // a tall narrow widget: the globe never wider than the widget
        val narrow = providerGlobeLayout(150f, 420f)
        assertEquals(GlobeArrangement.ListBelow, narrow.arrangement)
        assertEquals(122f, narrow.globeWidthDp, 0f)
        // a short widget only just wide enough for the list keeps half for it
        val crowded = providerGlobeLayout(240f, 200f)
        assertEquals(GlobeArrangement.ListBeside, crowded.arrangement)
        assertEquals((240f - 28f - 12f) / 2f, crowded.globeWidthDp, 0f)
    }

    @Test
    fun theProviderListShowsTheRowsThatFit() {
        // all of them when they fit
        assertEquals(3, providerListRows(200f, 1f, 3))
        // else as many as leave room for "+N more"
        assertEquals(7, providerListRows(200f, 1f, 12))
        // a larger font size fits fewer
        assertEquals(4, providerListRows(200f, 1.5f, 12))
        // never fewer than one, never more than Glance keeps
        assertEquals(1, providerListRows(30f, 1f, 5))
        assertEquals(PROVIDER_ROWS_MAX, providerListRows(1000f, 1f, 40))
        assertEquals(0, providerListRows(200f, 1f, 0))
    }

    @Test
    fun theContractsFlowWrapsAtTenCardsAndStopsAtTenRows() {
        // forty 40dp cards: 22 fit a 1000dp row, but Glance keeps ten
        val cards = List(40) { it }
        val rows = packRows(cards, { 40f }, { 30f }, availableWidth = 1000f, availableHeight = 1000f)
        assertTrue(rows.all { it.size <= GLANCE_MAX_CHILDREN })
        assertEquals(4, rows.size)
        assertEquals(cards, rows.flatten())
        // a hundred cards in a tall widget: ten rows at most
        val many = packRows(List(150) { it }, { 40f }, { 30f }, availableWidth = 1000f, availableHeight = 2000f)
        assertEquals(GLANCE_MAX_CHILDREN, many.size)
        // and the bottom edge still stops it first
        val short = packRows(cards, { 40f }, { 30f }, availableWidth = 1000f, availableHeight = 100f)
        assertEquals(2, short.size)
    }

    @Test
    fun theDashboardGlobeAndContractsHaveNoMaximumSize() {
        for (name in listOf("widget_dashboard_info", "widget_globe_info", "widget_contracts_info")) {
            val info = moduleFile("src/main/res/xml/$name.xml").readText()
            assertFalse(name, info.contains("android:maxResizeWidth"))
            assertFalse(name, info.contains("android:maxResizeHeight"))
            assertTrue(name, info.contains("android:resizeMode=\"horizontal|vertical\""))
        }
    }

    @Test
    fun everyWidgetBitmapIsDrawnAtTheWidgetDensityAndSaysSo() {
        val widgets = moduleFile("src/main/java/com/bringyour/network/widgets/WidgetPixels.kt").parentFile
        val sources = widgets.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        for (file in sources) {
            val text = file.readText()
            // the pill measures its text in dp; DisplayMetrics only carries
            // the user's font size there
            if (file.name != "DashboardSlowInternetPill.kt") {
                assertFalse(file.name, text.contains("displayMetrics"))
            }
            // each bitmap carries the density it is drawn at: the call that
            // creates it goes straight on to drawnAt
            for (match in Regex("""Bitmap\.createBitmap\(""").findAll(text)) {
                var end = match.range.last + 1
                var depth = 1
                while (0 < depth) {
                    when (text[end]) {
                        '(' -> depth += 1
                        ')' -> depth -= 1
                    }
                    end += 1
                }
                assertTrue(
                    "${file.name}: ${text.substring(match.range.first, end)}",
                    text.startsWith(".drawnAt(", end),
                )
            }
        }
        for (name in listOf("DashboardWidget.kt", "ProviderGlobeWidget.kt", "ContractsWidget.kt", "OverviewWidget.kt")) {
            assertTrue(name, sources.first { it.name == name }.readText().contains("widgetBitmapDensity()"))
        }
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
}
