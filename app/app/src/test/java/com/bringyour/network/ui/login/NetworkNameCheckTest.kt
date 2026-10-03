package com.bringyour.network.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkNameCheckTest {

    /** a manual clock: scheduled actions run only when the test advances it */
    private class FakeScheduler {
        private class Scheduled(val dueMillis: Long, val action: () -> Unit) {
            var cancelled = false
        }

        var nowMillis = 0L
            private set
        private val scheduled = mutableListOf<Scheduled>()

        fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit {
            val s = Scheduled(nowMillis + delayMillis, action)
            scheduled.add(s)
            return { s.cancelled = true }
        }

        fun advance(millis: Long) {
            val endMillis = nowMillis + millis
            while (true) {
                val next = scheduled
                    .filter { !it.cancelled && it.dueMillis <= endMillis }
                    .minByOrNull { it.dueMillis } ?: break
                scheduled.remove(next)
                nowMillis = next.dueMillis
                next.action()
            }
            nowMillis = endMillis
        }
    }

    /** an availability api whose answers the test delivers by hand */
    private class FakeCheck {
        val pending = mutableListOf<Pair<String, (Boolean?) -> Unit>>()

        fun check(networkName: String, onResult: (Boolean?) -> Unit) {
            pending.add(networkName to onResult)
        }

        fun answer(available: Boolean?) {
            val (_, onResult) = pending.removeAt(0)
            onResult(available)
        }
    }

    private val scheduler = FakeScheduler()
    private val api = FakeCheck()
    private val nameCheck = NetworkNameCheck(api::check, scheduler::schedule) {}

    @Test
    fun aFailedCheckIsNotShownAsAnUnavailableName() {
        nameCheck.validate("mynetwork")
        api.answer(null)

        assertEquals(NetworkNameCheckState.FAILED, nameCheck.state)
    }

    @Test
    fun aFailedCheckKeepsCreateUsable() {
        nameCheck.validate("mynetwork")
        api.answer(null)

        assertTrue(nameCheck.state.allowsCreate)
    }

    @Test
    fun aFailedCheckIsRetriedAndAnAvailableAnswerIsApplied() {
        nameCheck.validate("mynetwork")
        api.answer(null)
        assertTrue(api.pending.isEmpty())

        scheduler.advance(NetworkNameCheck.RETRY_DELAY_MILLIS)
        assertEquals(listOf("mynetwork"), api.pending.map { it.first })
        assertEquals(NetworkNameCheckState.CHECKING, nameCheck.state)

        api.answer(true)
        assertEquals(NetworkNameCheckState.AVAILABLE, nameCheck.state)
    }

    @Test
    fun retriesAreBounded() {
        nameCheck.validate("mynetwork")
        api.answer(null)
        repeat(NetworkNameCheck.MAX_RETRY_COUNT) {
            scheduler.advance(NetworkNameCheck.RETRY_DELAY_MILLIS)
            assertEquals(1, api.pending.size)
            api.answer(null)
        }
        scheduler.advance(10 * NetworkNameCheck.RETRY_DELAY_MILLIS)

        assertTrue(api.pending.isEmpty())
        assertEquals(NetworkNameCheckState.FAILED, nameCheck.state)
        assertTrue(nameCheck.state.allowsCreate)
    }

    @Test
    fun aCheckThatNeverAnswersFails() {
        nameCheck.validate("mynetwork")
        scheduler.advance(NetworkNameCheck.CHECK_TIMEOUT_MILLIS)

        assertEquals(NetworkNameCheckState.FAILED, nameCheck.state)
        assertTrue(nameCheck.state.allowsCreate)
    }

    @Test
    fun anUnavailableNameBlocksCreateAndIsNotRetried() {
        nameCheck.validate("mynetwork")
        api.answer(false)
        scheduler.advance(10 * NetworkNameCheck.RETRY_DELAY_MILLIS)

        assertEquals(NetworkNameCheckState.UNAVAILABLE, nameCheck.state)
        assertFalse(nameCheck.state.allowsCreate)
        assertTrue(api.pending.isEmpty())
    }

    @Test
    fun anEditCancelsThePendingRetryAndAStaleAnswerIsIgnored() {
        nameCheck.validate("mynetwork")
        api.answer(null)
        nameCheck.validate("othernetwork")
        // the answer for the edited name is still outstanding
        scheduler.advance(NetworkNameCheck.RETRY_DELAY_MILLIS)
        assertEquals(listOf("othernetwork"), api.pending.map { it.first })

        nameCheck.validate("thirdnetwork")
        api.answer(true) // the stale answer for "othernetwork"
        assertEquals(NetworkNameCheckState.CHECKING, nameCheck.state)
        api.answer(false)
        assertEquals(NetworkNameCheckState.UNAVAILABLE, nameCheck.state)
    }

    @Test
    fun anErroredResultIsFailedNotUnavailable() {
        assertEquals(NetworkNameCheckState.FAILED, NetworkNameCheck.resultState(null))
        assertEquals(NetworkNameCheckState.UNAVAILABLE, NetworkNameCheck.resultState(false))
        assertEquals(NetworkNameCheckState.AVAILABLE, NetworkNameCheck.resultState(true))
    }

    @Test
    fun shortNamesAreJudgedWithoutAnOnlineCheck() {
        nameCheck.validate("abcde")
        assertEquals(NetworkNameCheckState.TOO_SHORT, nameCheck.state)
        nameCheck.validate("")
        assertEquals(NetworkNameCheckState.EMPTY, nameCheck.state)
        assertTrue(api.pending.isEmpty())
        assertFalse(nameCheck.state.allowsCreate)
    }
}
