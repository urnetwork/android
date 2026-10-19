package com.bringyour.network.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Change password only resets a password sign-in (inbox 7969, urnetwork/android#485).
class PasswordResetUserAuthTest {

    @Test
    fun googleOnlyAccountHasNoPasswordToChange() {
        // the legacy user_auth holds the Google email, but there is no password sign-in:
        // the reset link arrived and the new password changed nothing
        assertNull(passwordResetUserAuth(listOf("google"), "google", "a@example.com"))
        assertNull(passwordResetUserAuth(listOf("apple"), "apple", "a@example.com"))
    }

    @Test
    fun passwordAccountChangesItsPassword() {
        assertEquals("a@example.com", passwordResetUserAuth(listOf("email"), "password", "a@example.com"))
        assertEquals("a@example.com", passwordResetUserAuth(listOf("google", "email"), "google", "a@example.com"))
        assertEquals("15555550100", passwordResetUserAuth(listOf("phone"), "password", "15555550100"))
    }

    @Test
    fun oldServerWithoutAuthTypesFallsBackToAuthType() {
        assertEquals("a@example.com", passwordResetUserAuth(emptyList(), "password", "a@example.com"))
        assertNull(passwordResetUserAuth(emptyList(), "google", "a@example.com"))
    }

    @Test
    fun noAddressNoReset() {
        assertNull(passwordResetUserAuth(listOf("email"), "solana", null))
        assertNull(passwordResetUserAuth(listOf("email"), "solana", ""))
    }
}
