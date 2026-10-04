package com.bringyour.network.ui.account

import com.bringyour.network.ui.components.LoginMode

/**
 * Legacy guest networks (findings A4 and D8 in server/UPGRADE.md).
 *
 * The server no longer creates guest networks, but a network created as a guest
 * before July still has no login method at all. Such a network can hold a plan
 * and a balance, and nothing can sign back in to it. Two signals mark it:
 * - the jwt's `guest_mode` claim, which every token refresh clears, so a
 *   refreshed guest no longer carries it
 * - the server's subscription-balance `guest`, read from the live auth methods,
 *   so it stays true after a refresh and turns false once a login method exists
 *
 * A guest is never sold a plan. It converts in place instead: a sign-in method
 * is added to its own network with AddAuth, then the jwt is re-signed. Nothing
 * logs out, because logging out to create a new account is what stranded the
 * guest's paid balance on a network with no way back.
 */
object GuestAccount {

    /**
     * Either signal marks a guest. A still-set claim keeps reading as a guest
     * until the jwt is re-signed (an older server sends no `guest`).
     */
    fun isGuest(guestModeClaim: Boolean, serverGuest: Boolean): Boolean {
        return guestModeClaim || serverGuest
    }

    /**
     * No parsed jwt is not signed in, which the account screens render as the
     * guest mode as well.
     */
    fun loginMode(jwtParsed: Boolean, isGuest: Boolean): LoginMode {
        return if (jwtParsed && !isGuest) LoginMode.Authenticated else LoginMode.Guest
    }

    /**
     * Where an upgrade or "create an account" entry leads.
     */
    fun upgradeEntry(isGuest: Boolean): UpgradeEntry {
        return if (isGuest) UpgradeEntry.AddSignInMethod else UpgradeEntry.Checkout
    }

    /**
     * Whether `isGuest` is settled. A guest claim marks a guest on its own; a
     * jwt without the claim (every refreshed jwt) says nothing until the
     * server's `guest` has loaded once, so until then a refreshed guest reads
     * as an account.
     */
    fun guestStatusKnown(guestModeClaim: Boolean, serverGuestLoaded: Boolean): Boolean {
        return guestModeClaim || serverGuestLoaded
    }

    /**
     * The intro funnel sells a plan, and opens by itself at startup, before the
     * first balance load. It waits for the guest status: shown on the claim
     * alone, a refreshed guest saw the funnel until the balance arrived. Hidden
     * (not marked prompted) for a guest, so it can show once the network has a
     * login.
     */
    fun introFunnel(
        isPro: Boolean,
        isGuest: Boolean,
        guestStatusKnown: Boolean,
        allowPrompt: Boolean,
    ): IntroFunnel {
        return when {
            isPro -> IntroFunnel.Hide
            !guestStatusKnown || upgradeEntry(isGuest) == UpgradeEntry.AddSignInMethod -> IntroFunnel.Hide
            allowPrompt -> IntroFunnel.Show
            else -> IntroFunnel.Unchanged
        }
    }
}

enum class IntroFunnel {
    // not displayed, and not marked prompted
    Hide,
    // displayed and marked prompted
    Show,
    // left as it is (not due to prompt again)
    Unchanged,
}

enum class UpgradeEntry {
    Checkout,
    // add a sign-in method to the current network first; no checkout
    AddSignInMethod,
}

/**
 * The session a conversion runs on. `logout` is part of the contract so it is
 * explicit that the conversion never calls it.
 */
interface GuestConversionSession<A> {
    fun addAuth(args: A, onSuccess: () -> Unit, onError: (String) -> Unit)
    fun refreshJwt()
    fun logout()
}

/**
 * Converts a guest network in place: adds the sign-in method to the current
 * network, then re-signs the jwt for the same network so `guest_mode` clears.
 */
class GuestConversion<A>(private val session: GuestConversionSession<A>) {

    fun addSignInMethod(args: A, onAdded: () -> Unit, onError: (String) -> Unit) {
        session.addAuth(
            args,
            {
                session.refreshJwt()
                onAdded()
            },
            onError
        )
    }
}
