package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPollingSessionTest {
    @Test
    fun inactiveSessionDoesNotResume() {
        val session = ForegroundPollingSession(nowMillis = { 10L })

        assertEquals(ForegroundPollingResume.INACTIVE, session.resume())
        assertFalse(session.active)
    }

    @Test
    fun foregroundSessionResumesBeforeDeadline() {
        var now = 10L
        val session = ForegroundPollingSession(nowMillis = { now })
        session.start(20L)
        now = 29L

        assertEquals(ForegroundPollingResume.ACTIVE, session.resume())
        assertTrue(session.active)
    }

    @Test
    fun backgroundPausePreservesConfirmationState() {
        var now = 10L
        val session = ForegroundPollingSession(nowMillis = { now })
        session.start(20L)

        session.pause()
        now = 15L

        assertEquals(ForegroundPollingResume.ACTIVE, session.resume())
        assertTrue(session.active)
    }

    @Test
    fun foregroundResumeReportsExpiredSession() {
        var now = 10L
        val session = ForegroundPollingSession(nowMillis = { now })
        session.start(20L)
        session.pause()
        now = 30L

        assertEquals(ForegroundPollingResume.EXPIRED, session.resume())
        assertTrue(session.active)
    }

    @Test
    fun stopClearsLogicalSession() {
        val session = ForegroundPollingSession(nowMillis = { 10L })
        session.start(20L)

        session.stop()

        assertEquals(ForegroundPollingResume.INACTIVE, session.resume())
        assertFalse(session.active)
    }

    @Test
    fun deadlineAdditionSaturatesWithoutOverflow() {
        val session = ForegroundPollingSession(nowMillis = { Long.MAX_VALUE - 1L })
        session.start(10L)

        assertEquals(ForegroundPollingResume.ACTIVE, session.resume())
        assertFalse(session.hasExpired())
    }
}
