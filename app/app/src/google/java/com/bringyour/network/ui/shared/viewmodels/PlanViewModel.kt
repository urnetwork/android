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
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
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
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
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

                viewModelScope.launch {
                    _onUpgradeSuccess.emit(Unit)
                }

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

    val setInProgress: (Boolean) -> Unit = { ip ->
        inProgress = ip
    }

    val setChangePlanError: (String?) -> Unit = { msg ->
        Log.i("PlanViewModel", "setChangePlanError: $msg")
        changePlanError = msg
    }

    init {
        createBillingClientConnection {
            fetchSubscriptionPriceInfo()
        }
    }

}
