package com.bringyour.network.ui.upgrade

import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the crypto invoice sheet shows for each state of the sdk's invoice
 * (mmm/ZCASH-CONTRACT.md §6.2 and §6.2.1). The rule that matters most: the QR code and
 * Open in wallet show only a uri that passed CheckCryptoPaymentUri, and only
 * while the quote is current and nothing has arrived -- the uri asks for the
 * full amount, so after a part payment, an expiry or during a requote it would
 * ask for the wrong one. The addresses and amounts are fixtures, not wallets.
 */
class CryptoInvoiceViewTest {

    private val xmrAddress = "8FixtureMoneroSubaddressForTheAppTestsOnlyNotAWallet"
    private val xmrUri = "monero:$xmrAddress?tx_amount=0.072387&tx_description=URnetwork%20Pro%20yearly"

    // an open Monero invoice, just quoted, nothing received
    private val pending = CryptoInvoiceSnapshot(
        phase = Sdk.CryptoInvoicePhaseOpen,
        intentId = "intent-1",
        asset = Sdk.CryptoAssetXmr,
        plan = Sdk.PlanYearly,
        address = xmrAddress,
        amount = "0.072387",
        ticker = "XMR",
        uri = xmrUri,
        priceUsd = 40.0,
        secondsLeft = 1799,
        status = Sdk.CryptoPaymentStatusPending,
        confirmationsRequired = 1,
    )

    @Test
    fun aCurrentQuoteAsksForThePayment() {
        val view = CryptoPayment.view(pending, uriPasses = true)
        assertFalse(view.loading)
        assertTrue(view.paymentRequest)
        assertEquals(xmrUri, view.payUri)
        assertTrue(view.amount)
        assertTrue(view.address)
        assertFalse(view.memo)
        assertFalse(view.usdcNetworkWarning)
        assertEquals("29:59", view.countdown)
        assertFalse(view.expired)
        assertTrue(view.refresh)
        assertEquals(
            listOf(CryptoInvoiceStep.WAITING, CryptoInvoiceStep.SEEN, CryptoInvoiceStep.CONFIRMING, CryptoInvoiceStep.PAID),
            view.steps,
        )
        assertEquals(0, view.currentStep)
        assertNull(view.remaining)
        assertFalse(view.review)
        assertTrue(view.wallets)
        assertFalse(view.zcashExchangeNote)
        assertTrue(view.payAnotherWay)
    }

    @Test
    fun aUriThatFailsTheCheckIsNeitherShownNorOpened() {
        val view = CryptoPayment.view(pending, uriPasses = false)
        assertNull(view.payUri)
        // the amount and the address still let a wallet pay by hand
        assertTrue(view.paymentRequest)
        assertTrue(view.amount)
        assertTrue(view.address)
    }

    @Test
    fun noUriIsNoQrCode() {
        // the controller blanks a uri that fails the check, or one a requote elsewhere made stale
        val view = CryptoPayment.view(pending.copy(uri = ""), uriPasses = true)
        assertNull(view.payUri)
        assertTrue(view.amount)
        assertTrue(view.address)
    }

    @Test
    fun anExpiredQuoteOffersARefreshAndNothingToPay() {
        val view = CryptoPayment.view(pending.copy(status = Sdk.CryptoPaymentStatusExpired, secondsLeft = 0), uriPasses = true)
        assertTrue(view.expired)
        assertTrue(view.refresh)
        assertFalse(view.paymentRequest)
        assertNull(view.payUri)
        assertFalse(view.amount)
        assertFalse(view.address)
        assertNull(view.countdown)
        assertEquals(0, view.currentStep)
        assertTrue(view.payAnotherWay)
    }

    @Test
    fun aCountdownAtZeroIsExpiredBeforeTheServerSaysSo() {
        val view = CryptoPayment.view(pending.copy(secondsLeft = 0), uriPasses = true)
        assertTrue(view.expired)
        assertTrue(view.refresh)
        assertNull(view.payUri)
        assertFalse(view.amount)
    }

    @Test
    fun aRequoteInFlightHidesTheStaleQuote() {
        val view = CryptoPayment.view(pending.copy(refreshing = true), uriPasses = true)
        // the refresh button stays, disabled while it is in flight
        assertTrue(view.refreshing)
        assertTrue(view.refresh)
        assertFalse(view.paymentRequest)
        assertNull(view.payUri)
        assertFalse(view.amount)
        // the address is the same after a requote; the amount is not
        assertTrue(view.address)
        assertEquals("29:59", view.countdown)
    }

    @Test
    fun aPartPaymentAsksForTheRestToTheSameAddress() {
        val view = CryptoPayment.view(
            pending.copy(status = Sdk.CryptoPaymentStatusSeen, remaining = "0.012000", secondsLeft = 600),
            uriPasses = true,
        )
        // the uri would ask for the full amount again
        assertFalse(view.paymentRequest)
        assertNull(view.payUri)
        assertFalse(view.amount)
        assertTrue(view.address)
        assertEquals("0.012000", view.remaining)
        // the countdown is the payable invoice's
        assertNull(view.countdown)
        // a requote is the server's for nothing received only
        assertFalse(view.refresh)
        assertEquals(1, view.currentStep)
        // money arrived: another way would pay twice
        assertFalse(view.payAnotherWay)
    }

    @Test
    fun seenWithNothingMoreToSendSaysNothingMore() {
        val view = CryptoPayment.view(pending.copy(status = Sdk.CryptoPaymentStatusSeen, remaining = "0"), uriPasses = true)
        assertNull(view.remaining)
    }

    @Test
    fun confirmingShowsTheConfirmationsStep() {
        val view = CryptoPayment.view(
            pending.copy(status = Sdk.CryptoPaymentStatusConfirming, confirmations = 0, secondsLeft = 900),
            uriPasses = true,
        )
        assertEquals(CryptoInvoiceStep.CONFIRMING, view.steps[view.currentStep])
        assertFalse(view.paymentRequest)
        assertFalse(view.address)
        assertNull(view.countdown)
        assertFalse(view.refresh)
        assertFalse(view.payAnotherWay)
        assertTrue(view.wallets)
    }

    @Test
    fun reviewShowsItsCopyAndNothingElseToDo() {
        val view = CryptoPayment.view(
            pending.copy(status = Sdk.CryptoPaymentStatusReview, reviewReason = "underpaid", errorCode = Sdk.PurchaseErrorCodeUnderpaid),
            uriPasses = true,
        )
        assertTrue(view.review)
        assertTrue(view.steps.isEmpty())
        assertFalse(view.paymentRequest)
        assertFalse(view.address)
        assertNull(view.countdown)
        assertFalse(view.refresh)
        assertFalse(view.wallets)
        assertFalse(view.payAnotherWay)
        // a requote's failure says nothing once there is nothing to requote
        assertFalse(view.failed)
        assertFalse(view.unavailable)
    }

    @Test
    fun paidIsTheLastStep() {
        val view = CryptoPayment.view(
            pending.copy(phase = Sdk.CryptoInvoicePhasePaid, status = Sdk.CryptoPaymentStatusPaid, confirmations = 1),
            uriPasses = true,
        )
        assertEquals(CryptoInvoiceStep.PAID, view.steps[view.currentStep])
        assertFalse(view.paymentRequest)
        assertFalse(view.payAnotherWay)
    }

    @Test
    fun usdcHasAMemoTheNetworkWarningAndTwoSteps() {
        val address = "FixtureSolanaRecipientForTheAppTests"
        val usdc = CryptoInvoiceSnapshot(
            phase = Sdk.CryptoInvoicePhaseOpen,
            intentId = "intent-2",
            asset = Sdk.CryptoAssetUsdcSolana,
            plan = Sdk.PlanYearlyOnboarding,
            address = address,
            amount = "29.990417",
            ticker = "USDC",
            uri = "solana:$address?amount=29.990417",
            memo = "FixtureReference",
            secondsLeft = 3600,
            status = Sdk.CryptoPaymentStatusPending,
        )
        val view = CryptoPayment.view(usdc, uriPasses = true)
        assertTrue(view.memo)
        assertTrue(view.usdcNetworkWarning)
        assertEquals(listOf(CryptoInvoiceStep.WAITING, CryptoInvoiceStep.PAID), view.steps)
        assertEquals("60:00", view.countdown)
        assertFalse(view.zcashExchangeNote)

        val paid = CryptoPayment.view(usdc.copy(phase = Sdk.CryptoInvoicePhasePaid, status = Sdk.CryptoPaymentStatusPaid), uriPasses = true)
        assertEquals(1, paid.currentStep)
        // USDC without a memo shows none
        assertFalse(CryptoPayment.view(usdc.copy(memo = ""), uriPasses = true).memo)
        // the memo is the payable invoice's
        assertFalse(CryptoPayment.view(usdc.copy(status = Sdk.CryptoPaymentStatusExpired, secondsLeft = 0), uriPasses = true).memo)
        assertFalse(CryptoPayment.view(usdc.copy(status = ""), uriPasses = true).memo)
    }

    @Test
    fun zcashWarnsAboutExchanges() {
        val view = CryptoPayment.view(pending.copy(asset = Sdk.CryptoAssetZec, ticker = "ZEC"), uriPasses = true)
        assertTrue(view.zcashExchangeNote)
        assertTrue(view.wallets)
        assertFalse(CryptoPayment.view(pending, uriPasses = true).zcashExchangeNote)
    }

    @Test
    fun aStartInFlightIsLoadingAndOffersOnlyClose() {
        val starting = CryptoInvoiceSnapshot(phase = Sdk.CryptoInvoicePhaseStarting, asset = Sdk.CryptoAssetXmr, plan = Sdk.PlanYearly, ticker = "XMR")
        val view = CryptoPayment.view(starting, uriPasses = false)
        assertTrue(view.loading)
        assertFalse(view.payAnotherWay)
        assertFalse(view.refresh)
        assertTrue(view.steps.isEmpty())
        assertFalse(view.paymentRequest)
        assertFalse(view.wallets)
    }

    // CONTRACT §6.2.1: only while nothing was received, or for an invoice that
    // failed or the server no longer knows; otherwise only Close, which hides it
    @Test
    fun payAnotherWayOnlyWhileNothingWasReceivedOrTheInvoiceFailed() {
        assertTrue(CryptoPayment.view(pending, uriPasses = true).payAnotherWay)
        assertTrue(CryptoPayment.view(pending.copy(status = Sdk.CryptoPaymentStatusExpired, secondsLeft = 0), uriPasses = true).payAnotherWay)
        assertTrue(CryptoPayment.view(pending.copy(secondsLeft = 0), uriPasses = true).payAnotherWay)
        assertTrue(CryptoPayment.view(pending.copy(refreshing = true), uriPasses = true).payAnotherWay)
        assertTrue(CryptoPayment.view(pending.copy(phase = Sdk.CryptoInvoicePhaseFailed, errorCode = "rate_limited"), uriPasses = true).payAnotherWay)
        assertTrue(
            CryptoPayment.view(pending.copy(phase = Sdk.CryptoInvoicePhaseFailed, status = Sdk.CryptoPaymentStatusUnknown), uriPasses = true).payAnotherWay
        )

        for (status in listOf("", Sdk.CryptoPaymentStatusSeen, Sdk.CryptoPaymentStatusConfirming, Sdk.CryptoPaymentStatusReview)) {
            assertFalse(status, CryptoPayment.view(pending.copy(status = status), uriPasses = true).payAnotherWay)
        }
        assertFalse(CryptoPayment.view(pending.copy(phase = Sdk.CryptoInvoicePhasePaid, status = Sdk.CryptoPaymentStatusPaid), uriPasses = true).payAnotherWay)
    }

    // CONTRACT §6.2.1: shown only while nothing was received
    @Test
    fun refreshQuoteOnlyWhileNothingWasReceived() {
        assertTrue(CryptoPayment.view(pending, uriPasses = true).refresh)
        assertTrue(CryptoPayment.view(pending.copy(status = Sdk.CryptoPaymentStatusExpired, secondsLeft = 0), uriPasses = true).refresh)
        for (status in listOf("", Sdk.CryptoPaymentStatusSeen, Sdk.CryptoPaymentStatusConfirming, Sdk.CryptoPaymentStatusReview)) {
            assertFalse(status, CryptoPayment.view(pending.copy(status = status), uriPasses = true).refresh)
        }
        assertFalse(CryptoPayment.view(CryptoInvoiceSnapshot(phase = Sdk.CryptoInvoicePhaseStarting), uriPasses = true).refresh)
    }

    // CONTRACT §6.2.1: a coin's button shows the saved invoice in progress instead of starting another
    @Test
    fun oneInvoiceAtATime() {
        assertEquals(CoinTap.Start, CryptoPayment.coinTap(CryptoInvoiceSnapshot()))
        for (status in listOf(
            Sdk.CryptoPaymentStatusPending,
            Sdk.CryptoPaymentStatusExpired,
            Sdk.CryptoPaymentStatusSeen,
            Sdk.CryptoPaymentStatusConfirming,
            Sdk.CryptoPaymentStatusReview,
            // resumed and not polled yet: money may be on its way
            "",
        )) {
            assertEquals(status, CoinTap.ShowHeld, CryptoPayment.coinTap(pending.copy(status = status)))
        }
        // nothing in progress: the server no longer knows it, it failed, or it was paid
        assertEquals(CoinTap.Start, CryptoPayment.coinTap(pending.copy(status = Sdk.CryptoPaymentStatusUnknown)))
        assertEquals(CoinTap.Start, CryptoPayment.coinTap(pending.copy(phase = Sdk.CryptoInvoicePhaseFailed, status = Sdk.CryptoPaymentStatusUnknown)))
        assertEquals(CoinTap.Start, CryptoPayment.coinTap(pending.copy(phase = Sdk.CryptoInvoicePhasePaid, status = Sdk.CryptoPaymentStatusPaid)))
    }

    @Test
    fun aResumedInvoiceShowsNothingToPayUntilItsStatusAnswers() {
        // the sdk resumes a saved invoice with its quote but no status: money may have arrived
        val resumed = pending.copy(status = "")
        val view = CryptoPayment.view(resumed, uriPasses = true)
        assertTrue(view.loading)
        assertFalse(view.paymentRequest)
        assertNull(view.payUri)
        assertFalse(view.address)
        assertFalse(view.refresh)
        assertFalse(view.payAnotherWay)
        assertTrue(view.steps.isEmpty())
        assertFalse(view.wallets)
    }

    @Test
    fun aRefusedRequoteIsSaid() {
        val unavailable = CryptoPayment.view(pending.copy(errorCode = Sdk.PurchaseErrorCodeAssetUnavailable, errorMessage = "rates diverge"), uriPasses = true)
        assertTrue(unavailable.unavailable)
        assertFalse(unavailable.failed)

        val failed = CryptoPayment.view(pending.copy(errorCode = "", errorMessage = "i/o timeout"), uriPasses = true)
        assertFalse(failed.unavailable)
        assertTrue(failed.failed)

        // an expired quote's refused refresh too
        assertTrue(
            CryptoPayment.view(
                pending.copy(status = Sdk.CryptoPaymentStatusExpired, errorCode = Sdk.PurchaseErrorCodeAssetUnavailable),
                uriPasses = true,
            ).unavailable
        )

        assertFalse(CryptoPayment.view(pending, uriPasses = true).failed)
        // while the next requote is in flight the last failure is not said
        assertFalse(CryptoPayment.view(pending.copy(refreshing = true, errorMessage = "i/o timeout"), uriPasses = true).failed)
    }

    @Test
    fun aCloseWithNothingReceivedIsACancelledPurchase() {
        assertTrue(CryptoPayment.nothingReceived(pending))
        assertTrue(CryptoPayment.nothingReceived(pending.copy(status = Sdk.CryptoPaymentStatusExpired)))
        assertFalse(CryptoPayment.nothingReceived(pending.copy(status = Sdk.CryptoPaymentStatusSeen)))
        assertFalse(CryptoPayment.nothingReceived(pending.copy(status = Sdk.CryptoPaymentStatusConfirming)))
        assertFalse(CryptoPayment.nothingReceived(pending.copy(status = Sdk.CryptoPaymentStatusReview)))
        // resumed, not polled yet
        assertFalse(CryptoPayment.nothingReceived(pending.copy(status = "")))
    }

    @Test
    fun anOpenInvoiceIsAPaymentInProgress() {
        assertTrue(CryptoPayment.inProgress(pending))
        assertTrue(CryptoPayment.inProgress(pending.copy(status = "")))
        assertTrue(CryptoPayment.inProgress(pending.copy(status = Sdk.CryptoPaymentStatusConfirming)))
        assertFalse(CryptoPayment.inProgress(CryptoInvoiceSnapshot()))
        assertFalse(CryptoPayment.inProgress(pending.copy(phase = Sdk.CryptoInvoicePhaseStarting)))
        assertFalse(CryptoPayment.inProgress(pending.copy(phase = Sdk.CryptoInvoicePhasePaid)))
        assertFalse(CryptoPayment.inProgress(pending.copy(phase = Sdk.CryptoInvoicePhaseFailed)))
        assertFalse(CryptoPayment.inProgress(pending.copy(status = Sdk.CryptoPaymentStatusUnknown)))
    }

    @Test
    fun paidIsThePaidPhase() {
        assertTrue(CryptoPayment.isPaid(pending.copy(phase = Sdk.CryptoInvoicePhasePaid, status = Sdk.CryptoPaymentStatusPaid)))
        assertFalse(CryptoPayment.isPaid(pending))
        assertFalse(CryptoPayment.isPaid(CryptoInvoiceSnapshot()))
    }

    @Test
    fun thePhasesAreTheSdks() {
        assertEquals("none", Sdk.CryptoInvoicePhaseNone)
        assertEquals("starting", Sdk.CryptoInvoicePhaseStarting)
        assertEquals("open", Sdk.CryptoInvoicePhaseOpen)
        assertEquals("paid", Sdk.CryptoInvoicePhasePaid)
        assertEquals("failed", Sdk.CryptoInvoicePhaseFailed)
        assertEquals(Sdk.CryptoInvoicePhaseNone, CryptoInvoiceSnapshot().phase)
    }
}
