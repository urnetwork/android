package com.bringyour.network.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test

class VerifySendNoticeTest {

    @Test
    fun `rate limited send is not reported as sent`() {
        // the server's answer when the account asked for too many codes
        val sendError = VerifySendError(
            code = "verify_rate_limited",
            message = "Too many recent sign-in attempts. Please try again in 5 minutes.",
            retryAfterSeconds = 300,
        )

        assertEquals(VerifySendNotice.RateLimited(5), VerifySendNotice.from(false, sendError))
    }

    @Test
    fun `failed send is not reported as sent`() {
        val sendError = VerifySendError(
            code = "verify_send_failed",
            message = "The verification code could not be sent.",
            retryAfterSeconds = 0,
        )

        assertEquals(VerifySendNotice.SendFailed, VerifySendNotice.from(false, sendError))
    }

    @Test
    fun `rate limit minutes round up and are at least one`() {
        fun minutes(retryAfterSeconds: Long) = VerifySendNotice.from(
            false,
            VerifySendError("verify_rate_limited", "", retryAfterSeconds),
        )

        assertEquals(VerifySendNotice.RateLimited(1), minutes(1))
        assertEquals(VerifySendNotice.RateLimited(1), minutes(60))
        assertEquals(VerifySendNotice.RateLimited(2), minutes(61))
    }

    @Test
    fun `unknown code or rate limit without retry falls back to the server message`() {
        assertEquals(
            VerifySendNotice.ServerMessage("Try later."),
            VerifySendNotice.from(false, VerifySendError("verify_rate_limited", "Try later.", 0)),
        )
        assertEquals(
            VerifySendNotice.ServerMessage("New reason."),
            VerifySendNotice.from(false, VerifySendError("verify_new_reason", "New reason.", 0)),
        )
        assertEquals(
            VerifySendNotice.SendFailed,
            VerifySendNotice.from(false, VerifySendError("verify_new_reason", "", 0)),
        )
        assertEquals(
            VerifySendNotice.SendFailed,
            VerifySendNotice.from(false, VerifySendError("", "", 0)),
        )
    }

    @Test
    fun `transport error is a failed send and no error is sent`() {
        assertEquals(VerifySendNotice.SendFailed, VerifySendNotice.from(true, null))
        assertEquals(VerifySendNotice.Sent, VerifySendNotice.from(false, null))
    }
}
