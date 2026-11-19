package com.bringyour.network.ui.upgrade

import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the crypto invoice sheet shows for each state of the sdk's invoice
 * (mmm/ZCASH-CONTRACT.md §6.2 and §6.2.1, and Bitcoin's confirmation grace,
 * mmm/BTCPAY.md §16.8). The rule that matters most: the QR code and
 * Open in wallet show only a uri that passed CheckCryptoPaymentUri, and only
 * while the quote is current and nothing has arrived -- the uri asks for the
 * full amount, so after a part payment, an expiry or during a requote it would
 * ask for the wrong one. The addresses and amounts are fixtures, not wallets.
 * The status steps, the memo rule and whether a payment sent within an ended
 * quote may still confirm are the sdk's (CryptoPaymentSteps,
 * CryptoPaymentShowsMemo, AwaitingConfirmation), carried on the snapshot as
 * the sdk hands them out: these fixtures spell them the way the sdk's vectors
 * and tests do.
 */
class CryptoInvoiceViewTest {

    private val xmrAddress = "8FixtureMoneroSubaddressForTheAppTestsOnlyNotAWallet"
    private val xmrUri = "monero:$xmrAddress?tx_amount=0.072387&tx_description=URnetwork%20Pro%20yearly"

    private val done = Sdk.CryptoPaymentStepDone
    private val current = Sdk.CryptoPaymentStepCurrent
    private val upcoming = Sdk.CryptoPaymentStepUpcoming

    /** The sdk's steps of a coin other than USDC, with the confirming step's counts. */
    private fun steps(
        pendingProgress: String,
        seenProgress: String,
        confirmingProgress: String,
        paidProgress: String,
        confirmations: Long = 0L,
        confirmationsRequired: Long = 1L,
    ): List<CryptoInvoiceStep> = listOf(
        CryptoInvoiceStep(Sdk.CryptoPaymentStatusPending, pendingProgress),
        CryptoInvoiceStep(Sdk.CryptoPaymentStatusSeen, seenProgress),
        CryptoInvoiceStep(Sdk.CryptoPaymentStatusConfirming, confirmingProgress, confirmations, confirmationsRequired),
        CryptoInvoiceStep(Sdk.CryptoPaymentStatusPaid, paidProgress),
    )

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
        // the sdk's CryptoPaymentQrText: the uri, which passes its check
        qrText = xmrUri,
        priceUsd = 40.0,
        secondsLeft = 1799,
        status = Sdk.CryptoPaymentStatusPending,
        confirmationsRequired = 1,
        steps = steps(current, upcoming, upcoming, upcoming),
    )

    // an open Bitcoin invoice, just quoted, nothing received: the server
    // credits a payment sent within its quote at the quoted price while it is
    // mined within 3 hours of the quote's end (mmm/BTCPAY.md §16.8)
    private val btcAddress = "bc1qFixtureBitcoinAddressForTheAppTestsOnlyNotAWallet"
    private val btcUri = "bitcoin:$btcAddress?amount=0.00054622"
    private val btcPending = CryptoInvoiceSnapshot(
        phase = Sdk.CryptoInvoicePhaseOpen,
        intentId = "intent-5",
        asset = Sdk.CryptoAssetBtc,
        plan = Sdk.PlanYearly,
        address = btcAddress,
        amount = "0.00054622",
        ticker = "BTC",
        uri = btcUri,
        qrText = btcUri,
        priceUsd = 40.0,
        secondsLeft = 1799,
        status = Sdk.CryptoPaymentStatusPending,
        confirmationsRequired = 1,
        steps = steps(current, upcoming, upcoming, upcoming),
    )

    // its countdown ended, the server still says pending, and the sdk says a
    // payment sent within the quote may still confirm (AwaitingConfirmation)
    private val btcAwaiting = btcPending.copy(secondsLeft = 0, awaitingConfirmation = true)

    @Test
    fun aCurrentQuoteAsksForThePayment() {
        val view = CryptoPayment.view(pending, uriPasses = true)
        assertFalse(view.loading)
        assertTrue(view.paymentRequest)
        assertEquals(xmrUri, view.payUri)
        assertEquals(xmrUri, view.qrText)
        assertFalse(view.noPaymentLink)
        assertTrue(view.amount)
        assertTrue(view.address)
        assertFalse(view.memo)
        assertEquals("29:59", view.countdown)
        assertFalse(view.expired)
        assertTrue(view.refresh)
        // the sdk's steps, as it hands them out
        assertEquals(pending.steps, view.steps)
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
        assertNull(view.qrText)
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
        // a QR text left over from the uri is not drawn either
        assertNull(view.qrText)
        assertTrue(view.amount)
        assertTrue(view.address)
    }

    @Test
    fun theQrCodeShowsOnlyTheCheckedUriOrTheAddressOnScreen() {
        // a QR text that is neither (the sdk never hands one out) shows no code
        assertNull(CryptoPayment.view(pending.copy(qrText = "monero:SomewhereElse"), uriPasses = true).qrText)
        assertNull(CryptoPayment.view(pending.copy(qrText = ""), uriPasses = true).qrText)
        // no code once nothing is payable
        assertNull(CryptoPayment.view(pending.copy(refreshing = true), uriPasses = true).qrText)
        assertNull(CryptoPayment.view(pending.copy(status = Sdk.CryptoPaymentStatusSeen, remaining = "0.01"), uriPasses = true).qrText)
    }

    // TAO has no payment uri (mmm/BTCPAY.md §17.6): a QR code of the address
    // alone, no Open in wallet, and the amount and the address to send by hand
    @Test
    fun aTaoInvoiceShowsTheAddressAsItsQrCodeAndNoOpenInWallet() {
        val address = "5FixtureBittensorColdkeyForTheAppTestsOnlyNotAWallet"
        val tao = CryptoInvoiceSnapshot(
            phase = Sdk.CryptoInvoicePhaseOpen,
            intentId = "intent-3",
            asset = Sdk.CryptoAssetTao,
            plan = Sdk.PlanYearly,
            address = address,
            amount = "0.123457",
            ticker = "TAO",
            uri = "",
            // the sdk's CryptoPaymentQrText for a coin without a payment uri
            qrText = address,
            priceUsd = 40.0,
            secondsLeft = 1799,
            status = Sdk.CryptoPaymentStatusPending,
            confirmationsRequired = 1,
        )
        val view = CryptoPayment.view(tao, uriPasses = false, hasPaymentLink = false)
        assertTrue(view.paymentRequest)
        assertNull(view.payUri)
        assertEquals(address, view.qrText)
        assertTrue(view.noPaymentLink)
        assertTrue(view.amount)
        assertTrue(view.address)
        assertFalse(view.memo)
        assertEquals("29:59", view.countdown)
        assertTrue(view.wallets)

        // the quote ran out: no amount, and no code of the address either
        val expired = CryptoPayment.view(tao.copy(status = Sdk.CryptoPaymentStatusExpired, secondsLeft = 0), uriPasses = false, hasPaymentLink = false)
        assertNull(expired.qrText)
        assertFalse(expired.amount)
        // part of it arrived: the address stays for the rest
        val seen = CryptoPayment.view(tao.copy(status = Sdk.CryptoPaymentStatusSeen, remaining = "0.023457"), uriPasses = false, hasPaymentLink = false)
        assertNull(seen.qrText)
        assertTrue(seen.address)
        assertEquals("0.023457", seen.remaining)
    }

    // the server sends "memo": "" for SOL (its own deposit address names the
    // invoice), and the sdk shows a memo only when there is one: the sheet used
    // to show a memo row for SOL as for USDC
    @Test
    fun aSolInvoiceShowsNoMemo() {
        val address = "FixtureSolanaRecipientForTheAppTests"
        val uri = "solana:$address?amount=0.204517"
        val sol = CryptoInvoiceSnapshot(
            phase = Sdk.CryptoInvoicePhaseOpen,
            intentId = "intent-4",
            asset = Sdk.CryptoAssetSol,
            plan = Sdk.PlanYearly,
            address = address,
            amount = "0.204517",
            ticker = "SOL",
            uri = uri,
            qrText = uri,
            memo = "",
            // the sdk's CryptoPaymentShowsMemo(sol, "")
            showsMemo = false,
            secondsLeft = 1800,
            status = Sdk.CryptoPaymentStatusPending,
        )
        val view = CryptoPayment.view(sol, uriPasses = true)
        assertFalse(view.memo)
        assertFalse(view.noPaymentLink)
        assertEquals(uri, view.payUri)
        assertEquals(uri, view.qrText)
        // the sheet follows the sdk's rule, not the coin: no memo row unless the sdk shows one
        assertFalse(CryptoPayment.view(pending.copy(memo = "stray"), uriPasses = true).memo)
        assertTrue(CryptoPayment.view(pending.copy(memo = "FixtureReference", showsMemo = true), uriPasses = true).memo)
    }

    // SOL and TAO, which the server sees only once final, usually go from
    // pending straight to paid: the sdk draws every step they skipped done
    @Test
    fun aPaymentThatSkippedStepsShowsThemDone() {
        val paid = pending.copy(
            asset = Sdk.CryptoAssetSol,
            ticker = "SOL",
            phase = Sdk.CryptoInvoicePhasePaid,
            status = Sdk.CryptoPaymentStatusPaid,
            confirmations = 1,
            steps = steps(done, done, done, done, confirmations = 1L),
        )
        val view = CryptoPayment.view(paid, uriPasses = true)
        assertEquals(paid.steps, view.steps)
        assertTrue(view.steps.all { it.progress == Sdk.CryptoPaymentStepDone })
        assertFalse(view.paymentRequest)
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
        // nothing received: the sdk puts an expired quote at the first step
        assertEquals(pending.steps, view.steps)
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

    // Bitcoin's confirmation grace (mmm/BTCPAY.md §16.8): the countdown is
    // still the time to send by, and while the invoice is payable the coin's
    // confirmation note goes under it -- a payment can take up to 3 hours to
    // confirm, and one sent in time is credited at the quoted price
    @Test
    fun aBitcoinQuoteSaysHowLongItsPaymentCanTakeToConfirm() {
        val view = CryptoPayment.view(btcPending, uriPasses = true)
        assertTrue(view.paymentRequest)
        assertEquals(btcUri, view.payUri)
        assertEquals(btcUri, view.qrText)
        assertTrue(view.amount)
        assertTrue(view.address)
        assertEquals("29:59", view.countdown)
        // where the note goes; the sdk's lines say which coin has one (Bitcoin alone)
        assertTrue(view.confirmationNote)
        assertFalse(view.awaitingConfirmation)
        assertFalse(view.expired)
        assertTrue(view.refresh)
        assertTrue(view.payAnotherWay)
        // with the countdown, a requote in flight too
        assertTrue(CryptoPayment.view(btcPending.copy(refreshing = true), uriPasses = true).confirmationNote)
        // and only with it: not once the quote ended, nor once money may have arrived
        assertFalse(CryptoPayment.view(btcAwaiting, uriPasses = true).confirmationNote)
        assertFalse(CryptoPayment.view(btcPending.copy(status = Sdk.CryptoPaymentStatusExpired, secondsLeft = 0), uriPasses = true).confirmationNote)
        for (status in listOf("", Sdk.CryptoPaymentStatusSeen, Sdk.CryptoPaymentStatusConfirming, Sdk.CryptoPaymentStatusReview)) {
            assertFalse(status, CryptoPayment.view(btcPending.copy(status = status), uriPasses = true).confirmationNote)
        }
        assertFalse(
            CryptoPayment.view(btcPending.copy(phase = Sdk.CryptoInvoicePhasePaid, status = Sdk.CryptoPaymentStatusPaid), uriPasses = true).confirmationNote
        )
    }

    // Bitcoin's confirmation grace: once the quote ended with nothing received,
    // a payment sent within it may still confirm and is credited at its price,
    // and the server refuses a requote until the grace is over. While the sdk
    // says so the invoice is neither payable nor expired: the coin's awaiting
    // line stands in place of the expired copy, with no Refresh quote
    @Test
    fun anEndedBitcoinQuoteAwaitsConfirmationWithoutARefresh() {
        val view = CryptoPayment.view(btcAwaiting, uriPasses = true)
        assertTrue(view.awaitingConfirmation)
        assertFalse(view.expired)
        assertFalse(view.refresh)
        // nothing to pay, as once a quote expired
        assertFalse(view.paymentRequest)
        assertNull(view.payUri)
        assertNull(view.qrText)
        assertFalse(view.amount)
        assertFalse(view.address)
        assertFalse(view.memo)
        assertNull(view.countdown)
        assertFalse(view.confirmationNote)
        // nothing received: the status steps and the ways out are a pending invoice's
        assertEquals(btcPending.steps, view.steps)
        assertNull(view.remaining)
        assertFalse(view.loading)
        assertFalse(view.review)
        assertTrue(view.wallets)
        assertTrue(view.payAnotherWay)
    }

    // nothing can be requoted in the grace, so the last requote's failure is
    // not said there, where the awaiting line says what to do: one that
    // failed before the quote ended, or reached the server after it and was
    // refused (invalid_request)
    @Test
    fun aRequoteFailureIsNotSaidWhileAwaitingConfirmation() {
        for (failed in listOf(
            btcAwaiting.copy(errorCode = "invalid_request"),
            btcAwaiting.copy(errorCode = "", errorMessage = "i/o timeout"),
            btcAwaiting.copy(errorCode = Sdk.PurchaseErrorCodeAssetUnavailable),
        )) {
            val view = CryptoPayment.view(failed, uriPasses = true)
            assertFalse(view.failed)
            assertFalse(view.unavailable)
        }
        // past the grace it is said, as for every expired quote
        assertTrue(CryptoPayment.view(btcPending.copy(secondsLeft = 0, errorCode = "invalid_request"), uriPasses = true).failed)
    }

    // past the grace the sdk no longer says so (on the quote's own clock, or
    // once the server says expired): an ended Bitcoin quote is an expired one,
    // as every coin's, with the expired copy and Refresh quote. A coin without
    // a grace never awaits
    @Test
    fun pastTheGraceAnEndedBitcoinQuoteIsExpired() {
        for (ended in listOf(
            btcPending.copy(secondsLeft = 0),
            btcPending.copy(status = Sdk.CryptoPaymentStatusExpired, secondsLeft = 0),
        )) {
            val view = CryptoPayment.view(ended, uriPasses = true)
            assertTrue(view.expired)
            assertFalse(view.awaitingConfirmation)
            assertTrue(view.refresh)
            assertFalse(view.paymentRequest)
            assertNull(view.countdown)
            assertTrue(view.payAnotherWay)
        }
        assertFalse(CryptoPayment.view(pending.copy(secondsLeft = 0), uriPasses = true).awaitingConfirmation)
    }

    // the sdk says AwaitingConfirmation only of an open, pending invoice whose
    // countdown ended, and the view takes it there alone: time left on the
    // quote stays payable, and a payment mined in the grace shows as one
    @Test
    fun awaitingConfirmationOnlyStandsForAnEndedQuote() {
        val payable = CryptoPayment.view(btcPending.copy(awaitingConfirmation = true), uriPasses = true)
        assertFalse(payable.awaitingConfirmation)
        assertTrue(payable.paymentRequest)
        assertEquals("29:59", payable.countdown)

        val confirmingSteps = steps(done, done, current, upcoming)
        val confirming = CryptoPayment.view(
            btcAwaiting.copy(status = Sdk.CryptoPaymentStatusConfirming, steps = confirmingSteps),
            uriPasses = true,
        )
        assertFalse(confirming.awaitingConfirmation)
        assertFalse(confirming.expired)
        assertEquals(confirmingSteps, confirming.steps)
        // money arrived: another way would pay twice
        assertFalse(confirming.payAnotherWay)
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
        val seenSteps = steps(done, current, upcoming, upcoming)
        val view = CryptoPayment.view(
            pending.copy(status = Sdk.CryptoPaymentStatusSeen, remaining = "0.012000", secondsLeft = 600, steps = seenSteps),
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
        assertEquals(seenSteps, view.steps)
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
        // a whole payment skips seen, which the sdk draws done
        val confirmingSteps = steps(done, done, current, upcoming)
        val view = CryptoPayment.view(
            pending.copy(status = Sdk.CryptoPaymentStatusConfirming, confirmations = 0, secondsLeft = 900, steps = confirmingSteps),
            uriPasses = true,
        )
        assertEquals(confirmingSteps, view.steps)
        assertEquals(Sdk.CryptoPaymentStatusConfirming, view.steps.single { it.progress == Sdk.CryptoPaymentStepCurrent }.status)
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
            // the sdk lists no steps in review; none show even if a stale list came along
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
        val paidSteps = steps(done, done, done, done, confirmations = 1L)
        val view = CryptoPayment.view(
            pending.copy(phase = Sdk.CryptoInvoicePhasePaid, status = Sdk.CryptoPaymentStatusPaid, confirmations = 1, steps = paidSteps),
            uriPasses = true,
        )
        // once paid every step is done, the last one too
        assertEquals(paidSteps, view.steps)
        assertEquals(Sdk.CryptoPaymentStatusPaid, view.steps.last().status)
        assertFalse(view.paymentRequest)
        assertFalse(view.payAnotherWay)
    }

    @Test
    fun usdcHasAMemoTheAddressForItsNetworkWarningAndTwoSteps() {
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
            qrText = "solana:$address?amount=29.990417",
            memo = "FixtureReference",
            // the sdk's CryptoPaymentShowsMemo(usdc_solana, "FixtureReference")
            showsMemo = true,
            secondsLeft = 3600,
            status = Sdk.CryptoPaymentStatusPending,
            // the sdk's two USDC steps
            steps = listOf(
                CryptoInvoiceStep(Sdk.CryptoPaymentStatusPending, current),
                CryptoInvoiceStep(Sdk.CryptoPaymentStatusPaid, upcoming),
            ),
        )
        val view = CryptoPayment.view(usdc, uriPasses = true)
        assertTrue(view.memo)
        // the network-only warning shows with the address (the sdk's network warning)
        assertTrue(view.address)
        assertEquals(usdc.steps, view.steps)
        assertEquals("60:00", view.countdown)
        assertFalse(view.zcashExchangeNote)

        val paidSteps = listOf(
            CryptoInvoiceStep(Sdk.CryptoPaymentStatusPending, done),
            CryptoInvoiceStep(Sdk.CryptoPaymentStatusPaid, done),
        )
        val paid = CryptoPayment.view(
            usdc.copy(phase = Sdk.CryptoInvoicePhasePaid, status = Sdk.CryptoPaymentStatusPaid, steps = paidSteps),
            uriPasses = true,
        )
        assertEquals(paidSteps, paid.steps)
        // USDC without a memo shows none (the sdk says so)
        assertFalse(CryptoPayment.view(usdc.copy(memo = "", showsMemo = false), uriPasses = true).memo)
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
