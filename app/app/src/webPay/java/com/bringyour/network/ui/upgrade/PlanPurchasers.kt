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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bringyour.network.R
import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.account.GuestAccount
import com.bringyour.network.ui.account.PurchaseRefusal
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.sdk.Sdk

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
    val accountNotReady = stringResource(id = R.string.account_isn_t_ready_for_purchases)
    // saved, so a recreated activity (a fold the activity does not handle, or the
    // process coming back) reopens the checkout instead of dropping it mid-payment
    var payPage by rememberSaveable(stateSaver = PayPageSaver) { mutableStateOf<PayPage?>(null) }

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
                subscriptionBalanceViewModel.cancelPurchaseConfirmation()
                planViewModel.setInProgress(false)
                ClientEvents.purchaseCancelled(Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, page.plan, page.trial, page.price, page.currency)
            },
        )
    }

    return remember(planViewModel) {
        PlanPurchaser(store = Sdk.EventStoreStripe) { plan, presentation ->
            val api = planViewModel.api
            when (purchaseStartFor(api != null, planViewModel.inProgress)) {
                PurchaseStart.InProgress -> return@PlanPurchaser
                PurchaseStart.AccountNotReady -> {
                    planViewModel.setChangePlanError(accountNotReady)
                    return@PlanPurchaser
                }
                PurchaseStart.Start -> Unit
            }
            // Start implies an api
            api ?: return@PlanPurchaser
            planViewModel.setInProgress(true)
            // the confirmation baseline loads while the pay page is up
            subscriptionBalanceViewModel.preparePurchaseConfirmation()
            val yearly = plan == PlanType.YEARLY
            val planName = if (yearly) Sdk.PlanYearly else Sdk.PlanMonthly
            ClientEvents.purchaseStarted(
                Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, planName, yearly,
                if (yearly) (presentation.offer?.firstYearAmount ?: presentation.yearlyAmount) else presentation.monthlyAmount,
                presentation.currency,
            )
            StripeSheetRequest.request(api, plan, subscriptionBalanceViewModel.storefrontCountry) { result, failure, refusal ->
                if (result == null) {
                    subscriptionBalanceViewModel.cancelPurchaseConfirmation()
                    planViewModel.setInProgress(false)
                    if (refusal == PurchaseRefusal.AddSignInMethod) {
                        // a guest network: the add-sign-in sheet instead of the error
                        ClientEvents.purchaseFailed(Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, planName, yearly, 0.0, presentation.currency, GuestAccount.PURCHASE_ERROR_GUEST_SIGN_IN_REQUIRED)
                        subscriptionBalanceViewModel.guestSignInRequired()
                        return@request
                    }
                    ClientEvents.purchaseFailed(Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, planName, yearly, 0.0, presentation.currency, "prepare")
                    // a refusal the app has a line for reads that line, in the reader's language
                    planViewModel.setChangePlanError(
                        failure?.let { CheckoutRefusal.message(it, notCompleted) { id -> context.getString(id) } } ?: notCompleted
                    )
                    return@request
                }
                val secret = if (result.intentType == Sdk.StripeIntentTypeSetup) result.setupIntentClientSecret else result.paymentIntentClientSecret
                if (secret.isNullOrEmpty()) {
                    subscriptionBalanceViewModel.cancelPurchaseConfirmation()
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

private val PayPageSaver = listSaver<PayPage?, Any>(
    save = { page -> page?.let { listOf(it.url, it.plan, it.trial, it.price, it.currency) } ?: emptyList() },
    restore = { values ->
        if (values.size < 5) null
        else PayPage(values[0] as String, values[1] as String, values[2] as Boolean, values[3] as Double, values[4] as String)
    },
)

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
