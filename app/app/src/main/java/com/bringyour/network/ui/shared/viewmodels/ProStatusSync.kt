package com.bringyour.network.ui.shared.viewmodels

import com.bringyour.network.ui.shared.models.ProvideControlMode

/**
 * What the app does after a balance fetch reports the server's Pro status.
 *
 * Changing Pro status, in either direction, never changes the provide control
 * mode. That is the user's choice; an earlier reset to Never on the upgrade
 * silently stopped paying users from earning.
 *
 * Whether to refresh the jwt is the sdk's decision (SubscriptionProTracker,
 * JwtManager.proTracker): one refresh per stored jwt. This used to refresh
 * whenever the stored jwt's `pro` claim disagreed with the server, which
 * refreshed on every poll: the refresh renews the client token and leaves the
 * stored jwt, and its claim, as it was.
 */
data class ProStatusSync(
    /** the provide control mode to keep after the sync */
    val provideControlMode: ProvideControlMode,
) {
    companion object {
        fun plan(provideControlMode: ProvideControlMode): ProStatusSync {
            return ProStatusSync(
                provideControlMode = provideControlMode,
            )
        }
    }
}
