package com.bringyour.network.ui.upgrade

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether this flavor's alert says to disconnect before paying: only where a held
 * tunnel stops the checkout. Play Billing runs in the Play Store and Play services, which the tunnel captures.
 */
class PlanCheckoutRouteTest {

    @Test
    fun checkoutRidesTheTunnel() {
        assertTrue(PLAN_CHECKOUT_RIDES_TUNNEL)
    }
}
