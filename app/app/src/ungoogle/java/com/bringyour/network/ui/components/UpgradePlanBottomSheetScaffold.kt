package com.bringyour.network.ui.components

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.funkatronics.encoders.Base58
import java.security.SecureRandom


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradePlanBottomSheet(
    sheetState: SheetState,
    scope: CoroutineScope,
    planViewModel: PlanViewModel,
    overlayViewModel: OverlayViewModel,
    setIsPresentingUpgradePlanSheet: (Boolean) -> Unit,
    setPendingSolanaSubscriptionReference: (String) -> Unit,
    createSolanaPaymentIntent: (
        reference: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) -> Unit
) {

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

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

    val promptWalletTransaction: (reference: String) -> Unit = { reference ->

        val recipient = "74UNdYRpvakSABaYHSZMQNaXBVtA6eY9Nt8chcqocKe7"
        val amountDecimal = "40" // 40 USDC yearly sub
        val usdcMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" // mainnet USDC

        val label = "URnetwork"
        val message = "Yearly Supporter Subscription"
        val memo = ""

        val url = buildString {
            append("solana:")
            append(recipient)
            append("?amount="); append(amountDecimal)
            append("&spl-token="); append(usdcMint)
            append("&reference="); append(reference)
            append("&label="); append(Uri.encode(label))
            append("&message="); append(Uri.encode(message))
            append("&memo="); append(Uri.encode(memo))
        }

        uriHandler.openUri(url)

        setPendingSolanaSubscriptionReference(reference)

        closeSheet()

    }

    val upgradeWithSolana: () -> Unit = {

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val reference = Base58.encodeToString(bytes)

        createSolanaPaymentIntent(
            reference,
            {
                // on success
                promptWalletTransaction(reference)
            },
            {
                // on error
                Toast.makeText(context, "Error creating payment reference", Toast.LENGTH_SHORT).show()
            }
        )

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
            upgradeSolana = upgradeWithSolana,
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

    var isPromptingSolanaPayment by remember { mutableStateOf(false) }

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
                         stringResource(id = R.string.pay_with_stripe),
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
                        isPromptingSolanaPayment = true
                        upgradeSolana()
                    },
                    enabled = !isPromptingSolanaPayment
                ) {
                    Text(
                        stringResource(id = R.string.pay_with_solana_wallet),
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