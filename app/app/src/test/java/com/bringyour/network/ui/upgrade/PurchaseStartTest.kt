package com.bringyour.network.ui.upgrade

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * UPGRADE.md N5: the Stripe sheet and pay page purchasers used to `return` silently
 * when the active network space had no api, so the purchase button did nothing.
 */
class PurchaseStartTest {

    @Test
    fun missingApiIsSurfacedNotSwallowed() {
        assertEquals(
            PurchaseStart.AccountNotReady,
            purchaseStartFor(apiAvailable = false, inProgress = false)
        )
    }

    @Test
    fun aTapWhileInProgressStaysANoOp() {
        assertEquals(
            PurchaseStart.InProgress,
            purchaseStartFor(apiAvailable = true, inProgress = true)
        )
        assertEquals(
            PurchaseStart.InProgress,
            purchaseStartFor(apiAvailable = false, inProgress = true)
        )
    }

    @Test
    fun readyAccountStartsThePurchase() {
        assertEquals(
            PurchaseStart.Start,
            purchaseStartFor(apiAvailable = true, inProgress = false)
        )
    }
}
