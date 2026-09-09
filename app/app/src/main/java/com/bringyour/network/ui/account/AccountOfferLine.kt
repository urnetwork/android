package com.bringyour.network.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.ProGoldLight
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.upgrade.OfferPresentation
import com.bringyour.network.ui.upgrade.formatOfferDeadline

/**
 * The welcome offer on the Account plan row while it can still be redeemed
 * (mmm/onboarding/PLAN.md): "3 months of Pro, free · $29.99 for your first
 * year" and the static deadline. Read-only here: the offer was issued by the
 * onboarding or the email; a tap opens the Get Pro screen, which sells it.
 */
@Composable
fun AccountOfferLine(
    offer: OfferPresentation,
    openGetPro: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openGetPro() }
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(id = R.string.offer_months_free_headline, offer.monthsFree) + " · " +
                stringResource(id = R.string.offer_first_year_price, offer.firstYearPrice),
            style = MaterialTheme.typography.bodyMedium,
            color = ProGoldLight
        )
        if (0L < offer.expiresAtMillis) {
            Text(
                stringResource(id = R.string.offer_available_until, formatOfferDeadline(offer.expiresAtMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
