package com.bringyour.network.ui.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.analytics.ClientEvents
import com.bringyour.sdk.CryptoInvoiceListener
import com.bringyour.sdk.CryptoInvoiceState
import com.bringyour.sdk.CryptoInvoiceViewController
import com.bringyour.sdk.CryptoPaymentCoin
import com.bringyour.sdk.CryptoPaymentStepList
import com.bringyour.sdk.CryptoPaymentTab
import com.bringyour.sdk.CryptoPaymentTabList
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.StringList
import com.bringyour.sdk.Sub
import com.bringyour.sdk.SubscriptionBalanceResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The crypto invoice of the flavors that sell outside a store (crypto-pay
 * mmm/ZCASH-CONTRACT.md §4.4, §6.2), over the sdk's CryptoInvoiceViewController. The
 * controller owns the quote, the countdown, the requote, the status polls and
 * the saved invoice (`.crypto_invoice` in the local state directory, which the
 * sign-out clears), and the method last chosen on this device
 * (`.payment_method`, the same). This view model opens the controller on the
 * current device, hands its state to the sheet as a [CryptoInvoiceSnapshot],
 * keeps whether the sheet shows, and reports a start's refusal and an
 * invoice's payment once each, for CryptoInvoiceHost to route the way the
 * surface routes a card purchase's.
 *
 * Activity scoped (PaymentMethodsScope provides it), so one invoice is shared
 * by every surface and outlives the screen that opened it. Nothing here keeps
 * state of its own across a sign-out: the activity ends with it.
 */
@HiltViewModel
class CryptoPayViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel() {

    private var device: DeviceLocal? = null
    private var controller: CryptoInvoiceViewController? = null
    private var listenerSub: Sub? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null

    // the invoice is on screen with the app in front -- the sheet, or the
    // account screen's payment in progress entry: the controller polls and
    // ticks only then
    private var sheetActive = false
    private var entryActive = false
    private var started = false

    // the start this session asked for, until it opens or is refused
    private var startRequest: StartRequest? = null

    // the invoice whose payment was reported, so it is reported once
    private var paidIntentId: String? = null

    private val _invoice = MutableStateFlow(CryptoInvoiceSnapshot())
    val invoice: StateFlow<CryptoInvoiceSnapshot> = _invoice.asStateFlow()

    private val _sheetVisible = MutableStateFlow(false)
    val sheetVisible: StateFlow<Boolean> = _sheetVisible.asStateFlow()

    /** The method last chosen on this device; the card until the controller has said. */
    private val _rememberedMethod = MutableStateFlow(Sdk.PaymentMethodCard)
    val rememberedMethod: StateFlow<String> = _rememberedMethod.asStateFlow()

    /**
     * The coin last chosen in each tab, by tab id, while the activity lives:
     * what a tap on the tab chooses first (the sdk's CryptoPaymentTabCoin), so
     * More coins gets back the coin chosen there after a tap on another tab.
     */
    private val lastTabCoins = mutableMapOf<String, String>()

    /** The coins whose last start was refused with asset_unavailable ("Temporarily unavailable"). */
    private val _unavailable = MutableStateFlow<Set<String>>(emptySet())
    val unavailable: StateFlow<Set<String>> = _unavailable.asStateFlow()

    /** A refused start, once per refusal (consumeRefused). */
    class Refused(val sequence: Long, val refusal: CryptoStartRefusal)

    private val _refused = MutableStateFlow<Refused?>(null)
    val refused: StateFlow<Refused?> = _refused.asStateFlow()
    private var refusedSequence = 0L
    private var consumedRefusedSequence = 0L

    /** A paid invoice, once per invoice (consumePaid). */
    private val _paidSequence = MutableStateFlow(0L)
    val paidSequence: StateFlow<Long> = _paidSequence.asStateFlow()
    private var consumedPaidSequence = 0L

    private class StartRequest(val asset: String, val plan: String)

    init {
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { next ->
            viewModelScope.launch { setDevice(next) }
        }
    }

    private fun setDevice(next: DeviceLocal?) {
        if (device === next) {
            return
        }
        closeController()
        device = next
        val opened = next?.openCryptoInvoiceViewController() ?: run {
            refresh()
            return
        }
        controller = opened
        // the controller calls back on the thread of the call that changed it,
        // or on a Go thread: always handled later, on the main thread
        listenerSub = opened.addCryptoInvoiceListener(CryptoInvoiceListener {
            viewModelScope.launch(Dispatchers.Main) { refresh() }
        })
        _rememberedMethod.value = opened.paymentMethod.ifEmpty { Sdk.PaymentMethodCard }
        if (started) {
            opened.start()
        }
        // the app's launch (and a new device) brings back the network's saved invoice
        opened.resume()
        refresh()
    }

    private fun closeController() {
        val closing = controller ?: return
        val owner = device
        controller = null
        listenerSub?.close()
        listenerSub = null
        closing.stop()
        owner?.closeViewController(closing)
        // what this view model held of the network's invoice goes with its
        // device (a sign-out, or another network): the next starts fresh
        startRequest = null
        paidIntentId = null
        _invoice.value = CryptoInvoiceSnapshot()
        _sheetVisible.value = false
        _unavailable.value = emptySet()
        _rememberedMethod.value = Sdk.PaymentMethodCard
        lastTabCoins.clear()
    }

    /** Remembers the chosen method on this device, and a coin as the one chosen in its tab. */
    fun selectMethod(method: String) {
        _rememberedMethod.value = method
        controller?.paymentMethod = method
        if (CryptoPayment.isCoin(method)) {
            tabId(method).takeIf { it.isNotEmpty() }?.let { lastTabCoins[it] = method }
        }
    }

    /**
     * What a tap on [tab] chooses (the sdk's CryptoPaymentTabCoin): the coin
     * last chosen in the tab, else the remembered method's coin, else the
     * tab's first coin that can be paid with now; null for a tab none of whose
     * coins can be, which cannot be chosen.
     */
    fun tabCoin(tab: CryptoPayTab): String? {
        val sdkTab = CryptoPaymentTab()
        sdkTab.id = tab.id
        val coins = Sdk.newCryptoPaymentCoinList()
        tab.coins.forEach { coin ->
            val sdkCoin = CryptoPaymentCoin()
            sdkCoin.asset = coin.asset
            sdkCoin.available = coin.available
            coins.add(sdkCoin)
        }
        sdkTab.coins = coins
        return Sdk.cryptoPaymentTabCoin(sdkTab, lastTabCoins[tab.id] ?: "", _rememberedMethod.value)
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * A coin's button: starts an invoice for [asset] and [plan] and shows the
     * sheet. One invoice at a time (CONTRACT §6.2.1): a saved invoice in
     * progress shows instead, and "Pay another way" is the way out of it. A
     * start already in flight is not started twice. Without a device to start
     * it on it is refused as the card purchase is (PurchaseStart.AccountNotReady).
     *
     * Returns whether an invoice was started.
     */
    fun startInvoice(asset: String, plan: String, storefrontCountry: String): Boolean {
        if (startRequest != null) {
            return false
        }
        val opened = controller ?: run {
            refuse(CryptoStartRefusal.AccountNotReady)
            return false
        }
        // the network's saved invoice, if the launch could not bring it back yet
        if (_invoice.value.phase == Sdk.CryptoInvoicePhaseNone) {
            opened.resume()
            refresh()
        }
        if (CryptoPayment.coinTap(_invoice.value) == CoinTap.ShowHeld) {
            _sheetVisible.value = true
            return false
        }
        // the controller is starting once this returns, and drops any answer
        // for the invoice it held: what it reports from here is this start's
        startRequest = StartRequest(asset, plan)
        _sheetVisible.value = true
        opened.startInvoice(asset, plan, "", storefrontCountry)
        refresh()
        return true
    }

    /** The account screen appeared: brings back the network's saved invoice, unless one is shown. */
    fun resumeSaved() {
        if (_sheetVisible.value || startRequest != null) {
            return
        }
        controller?.resume()
        refresh()
    }

    /** Opens the sheet on the invoice the controller holds (the "Payment in progress" entry). */
    fun openSheet() {
        if (CryptoPayment.inProgress(_invoice.value)) {
            _sheetVisible.value = true
        }
    }

    fun requote() {
        controller?.requote()
        refresh()
    }

    /**
     * Close. An invoice nothing has arrived for is dismissed: the controller
     * forgets it, and the purchase ends as cancelled. One money may have
     * arrived for (seen, confirming, in review, or resumed and not asked yet)
     * is only hidden: the controller keeps it, saved and held, and the account
     * screen shows it as a payment in progress until it is paid.
     */
    fun close() {
        val current = _invoice.value
        startRequest = null
        _sheetVisible.value = false
        if (current.phase == Sdk.CryptoInvoicePhaseOpen && !CryptoPayment.nothingReceived(current)) {
            return
        }
        if (current.phase == Sdk.CryptoInvoicePhaseOpen) {
            purchaseEvent(current.asset)?.let { (store, product) ->
                ClientEvents.purchaseCancelled(store, product, CryptoPayment.eventPlan(current.plan), false, current.priceUsd, USD)
            }
        }
        controller?.dismiss()
        refresh()
    }

    /** "Pay another way": closes the invoice and puts the row back on the card. */
    fun payAnotherWay() {
        close()
        selectMethod(Sdk.PaymentMethodCard)
    }

    /** Whether the sheet shows with the app in front. */
    fun setActive(nextActive: Boolean) {
        sheetActive = nextActive
        applyStarted()
    }

    /**
     * Whether the account screen's payment in progress entry shows with the
     * app in front: its status stays current, and a payment credited while it
     * shows ends the way one in the sheet does.
     */
    fun setEntryActive(nextActive: Boolean) {
        entryActive = nextActive
        applyStarted()
    }

    // Start while the invoice is on screen, Stop once it is not: no background checks
    private fun applyStarted() {
        val nextStarted = sheetActive || entryActive
        if (started == nextStarted) {
            return
        }
        started = nextStarted
        controller?.let { if (nextStarted) it.start() else it.stop() }
    }

    fun consumeRefused(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedRefusedSequence) {
            return false
        }
        consumedRefusedSequence = sequence
        return true
    }

    fun consumePaid(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedPaidSequence) {
            return false
        }
        consumedPaidSequence = sequence
        return true
    }

    /**
     * The coin selector's tabs for a plan surface selling [plan], from the
     * server's last balance answer: the sdk's CryptoPaymentTabs over the
     * balance's coins that can be paid with now and the ones it cannot quote
     * now, which stay in their place greyed ("Temporarily unavailable"). None
     * without a balance or without a coin of either list that sells [plan]:
     * the row is not shown then.
     */
    fun paymentTabs(result: SubscriptionBalanceResult?, plan: String): List<CryptoPayTab> {
        val balance = result ?: return emptyList()
        val items = Sdk.newStringList()
        items.add(plan)
        return Sdk.cryptoPaymentTabs(balance.cryptoAssets, balance.cryptoUnavailableAssets, items)?.toCryptoPayTabs()
            ?: emptyList()
    }

    /**
     * The tab a method sits in (the sdk's CryptoPaymentTabId): the coin's own
     * for USDC, Zcash and Monero, "More coins" for TAO, Bitcoin, ETH and SOL,
     * and "" for the card.
     */
    fun tabId(method: String): String = Sdk.cryptoPaymentTabId(method) ?: ""

    /**
     * The store a coin's offer and purchase events name: its market (the sdk's
     * CryptoAssetMarket), null for anything that is not a coin.
     */
    fun eventStore(asset: String): String? = Sdk.cryptoAssetMarket(asset)?.takeIf { it.isNotEmpty() }

    /**
     * Whether a coin has a payment uri (the sdk's CryptoPaymentUriScheme). TAO
     * has none: its invoice shows the address and the amount to send by hand,
     * and a QR code of the address alone.
     */
    fun hasPaymentLink(asset: String): Boolean = !Sdk.cryptoPaymentUriScheme(asset).isNullOrEmpty()

    /**
     * The store and the product a coin's purchase events name: its market and
     * its year of Pro (the sdk's CryptoPaymentEventProduct, pro_yearly_<ticker>);
     * null for anything that is not a coin.
     */
    private fun purchaseEvent(asset: String): Pair<String, String>? {
        val store = eventStore(asset) ?: return null
        val product = Sdk.cryptoPaymentEventProduct(asset)?.takeIf { it.isNotEmpty() } ?: return null
        return store to product
    }

    /**
     * Whether the invoice's uri pays the address it shows (CheckCryptoPaymentUri).
     * The controller already blanks a uri that fails it; a uri is never shown
     * or opened without passing it here too.
     */
    fun uriPasses(snapshot: CryptoInvoiceSnapshot): Boolean =
        snapshot.uri.isNotEmpty() && Sdk.checkCryptoPaymentUri(snapshot.asset, snapshot.uri, snapshot.address)

    /** "Wallets that work" for a coin. */
    fun wallets(asset: String): List<String> = Sdk.cryptoWalletSuggestions(asset).toKotlinList()

    private fun refuse(refusal: CryptoStartRefusal) {
        refusedSequence += 1L
        _refused.value = Refused(sequence = refusedSequence, refusal = refusal)
    }

    private fun refresh() {
        val next = controller?.state?.let { snapshotOf(it) } ?: CryptoInvoiceSnapshot()
        _invoice.value = next
        onChanged(next)
    }

    /**
     * Follows the controller's state: the start this session asked for opens
     * or is refused, an invoice is paid, or the controller let it go. A call
     * back into the controller comes last: its callbacks are handled later.
     */
    private fun onChanged(next: CryptoInvoiceSnapshot) {
        var dismiss = false

        val request = startRequest
        if (request != null) {
            when (next.phase) {
                Sdk.CryptoInvoicePhaseOpen, Sdk.CryptoInvoicePhasePaid -> {
                    startRequest = null
                    _unavailable.update { it - request.asset }
                    purchaseEvent(next.asset)?.let { (store, product) ->
                        ClientEvents.purchaseStarted(store, product, CryptoPayment.eventPlan(next.plan), false, next.priceUsd, USD)
                    }
                }
                Sdk.CryptoInvoicePhaseFailed -> {
                    startRequest = null
                    val refusal = CryptoPayment.startRefusal(next.errorCode, next.errorMessage)
                    if (refusal == CryptoStartRefusal.Unavailable) {
                        _unavailable.update { it + request.asset }
                    }
                    purchaseEvent(request.asset)?.let { (store, product) ->
                        ClientEvents.purchaseFailed(store, product, CryptoPayment.eventPlan(request.plan), false, 0.0, USD, next.errorCode.ifEmpty { START_FAILED })
                    }
                    _sheetVisible.value = false
                    refuse(refusal)
                    // back to none, so the next start begins from nothing
                    dismiss = true
                }
            }
        } else if (next.phase == Sdk.CryptoInvoicePhaseFailed) {
            // the server no longer knows the invoice (status unknown), or a
            // refusal no start waits for (the start was closed): nothing to show
            _sheetVisible.value = false
            dismiss = true
        }

        if (CryptoPayment.isPaid(next) && next.intentId.isNotEmpty() && next.intentId != paidIntentId) {
            paidIntentId = next.intentId
            purchaseEvent(next.asset)?.let { (store, product) ->
                ClientEvents.purchaseCompleted(store, product, CryptoPayment.eventPlan(next.plan), false, next.priceUsd, USD)
            }
            _sheetVisible.value = false
            _paidSequence.update { it + 1L }
            // delivered: the controller lets the paid invoice go
            dismiss = true
        }

        if (next.phase == Sdk.CryptoInvoicePhaseNone && startRequest == null) {
            _sheetVisible.value = false
        }

        if (dismiss) {
            controller?.dismiss()
        }
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        closeController()
        device = null
        super.onCleared()
    }

    private companion object {
        const val USD = "USD"
        // the purchase.failed error class of a refusal without a code (a transport failure)
        const val START_FAILED = "start"
    }
}

/** The sdk's invoice state as plain values, with the sdk's memo rule (CryptoPaymentShowsMemo) on it. */
internal fun snapshotOf(state: CryptoInvoiceState): CryptoInvoiceSnapshot = CryptoInvoiceSnapshot(
    phase = state.phase ?: Sdk.CryptoInvoicePhaseNone,
    intentId = state.intentId ?: "",
    asset = state.asset ?: "",
    plan = state.plan ?: "",
    address = state.address ?: "",
    amount = state.amount ?: "",
    ticker = state.ticker ?: "",
    uri = state.uri ?: "",
    qrText = state.qrText ?: "",
    memo = state.memo ?: "",
    showsMemo = Sdk.cryptoPaymentShowsMemo(state.asset ?: "", state.memo ?: ""),
    priceUsd = state.priceUsd,
    secondsLeft = state.secondsLeft,
    status = state.status ?: "",
    reviewReason = state.reviewReason ?: "",
    remaining = state.remaining ?: "",
    confirmations = state.confirmations,
    confirmationsRequired = state.confirmationsRequired,
    steps = state.steps?.toCryptoInvoiceSteps() ?: emptyList(),
    refreshing = state.refreshing,
    errorCode = state.errorCode ?: "",
    errorMessage = state.errorMessage ?: "",
)

/** The sdk's status steps (CryptoInvoiceState.Steps) as plain values, in their order. */
internal fun CryptoPaymentStepList.toCryptoInvoiceSteps(): List<CryptoInvoiceStep> =
    (0L until len()).mapNotNull { get(it) }.map { step ->
        CryptoInvoiceStep(
            status = step.status ?: "",
            progress = step.progress ?: "",
            confirmations = step.confirmations,
            confirmationsRequired = step.confirmationsRequired,
        )
    }

internal fun StringList.toKotlinList(): List<String> = (0L until len()).map { get(it) }

/** The sdk's coin selector (CryptoPaymentTabs) as plain values, in its order. */
internal fun CryptoPaymentTabList.toCryptoPayTabs(): List<CryptoPayTab> =
    (0L until len()).mapNotNull { get(it) }.map { tab ->
        val coins = tab.coins
        CryptoPayTab(
            id = tab.id ?: "",
            label = tab.label ?: "",
            labelKey = tab.labelKey ?: "",
            iconKeys = tab.iconKeys?.toKotlinList() ?: emptyList(),
            coins = if (coins == null) {
                emptyList()
            } else {
                (0L until coins.len()).mapNotNull { coins.get(it) }.map { coin ->
                    CryptoPayCoin(
                        asset = coin.asset ?: "",
                        label = coin.label ?: "",
                        labelKey = coin.labelKey ?: "",
                        iconKey = coin.iconKey ?: "",
                        available = coin.available,
                    )
                }
            },
            available = tab.available,
        )
    }
