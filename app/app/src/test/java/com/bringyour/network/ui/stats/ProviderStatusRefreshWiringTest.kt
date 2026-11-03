package com.bringyour.network.ui.stats

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A failed poll leaves the Demand chart and its Why? empty until the next poll,
 * up to a minute away, and the pull to refresh on Earnings is the user asking for
 * them again (android#487). Read from the source, since the screen needs a device.
 */
class ProviderStatusRefreshWiringTest {

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
    fun theEarningsRefreshAsksForTheProviderStatus() {
        val refresh = between(source("ui/wallet/EarningsScreen.kt"), "refresh = {", "},")
        assertTrue(refresh, refresh.contains("providerStatusViewModel.refresh()"))
    }

    @Test
    fun theViewModelRefreshPollsTheController() {
        val refresh = between(source("ui/stats/ProviderStatusViewModel.kt"), "fun refresh()", "\n    }")
        assertTrue(refresh, refresh.contains("ifStarted") && refresh.contains("it.refresh()"))
    }

    @Test
    fun theChartsRetryIsWired() {
        val section = source("ui/stats/ProviderStatsSection.kt")
        assertTrue(section.contains("onRetry = providerStatusViewModel::refresh"))
        val chart = source("ui/stats/ProviderDemandChart.kt")
        assertTrue(chart.contains("onRetry: () -> Unit"))
        assertTrue(chart.contains("display.retry"))
    }
}
