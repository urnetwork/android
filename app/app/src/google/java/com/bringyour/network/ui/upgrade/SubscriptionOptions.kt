package com.bringyour.network.ui.upgrade

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.Yellow

@Composable
fun SubscriptionOptions(
    planViewModel: PlanViewModel,
    /**
     * keep below params for different build flavors
     */
    createSolanaPaymentIntent: (
        reference: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) -> Unit,
    onSolanaUriOpened: (String) -> Unit,
    onStripePaymentSuccess: () -> Unit,
    isCheckingSolanaTransaction: Boolean
) {

    SubscriptionOptions(
        upgrade = planViewModel.upgrade,
        upgradeInProgress = planViewModel.inProgress,
        monthlyCostFormatted = planViewModel.formattedSubscriptionPrice
    )

}

@Composable
fun SubscriptionOptions(
    upgrade: () -> Unit,
    upgradeInProgress: Boolean,
    monthlyCostFormatted: String
) {


    Column(
        modifier = Modifier
            // .padding(16.dp)
            .background(
                OffBlack,
                RoundedCornerShape(12.dp)
            )
            .border(width = 2.dp, color = Yellow, shape = RoundedCornerShape(12.dp))
            .fillMaxWidth()
            .padding(16.dp)
    ) {

        Row (
            verticalAlignment = Alignment.Bottom
        ) {

            Text(
                "$5",
                fontSize = 24.sp,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                "/month",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 2.dp)
            )

        }

        Text(
            "Monthly",
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {

            URButton(
                onClick = {
                    upgrade()
                },
                enabled = !upgradeInProgress,
                isProcessing = upgradeInProgress
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.join_the_movement),
                    style = buttonTextStyle
                )
            }

        }

    }

}
