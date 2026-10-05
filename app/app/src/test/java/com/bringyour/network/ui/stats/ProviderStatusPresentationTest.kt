package com.bringyour.network.ui.stats

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ProviderStatusPresentationTest {

    // the English templates of the value keys
    private val strings = object : ProviderStatusValueStrings {
        override fun withMinimum(value: String, minimum: String) = "$value (needs $minimum)"
        override fun withMaximum(value: String, maximum: String) = "$value (at most $maximum)"
        override fun countOfTotal(count: Long, total: Long) = "$count of $total loaded"
        override val notYet = "Not yet"
        override val noHistory = "No history yet"
        override val notInPool = "Not in this pool"
    }

    private fun text(row: ProviderStatusRow?): String? = row?.value?.format(strings, Locale.US)

    private fun loadedStatus(
        appearancesPerMinute: List<Long>? = List(60) { 0L },
        rankingNumbers: List<ProviderRankingNumberUi> = listOf(),
        country: ProviderStatusCountryUi? = null,
    ) = ProviderStatusUi(
        loaded = true,
        hasStatus = true,
        reason = Sdk.ProviderStatusReasonNone,
        reasonText = "Everything checks out.",
        hasAppearances = appearancesPerMinute != null,
        appearancesPerMinute = appearancesPerMinute ?: listOf(),
        rankingNumbers = rankingNumbers,
        country = country,
    )

    // the histogram

    @Test
    fun histogramScalesSixtyBarsToTheLargestMinute() {
        val counts = List(60) { 0L }.toMutableList()
        counts[0] = 2
        counts[30] = 8
        counts[59] = 4
        val histogram = ProviderDemandHistogram.fromCounts(counts)

        assertEquals(60, histogram.fractions.size)
        assertEquals(counts, histogram.counts)
        assertEquals(8L, histogram.maxCount)
        assertEquals(14L, histogram.total)
        assertEquals(0.25f, histogram.fractions[0], 0f)
        assertEquals(1f, histogram.fractions[30], 0f)
        assertEquals(0.5f, histogram.fractions[59], 0f)
        assertEquals(0f, histogram.fractions[1], 0f)
        assertFalse(histogram.empty)
    }

    @Test
    fun allZeroHistogramIsEmptyAndScalesByOne() {
        val histogram = ProviderDemandHistogram.fromCounts(List(60) { 0L })

        assertTrue(histogram.empty)
        assertEquals(0L, histogram.total)
        assertEquals(0L, histogram.maxCount)
        assertEquals(List(60) { 0f }, histogram.fractions)
    }

    @Test
    fun histogramAlwaysHasSixtyBarsWithTheCurrentMinuteLast() {
        val short = ProviderDemandHistogram.fromCounts(listOf(1L, 2L))
        assertEquals(60, short.counts.size)
        assertEquals(List(58) { 0L } + listOf(1L, 2L), short.counts)

        val long = ProviderDemandHistogram.fromCounts(List(61) { it.toLong() })
        assertEquals(60, long.counts.size)
        assertEquals(1L, long.counts.first())
        assertEquals(60L, long.counts.last())

        assertTrue(ProviderDemandHistogram.fromCounts(listOf()).empty)
        assertEquals(60, ProviderDemandHistogram.fromCounts(listOf()).fractions.size)
    }

    // the line under the provide mode row

    @Test
    fun localReasonsWinOverTheServerReason() {
        assertEquals(
            ProviderStatusLine.Resource(R.string.provider_idle_auto_not_connected),
            providerStatusLine(ProviderIdleReason.AUTO_NOT_CONNECTED, Sdk.ProviderStatusReasonReliabilityLow, "low"),
        )
        // right after a mode change the cached server reason still says network only
        assertEquals(
            ProviderStatusLine.Resource(R.string.provider_idle_paused_wifi_only),
            providerStatusLine(ProviderIdleReason.PAUSED_WIFI_ONLY, Sdk.ProviderStatusReasonNetworkOnly, "network"),
        )
        assertEquals(
            ProviderStatusLine.Resource(R.string.provider_idle_network_only),
            providerStatusLine(ProviderIdleReason.NETWORK_ONLY, "", ""),
        )
        assertEquals(
            ProviderStatusLine.Resource(R.string.provider_idle_paused_no_network),
            providerStatusLine(ProviderIdleReason.PAUSED_NO_NETWORK, Sdk.ProviderStatusReasonNone, ""),
        )
    }

    @Test
    fun theServerReasonWinsOverNoTrafficYet() {
        assertEquals(
            ProviderStatusLine.Resource(R.string.provider_status_reason_reliability_warming_up),
            providerStatusLine(
                ProviderIdleReason.NO_TRAFFIC_YET,
                Sdk.ProviderStatusReasonReliabilityWarmingUp,
                "Building reliability.",
            ),
        )
        assertEquals(
            ProviderStatusLine.Resource(R.string.provider_status_reason_egress_failing),
            providerStatusLine(ProviderIdleReason.NONE, Sdk.ProviderStatusReasonEgressFailing, "failing"),
        )
    }

    @Test
    fun serverNoneShowsNoTrafficYetOnlyWhileIdle() {
        assertEquals(
            ProviderStatusLine.Resource(R.string.provider_idle_no_traffic_yet),
            providerStatusLine(ProviderIdleReason.NO_TRAFFIC_YET, Sdk.ProviderStatusReasonNone, "Everything checks out."),
        )
        assertNull(providerStatusLine(ProviderIdleReason.NONE, Sdk.ProviderStatusReasonNone, "Everything checks out."))
        // before the first poll
        assertEquals(
            ProviderStatusLine.Resource(R.string.provider_idle_no_traffic_yet),
            providerStatusLine(ProviderIdleReason.NO_TRAFFIC_YET, "", ""),
        )
        assertNull(providerStatusLine(ProviderIdleReason.NONE, "", ""))
    }

    @Test
    fun anUnknownServerReasonShowsItsText() {
        assertEquals(
            ProviderStatusLine.Text("A reason from a newer server."),
            providerStatusLine(ProviderIdleReason.NONE, "future_reason", "A reason from a newer server."),
        )
        assertEquals(
            ProviderStatusLine.Text("A reason from a newer server."),
            providerStatusLine(ProviderIdleReason.NO_TRAFFIC_YET, "future_reason", "A reason from a newer server."),
        )
        // without text the line falls through
        assertEquals(
            ProviderStatusLine.Resource(R.string.provider_idle_no_traffic_yet),
            providerStatusLine(ProviderIdleReason.NO_TRAFFIC_YET, "future_reason", ""),
        )
    }

    @Test
    fun everyServerReasonCodeHasItsKey() {
        val keys = mapOf(
            Sdk.ProviderStatusReasonNotProviding to R.string.provider_status_reason_not_providing,
            Sdk.ProviderStatusReasonNotConnected to R.string.provider_status_reason_not_connected,
            Sdk.ProviderStatusReasonLocationInvalid to R.string.provider_status_reason_location_invalid,
            Sdk.ProviderStatusReasonNetworkOnly to R.string.provider_status_reason_network_only,
            Sdk.ProviderStatusReasonReliabilityWarmingUp to R.string.provider_status_reason_reliability_warming_up,
            Sdk.ProviderStatusReasonReliabilityLow to R.string.provider_status_reason_reliability_low,
            Sdk.ProviderStatusReasonNotEligible to R.string.provider_status_reason_not_eligible,
            Sdk.ProviderStatusReasonEgressUnprobed to R.string.provider_status_reason_egress_unprobed,
            Sdk.ProviderStatusReasonEgressFailing to R.string.provider_status_reason_egress_failing,
            Sdk.ProviderStatusReasonSpeedTestMissing to R.string.provider_status_reason_speed_test_missing,
            Sdk.ProviderStatusReasonSlow to R.string.provider_status_reason_slow,
            Sdk.ProviderStatusReasonNone to R.string.provider_status_reason_none,
        )
        assertEquals(12, keys.size)
        keys.forEach { (reason, key) ->
            assertEquals(reason, key, providerStatusReasonResourceId(reason))
        }
        assertNull(providerStatusReasonResourceId("future_reason"))
        assertNull(providerStatusReasonResourceId(""))
    }

    // the "Why?" rows

    @Test
    fun reliabilityRowsShowTheMinimumOrNoHistory() {
        val fiveMinutes = providerStatusRow(
            ProviderRankingNumberUi(name = Sdk.ProviderStatusNumberReliability5m, hasValue = true, value = 0.95)
        )
        assertEquals(R.string.provider_status_number_reliability_5m, fiveMinutes?.labelResourceId)
        assertEquals(R.string.provider_status_help_reliability_5m, fiveMinutes?.helpResourceId)
        assertEquals("95%", text(fiveMinutes))
        assertEquals(
            "No history yet",
            text(providerStatusRow(ProviderRankingNumberUi(name = Sdk.ProviderStatusNumberReliability5m))),
        )

        val hour = providerStatusRow(
            ProviderRankingNumberUi(
                name = Sdk.ProviderStatusNumberReliability1h,
                hasValue = true,
                value = 0.62,
                hasMinimum = true,
                minimum = 0.7,
                passes = false,
            )
        )
        assertEquals(R.string.provider_status_number_reliability_1h, hour?.labelResourceId)
        assertEquals(R.string.provider_status_help_reliability_1h, hour?.helpResourceId)
        assertEquals("62% (needs 70%)", text(hour))
        assertEquals(false, hour?.passes)

        val twelveHours = providerStatusRow(
            ProviderRankingNumberUi(
                name = Sdk.ProviderStatusNumberReliability12h,
                hasMinimum = true,
                minimum = 0.7,
            )
        )
        assertEquals(R.string.provider_status_number_reliability_12h, twelveHours?.labelResourceId)
        assertEquals(R.string.provider_status_help_reliability_12h, twelveHours?.helpResourceId)
        assertEquals("No history yet", text(twelveHours))
        assertEquals(true, twelveHours?.passes)

        val lookback = providerStatusRow(
            ProviderRankingNumberUi(
                name = "reliability_lookback_3",
                hasValue = true,
                value = 0.9,
                hasMinimum = true,
                minimum = 0.5,
            )
        )
        assertEquals(R.string.reliability, lookback?.labelResourceId)
        assertEquals(R.string.provider_status_help_reliability_other, lookback?.helpResourceId)
        assertEquals("90% (needs 50%)", text(lookback))
    }

    @Test
    fun checkSpeedAndDelayRowsSayNotYetWithoutAValue() {
        val urlChecks = providerStatusRow(
            ProviderRankingNumberUi(
                name = Sdk.ProviderStatusNumberUrlChecks,
                hasValue = true,
                value = 0.8,
                hasMinimum = true,
                minimum = 0.8,
                count = 4,
                total = 5,
            )
        )
        assertEquals(R.string.provider_status_number_url_checks, urlChecks?.labelResourceId)
        assertEquals(R.string.provider_status_help_url_checks, urlChecks?.helpResourceId)
        assertEquals("4 of 5 loaded (needs 80%)", text(urlChecks))
        assertEquals(
            "Not yet",
            text(
                providerStatusRow(
                    ProviderRankingNumberUi(
                        name = Sdk.ProviderStatusNumberUrlChecks,
                        hasMinimum = true,
                        minimum = 0.8,
                        passes = false,
                    )
                )
            ),
        )

        val speedTest = providerStatusRow(
            ProviderRankingNumberUi(
                name = Sdk.ProviderStatusNumberSpeedTest,
                hasValue = true,
                value = 2_500_000.0,
                hasMinimum = true,
                minimum = 1_000_000.0,
            )
        )
        assertEquals(R.string.provider_status_number_speed_test, speedTest?.labelResourceId)
        assertEquals(R.string.provider_status_help_speed_test, speedTest?.helpResourceId)
        assertEquals("2.38 MiB/s (needs 977 KiB/s)", text(speedTest))
        assertEquals(
            "Not yet",
            text(providerStatusRow(ProviderRankingNumberUi(name = Sdk.ProviderStatusNumberSpeedTest, hasMinimum = true))),
        )

        val latency = providerStatusRow(
            ProviderRankingNumberUi(
                name = Sdk.ProviderStatusNumberLatency,
                hasValue = true,
                value = 35.0,
                hasMaximum = true,
                maximum = 120.0,
            )
        )
        assertEquals(R.string.provider_status_number_latency, latency?.labelResourceId)
        assertEquals(R.string.provider_status_help_latency, latency?.helpResourceId)
        assertEquals("35 ms (at most 120 ms)", text(latency))
        assertEquals(
            "Not yet",
            text(providerStatusRow(ProviderRankingNumberUi(name = Sdk.ProviderStatusNumberLatency, hasMaximum = true))),
        )
    }

    @Test
    fun weightAndTierRows() {
        val qualityWeight = providerStatusRow(
            ProviderRankingNumberUi(name = Sdk.ProviderStatusNumberWeightQuality, hasValue = true, value = 1.23456)
        )
        assertEquals(R.string.provider_status_number_weight_quality, qualityWeight?.labelResourceId)
        assertEquals(R.string.provider_status_help_weight, qualityWeight?.helpResourceId)
        assertEquals("1.23", text(qualityWeight))

        val speedWeight = providerStatusRow(
            ProviderRankingNumberUi(
                name = Sdk.ProviderStatusNumberWeightSpeed,
                hasValue = true,
                value = 0.5,
                passes = false,
            )
        )
        assertEquals(R.string.provider_status_number_weight_speed, speedWeight?.labelResourceId)
        assertEquals("Not in this pool", text(speedWeight))
        assertEquals(false, speedWeight?.passes)

        val qualityTier = providerStatusRow(
            ProviderRankingNumberUi(
                name = Sdk.ProviderStatusNumberTierQuality,
                hasValue = true,
                value = 0.0,
                hasMaximum = true,
                maximum = 2.0,
            )
        )
        assertEquals(R.string.provider_status_number_tier_quality, qualityTier?.labelResourceId)
        assertEquals(R.string.provider_status_help_tier, qualityTier?.helpResourceId)
        // the integer only, never "(at most ...)"
        assertEquals("0", text(qualityTier))

        val speedTier = providerStatusRow(
            ProviderRankingNumberUi(
                name = Sdk.ProviderStatusNumberTierSpeed,
                hasValue = true,
                value = 3.0,
                hasMaximum = true,
                maximum = 2.0,
                passes = false,
            )
        )
        assertEquals(R.string.provider_status_number_tier_speed, speedTier?.labelResourceId)
        assertEquals("3", text(speedTier))
    }

    @Test
    fun unknownNamesAreSkippedAndTheCountryComesLastInServerOrder() {
        val status = loadedStatus(
            rankingNumbers = listOf(
                ProviderRankingNumberUi(name = Sdk.ProviderStatusNumberLatency, hasValue = true, value = 10.0),
                ProviderRankingNumberUi(name = "future_number", hasValue = true, value = 1.0),
                ProviderRankingNumberUi(name = "reliability_lookback_x", hasValue = true, value = 1.0),
                ProviderRankingNumberUi(name = Sdk.ProviderStatusNumberReliability5m, hasValue = true, value = 1.0),
            ),
            country = ProviderStatusCountryUi(countryCode = "de", country = "Germany"),
        )
        val rows = providerStatusRows(status)

        assertEquals(
            listOf(
                R.string.provider_status_number_latency,
                R.string.provider_status_number_reliability_5m,
                R.string.country,
            ),
            rows.map { it.labelResourceId },
        )
        assertEquals("Germany", text(rows.last()))
        assertEquals(R.string.provider_status_help_country, rows.last().helpResourceId)
        assertNull(providerStatusRow(ProviderRankingNumberUi(name = "future_number")))

        val codeOnly = providerStatusRows(
            loadedStatus(country = ProviderStatusCountryUi(countryCode = "de", country = ""))
        )
        assertEquals("DE", text(codeOnly.single()))

        assertEquals(listOf<ProviderStatusRow>(), providerStatusRows(loadedStatus()))
    }

    // the states of the chart area

    @Test
    fun statesBeforeAndWithoutAStatus() {
        assertEquals(
            ProviderStatusDisplay(ProviderDemandState.LOADING, why = false),
            providerStatusDisplay(ProviderStatusUi.Empty),
        )
        // for example before the server has the route
        assertEquals(
            ProviderStatusDisplay(ProviderDemandState.UNAVAILABLE, why = false),
            providerStatusDisplay(ProviderStatusUi(lastFetchError = "404 Not Found")),
        )
        // this device is not among the network's provider clients
        assertEquals(
            ProviderStatusDisplay(ProviderDemandState.UNAVAILABLE, why = false),
            providerStatusDisplay(ProviderStatusUi(loaded = true)),
        )
    }

    @Test
    fun statesWithAStatus() {
        assertEquals(
            ProviderStatusDisplay(ProviderDemandState.UNAVAILABLE, why = true),
            providerStatusDisplay(loadedStatus(appearancesPerMinute = null)),
        )
        assertEquals(
            ProviderStatusDisplay(ProviderDemandState.EMPTY, why = true),
            providerStatusDisplay(loadedStatus()),
        )
        val counts = List(60) { if (it == 10) 3L else 0L }
        assertEquals(
            ProviderStatusDisplay(ProviderDemandState.BARS, why = true),
            providerStatusDisplay(loadedStatus(appearancesPerMinute = counts)),
        )
        // a failed poll after a success keeps the last snapshot
        assertEquals(
            ProviderStatusDisplay(ProviderDemandState.BARS, why = true),
            providerStatusDisplay(loadedStatus(appearancesPerMinute = counts).copy(lastFetchError = "timeout")),
        )
    }
}
