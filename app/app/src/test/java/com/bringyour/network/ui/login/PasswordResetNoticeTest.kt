package com.bringyour.network.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The password reset screens ignored the server's answer and said the reset
 * link was sent after any request that did not fail outright. A send error in
 * the result must never read as sent.
 */
class PasswordResetNoticeTest {

    @Test
    fun `failed reset link send is not reported as sent`() {
        val sendError = VerifySendError(
            code = "verify_send_failed",
            message = "The password reset code could not be sent. Please try again.",
            retryAfterSeconds = 0,
        )
        assertEquals(VerifySendNotice.SendFailed, passwordResetNotice(false, sendError))
    }

    @Test
    fun `rate limited reset link send is not reported as sent`() {
        val sendError = VerifySendError(
            code = "verify_rate_limited",
            message = "Too many recent sign-in attempts. Please try again in 5 minutes.",
            retryAfterSeconds = 300,
        )
        assertEquals(VerifySendNotice.RateLimited(5), passwordResetNotice(false, sendError))
    }

    @Test
    fun `unknown code shows the server message and no error is sent`() {
        assertEquals(
            VerifySendNotice.ServerMessage("New reason."),
            passwordResetNotice(false, VerifySendError("reset_new_reason", "New reason.", 0)),
        )
        assertEquals(VerifySendNotice.SendFailed, passwordResetNotice(true, null))
        assertEquals(VerifySendNotice.Sent, passwordResetNotice(false, null))
    }
}
