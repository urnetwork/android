package com.bringyour.network.ui.upgrade

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R
import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.utils.buildSolanaPaymentUrl
import com.bringyour.sdk.Sdk
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

/**
 * The inline Stripe PaymentSheet (the dapp flavors, which ship Google
 * services): the server prepares the customer and the intent
 * (StripeSheetRequest), the sheet confirms it in the app -- a SetupIntent for
 * the yearly plan (card saved, 14-day trial, the welcome coupon when the offer
 * is active), a PaymentIntent for the monthly plan -- and on completion the
 * balance is polled until Pro lands.
 */
@Composable
fun rememberPlanPurchaser(
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    onPurchaseSuccess: () -> Unit,
): PlanPurchaser {
    val context = LocalContext.current
    val merchantName = stringResource(id = R.string.app_name)
    val notCompleted = stringResource(id = R.string.payment_not_completed)
    val pending = remember { arrayOfNulls<PurchaseInFlight>(1) }

    val paymentSheet = remember {
        PaymentSheet.Builder { result ->
            val inFlight = pending[0]
            pending[0] = null
            planViewModel.setInProgress(false)
            when (result) {
                is PaymentSheetResult.Completed -> {
                    inFlight?.let { ClientEvents.purchaseCompleted(Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, it.plan, it.trial, it.price, it.currency) }
                    onPurchaseSuccess()
                }
                is PaymentSheetResult.Canceled -> {
                    inFlight?.let { ClientEvents.purchaseCancelled(Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, it.plan, it.trial, it.price, it.currency) }
                }
                is PaymentSheetResult.Failed -> {
                    Log.e("PlanPurchaser", "payment sheet failed", result.error)
                    inFlight?.let { ClientEvents.purchaseFailed(Sdk.EventStoreStripe, ClientEvents.PRODUCT_STRIPE_PRO, it.plan, it.trial, it.price, it.currency, "sheet") }
                    planViewModel.setChangePlanError(listOfNotNull(notCompleted, result.error.localizedMessage).joinToString("\n"))
                }
            }
        }
    }.build()

    return remember(planViewModel, paymentSheet) {
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
                pending[0] = PurchaseInFlight(planName, 0 < result.trialDays, result.amountFirstPeriodUsd, result.currency.ifEmpty { "USD" })
                PaymentConfiguration.init(context, result.publishableKey)
                val configuration = PaymentSheet.Configuration.Builder(merchantDisplayName = merchantName)
                    .customer(PaymentSheet.CustomerConfiguration(id = result.customerId, ephemeralKeySecret = result.ephemeralKeySecret))
                    .googlePay(
                        PaymentSheet.GooglePayConfiguration(
                            environment = PaymentSheet.GooglePayConfiguration.Environment.Production,
                            countryCode = subscriptionBalanceViewModel.storefrontCountry ?: "US",
                            currencyCode = result.currency.ifEmpty { "USD" },
                        )
                    )
                    .allowsDelayedPaymentMethods(false)
                    .build()
                if (result.intentType == Sdk.StripeIntentTypeSetup && result.setupIntentClientSecret.isNotEmpty()) {
                    paymentSheet.presentWithSetupIntent(result.setupIntentClientSecret, configuration)
                } else if (result.paymentIntentClientSecret.isNotEmpty()) {
                    paymentSheet.presentWithPaymentIntent(result.paymentIntentClientSecret, configuration)
                } else {
                    pending[0] = null
                    planViewModel.setInProgress(false)
                    Toast.makeText(context, notCompleted, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

private class PurchaseInFlight(val plan: String, val trial: Boolean, val price: Double, val currency: String)

/** The dapp flavors open the wallet through the `solana:` deep link. */
@Composable
fun rememberSolanaPayLauncher(): SolanaPayLauncher {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    return remember {
        SolanaPayLauncher { reference, amountUsd, plan ->
            try {
                uriHandler.openUri(buildSolanaPaymentUrl(reference, amountUsd, plan))
                true
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, context.getString(R.string.payment_not_completed), Toast.LENGTH_LONG).show()
                false
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.no_solana_wallet_found), Toast.LENGTH_LONG).show()
                false
            }
        }
    }
}
