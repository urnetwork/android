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
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.bringyour.network.TAG
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    @ApplicationContext private val context: Context
): ViewModel() {

    private val _requestPlanUpgrade = MutableSharedFlow<Unit>()
    val requestPlanUpgrade: SharedFlow<Unit> = _requestPlanUpgrade.asSharedFlow()

    private val _onUpgradeSuccess = MutableSharedFlow<Unit>()
    val onUpgradeSuccess: SharedFlow<Unit> = _onUpgradeSuccess.asSharedFlow()

    private val _currentPlan = MutableStateFlow<Plan>(Plan.Basic)
    val currentPlan: StateFlow<Plan> = _currentPlan.asStateFlow()

    var isLoadingCurrentPlan by mutableStateOf(false)
        private set

    var inProgress by mutableStateOf(false)
        private set

    var formattedSubscriptionPrice by mutableStateOf("")

    private val _billingClient = MutableStateFlow<BillingClient?>(null)
    val billingClient: StateFlow<BillingClient?> = _billingClient.asStateFlow()

    val upgrade: () -> Unit = {
        if (!inProgress) {
            setInProgress(true)
            setChangePlanError(null)

            createBillingClientConnection {
                if (_billingClient.value != null) {
                    _billingClient.value?.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(billingResult: BillingResult) {

                            Log.i("Upgrade", "billing result ${billingResult.responseCode}")

                            if (billingResult.responseCode == BillingResponseCode.OK) {

                                viewModelScope.launch {
                                    _requestPlanUpgrade.emit(Unit)
                                }


                            } else {
                                // show error message of billing error
                                // FIXME show error

                                setChangePlanError("Billing error: ${billingResult.responseCode} ${billingResult.debugMessage}")

                                setInProgress(false)
                                _billingClient.value?.endConnection()
                            }
                        }

                        override fun onBillingServiceDisconnected() {
                            // Try to restart the connection on the next request to
                            // Google Play by calling the startConnection() method.

                            setChangePlanError("Billing error: Disconnected")
                            setInProgress(false)
                            _billingClient.value?.endConnection()
                        }
                    })
                }
            }
        }
    }

    val createBillingClientConnection: (() -> Unit) -> Unit = { onConnection ->
        val pul = initPurchasesUpdatedListener()

        if (_billingClient.value != null) {
            _billingClient.value?.endConnection()
        }

        _billingClient.value = BillingClient.newBuilder(context)
            .setListener(pul)
            .enablePendingPurchases()
            .build()

        _billingClient.value?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {

                Log.i("Upgrade", "billing result ${billingResult.responseCode}")

                if (billingResult.responseCode == BillingResponseCode.OK) {
                    onConnection()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.

                setChangePlanError("Billing error: Disconnected")
                setInProgress(false)
                _billingClient.value?.endConnection()
            }
        })
    }

    var changePlanError by mutableStateOf<String?>(null)
        private set

    val initPurchasesUpdatedListener: () -> PurchasesUpdatedListener = {
        PurchasesUpdatedListener { billingResult, purchases ->
            setInProgress(false)
            _billingClient.value?.endConnection()
            setChangePlanError(null)

            if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {

                Log.i(TAG, "PurchasesUpdatedListener billing response ok")

                if (isSupporter(purchases)) {
                    setCurrentPlan(Plan.Supporter)
                } else {
                    setCurrentPlan(Plan.Basic)
                }

                viewModelScope.launch {
                    _onUpgradeSuccess.emit(Unit)
                }

                // onSuccess()
            } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) {
                // Handle an error caused by a user cancelling the purchase flow.
                Log.i("PlanViewModel", "purchases updated listener USER CANCELED")
            } else {
                // Handle any other error codes.
                // FIXME  show error message of billing error

                val msg = "Billing error: ${billingResult.responseCode} ${billingResult.debugMessage}"

                setChangePlanError(msg)

            }
        }
    }

    val setIsLoadingCurrentPlan: (Boolean) -> Unit = { isLoading ->
        isLoadingCurrentPlan = isLoading
    }

    private val fetchPurchases = {

        if (_billingClient.value != null) {
            setIsLoadingCurrentPlan(true)

            val params = QueryPurchasesParams.newBuilder()
                .setProductType(ProductType.SUBS)

            viewModelScope.launch {
                val purchasesResult = _billingClient.value?.queryPurchasesAsync(params.build())

                if (purchasesResult?.purchasesList != null) {

                    if (isSupporter(purchasesResult.purchasesList)) {
                        setCurrentPlan(Plan.Supporter)
                    } else {
                        setCurrentPlan(Plan.Basic)
                    }

                }

                setIsLoadingCurrentPlan(false)

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

                formattedSubscriptionPrice = productDetails.subscriptionOfferDetails
                        ?.first()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.first()
                        ?.formattedPrice
                    ?: "$5.00"

            }

        }


    }

    val isSupporter: (List<Purchase>) -> Boolean = { purchases ->
        val planPurchases = purchases.filter { purchase ->
            "supporter" in purchase.products
        }

        planPurchases.isNotEmpty()
    }

    val setCurrentPlan: (Plan) -> Unit = { plan ->
        _currentPlan.value = plan
    }

    val setInProgress: (Boolean) -> Unit = { ip ->
        inProgress = ip
    }

    val setChangePlanError: (String?) -> Unit = { msg ->
        Log.i("PlanViewModel", "setChangePlanError: $msg")
        changePlanError = msg
    }

    init {
        createBillingClientConnection {
            fetchPurchases()
            fetchSubscriptionPriceInfo()
        }
    }

}
