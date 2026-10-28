package com.bringyour.network.ui.connect

import com.bringyour.network.TransportDelayUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the connect screen's slow internet pill says (apple/DELAY.md §5.2),
 * the Apple pill's SlowInternetPillContentTests: "Poor signal", or "Fallback"
 * on the fallback connection; each direction's standing delay, else its
 * limited rate, else nothing; the larger delay the worse, drawn amber; with
 * too little room the better direction goes, then the numbers; and "Internet
 * back to normal" on a return to normal, never when the reading only went
 * away.
 */
class SlowInternetPillContentTest {

    private val slow = TransportDelayUi(
        upQuality = 0.25,
        downQuality = 0.75,
        upBitsPerSecond = 48_000,
        downBitsPerSecond = 85_000,
        upQueueDelayMillis = 800,
        downQueueDelayMillis = 1_900,
    )

    /** No standing delay either way, both directions limited. */
    private val ratesOnly = TransportDelayUi(
        downQuality = 0.75,
        upBitsPerSecond = 150_000,
        downBitsPerSecond = 280_000,
    )

    private val normal = TransportDelayUi.None

    /** What the pill shows after [shown] for [delay], by default with a connection requested and nothing over it. */
    private fun next(
        shown: SlowInternetPillContent?,
        delay: TransportDelayUi,
        available: Boolean = true,
        hidden: Boolean = false,
    ): SlowInternetPillContent? = SlowInternetPillContent.next(shown, delay, available, hidden)

    @Test
    fun theLabelIsPoorSignalOrFallback() {
        assertEquals(SlowInternetPillContent.Label.POOR_SIGNAL, SlowInternetPillContent(slow).label)
        assertEquals(SlowInternetPillContent.Label.FALLBACK, SlowInternetPillContent(slow.copy(fallback = true)).label)
    }

    // each direction shows its standing delay, the larger one the worse
    @Test
    fun eachDirectionShowsItsDelay() {
        val content = SlowInternetPillContent(slow)
        assertEquals("800 ms", content.up)
        assertEquals("1.9 s", content.down)
        assertEquals(SlowInternetPillContent.Direction.DOWN, content.worse)
        assertFalse(content.recovered)

        val upWorse = slow.copy(upQueueDelayMillis = 2_900)
        assertEquals(SlowInternetPillContent.Direction.UP, SlowInternetPillContent(upWorse).worse)

        // equal delays mark neither
        val equal = slow.copy(upQueueDelayMillis = 1_900)
        assertNull(SlowInternetPillContent(equal).worse)
    }

    // no standing delay but a limited rate: the rate shows instead, and a
    // rate is never the worse, which goes by delay
    @Test
    fun aDirectionWithNoDelayShowsItsLimitedRate() {
        val mixed = SlowInternetPillContent(
            TransportDelayUi(
                upQuality = 0.5,
                upBitsPerSecond = 150_000,
                downBitsPerSecond = 280_000,
                downQueueDelayMillis = 620,
            )
        )
        assertEquals("150 kb/s", mixed.up)
        assertEquals("620 ms", mixed.down)
        assertEquals(SlowInternetPillContent.Direction.DOWN, mixed.worse)

        val rates = SlowInternetPillContent(ratesOnly)
        assertEquals("150 kb/s", rates.up)
        assertEquals("280 kb/s", rates.down)
        assertNull(rates.worse)
    }

    // neither a delay nor a rate: the direction hides; with both hidden the
    // pill is the tunnel and the label
    @Test
    fun aDirectionWithNeitherHides() {
        val downOnly = SlowInternetPillContent(TransportDelayUi(downQuality = 0.25, downQueueDelayMillis = 640))
        assertNull(downOnly.up)
        assertEquals("640 ms", downOnly.down)
        assertEquals(SlowInternetPillContent.Direction.DOWN, downOnly.worse)

        val neither = SlowInternetPillContent(TransportDelayUi(upQuality = 0.5))
        assertNull(neither.up)
        assertNull(neither.down)
        assertNull(neither.worse)
        assertEquals(SlowInternetPillContent.Label.POOR_SIGNAL, neither.label)
    }

    // with too little room the better direction goes first, then the numbers
    @Test
    fun theBetterDirectionGoesFirstThenTheNumbers() {
        val content = SlowInternetPillContent(slow)
        val one = content.worseOnly
        assertNull(one.up)
        assertEquals("1.9 s", one.down)
        assertEquals(SlowInternetPillContent.Label.POOR_SIGNAL, one.label)
        val none = content.labelOnly
        assertNull(none.up)
        assertNull(none.down)
        assertEquals(SlowInternetPillContent.Label.POOR_SIGNAL, none.label)

        val upWorse = SlowInternetPillContent(slow.copy(upQueueDelayMillis = 2_900))
        assertEquals("2.9 s", upWorse.worseOnly.up)
        assertNull(upWorse.worseOnly.down)

        // neither worse: the download stays, or the upload when only it shows
        val rates = SlowInternetPillContent(ratesOnly).worseOnly
        assertNull(rates.up)
        assertEquals("280 kb/s", rates.down)
        val upOnly = SlowInternetPillContent(TransportDelayUi(upQuality = 0.5, upBitsPerSecond = 150_000))
        assertEquals(upOnly, upOnly.worseOnly)
    }

    // the reading returns to normal while the pill shows it: "Internet back
    // to normal" with no numbers, which then holds
    @Test
    fun aReturnToNormalShowsTheRecoveredState() {
        val shown = next(null, slow)
        assertEquals(SlowInternetPillContent(slow), shown)

        val recovered = next(shown, normal)
        assertNotNull(recovered)
        assertTrue(recovered!!.recovered)
        assertNull(recovered.up)
        assertNull(recovered.down)
        assertNull(recovered.worse)

        // later normal readings, numbers under the thresholds included, hold it
        assertEquals(recovered, next(recovered, normal))
        val quiet = TransportDelayUi(upQueueDelayMillis = 120, downQueueDelayMillis = 90)
        assertEquals(recovered, next(recovered, quiet))
        assertEquals(recovered, next(shown, quiet))
    }

    // a new slow level during the hold ends it: the slow content returns
    @Test
    fun aSlowLevelDuringTheHoldReturnsToTheSlowContent() {
        val recovered = next(SlowInternetPillContent(slow), normal)
        assertNotNull(recovered)
        val again = slow.copy(downQueueDelayMillis = 2_400)
        val shown = next(recovered, again)
        assertNotNull(shown)
        assertFalse(shown!!.recovered)
        assertEquals(SlowInternetPillContent(again), shown)
    }

    // the reading going away (the connection no longer requested) or the
    // referral toast taking the top is no recovery: the pill just leaves
    @Test
    fun aReadingThatGoesAwayIsNoRecovery() {
        val shown = SlowInternetPillContent(slow)
        assertNull(next(shown, normal, available = false))
        assertNull(next(shown, slow, available = false))
        assertNull(next(shown, slow, hidden = true))
        assertNull(next(shown, normal, hidden = true))

        // the toast during the hold ends it at once
        val recovered = next(shown, normal)
        assertEquals(true, recovered?.recovered)
        assertNull(next(recovered, normal, hidden = true))
    }

    // nothing shown, a normal reading shows nothing: no recovered state
    // without a slow spell on screen (the pill appearing, one that left, or a
    // return to the screen: its stop forgets what the pill showed)
    @Test
    fun normalWithNothingShownShowsNothing() {
        assertNull(next(null, normal))
        assertNull(next(null, normal, available = false))
        // a return to a level still above 0 shows the slow content at once
        assertEquals(SlowInternetPillContent(slow), next(null, slow))
    }
}
