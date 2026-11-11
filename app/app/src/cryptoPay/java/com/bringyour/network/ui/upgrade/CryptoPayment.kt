package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.account.GuestAccount
import com.bringyour.network.ui.account.PurchaseRefusal
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.sdk.Sdk
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Locale

/**
 * The payment method row and the crypto invoice as plain values and rules
 * (mmm/ZCASH-CONTRACT.md §6.1, §6.2; the coin tabs, mmm/BTCPAY.md §17.6), so
 * the unit tests run without the native sdk. The sdk owns what is decided
 * once for every app -- the coin selector's tabs, their order, labels, marks
 * and availability (CryptoPaymentTabs), the tab a method sits in
 * (CryptoPaymentTabId), the invoice state machine
 * (CryptoInvoiceViewController), the payment uri check (CheckCryptoPaymentUri)
 * and the QR code's text (CryptoPaymentQrText), the events' store
 * (CryptoAssetMarket), the crypto term notice (CryptoTermNoticeDue) -- and
 * CryptoPayViewModel hands its answers to these rules. Only the sdk's string
 * constants are read here, which the compiler inlines.
 */
object CryptoPayment {

    /**
     * With no tab the row is not shown at all: the sdk's CryptoPaymentTabs
     * lists none when no coin sells what the surface sells, and the surface
     * looks as it did.
     */
    fun showsRow(tabs: List<CryptoPayTab>): Boolean = tabs.isNotEmpty()

    fun isCoin(method: String): Boolean = method != Sdk.PaymentMethodCard

    /** Whether [asset] is a coin of [tabs] that can be paid with now. */
    fun isAvailable(asset: String, tabs: List<CryptoPayTab>): Boolean =
        tabs.any { tab -> tab.coins.any { it.asset == asset && it.available } }

    /**
     * The chosen method: the one remembered on this device while it can be
     * paid with, else the card ("" when none was remembered). A coin that is
     * greyed out ("Temporarily unavailable") cannot be the chosen one.
     */
    fun chosenMethod(remembered: String?, tabs: List<CryptoPayTab>): String =
        remembered?.takeIf { isCoin(it) && isAvailable(it, tabs) } ?: Sdk.PaymentMethodCard

    /**
     * What a tap on [tab] chooses: the [chosen] coin while it sits in the tab,
     * else the tab's first coin that can be paid with now (TAO first in "More
     * coins"); null for a tab none of whose coins can be, which cannot be
     * chosen.
     */
    fun tabChoice(tab: CryptoPayTab, chosen: String): String? =
        tab.coins.firstOrNull { it.asset == chosen && it.available }?.asset
            ?: tab.coins.firstOrNull { it.available }?.asset

    /** What a coin buys on a plan surface: the welcome offer while it is active, else the yearly plan. */
    fun coinPlan(offerActive: Boolean): String =
        if (offerActive) Sdk.PlanYearlyOnboarding else Sdk.PlanYearly

    /**
     * The plan selected with [method]: kept while the method sells it, else
     * yearly ("Monthly is card only"). [allowsPlan] is the sdk's
     * PaymentMethodAllowsPlan in the app.
     */
    fun planFor(
        method: String,
        selected: PlanType,
        allowsPlan: (method: String, plan: String) -> Boolean,
    ): PlanType {
        val plan = if (selected == PlanType.YEARLY) Sdk.PlanYearly else Sdk.PlanMonthly
        return if (allowsPlan(method, plan)) selected else PlanType.YEARLY
    }

    /**
     * What a refused invoice start leads to (CONTRACT §6.1): an unavailable
     * coin says so under the row and stays selectable for a retry; a guest
     * network adds a sign-in method first, as every purchase of a guest does
     * (GuestAccount.purchaseRefusal); anything else is the surface's error
     * line, worded the way a refused card checkout is (CheckoutRefusal). A
     * transport failure has no code, only words.
     */
    fun startRefusal(code: String, words: String): CryptoStartRefusal = when {
        code == Sdk.PurchaseErrorCodeAssetUnavailable -> CryptoStartRefusal.Unavailable
        GuestAccount.purchaseRefusal(code) == PurchaseRefusal.AddSignInMethod -> CryptoStartRefusal.AddSignInMethod
        else -> CryptoStartRefusal.Refused(code = code, words = words)
    }

    /**
     * The product a coin's purchase events name: one year of Pro, by the
     * coin's ticker (the sdk's CryptoAssetTicker): pro_yearly_usdc,
     * pro_yearly_zec, pro_yearly_xmr, pro_yearly_tao, pro_yearly_btc,
     * pro_yearly_eth, pro_yearly_sol. Null without a ticker (not a coin). The
     * events' store is the coin's market (CryptoPayViewModel.eventStore).
     */
    fun eventProduct(ticker: String): String? =
        ticker.trim().takeIf { it.isNotEmpty() }?.let { "pro_yearly_" + it.lowercase(Locale.ROOT) }

    /**
     * The plan an offer or purchase event names. The events know yearly and
     * monthly only (the server refuses any other); the welcome offer a coin
     * buys (`yearly_onboarding`) is the yearly plan.
     */
    fun eventPlan(plan: String): String =
        if (plan == Sdk.PlanMonthly) Sdk.PlanMonthly else Sdk.PlanYearly

    /** "29:59": the quote's countdown as m:ss (CONTRACT §5 crypto_invoice_expires_in). */
    fun countdown(secondsLeft: Long): String {
        val seconds = secondsLeft.coerceAtLeast(0L)
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L)
    }

    /** Whether an exact decimal amount is more than zero; a malformed one is not. */
    fun isPositive(amount: String): Boolean =
        amount.trim().toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } ?: false

    /**
     * An RFC 3339 time from the sdk in unix millis (the crypto term's grace
     * end, which the renewal notice prints), or null when there is none or it
     * does not parse.
     */
    fun timeMillis(rfc3339: String?): Long? {
        if (rfc3339.isNullOrBlank()) {
            return null
        }
        return try {
            OffsetDateTime.parse(rfc3339.trim()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Whether nothing has arrived for the invoice, by its status: pending or
     * expired. A resumed invoice has no status until its first poll answers,
     * and money may have arrived for it (the sdk keeps such an invoice).
     */
    fun nothingReceived(snapshot: CryptoInvoiceSnapshot): Boolean =
        snapshot.status == Sdk.CryptoPaymentStatusPending || snapshot.status == Sdk.CryptoPaymentStatusExpired

    /**
     * A saved invoice in progress (phase open, status not unknown): the
     * account's "Payment in progress" entry, and what a coin button shows
     * instead of starting another invoice ([coinTap]).
     */
    fun inProgress(snapshot: CryptoInvoiceSnapshot): Boolean =
        snapshot.phase == Sdk.CryptoInvoicePhaseOpen && snapshot.status != Sdk.CryptoPaymentStatusUnknown

    /**
     * What a coin's button does (CONTRACT §6.2.1, one invoice at a time): with
     * a saved invoice in progress it shows that invoice instead of starting
     * another, which would replace it -- the server would still credit the
     * first, but the app would lose track of it. "Pay another way" is the way
     * out of an invoice nothing was paid to.
     */
    fun coinTap(held: CryptoInvoiceSnapshot): CoinTap =
        if (inProgress(held)) CoinTap.ShowHeld else CoinTap.Start

    /** Whether the invoice was paid: the surface then does what a card purchase does once it is done. */
    fun isPaid(snapshot: CryptoInvoiceSnapshot): Boolean =
        snapshot.phase == Sdk.CryptoInvoicePhasePaid

    /**
     * What the invoice sheet shows for a [snapshot], top to bottom (CONTRACT
     * §6.2, and what each state shows, §6.2.1). [uriPasses] is the sdk's
     * CheckCryptoPaymentUri(asset, uri, address): a uri that fails it is never
     * shown as a QR or opened. The QR code shows the sdk's QR text
     * ([CryptoInvoiceSnapshot.qrText]), and Open in wallet only a uri.
     * [hasPaymentLink] is false for a coin without a payment uri (the sdk's
     * CryptoPaymentUriScheme is empty: TAO), whose QR code holds the address
     * alone and whose amount and address are sent by hand.
     *
     * Only a payable invoice (open, pending, time left) shows "Send exactly"
     * and the amount, the QR, Open in wallet, the memo and the countdown, and
     * the payment request also waits out a requote in flight: the uri carries
     * the full amount, so once part of a payment arrived it would ask for that
     * amount again, and once the quote expired, or while it is refreshed, for
     * a stale one. The address stays for a top-up. A resumed invoice shows
     * nothing to pay until its first status answers. Review shows its copy and
     * nothing to do.
     */
    fun view(snapshot: CryptoInvoiceSnapshot, uriPasses: Boolean, hasPaymentLink: Boolean = true): CryptoInvoiceView {
        val open = snapshot.phase == Sdk.CryptoInvoicePhaseOpen
        val status = snapshot.status
        val checking = open && status.isEmpty()
        val review = open && status == Sdk.CryptoPaymentStatusReview
        val expired = open && (
            status == Sdk.CryptoPaymentStatusExpired ||
                (status == Sdk.CryptoPaymentStatusPending && snapshot.secondsLeft <= 0L)
            )
        // payable: open, pending, and time left on the quote
        val pending = open && status == Sdk.CryptoPaymentStatusPending && !expired
        val seen = open && status == Sdk.CryptoPaymentStatusSeen
        val confirming = open && status == Sdk.CryptoPaymentStatusConfirming
        val paid = isPaid(snapshot)
        val usdc = snapshot.asset == Sdk.CryptoAssetUsdcSolana
        // the coins on Solana, whose invoices may carry a memo for a transfer sent by hand
        val solana = usdc || snapshot.asset == Sdk.CryptoAssetSol
        // what a requote can change: a quote nothing was paid to
        val requotable = open && (status == Sdk.CryptoPaymentStatusPending || status == Sdk.CryptoPaymentStatusExpired)

        val paymentRequest = pending && !snapshot.refreshing
        val address = (pending || seen) && snapshot.address.isNotEmpty()
        val payUri = snapshot.uri.takeIf { paymentRequest && uriPasses && it.isNotEmpty() }
        // the code never pays anything but what the screen shows: the uri that
        // passed the check, or the address itself (a coin without a payment uri)
        val qrText = snapshot.qrText.takeIf {
            paymentRequest && it.isNotEmpty() && (it == snapshot.address || (it == snapshot.uri && payUri != null))
        }

        val steps = when {
            !open && !paid -> emptyList()
            checking || review -> emptyList()
            // USDC reports no seen or confirming step: it is waiting until it is paid
            usdc -> listOf(CryptoInvoiceStep.WAITING, CryptoInvoiceStep.PAID)
            else -> listOf(CryptoInvoiceStep.WAITING, CryptoInvoiceStep.SEEN, CryptoInvoiceStep.CONFIRMING, CryptoInvoiceStep.PAID)
        }
        val atStep = when {
            paid -> CryptoInvoiceStep.PAID
            confirming -> CryptoInvoiceStep.CONFIRMING
            seen -> CryptoInvoiceStep.SEEN
            else -> CryptoInvoiceStep.WAITING
        }

        return CryptoInvoiceView(
            loading = snapshot.phase == Sdk.CryptoInvoicePhaseStarting || checking,
            paymentRequest = paymentRequest,
            payUri = payUri,
            qrText = qrText,
            amount = paymentRequest && snapshot.amount.isNotEmpty(),
            address = address,
            memo = paymentRequest && solana && snapshot.memo.isNotEmpty(),
            noPaymentLink = !hasPaymentLink,
            countdown = if (pending) countdown(snapshot.secondsLeft) else null,
            expired = expired,
            refresh = pending || expired,
            refreshing = snapshot.refreshing,
            steps = steps,
            currentStep = steps.indexOf(atStep).coerceAtLeast(0),
            remaining = if (seen && isPositive(snapshot.remaining)) snapshot.remaining else null,
            review = review,
            unavailable = requotable && snapshot.errorCode == Sdk.PurchaseErrorCodeAssetUnavailable,
            failed = requotable && !snapshot.refreshing && snapshot.errorCode != Sdk.PurchaseErrorCodeAssetUnavailable &&
                (snapshot.errorCode.isNotEmpty() || snapshot.errorMessage.isNotEmpty()),
            wallets = open && !checking && !review,
            zcashExchangeNote = open && !checking && !review && snapshot.asset == Sdk.CryptoAssetZec,
            // only while nothing was received, or for an invoice that failed or
            // the server no longer knows: once money may have arrived another
            // way would pay twice, and Close (which only hides it) is the way out
            payAnotherWay = (open && nothingReceived(snapshot)) ||
                snapshot.phase == Sdk.CryptoInvoicePhaseFailed ||
                snapshot.status == Sdk.CryptoPaymentStatusUnknown,
        )
    }
}

/** What a refused invoice start leads to (CryptoPayment.startRefusal). */
sealed interface CryptoStartRefusal {
    /** asset_unavailable: "Temporarily unavailable" under the row; the coin stays selectable. */
    data object Unavailable : CryptoStartRefusal

    /** guest_sign_in_required: the add-sign-in sheet (GuestAccount). */
    data object AddSignInMethod : CryptoStartRefusal

    /** No device or api to start the invoice on (PurchaseStart.AccountNotReady). */
    data object AccountNotReady : CryptoStartRefusal

    /** Any other refusal: its code ("" for a transport failure) and the words, for CheckoutRefusal. */
    data class Refused(val code: String, val words: String) : CryptoStartRefusal
}

/**
 * The sdk's CryptoInvoiceState as plain values (CONTRACT §4.4), or a test's.
 * Amounts are the server's exact decimal strings; the countdown is the sdk's
 * ([secondsLeft]). [status] is "" for a resumed invoice until its first poll
 * answers.
 */
data class CryptoInvoiceSnapshot(
    val phase: String = Sdk.CryptoInvoicePhaseNone,
    val intentId: String = "",
    val asset: String = "",
    val plan: String = "",
    val address: String = "",
    val amount: String = "",
    val ticker: String = "",
    val uri: String = "",
    /** what the QR code encodes (the sdk's CryptoPaymentQrText): [uri], or for TAO the address alone; "" for no QR code */
    val qrText: String = "",
    val memo: String = "",
    val priceUsd: Double = 0.0,
    val secondsLeft: Long = 0L,
    val status: String = "",
    val reviewReason: String = "",
    val remaining: String = "",
    val confirmations: Long = 0L,
    val confirmationsRequired: Long = 0L,
    val refreshing: Boolean = false,
    val errorCode: String = "",
    val errorMessage: String = "",
)

/** What a coin's button does (CryptoPayment.coinTap). */
enum class CoinTap {
    /** start an invoice for the chosen coin */
    Start,

    /** show the saved invoice in progress instead */
    ShowHeld,
}

/** The invoice's status steps: waiting → payment seen → confirming n of N → paid. */
enum class CryptoInvoiceStep {
    WAITING,
    SEEN,
    CONFIRMING,
    PAID,
}

/** What the invoice sheet shows (CryptoPayment.view). */
data class CryptoInvoiceView(
    /** the start is in flight, or a resumed invoice waits for its first status: a progress indicator */
    val loading: Boolean,
    /** the quote is current and nothing has arrived: the QR, Open in wallet and "Send exactly" */
    val paymentRequest: Boolean,
    /** the uri to open in a wallet (Open in wallet); null when it failed the check, the coin has none (TAO), or there is no request */
    val payUri: String?,
    /** what the QR code shows: the payment uri, or for a coin without one (TAO) the address alone; null when there is no request */
    val qrText: String?,
    /** "Send exactly" and the amount with its ticker, with Copy */
    val amount: Boolean,
    /** "To this address" and the address, with Copy, and the coin's network-only warning beside it */
    val address: Boolean,
    /** USDC and SOL: the memo, with Copy, while payable */
    val memo: Boolean,
    /**
     * a coin without a payment link (TAO): "Send exactly this amount to this
     * address." stands where Open in wallet would, the amount and the address
     * each have their own copy button (Copy amount, Copy address), and a note
     * says the QR code holds the address alone
     */
    val noPaymentLink: Boolean,
    /** "Quote expires in m:ss" while payable, or null */
    val countdown: String?,
    /** the quote expired with nothing received */
    val expired: Boolean,
    /** Refresh quote: offered while nothing has been received */
    val refresh: Boolean,
    /** a requote is in flight */
    val refreshing: Boolean,
    val steps: List<CryptoInvoiceStep>,
    /** the index in [steps] the payment is at */
    val currentStep: Int,
    /** seen with more to send: the exact amount still to send */
    val remaining: String?,
    /** a receipt needs an operator: the review copy and nothing else to do */
    val review: Boolean,
    /** the last requote was refused with asset_unavailable */
    val unavailable: Boolean,
    /** the last requote failed otherwise */
    val failed: Boolean,
    /** "Wallets that work: ..." */
    val wallets: Boolean,
    /** ZEC: the shielded-address note for exchanges */
    val zcashExchangeNote: Boolean,
    /** "Pay another way": while nothing was received, or for a failed or unknown invoice; else only Close */
    val payAnotherWay: Boolean,
)

/**
 * A coin of the payment widget, the sdk's CryptoPaymentCoin as plain values.
 * An unavailable coin stays in its place and cannot be chosen: its label is
 * greyed, with "Temporarily unavailable" beside it, and its mark is never
 * dimmed (Solana's and Bittensor's marks are shown only at full contrast).
 */
data class CryptoPayCoin(
    /** one of the sdk's CryptoAsset* values */
    val asset: String,
    /** in English (the sdk's Label), shown when the app has no string for [labelKey] */
    val label: String,
    /** the label's string id in the shared localizations */
    val labelKey: String,
    /** the coin's mark, one of the sdk's CryptoIconKey* values */
    val iconKey: String,
    /** payable now, for one of the surface's items */
    val available: Boolean,
)

/**
 * A tab of the coin selector, the sdk's CryptoPaymentTab as plain values: a
 * tab of one coin (its id is the coin's asset), or "More coins"
 * (CryptoPaymentTabMoreCoins), which shows its coins' marks as separate marks
 * with space between them and opens a list of its coins.
 */
data class CryptoPayTab(
    val id: String,
    /** in English (the sdk's Label), shown when the app has no string for [labelKey] */
    val label: String,
    val labelKey: String,
    /** the marks the tab shows, in order, each at full contrast */
    val iconKeys: List<String>,
    /** its coins, in order: one, or TAO, Bitcoin, ETH and SOL */
    val coins: List<CryptoPayCoin>,
    /** one of its coins can be paid with now; a tab with none is greyed and cannot be chosen */
    val available: Boolean,
)
