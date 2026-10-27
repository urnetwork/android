package com.bringyour.network.ui.upgrade

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bringyour.network.R
import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.settings.ManageSubscriptionButton
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk
import java.text.DateFormat
import java.util.Date

/*
 * The crypto payment hooks of the flavors that sell outside a store
 * (mmm/ZCASH-CONTRACT.md §6.3: github/F-Droid, solana_dapp, ethos_dapp),
 * and the past-due notice's link to ur.io's billing (PastDueRenewal).
 * Shared code calls them by name; the play flavor's src/google/java defines
 * the same functions empty, so Play compiles none of this and its surfaces
 * render as they always have.
 */

/** The activity's crypto invoice, for the hooks below; provided by [PaymentMethodsScope]. */
val LocalCryptoPay = staticCompositionLocalOf<CryptoPayViewModel?> { null }

/**
 * Provides the one crypto invoice of the activity to every surface under it
 * (MainNavHost wraps its content in this, where the view model store is the
 * activity's), so the method rows, the invoice sheet and the account notices
 * share it, and the invoice outlives the screen that opened it.
 */
@Composable
fun PaymentMethodsScope(content: @Composable () -> Unit) {
    val cryptoPay: CryptoPayViewModel = hiltViewModel()
    CompositionLocalProvider(LocalCryptoPay provides cryptoPay, content = content)
}

/**
 * The invoice sheet, above whatever screen opened it, and what follows an
 * invoice: a paid one does what a card purchase does once it is done
 * ([onPaid]: the confirmation, and the surface that sold it closes); a
 * refused start takes the surface's refusal path -- the add-sign-in sheet for
 * a guest network, the payment error dialog for the rest. An unavailable coin
 * says so under its row instead.
 */
@Composable
fun CryptoInvoiceHost(
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    planViewModel: PlanViewModel,
    onPaid: () -> Unit,
) {
    val cryptoPay = LocalCryptoPay.current ?: return
    val context = LocalContext.current

    val paidSequence by cryptoPay.paidSequence.collectAsState()
    LaunchedEffect(paidSequence) {
        if (cryptoPay.consumePaid(paidSequence)) {
            onPaid()
        }
    }

    val refused by cryptoPay.refused.collectAsState()
    LaunchedEffect(refused) {
        val next = refused ?: return@LaunchedEffect
        if (!cryptoPay.consumeRefused(next.sequence)) {
            return@LaunchedEffect
        }
        when (val refusal = next.refusal) {
            // said under the row, where the coin stays selectable for a retry
            CryptoStartRefusal.Unavailable -> Unit
            CryptoStartRefusal.AddSignInMethod -> subscriptionBalanceViewModel.guestSignInRequired()
            CryptoStartRefusal.AccountNotReady ->
                planViewModel.setChangePlanError(context.getString(R.string.account_isn_t_ready_for_purchases))
            is CryptoStartRefusal.Refused -> planViewModel.setChangePlanError(
                // worded as a refused card checkout is: a line of its own, or the screen's line with the words
                CheckoutRefusal.refusedMessage(
                    code = refusal.code,
                    words = refusal.words,
                    screenLine = context.getString(R.string.payment_not_completed),
                ) { id -> context.getString(id) }
            )
        }
    }

    val visible by cryptoPay.sheetVisible.collectAsState()
    if (visible) {
        val invoice by cryptoPay.invoice.collectAsState()
        val uriPasses = remember(invoice.asset, invoice.uri, invoice.address) { cryptoPay.uriPasses(invoice) }
        val wallets = remember(invoice.asset) { cryptoPay.wallets(invoice.asset) }
        CryptoInvoiceSheet(
            invoice = invoice,
            uriPasses = uriPasses,
            wallets = wallets,
            setActive = cryptoPay::setActive,
            requote = cryptoPay::requote,
            payAnotherWay = cryptoPay::payAnotherWay,
            close = cryptoPay::close,
        )
    }
}

/**
 * The payment method row for a plan surface (CONTRACT §6.1), or null when the
 * server lists no coin for what the surface sells (the surface then looks as
 * it did). A coin buys the welcome offer while it is active, else the yearly
 * plan; choosing one moves a monthly selection to yearly. Its button starts
 * the invoice -- or, one invoice at a time (CONTRACT §6.2.1), shows the saved
 * invoice in progress instead -- and its events name the coin's store.
 */
@Composable
fun rememberPlanMethodSlot(
    presentation: PlanPresentation,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    selectedPlan: PlanType,
    setSelectedPlan: (PlanType) -> Unit,
): PlanMethodSlot? {
    val cryptoPay = LocalCryptoPay.current ?: return null

    val offerActive = presentation.offer != null
    val plan = CryptoPayment.coinPlan(offerActive)
    val balanceAnswer by subscriptionBalanceViewModel.balanceAnswer.collectAsState()
    val methods = remember(balanceAnswer, plan) { cryptoPay.paymentMethods(balanceAnswer?.result, plan) }
    val remembered by cryptoPay.rememberedMethod.collectAsState()
    val unavailable by cryptoPay.unavailable.collectAsState()
    val invoice by cryptoPay.invoice.collectAsState()
    val method = CryptoPayment.chosenMethod(remembered, methods)

    // a coin sells no monthly plan ("Monthly is card only")
    LaunchedEffect(method, selectedPlan) {
        val next = CryptoPayment.planFor(method, selectedPlan) { m, p -> Sdk.paymentMethodAllowsPlan(m, p) }
        if (next != selectedPlan) {
            setSelectedPlan(next)
        }
    }

    if (!CryptoPayment.showsRow(methods)) {
        return null
    }

    val alternative = if (CryptoPayment.isCoin(method)) {
        PlanAlternative(
            label = stringResource(id = R.string.crypto_pay_button, stringResource(id = paymentMethodLabel(method))),
            finePrint = stringResource(id = R.string.crypto_fine_print_yearly),
            monthlyNote = stringResource(id = R.string.pay_method_card_only_monthly),
            inProgress = invoice.phase == Sdk.CryptoInvoicePhaseStarting,
            start = {
                // one invoice at a time: a saved invoice in progress shows instead of a new one
                val started = cryptoPay.startInvoice(
                    asset = method,
                    plan = plan,
                    storefrontCountry = subscriptionBalanceViewModel.storefrontCountry ?: "",
                )
                if (started && offerActive) {
                    CryptoPayment.eventStore(method)?.let { store -> ClientEvents.offerCtaTapped(CryptoPayment.eventPlan(plan), store) }
                }
            },
        )
    } else {
        null
    }

    return PlanMethodSlot(
        row = {
            PaymentMethodRow(
                methods = methods,
                selected = method,
                onSelect = cryptoPay::selectMethod,
                unavailable = method in unavailable,
            )
        },
        alternative = alternative,
    )
}

/**
 * The account plan area's crypto lines (CONTRACT §6.2): the screen appearing
 * resumes a saved invoice, and one still waiting shows as "Payment in
 * progress", which opens it; a one-time crypto subscription says it does not
 * renew; and once its paid term has ended it asks for a renewal until the
 * grace ends (`crypto_term`), never as a system notification.
 */
@Composable
fun AccountPaymentNotices(
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    openUpgrade: () -> Unit,
) {
    val cryptoPay = LocalCryptoPay.current ?: return

    LaunchedEffect(Unit) {
        cryptoPay.resumeSaved()
    }

    val invoice by cryptoPay.invoice.collectAsState()
    val sheetVisible by cryptoPay.sheetVisible.collectAsState()
    if (!sheetVisible && CryptoPayment.inProgress(invoice)) {
        PaymentInProgressEntry(invoice = invoice, setActive = cryptoPay::setEntryActive, open = cryptoPay::openSheet)
    }

    val balanceAnswer by subscriptionBalanceViewModel.balanceAnswer.collectAsState()
    // the notice is the sdk's rule: from the end of the paid term until the grace ends
    val graceEndMillis = remember(balanceAnswer) {
        balanceAnswer?.result?.cryptoTerm
            ?.takeIf { Sdk.cryptoTermNoticeDue(it) }
            ?.let { CryptoPayment.timeMillis(it.graceEndTime) }
    }
    val currentStore by subscriptionBalanceViewModel.currentStore.collectAsState()
    if (graceEndMillis != null) {
        CryptoTermNotice(graceEndMillis = graceEndMillis, renew = openUpgrade)
    } else if (isOneTimeCryptoStore(currentStore)) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(id = R.string.crypto_does_not_renew),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

/**
 * The Settings plan row's subscription line for a Pro network: a one-time
 * crypto subscription (store family SubscriptionStoreCrypto) does not renew
 * and has nothing to manage, so it says so; any other keeps the flavor's
 * manage link.
 */
@Composable
fun SubscriptionManageLine(currentStore: String?, stripePortalUrl: String?) {
    if (isOneTimeCryptoStore(currentStore)) {
        // where the manage button sits, across from the plan
        Text(
            stringResource(id = R.string.crypto_does_not_renew),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp)
        )
    } else {
        ManageSubscriptionButton(stripePortalUrl)
    }
}

/**
 * The past-due notice's "Update payment method" (PaymentPastDueNotice): ur.io's
 * Manage Subscription in the system browser, signed in with a one-time auth
 * code, which the flavors that sell outside a store may link to, as their
 * Manage subscription does.
 */
@Composable
fun rememberUpdatePaymentAction(subscriptionBalanceViewModel: SubscriptionBalanceViewModel): (() -> Unit)? {
    val context = LocalContext.current
    return remember(subscriptionBalanceViewModel, context) {
        {
            subscriptionBalanceViewModel.openUpdatePayment { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            }
        }
    }
}

/** A subscription paid once in USDC, Zcash or Monero: it never renews. */
private fun isOneTimeCryptoStore(store: String?): Boolean =
    !store.isNullOrEmpty() && Sdk.classifySubscriptionStore(store) == Sdk.SubscriptionStoreCrypto

/**
 * A saved invoice still waiting: "Payment in progress", its network and where
 * it is; opens the invoice. While it shows with the app in front the invoice
 * is followed as in the sheet ([setActive]).
 */
@Composable
private fun PaymentInProgressEntry(invoice: CryptoInvoiceSnapshot, setActive: (Boolean) -> Unit, open: () -> Unit) {
    LifecycleResumeEffect(Unit) {
        setActive(true)
        onPauseOrDispose { setActive(false) }
    }
    val view = remember(invoice) { CryptoPayment.view(invoice, uriPasses = false) }
    val network = stringResource(id = cryptoNetworkLine(invoice.asset))
    // a resumed invoice says where it is once the sheet has asked
    val status = when {
        invoice.status.isEmpty() -> null
        view.review -> stringResource(id = R.string.crypto_status_review)
        view.expired -> stringResource(id = R.string.crypto_invoice_quote_expired)
        invoice.status == Sdk.CryptoPaymentStatusConfirming -> stringResource(
            id = R.string.crypto_status_confirming,
            invoice.confirmations.coerceAtMost(invoice.confirmationsRequired),
            invoice.confirmationsRequired
        )
        invoice.status == Sdk.CryptoPaymentStatusSeen -> stringResource(id = R.string.crypto_status_seen)
        else -> stringResource(id = R.string.crypto_status_waiting)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { open() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(id = R.string.crypto_payment_in_progress),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                if (status != null) "$network · $status" else network,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** "Your paid year of Pro has ended. Renew by {date} to keep Pro." with Renew, which opens Get Pro. */
@Composable
private fun CryptoTermNotice(graceEndMillis: Long, renew: () -> Unit) {
    val date = remember(graceEndMillis) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(graceEndMillis))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(id = R.string.crypto_term_ended_notice, date),
            style = MaterialTheme.typography.bodyMedium
        )
        TextButton(
            onClick = renew,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                stringResource(id = R.string.crypto_term_renew),
                color = BlueMedium
            )
        }
    }
}
