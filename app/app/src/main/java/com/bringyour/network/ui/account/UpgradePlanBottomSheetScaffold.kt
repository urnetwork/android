package com.bringyour.network.ui.account

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import com.bringyour.client.SubscriptionCreatePaymentIdArgs
import com.bringyour.client.SubscriptionCreatePaymentIdResult
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.components.BottomSheetContentContainer
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.gravityCondensedFamily
import com.bringyour.network.utils.isTablet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradePlanBottomSheetScaffold(
    scaffoldState: BottomSheetScaffoldState,
    scope: CoroutineScope,
    planViewModel: PlanViewModel,
    content: @Composable (PaddingValues) -> Unit,
) {

    UpgradePlanBottomSheetScaffold(
        scaffoldState = scaffoldState,
        scope = scope,
        inProgress = planViewModel.inProgress,
        setInProgress = planViewModel.setInProgress,
        changePlanError = planViewModel.changePlanError,
        setChangePlanError = planViewModel.setChangePlanError,
        // billingClient = planViewModel.billingClient,
        buildBillingClient = planViewModel.buildBillingClient,
        createPurchasesUpdatedListener = planViewModel.purchasesUpdatedListener,
        productList = planViewModel.productList,
        content = content
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradePlanBottomSheetScaffold(
    scaffoldState: BottomSheetScaffoldState,
    scope: CoroutineScope,
    inProgress: Boolean,
    setInProgress: (Boolean) -> Unit,
    changePlanError: String?,
    setChangePlanError: (String?) -> Unit,
    // billingClient: BillingClient?,
    buildBillingClient: (PurchasesUpdatedListener) -> BillingClient,
    productList: List<QueryProductDetailsParams.Product>,
    // setBillingClient: (BillingClient?) -> Unit,
    createPurchasesUpdatedListener: (() -> Unit) -> PurchasesUpdatedListener,
    content: @Composable (PaddingValues) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val application = context.applicationContext as? MainApplication
    val overlayVc = application?.overlayVc

    val PlanSupporter = "supporter"

    suspend fun purchase(plan: String, billingClient: BillingClient?) {

        val params = QueryProductDetailsParams.newBuilder()
        params.setProductList(productList)

        val productDetailsResult: ProductDetailsResult? = withContext(Dispatchers.IO) {
            billingClient?.queryProductDetails(params.build())
        }

        // Process the result.

        // An activity reference from which the billing flow will be launched.
        // val activity : Activity = ...;

        // FIXME find the product details that correspond to the selected plan

        val productDetails = productDetailsResult?.productDetailsList?.find { productDetails: ProductDetails ->
            when (plan) {
                PlanSupporter -> productDetails.productId == "supporter"
                else -> false
            }
        }

        Log.i("UpgradePlanBottomSheetScaffold", "FOUND PRODUCT DETAILS $productDetails")

        if (productDetails == null) {

            setChangePlanError("Product not found.")

            return
        }

        // just choose the first offer
        val offer = productDetails.subscriptionOfferDetails?.first()

        if (offer == null) {

            setChangePlanError("Offer not found.")

            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                .setProductDetails(productDetails)
                // to get an offer token, call ProductDetails.subscriptionOfferDetails()
                // for a list of offers that are available to the user
                .setOfferToken(offer.offerToken)
                .build()
        )

        val subscriptionCreatePaymentIdResult: SubscriptionCreatePaymentIdResult? = withContext(Dispatchers.IO) {
            application?.api?.subscriptionCreatePaymentIdSync(SubscriptionCreatePaymentIdArgs())
        }

        val buildingFlowParamsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)

        subscriptionCreatePaymentIdResult?.subscriptionPaymentId?.string()?.let {
            buildingFlowParamsBuilder.setObfuscatedAccountId(it)
        }

        val billingFlowParams = buildingFlowParamsBuilder.build()

        // Launch the billing flow

        activity?.let { a ->
            val billingResult = billingClient?.launchBillingFlow(a, billingFlowParams)
            Log.i("UpgradePlanBottomSheetScaffold", "billing result: $billingResult")
        }
    }

    val upgrade = {
        Log.i("Upgrade", "start")
        setInProgress(true)
        setChangePlanError(null)

        val purchasesUpdatedListener = createPurchasesUpdatedListener {
            // on success
            scope.launch {
                overlayVc?.openOverlay(OverlayMode.Upgrade.toString())
                scaffoldState.bottomSheetState.hide()
            }
        }

        val billingClient = buildBillingClient(purchasesUpdatedListener)

        Log.i("Upgrade", "billingClient is $billingClient")

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {

                Log.i("Upgrade", "billing result ${billingResult.responseCode}")

                if (billingResult.responseCode == BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
//                    scope.launch(Dispatchers.Main.immediate) {
//                        purchase(PlanSupporter)
//                    }
                    scope.launch {
                        purchase(PlanSupporter, billingClient)
                    }
                } else {
                    // show error message of billing error
                    // FIXME show error

                    setChangePlanError("Billing error: ${billingResult.responseCode} ${billingResult.debugMessage}")

                    setInProgress(false)
                    billingClient.endConnection()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.

                setChangePlanError("Billing error: Disconnected")
                setInProgress(false)
                billingClient.endConnection()
            }
        })

    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetShape = RoundedCornerShape(
            0.dp,
        ),
        sheetContainerColor = Black,
        sheetContentColor = Black,
        sheetPeekHeight = 0.dp,
        sheetDragHandle = {},
        sheetContent = {
            UpgradePlanSheetContent(
                scope = scope,
                scaffoldState = scaffoldState,
                upgrade = upgrade
            )
        }) { innerPadding ->
        content(innerPadding)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpgradePlanSheetContent(
    scope: CoroutineScope,
    scaffoldState: BottomSheetScaffoldState,
    upgrade: () -> Unit
) {
    BottomSheetContentContainer {

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text("Upgrade Subscription", style = TopBarTitleTextStyle)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Black
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    scaffoldState.bottomSheetState.hide()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                )
            }
        ) { innerPadding ->
            val colModifier = Modifier

            if (!isTablet()) {
                colModifier.fillMaxSize()
            }

            Column(
                modifier = colModifier
                    .background(color = Black)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
                // horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Column {
                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Become a",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "$5/month",
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontFamily = gravityCondensedFamily,
                                color = TextMuted
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "URnetwork",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Supporter",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Support us in building a new kind of network that gives instead of takes.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "You’ll unlock even faster speeds, and first dibs on new features like robust anti-censorship measures and data control.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(64.dp))

                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        URButton(onClick = {

                            // todo - process plan upgrade

                            upgrade()

//                                scope.launch {
//                                    scaffoldState.bottomSheetState.hide()
//                                }


                        }) { buttonTextStyle ->
                            Text("Join the movement", style = buttonTextStyle)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun UpgradePlanSheetContentPreview() {

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            SheetValue.Expanded
        )
    )
    val scope = rememberCoroutineScope()

    URNetworkTheme {
        UpgradePlanSheetContent(
            scope = scope,
            scaffoldState = scaffoldState,
            upgrade = {}
        )
    }
}