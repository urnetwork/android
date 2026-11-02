package com.bringyour.network.ui.upgrade

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Whether this flavor's alert says to disconnect before paying: only where a held
 * tunnel stops the checkout. The web view (github) and the Stripe sheet (the dapp flavors) run in this app, which the tunnel leaves out.
 */
class PlanCheckoutRouteTest {

    @Test
    fun checkoutStaysOutOfTheTunnel() {
        assertFalse(PLAN_CHECKOUT_RIDES_TUNNEL)
    }
}
