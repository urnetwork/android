package com.bringyour.network.ui.upgrade

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bringyour.network.R
import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.utils.buildSolanaPaymentUrl
import com.bringyour.sdk.Sdk
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * The F-Droid build cannot ship the Stripe SDK (it depends on Google Play
 * Services), so a card purchase hosts the ur.io pay page in an in-app web
 * view: the server prepares the intent (StripeSheetRequest), the page mounts
 * Stripe's Payment Element on it, and navigates to the `return` url when the
 * purchase is done, which the web view intercepts. Never an external browser.
 */
@Composable
fun rememberPlanPurchaser(
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    onPurchaseSuccess: () -> Unit,
): PlanPurchaser {
    val context = LocalContext.current
    val notCompleted = stringResource(id = R.string.payment_not_completed)
    var payPage by remember { mutableStateOf<PayPage?>(null) }

    payPage?.let { page ->
        PayPageDialog(
            page = page,
            onDone = {
                payPage = null
                planViewModel.setInProgress(false)
                ClientEvents.purchaseCompleted(Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, page.plan, page.trial, page.price, page.currency)
                onPurchaseSuccess()
            },
            onDismiss = {
                payPage = null
                planViewModel.setInProgress(false)
                ClientEvents.purchaseCancelled(Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, page.plan, page.trial, page.price, page.currency)
            },
        )
    }

    return remember(planViewModel) {
        PlanPurchaser(store = Sdk.EventStoreStripe) { plan, presentation ->
            val api = planViewModel.api
            if (api == null || planViewModel.inProgress) {
                return@PlanPurchaser
            }
            planViewModel.setInProgress(true)
            val yearly = plan == PlanType.YEARLY
            val planName = if (yearly) Sdk.PlanYearly else Sdk.PlanMonthly
            ClientEvents.purchaseStarted(
                Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, planName, yearly,
                if (yearly) (presentation.offer?.firstYearAmount ?: presentation.yearlyAmount) else presentation.monthlyAmount,
                presentation.currency,
            )
            StripeSheetRequest.request(api, plan, subscriptionBalanceViewModel.storefrontCountry) { result, error ->
                if (result == null) {
                    planViewModel.setInProgress(false)
                    ClientEvents.purchaseFailed(Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, planName, yearly, 0.0, presentation.currency, "prepare")
                    planViewModel.setChangePlanError(listOfNotNull(notCompleted, error).joinToString("\n"))
                    return@request
                }
                val secret = if (result.intentType == Sdk.StripeIntentTypeSetup) result.setupIntentClientSecret else result.paymentIntentClientSecret
                if (secret.isNullOrEmpty()) {
                    planViewModel.setInProgress(false)
                    Toast.makeText(context, notCompleted, Toast.LENGTH_SHORT).show()
                    return@request
                }
                payPage = PayPage(
                    url = Uri.parse(PAY_SHEET_URL).buildUpon()
                        .appendQueryParameter("cs", secret)
                        .appendQueryParameter("pk", result.publishableKey)
                        .appendQueryParameter("plan", planName)
                        .appendQueryParameter("return", PAY_RETURN_URL)
                        .build()
                        .toString(),
                    plan = planName,
                    trial = 0 < result.trialDays,
                    price = result.amountFirstPeriodUsd,
                    currency = result.currency.ifEmpty { "USD" },
                )
            }
        }
    }
}

private const val PAY_SHEET_URL = "https://ur.io/app/pay-sheet"
private const val PAY_RETURN_URL = "urnetwork://pay/done"

private class PayPage(val url: String, val plan: String, val trial: Boolean, val price: Double, val currency: String)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PayPageDialog(page: PayPage, onDone: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.close))
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url ?: return false
                                if (url.toString().startsWith(PAY_RETURN_URL)) {
                                    onDone()
                                    return true
                                }
                                // the page stays on ur.io and Stripe's own frames
                                return false
                            }
                        }
                        loadUrl(page.url)
                    }
                }
            )
        }
    }
}

/**
 * Solana Pay on the F-Droid build: a sheet with the payment QR code (for a
 * wallet on another device) and an "Open wallet" button for the `solana:`
 * deep link on this one. The balance is polled when the app returns.
 */
@Composable
fun rememberSolanaPayLauncher(): SolanaPayLauncher {
    var request by remember { mutableStateOf<String?>(null) }
    request?.let { url ->
        SolanaPaySheet(url = url, onDismiss = { request = null })
    }
    val context = LocalContext.current
    return remember {
        SolanaPayLauncher { reference, amountUsd, plan ->
            try {
                request = buildSolanaPaymentUrl(reference, amountUsd, plan)
                true
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, context.getString(R.string.payment_not_completed), Toast.LENGTH_LONG).show()
                false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SolanaPaySheet(url: String, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val qr = remember(url) { qrBitmap(url, 640) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(id = R.string.paid_in_usdc_on_solana),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            qr?.let {
                androidx.compose.foundation.Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(id = R.string.solana_one_time_payment),
                    modifier = Modifier
                        .size(240.dp)
                        .background(Color.White)
                        .padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(id = R.string.solana_one_time_payment),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))
            URButton(onClick = {
                try {
                    uriHandler.openUri(url)
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.no_solana_wallet_found), Toast.LENGTH_LONG).show()
                }
            }) { style ->
                Text(stringResource(id = R.string.join_solana_wallet), style = style)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun qrBitmap(content: String, size: Int): android.graphics.Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bitmap
}.getOrNull()
