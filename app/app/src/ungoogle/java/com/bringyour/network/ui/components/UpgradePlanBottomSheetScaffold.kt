package com.bringyour.network.ui.components

import android.net.Uri
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
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.publickey.SolanaPublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.solana.mobilewalletadapter.clientlib.*
import com.solana.programs.MemoProgram
import com.solana.programs.SystemProgram
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.rpccore.JsonRpcDriver
import com.solana.networking.HttpNetworkDriver
import com.solana.networking.Rpc20Driver


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradePlanBottomSheet(
    sheetState: SheetState,
    scope: CoroutineScope,
    planViewModel: PlanViewModel,
    overlayViewModel: OverlayViewModel,
    setIsPresentingUpgradePlanSheet: (Boolean) -> Unit,
    activityResultSender: ActivityResultSender?,
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

    val upgradeWithSolana = {
        val solanaUri = Uri.parse("https://ur.io")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "URnetwork"


        scope.launch {

            // `connect` dispatches an association intent to MWA-compatible wallet apps.
            activityResultSender?.let { sender ->

                // Instantiate the MWA client object
                val walletAdapter = MobileWalletAdapter(
                    connectionIdentity = ConnectionIdentity(
                        identityUri = solanaUri,
                        iconUri = iconUri,
                        identityName = identityName,
                    ),
                )
                walletAdapter.blockchain = Solana.Mainnet


                val result = walletAdapter.transact(sender) { authResult ->
                    // Build a transaction using web3-solana classes
                    val account = SolanaPublicKey(authResult.accounts.first().publicKey)

                    val rpcClient = SolanaRpcClient("https://api.devnet.solana.com", KtorNetworkDriver())
                    val blockhasResponse = rpcClient.getLatestBlockhash()


                    val tx = Message.Builder()
                        .addInstruction(
                        MemoProgram.publishMemo(
                            account,
                            "solana:CckxW6C1CjsxYcXSiDbk7NYfPLhfqAm3kSB5LEZunnSE?amount=5&spl-token=EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
                            )
                        )
                        .setRecentBlockhash("")
                        .build()



                    // Issue a 'signTransactions' request
                    signAndSendTransactions(arrayOf(tx.serialize()));
                }

            }
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