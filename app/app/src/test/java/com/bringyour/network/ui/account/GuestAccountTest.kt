package com.bringyour.network.ui.account

import com.bringyour.network.ui.components.LoginMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Legacy guest networks (UPGRADE.md A4 and D8). A guest has no login method; the
 * jwt's guest_mode claim is cleared by every token refresh, so the server's
 * subscription-balance `guest` must still mark it. A guest never reaches
 * checkout, and its conversion adds a sign-in method to the same network
 * instead of signing out.
 */
class GuestAccountTest {

    private class FakeSession : GuestConversionSession<String> {
        val calls = mutableListOf<String>()
        var addAuthError: String? = null

        override fun addAuth(args: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
            calls += "addAuth:$args"
            addAuthError?.let(onError) ?: onSuccess()
        }

        override fun refreshJwt() { calls += "refreshJwt" }
        override fun logout() { calls += "logout" }
    }

    @Test
    fun refreshedLegacyGuestIsStillAGuest() {
        // the refreshed jwt parses, guest_mode is false, the server reports a guest
        val isGuest = GuestAccount.isGuest(guestModeClaim = false, serverGuest = true)
        assertTrue("refreshed legacy guest not detected", isGuest)
        assertEquals(LoginMode.Guest, GuestAccount.loginMode(jwtParsed = true, isGuest = isGuest))
        assertEquals(UpgradeEntry.AddSignInMethod, GuestAccount.upgradeEntry(isGuest))
    }

    @Test
    fun guestClaimStillCountsWithoutServerGuest() {
        val isGuest = GuestAccount.isGuest(guestModeClaim = true, serverGuest = false)
        assertTrue(isGuest)
        assertEquals(LoginMode.Guest, GuestAccount.loginMode(jwtParsed = true, isGuest = isGuest))
    }

    @Test
    fun networkWithALoginMethodReachesCheckout() {
        val isGuest = GuestAccount.isGuest(guestModeClaim = false, serverGuest = false)
        assertFalse(isGuest)
        assertEquals(LoginMode.Authenticated, GuestAccount.loginMode(jwtParsed = true, isGuest = isGuest))
        assertEquals(UpgradeEntry.Checkout, GuestAccount.upgradeEntry(isGuest))
    }

    @Test
    fun noJwtIsNotSignedIn() {
        assertEquals(LoginMode.Guest, GuestAccount.loginMode(jwtParsed = false, isGuest = false))
    }

    @Test
    fun conversionAddsASignInMethodAndNeverLogsOut() {
        val session = FakeSession()
        var added = false

        GuestConversion(session).addSignInMethod("user@example.com", { added = true }, { })

        assertTrue(added)
        // re-signed for the same network; never logged out to another one
        assertEquals(listOf("addAuth:user@example.com", "refreshJwt"), session.calls)
    }

    @Test
    fun failedAddAuthStaysOnTheGuestNetwork() {
        val session = FakeSession().apply { addAuthError = "Password must have at least 12 characters" }
        var error: String? = null

        GuestConversion(session).addSignInMethod("user@example.com", { }, { error = it })

        assertEquals("Password must have at least 12 characters", error)
        assertEquals(listOf("addAuth:user@example.com"), session.calls)
    }

    /**
     * The intro funnel opens by itself at startup, before the first balance
     * load. A refreshed guest has no guest_mode claim, so until the server's
     * `guest` loads it reads as an account: the funnel must wait for the guest
     * status instead of selling it a plan in that window.
     */
    @Test
    fun introFunnelWaitsForTheGuestStatus() {
        // a refreshed guest before the first balance load: no claim, nothing loaded
        val known = GuestAccount.guestStatusKnown(guestModeClaim = false, serverGuestLoaded = false)
        assertFalse("a jwt without the claim does not settle the guest status", known)
        assertEquals(
            "intro funnel shown before the guest status is known",
            IntroFunnel.Hide,
            GuestAccount.introFunnel(isPro = false, isGuest = false, guestStatusKnown = known, allowPrompt = true),
        )
        // the balance loaded: a guest stays hidden, an account is prompted
        assertEquals(
            IntroFunnel.Hide,
            GuestAccount.introFunnel(isPro = false, isGuest = true, guestStatusKnown = true, allowPrompt = true),
        )
        assertEquals(
            IntroFunnel.Show,
            GuestAccount.introFunnel(isPro = false, isGuest = false, guestStatusKnown = true, allowPrompt = true),
        )
        assertEquals(
            IntroFunnel.Unchanged,
            GuestAccount.introFunnel(isPro = false, isGuest = false, guestStatusKnown = true, allowPrompt = false),
        )
        assertEquals(
            IntroFunnel.Hide,
            GuestAccount.introFunnel(isPro = true, isGuest = false, guestStatusKnown = false, allowPrompt = true),
        )
    }

    @Test
    fun guestClaimSettlesTheGuestStatus() {
        assertTrue(GuestAccount.guestStatusKnown(guestModeClaim = true, serverGuestLoaded = false))
        assertTrue(GuestAccount.guestStatusKnown(guestModeClaim = false, serverGuestLoaded = true))
    }

    /**
     * An instant account is the server's seedphrase path (terms and no login
     * method). The server dropped guest_mode from network create with that
     * path and ignores it, so the app no longer sends guest_mode = true. The
     * sdk args are a native gomobile class, so the source is read as text.
     */
    @Test
    fun instantAccountIsNotCreatedAsAGuest() {
        val source = java.io.File(
            "src/main/java/com/bringyour/network/ui/login/CreateNetworkInstantViewModel.kt"
        ).readText()
        assertTrue("networkCreate call not found", source.contains("api.networkCreate(args)"))
        assertFalse("the instant account still sets guestMode = true", source.contains("guestMode = true"))
    }
}
