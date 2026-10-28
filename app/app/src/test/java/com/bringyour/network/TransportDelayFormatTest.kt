package com.bringyour.network

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the slow internet surfaces show the sdk's numbers (apple/DELAY.md §4):
 * the same on every platform, units not localized, a 0 value hidden.
 */
class TransportDelayFormatTest {

    @Test
    fun delaysUnderASecondAreWholeMilliseconds() {
        assertEquals("640 ms", formatTransportDelayMillis(640))
        assertEquals("1 ms", formatTransportDelayMillis(1))
        assertEquals("999 ms", formatTransportDelayMillis(999))
    }

    @Test
    fun delaysUnderTenSecondsHaveOneDecimal() {
        assertEquals("1.0 s", formatTransportDelayMillis(1_000))
        assertEquals("1.2 s", formatTransportDelayMillis(1_200))
        assertEquals("1.2 s", formatTransportDelayMillis(1_249))
        assertEquals("1.3 s", formatTransportDelayMillis(1_250))
        assertEquals("9.9 s", formatTransportDelayMillis(9_949))
    }

    @Test
    fun longerDelaysAreWholeSeconds() {
        // a delay that rounds to 10 s is not "10.0 s"
        assertEquals("10 s", formatTransportDelayMillis(9_950))
        assertEquals("12 s", formatTransportDelayMillis(12_000))
        assertEquals("12 s", formatTransportDelayMillis(12_499))
        assertEquals("13 s", formatTransportDelayMillis(12_500))
        assertEquals("125 s", formatTransportDelayMillis(125_000))
    }

    @Test
    fun ratesUnderAMegabitAreWholeKilobits() {
        assertEquals("48 kb/s", formatTransportDelayBitRate(48_000))
        assertEquals("48 kb/s", formatTransportDelayBitRate(48_499))
        assertEquals("49 kb/s", formatTransportDelayBitRate(48_500))
        assertEquals("85 kb/s", formatTransportDelayBitRate(85_000))
        assertEquals("999 kb/s", formatTransportDelayBitRate(999_499))
    }

    @Test
    fun aLimitedRateIsAtLeastOneKilobit() {
        assertEquals("1 kb/s", formatTransportDelayBitRate(300))
        assertEquals("1 kb/s", formatTransportDelayBitRate(1))
    }

    @Test
    fun fasterRatesAreMegabitsWithOneDecimal() {
        // a rate that rounds to 1000 kb/s is not "1000 kb/s"
        assertEquals("1.0 Mb/s", formatTransportDelayBitRate(999_500))
        assertEquals("1.4 Mb/s", formatTransportDelayBitRate(1_400_000))
        assertEquals("12.3 Mb/s", formatTransportDelayBitRate(12_340_000))
    }

    @Test
    fun theFormatsDoNotFollowTheDeviceLocale() {
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.2 s", formatTransportDelayMillis(1_200))
            assertEquals("1.4 Mb/s", formatTransportDelayBitRate(1_400_000))
        } finally {
            Locale.setDefault(locale)
        }
    }

    private val delay = TransportDelayUi(
        upQuality = 0.5,
        downQuality = 0.75,
        upBitsPerSecond = 48_000,
        downBitsPerSecond = 85_000,
        upQueueDelayMillis = 640,
        downQueueDelayMillis = 1_200,
    )

    @Test
    fun eachDirectionHasALineWithItsDelayAndRate() {
        assertEquals(
            listOf("Upload delay 640 ms · 48 kb/s", "Download delay 1.2 s · 85 kb/s"),
            transportDelayLines(delay, uploadLabel = "Upload delay", downloadLabel = "Download delay"),
        )
    }

    @Test
    fun aZeroValueHidesItsPart() {
        // no limited rate: the delay alone
        assertEquals("Upload delay 640 ms", transportDelayLine("Upload delay", delayMillis = 640, bitsPerSecond = 0))
        // no delay: the direction's delay line hides
        assertEquals(null, transportDelayLine("Upload delay", delayMillis = 0, bitsPerSecond = 48_000))
        assertEquals(
            listOf("Download delay 1.2 s · 85 kb/s"),
            transportDelayLines(
                delay.copy(upQueueDelayMillis = 0, upBitsPerSecond = 0),
                uploadLabel = "Upload delay",
                downloadLabel = "Download delay",
            ),
        )
        assertEquals(
            emptyList<String>(),
            transportDelayLines(TransportDelayUi.None, uploadLabel = "Upload delay", downloadLabel = "Download delay"),
        )
    }

    @Test
    fun theWorseDirectionSetsTheQuality() {
        assertEquals(0.75, delay.quality, 0.0)
        assertEquals(0.75, delay.copy(upQuality = 0.75, downQuality = 0.25).quality, 0.0)
        assertTrue(delay.slow)
        assertTrue(TransportDelayUi(upQuality = 0.25).slow)
        assertFalse(TransportDelayUi.None.slow)
        // a fallback reading with no delay is not slow: fallback only switches the copy
        assertFalse(TransportDelayUi(fallback = true).slow)
    }
}
