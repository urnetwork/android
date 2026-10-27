package com.bringyour.network.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Change password only resets a password sign-in, at that sign-in's address
// (inbox 7962, 7969, urnetwork/android#485).
class PasswordResetUserAuthTest {

    @Test
    fun googleOnlyAccountHasNoPasswordToChange() {
        // the legacy user_auth holds the Google email, but there is no password sign-in:
        // the reset link arrived and the new password changed nothing
        assertNull(passwordResetUserAuth(listOf("google"), "google", "a@example.com", emptyList()))
        assertNull(passwordResetUserAuth(listOf("apple"), "apple", "a@example.com", emptyList()))
    }

    @Test
    fun passwordAccountChangesItsPassword() {
        val email = listOf(PasswordSignIn(userAuth = "a@example.com", authType = "email"))
        assertEquals("a@example.com", passwordResetUserAuth(listOf("email"), "password", "a@example.com", email))
        val phone = listOf(PasswordSignIn(userAuth = "15555550100", authType = "phone"))
        assertEquals("15555550100", passwordResetUserAuth(listOf("phone"), "password", "15555550100", phone))
    }

    // An account created with a wallet, a seed phrase or as a guest that added an email and
    // password later has no user_auth: it was never offered Change password.
    @Test
    fun accountThatAddedAnEmailChangesItsPasswordAtThatEmail() {
        val added = listOf(PasswordSignIn(userAuth = "added@example.com", authType = "email"))
        assertEquals("added@example.com", passwordResetUserAuth(listOf("email", "solana"), "solana", null, added))
        assertEquals("added@example.com", passwordResetUserAuth(listOf("seedphrase", "email"), "seedphrase", "", added))
        assertEquals("added@example.com", passwordResetUserAuth(listOf("email"), "guest", null, added))
    }

    // A Google or Apple account that added an email and password is offered the reset at the
    // added email, the sign-in whose password changes, not at the provider's email.
    @Test
    fun ssoAccountThatAddedAnEmailChangesItsPasswordAtTheAddedEmail() {
        val added = listOf(PasswordSignIn(userAuth = "added@example.com", authType = "email"))
        assertEquals(
            "added@example.com",
            passwordResetUserAuth(listOf("google", "email"), "google", "provider@example.com", added),
        )
    }

    @Test
    fun emailIsPreferredOverPhone() {
        val both = listOf(
            PasswordSignIn(userAuth = "15555550100", authType = "phone"),
            PasswordSignIn(userAuth = "a@example.com", authType = "email"),
        )
        assertEquals("a@example.com", passwordResetUserAuth(listOf("phone", "email"), "password", "15555550100", both))
    }

    // An older sdk or server sends no user_auths: the identity the account was created with is
    // used when the account has a password sign-in.
    @Test
    fun withoutUserAuthsTheCreatedIdentityIsUsed() {
        assertEquals("a@example.com", passwordResetUserAuth(listOf("email"), "password", "a@example.com", emptyList()))
        assertEquals("a@example.com", passwordResetUserAuth(listOf("google", "email"), "google", "a@example.com", emptyList()))
    }

    @Test
    fun oldServerWithoutAuthTypesFallsBackToAuthType() {
        assertEquals("a@example.com", passwordResetUserAuth(emptyList(), "password", "a@example.com", emptyList()))
        assertNull(passwordResetUserAuth(emptyList(), "google", "a@example.com", emptyList()))
    }

    @Test
    fun noAddressNoReset() {
        assertNull(passwordResetUserAuth(listOf("email"), "solana", null, emptyList()))
        assertNull(passwordResetUserAuth(listOf("email"), "solana", "", emptyList()))
        assertNull(
            passwordResetUserAuth(
                listOf("email"),
                "solana",
                null,
                listOf(PasswordSignIn(userAuth = "", authType = "email")),
            ),
        )
    }
}
