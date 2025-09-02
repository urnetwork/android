package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.gravityCondensedFamily
import com.bringyour.network.ui.theme.ppNeueBitBold
import com.bringyour.network.utils.isTablet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradePlanBottomSheet(
    sheetState: SheetState,
    scope: CoroutineScope,
    planViewModel: PlanViewModel,
    overlayViewModel: OverlayViewModel,
    setIsPresentingUpgradePlanSheet: (Boolean) -> Unit,
    // pollSubscriptionBalance: () -> Unit
) {

    val uriHandler = LocalUriHandler.current

    val closeSheet: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                setIsPresentingUpgradePlanSheet(false)
            }
        }
    }

    LaunchedEffect(Unit) {
        planViewModel.onUpgradeSuccess.collect {
            overlayViewModel.launch(OverlayMode.Upgrade)
            closeSheet()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { setIsPresentingUpgradePlanSheet(false) },
        sheetState = sheetState,
        modifier = Modifier
            .padding(
                top = WindowInsets.systemBars
                    .asPaddingValues()
                    .calculateTopPadding()
            ),
        containerColor = Black
    ) {

        UpgradePlanSheetContent(
            upgradeStripe = {
                uriHandler.openUri("https://pay.ur.io/b/3csaIs85tgIrh208wE?client_reference_id=${planViewModel.networkId}")
                // closeSheet()
                      },
            upgradeSolana = {},
            upgradeInProgress = planViewModel.inProgress,
            formattedSubscriptionPrice = planViewModel.formattedSubscriptionPrice
        )

    }

}


@Composable
private fun UpgradePlanSheetContent(
    upgradeInProgress: Boolean,
    upgradeStripe: () -> Unit,
    upgradeSolana: () -> Unit,
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
                        upgradeStripe()
                    },
                    enabled = !upgradeInProgress,
                    isProcessing = upgradeInProgress
                ) { buttonTextStyle ->
                    Text(
                        // stringResource(id = R.string.join_the_movement),
                        "Join with Stripe",
                        style = buttonTextStyle
                    )
                }

            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = {
                        upgradeSolana()
                    }
                ) {
                    Text(
                        "Pay with Solana Wallet",
                        color = BlueMedium,
                        fontFamily = ppNeueBitBold,
                        fontSize = 24.sp
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
            upgradeStripe = {},
            upgradeSolana = {},
            upgradeInProgress = false,
            formattedSubscriptionPrice = "$5.00"
        )
    }
}