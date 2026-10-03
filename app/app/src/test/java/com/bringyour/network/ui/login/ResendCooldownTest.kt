package com.bringyour.network.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * After the server refused a code for too many attempts, Resend stayed enabled,
 * so the user kept asking for codes the server would refuse, and the "new code
 * in N minutes" notice never changed. Resend must stay disabled until the retry
 * time has passed, and the notice must count down. The clock is a parameter.
 */
class ResendCooldownTest {

    private val start = 1_000_000L

    private val rateLimited = VerifySendError(
        code = "verify_rate_limited",
        message = "Too many attempts.",
        retryAfterSeconds = 300,
    )

    @Test
    fun `resend is disabled until the retry time has passed`() {
        val cooldown = ResendCooldown.after(rateLimited, start)!!

        assertFalse("resend enabled right after the rate limit", cooldown.canResend(start))
        assertFalse("resend enabled 1s before the retry time", cooldown.canResend(start + 299_000))
        assertFalse("resend enabled 1ms before the retry time", cooldown.canResend(start + 299_999))
        assertTrue(cooldown.canResend(start + 300_000))
        assertTrue(cooldown.canResend(start + 900_000))
    }

    @Test
    fun `notice counts down the minutes left and clears`() {
        val cooldown = ResendCooldown.after(rateLimited, start)!!
        val notice = VerifySendNotice.from(false, rateLimited)

        assertEquals(VerifySendNotice.RateLimited(5), notice.at(cooldown, start))
        assertEquals(VerifySendNotice.RateLimited(5), notice.at(cooldown, start + 59_000))
        assertEquals(VerifySendNotice.RateLimited(4), notice.at(cooldown, start + 60_000))
        assertEquals(VerifySendNotice.RateLimited(1), notice.at(cooldown, start + 299_000))
        assertEquals(VerifySendNotice.Sent, notice.at(cooldown, start + 300_000))
        assertEquals(240L, cooldown.remainingSeconds(start + 60_000))
    }

    @Test
    fun `only a timed rate limit starts a cooldown`() {
        assertNull(ResendCooldown.after(null, start))
        assertNull(ResendCooldown.after(VerifySendError("verify_send_failed", "", 0), start))
        assertNull(ResendCooldown.after(VerifySendError("verify_rate_limited", "Try later.", 0), start))
        // other notices are not changed by the clock
        assertEquals(VerifySendNotice.SendFailed, VerifySendNotice.SendFailed.at(null, start))
    }
}
