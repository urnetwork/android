package com.bringyour.network.ui.shared.viewmodels

import com.bringyour.network.ui.shared.models.ProvideControlMode

/**
 * What the app does after a balance fetch compares the server's Pro status
 * with the jwt's `pro` claim.
 *
 * Changing Pro status, in either direction, never changes the provide control
 * mode. That is the user's choice; an earlier reset to Never on the upgrade
 * silently stopped paying users from earning.
 */
data class ProStatusSync(
    /** the jwt's `pro` claim is stale and the token must be refreshed */
    val refreshToken: Boolean,
    /** the provide control mode to keep after the sync */
    val provideControlMode: ProvideControlMode,
) {
    companion object {
        fun plan(
            serverIsPro: Boolean,
            jwtIsPro: Boolean,
            provideControlMode: ProvideControlMode,
        ): ProStatusSync {
            return ProStatusSync(
                refreshToken = serverIsPro != jwtIsPro,
                provideControlMode = provideControlMode,
            )
        }
    }
}
