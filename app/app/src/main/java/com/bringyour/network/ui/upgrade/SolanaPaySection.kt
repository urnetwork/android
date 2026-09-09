package com.bringyour.network.ui.upgrade

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.theme.TextMuted

/**
 * The Solana Pay alternative under the card picker on the non-Play flavors:
 * one year of Pro for a one-time USDC payment (the welcome offer's 75% while
 * it is active, quoted by the server), as a secondary button -- the screen's
 * one primary button is the card picker's.
 */
@Composable
fun SolanaPaySection(
    presentation: PlanPresentation,
    onPay: () -> Unit,
    inProgress: Boolean,
    enabled: Boolean,
) {
    Column {
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            URButton(
                onClick = onPay,
                enabled = enabled && !inProgress,
                isProcessing = inProgress,
                style = ButtonStyle.SECONDARY
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.join_solana_wallet),
                    style = buttonTextStyle
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val offer = presentation.offer
        Text(
            stringResource(id = R.string.solana_one_time_payment) + " · " +
                stringResource(id = R.string.plan_price_per_year, offer?.firstYearPrice ?: presentation.yearlyPrice),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Text(
            stringResource(id = R.string.paid_in_usdc_on_solana),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}
