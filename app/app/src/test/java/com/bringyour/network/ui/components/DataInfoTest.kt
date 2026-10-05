package com.bringyour.network.ui.components

import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.utils.formatBalanceBytes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class DataInfoTest {

    private val gib = 1024L * 1024 * 1024
    private val tib = 1024L * gib

    private lateinit var defaultLocale: Locale
    private lateinit var defaultTimeZone: TimeZone

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        defaultTimeZone = TimeZone.getDefault()
        // formatBalanceBytes formats with the default locale
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultTimeZone)
    }

    private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun nextRefreshIsTheNextUtcMidnight() {
        assertEquals(millis("2026-10-05T00:00:00Z"), nextFreeRefreshMillis(millis("2026-10-04T12:34:56Z")))
        assertEquals(millis("2026-10-05T00:00:00Z"), nextFreeRefreshMillis(millis("2026-10-04T00:00:00.001Z")))
    }

    @Test
    fun nextRefreshAroundMidnight() {
        // just before midnight: the refresh is a millisecond away, still today's
        assertEquals(millis("2026-10-05T00:00:00Z"), nextFreeRefreshMillis(millis("2026-10-04T23:59:59.999Z")))
        // at midnight the refresh has just happened: the next one is a day away
        assertEquals(millis("2026-10-06T00:00:00Z"), nextFreeRefreshMillis(millis("2026-10-05T00:00:00Z")))
        assertEquals(millis("2026-10-06T00:00:00Z"), nextFreeRefreshMillis(millis("2026-10-05T00:00:00.001Z")))
    }

    @Test
    fun nextRefreshRollsOverMonthsYearsAndLeapDays() {
        assertEquals(millis("2026-11-01T00:00:00Z"), nextFreeRefreshMillis(millis("2026-10-31T18:00:00Z")))
        assertEquals(millis("2027-01-01T00:00:00Z"), nextFreeRefreshMillis(millis("2026-12-31T23:30:00Z")))
        assertEquals(millis("2028-02-29T00:00:00Z"), nextFreeRefreshMillis(millis("2028-02-28T10:00:00Z")))
        assertEquals(millis("2028-03-01T00:00:00Z"), nextFreeRefreshMillis(millis("2028-02-29T10:00:00Z")))
    }

    @Test
    fun nextRefreshIgnoresTheDeviceTimeZone() {
        // the refresh is 00:00 UTC, not local midnight
        val now = millis("2026-10-04T20:15:00Z")
        for (zone in listOf("UTC", "Asia/Kolkata", "America/Los_Angeles", "Pacific/Kiritimati", "Pacific/Pago_Pago")) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            assertEquals(zone, millis("2026-10-05T00:00:00Z"), nextFreeRefreshMillis(now))
            assertEquals(zone, RefreshCountdown(hours = 3, minutes = 45), freeRefreshCountdown(now))
        }
    }

    @Test
    fun countdownInHoursAndMinutes() {
        assertEquals(RefreshCountdown(hours = 5, minutes = 12), freeRefreshCountdown(millis("2026-10-04T18:48:00Z")))
        assertEquals(RefreshCountdown(hours = 23, minutes = 59), freeRefreshCountdown(millis("2026-10-04T00:01:00Z")))
        // a partial minute rounds up
        assertEquals(RefreshCountdown(hours = 5, minutes = 13), freeRefreshCountdown(millis("2026-10-04T18:47:30Z")))
    }

    @Test
    fun countdownAroundMidnight() {
        // just before midnight it never reads zero
        assertEquals(RefreshCountdown(hours = 0, minutes = 1), freeRefreshCountdown(millis("2026-10-04T23:59:59.999Z")))
        assertEquals(RefreshCountdown(hours = 0, minutes = 1), freeRefreshCountdown(millis("2026-10-04T23:59:00Z")))
        assertEquals(RefreshCountdown(hours = 0, minutes = 2), freeRefreshCountdown(millis("2026-10-04T23:58:59.999Z")))
        // at and just after midnight it rolls over to the next day's refresh
        assertEquals(RefreshCountdown(hours = 24, minutes = 0), freeRefreshCountdown(millis("2026-10-05T00:00:00Z")))
        assertEquals(RefreshCountdown(hours = 24, minutes = 0), freeRefreshCountdown(millis("2026-10-05T00:00:00.001Z")))
        assertEquals(RefreshCountdown(hours = 23, minutes = 59), freeRefreshCountdown(millis("2026-10-05T00:01:00Z")))
    }

    @Test
    fun countdownTicksOncePerDisplayedMinute() {
        assertEquals(60_000L, millisUntilCountdownChanges(millis("2026-10-04T18:48:00Z")))
        assertEquals(30_000L, millisUntilCountdownChanges(millis("2026-10-04T18:47:30Z")))
        assertEquals(1L, millisUntilCountdownChanges(millis("2026-10-04T23:59:59.999Z")))
        for (iso in listOf(
            "2026-10-04T18:48:00Z",
            "2026-10-04T18:47:30.250Z",
            "2026-10-04T23:59:59.999Z",
            "2026-10-04T23:59:00Z",
            "2026-10-05T00:00:00Z",
        )) {
            val now = millis(iso)
            val wake = millisUntilCountdownChanges(now)
            assertEquals(iso, freeRefreshCountdown(now), freeRefreshCountdown(now + wake - 1))
            assertNotEquals(iso, freeRefreshCountdown(now), freeRefreshCountdown(now + wake))
        }
    }

    @Test
    fun countdownFormatsAsACompactDuration() {
        // the provider_connected_duration_hours / _minutes English templates
        val hoursAndMinutes = { hours: Long, minutes: Long -> "${hours}h ${minutes}m" }
        val minutesOnly = { minutes: Long -> "${minutes}m" }
        assertEquals("5h 12m", formatRefreshCountdown(RefreshCountdown(5, 12), hoursAndMinutes, minutesOnly))
        assertEquals("24h 0m", formatRefreshCountdown(RefreshCountdown(24, 0), hoursAndMinutes, minutesOnly))
        assertEquals("1h 0m", formatRefreshCountdown(RefreshCountdown(1, 0), hoursAndMinutes, minutesOnly))
        assertEquals("59m", formatRefreshCountdown(RefreshCountdown(0, 59), hoursAndMinutes, minutesOnly))
        assertEquals("1m", formatRefreshCountdown(freeRefreshCountdown(millis("2026-10-04T23:59:59.999Z")), hoursAndMinutes, minutesOnly))
    }

    @Test
    fun usedIsWhatIsNeitherAvailableNorPending() {
        val info = dataInfo(
            startBalanceByteCount = 30 * gib,
            availableByteCount = 20 * gib,
            pendingByteCount = 2 * gib,
        )
        assertEquals(DataInfo(used = "8.00 GiB", pending = "2.00 GiB", available = "20.00 GiB", daily = "30.00 GiB"), info)
    }

    @Test
    fun usedNeverGoesNegative() {
        // the server samples the values independently
        val info = dataInfo(
            startBalanceByteCount = 1 * gib,
            availableByteCount = 1 * gib,
            pendingByteCount = gib / 2,
        )
        assertEquals("0.00 GiB", info.used)
        assertEquals("0.50 GiB", info.pending)
        assertEquals("1.00 GiB", info.available)
        assertEquals("0.00 GiB", dataInfo(0, -1, -1).pending)
        assertEquals("0.00 GiB", dataInfo(0, -1, -1).available)
    }

    @Test
    fun allPendingNothingAvailable() {
        // the reported case: Pending fills the bar and nothing is available
        val info = dataInfo(
            startBalanceByteCount = 30 * gib,
            availableByteCount = 0,
            pendingByteCount = 29 * gib,
        )
        assertEquals(DataInfo(used = "1.00 GiB", pending = "29.00 GiB", available = "0.00 GiB", daily = "30.00 GiB"), info)
    }

    @Test
    fun dailyAmountIsTheServersStartBalance() {
        // never a hard-coded allowance: whatever start_balance_byte_count says
        assertEquals("30.00 GiB", dataInfo(30 * gib, 30 * gib, 0).daily)
        assertEquals("60.00 GiB", dataInfo(60 * gib, 60 * gib, 0).daily)
        assertEquals("33.00 GiB", dataInfo(33 * gib, 10 * gib, 0).daily)
        assertEquals("10.00 TiB", dataInfo(10 * tib, 10 * tib, 0).daily)
        assertEquals(formatBalanceBytes(12_345_678_901L), dataInfo(12_345_678_901L, 0, 0).daily)
    }

    @Test
    fun freeRefreshLineIsForNetworksWithoutPro() {
        assertTrue(dataInfoShowsFreeRefresh(Plan.Basic))
        assertFalse(dataInfoShowsFreeRefresh(Plan.Supporter))
    }
}
