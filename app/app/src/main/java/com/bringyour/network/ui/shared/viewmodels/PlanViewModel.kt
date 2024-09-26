package com.bringyour.network.ui.shared.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    @ApplicationContext private val context: Context
): ViewModel() {

    var currentPlan by mutableStateOf<Plan>(Plan.Basic)
        private set

    var isLoadingCurrentPlan by mutableStateOf(false)
        private set

    var inProgress by mutableStateOf(false)
        private set

    private var billingClient by mutableStateOf<BillingClient?>(null)

    val productList = listOf(
        QueryProductDetailsParams.Product.newBuilder()
            .setProductId("supporter")
            .setProductType(BillingClient.ProductType.SUBS)
            .build(),
    )

    val buildBillingClient: (PurchasesUpdatedListener) -> BillingClient = { pul ->
        billingClient = BillingClient.newBuilder(context)
            .setListener(pul)
            .enablePendingPurchases()
            .build()
        billingClient!!
    }

    var changePlanError by mutableStateOf<String?>(null)
        private set

    val purchasesUpdatedListener: (() -> Unit) -> PurchasesUpdatedListener = { onSuccess ->
        PurchasesUpdatedListener { billingResult, purchases ->
            setInProgress(false)
            billingClient?.endConnection()
            setChangePlanError(null)

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {

                if (isSupporter(purchases)) {
                    setCurrentPlan(Plan.Supporter)
                } else {
                    setCurrentPlan(Plan.Basic)
                }

                onSuccess()
            } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                // Handle an error caused by a user cancelling the purchase flow.

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

    // fun fetchPurchases(params: BillingClient.QueryPurchasesParams) {
    val fetchPurchases = {

        setIsLoadingCurrentPlan(true)

        val pul = PurchasesUpdatedListener{ _, _ -> }

        val bc = BillingClient.newBuilder(context)
            .setListener(pul)
            .enablePendingPurchases()
            .build()

        // bc.startConnection(pul)

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.SUBS)

        viewModelScope.launch {
            val purchasesResult = bc.queryPurchasesAsync(params.build())
            // purchases = purchasesResult.purchasesList
            // purchases.clear()

            Log.i("PlanViewModel", "purchases length is ${purchasesResult.purchasesList.count()}")
            purchasesResult.purchasesList.forEach { purchase ->
                Log.i("PlanViewModel", "$purchase")

            }

            if (isSupporter(purchasesResult.purchasesList)) {
                // currentPlanId = "supporter"
                setCurrentPlan(Plan.Supporter)
            } else {
                setCurrentPlan(Plan.Basic)
            }

            setIsLoadingCurrentPlan(false)

        }

    }

    private val isSupporter: (List<Purchase>) -> Boolean = { purchases ->
        val planPurchases = purchases.filter { purchase ->
            "supporter" in purchase.products
        }

        planPurchases.isNotEmpty()
    }

    val setCurrentPlan: (Plan) -> Unit = { plan ->
        currentPlan = plan
    }

    val setInProgress: (Boolean) -> Unit = { ip ->
        inProgress = ip
    }

    val setChangePlanError: (String?) -> Unit = { msg ->
        changePlanError = msg
    }

    init {
        fetchPurchases()
    }

}

enum class Plan {
    Basic,
    Supporter;

    companion object {
        fun fromString(value: String): Plan {
            return when (value.uppercase()) {
                "SUPPORTER" -> Supporter
                else -> Basic
            }
        }
    }
}