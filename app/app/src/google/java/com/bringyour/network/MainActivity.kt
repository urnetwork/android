package com.bringyour.network

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import com.bringyour.sdk.SubscriptionCreatePaymentIdArgs
import com.bringyour.network.ui.MainNavHost
import com.bringyour.network.ui.settings.SettingsViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.wallet.WalletViewModel
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.publickey.SolanaPublicKey
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity: AppCompatActivity() {

    var requestPermissionLauncherAndStart : ActivityResultLauncher<String>? = null
    var requestPermissionLauncher : ActivityResultLauncher<String>? = null

    var vpnLauncher : ActivityResultLauncher<Intent>? = null

    // used for solana mobile adapter
    val activityResultSender = ActivityResultSender(this)

    private val walletViewModel: WalletViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val planViewModel: PlanViewModel by viewModels()

    private var sagaActivitySender: ActivityResultSender? = null

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
        val app = application as MainApplication
        if (app.allowForeground) {
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
        } else {
            prepareVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // immutable shadow
        val app = application as MainApplication

        // allow foreground to be started when the activity is active
        app.allowForeground = true

        sagaActivitySender = ActivityResultSender(this)

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

        val animateIn = intent.getBooleanExtra("ANIMATE_IN", false)
        val targetUrl = intent.getStringExtra("TARGET_URL")
        val defaultLocation = intent.getStringExtra("DEFAULT_LOCATION")

        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        // disable animation in if mobile or tablet
        if (Build.VERSION.SDK_INT >= 34 && !isTv) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        }

        setContent {
            URNetworkTheme {
                MainNavHost(
                    walletViewModel,
                    settingsViewModel,
                    planViewModel,
                    animateIn,
                    targetUrl,
                    defaultLocation,
                    activityResultSender
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

        // for upgrading plan
        lifecycleScope.launch {
            planViewModel.requestPlanUpgrade.collect {
                upgradePlan()
            }
        }

        // for requesting saga wallet
        lifecycleScope.launch {
            walletViewModel.requestSagaWallet.collect {
                requestSagaWallet()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStop() {
        super.onStop()

        val app = application as MainApplication

        app.vpnRequestStartListener = null
    }

    override fun onDestroy() {
        super.onDestroy()

        // immutable shadow
        val app = application as MainApplication

        app.allowForeground = false
    }

    private fun requestSagaWallet() {

        val solanaWalletAdapter = MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = Uri.parse("https://ur.io"),
                iconUri = Uri.parse("favicon.svg"),
                identityName = "URnetwork"
            )
        )
        solanaWalletAdapter.blockchain = Solana.Mainnet

        if (sagaActivitySender != null) {
            lifecycleScope.launch {
                val address = getWalletAddress(solanaWalletAdapter, sagaActivitySender!!)
                walletViewModel.sagaWalletAddressRetrieved(address)
            }
        }
    }

    private suspend fun getWalletAddress(walletAdapter: MobileWalletAdapter, activitySender: ActivityResultSender): String? {
        return when (val result = walletAdapter.connect(activitySender)) {
            is TransactionResult.Success -> {
                val pubKey = SolanaPublicKey(result.authResult.publicKey)
                pubKey.base58()
            }
            is TransactionResult.NoWalletFound -> {
                Log.i("SolanaViewModel", "No MWA compatible wallet app found on device.")
                null
            }
            is TransactionResult.Failure -> {
                Log.i("SolanaViewModel", "Error connecting to wallet: " + result.e.message)
                null
            }
        }
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
            planViewModel.setInProgress(false)

            return
        }

        // just choose the first offer
        val offer = productDetails.subscriptionOfferDetails?.first()

        if (offer == null) {

            planViewModel.setChangePlanError("Offer not found.")
            planViewModel.setInProgress(false)

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

//        val subscriptionCreatePaymentIdResult: SubscriptionCreatePaymentIdResult? = withContext(
//            Dispatchers.IO) {
//            app.api?.subscriptionCreatePaymentId(SubscriptionCreatePaymentIdArgs())
//        }

        app.api?.subscriptionCreatePaymentId(SubscriptionCreatePaymentIdArgs()) { result, error ->
            val buildingFlowParamsBuilder = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)

            result?.subscriptionPaymentId?.string()?.let {
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
}
