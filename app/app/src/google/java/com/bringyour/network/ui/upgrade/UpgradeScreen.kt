package com.bringyour.network.ui.upgrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.gravityCondensedFamily
import com.bringyour.network.utils.isTablet

@Composable
fun UpgradeScreen(
    navController: NavHostController,
    planViewModel: PlanViewModel,
    overlayViewModel: OverlayViewModel,
    setPendingSolanaSubscriptionReference: (String) -> Unit,
    createSolanaPaymentIntent: (
        reference: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) -> Unit,
    pollSubscriptionBalance: () -> Unit
) {

    LaunchedEffect(Unit) {
        planViewModel.onUpgradeSuccess.collect {
            overlayViewModel.launch(OverlayMode.Upgrade)

            navController.popBackStack()

        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            UpgradePlanSheetContent(
                upgrade = planViewModel.upgrade,
                upgradeInProgress = planViewModel.inProgress,
                formattedSubscriptionPrice = planViewModel.formattedSubscriptionPrice
            )
        }
    }

}

@Composable
private fun UpgradePlanSheetContent(
    upgradeInProgress: Boolean,
    upgrade: () -> Unit,
    formattedSubscriptionPrice: String,
) {

    val colModifier = Modifier

    if (!isTablet()) {
        colModifier.fillMaxSize()
    }

    Column(
        modifier = colModifier
            .padding(horizontal = 16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Column {
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Become a",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    "${formattedSubscriptionPrice}/month",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontFamily = gravityCondensedFamily,
                        color = TextMuted
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "URnetwork",
                    style = MaterialTheme.typography.headlineLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Supporter",
                    style = MaterialTheme.typography.headlineLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(id = R.string.support_us),
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(id = R.string.unlock_speed),
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(64.dp))

        Column {
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
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@Preview
@Composable
private fun UpgradePlanSheetContentPreview() {

    URNetworkTheme {
        UpgradePlanSheetContent(
            upgrade = {},
            upgradeInProgress = false,
            formattedSubscriptionPrice = "$5.00"
        )
    }
}