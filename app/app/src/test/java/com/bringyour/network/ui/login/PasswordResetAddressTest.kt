package com.bringyour.network.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Forgot password opened with one address, the user corrected it and sent the
 * link: the link went to the corrected address, but the after-send screen got
 * the address the screen opened with, so its Resend mailed the old address.
 * The route encoder is a parameter (`Uri.encode` on device).
 */
class PasswordResetAddressTest {

    private val identity: (String) -> String = { it }

    @Test
    fun `after-send screen gets the edited address`() {
        val address = PasswordResetAddress("old@example.com")
        address.typed = " new@example.com "

        assertEquals("new@example.com", address.userAuth)
        assertEquals("reset-password-after-send/new@example.com", address.afterSendRoute(identity))
    }

    @Test
    fun `after-send screen gets the opened address when it was not edited`() {
        val address = PasswordResetAddress("same@example.com")

        assertEquals("same@example.com", address.userAuth)
        assertEquals("reset-password-after-send/same@example.com", address.afterSendRoute(identity))
    }

    @Test
    fun `after-send route encodes the sent address`() {
        val address = PasswordResetAddress("old@example.com")
        address.typed = "a+b@example.com"

        assertEquals("reset-password-after-send/<a+b@example.com>", address.afterSendRoute { "<$it>" })
    }
}
