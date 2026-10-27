package com.bringyour.network.ui.upgrade

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.BlueMedium

/**
 * The past-due notice under the plan (PastDueRenewal), in the account plan
 * area and the Settings plan row, on every flavor: "Your UR Pro payment didn't
 * go through. Update your payment method to keep Pro." It shows exactly while
 * the latest balance carries payment_past_due, never as a system
 * notification. "Update payment method" shows where the flavor offers it
 * (rememberUpdatePaymentAction); on Play the notice is the text alone.
 * Laid out as the crypto renewal notice is (CryptoTermNotice).
 */
@Composable
fun PaymentPastDueNotice(subscriptionBalanceViewModel: SubscriptionBalanceViewModel) {
    val isPaymentPastDue by subscriptionBalanceViewModel.isPaymentPastDue.collectAsState()
    if (!isPaymentPastDue) {
        return
    }
    val updatePayment = rememberUpdatePaymentAction(subscriptionBalanceViewModel)
    val isOpeningUpdatePayment by subscriptionBalanceViewModel.isOpeningUpdatePayment.collectAsState()

    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(id = R.string.payment_past_due_notice),
            style = MaterialTheme.typography.bodyMedium
        )
        if (updatePayment != null) {
            TextButton(
                onClick = updatePayment,
                // one code at a time
                enabled = !isOpeningUpdatePayment,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    stringResource(id = R.string.payment_past_due_update_payment),
                    color = BlueMedium
                )
            }
        }
    }
}
