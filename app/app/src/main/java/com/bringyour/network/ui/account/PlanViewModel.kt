package com.bringyour.network.ui.account

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    @ApplicationContext private val context: Context
): ViewModel() {

    var inProgress by mutableStateOf(false)
        private set

    var billingClient: BillingClient? = null

    val productList = listOf(
        QueryProductDetailsParams.Product.newBuilder()
            .setProductId("supporter")
            .setProductType(BillingClient.ProductType.SUBS)
            .build(),
    )

    val buildBillingClient: (PurchasesUpdatedListener) -> BillingClient = { pul ->
        BillingClient.newBuilder(context)
            .setListener(pul)
            .enablePendingPurchases()
            .build()
    }

    var changePlanError by mutableStateOf<String?>(null)
        private set

    val purchasesUpdatedListener: (() -> Unit) -> PurchasesUpdatedListener = { onSuccess ->
        PurchasesUpdatedListener { billingResult, purchases ->
            setInProgress(false)
            billingClient?.endConnection()
            setChangePlanError(null)

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
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

    val setInProgress: (Boolean) -> Unit = { ip ->
        inProgress = ip
    }

    val setChangePlanError: (String?) -> Unit = { msg ->
        changePlanError = msg
    }

}