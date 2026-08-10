package com.bringyour.network.ui.shared.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.bringyour.network.MainApplication
import com.bringyour.network.PendingPurchaseReconcileWorker
import com.bringyour.network.PurchaseReporter
import com.bringyour.network.R
import com.bringyour.network.TAG
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    @ApplicationContext private val context: Context
): ViewModel() {

    private val _requestPlanUpgrade = MutableStateFlow(false)
    val requestPlanUpgrade: StateFlow<Boolean> = _requestPlanUpgrade.asStateFlow()

    private val _onUpgradeSuccess = MutableSharedFlow<Unit>()
    val onUpgradeSuccess: SharedFlow<Unit> = _onUpgradeSuccess.asSharedFlow()

    private val _upgradeSuccessSequence = MutableStateFlow(0L)
    val upgradeSuccessSequence: StateFlow<Long> = _upgradeSuccessSequence.asStateFlow()
    private var consumedUpgradeSuccessSequence = 0L

    private val _restoredSubscriptionSequence = MutableStateFlow(0L)
    val restoredSubscriptionSequence: StateFlow<Long> = _restoredSubscriptionSequence.asStateFlow()
    private var consumedRestoredSubscriptionSequence = 0L

    /**
     * A purchase that Google Play accepted but has NOT completed: it is awaiting
     * approval (a child needing a parent's OK) or an out-of-band payment. The PURCHASED
     * state does not arrive now -- it lands later, and `reconcileExistingSubscriptions`
     * picks it up on the next billing connection.
     *
     * This used to be swallowed entirely: `acknowledgePurchases` filters to PURCHASED,
     * found nothing, stopped the spinner and returned. No success, no error, no
     * message. The user's only reasonable conclusion is that the purchase failed -- so
     * they try to buy again.
     */
    private val _purchasePendingSequence = MutableStateFlow(0L)
    val purchasePendingSequence: StateFlow<Long> = _purchasePendingSequence.asStateFlow()
    private var consumedPurchasePendingSequence = 0L

    /**
     * The server verified the Play purchase but it is linked to a DIFFERENT network
     * than this session (terminal `wrong_network` report). The purchase is real --
     * it is still acknowledged so Play does not auto-refund it -- but this session
     * gets no credit, so the user is told it was purchased under a different
     * account instead of being shown a success overlay backed by nothing.
     */
    private val _purchaseWrongNetworkSequence = MutableStateFlow(0L)
    val purchaseWrongNetworkSequence: StateFlow<Long> = _purchaseWrongNetworkSequence.asStateFlow()
    private var consumedPurchaseWrongNetworkSequence = 0L

    /**
     * The purchase is persisted but the server could not be reached (or kept
     * answering `pending`) within the bounded in-session report attempts. Play has
     * the money; the daily PendingPurchaseReconcileWorker carries the report until
     * the server answers. The user sees "payment received, confirmation delayed" --
     * NOT a failure (they must not buy again) and NOT a success (the server has not
     * credited anything yet).
     */
    private val _purchaseReportDeferredSequence = MutableStateFlow(0L)
    val purchaseReportDeferredSequence: StateFlow<Long> = _purchaseReportDeferredSequence.asStateFlow()
    private var consumedPurchaseReportDeferredSequence = 0L

    var inProgress by mutableStateOf(false)
        private set

    /**
     * Bounded retry for the reconcile query within one billing-connection lifecycle
     * (1 s, 2 s, 4 s). The daily PendingPurchaseReconcileWorker is the durable
     * backstop beyond it.
     */
    private val reconcileMaxAttempts = 3
    private val reconcileRetryBackoffMs = 1_000L

    var formattedMonthlySubscriptionPrice by mutableStateOf("$5.00")


    private val _billingClient = MutableStateFlow<BillingClient?>(null)
    val billingClient: StateFlow<BillingClient?> = _billingClient.asStateFlow()

    val upgrade: () -> Unit = {
        if (!inProgress) {
            setInProgress(true)
            setChangePlanError(null)

            createBillingClientConnection(
                continueAfterRecoveredPurchase = false,
                closeUpgradeUiForRecoveredPurchase = true
            ) {
                _requestPlanUpgrade.value = true
            }
        }
    }

    private fun createBillingClientConnection(
        continueAfterRecoveredPurchase: Boolean = true,
        closeUpgradeUiForRecoveredPurchase: Boolean = false,
        onConnection: () -> Unit
    ) {
        val pul = initPurchasesUpdatedListener()

        _billingClient.value?.endConnection()

        val client = BillingClient.newBuilder(context)
            .setListener(pul)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()

        _billingClient.value = client

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {

                Log.i("Upgrade", "billing result ${billingResult.responseCode}")

                if (billingResult.responseCode == BillingResponseCode.OK) {
                    reconcileExistingSubscriptions(client, closeUpgradeUiForRecoveredPurchase) { recoveredPurchase ->
                        if (recoveredPurchase && !continueAfterRecoveredPurchase) {
                            setInProgress(false)
                        } else {
                            onConnection()
                        }
                    }
                } else {
                    setChangePlanError("Billing setup error: ${billingResult.responseCode} ${billingResult.debugMessage}")
                    setInProgress(false)
                }
            }

            override fun onBillingServiceDisconnected() {
                setChangePlanError("Billing error: Disconnected")
                setInProgress(false)
                client.endConnection()
            }
        })
    }

    var changePlanError by mutableStateOf<String?>(null)
        private set

    /**
     * The remedy offered alongside a billing error. On this flavor the purchase runs
     * through Google Play, so we can re-launch it directly.
     */
    val retryUpgrade: (() -> Unit)? = { upgrade() }

    val initPurchasesUpdatedListener: () -> PurchasesUpdatedListener = {
        PurchasesUpdatedListener { billingResult, purchases ->
            setChangePlanError(null)

            if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {

                Log.i(TAG, "PurchasesUpdatedListener billing response ok")

                reportAndAcknowledgePurchases(purchases, emitSuccess = true, updateProgress = true)

            } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) {
                // Handle an error caused by a user cancelling the purchase flow.
                setInProgress(false)
                Log.i("PlanViewModel", "purchases updated listener USER CANCELED")
            } else {
                // Handle any other error codes.
                // FIXME  show error message of billing error

                val msg = "Billing error: ${billingResult.responseCode} ${billingResult.debugMessage}"

                setChangePlanError(msg)
                setInProgress(false)

            }
        }
    }

    fun resetPlanUpgradeRequest() {
        _requestPlanUpgrade.value = false
    }

    fun consumeUpgradeSuccessSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedUpgradeSuccessSequence) {
            return false
        }
        consumedUpgradeSuccessSequence = sequence
        return true
    }

    fun consumePurchasePendingSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedPurchasePendingSequence) {
            return false
        }
        consumedPurchasePendingSequence = sequence
        return true
    }

    fun consumePurchaseWrongNetworkSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedPurchaseWrongNetworkSequence) {
            return false
        }
        consumedPurchaseWrongNetworkSequence = sequence
        return true
    }

    fun consumePurchaseReportDeferredSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedPurchaseReportDeferredSequence) {
            return false
        }
        consumedPurchaseReportDeferredSequence = sequence
        return true
    }

    fun consumeRestoredSubscriptionSequence(sequence: Long): Boolean {
        if (sequence == 0L || sequence <= consumedRestoredSubscriptionSequence) {
            return false
        }
        consumedRestoredSubscriptionSequence = sequence
        return true
    }

    private fun reconcileExistingSubscriptions(
        client: BillingClient,
        closeUpgradeUiForRecoveredPurchase: Boolean,
        attempt: Int = 0,
        onComplete: (Boolean) -> Unit
    ) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                val recoveredPurchases = purchases.filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
                }
                if (recoveredPurchases.isNotEmpty()) {
                    /**
                     * Report-before-acknowledge (UPGRADE.md N1): success UI and the
                     * confirmation poll now fire from inside the sequence, only once
                     * the server answers credited/already_credited -- never at
                     * observation time.
                     */
                    reportAndAcknowledgePurchases(
                        recoveredPurchases,
                        emitSuccess = closeUpgradeUiForRecoveredPurchase,
                        updateProgress = false,
                        emitRestored = !closeUpgradeUiForRecoveredPurchase
                    )
                }

                /**
                 * Keep the durable backstop in sync with what this reconcile just
                 * observed. A PENDING purchase can flip to PURCHASED days from now,
                 * while the app is closed -- the daily worker reports and
                 * acknowledges it before Play's 3-day auto-refund. Once nothing is
                 * pending, nothing is unacknowledged, and no persisted purchase
                 * proof is awaiting a terminal server answer, the backstop stands
                 * down.
                 */
                val hasPending = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PENDING
                }
                if (hasPending) {
                    PendingPurchaseReconcileWorker.markPendingSeen(context)
                } else if (recoveredPurchases.isEmpty() && !PurchaseReporter.hasEntries(context)) {
                    PendingPurchaseReconcileWorker.markSettled(context)
                }

                onComplete(recoveredPurchases.isNotEmpty())
            } else {
                Log.i(
                    "PlanViewModel",
                    "queryPurchasesAsync error (attempt ${attempt + 1}): ${billingResult.responseCode} ${billingResult.debugMessage}"
                )

                /**
                 * This used to be logged-and-dropped: the next reconcile attempt was
                 * the next Activity recreation, and reconcile-on-start is the whole
                 * restore story. Retry with backoff while this billing connection is
                 * still the live one; the daily PendingPurchaseReconcileWorker is the
                 * durable backstop past that.
                 */
                if (attempt + 1 < reconcileMaxAttempts) {
                    viewModelScope.launch {
                        delay(reconcileRetryBackoffMs shl attempt)
                        if (client === _billingClient.value && client.isReady) {
                            reconcileExistingSubscriptions(
                                client,
                                closeUpgradeUiForRecoveredPurchase,
                                attempt + 1,
                                onComplete
                            )
                        } else {
                            onComplete(false)
                        }
                    }
                } else {
                    onComplete(false)
                }
            }
        }
    }

    /**
     * The wave-2 reorder closing UPGRADE.md finding N1. For every PURCHASED
     * purchase, in order (the shared sequence lives in PurchaseReporter; the
     * PendingPurchaseReconcileWorker runs the same one):
     *
     *  1. PERSIST the purchase token durably, before anything else.
     *  2. REPORT it to the server and retry (bounded in-session) until a terminal
     *     answer.
     *  3. Only THEN acknowledge with Play and drop the persisted token.
     *
     * The success overlay and the confirmation poll fire ONLY on a server credit
     * (credited/already_credited) -- this replaces the old optimistic emit at
     * acknowledge time, where "You're premium." could be backed by nothing.
     * wrong_network and invalid still acknowledge (the purchase is real; Play must
     * not auto-refund it) but surface honestly instead of celebrating.
     */
    private fun reportAndAcknowledgePurchases(
        purchases: List<Purchase>,
        emitSuccess: Boolean,
        updateProgress: Boolean,
        emitRestored: Boolean = false
    ) {
        val purchasedSubscriptions = purchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        if (purchasedSubscriptions.isEmpty()) {

            /**
             * Play fires PurchasesUpdatedListener with OK for a PENDING purchase too
             * (pending purchases are enabled -- see enablePendingPurchases above). It is
             * not complete and no PURCHASED state arrives now, so there is nothing to
             * report or acknowledge -- but the user MUST be told, or the spinner just
             * stops and they are left staring at the plan screen assuming it failed.
             */
            val hasPending = purchases.any {
                it.purchaseState == Purchase.PurchaseState.PENDING
            }
            if (hasPending) {
                _purchasePendingSequence.update { it + 1L }
                // the PENDING -> PURCHASED flip can land days later, with the app
                // closed -- hand off to the durable daily reconcile so Play's 3-day
                // auto-refund clock never wins
                PendingPurchaseReconcileWorker.markPendingSeen(context)
            }

            if (updateProgress) {
                setInProgress(false)
            }
            return
        }

        val unacknowledgedPurchases = purchasedSubscriptions.filter { !it.isAcknowledged }

        /**
         * Step 1, before any network or billing call: persist the proof and arm the
         * durable backstop. From this moment process death loses nothing -- the
         * worker finds the persisted token (or the still-unacknowledged purchase),
         * reports, then acknowledges.
         */
        if (unacknowledgedPurchases.isNotEmpty()) {
            unacknowledgedPurchases.forEach { PurchaseReporter.persist(context, it) }
            PendingPurchaseReconcileWorker.markPendingSeen(context)
        }

        if (unacknowledgedPurchases.isEmpty()) {
            // already acknowledged: a terminal answer was reached in an earlier
            // session (or predates the report path). The entitlement is real --
            // celebrate and let the poll confirm.
            emitSuccessOrRestored(emitSuccess, emitRestored)
            if (updateProgress) {
                setInProgress(false)
            }
            return
        }

        val billingClient = _billingClient.value
        if (billingClient == null) {
            // the proof is persisted and the worker is armed; nothing is lost
            setChangePlanError("Billing error: client unavailable")
            if (updateProgress) {
                setInProgress(false)
            }
            return
        }

        val api = (context.applicationContext as? MainApplication)?.api

        viewModelScope.launch {
            var credited = false
            var wrongNetwork = false
            var invalid = false
            var deferred = false

            for (purchase in unacknowledgedPurchases) {
                val result = PurchaseReporter.reportAndAcknowledge(
                    context,
                    api,
                    billingClient,
                    purchase
                )
                when {
                    result.credited -> credited = true
                    result.wrongNetwork -> wrongNetwork = true
                    result.invalid -> invalid = true
                    else -> deferred = true
                }
            }

            if (credited) {
                emitSuccessOrRestored(emitSuccess, emitRestored)
            }
            if (wrongNetwork) {
                _purchaseWrongNetworkSequence.update { it + 1L }
            }
            if (invalid) {
                setChangePlanError(context.getString(R.string.purchase_could_not_be_verified))
            }
            if (deferred && emitSuccess) {
                // only for the interactive purchase path: the user just paid and is
                // watching -- tell them the payment landed and confirmation is on
                // its way. Reconcile-on-start stays quiet; the worker carries it.
                _purchaseReportDeferredSequence.update { it + 1L }
            }

            if (updateProgress) {
                setInProgress(false)
            }
        }
    }

    private fun emitSuccessOrRestored(emitSuccess: Boolean, emitRestored: Boolean) {
        if (emitSuccess) {
            emitUpgradeSuccessIfNeeded(true)
        } else if (emitRestored) {
            _restoredSubscriptionSequence.update { it + 1L }
        }
    }

    private fun emitUpgradeSuccessIfNeeded(emitSuccess: Boolean) {
        if (emitSuccess) {
            _upgradeSuccessSequence.update { it + 1L }
            viewModelScope.launch {
                _onUpgradeSuccess.emit(Unit)
            }
        }
    }

    private val fetchSubscriptionPriceInfo: () -> Unit = {
        val params = QueryProductDetailsParams.newBuilder()

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("supporter")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        )

        params.setProductList(productList)

        viewModelScope.launch {

            val productDetailsResult = billingClient.value?.queryProductDetails(params.build())

            val productDetails = productDetailsResult?.productDetailsList?.find { productDetails: ProductDetails ->
                productDetails.productId == "supporter"
            }

            if (productDetails != null) {

                formattedMonthlySubscriptionPrice = productDetails.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice
                    ?: "$5.00"

            }

        }

    }

    val setInProgress: (Boolean) -> Unit = { ip ->
        inProgress = ip
    }

    val setChangePlanError: (String?) -> Unit = { msg ->
        Log.i("PlanViewModel", "setChangePlanError: $msg")
        changePlanError = msg
    }

    override fun onCleared() {
        _billingClient.value?.endConnection()
        super.onCleared()
    }

    init {
        // WorkManager persists the daily reconcile job on its own; this only repairs
        // the case where the job was dropped (e.g. across an app update) while the
        // pending marker survived
        PendingPurchaseReconcileWorker.ensureScheduledIfPendingSeen(context)

        createBillingClientConnection {
            fetchSubscriptionPriceInfo()
        }
    }

}
