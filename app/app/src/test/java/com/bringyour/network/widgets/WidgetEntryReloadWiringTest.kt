package com.bringyour.network.widgets

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each Home Screen widget reads its entry inside its composition, again on
 * each reload, read from the sources. Glance keeps a widget's composition
 * running for about 45 seconds after it renders, and an update in that time
 * recomposes it without running provideGlance again (Glance 1.2.0,
 * GlanceAppWidget.update and AppWidgetSession.updateGlance). An entry loaded
 * in provideGlance was the one the session started with: the dashboard kept
 * showing connecting after a start's wait ended (review of the connect tap
 * follow-ups, 2026-10-16), and a disconnect soon after an update kept it on.
 * The Glance composition needs a device, which these JVM tests do not have;
 * the tick itself is WidgetReloadsTest's.
 */
class WidgetEntryReloadWiringTest {
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

        private const val SOURCE_DIR = "src/main/java/com/bringyour/network/widgets"

        private fun source(name: String) = moduleFile("$SOURCE_DIR/$name").readText()

        // the text between two markers, failing when either is missing
        private fun between(source: String, start: String, end: String): String {
            val from = source.indexOf(start)
            assertTrue("missing: $start", 0 <= from)
            val to = source.indexOf(end, from + start.length)
            assertTrue("missing after $start: $end", 0 <= to)
            return source.substring(from, to)
        }

        private const val PROVIDE_GLANCE = "override suspend fun provideGlance(context: Context, id: GlanceId) {"
    }

    private fun assertReadsItsEntryInTheComposition(file: String, kind: String) {
        val provideGlance = between(source(file), PROVIDE_GLANCE, "\n    }\n")
        assertFalse(
            "$file loads its entry outside the composition",
            provideGlance.contains("WidgetEntry.load("),
        )
        val from = provideGlance.indexOf("provideContent {")
        assertTrue("$file provides no content", 0 <= from)
        assertTrue(
            "$file does not read its entry in the composition",
            provideGlance.substring(from).lineSequence().first()
                .contains("rememberWidgetEntry(context, WidgetKinds.$kind)"),
        )
    }

    @Test
    fun theDashboardReadsItsEntryOnEachReload() {
        assertReadsItsEntryInTheComposition("DashboardWidget.kt", "DASHBOARD")
    }

    @Test
    fun theGlobeReadsItsEntryOnEachReload() {
        assertReadsItsEntryInTheComposition("ProviderGlobeWidget.kt", "PROVIDER_GLOBE")
    }

    @Test
    fun theContractsWidgetReadsItsEntryOnEachReload() {
        assertReadsItsEntryInTheComposition("ContractsWidget.kt", "CONTRACTS")
    }

    @Test
    fun theOverviewReadsItsEntryOnEachReload() {
        assertReadsItsEntryInTheComposition("OverviewWidget.kt", "OVERVIEW")
    }

    @Test
    fun theEntryIsReadAgainOnItsKindsTick() {
        val remembered = between(
            source("WidgetReloads.kt"),
            "internal fun rememberWidgetEntry(context: Context, kind: String): WidgetEntry {",
            "\n}\n",
        )
        assertTrue(remembered.contains("val tick by WidgetReloads.tick(kind).collectAsState()"))
        assertTrue(remembered.contains("return remember(tick) { WidgetEntry.load(context) }"))
    }

    @Test
    fun aReloadMovesItsKindsTickBeforeTheUpdate() {
        // a running composition re-reads its entry on the tick, and one that
        // is not running reads it when the update starts it
        val refresh = source("GlanceWidgetRefresh.kt")
        // the overview shows what each of the three does: it re-renders with any of them
        for ((name, call) in listOf(
            "reloadDashboard" to "reload(WidgetKinds.DASHBOARD, DashboardWidget())",
            "reloadProviderGlobe" to "reload(WidgetKinds.PROVIDER_GLOBE, ProviderGlobeWidget())",
            "reloadContracts" to "reload(WidgetKinds.CONTRACTS, ContractsWidget())",
        )) {
            val body = between(refresh, "override fun $name() {", "\n    }\n")
            assertTrue(body, body.contains(call))
            assertTrue(body, body.contains("reloadOverview()"))
        }
        val all = between(refresh, "override fun reloadAll() {", "\n    }\n")
        assertTrue(all, all.contains("reloadOverview()"))
        assertTrue(refresh.contains("private fun reloadOverview() = reload(WidgetKinds.OVERVIEW, OverviewWidget())"))
        val reload = between(refresh, "private fun reload(kind: String, widget: GlanceAppWidget) {", "\n    }\n")
        val bump = reload.indexOf("WidgetReloads.bump(kind)")
        val update = reload.indexOf("widget.updateAll(context)")
        assertTrue("the reload does not move the tick", 0 <= bump)
        assertTrue("the reload does not update the widget", 0 <= update)
        assertTrue("the tick moves after the update", bump < update)
    }
}
