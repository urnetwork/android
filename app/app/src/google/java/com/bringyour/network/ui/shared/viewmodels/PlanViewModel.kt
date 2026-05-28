package com.bringyour.network.ui.shared.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.AcknowledgePurchaseParams
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
import com.bringyour.network.TAG
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

    var inProgress by mutableStateOf(false)
        private set

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

    val initPurchasesUpdatedListener: () -> PurchasesUpdatedListener = {
        PurchasesUpdatedListener { billingResult, purchases ->
            setChangePlanError(null)

            if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {

                Log.i(TAG, "PurchasesUpdatedListener billing response ok")

                acknowledgePurchases(purchases, emitSuccess = true, updateProgress = true)

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
                    if (closeUpgradeUiForRecoveredPurchase) {
                        emitUpgradeSuccessIfNeeded(true)
                    } else {
                        _restoredSubscriptionSequence.update { it + 1L }
                    }
                    acknowledgePurchases(
                        recoveredPurchases,
                        emitSuccess = false,
                        updateProgress = false
                    )
                }
                onComplete(recoveredPurchases.isNotEmpty())
            } else {
                Log.i(
                    "PlanViewModel",
                    "queryPurchasesAsync error: ${billingResult.responseCode} ${billingResult.debugMessage}"
                )
                onComplete(false)
            }
        }
    }

    private fun acknowledgePurchases(
        purchases: List<Purchase>,
        emitSuccess: Boolean,
        updateProgress: Boolean
    ) {
        val purchasedSubscriptions = purchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        if (purchasedSubscriptions.isEmpty()) {
            if (updateProgress) {
                setInProgress(false)
            }
            return
        }

        val billingClient = _billingClient.value
        if (billingClient == null) {
            setChangePlanError("Billing error: client unavailable")
            if (updateProgress) {
                setInProgress(false)
            }
            return
        }

        val unacknowledgedPurchases = purchasedSubscriptions.filter { !it.isAcknowledged }
        emitUpgradeSuccessIfNeeded(emitSuccess)

        if (unacknowledgedPurchases.isEmpty()) {
            if (updateProgress) {
                setInProgress(false)
            }
            return
        }

        val remaining = AtomicInteger(unacknowledgedPurchases.size)
        val failed = AtomicBoolean(false)

        unacknowledgedPurchases.forEach { purchase ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(params) { billingResult ->
                if (billingResult.responseCode != BillingResponseCode.OK) {
                    if (!failed.getAndSet(true)) {
                        setChangePlanError(
                            "Billing acknowledgement error: ${billingResult.responseCode} ${billingResult.debugMessage}"
                        )
                        if (updateProgress) {
                            setInProgress(false)
                        }
                    }
                    return@acknowledgePurchase
                }

                if (remaining.decrementAndGet() == 0 && !failed.get()) {
                    if (updateProgress) {
                        setInProgress(false)
                    }
                }
            }
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
        createBillingClientConnection {
            fetchSubscriptionPriceInfo()
        }
    }

}
