package com.bringyour.network.ui.shared.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UPGRADE.md N2: the success overlay must launch on the server's confirmation, not
 * when the Stripe sheet or pay page reports success. The fake source stands in for
 * the SDK SubscriptionBalanceViewController (whose confirmation rule is tested in
 * the sdk, subscription_balance_view_controller_test.go).
 */
class PurchaseConfirmationTest {

    private class FakeSource : PurchaseConfirmation.Source {
        val calls = mutableListOf<String>()
        var pro = false

        override fun start() { calls += "start" }
        override fun setForeground(foreground: Boolean) { calls += "foreground:$foreground" }
        override fun startPurchaseConfirmation() { calls += "startPurchaseConfirmation" }
        override fun clearPurchaseConfirmation() { calls += "clear" }
        override fun isPro(): Boolean = pro
        override fun close() { calls += "close" }
    }

    private class Harness(apiAvailable: Boolean = true) {
        val sources = mutableListOf<FakeSource>()
        var onState: ((String) -> Unit)? = null
        val confirmed = mutableListOf<Boolean>()
        var gaveUp = 0

        val confirmation = PurchaseConfirmation(
            openSource = { callback ->
                if (!apiAvailable) {
                    null
                } else {
                    onState = callback
                    FakeSource().also { sources += it }
                }
            },
            onConfirmed = { confirmed += it },
            onGaveUp = { gaveUp += 1 },
        )
    }

    @Test
    fun paymentSuccessAloneDoesNotAnnounceTheUpgrade() {
        val h = Harness()
        h.confirmation.prepare()

        // the sheet reports Completed
        assertTrue(h.confirmation.confirm())

        assertEquals(emptyList<Boolean>(), h.confirmed)
        assertTrue(h.confirmation.waiting)
        // still waiting while the server has not answered
        h.onState!!("waiting_for_confirmation")
        assertEquals(emptyList<Boolean>(), h.confirmed)
    }

    @Test
    fun serverConfirmationAnnouncesTheUpgradeOnce() {
        val h = Harness()
        h.confirmation.prepare()
        h.confirmation.confirm()
        h.sources.single().pro = true

        h.onState!!("confirmed")
        h.onState!!("confirmed")

        assertEquals(listOf(true), h.confirmed)
        assertFalse(h.confirmation.waiting)
        assertEquals(
            listOf("foreground:true", "start", "startPurchaseConfirmation", "clear", "close"),
            h.sources.single().calls
        )
    }

    @Test
    fun budgetRunningOutShowsTheDelayedNoticeNotTheOverlay() {
        val h = Harness()
        h.confirmation.confirm()

        h.onState!!("confirmation_gave_up")

        assertEquals(emptyList<Boolean>(), h.confirmed)
        assertEquals(1, h.gaveUp)
        assertFalse(h.confirmation.waiting)
    }

    @Test
    fun baselineSourceIsOpenedBeforeThePaymentAndReused() {
        val h = Harness()
        h.confirmation.prepare()
        h.confirmation.confirm()

        assertEquals(1, h.sources.size)
        assertEquals(listOf("foreground:true", "start", "startPurchaseConfirmation"), h.sources.single().calls)
    }

    @Test
    fun cancelledPurchaseClosesTheSourceWithoutAnnouncing() {
        val h = Harness()
        h.confirmation.prepare()
        h.confirmation.cancel()

        h.onState!!("confirmed")

        assertEquals(emptyList<Boolean>(), h.confirmed)
        assertEquals(listOf("foreground:true", "start", "close"), h.sources.single().calls)
    }

    @Test
    fun backgroundPausesTheSource() {
        val h = Harness()
        h.confirmation.setForeground(false)
        h.confirmation.confirm()
        h.confirmation.setForeground(true)

        assertEquals(
            listOf("foreground:false", "start", "startPurchaseConfirmation", "foreground:true"),
            h.sources.single().calls
        )
    }

    @Test
    fun withoutAnApiTheCallerFallsBack() {
        val h = Harness(apiAvailable = false)
        assertFalse(h.confirmation.confirm())
        assertFalse(h.confirmation.waiting)
    }
}
