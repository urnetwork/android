package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The VPN notification's slow internet dwell (apple/DELAY.md §7.2): it
 * appears only after the level has been above 0 for 30 s of the last 60 s,
 * stays at least 60 s, and says "back to normal" for 3 s before the normal
 * content returns. The clock is the test's; nothing waits on time.
 */
class SlowInternetNoticePolicyTest {

    private val slow = TransportDelayUi(downQuality = 0.5, downQueueDelayMillis = 640, downBitsPerSecond = 48_000)
    private val slower = TransportDelayUi(downQuality = 1.0, downQueueDelayMillis = 2_400)
    private val normal = TransportDelayUi.None

    /** The notice after each reading, at its time in seconds, in order, from a fresh notice. */
    private fun observed(vararg readings: Pair<Int, TransportDelayUi>): SlowInternetNotice =
        readings.fold(SlowInternetNotice()) { notice, (seconds, delay) ->
            notice.observe(delay, active = true, nowMillis = seconds * 1_000L)
        }

    private fun SlowInternetNotice.at(seconds: Int, delay: TransportDelayUi, active: Boolean = true) =
        observe(delay, active = active, nowMillis = seconds * 1_000L)

    private fun SlowInternetNotice.nextCheckAtSeconds(seconds: Int): Double? =
        nextCheckAtMillis(seconds * 1_000L)?.let { it / 1_000.0 }

    @Test
    fun aSlowLevelShowsAfterThirtySeconds() {
        val notice = observed(0 to slow)
        assertEquals(SlowInternetNoticePhase.NONE, notice.phase)
        assertEquals(30.0, notice.nextCheckAtSeconds(0))

        assertEquals(SlowInternetNoticePhase.NONE, notice.observe(slow, active = true, nowMillis = 29_999).phase)
        val shown = notice.at(30, slow)
        assertEquals(SlowInternetNoticePhase.SLOW, shown.phase)
        assertEquals(30_000L, shown.phaseSinceMillis)
        assertEquals(slow, shown.delay)
    }

    @Test
    fun aBriefSlowLevelNeverShows() {
        val notice = observed(0 to slow, 20 to normal)
        assertEquals(SlowInternetNoticePhase.NONE, notice.phase)
        // nothing to check until the level changes again
        assertNull(notice.nextCheckAtSeconds(20))
        assertEquals(SlowInternetNoticePhase.NONE, notice.at(80, normal).phase)
    }

    @Test
    fun slowTimeAddsUpWithinTheWindow() {
        // 20 s slow, 10 s normal, slow again: 30 s of the last 60 at 40 s
        val notice = observed(0 to slow, 20 to normal, 30 to slow)
        assertEquals(40.0, notice.nextCheckAtSeconds(30))
        assertEquals(SlowInternetNoticePhase.NONE, notice.at(39, slow).phase)
        assertEquals(SlowInternetNoticePhase.SLOW, notice.at(40, slow).phase)
    }

    @Test
    fun slowTimeOutsideTheWindowNoLongerCounts() {
        // 20 s slow long ago: it slides out of the window while the level
        // is slow again, so the new slow time alone must reach 30 s
        val notice = observed(0 to slow, 20 to normal, 70 to slow)
        assertEquals(100.0, notice.nextCheckAtSeconds(70))
        assertEquals(SlowInternetNoticePhase.NONE, notice.at(99, slow).phase)
        assertEquals(SlowInternetNoticePhase.SLOW, notice.at(100, slow).phase)
    }

    @Test
    fun slowTimeHoldsWhileAnEarlierSlowTimeSlidesOut() {
        // 15 s slow, then slow again from 55 s: 20 s at 60 s, held there
        // while the first 15 s slide out (60 to 75 s), then 30 s at 85 s
        val notice = observed(0 to slow, 15 to normal, 55 to slow)
        assertEquals(85.0, notice.nextCheckAtSeconds(55))
        assertEquals(20_000L, notice.slowMillisAt(60_000))
        assertEquals(20_000L, notice.slowMillisAt(75_000))
        assertEquals(SlowInternetNoticePhase.NONE, notice.at(84, slow).phase)
        assertEquals(SlowInternetNoticePhase.SLOW, notice.at(85, slow).phase)
    }

    @Test
    fun theNoticeStaysAMinuteThenSaysBackToNormal() {
        val shown = observed(0 to slow, 30 to slow)
        assertEquals(SlowInternetNoticePhase.SLOW, shown.phase)

        // normal 5 s after it appeared: it stays its minute
        val stays = shown.at(35, normal)
        assertEquals(SlowInternetNoticePhase.SLOW, stays.phase)
        assertEquals(90.0, stays.nextCheckAtSeconds(35))
        assertEquals(SlowInternetNoticePhase.SLOW, stays.at(89, normal).phase)

        val recovered = stays.at(90, normal)
        assertEquals(SlowInternetNoticePhase.RECOVERED, recovered.phase)
        assertEquals(93.0, recovered.nextCheckAtSeconds(90))
        assertEquals(SlowInternetNoticePhase.RECOVERED, recovered.at(92, normal).phase)

        val ended = recovered.at(93, normal)
        assertEquals(SlowInternetNoticePhase.NONE, ended.phase)
        assertEquals(TransportDelayUi.None, ended.delay)
        assertNull(ended.nextCheckAtSeconds(93))
    }

    @Test
    fun aLevelThatClearsAfterTheMinuteSaysBackToNormalAtOnce() {
        val shown = observed(0 to slow, 30 to slow, 95 to slow)
        assertEquals(SlowInternetNoticePhase.SLOW, shown.phase)
        assertEquals(SlowInternetNoticePhase.RECOVERED, shown.at(96, normal).phase)
    }

    @Test
    fun theMinimumStayKeepsTheNumbersItWasSlowWith() {
        val stays = observed(0 to slow, 30 to slow, 35 to normal)
        assertEquals(SlowInternetNoticePhase.SLOW, stays.phase)
        assertEquals(slow, stays.delay)
    }

    @Test
    fun aLevelChangeWhileShownChangesTheNumbers() {
        val shown = observed(0 to slow, 30 to slow)
        val deeper = shown.at(40, slower)
        assertEquals(SlowInternetNoticePhase.SLOW, deeper.phase)
        assertEquals(slower, deeper.delay)
        // the phase began when the notice appeared, not at the change
        assertEquals(30_000L, deeper.phaseSinceMillis)
        assertTrue(slowInternetNoticeChanged(shown, deeper))
    }

    @Test
    fun aSlowLevelDuringBackToNormalShowsAgainAtOnce() {
        // slow nearly the whole minute before: still over the threshold
        val recovered = observed(0 to slow, 30 to slow, 89 to normal, 90 to normal)
        assertEquals(SlowInternetNoticePhase.RECOVERED, recovered.phase)
        val again = recovered.at(91, slower)
        assertEquals(SlowInternetNoticePhase.SLOW, again.phase)
        assertEquals(slower, again.delay)
    }

    @Test
    fun aSlowLevelDuringBackToNormalWaitsForTheThreshold() {
        // the slow time before has mostly slid out: back to normal runs out
        // first, then the new slow time shows once it reaches the threshold
        val recovered = observed(0 to slow, 30 to slow, 40 to normal, 90 to normal)
        assertEquals(SlowInternetNoticePhase.RECOVERED, recovered.phase)
        val slowAgain = recovered.at(91, slow)
        assertEquals(SlowInternetNoticePhase.RECOVERED, slowAgain.phase)
        assertEquals(93.0, slowAgain.nextCheckAtSeconds(91))
        assertEquals(SlowInternetNoticePhase.NONE, slowAgain.at(93, slow).phase)
    }

    @Test
    fun aConnectionThatEndsStartsOver() {
        val shown = observed(0 to slow, 30 to slow)
        val ended = shown.at(31, slow, active = false)
        assertEquals(SlowInternetNoticePhase.NONE, ended.phase)
        assertEquals(TransportDelayUi.None, ended.delay)
        assertNull(ended.slowSinceMillis)
        assertTrue(ended.slowSpans.isEmpty())
        // the next connection is judged on its own
        val next = ended.at(40, slow)
        assertEquals(SlowInternetNoticePhase.NONE, next.phase)
        assertEquals(70.0, next.nextCheckAtSeconds(40))
    }

    @Test
    fun whileShownTheNumbersAreReadAgainEveryThirtySeconds() {
        val shown = observed(0 to slow, 30 to slow)
        assertEquals(60.0, shown.nextCheckAtSeconds(30))
        assertEquals(75.0, shown.at(45, slow).nextCheckAtSeconds(45))
    }

    @Test
    fun nothingIsScheduledWhileTheLevelIsZero() {
        assertNull(SlowInternetNotice().nextCheckAtSeconds(0))
        assertNull(observed(0 to normal, 10 to normal).nextCheckAtSeconds(10))
    }

    @Test
    fun onlyAPhaseChangeOrNewNumbersWhileShownChangeTheNotification() {
        val fresh = SlowInternetNotice()
        // a slow reading too brief to show
        val brief = fresh.at(0, slow)
        assertFalse(slowInternetNoticeChanged(fresh, brief))
        val shown = brief.at(30, slow)
        assertTrue(slowInternetNoticeChanged(brief, shown))
        // the same numbers read again
        assertFalse(slowInternetNoticeChanged(shown, shown.at(60, slow)))
        assertTrue(slowInternetNoticeChanged(shown, shown.at(60, slow.copy(downQueueDelayMillis = 900))))
        // the minimum stay with the level at 0 keeps what it says
        assertFalse(slowInternetNoticeChanged(shown, shown.at(61, normal)))
    }

    @Test
    fun theDwellUsesTheContractsDefaults() {
        assertEquals(60_000L, SLOW_INTERNET_NOTICE_WINDOW_MILLIS)
        assertEquals(30_000L, SLOW_INTERNET_NOTICE_ENTER_MILLIS)
        assertEquals(60_000L, SLOW_INTERNET_NOTICE_MIN_SHOW_MILLIS)
        assertEquals(3_000L, SLOW_INTERNET_RECOVERED_MILLIS)
    }
}
