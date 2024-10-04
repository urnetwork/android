package com.bringyour.network

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import com.bringyour.client.SubscriptionCreatePaymentIdArgs
import com.bringyour.client.SubscriptionCreatePaymentIdResult
import com.bringyour.network.ui.MainNavHost
import com.bringyour.network.ui.settings.SettingsViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.PromptReviewViewModel
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.wallet.SagaViewModel
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity: AppCompatActivity() {

    var requestPermissionLauncherAndStart : ActivityResultLauncher<String>? = null
    var requestPermissionLauncher : ActivityResultLauncher<String>? = null

    var vpnLauncher : ActivityResultLauncher<Intent>? = null

    private val sagaViewModel: SagaViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val promptReviewViewModel: PromptReviewViewModel by viewModels()
    private val planViewModel: PlanViewModel by viewModels()
    private var reviewManager: ReviewManager? = null

    private fun prepareVpnService() {
        val app = application as MainApplication
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnLauncher?.launch(intent)
        } else {
//            onActivityResult(ActivityResult(RESULT_OK, null))
            app.startVpnService()
        }
    }

    fun requestPermissionsThenStartVpnService() {
        requestPermissionsThenStartVpnServiceWithRestart()
    }

    fun requestPermissionsThenStartVpnServiceWithRestart() {
        if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
            if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                val hasForegroundPermissions = ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                if (hasForegroundPermissions) {
                    prepareVpnService()
                } else {
                    requestPermissionLauncherAndStart?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                prepareVpnService()
            }
        } else {
            prepareVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // immutable shadow
        val app = application as MainApplication

        val sender = ActivityResultSender(this)
        sagaViewModel.setSender(sender)

        reviewManager = ReviewManagerFactory.create(this)

        // used when connecting
        requestPermissionLauncherAndStart =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                // the vpn service can start with degraded options if not granted
                prepareVpnService()
                settingsViewModel.onPermissionResult(isGranted)
                settingsViewModel.resetPermissionRequest()
            }

        // used in settings
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            settingsViewModel.onPermissionResult(isGranted)
            settingsViewModel.resetPermissionRequest()
        }

        vpnLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                app.startVpnService()
            }
        }

        // this is so overlays don't get cut by top bar and bottom drawer
        // WindowCompat.setDecorFitsSystemWindows(window, false)
        setTransparentStatusBar()

        // setStatusBarColor(color = Color.Transparent.toArgb(), false)

        setContent {
            URNetworkTheme {
                MainNavHost(
                    sagaViewModel,
                    settingsViewModel,
                    promptReviewViewModel,
                    planViewModel
                )
            }
        }

    }

    override fun onStart() {
        super.onStart()

        val app = application as MainApplication
        val activity = this

        // do this once at start
        lifecycleScope.launch {
            if (app.vpnRequestStart) {
                requestPermissionsThenStartVpnServiceWithRestart()
            }
        }

        app.vpnRequestStartListener = {
            lifecycleScope.launch {
                if (app.vpnRequestStart) {
                    requestPermissionsThenStartVpnServiceWithRestart()
                }
            }
        }

        settingsViewModel.checkPermissionStatus(this)

        // Observe the requestPermission state
        lifecycleScope.launch {
            settingsViewModel.requestPermission.collect { shouldRequest ->
                if (shouldRequest) {
                    // Check if the permission is already granted
                    if (ContextCompat.checkSelfPermission(
                            activity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        settingsViewModel.onPermissionResult(true)
                    } else {
                        // Request the permission
                        if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                            requestPermissionLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
        }

        // watch for review prompt
        lifecycleScope.launch {
            promptReviewViewModel.promptReview.collect { prompt ->
                if (prompt) {
                    val request = reviewManager?.requestReviewFlow()
                    request?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // We got the ReviewInfo object
                            val reviewInfo = task.result
                            launchReviewFlow(reviewInfo)
                        } else {
                            // There was some problem, log or handle the error code.
                            @ReviewErrorCode val reviewErrorCode = (task.getException() as ReviewException).errorCode
                            Log.i("MainActivity", "error prompting review -> code: $reviewErrorCode")
                        }
                    }
                }
            }
        }

        // for upgrading plan
        lifecycleScope.launch {
            planViewModel.requestPlanUpgrade.collect {
                upgradePlan()
            }
        }

    }

    override fun onStop() {
        super.onStop()

        val app = application as MainApplication

        app.vpnRequestStartListener = null
    }

    private fun launchReviewFlow(reviewInfo: ReviewInfo) {

        val flow = reviewManager?.launchReviewFlow(this, reviewInfo)
        flow?.addOnCompleteListener { _ ->
            // The flow has finished. The API does not indicate whether the user
            // reviewed or not, or even whether the review dialog was shown. Thus, no
            // matter the result, we continue our app flow.
            promptReviewViewModel.resetPromptReview()
        }

    }

    private fun setTransparentStatusBar() {
        val window = window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
    }

    private suspend fun upgradePlan() {

        Log.i("MainActivity", "upgrade plan hit")

        val app = application as MainApplication

        val billingClient = planViewModel.billingClient.value

        val activity = this

        val params = QueryProductDetailsParams.newBuilder()

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("supporter")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        )

        params.setProductList(productList)

        val productDetailsResult: ProductDetailsResult? = withContext(Dispatchers.IO) {
            billingClient?.queryProductDetails(params.build())
        }

        // Process the result.

        // An activity reference from which the billing flow will be launched.
        // val activity : Activity = ...;

        // FIXME find the product details that correspond to the selected plan

        val productDetails = productDetailsResult?.productDetailsList?.find { productDetails: ProductDetails ->
            productDetails.productId == "supporter"
        }

        Log.i("MainActivity", "FOUND PRODUCT DETAILS $productDetails")

        if (productDetails == null) {

            planViewModel.setChangePlanError("Product not found.")

            return
        }

        // just choose the first offer
        val offer = productDetails.subscriptionOfferDetails?.first()

        if (offer == null) {

            planViewModel.setChangePlanError("Offer not found.")

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

        val subscriptionCreatePaymentIdResult: SubscriptionCreatePaymentIdResult? = withContext(
            Dispatchers.IO) {
            app.api?.subscriptionCreatePaymentIdSync(SubscriptionCreatePaymentIdArgs())
        }

        val buildingFlowParamsBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)

        subscriptionCreatePaymentIdResult?.subscriptionPaymentId?.string()?.let {
            buildingFlowParamsBuilder.setObfuscatedAccountId(it)
        }

        val billingFlowParams = buildingFlowParamsBuilder.build()

        // Launch the billing flow

        activity.let { a ->
            val billingResult = billingClient?.launchBillingFlow(a, billingFlowParams)
            Log.i("MainActivity", "billing result: $billingResult")
        }
    }
}
