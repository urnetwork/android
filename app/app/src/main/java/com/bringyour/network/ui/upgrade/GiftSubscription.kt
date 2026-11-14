package com.bringyour.network.ui.upgrade

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk

/*
 * A redeemed gift code's year of Pro (GIFTOFPRIVACY.md 10.5, "After
 * redemption, everywhere"): a subscription on the store "gift", the sdk's
 * family SubscriptionStoreGift. Someone else paid for it and it never renews,
 * so there is nothing to manage, cancel or update. Where a plan row offers
 * that, every flavor says "A gift. It doesn't renew." instead, laid out as the
 * crypto line is (src/cryptoPay PaymentMethodHooks.kt).
 */

/** [store] is a gift's year of Pro (the sdk's store family SubscriptionStoreGift). */
fun isGiftStore(
    store: String?,
    classify: (String) -> String = Sdk::classifySubscriptionStore,
): Boolean = !store.isNullOrEmpty() && classify(store) == Sdk.SubscriptionStoreGift

/**
 * The store the plan rows describe: the current subscription's, except a gift
 * while another store also bills this network.
 *
 * A gift year starts after any paid period, and a store subscription that
 * renews after that overlaps it and keeps charging (the gift redeem bills no
 * one). The server names one active store as current, the first by name
 * ("gift" before "google" and "stripe"), so for a gift it could hide the
 * manage control of a subscription still charging. [activeStores] is every
 * store with a subscription running now (the balance's `subscriptions`); the
 * first that is not a gift is the one described then, and its manage control
 * stays.
 */
fun planStore(
    currentStore: String?,
    activeStores: List<String>,
    classify: (String) -> String = Sdk::classifySubscriptionStore,
): String? {
    if (!isGiftStore(currentStore, classify)) {
        return currentStore
    }
    return activeStores.firstOrNull { it.isNotEmpty() && !isGiftStore(it, classify) } ?: currentStore
}

/**
 * The Settings plan row's line for a gift, where the manage button sits,
 * across from the plan, as the crypto line does (SubscriptionManageLine).
 */
@Composable
fun GiftDoesNotRenewLine() {
    Text(
        stringResource(id = R.string.gift_does_not_renew),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        textAlign = TextAlign.End,
        modifier = Modifier.padding(start = 16.dp)
    )
}

/**
 * The account plan area's line for a gift, under the plan, as the crypto line
 * shows there (AccountPaymentNotices). The same on every flavor: Play sells
 * no gift but redeems one.
 */
@Composable
fun GiftPlanNotice(subscriptionBalanceViewModel: SubscriptionBalanceViewModel) {
    val currentStore by subscriptionBalanceViewModel.currentStore.collectAsState()
    if (isGiftStore(currentStore)) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(id = R.string.gift_does_not_renew),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}
